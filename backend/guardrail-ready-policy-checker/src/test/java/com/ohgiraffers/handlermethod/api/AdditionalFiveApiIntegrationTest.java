package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.dto.IntakeEventRequest;
import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import com.ohgiraffers.handlermethod.repository.*;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import com.ohgiraffers.handlermethod.service.IntakeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:h2:mem:additional_five;MODE=MySQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class AdditionalFiveApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired JwtTokenProvider tokens;
    @Autowired IntakeEventService events;
    @Autowired IntakeEventRepository eventRepository;
    @Autowired IntakeDocumentRepository documents;
    @Autowired IntakeDocumentDownloadRepository downloads;
    @Autowired LegalIntakeRepository intakes;
    @Autowired AiSearchRequestRepository searches;
    @Autowired SearchResultSnapshotRepository results;
    @Autowired LawyerSearchConditionRepository conditions;

    @BeforeEach
    void clean() {
        eventRepository.deleteAll();
        downloads.deleteAll();
        documents.deleteAll();
        results.deleteAll();
        searches.deleteAll();
        conditions.deleteAll();
        intakes.deleteAll();
    }

    @Test
    void loginTokenCanReadCurrentUser() throws Exception {
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"password\":\"user123\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer broken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyIsNewestFirstAndContainsOnlyTargetIntake() throws Exception {
        long id = intake("history", "user");
        long other = intake("other", "user");
        long first = search(id);
        long second = search(id);
        search(other);
        mvc.perform(post("/api/intakes/search-requests/{id}/results", first).header("Authorization", auth("user")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"TEST\",\"title\":\"result\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/intakes/{id}/search-requests", id).header("Authorization", auth("user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].results", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PREVIEW_READY"));
    }

    @Test
    void historyEnforcesAuthenticationOwnershipAndExistence() throws Exception {
        long id = intake("private", "user");
        mvc.perform(get("/api/intakes/{id}/search-requests", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/intakes/{id}/search-requests", id).header("Authorization", auth("other")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/intakes/{id}/search-requests", id).header("Authorization", auth("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/intakes/{id}/search-requests", Long.MAX_VALUE).header("Authorization", auth("user")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIntakeFilesAndHistoryAreRestrictedToAdmin() throws Exception {
        long id = intake("anonymous-intake", null);
        mvc.perform(get("/api/intakes/{id}/search-requests", id).header("Authorization", auth("user")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/intakes/{id}/documents", id).file(textFile())
                        .header("Authorization", auth("admin")))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadedFileCanBeDiscoveredAndDownloadedWithIdenticalBytesAndAudit() throws Exception {
        long id = intake("file-flow", "user");
        var uploaded = mvc.perform(multipart("/api/intakes/{id}/documents", id).file(textFile())
                        .param("documentType", "입금 내역").header("Authorization", auth("user")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("자료.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.documentType").value("입금 내역"))
                .andExpect(jsonPath("$.fileContent").doesNotExist()).andReturn();
        long documentId = id(uploaded);
        mvc.perform(get("/api/intakes/{id}", id).header("Authorization", auth("user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uploadedDocuments[0].id").value(documentId));
        mvc.perform(get("/api/intakes/{id}/documents/{doc}/download", id, documentId)
                        .header("Authorization", auth("user")))
                .andExpect(status().isOk()).andExpect(content().bytes(textFile().getBytes()))
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(downloads.count()).isEqualTo(1);
    }

    @Test
    void fileRequestsCannotCrossIntakesOrReadDocumentTypePlaceholders() throws Exception {
        long id = intake("first-file", "user");
        long other = intake("second-file", "user");
        long placeholder = documents.findByIntakeIdOrderById(id).getFirst().getId();
        long fileId = id(mvc.perform(multipart("/api/intakes/{id}/documents", id).file(textFile())
                .header("Authorization", auth("user"))).andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/intakes/{id}/documents/{doc}/download", other, fileId)
                .header("Authorization", auth("user"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/intakes/{id}/documents/{doc}/download", id, placeholder)
                .header("Authorization", auth("user"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/intakes/{id}/documents/{doc}/download", id, fileId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/intakes/{id}/documents/{doc}/download", id, fileId)
                .header("Authorization", auth("other"))).andExpect(status().isForbidden());
        assertThat(downloads.count()).isZero();
    }

    @Test
    void uploadRejectsUnauthorizedMissingAndEmptyFiles() throws Exception {
        long id = intake("invalid-files", "user");
        long before = documents.count();
        mvc.perform(multipart("/api/intakes/{id}/documents", id).file(textFile()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/intakes/{id}/documents", id).file(textFile())
                .header("Authorization", auth("other"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/intakes/{id}/documents", Long.MAX_VALUE).file(textFile())
                .header("Authorization", auth("user"))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/intakes/{id}/documents", id).header("Authorization", auth("user")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/intakes/{id}/documents", id)
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .header("Authorization", auth("user"))).andExpect(status().isBadRequest());
        assertThat(documents.count()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"script.html", "fake.pdf", "fake.png", "fake.jpg", "binary.txt"})
    void uploadRejectsUnsupportedExtensionsAndMismatchingContent(String filename) throws Exception {
        long id = intake("file-types", "user");
        mvc.perform(multipart("/api/intakes/{id}/documents", id)
                .file(new MockMultipartFile("file", filename, "application/octet-stream", new byte[]{0, 1, 2}))
                .header("Authorization", auth("user"))).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void oversizedFileAndMisdeclaredMimeAreRejected() throws Exception {
        long id = intake("file-limits", "user");
        mvc.perform(multipart("/api/intakes/{id}/documents", id)
                .file(new MockMultipartFile("file", "large.txt", "text/plain", new byte[5242881]))
                .header("Authorization", auth("user"))).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/intakes/{id}/documents", id)
                .file(new MockMultipartFile("file", "file.txt", "image/png", "text".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", auth("user"))).andExpect(status().isUnsupportedMediaType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "png", "jpeg"})
    void supportedBinarySignaturesRoundTrip(String extension) throws Exception {
        long id = intake("binary-file", "user");
        byte[] bytes = switch (extension) {
            case "pdf" -> "%PDF-1.7\nexperiment".getBytes(StandardCharsets.US_ASCII);
            case "png" -> new byte[]{(byte)137,80,78,71,13,10,26,10,0};
            default -> new byte[]{(byte)255,(byte)216,(byte)255,0};
        };
        long fileId = id(mvc.perform(multipart("/api/intakes/{id}/documents", id)
                .file(new MockMultipartFile("file", "sample." + extension, "application/octet-stream", bytes))
                .header("Authorization", auth("user"))).andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/intakes/{id}/documents/{doc}/download", id, fileId)
                .header("Authorization", auth("user")))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }

    @Test
    void pathFilenameIsOnlyUsedAsDisplayBasename() throws Exception {
        long id = intake("filename", "user");
        mvc.perform(multipart("/api/intakes/{id}/documents", id)
                .file(new MockMultipartFile("file", "../../자료.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", auth("user"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("자료.txt"));
    }

    @Test
    void eventsArePersistedDeduplicatedAndVisibleInFunnel() throws Exception {
        long intakeId = intake("event-session", "user");
        String visit = "{\"eventId\":\"event-1\",\"eventType\":\"VISIT\",\"sessionId\":\"event-session\"}";
        var first = mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON).content(visit))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.receivedAt").exists()).andReturn();
        mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON).content(visit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.receivedAt").value(JsonPath.<String>read(first.getResponse().getContentAsString(), "$.receivedAt")));
        mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"event-2\",\"eventType\":\"INTAKE_STARTED\",\"sessionId\":\"event-session\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"event-3\",\"eventType\":\"SUMMARY_VIEWED\",\"sessionId\":\"event-session\",\"intakeId\":" + intakeId + "}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/dashboard/marketing-funnel").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCounts.VISIT").value(1))
                .andExpect(jsonPath("$.eventCounts.INTAKE_STARTED").value(1))
                .andExpect(jsonPath("$.eventCounts.SUMMARY_VIEWED").value(1))
                .andExpect(jsonPath("$.stages[0].measurement").value("REPORTED_EVENT"))
                .andExpect(jsonPath("$.dropOffMeasured").value(false));
    }

    @Test
    void changedPayloadForSameEventIdIsConflict() throws Exception {
        events.record(new IntakeEventRequest("same-id", IntakeEventType.VISIT, "s", null));
        mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"same-id\",\"eventType\":\"INTAKE_STARTED\",\"sessionId\":\"s\"}"))
                .andExpect(status().isConflict());
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidOrUnlinkedEventsDoNotCreateRecords() throws Exception {
        long id = intake("correct-session", "user");
        List<String> invalid = List.of("{}",
                "{\"eventId\":\"a\",\"eventType\":\"UNKNOWN\",\"sessionId\":\"s\"}",
                "{\"eventId\":\"a\",\"eventType\":\"SUMMARY_VIEWED\",\"sessionId\":\"s\"}",
                "{\"eventId\":\"a\",\"eventType\":\"VISIT\",\"sessionId\":\"wrong\",\"intakeId\":" + id + "}");
        for (String body : invalid) {
            mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"a\",\"eventType\":\"VISIT\",\"sessionId\":\"s\",\"intakeId\":9223372036854775807}"))
                .andExpect(status().isNotFound());
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void concurrentEventRetriesCreateOnlyOneRow() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tasks = IntStream.range(0, 12).<Callable<Boolean>>mapToObj(i -> () ->
                    events.record(new IntakeEventRequest("concurrent", IntakeEventType.VISIT, "s", null)).created()).toList();
            int created = 0;
            for (var future : executor.invokeAll(tasks)) { if (future.get()) { created++; } }
            assertThat(created).isEqualTo(1);
            assertThat(eventRepository.count()).isEqualTo(1);
        }
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile("file", "자료.txt", "text/plain", "실험 자료\n".getBytes(StandardCharsets.UTF_8));
    }

    private String auth(String username) {
        return "Bearer " + tokens.createToken(username, username.equals("admin") ? "ROLE_ADMIN" : "ROLE_USER");
    }

    private long intake(String session, String username) throws Exception {
        var request = post("/api/intakes").contentType(MediaType.APPLICATION_JSON).content("""
                {"sessionId":"%s","inquiryPurpose":"자료 확인","caseType":"CIVIL","partyPosition":"원고",
                 "coreStatement":"빌려준 돈을 못 받음","desiredNextStep":"상담","documents":["입금 내역"]}
                """.formatted(session));
        request.header("Authorization", auth(username == null ? "admin" : username));
        long created = id(mvc.perform(request).andExpect(status().isCreated()).andReturn());
        if (username == null) {
            jdbc.update("update legal_intake set owner_username = null where id = ?", created);
            entityManager.clear();
        }
        return created;
    }

    private long search(long intakeId) throws Exception {
        return id(mvc.perform(post("/api/intakes/{id}/search-requests", intakeId).header("Authorization", auth("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated()).andReturn());
    }

    private long id(MvcResult result) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
    }
}
