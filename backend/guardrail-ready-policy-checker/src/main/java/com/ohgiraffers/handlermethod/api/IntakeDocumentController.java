package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.IntakeDocumentResponse;
import com.ohgiraffers.handlermethod.service.IntakeDocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/intakes/{intakeId}/documents")
public class IntakeDocumentController {
    private final IntakeDocumentService service;

    public IntakeDocumentController(IntakeDocumentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IntakeDocumentResponse upload(@PathVariable Long intakeId, @RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) String documentType,
                                          Authentication authentication) {
        return service.upload(intakeId, file, documentType, authentication);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long intakeId, @PathVariable Long documentId,
                                            Authentication authentication) {
        var file = service.download(intakeId, documentId, authentication);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}
