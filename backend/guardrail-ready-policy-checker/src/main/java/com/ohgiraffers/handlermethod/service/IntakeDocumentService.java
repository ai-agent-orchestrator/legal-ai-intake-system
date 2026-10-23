package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.IntakeDocumentResponse;
import com.ohgiraffers.handlermethod.entity.IntakeDocument;
import com.ohgiraffers.handlermethod.entity.IntakeDocumentDownload;
import com.ohgiraffers.handlermethod.repository.IntakeDocumentDownloadRepository;
import com.ohgiraffers.handlermethod.repository.IntakeDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

@Service
public class IntakeDocumentService {
    private final IntakeAccessService access;
    private final IntakeDocumentRepository documents;
    private final IntakeDocumentDownloadRepository downloads;
    private final long maxBytes;

    public IntakeDocumentService(IntakeAccessService access, IntakeDocumentRepository documents,
                                  IntakeDocumentDownloadRepository downloads,
                                  @Value("${intake.documents.max-bytes:5242880}") long maxBytes) {
        this.access = access;
        this.documents = documents;
        this.downloads = downloads;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public IntakeDocumentResponse upload(Long intakeId, MultipartFile file, String documentType,
                                          Authentication authentication) {
        access.requireAccess(intakeId, authentication);
        if (file.isEmpty()) { throw error(HttpStatus.BAD_REQUEST, "File must not be empty"); }
        if (file.getSize() > maxBytes) { throw error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds size limit"); }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        // Treat the client filename only as a display name, never as a storage path.
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank()) { throw error(HttpStatus.BAD_REQUEST, "Invalid filename"); }
        String type = documentType == null ? "ATTACHMENT" : documentType.trim();
        if (type.isBlank() || type.length() > 100) { throw error(HttpStatus.BAD_REQUEST, "Invalid documentType"); }
        try {
            byte[] content = file.getBytes();
            if (content.length > maxBytes) { throw error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds size limit"); }
            String mediaType = validateContent(filename, content);
            String declared = file.getContentType();
            if (declared != null && !declared.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
                try {
                    if (!MediaType.parseMediaType(declared).isCompatibleWith(MediaType.parseMediaType(mediaType))) {
                        throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type does not match its content");
                    }
                } catch (org.springframework.http.InvalidMediaTypeException exception) {
                    throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid content type");
                }
            }
            return IntakeDocumentResponse.from(documents.save(
                    IntakeDocument.upload(intakeId, type, filename, mediaType, content)));
        } catch (IOException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Cannot read uploaded file");
        }
    }

    @Transactional
    public Download download(Long intakeId, Long documentId, Authentication authentication) {
        access.requireAccess(intakeId, authentication);
        IntakeDocument document = documents.findById(documentId)
                .filter(d -> d.getIntakeId().equals(intakeId) && d.hasFile())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Uploaded file not found for intake"));
        downloads.save(new IntakeDocumentDownload(intakeId, documentId, authentication.getName()));
        return new Download(document.getOriginalFilename(), document.getContentType(), document.getFileContent());
    }

    private String validateContent(String filename, byte[] content) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (lower.endsWith(".png") && startsWith(content, new byte[]{(byte)137,80,78,71,13,10,26,10})) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                && startsWith(content, new byte[]{(byte)255,(byte)216,(byte)255})) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lower.endsWith(".txt")) {
            try {
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
                if (text.chars().noneMatch(c -> Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t')) {
                    return MediaType.TEXT_PLAIN_VALUE;
                }
            } catch (CharacterCodingException ignored) { }
        }
        throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Allowed files: PDF, PNG, JPEG, UTF-8 TXT with matching content");
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record Download(String filename, String contentType, byte[] content) { }
}
