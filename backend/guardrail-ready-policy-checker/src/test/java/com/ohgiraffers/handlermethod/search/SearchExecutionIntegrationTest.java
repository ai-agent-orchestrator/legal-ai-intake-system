package com.ohgiraffers.handlermethod.search;

import com.ohgiraffers.handlermethod.cost.AiCostControlService;
import com.ohgiraffers.handlermethod.dto.*;
import com.ohgiraffers.handlermethod.guardrail.*;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP adapters against loopback provider fixtures. No paid external services. */
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "spring.datasource.url=jdbc:h2:mem:p03;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "llm.api-key=fixture-llm-key", "llm.request-timeout-ms=5000", "llm.cost.monthly-budget-usd=1000",
        "llm.cost.daily-budget-usd=1000", "llm.cost.session-budget-usd=1000", "search.execution-timeout-ms=2500",
        "search.naver.client-id=fixture-id", "search.naver.client-secret=fixture-secret", "search.max-results=3"})
@AutoConfigureMockMvc
class SearchExecutionIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpServer PROVIDERS = providers();
    static final List<String> ORDER = new CopyOnWriteArrayList<>(), PROMPTS = new CopyOnWriteArrayList<>();
    static final AtomicInteger SEARCHES = new AtomicInteger(), QUERIES = new AtomicInteger(), EXPLANATIONS = new AtomicInteger();
    static volatile String mode = "normal";
    static volatile CountDownLatch held = new CountDownLatch(0);
    static final AtomicBoolean failExplanation = new AtomicBoolean();
    static volatile String searchQuery, searchHeader;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("llm.base-url", () -> "http://127.0.0.1:" + PROVIDERS.getAddress().getPort());
        registry.add("search.naver.base-url", () -> "http://127.0.0.1:" + PROVIDERS.getAddress().getPort());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider tokens;
    @Autowired LegalIntakeRecordService records;
    @Autowired AiCostControlService cost;
    @Autowired SearchExecutionStore store;
    @Autowired SearchExecutionService execution;
    @Autowired SearchLlmService llm;
    @Autowired com.ohgiraffers.handlermethod.service.IntakeAccessService access;
    @Autowired ExternalSearchClient external;
    @MockitoBean InputGuardrailClient input;
    @MockitoBean OutputGuardrailClient output;

    @BeforeEach void reset() {
        mode = "normal"; held.countDown(); held = new CountDownLatch(0); failExplanation.set(false);
        ORDER.clear(); PROMPTS.clear(); SEARCHES.set(0); QUERIES.set(0); EXPLANATIONS.set(0);
        when(input.check(anyString())).thenAnswer(call -> new InputGuardrailResult("PASSED", call.getArgument(0), "INPUT", new GuardrailUsage(0, 0)));
        when(output.check(anyString())).thenAnswer(call -> new OutputGuardrailResult("PASSED", call.getArgument(0), "OUTPUT", new GuardrailUsage(0, 0)));
    }
    @AfterEach void release() { held.countDown(); }
    @AfterAll static void stop() { PROVIDERS.stop(0); }

    @Test void fullPipelineUsesConditionsAndStoresSourceAndSeparateExplanationUnderExistingIds() throws Exception {
        var request = create("user", "KBA_NAVER_PUBLIC_WEB");
        start(request.id(), "user", false, 202);
        var done = terminal(request.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.id()).isEqualTo(request.id()); assertThat(done.intakeId()).isEqualTo(request.intakeId());
        assertThat(done.conditionId()).isEqualTo(request.conditionId());
        assertThat(done.searchQuery()).isEqualTo("서울 민사 대여금 변호사");
        assertThat(done.results()).hasSize(2);
        assertThat(done.results().getFirst().url()).isEqualTo("https://law.example.org/loan");
        assertThat(done.results().getFirst().title()).isEqualTo("대여금 & 민사 안내");
        assertThat(done.results().getFirst().snippet()).doesNotContain("<", "010-1234-5678");
        assertThat(done.results()).allSatisfy(result -> {
            assertThat(result.searchRequestId()).isEqualTo(request.id());
            assertThat(result.sourceType()).isEqualTo("NAVER_WEB");
            assertThat(result.explanation()).contains("원문 확인");
        });
        assertThat(done.execution().stage()).isEqualTo("FINISHED"); assertThat(done.execution().finishedAt()).isNotNull();
        assertThat(ORDER).containsExactly("query", "search", "search", "explain", "explain");
        assertThat(searchQuery).contains("query=서울 민사 대여금 변호사", "display=3", "start=1");
        assertThat(searchHeader).isEqualTo("fixture-id:fixture-secret");
        assertThat(String.join(" ", PROMPTS)).doesNotContain("홍길동", "김철수", "010-5555-9999", "raw-private", "비밀 검색 목적", "101동");
        assertThat(String.join(" ", PROMPTS)).contains("민사", "대여금", "서울");
        assertThat(cost.dailyCost(LocalDate.now()).reserved()).isZero();
        assertThat(cost.dailyCost(LocalDate.now()).reported()).isPositive();
    }

    @Test void optionalExecuteBodyDefaultsToFalseAndStillEnforcesOwnership() throws Exception {
        var request = create("other", "KBA_NAVER_PUBLIC_WEB");
        for (String body : List.of("", "{}", "{\"retryExplanation\":null}")) {
            mvc.perform(post(path(request.id())).header("Authorization", "Bearer " + tokens.createToken("user", "ROLE_USER"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(post(path(request.id())).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isAccepted());
        assertThat(terminal(request.id()).status()).isEqualTo("COMPLETED");
        assertThat(QUERIES).hasValue(1);
    }

    @Test void malformedExecuteFlagIsRejectedBeforeProviderCalls() throws Exception {
        var request = create("user", "KBA_NAVER_PUBLIC_WEB");
        mvc.perform(post(path(request.id())).header("Authorization", "Bearer " + tokens.createToken("user", "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"retryExplanation\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        assertThat(QUERIES).hasValue(0); assertThat(SEARCHES).hasValue(0);
    }

    @Test void authenticationOwnershipAndExistenceAreCheckedBeforeSpending() throws Exception {
        var request = create("other", "KBA_NAVER_PUBLIC_WEB");
        mvc.perform(post(path(request.id()))).andExpect(status().isUnauthorized());
        start(request.id(), "user", false, 403); start(Long.MAX_VALUE, "user", false, 404);
        start(request.id(), "admin", false, 202); assertThat(terminal(request.id()).status()).isEqualTo("COMPLETED");
    }

    @Test void missingConfigurationDoesNotClaimJobOrSpend() throws Exception {
        var request = create("user", "KBA_NAVER_PUBLIC_WEB");
        var disabled = new SearchExecutionService(store, access, records, external, llm, true, "", 5000);
        try {
            var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("user", "", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> disabled.execute(request.id(), false, auth))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("503");
            assertThat(records.findSearchRequest(request.id(), asUser("admin")).status()).isEqualTo("PREVIEW_READY");
            assertThat(records.findSearchRequest(request.id(), asUser("admin")).execution()).isNull(); assertThat(QUERIES).hasValue(0);
        } finally { disabled.shutdown(); }
    }

    @Test void unsupportedPolicyDoesNotCallProviders() throws Exception {
        var request = create("user", "UNSUPPORTED"); start(request.id(), "user", false, 422);
        assertThat(QUERIES).hasValue(0); assertThat(SEARCHES).hasValue(0);
    }

    @Test void duplicateExecutionAndConcurrentManualWritesAreControlled() throws Exception {
        held = new CountDownLatch(1); mode = "hold-query";
        var request = create("user", "KBA_NAVER_PUBLIC_WEB");
        start(request.id(), "user", false, 202); waitFor(() -> QUERIES.get() == 1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var calls = pool.invokeAll(List.<Callable<Void>>of(() -> { start(request.id(), "user", false, 202); return null; }, () -> { start(request.id(), "user", false, 202); return null; }));
            for (var call : calls) call.get();
        }
        mvc.perform(post("/api/intakes/search-requests/{id}/results", request.id()).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceType\":\"PUBLIC_WEB\",\"title\":\"manual\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isConflict());
        held.countDown(); assertThat(terminal(request.id()).status()).isEqualTo("COMPLETED");
        start(request.id(), "user", false, 202); assertThat(QUERIES).hasValue(1); assertThat(SEARCHES).hasValue(2);
    }

    @Test void emptySearchIsExplicitAndSkipsExplanations() throws Exception {
        mode = "empty"; var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id()); assertThat(done.status()).isEqualTo("NO_RESULTS");
        assertThat(done.results()).isEmpty(); assertThat(EXPLANATIONS).hasValue(0);
    }

    @Test void mixedSourcesStayUnderTheSameRequestWithOnlyThreeExplanations() throws Exception {
        mode = "mixed"; var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.results()).hasSize(3).allSatisfy(result -> assertThat(result.searchRequestId()).isEqualTo(request.id()));
        assertThat(done.results()).extracting(SearchResultSnapshotResponse::sourceType)
                .containsExactly("KBA_PUBLIC_WEB", "NAVER_WEB", "KBA_PUBLIC_WEB");
        assertThat(done.execution().sourceWarningCode()).isNull();
        assertThat(QUERIES).hasValue(1); assertThat(SEARCHES).hasValue(2); assertThat(EXPLANATIONS).hasValue(3);
        mvc.perform(get("/api/intakes/search-requests/{id}", request.id()).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].sourceType").value("KBA_PUBLIC_WEB"))
                .andExpect(jsonPath("$.results[1].sourceType").value("NAVER_WEB"));
    }

    @Test void generalSearchFailureKeepsOfficialResultsAndPersistsSourceWarning() throws Exception {
        mode = "general-error"; var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.results()).hasSize(2).allSatisfy(result -> assertThat(result.sourceType()).isEqualTo("KBA_PUBLIC_WEB"));
        mvc.perform(get("/api/intakes/search-requests/{id}", request.id()).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.sourceWarningCode").value("GENERAL_SEARCH_UNAVAILABLE"));
    }

    @Test void oneFailedSearchAndOneEmptySearchMustNotReportNoResults() throws Exception {
        mode = "empty-plus-error"; var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id());
        assertThat(done.status()).isEqualTo("FAILED");
        assertThat(done.execution().errorCode()).isEqualTo("SEARCH_PROVIDER_ERROR");
        assertThat(done.results()).isEmpty(); assertThat(EXPLANATIONS).hasValue(0);
    }

    @Test void searchFailureRetainsGeneratedQueryAndDoesNotInventResults() throws Exception {
        mode = "search-error"; var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id()); assertThat(done.status()).isEqualTo("FAILED");
        assertThat(done.execution().errorCode()).isEqualTo("SEARCH_PROVIDER_ERROR"); assertThat(done.results()).isEmpty();
        assertThat(done.searchQuery()).contains("서울"); assertThat(EXPLANATIONS).hasValue(0);
    }

    @Test void invalidLlmQueryCannotReachSearchProvider() throws Exception {
        mode = "invalid-query"; var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id()); assertThat(done.execution().errorCode()).isEqualTo("INVALID_QUERY"); assertThat(SEARCHES).hasValue(0);
    }

    @Test void snapshotsSurviveExplanationFailureAndExplicitRetryDoesNotSearchAgain() throws Exception {
        mode = "kba-error"; failExplanation.set(true); var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var partial = terminal(request.id()); assertThat(partial.status()).isEqualTo("PARTIAL"); assertThat(partial.results()).hasSize(2);
        assertThat(partial.results().getFirst().explanation()).isNull();
        start(request.id(), "user", false, 202); assertThat(EXPLANATIONS).hasValue(1);
        start(request.id(), "user", true, 202); var done = terminal(request.id());
        assertThat(done.status()).isEqualTo("COMPLETED"); assertThat(SEARCHES).hasValue(2); assertThat(QUERIES).hasValue(1);
        assertThat(done.results().stream().map(SearchResultSnapshotResponse::id).toList())
                .isEqualTo(partial.results().stream().map(SearchResultSnapshotResponse::id).toList());
        assertThat(done.execution().id()).isEqualTo(partial.execution().id());
        assertThat(partial.execution().sourceWarningCode()).isEqualTo("KBA_SEARCH_UNAVAILABLE");
        assertThat(done.execution().sourceWarningCode()).isEqualTo("KBA_SEARCH_UNAVAILABLE");
    }

    @Test void sourcesAreCommittedBeforeExplanationStarts() throws Exception {
        mode = "hold-explanation"; held = new CountDownLatch(1);
        var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        waitFor(() -> EXPLANATIONS.get() == 1);
        var mid = records.findSearchRequest(request.id(), asUser("admin")); assertThat(mid.status()).isEqualTo("RUNNING");
        assertThat(mid.execution().stage()).isEqualTo("EXPLANATION"); assertThat(mid.results()).hasSize(2);
        held.countDown(); assertThat(terminal(request.id()).status()).isEqualTo("COMPLETED");
    }

    @Test void outputGuardBlocksQueryAfterUsageIsSettled() throws Exception {
        when(output.check(anyString())).thenReturn(new OutputGuardrailResult("BLOCKED", "blocked", "OUTPUT", new GuardrailUsage(0, 0)));
        var before = cost.dailyCost(LocalDate.now()).reported();
        var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        assertThat(terminal(request.id()).execution().errorCode()).isEqualTo("SEARCH_GUARD_BLOCKED");
        assertThat(SEARCHES).hasValue(0); assertThat(cost.dailyCost(LocalDate.now()).reported()).isGreaterThan(before);
        assertThat(cost.dailyCost(LocalDate.now()).reserved()).isZero();
    }

    @Test void deadlineStopsPipelineAndLateResponseCannotOverwriteTerminalState() throws Exception {
        mode = "hold-query"; held = new CountDownLatch(1);
        var request = create("user", "KBA_NAVER_PUBLIC_WEB"); start(request.id(), "user", false, 202);
        var done = terminal(request.id()); assertThat(done.status()).isEqualTo("FAILED");
        assertThat(done.execution().errorCode()).isEqualTo("SEARCH_TIMEOUT"); held.countDown();
        waitFor(() -> cost.dailyCost(LocalDate.now()).reserved().signum() == 0);
        assertThat(SEARCHES).hasValue(0); assertThat(records.findSearchRequest(request.id(), asUser("admin")).status()).isEqualTo("FAILED");
    }

    private AiSearchRequestResponse create(String owner, String policy) {
        var dto = mapper.readValue("""
            {"sessionId":"raw-private","clientName":"홍길동","clientPhone":"010-5555-9999","inquiryPurpose":"절차 안내",
             "caseType":"CIVIL","partyPosition":"원고","counterpart":"김철수","coreStatement":"돈을 빌려줬는데 못 받았습니다.",
             "desiredNextStep":"절차 안내","preferredRegion":"서울특별시 101동","lawyerSearchPurpose":"비밀 검색 목적"}
            """, LegalIntakeCreateRequest.class);
        var intake = records.create(dto, asUser(owner));
        return records.createSearchRequest(intake.id(), new AiSearchRequestCreateRequest("비밀 검색 목적", policy), asUser(owner));
    }
    private org.springframework.security.core.Authentication asUser(String username) {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(username, null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(username.equals("admin") ? "ROLE_ADMIN" : "ROLE_USER")));
    }
    private String path(Long id) { return "/api/intakes/search-requests/" + id + "/execute"; }
    private void start(Long id, String username, boolean retry, int expected) throws Exception {
        mvc.perform(post(path(id)).header("Authorization", "Bearer " + tokens.createToken(username, username.equals("admin") ? "ROLE_ADMIN" : "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"retryExplanation\":" + retry + "}"))
                .andExpect(status().is(expected));
    }
    private AiSearchRequestResponse terminal(Long id) throws Exception {
        waitFor(() -> !List.of("RUNNING", "QUEUED").contains(records.findSearchRequest(id, asUser("admin")).status()));
        var result = mvc.perform(get("/api/intakes/search-requests/{id}", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), AiSearchRequestResponse.class);
    }
    private static void waitFor(java.util.function.BooleanSupplier ready) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!ready.getAsBoolean() && System.nanoTime() < end) Thread.sleep(20);
        assertThat(ready.getAsBoolean()).isTrue();
    }

    static HttpServer providers() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(r -> { var t = new Thread(r); t.setDaemon(true); return t; }));
            server.createContext("/v1/chat/completions", exchange -> {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); PROMPTS.add(body);
                    boolean query = body.contains("P03_QUERY");
                    if (query) { ORDER.add("query"); QUERIES.incrementAndGet(); } else { ORDER.add("explain"); EXPLANATIONS.incrementAndGet(); }
                    if ((query && mode.equals("hold-query")) || (!query && mode.equals("hold-explanation"))) held.await(6, TimeUnit.SECONDS);
                    if (!query && failExplanation.compareAndSet(true, false)) { send(exchange, 500, "{}"); return; }
                    String content = query ? (mode.equals("invalid-query") ? "{\"query\":\"https://unsafe.example.com\"}" : "{\"query\":\"서울 민사 대여금 변호사\"}")
                            : "민사 대여금 검색 조건과 관련된 공개 자료입니다. 제공된 요약으로 확인되지 않는 정보는 원문 확인 필요합니다.";
                    send(exchange, 200, JSON.writeValueAsString(Map.of("model", "fixture-model", "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content))), "usage", Map.of("prompt_tokens", 20, "completion_tokens", 12))));
                } catch (Exception ignored) { exchange.close(); }
            });
            server.createContext("/v1/search/webkr.json", exchange -> {
                ORDER.add("search"); SEARCHES.incrementAndGet();
                searchQuery = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                searchHeader = exchange.getRequestHeaders().getFirst("X-Naver-Client-Id") + ":" + exchange.getRequestHeaders().getFirst("X-Naver-Client-Secret");
                boolean targeted = searchQuery.contains("site:klaw.or.kr");
                if (mode.equals("search-error") || (targeted && (mode.equals("kba-error") || mode.equals("empty-plus-error")))
                        || (!targeted && mode.equals("general-error"))) {
                    send(exchange, 500, "{\"secret\":\"never expose\"}"); return;
                }
                var first = Map.of("title", "<b>대여금</b> &amp; 민사 안내", "link", "https://law.example.org/loan", "description", "민사 절차 안내 <script>bad()</script> 010-1234-5678");
                var second = Map.of("title", "지역별 법률 자료", "link", "https://public.example.org/civil", "description", "서울 민사 자료");
                var official = Map.of("title", "대한변협 공개 자료 · 모의 검수", "link", "https://www.klaw.or.kr/fixture?id=1", "description", "서울 민사 공개 자료");
                var otherOfficial = Map.of("title", "대한변협 공개 자료 2 · 모의 검수", "link", "https://www.klaw.or.kr/fixture?id=2", "description", "서울 민사 공개 자료");
                List<?> items = mode.equals("empty") || mode.equals("empty-plus-error") ? List.of()
                        : (mode.equals("mixed") || mode.equals("general-error")) && targeted ? List.of(official, otherOfficial)
                        : mode.equals("mixed") ? List.of(first, official, second)
                        : List.of(first, first, second, Map.of("title", "unsafe", "link", "javascript:alert(1)", "description", "bad"));
                send(exchange, 200, JSON.writeValueAsString(Map.of("items", items)));
            });
            server.start(); return server;
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length); try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }
}
