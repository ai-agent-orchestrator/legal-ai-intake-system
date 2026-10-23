package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import com.ohgiraffers.handlermethod.repository.LawyerSearchConditionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class IntakeCreationAuthenticationTest {
    @Autowired MockMvc mvc;
    @Autowired LegalIntakeRepository intakes;
    @Autowired JwtTokenProvider tokens;
    @Autowired LawyerSearchConditionRepository conditions;

    private static final String BODY = """
            {"inquiryPurpose":"절차 안내","caseType":"CIVIL","partyPosition":"검수용 의뢰인",
             "coreStatement":"검수용 계약 문의","desiredNextStep":"검수용 절차 확인","documents":[]}
            """;

    @Test
    void noAuthorizationCannotCreateAnUnownedRecord() throws Exception {
        long before = intakes.count();
        mvc.perform(post("/api/intakes").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        assertThat(intakes.count()).isEqualTo(before);
    }

    @Test
    void validJwtPreservesTheOwner() throws Exception {
        var result = mvc.perform(post("/api/intakes").header("Authorization", "Bearer " + tokens.createToken("user", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn();
        long id = JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
        assertThat(intakes.findById(id).orElseThrow().getOwnerUsername()).isEqualTo("user");
    }

    @Test
    void invalidCredentialsCannotSilentlyCreateAnAnonymousRecord() throws Exception {
        long before = intakes.count();
        for (String authorization : new String[]{"Bearer invalid-token", "Bearer ", "Basic dXNlcjp3cm9uZw=="}) {
            mvc.perform(post("/api/intakes").header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
            assertThat(intakes.count()).isEqualTo(before);
        }
    }

    @Test
    void screen03OptionalFieldsAndSearchPreferencesArePersistedWithTheGeneratedId() throws Exception {
        String body = """
                {"inquiryPurpose":"문서 검토","caseType":"FAMILY","partyPosition":"신청인",
                 "coreStatement":"개발 검수용 가상 가사 문의","desiredNextStep":"자료 확인",
                 "clientName":"가상 검수인","clientPhone":"010-0000-0000","incidentDate":"2024-02-29",
                 "counterpart":"가상 상대방","amountOrDamage":"500만원","currentStage":"상담 전 / 준비 중",
                 "preferredRegion":"서울특별시","officeType":"법무법인","experienceLevel":"10년 이상",
                 "publicCareerPreference":"상관없음","consultationStyle":"전화 상담",
                 "lawyerSearchPurpose":"개발 검수용 상담 목적","documents":["계약서","문자·대화"]}
                """;
        var result = mvc.perform(post("/api/intakes").header("Authorization", "Bearer " + tokens.createToken("user", "ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        long id = JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
        var saved = intakes.findById(id).orElseThrow();
        assertThat(saved.getCaseType()).isEqualTo("FAMILY");
        assertThat(saved.getInquiryPurpose()).isEqualTo("문서 검토");
        assertThat(saved.getClientName()).isEqualTo("가상 검수인");
        assertThat(saved.getClientPhone()).isEqualTo("010-0000-0000");
        assertThat(saved.getIncidentDate()).isEqualTo("2024-02-29");
        assertThat(saved.getCounterpart()).isEqualTo("가상 상대방");
        assertThat(saved.getAmountOrDamage()).isEqualTo("500만원");
        assertThat(saved.getCurrentStage()).isEqualTo("상담 전 / 준비 중");
        assertThat(saved.getPreferredRegion()).isEqualTo("서울특별시");
        assertThat(saved.getLawyerSearchPurpose()).isEqualTo("개발 검수용 상담 목적");
        var condition = conditions.findFirstByIntakeIdOrderByIdDesc(id).orElseThrow();
        assertThat(condition.getIntakeId()).isEqualTo(id);
        assertThat(condition.getOfficeType()).isEqualTo("법무법인");
        assertThat(condition.getExperienceLevel()).isEqualTo("10년 이상");
        assertThat(condition.getPublicCareerPreference()).isEqualTo("상관없음");
        assertThat(condition.getConsultationStyle()).isEqualTo("전화 상담");
    }
}
