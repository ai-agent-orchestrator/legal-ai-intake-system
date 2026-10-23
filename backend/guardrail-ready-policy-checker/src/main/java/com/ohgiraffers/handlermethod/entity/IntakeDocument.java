package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "intake_document")
public class IntakeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long intakeId;

    @Column(nullable = false, length = 100)
    private String documentType;

    @Column(length = 255)
    private String originalFilename;

    @Column(length = 100)
    private String contentType;

    private Long sizeBytes;

    @Lob
    private byte[] fileContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected IntakeDocument() {
    }

    private IntakeDocument(Long intakeId, String documentType) {
        this.intakeId = intakeId;
        this.documentType = documentType;
        this.createdAt = LocalDateTime.now();
    }

    public static IntakeDocument create(Long intakeId, String documentType) {
        return new IntakeDocument(intakeId, documentType);
    }

    public static IntakeDocument upload(Long intakeId, String documentType, String filename,
                                        String contentType, byte[] content) {
        IntakeDocument document = create(intakeId, documentType);
        document.originalFilename = filename;
        document.contentType = contentType;
        document.sizeBytes = (long) content.length;
        document.fileContent = content;
        return document;
    }

    public boolean hasFile() { return originalFilename != null; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public byte[] getFileContent() { return fileContent; }

    public Long getId() {
        return id;
    }

    public Long getIntakeId() {
        return intakeId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
