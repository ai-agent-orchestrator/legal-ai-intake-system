package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.repository.ConsultationMemoRepository;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class BackendDraftApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired com.ohgiraffers.handlermethod.security.JwtTokenProvider tokens;
    @Autowired LegalIntakeRepository intakeRepository;
    @Autowired ConsultationMemoRepository memoRepository;

    @Test
    void classificationPreviewDoesNotPersistAnIntake() throws Exception {
        long before = intakeRepository.count();
        mvc.perform(post("/api/intakes/classify-preview").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseType":"UNKNOWN","coreStatement":"돈을 빌려줬는데 못 받고 있습니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseType").value("CIVIL"))
                .andExpect(jsonPath("$.disputeArea").value("대여금/금전 반환 분쟁"))
                .andExpect(jsonPath("$.classificationMethod").value("RULE_BASED"))
                .andExpect(jsonPath("$.persisted").value(false));
        assertThat(intakeRepository.count()).isEqualTo(before);
    }

    @Test
    void classificationPreviewAccepts100CharactersAndRejects101() throws Exception {
        long before = intakeRepository.count();
        String statement = "민사" + "가".repeat(98);
        mvc.perform(post("/api/intakes/classify-preview").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseType":"CIVIL","coreStatement":"%s"}
                                """.formatted(statement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificationMethod").value("RULE_BASED"))
                .andExpect(jsonPath("$.persisted").value(false));
        mvc.perform(post("/api/intakes/classify-preview").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseType":"CIVIL","coreStatement":"%s"}
                                """.formatted(statement + "가")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.coreStatement").exists());
        assertThat(intakeRepository.count()).isEqualTo(before);
    }

    @Test
    void intakeStoryAccepts100CharactersAndRejects101WithoutSavingAnotherRecord() throws Exception {
        long before = intakeRepository.count();
        String statement = "돈을 빌려줬는데 못받았어요." + "가".repeat(100 - "돈을 빌려줬는데 못받았어요.".length());
        String template = """
                {"inquiryPurpose":"절차 안내","caseType":"CIVIL","partyPosition":"원고",
                 "coreStatement":"%s","desiredNextStep":"절차 안내","documents":[]}
                """;
        mvc.perform(post("/api/intakes").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(statement)))
                .andExpect(status().isCreated());
        assertThat(intakeRepository.count()).isEqualTo(before + 1);
        mvc.perform(post("/api/intakes").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(statement + "가")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.coreStatement").exists());
        assertThat(intakeRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void blankPreviewAndInvalidStatusReturnFieldErrors() throws Exception {
        mvc.perform(post("/api/intakes/classify-preview").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coreStatement\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.coreStatement").exists());
        long id = createIntake("invalid-status", "서울", "상담");
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
        assertThat(intakeRepository.findById(id).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void statusChangesAreVisibleOnDetailAndClosedIntakesCannotReopen() throws Exception {
        long id = createIntake("status-flow", "서울", "상담");
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.changed").value(true));
        mvc.perform(get("/api/intakes/{id}", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void sameStatusIsIdempotentAndReturningToSubmittedIsRejected() throws Exception {
        long id = createIntake("idempotent-status", "서울", "상담");
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/intakes/{id}/status", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void memoIsStoredAndMaskedOnCreateAndDetail() throws Exception {
        long id = createIntake("memo-flow", "서울", "상담");
        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"연락처 010-1234-5678로 자료 요청",
                                 "followUpItems":["REQUEST_DOCUMENTS","REQUEST_DOCUMENTS"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentMasked").value("연락처 010-1***-****로 자료 요청"))
                .andExpect(jsonPath("$.followUpItems", hasSize(1)));
        assertThat(memoRepository.findByIntakeIdOrderByCreatedAtDescIdDesc(id)).hasSize(1);
        mvc.perform(get("/api/intakes/{id}", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationMemos[0].contentMasked").value("연락처 010-1***-****로 자료 요청"));
    }

    @Test
    void missingIntakeAndInvalidMemoDoNotCreateOrphanRecords() throws Exception {
        long before = memoRepository.count();
        mvc.perform(post("/api/intakes/{id}/memo", Long.MAX_VALUE).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"검토 메모\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/intakes/{id}/status", Long.MAX_VALUE).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isNotFound());
        long id = createIntake("invalid-memo", "서울", "상담");
        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/intakes/{id}/memo", id).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"검토\",\"followUpItems\":[\"UNKNOWN_ACTION\"]}"))
                .andExpect(status().isBadRequest());
        assertThat(memoRepository.count()).isEqualTo(before);
    }

    @Test
    void funnelDeduplicatesIntakesAndDoesNotInventUntrackedStages() throws Exception {
        long first = createIntake("funnel-first", "서울", "상담");
        createIntake("funnel-second", null, "상담");
        createSearch(first);
        createSearch(first);
        mvc.perform(patch("/api/intakes/{id}/status", first).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONSULTATION_READY\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard/marketing-funnel").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedIntakeCount").value(2))
                .andExpect(jsonPath("$.searchRequestCount").value(2))
                .andExpect(jsonPath("$.searchRequestedIntakeCount").value(1))
                .andExpect(jsonPath("$.consultationReadyIntakeCount").value(1))
                .andExpect(jsonPath("$.caseTypeDistribution.CIVIL").value(2))
                .andExpect(jsonPath("$.regionDistribution['지역 미정']").value(1))
                .andExpect(jsonPath("$.stages[0].measurement").value("REPORTED_EVENT"))
                .andExpect(jsonPath("$.stages[0].count").value(0))
                .andExpect(jsonPath("$.dropOffMeasured").value(false));
    }

    @Test
    void emptyDatabaseReturnsZeroMeasuredCountsAndEmptyDistributions() throws Exception {
        mvc.perform(get("/api/admin/dashboard/marketing-funnel").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedIntakeCount").value(0))
                .andExpect(jsonPath("$.searchRequestedIntakeCount").value(0))
                .andExpect(jsonPath("$.caseTypeDistribution").isEmpty())
                .andExpect(jsonPath("$.stages[0].measurement").value("REPORTED_EVENT"))
                .andExpect(jsonPath("$.stages[0].count").value(0));
    }

    @Test
    void fullLengthSearchPurposeCanBeInheritedAndResultSnapshotCanBeReadBack() throws Exception {
        String purpose = "p".repeat(300);
        long id = createIntake("long-purpose", "서울", purpose);
        long searchId = createSearch(id);
        mvc.perform(post("/api/intakes/search-requests/{id}/results", searchId).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType":"PUBLIC_WEB","title":"예시 결과","url":"https://example.com/lawyer"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.results", hasSize(1)));
        mvc.perform(get("/api/intakes/search-requests/{id}", searchId).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchPurpose").value(purpose))
                .andExpect(jsonPath("$.results[0].displayOrder").value(1));
    }

    private long createIntake(String sessionId, String region, String purpose) throws Exception {
        MvcResult result = mvc.perform(post("/api/intakes").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","inquiryPurpose":"필요한 자료 확인","caseType":"CIVIL",
                                 "partyPosition":"원고","coreStatement":"빌려준 돈을 못 받고 있습니다.",
                                 "desiredNextStep":"상담","preferredRegion":%s,"lawyerSearchPurpose":"%s",
                                 "documents":["입금 내역","입금 내역"," "]}
                                """.formatted(sessionId, region == null ? "null" : "\"" + region + "\"", purpose)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selectedDocuments", hasSize(1)))
                .andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
    }

    private long createSearch(long intakeId) throws Exception {
        MvcResult result = mvc.perform(post("/api/intakes/{id}/search-requests", intakeId).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
    }
}
