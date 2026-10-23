package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import com.ohgiraffers.handlermethod.repository.RiskIncidentRepository;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import com.ohgiraffers.handlermethod.service.RiskIncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B03: 화면12 Incident 상태 전이(ack/resolve) 검증.
 * <p>
 * MockMvc + 테스트 트랜잭션이 아니라 임의 포트로 띄운 서버에 실제 HTTP 요청을 보낸다.
 * 각 요청은 자기 트랜잭션에서 커밋되고 risk_incident 행 잠금(SELECT ... FOR UPDATE)을 실제로 거친다.
 * 가상 자료만 쓰며 DB는 기본 프로필의 인메모리 H2다. MySQL로 확인하려면 SPRING_PROFILES_ACTIVE=mysql.
 * <p>
 * 확인하는 것
 * - B03-a 순차: 허용 전이, RESOLVED→ACKNOWLEDGED 409, 같은 상태 재요청은 200·updated_at 그대로, 404/401/403 계약.
 * - B03-b 동시: ack/resolve가 어느 순서로 잠금을 얻어도 최종 RESOLVED에서 역행하지 않고,
 *   응답은 200/409만, 500·교착·잠금 시간 초과(503) 없음, 이벤트·행 중복 없음,
 *   대시보드 openIncidentCount·Incident 목록이 DB와 일치, 원문(userInput) 비노출.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "search.enabled=false")
class IncidentTransitionConcurrencyIntegrationTest {
    private static final int ROUNDS = 10;
    private static final int STORM_THREADS = 8;
    /** 응답 JSON의 updatedAt(자바 메모리 값)과 DB 저장값(TIMESTAMP(6))의 반올림 차이 허용 범위. */
    private static final Duration CLOCK_TOLERANCE = Duration.ofMillis(1);

    @LocalServerPort int port;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AgentRiskHistoryRepository histories;
    @Autowired RiskIncidentRepository incidents;
    @Autowired RiskIncidentService riskIncidentService;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();

    record Res(String action, int status, String body) {
        <T> T path(String jsonPath) { return JsonPath.read(body, jsonPath); }
        String incidentStatus() { return path("$.status"); }
        String code() { return path("$.code"); }
        LocalDateTime updatedAt() { return LocalDateTime.parse(path("$.updatedAt")); }
    }

    // ---------- 준비 ----------

    /** 운영 경로(createIfNeeded)로 OPEN Incident를 만든다. 원문 표식은 AgentRiskHistory.userInput에만 들어간다. */
    private long newOpenIncident(String rawMarker) {
        String trace = "b03-" + UUID.randomUUID();
        var history = histories.saveAndFlush(AgentRiskHistory.create(rawMarker, "qa-tool", "QA_DECISION", 90,
                "HIGH", "POLICY_VIOLATION", "BLOCK_AND_ESCALATE", true, trace));
        riskIncidentService.createIfNeeded("QA_DECISION", 90, "HIGH", "BLOCK_AND_ESCALATE", history.getId(), trace);
        Long id = jdbc.queryForObject("select id from risk_incident where risk_history_id = ?", Long.class, history.getId());
        assertThat(dbStatus(id)).isEqualTo("OPEN");
        return id;
    }

    private static String rawMarker() { return "RAW-ORIGINAL-가상원문-" + UUID.randomUUID(); }

    private String dbStatus(long id) {
        return jdbc.queryForObject("select status from risk_incident where id = ?", String.class, id);
    }

    private LocalDateTime dbUpdatedAt(long id) {
        return jdbc.queryForObject("select updated_at from risk_incident where id = ?", LocalDateTime.class, id);
    }

