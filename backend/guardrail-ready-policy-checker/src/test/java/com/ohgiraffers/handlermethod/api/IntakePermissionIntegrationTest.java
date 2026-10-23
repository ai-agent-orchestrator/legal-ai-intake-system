package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.dto.*;
import com.ohgiraffers.handlermethod.repository.*;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "search.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class IntakePermissionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired LegalIntakeRecordService records;
    @Autowired LegalIntakeRepository intakes;
    @Autowired ConsultationMemoRepository memos;
    @Autowired SearchResultSnapshotRepository results;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper mapper;
    static final String BODY = """
            {"inquiryPurpose":"절차 안내","caseType":"CIVIL","partyPosition":"원고",
             "coreStatement":"빌려준 돈을 받지 못했습니다.","desiredNextStep":"절차 안내","documents":[]}
            """;
    Authentication user(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of(new SimpleGrantedAuthority(
                name.equals("admin") ? "ROLE_ADMIN" : name.startsWith("lawyer") ? "ROLE_LAWYER" : "ROLE_USER")));
    }
    String jwt(String name) { return "Bearer " + tokens.createToken(name, user(name).getAuthorities().iterator().next().getAuthority()); }
    long create(String owner) { return records.create(mapper.readValue(BODY, LegalIntakeCreateRequest.class), user(owner)).id(); }
    void assign(long id, String lawyer) { records.assignLawyer(id, lawyer, user("admin")); }
    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String name, String body) {
        return request.header("Authorization", jwt(name)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test void anonymousCannotReadOrWritePersistedIntakes() throws Exception {
        long id=create("user"); var search=records.createSearchRequest(id,new AiSearchRequestCreateRequest("확인",null),user("user"));
        for(var request:List.of(get("/api/intakes"),get("/api/intakes/"+id),post("/api/intakes"),
                patch("/api/intakes/"+id+"/status"),post("/api/intakes/"+id+"/memo"),patch("/api/intakes/"+id+"/assignment"),
                post("/api/intakes/"+id+"/search-requests"),get("/api/intakes/search-requests/"+search.id()),
                post("/api/intakes/search-requests/"+search.id()+"/results"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/intakes/classify-preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseType\":\"CIVIL\",\"coreStatement\":\"돈을 못받았습니다\"}" )).andExpect(status().isOk());
    }

    @Test void ownerCannotReadInternalNotesEvenThroughListOrManageTheirOwnCase() throws Exception {
        long id=create("user"); assign(id,"lawyer");
        records.createMemo(id,new ConsultationMemoCreateRequest("내부 검토 전용",List.of("REQUEST_DOCUMENTS")),user("lawyer"));
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("user"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationMemos").doesNotExist()).andExpect(jsonPath("$.permissions.canManage").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("내부 검토 전용"))));
        mvc.perform(get("/api/intakes").header("Authorization",jwt("user"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].consultationMemos").doesNotExist());
        mvc.perform(json(post("/api/intakes/"+id+"/memo"),"user","{\"content\":\"임의 메모\"}")).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/intakes/"+id+"/status"),"user","{\"status\":\"REVIEWED\"}")).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/intakes/"+id+"/assignment"),"user","{\"assignedLawyerUsername\":\"lawyer2\"}")).andExpect(status().isForbidden());
        assertThat(memos.findByIntakeIdOrderByCreatedAtDescIdDesc(id)).hasSize(1);
        assertThat(intakes.findById(id).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
    }

    @Test void assignedLawyerCanManageButAnotherLawyerAndClientCannotRead() throws Exception {
        long id=create("user"); assign(id,"lawyer");
        for(String name:List.of("lawyer2","user2")) {
            mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt(name))).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/intakes/"+id+"/memo"),name,"{\"content\":\"금지\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(json(patch("/api/intakes/"+id+"/status"),"lawyer","{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/intakes/"+id+"/memo"),"lawyer","{\"content\":\"담당 검토\",\"createdBy\":\"admin\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy").value("lawyer"));
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("lawyer"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationMemos[0].createdBy").value("lawyer"))
                .andExpect(jsonPath("$.permissions.canManage").value(true)).andExpect(jsonPath("$.permissions.canAssign").value(false));
    }

    @Test void reassignmentAndRemovalRevokePreviousLawyerImmediately() throws Exception {
        long id=create("user"); assign(id,"lawyer");
        mvc.perform(json(patch("/api/intakes/"+id+"/assignment"),"admin","{\"assignedLawyerUsername\":\"lawyer2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedLawyerUsername").value("lawyer2"));
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("lawyer"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("lawyer2"))).andExpect(status().isOk());
        assign(id,null);
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("lawyer2"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("user"))).andExpect(status().isOk());
    }

    @Test void listScopeIsAppliedInDatabaseBeforeFiftyRowLimit() throws Exception {
        long mine=create("user"); assign(mine,"lawyer");
        for(int i=0;i<51;i++)create("user2");
        mvc.perform(get("/api/intakes").header("Authorization",jwt("user"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(mine));
        mvc.perform(get("/api/intakes").header("Authorization",jwt("lawyer"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(mine));
        mvc.perform(get("/api/intakes").header("Authorization",jwt("lawyer2"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/intakes").header("Authorization",jwt("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(50));
    }

    @Test void legacyAnonymousIntakeIsAdminOnlyAndCannotBeAssigned() throws Exception {
        long id=create("user"); intakes.flush(); jdbc.update("update legal_intake set owner_username=null where id=?",id);entityManager.clear();
        for(String name:List.of("user","lawyer"))mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt(name))).andExpect(status().isForbidden());
        mvc.perform(get("/api/intakes/"+id).header("Authorization",jwt("admin"))).andExpect(status().isOk());
        mvc.perform(json(patch("/api/intakes/"+id+"/assignment"),"admin","{\"assignedLawyerUsername\":\"lawyer\"}"))
                .andExpect(status().isConflict());
    }

    @Test void searchIdsAndDocumentIdsResolveBackToAuthorizedParent() throws Exception {
        long id=create("user");assign(id,"lawyer");
        var search=records.createSearchRequest(id,new AiSearchRequestCreateRequest("확인",null),user("user"));
        var file = new MockMultipartFile("file","test.txt","text/plain","test".getBytes());
        var upload=mvc.perform(multipart("/api/intakes/"+id+"/documents").file(file).header("Authorization",jwt("user")))
                .andExpect(status().isCreated()).andReturn();
        long doc=JsonPath.<Number>read(upload.getResponse().getContentAsString(),"$.id").longValue();
        for(String name:List.of("user2","lawyer2")) {
            mvc.perform(get("/api/intakes/search-requests/"+search.id()).header("Authorization",jwt(name))).andExpect(status().isForbidden());
            mvc.perform(get("/api/intakes/"+id+"/documents/"+doc+"/download").header("Authorization",jwt(name))).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/intakes/search-requests/"+search.id()+"/results"),name,"{\"sourceType\":\"WEB\",\"title\":\"허용안됨\",\"url\":\"https://example.com\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/intakes/"+id+"/documents/"+doc+"/download").header("Authorization",jwt("lawyer"))).andExpect(status().isOk()).andExpect(content().bytes("test".getBytes()));
        mvc.perform(get("/api/intakes/search-requests/"+search.id()).header("Authorization",jwt("lawyer"))).andExpect(status().isOk());
        assertThat(results.findBySearchRequestIdOrderByDisplayOrder(search.id())).isEmpty();
    }

    @Test void assignmentRejectsNonLawyersAndCannotBeGrantedByLawyers() throws Exception {
        long id=create("user");assign(id,"lawyer");
        for(String name:List.of("user","admin","missing"))mvc.perform(json(patch("/api/intakes/"+id+"/assignment"),"admin",
                "{\"assignedLawyerUsername\":\""+name+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/intakes/"+id+"/assignment"),"lawyer","{\"assignedLawyerUsername\":\"lawyer2\"}"))
                .andExpect(status().isForbidden());
        assertThat(intakes.findById(id).orElseThrow().getAssignedLawyerUsername()).isEqualTo("lawyer");
    }
}
