package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "intake_document_download")
public class IntakeDocumentDownload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long intakeId;
    @Column(nullable = false) private Long documentId;
    @Column(nullable = false, length = 100) private String username;
    @Column(nullable = false) private Instant requestedAt;

    protected IntakeDocumentDownload() { }

    public IntakeDocumentDownload(Long intakeId, Long documentId, String username) {
        this.intakeId = intakeId;
        this.documentId = documentId;
        this.username = username;
        this.requestedAt = Instant.now();
    }
}
