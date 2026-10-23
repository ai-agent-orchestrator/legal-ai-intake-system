package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import com.ohgiraffers.handlermethod.repository.ConsultationMemoRepository;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import com.ohgiraffers.handlermethod.repository.RiskIncidentRepository;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 화면03 재전송, 화면07 동시 수정·메모 재전송, 화면12 Incident 상태 규칙. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class ResubmitConflictIncidentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired LegalIntakeRepository intakes;
    @Autowired ConsultationMemoRepository memos;
    @Autowired AgentRiskHistoryRepository histories;
    @Autowired RiskIncidentRepository incidents;

    private static final Map<String, Object> INTAKE = Map.of("inquiryPurpose", "절차 안내", "caseType", "CIVIL",
            "partyPosition", "원고", "coreStatement", "빌려준 돈을 못 받았습니다.", "desiredNextStep", "절차 안내",
            "documents", List.of("입금 내역"));

    private String jwt(String name, String role) { return "Bearer " + tokens.createToken(name, role); }
    private String user() { return jwt("user", "ROLE_USER"); }
    private String admin() { return jwt("admin", "ROLE_ADMIN"); }
    private String body(Object value) { return json.writeValueAsString(value); }
    private long id(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MvcResult submitIntake(String auth, String key, Object request) throws Exception {
        var builder = post("/api/intakes").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body(request));
        if (key != null) builder.header("Idempotency-Key", key);
        return mvc.perform(builder).andReturn();
    }

    private long version(long intakeId) throws Exception {
        MvcResult result = mvc.perform(get("/api/intakes/{id}", intakeId).header("Authorization", admin()))
                .andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }

    // ---------- 화면03 ----------

    @Test
    void intakeRetriedWithSameKeyIsStoredOnceAndReturnsTheFirstResponse() throws Exception {
        long before = intakes.count();
        MvcResult first = submitIntake(user(), "intake-retry-0001", INTAKE);
        MvcResult retry = submitIntake(user(), "intake-retry-0001", INTAKE);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("false");
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(id(retry)).isEqualTo(id(first));
        assertThat(intakes.count()).isEqualTo(before + 1);
    }

    @Test
    void sameKeyWithDifferentContentIsRejectedWithoutSaving() throws Exception {
        submitIntake(user(), "intake-retry-0002", INTAKE);
        long before = intakes.count();
        var changed = new java.util.HashMap<>(INTAKE);
        changed.put("coreStatement", "다른 문의 내용입니다.");
        mvc.perform(post("/api/intakes").header("Authorization", user()).header("Idempotency-Key", "intake-retry-0002")
                        .contentType(MediaType.APPLICATION_JSON).content(body(changed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(intakes.count()).isEqualTo(before);
    }

    @Test
    void keysAreScopedPerUserAndMissingKeyKeepsPreviousBehaviour() throws Exception {
        long mine = id(submitIntake(user(), "shared-key-0003", INTAKE));
        long other = id(submitIntake(jwt("user2", "ROLE_USER"), "shared-key-0003", INTAKE));
        assertThat(other).isNotEqualTo(mine);

        long before = intakes.count();
        submitIntake(user(), null, INTAKE);
        submitIntake(user(), null, INTAKE);
        assertThat(intakes.count()).isEqualTo(before + 2);
    }

    @Test
    void malformedKeyIsRejectedBeforePersistence() throws Exception {
        long before = intakes.count();
        for (String key : List.of("short", "has space 1234", "x".repeat(101), "한글키는안됩니다1234")) {
            assertThat(submitIntake(user(), key, INTAKE).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(intakes.count()).isEqualTo(before);
    }

    @Test
    void fieldValuesThatShiftAcrossFieldBoundariesAreStillDifferentRequests() throws Exception {
        // record toString 기준으로는 두 요청이 같은 문자열이 된다: "counterpart=a, amountOrDamage=b, amountOrDamage=c"
        var first = new java.util.HashMap<String, Object>(INTAKE);
        first.put("counterpart", "a, amountOrDamage=b");
        first.put("amountOrDamage", "c");
        var shifted = new java.util.HashMap<String, Object>(INTAKE);
        shifted.put("counterpart", "a");
        shifted.put("amountOrDamage", "b, amountOrDamage=c");
        var a = json.readValue(body(first), com.ohgiraffers.handlermethod.dto.LegalIntakeCreateRequest.class);
        var b = json.readValue(body(shifted), com.ohgiraffers.handlermethod.dto.LegalIntakeCreateRequest.class);
        assertThat(a.toString()).isEqualTo(b.toString());

        assertThat(submitIntake(user(), "intake-shift-0001", first).getResponse().getStatus()).isEqualTo(201);
        long before = intakes.count();
        mvc.perform(post("/api/intakes").header("Authorization", user()).header("Idempotency-Key", "intake-shift-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body(shifted)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(intakes.count()).isEqualTo(before);
    }

    @Test
    void intakeStoredWithTheLegacyFingerprintIsStillReplayed() throws Exception {
        long first = id(submitIntake(user(), "intake-legacy-0001", INTAKE));
        var request = json.readValue(body(INTAKE), com.ohgiraffers.handlermethod.dto.LegalIntakeCreateRequest.class);
        var stored = intakes.findById(first).orElseThrow();
        String legacy = com.ohgiraffers.handlermethod.support.RequestFingerprint.sha256("intake-v1", request);
        stored.recordSubmission(stored.getIdempotencyKey(), legacy);
        intakes.flush();

        MvcResult retry = submitIntake(user(), "intake-legacy-0001", INTAKE);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(id(retry)).isEqualTo(first);
    }

    @Test
    void replayReturnsTheCurrentStateOfTheSameIntake() throws Exception {
        long first = id(submitIntake(user(), "intake-current-0001", INTAKE));
        mvc.perform(patch("/api/intakes/{id}/status", first).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "REVIEWED")))).andExpect(status().isOk());
        mvc.perform(post("/api/intakes").header("Authorization", user()).header("Idempotency-Key", "intake-current-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body(INTAKE)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value((int) first))
                .andExpect(jsonPath("$.status").value("REVIEWED"));
    }

    @Test
    void onlyTheIdempotencyUniqueConstraintIsTreatedAsAConcurrentResend() {
        var ours = new org.springframework.dao.DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException("dup", new java.sql.SQLException("Duplicate entry"),
                        "PUBLIC.UK_LEGAL_INTAKE_OWNER_IDEMPOTENCY_INDEX_A"));
        var mysql = new org.springframework.dao.DataIntegrityViolationException("x",
                new java.sql.SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'user-key' for key 'legal_intake.uk_legal_intake_owner_idempotency'"));
        var other = new org.springframework.dao.DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException("null", new java.sql.SQLException("NULL not allowed"),
                        "core_statement"));
        assertThat(LegalIntakeRecordController.isIdempotencyKeyCollision(ours)).isTrue();
        assertThat(LegalIntakeRecordController.isIdempotencyKeyCollision(mysql)).isTrue();
        assertThat(LegalIntakeRecordController.isIdempotencyKeyCollision(other)).isFalse();
    }

    // ---------- 화면07 ----------

    private long managedIntake() throws Exception {
        return id(submitIntake(user(), null, INTAKE));
    }

    @Test
    void statusChangeWithStaleVersionConflictsButSameStatusRetrySucceeds() throws Exception {
        long id = managedIntake();
        long v = version(id);

        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "REVIEWED", "expectedVersion", v))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.version").value((int) (v + 1)));

        // 통신 오류 후 같은 요청 재전송: 이미 반영됐으므로 변경 없음으로 성공
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "REVIEWED", "expectedVersion", v))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));

        // 오래된 화면에서 다른 상태로 변경: 충돌
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "NEED_MORE_INFO", "expectedVersion", v))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTAKE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.details.currentStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.details.currentVersion").value((int) (v + 1)))
                .andExpect(jsonPath("$.details.expectedVersion").value((int) v));
        assertThat(intakes.findById(id).orElseThrow().getStatus()).isEqualTo("REVIEWED");

        // expectedVersion을 보내지 않는 기존 호출은 그대로 동작
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "NEED_MORE_INFO"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    void closedIntakeTransitionConflictReturnsCurrentState() throws Exception {
        long id = managedIntake();
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "CLOSED")))).andExpect(status().isOk());
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "REVIEWED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTAKE_STATUS_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details.currentStatus").value("CLOSED"));
    }

    @Test
    void memoResentWithSameKeyIsStoredOnce() throws Exception {
        long id = managedIntake();
        long v = version(id);
        var memo = Map.of("content", "연락처 010-1234-5678로 자료 요청", "followUpItems", List.of("REQUEST_DOCUMENTS"),
                "expectedVersion", v);
        MvcResult first = mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                        .header("Idempotency-Key", "memo-retry-0001").contentType(MediaType.APPLICATION_JSON).content(body(memo)))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.intakeVersion").value((int) (v + 1))).andReturn();
        // 재전송은 버전 검사보다 먼저 판별되므로 이전 expectedVersion 그대로여도 성공
        MvcResult retry = mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                        .header("Idempotency-Key", "memo-retry-0001").contentType(MediaType.APPLICATION_JSON).content(body(memo)))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true")).andReturn();
        assertThat(id(retry)).isEqualTo(id(first));
        assertThat(memos.findByIntakeIdOrderByCreatedAtDescIdDesc(id)).hasSize(1);
        assertThat(version(id)).isEqualTo(v + 1);

        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                        .header("Idempotency-Key", "memo-retry-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("content", "다른 메모"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(memos.findByIntakeIdOrderByCreatedAtDescIdDesc(id)).hasSize(1);
    }

    @Test
    void identicalMemoWithoutKeyFromSameAuthorIsTreatedAsResend() throws Exception {
        long id = managedIntake();
        for (String content : List.of("자료 요청 완료", "  자료 요청 완료  ")) {
            mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", content))))
                    .andExpect(status().isCreated());
        }
        assertThat(memos.findByIntakeIdOrderByCreatedAtDescIdDesc(id)).hasSize(1);
        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "새로운 메모"))))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "false"));
        assertThat(memos.findByIntakeIdOrderByCreatedAtDescIdDesc(id)).hasSize(2);
    }

    @Test
    void memoAndStatusFromStaleScreenConflictAfterAnotherEdit() throws Exception {
        long id = managedIntake();
        long loaded = version(id);
        // 상담자 A가 메모 저장 -> 버전 증가
        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "A 메모", "expectedVersion", loaded))))
                .andExpect(status().isCreated());
        long before = memos.count();
        // 같은 버전으로 화면을 연 상담자 B의 메모·상태 변경은 충돌
        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "B 메모", "expectedVersion", loaded))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTAKE_VERSION_CONFLICT"));
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "REVIEWED", "expectedVersion", loaded))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.currentVersion").value((int) (loaded + 1)));
        assertThat(memos.count()).isEqualTo(before);
        assertThat(intakes.findById(id).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
    }

    // ---------- 화면12 ----------

    private RiskIncident openIncident() {
        var history = histories.saveAndFlush(AgentRiskHistory.create("가상 사건", "qa-tool", "QA_DECISION", 85,
                "HIGH", "POLICY_VIOLATION", "BLOCK_AND_ESCALATE", true, "qa-incident-trace"));
        return incidents.saveAndFlush(RiskIncident.open("QA", "HIGH", "QA_DECISION", 85,
                "BLOCK_AND_ESCALATE", history.getId(), "qa-incident-trace", "가상 자료"));
    }

    @Test
    void resolvedIncidentCannotGoBackToAcknowledged() throws Exception {
        RiskIncident incident = openIncident();
        mvc.perform(patch("/api/incidents/{id}/ack", incident.getId()).header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        mvc.perform(patch("/api/incidents/{id}/resolve", incident.getId()).header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(patch("/api/incidents/{id}/ack", incident.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_RESOLVED"))
                .andExpect(jsonPath("$.details.currentStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.details.requestedStatus").value("ACKNOWLEDGED"));
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void repeatedIncidentActionsAreNoOpsAndOpenCanBeResolvedDirectly() throws Exception {
        RiskIncident incident = openIncident();
        mvc.perform(patch("/api/incidents/{id}/ack", incident.getId()).header("Authorization", admin())).andExpect(status().isOk());
        var acknowledgedAt = incidents.findById(incident.getId()).orElseThrow().getUpdatedAt();
        mvc.perform(patch("/api/incidents/{id}/ack", incident.getId()).header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        assertThat(incidents.findById(incident.getId()).orElseThrow().getUpdatedAt()).isEqualTo(acknowledgedAt);

        RiskIncident other = openIncident();
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/incidents/{id}/resolve", other.getId()).header("Authorization", admin()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        }
    }

    @Test
    void incidentTransitionRulesAreDefinedOnTheStatus() {
        assertThat(IncidentStatus.OPEN.canChangeTo(IncidentStatus.ACKNOWLEDGED)).isTrue();
        assertThat(IncidentStatus.OPEN.canChangeTo(IncidentStatus.RESOLVED)).isTrue();
        assertThat(IncidentStatus.ACKNOWLEDGED.canChangeTo(IncidentStatus.RESOLVED)).isTrue();
        assertThat(IncidentStatus.ACKNOWLEDGED.canChangeTo(IncidentStatus.OPEN)).isFalse();
        assertThat(IncidentStatus.RESOLVED.canChangeTo(IncidentStatus.ACKNOWLEDGED)).isFalse();
        assertThat(IncidentStatus.RESOLVED.canChangeTo(IncidentStatus.OPEN)).isFalse();
        assertThat(IncidentStatus.RESOLVED.canChangeTo(IncidentStatus.RESOLVED)).isTrue();
    }
}
