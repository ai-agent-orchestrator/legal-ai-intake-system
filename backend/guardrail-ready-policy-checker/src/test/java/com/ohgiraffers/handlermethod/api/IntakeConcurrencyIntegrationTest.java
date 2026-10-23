package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B01-b / B02-c: 실제 동시 요청 검사.
 * MockMvc 순차 호출이 아니라 임의 포트로 띄운 서버에 여러 스레드가 시작 신호를 맞춰 HTTP 요청을 보낸다.
 * 테스트 트랜잭션을 쓰지 않으므로 각 요청은 실제 커밋/잠금/유니크 제약을 거친다.
 * 가상 자료만 쓰고 DB는 기본 프로필의 로컬 H2(인메모리)다. 로컬 MySQL로 확인하려면
 * 환경 변수 SPRING_PROFILES_ACTIVE=mysql 을 설정하고 같은 테스트를 실행한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "search.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class IntakeConcurrencyIntegrationTest {
    private static final int THREADS = 8;
    private static final int ROUNDS = 5;

    @LocalServerPort int port;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();

    private static Map<String, Object> intakeBody(String story) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", "qa-session-concurrency");
        body.put("inquiryPurpose", "절차 안내");
        body.put("caseType", "CIVIL");
        body.put("partyPosition", "원고");
        body.put("coreStatement", story);
        body.put("desiredNextStep", "절차 안내");
        body.put("documents", List.of("입금 내역", "카카오톡 대화"));
        return body;
    }

    record Res(int status, String body, String replayed, long elapsedMs) {
        long id() { return ((Number) JsonPath.read(body, "$.id")).longValue(); }
        String code() { return JsonPath.read(body, "$.code"); }
        <T> T path(String path) { return JsonPath.read(body, path); }
    }

    private String role(String user) {
        return user.startsWith("admin") ? "ROLE_ADMIN" : user.startsWith("lawyer") ? "ROLE_LAWYER" : "ROLE_USER";
    }

    private Res send(String method, String path, String user, String key, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + tokens.createToken(user, role(user)))
                .header("Content-Type", "application/json");
        if (key != null) builder.header("Idempotency-Key", key);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        long started = System.nanoTime();
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(response.statusCode(), response.body(),
                response.headers().firstValue("Idempotent-Replayed").orElse(null),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    /** n개의 요청을 스레드에 올려 둔 뒤 같은 시점에 출발시킨다. */
    private List<Res> parallel(int n, IntFunction<Callable<Res>> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Res>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<Res> task = call.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Res> results = new ArrayList<>();
            for (Future<Res> future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String newKey(String prefix) { return prefix + "-" + UUID.randomUUID(); }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long dbVersion(long intakeId) { return count("select version from legal_intake where id = ?", intakeId); }

    private String dbStatus(long intakeId) {
        return jdbc.queryForObject("select status from legal_intake where id = ?", String.class, intakeId);
    }

    private long memoCount(long intakeId) { return count("select count(*) from consultation_memo where intake_id = ?", intakeId); }

    private long newIntake() throws Exception {
        Res created = send("POST", "/api/intakes", "user", null, intakeBody("가상 문의: 빌려준 돈을 받지 못했습니다."));
        assertThat(created.status()).isEqualTo(201);
        return created.id();
    }

    private static Map<String, String> statuses(List<Res> results) {
        return results.stream().collect(Collectors.groupingBy(r -> r.status() + "", Collectors.counting()))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }

    // ===================== B01-b 화면03 문의 접수 =====================

    @Test
    void sameUserSameKeySameBodyInParallelStoresOneIntakeWithOneSetOfRelatedRows() throws Exception {
        long eventsBefore = count("select count(*) from intake_event");
        for (int round = 0; round < ROUNDS; round++) {
            String key = newKey("b01-same");
            Map<String, Object> body = intakeBody("가상 문의 " + round + ": 계약금 반환을 받지 못했습니다.");
            List<Res> results = parallel(THREADS, i -> () -> send("POST", "/api/intakes", "user", key, body));

            assertThat(results).as("round %d statuses %s", round, statuses(results)).allMatch(r -> r.status() == 201);
            Set<Long> ids = results.stream().map(Res::id).collect(Collectors.toSet());
            assertThat(ids).as("모든 응답이 같은 문의ID").hasSize(1);
            assertThat(results.stream().filter(r -> "false".equals(r.replayed()))).as("최초 저장 응답은 하나").hasSize(1);
            assertThat(results.stream().filter(r -> "true".equals(r.replayed()))).hasSize(THREADS - 1);

            long id = ids.iterator().next();
            assertThat(count("select count(*) from legal_intake where owner_username = 'user' and idempotency_key = ?", key)).isEqualTo(1);
            assertThat(count("select count(*) from intake_document where intake_id = ?", id)).as("관련 자료 중복 없음").isEqualTo(2);
            assertThat(count("select count(*) from lawyer_search_condition where intake_id = ?", id)).as("검색조건 중복 없음").isEqualTo(1);
            assertThat(count("select count(*) from ai_search_request where intake_id = ?", id)).isZero();
        }
        assertThat(count("select count(*) from intake_event")).as("접수는 이벤트를 만들지 않는다").isEqualTo(eventsBefore);
    }

    @Test
    void sameKeyDifferentBodiesInParallelSaveOnlyOneAndRejectTheOtherWith409() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String key = newKey("b01-diff");
            Map<String, Object> a = intakeBody("가상 문의 A " + round);
            Map<String, Object> b = intakeBody("가상 문의 B " + round);
            List<Res> results = parallel(THREADS, i -> () -> send("POST", "/api/intakes", "user", key, i % 2 == 0 ? a : b));

            assertThat(results).as("statuses %s", statuses(results)).allMatch(r -> r.status() == 201 || r.status() == 409);
            assertThat(count("select count(*) from legal_intake where owner_username = 'user' and idempotency_key = ?", key)).isEqualTo(1);
            long storedId = count("select id from legal_intake where owner_username = 'user' and idempotency_key = ?", key);
            List<Res> ok = results.stream().filter(r -> r.status() == 201).toList();
            List<Res> conflict = results.stream().filter(r -> r.status() == 409).toList();
            assertThat(ok).isNotEmpty().allMatch(r -> r.id() == storedId);
            assertThat(ok.stream().filter(r -> "false".equals(r.replayed()))).hasSize(1);
            // 한쪽 본문 그룹은 전부 성공, 다른 그룹은 전부 409
            assertThat(ok).hasSize(THREADS / 2);
            assertThat(conflict).hasSize(THREADS / 2).allMatch(r -> "IDEMPOTENCY_KEY_REUSED".equals(r.code()));
            assertThat(count("select count(*) from intake_document where intake_id = ?", storedId)).isEqualTo(2);
            assertThat(count("select count(*) from lawyer_search_condition where intake_id = ?", storedId)).isEqualTo(1);
        }
    }

    @Test
    void differentUsersWithTheSameKeyInParallelAreIsolated() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String key = newKey("b01-users");
            Map<String, Object> body = intakeBody("가상 문의 사용자 격리 " + round);
            List<Res> results = parallel(THREADS, i -> () -> send("POST", "/api/intakes", i % 2 == 0 ? "user" : "user2", key, body));

            assertThat(results).allMatch(r -> r.status() == 201);
            assertThat(count("select count(*) from legal_intake where idempotency_key = ?", key)).isEqualTo(2);
            long userId = count("select id from legal_intake where owner_username = 'user' and idempotency_key = ?", key);
            long user2Id = count("select id from legal_intake where owner_username = 'user2' and idempotency_key = ?", key);
            assertThat(userId).isNotEqualTo(user2Id);
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).id()).isEqualTo(i % 2 == 0 ? userId : user2Id);
            }
        }
    }

    /**
     * 유니크 충돌 경로를 확정적으로 실행한다: 다른 트랜잭션이 같은 (사용자, 키) 행을 넣고 커밋하지 않은 채 잡고 있으면
     * 요청은 INSERT에서 기다렸다가, 그 트랜잭션이 커밋된 뒤 유니크 충돌 -> 롤백 -> 새 트랜잭션에서 기존 문의를 조회해 돌려준다.
     */
    @Test
    void uniqueCollisionIsResolvedOnlyAfterTheWinningTransactionCommits(CapturedOutput output) throws Exception {
        String story = "가상 문의 충돌경로 비밀문장-" + UUID.randomUUID();
        Map<String, Object> body = intakeBody(story);
        long template = send("POST", "/api/intakes", "user", newKey("b01-template"), body).id();

        String heldKey = newKey("b01-held");
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CompletableFuture<Long> holder = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            copyIntakeWithKey(template, heldKey);
            Long heldId = jdbc.queryForObject("select id from legal_intake where idempotency_key = ?", Long.class, heldKey);
            inserted.countDown();
            await(release);
            return heldId;
        }));
        assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Res> request = CompletableFuture.supplyAsync(() -> call(() -> send("POST", "/api/intakes", "user", heldKey, body)));
        Thread.sleep(700);
        assertThat(request).as("커밋 전에는 응답하지 않는다").isNotDone();
        release.countDown();
        long heldId = holder.get(10, TimeUnit.SECONDS);
        Res res = request.get(30, TimeUnit.SECONDS);

        assertThat(res.status()).isEqualTo(201);
        assertThat(res.replayed()).isEqualTo("true");
        assertThat(res.id()).isEqualTo(heldId);
        assertThat(res.elapsedMs()).isGreaterThanOrEqualTo(600);
        assertThat(count("select count(*) from legal_intake where idempotency_key = ?", heldKey)).isEqualTo(1);
        // 요청 쪽에서 만들려던 관련 자료/검색조건은 함께 롤백되어 남지 않는다.
        assertThat(count("select count(*) from intake_document where intake_id = ?", heldId)).isZero();
        assertThat(count("select count(*) from lawyer_search_condition where intake_id = ?", heldId)).isZero();
        assertThat(count("select count(*) from intake_document d where not exists (select 1 from legal_intake i where i.id = d.intake_id)")).isZero();
        // 유니크 충돌 경로를 실제로 탔다는 증거(값 없이 문의ID만 기록).
        assertThat(output.getAll()).contains("resolved after unique-key collision intakeId=" + heldId);
        // 문의 원문과 토큰은 로그에 남지 않는다.
        assertThat(output.getAll()).doesNotContain(story).doesNotContain("Bearer ey");
    }

    @Test
    void collisionWithARolledBackTransactionSavesTheRequestNormally() throws Exception {
        Map<String, Object> body = intakeBody("가상 문의 롤백 경로 " + UUID.randomUUID());
        long template = send("POST", "/api/intakes", "user", newKey("b01-template"), body).id();
        String heldKey = newKey("b01-rollback");
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            copyIntakeWithKey(template, heldKey);
            inserted.countDown();
            await(release);
            status.setRollbackOnly();
        }));
        assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Res> request = CompletableFuture.supplyAsync(() -> call(() -> send("POST", "/api/intakes", "user", heldKey, body)));
        Thread.sleep(500);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        Res res = request.get(30, TimeUnit.SECONDS);

        assertThat(res.status()).isEqualTo(201);
        assertThat(res.replayed()).isEqualTo("false");
        assertThat(count("select count(*) from legal_intake where idempotency_key = ?", heldKey)).isEqualTo(1);
        assertThat(count("select count(*) from intake_document where intake_id = ?", res.id())).isEqualTo(2);
    }

    private void copyIntakeWithKey(long sourceId, String key) {
        jdbc.update("""
                insert into legal_intake (session_id, owner_username, inquiry_purpose, case_type, party_position, core_statement,
                    desired_next_step, dispute_area, client_guide_message, status, created_at, version, idempotency_key, request_hash)
                select session_id, owner_username, inquiry_purpose, case_type, party_position, core_statement,
                    desired_next_step, dispute_area, client_guide_message, status, created_at, 0, ?, request_hash
                from legal_intake where id = ?""", key, sourceId);
    }

    // ===================== B02-c 화면07 상태·메모·재배정 =====================

    @Test
    void twoAdminsWithTheSameVersionChangingToDifferentStatusesOneWinsOneGets409() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long id = newIntake();
            long v = dbVersion(id);
            List<Res> results = parallel(2, i -> () -> send("PATCH", "/api/intakes/" + id + "/status", i == 0 ? "admin" : "admin2",
                    null, Map.of("status", i == 0 ? "REVIEWED" : "NEED_MORE_INFO", "expectedVersion", v)));

            List<Res> ok = results.stream().filter(r -> r.status() == 200).toList();
            List<Res> conflict = results.stream().filter(r -> r.status() == 409).toList();
            assertThat(ok).as("statuses %s", statuses(results)).hasSize(1);
            assertThat(conflict).hasSize(1).allMatch(r -> "INTAKE_VERSION_CONFLICT".equals(r.code()));
            Res winner = ok.get(0);
            assertThat((Boolean) winner.path("$.changed")).isTrue();
            assertThat(dbVersion(id)).isEqualTo(v + 1);
            assertThat(((Number) winner.path("$.version")).longValue()).isEqualTo(dbVersion(id));
            assertThat(dbStatus(id)).isEqualTo(winner.path("$.status"));
            assertThat(((Number) conflict.get(0).path("$.details.currentVersion")).longValue()).isEqualTo(v + 1);
        }
    }

    @Test
    void parallelRetriesOfTheSameStatusChangeApplyOnceAndBumpTheVersionOnce() throws Exception {
        long id = newIntake();
        long v = dbVersion(id);
        List<Res> results = parallel(THREADS, i -> () -> send("PATCH", "/api/intakes/" + id + "/status", "admin", null,
                Map.of("status", "REVIEWED", "expectedVersion", v)));
        assertThat(results).allMatch(r -> r.status() == 200);
        assertThat(results.stream().filter(r -> Boolean.TRUE.equals(r.path("$.changed")))).hasSize(1);
        assertThat(results).allMatch(r -> ((Number) r.path("$.version")).longValue() == v + 1);
        assertThat(dbVersion(id)).isEqualTo(v + 1);
    }

    @Test
    void statusAndMemoWithTheSameVersionInParallelOneWinsOneGets409() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long id = newIntake();
            long v = dbVersion(id);
            String memoText = "가상 메모 " + round;
            List<Res> results = parallel(2, i -> () -> i == 0
                    ? send("PATCH", "/api/intakes/" + id + "/status", "admin", null, Map.of("status", "REVIEWED", "expectedVersion", v))
                    : send("POST", "/api/intakes/" + id + "/memo", "admin2", newKey("b02-memo"),
                    Map.of("content", memoText, "expectedVersion", v)));
            Res status = results.get(0);
            Res memo = results.get(1);
            assertThat(List.of(status.status(), memo.status())).as("statuses %s", statuses(results))
                    .containsExactlyInAnyOrder(status.status() == 200 ? 200 : 409, status.status() == 200 ? 409 : 201);
            assertThat(dbVersion(id)).isEqualTo(v + 1);
            if (status.status() == 200) {
                assertThat(dbStatus(id)).isEqualTo("REVIEWED");
                assertThat(memoCount(id)).isZero();
                assertThat(memo.code()).isEqualTo("INTAKE_VERSION_CONFLICT");
            } else {
                assertThat(dbStatus(id)).isEqualTo("SUBMITTED");
                assertThat(memoCount(id)).isEqualTo(1);
                assertThat(((Number) memo.path("$.intakeVersion")).longValue()).isEqualTo(v + 1);
                assertThat(status.code()).isEqualTo("INTAKE_VERSION_CONFLICT");
            }
        }
    }

    @Test
    void theSameMemoRequestArrivingInParallelIsStoredOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long id = newIntake();
            long v = dbVersion(id);
            String key = newKey("b02-memo-retry");
            Map<String, Object> memo = Map.of("content", "가상 메모: 자료 요청 " + round,
                    "followUpItems", List.of("REQUEST_DOCUMENTS"), "expectedVersion", v);
            List<Res> results = parallel(THREADS, i -> () -> send("POST", "/api/intakes/" + id + "/memo", "admin", key, memo));

            assertThat(results).as("statuses %s", statuses(results)).allMatch(r -> r.status() == 201);
            assertThat(results.stream().map(Res::id).collect(Collectors.toSet())).hasSize(1);
            assertThat(results.stream().filter(r -> "false".equals(r.replayed()))).hasSize(1);
            assertThat(memoCount(id)).isEqualTo(1);
            assertThat(dbVersion(id)).isEqualTo(v + 1);
            assertThat(results).allMatch(r -> ((Number) r.path("$.intakeVersion")).longValue() == v + 1);
        }
    }

    @Test
    void identicalKeylessMemosInParallelFallIntoTheResendWindowOnce() throws Exception {
        long id = newIntake();
        List<Res> results = parallel(THREADS, i -> () -> send("POST", "/api/intakes/" + id + "/memo", "admin", null,
                Map.of("content", "가상 메모: 키 없는 재전송")));
        assertThat(results).allMatch(r -> r.status() == 201);
        assertThat(memoCount(id)).isEqualTo(1);
        assertThat(results.stream().filter(r -> "false".equals(r.replayed()))).hasSize(1);
    }

    @Test
    void memoKeysAreScopedToIntakeAndAuthor() throws Exception {
        long id = newIntake();
        send("PATCH", "/api/intakes/" + id + "/assignment", "admin", null, Map.of("assignedLawyerUsername", "lawyer"));
        Map<String, Object> memo = Map.of("content", "가상 메모: 같은 내용");

        // 서로 다른 키 + 같은 내용 = 별도 메모 두 건
        Res first = send("POST", "/api/intakes/" + id + "/memo", "admin", newKey("b02-k1"), memo);
        Res second = send("POST", "/api/intakes/" + id + "/memo", "admin", newKey("b02-k2"), memo);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(memoCount(id)).isEqualTo(2);

        // 다른 작성자가 같은 키를 쓰면 남의 메모를 돌려주지 않고 409
        String shared = newKey("b02-shared");
        Res mine = send("POST", "/api/intakes/" + id + "/memo", "admin", shared, Map.of("content", "가상 메모: 관리자 작성"));
        Res other = send("POST", "/api/intakes/" + id + "/memo", "lawyer", shared, Map.of("content", "가상 메모: 관리자 작성"));
        assertThat(mine.status()).isEqualTo(201);
        assertThat((String) mine.path("$.createdBy")).isEqualTo("admin");
        assertThat(other.status()).isEqualTo(409);
        assertThat(other.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        // 같은 키라도 다른 문의에서는 별도 범위
        long otherIntake = newIntake();
        Res elsewhere = send("POST", "/api/intakes/" + otherIntake + "/memo", "admin", shared, Map.of("content", "가상 메모: 관리자 작성"));
        assertThat(elsewhere.status()).isEqualTo(201);
        assertThat(elsewhere.replayed()).isEqualTo("false");
        assertThat(memoCount(otherIntake)).isEqualTo(1);
    }

    @Test
    void assignmentResponseCarriesTheCommittedVersion() throws Exception {
        long id = newIntake();
        long v = dbVersion(id);
        Res assigned = send("PATCH", "/api/intakes/" + id + "/assignment", "admin", null, Map.of("assignedLawyerUsername", "lawyer"));
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(dbVersion(id)).isEqualTo(v + 1);
        assertThat(((Number) assigned.path("$.version")).longValue()).isEqualTo(dbVersion(id));
        // 같은 담당자로 다시 배정하면 변경 없음 -> version 유지
        Res again = send("PATCH", "/api/intakes/" + id + "/assignment", "admin", null, Map.of("assignedLawyerUsername", "lawyer"));
        assertThat(((Number) again.path("$.version")).longValue()).isEqualTo(v + 1);
        assertThat(dbVersion(id)).isEqualTo(v + 1);
    }

    /** ADMIN 재배정과 이전 담당 LAWYER의 메모·상태 저장이 경합할 때 결과가 잠금 순서 하나로 설명되어야 한다. */
    @Test
    void reassignmentRacingThePreviousLawyersWritesIsSerialized() throws Exception {
        int lawyerWon = 0, adminWon = 0;
        for (int round = 0; round < ROUNDS * 2; round++) {
            long id = newIntake();
            send("PATCH", "/api/intakes/" + id + "/assignment", "admin", null, Map.of("assignedLawyerUsername", "lawyer"));
            long v = dbVersion(id);
            String memoKey = newKey("b02-race");
            List<Res> results = parallel(3, i -> () -> switch (i) {
                case 0 -> send("PATCH", "/api/intakes/" + id + "/assignment", "admin", null, Map.of("assignedLawyerUsername", "lawyer2"));
                case 1 -> send("POST", "/api/intakes/" + id + "/memo", "lawyer", memoKey, Map.of("content", "가상 메모: 이전 담당자"));
                default -> send("PATCH", "/api/intakes/" + id + "/status", "lawyer", null, Map.of("status", "REVIEWED"));
            });
            Res assign = results.get(0), memo = results.get(1), status = results.get(2);
            assertThat(assign.status()).isEqualTo(200);
            assertThat(memo.status()).as("memo %s", memo.body()).isIn(201, 403);
            assertThat(status.status()).as("status %s", status.body()).isIn(200, 403);

            assertThat(jdbc.queryForObject("select assigned_lawyer_username from legal_intake where id = ?", String.class, id))
                    .isEqualTo("lawyer2");
            long memoSaved = memoCount(id);
            assertThat(memoSaved).isEqualTo(memo.status() == 201 ? 1 : 0);
            assertThat(dbStatus(id)).isEqualTo(status.status() == 200 ? "REVIEWED" : "SUBMITTED");
            long expected = v + 1 + memoSaved + (status.status() == 200 ? 1 : 0);
            assertThat(dbVersion(id)).isEqualTo(expected);
            if (memo.status() == 201) {
                assertThat((String) memo.path("$.createdBy")).isEqualTo("lawyer");
                lawyerWon++;
            } else {
                adminWon++;
            }
        }
        System.out.printf("[B02-c] reassignment race: lawyer memo before reassignment=%d, after (403)=%d%n", lawyerWon, adminWon);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static <T> T call(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