    private LocalDateTime dbCreatedAt(long id) {
        return jdbc.queryForObject("select created_at from risk_incident where id = ?", LocalDateTime.class, id);
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    private static void assertClose(LocalDateTime actual, LocalDateTime expected) {
        assertThat(Duration.between(expected, actual).abs()).isLessThanOrEqualTo(CLOCK_TOLERANCE);
    }

    // ---------- HTTP ----------

    private Res send(String method, String path, String authorization, String action) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) builder.header("Authorization", authorization);
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(action, response.statusCode(), response.body());
    }

    private String bearer(String user, String role) { return "Bearer " + tokens.createToken(user, role); }
    private String admin() { return bearer("admin", "ROLE_ADMIN"); }

    private Res ack(long id) throws Exception { return send("PATCH", "/api/incidents/" + id + "/ack", admin(), "ack"); }
    private Res resolve(long id) throws Exception { return send("PATCH", "/api/incidents/" + id + "/resolve", admin(), "resolve"); }

    /** 작업들을 각 스레드에 올려 둔 뒤 같은 시점에 출발시킨다. */
    private List<Res> parallel(List<Callable<Res>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Res>> futures = new ArrayList<>();
            for (Callable<Res> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Res> results = new ArrayList<>();
            // 교착이 있으면 여기서 시간 초과로 실패한다.
            for (Future<Res> future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 노출 응답 계약: 200 또는 409만. 409는 항상 종결 건 되돌리기 거부이고 현재 상태를 담는다. */
    private static void assertOnly200Or409(List<Res> results) {
        for (Res r : results) {
            assertThat(r.status()).as("%s -> %s", r.action(), r.body()).isIn(200, 409);
            if (r.status() == 409) {
                assertThat(r.action()).isEqualTo("ack");
                assertThat(r.code()).isEqualTo("INCIDENT_ALREADY_RESOLVED");
                assertThat((String) r.path("$.details.currentStatus")).isEqualTo("RESOLVED");
                assertThat((String) r.path("$.details.requestedStatus")).isEqualTo("ACKNOWLEDGED");
            } else {
                assertThat(r.incidentStatus()).isEqualTo(r.action().equals("ack") ? "ACKNOWLEDGED" : "RESOLVED");
            }
        }
    }

    private static void assertNoRawText(String marker, List<Res> results) {
        for (Res r : results) assertThat(r.body()).doesNotContain(marker).doesNotContain("RAW-ORIGINAL");
    }

    // ---------- B03-a 순차 (커밋되는 실제 요청) ----------

    @Test
    void sequentialTransitionsCommitAndNoOpsKeepUpdatedAt() throws Exception {
        String marker = rawMarker();
        long id = newOpenIncident(marker);
        LocalDateTime created = dbCreatedAt(id);
        assertThat(dbUpdatedAt(id)).isEqualTo(created);

        Res acked = ack(id);
        assertThat(acked.status()).isEqualTo(200);
        assertThat(acked.incidentStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(dbStatus(id)).isEqualTo("ACKNOWLEDGED");
        LocalDateTime ackedAt = dbUpdatedAt(id);
        assertThat(ackedAt).isAfterOrEqualTo(created);
        assertClose(acked.updatedAt(), ackedAt);

        // 같은 상태 재요청: 200, 커밋 후에도 updated_at 그대로
        Res ackAgain = ack(id);
        assertThat(ackAgain.status()).isEqualTo(200);
        assertThat(ackAgain.incidentStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(dbUpdatedAt(id)).isEqualTo(ackedAt);
        assertThat(ackAgain.updatedAt()).isEqualTo(ackedAt);

        Res resolved = resolve(id);
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.incidentStatus()).isEqualTo("RESOLVED");
        LocalDateTime resolvedAt = dbUpdatedAt(id);
        assertThat(resolvedAt).isAfterOrEqualTo(ackedAt);
        assertClose(resolved.updatedAt(), resolvedAt);

        Res resolveAgain = resolve(id);
        assertThat(resolveAgain.status()).isEqualTo(200);
        assertThat(resolveAgain.incidentStatus()).isEqualTo("RESOLVED");
        assertThat(dbUpdatedAt(id)).isEqualTo(resolvedAt);

        // 종결 건을 확인됨으로 되돌리기: 409, 상태·updated_at 그대로
        Res reopen = ack(id);
        assertOnly200Or409(List.of(reopen));
        assertThat(reopen.status()).isEqualTo(409);
        assertThat(((Number) reopen.path("$.details.incidentId")).longValue()).isEqualTo(id);
        assertThat(dbStatus(id)).isEqualTo("RESOLVED");
        assertThat(dbUpdatedAt(id)).isEqualTo(resolvedAt);

        // OPEN -> RESOLVED 직행
        long direct = newOpenIncident(marker);
        Res directResolve = resolve(direct);
        assertThat(directResolve.status()).isEqualTo(200);
        assertThat(dbStatus(direct)).isEqualTo("RESOLVED");

        assertNoRawText(marker, List.of(acked, ackAgain, resolved, resolveAgain, reopen, directResolve));
    }

    @Test
    void missingIdAndNonAdminCallersKeepTheExistingContract() throws Exception {
        long missing = count("select coalesce(max(id), 0) from risk_incident") + 100_000;
        for (String action : List.of("ack", "resolve")) {
            Res r = send("PATCH", "/api/incidents/" + missing + "/" + action, admin(), action);
            assertThat(r.status()).isEqualTo(404);
            assertThat(r.code()).isEqualTo("INCIDENT_NOT_FOUND");
        }

        long id = newOpenIncident(rawMarker());
        LocalDateTime before = dbUpdatedAt(id);
        Map<String, Integer> expected = new java.util.LinkedHashMap<>();
        expected.put(null, 401);
        expected.put(bearer("user", "ROLE_USER"), 403);
        expected.put(bearer("lawyer", "ROLE_LAWYER"), 403);
        for (var entry : expected.entrySet()) {
            for (String action : List.of("ack", "resolve")) {
                Res r = send("PATCH", "/api/incidents/" + id + "/" + action, entry.getKey(), action);
                assertThat(r.status()).isEqualTo(entry.getValue());
                assertThat(r.code()).isEqualTo(entry.getValue() == 401 ? "JWT_REQUIRED" : "JWT_FORBIDDEN");
            }
        }
        assertThat(dbStatus(id)).isEqualTo("OPEN");
        assertThat(dbUpdatedAt(id)).isEqualTo(before);
    }

    // ---------- B03-b 잠금 순서를 고정한 경쟁 ----------

    /**
     * 다른 트랜잭션이 행을 잠근 채 RESOLVED로 바꾸는 동안 ack가 들어오면,
     * ack는 잠금을 기다렸다가 커밋된 RESOLVED를 다시 읽고 409로 거부해야 한다(OPEN을 보고 ACKNOWLEDGED로 덮으면 역행).
     */
    @Test
    void ackWaitingBehindResolveSeesCommittedResolvedAndIsRejected() throws Exception {
        long id = newOpenIncident(rawMarker());
        LocalDateTime resolvedAt = holdLockAndChange(id, IncidentStatus.RESOLVED, () -> ack(id));

        assertThat(lastBlocked.status()).isEqualTo(409);
        assertOnly200Or409(List.of(lastBlocked));
        assertThat(dbStatus(id)).isEqualTo("RESOLVED");
        // 거부된 ack는 updated_at을 건드리지 않는다.
        assertClose(dbUpdatedAt(id), resolvedAt);
    }

    /** 반대 순서: ack가 먼저 잠그고 커밋, 기다리던 resolve는 ACKNOWLEDGED를 읽고 RESOLVED로 진행한다. */
    @Test
    void resolveWaitingBehindAckStillResolves() throws Exception {
        long id = newOpenIncident(rawMarker());
        LocalDateTime ackedAt = holdLockAndChange(id, IncidentStatus.ACKNOWLEDGED, () -> resolve(id));

        assertThat(lastBlocked.status()).isEqualTo(200);
        assertThat(lastBlocked.incidentStatus()).isEqualTo("RESOLVED");
        assertThat(dbStatus(id)).isEqualTo("RESOLVED");
        assertThat(dbUpdatedAt(id)).isAfterOrEqualTo(ackedAt.minus(CLOCK_TOLERANCE));
        assertClose(lastBlocked.updatedAt(), dbUpdatedAt(id));
    }

    private volatile Res lastBlocked;

    /**
     * 테스트 트랜잭션에서 행을 FOR UPDATE로 잠그고 상태를 바꾼 채로, HTTP 요청을 보내 그 요청이 실제로 막히는지 확인한 뒤 커밋한다.
     * 대기 시간은 짧게(약 300ms) 잡아 DB 잠금 대기 한도 안에 머무른다.
     */
    private LocalDateTime holdLockAndChange(long id, IncidentStatus next, Callable<Res> blockedCall) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            CompletableFuture<Future<Res>> started = new CompletableFuture<>();
            CompletableFuture<LocalDateTime> changedAt = new CompletableFuture<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                var incident = incidents.findForUpdate(id).orElseThrow();
                assertThat(incident.changeStatus(next)).isTrue();
                incidents.flush();
                changedAt.complete(incident.getUpdatedAt());
                Future<Res> future = pool.submit(blockedCall);
                started.complete(future);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                assertThat(future.isDone()).as("request must wait for the row lock").isFalse();
            });
            lastBlocked = started.get().get(30, TimeUnit.SECONDS);
            return changedAt.get();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- B03-b 동시 출발 ----------

    /** ack 1건과 resolve 1건을 같은 시점에 출발. 순서와 무관하게 최종 RESOLVED, 응답은 {200,200} 또는 {200,409}. */
    @Test
    void simultaneousAckAndResolveNeverRegressFromResolved() throws Exception {
        String marker = rawMarker();
        long incidentRowsBefore = count("select count(*) from risk_incident");
        long securityEventsBefore = count("select count(*) from security_event_history");
        int ackFirst = 0, resolveFirst = 0;
        List<Long> ids = new ArrayList<>();

        for (int round = 0; round < ROUNDS; round++) {
            long id = newOpenIncident(marker);
            ids.add(id);
            List<Res> results = parallel(List.of(() -> ack(id), () -> resolve(id)));
            assertOnly200Or409(results);
            assertNoRawText(marker, results);

            Res ackRes = results.get(0), resolveRes = results.get(1);
            assertThat(resolveRes.status()).isEqualTo(200);
            if (ackRes.status() == 200) ackFirst++; else resolveFirst++;

            assertThat(dbStatus(id)).isEqualTo("RESOLVED");
            LocalDateTime finalUpdatedAt = dbUpdatedAt(id);
            assertThat(finalUpdatedAt).isAfterOrEqualTo(dbCreatedAt(id));
            // 마지막 실제 변경은 resolve이므로 DB updated_at은 resolve 응답 값과 같다.
            assertClose(resolveRes.updatedAt(), finalUpdatedAt);
            if (ackRes.status() == 200) assertThat(ackRes.updatedAt()).isBeforeOrEqualTo(resolveRes.updatedAt());
        }

        // 행·보안 이벤트 중복 없음 (Incident 생성분만 늘어난다)
        assertThat(count("select count(*) from risk_incident")).isEqualTo(incidentRowsBefore + ROUNDS);
        assertThat(count("select count(*) from security_event_history")).isEqualTo(securityEventsBefore);
        assertAggregatesMatchDatabase(ids, marker);
        System.out.printf("B03-b simultaneous ack/resolve: ack-first=%d resolve-first=%d (rounds=%d)%n",
                ackFirst, resolveFirst, ROUNDS);
    }

    /**
     * 같은 Incident에 ack 4건·resolve 4건을 동시에. 실제 전이는 OPEN→ACK 최대 1번, →RESOLVED 정확히 1번이고
     * 나머지는 변경 없는 200이거나 409여야 한다. 같은 상태 응답들의 updatedAt이 서로 같으면 no-op가 시간을 바꾸지 않은 것이다.
     */
    @Test
    void ackResolveStormHasExactlyOneResolveTransition() throws Exception {
        String marker = rawMarker();
        long securityEventsBefore = count("select count(*) from security_event_history");
        List<Long> ids = new ArrayList<>();

        for (int round = 0; round < ROUNDS; round++) {
            long id = newOpenIncident(marker);
            ids.add(id);
            List<Callable<Res>> calls = new ArrayList<>();
            for (int i = 0; i < STORM_THREADS; i++) calls.add(i % 2 == 0 ? () -> ack(id) : () -> resolve(id));
            List<Res> results = parallel(calls);
            assertOnly200Or409(results);
            assertNoRawText(marker, results);

            assertThat(dbStatus(id)).isEqualTo("RESOLVED");
            LocalDateTime finalUpdatedAt = dbUpdatedAt(id);
            List<Res> resolved = results.stream().filter(r -> r.status() == 200 && r.action().equals("resolve")).toList();
            List<Res> acked = results.stream().filter(r -> r.status() == 200 && r.action().equals("ack")).toList();
            assertThat(resolved).hasSize(STORM_THREADS / 2);
            for (Res r : resolved) assertClose(r.updatedAt(), finalUpdatedAt);
            if (!acked.isEmpty()) {
                LocalDateTime firstAck = acked.get(0).updatedAt();
                for (Res r : acked) {
                    assertClose(r.updatedAt(), firstAck);
                    assertThat(r.updatedAt()).isBeforeOrEqualTo(finalUpdatedAt.plus(CLOCK_TOLERANCE));
                }
            }
        }

        assertThat(count("select count(*) from security_event_history")).isEqualTo(securityEventsBefore);
        assertAggregatesMatchDatabase(ids, marker);
    }

    /** 대시보드 요약 openIncidentCount와 Incident 목록의 상태가 커밋된 DB와 같은지, 원문이 섞이지 않는지. */
    private void assertAggregatesMatchDatabase(List<Long> ids, String marker) throws Exception {
        Res summary = send("GET", "/api/admin/dashboard/summary", admin(), "summary");
        assertThat(summary.status()).isEqualTo(200);
        assertThat(((Number) summary.path("$.openIncidentCount")).longValue())
                .isEqualTo(count("select count(*) from risk_incident where status = 'OPEN'"));

        Res list = send("GET", "/api/incidents", admin(), "list");
        assertThat(list.status()).isEqualTo(200);
        List<Number> listedIds = list.path("$[*].id");
        List<String> listedStatuses = list.path("$[*].status");
        for (int i = 0; i < listedIds.size(); i++) {
            assertThat(listedStatuses.get(i)).isEqualTo(dbStatus(listedIds.get(i).longValue()));
        }
        List<Long> listed = listedIds.stream().map(Number::longValue).toList();
        assertThat(listed).containsAll(ids);
        assertNoRawText(marker, List.of(summary, list));
    }
}
