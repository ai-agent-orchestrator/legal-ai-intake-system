package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "consultation_memo",
        uniqueConstraints = @UniqueConstraint(name = "uk_consultation_memo_request_key",
                columnNames = {"intake_id", "request_key"}))
public class ConsultationMemo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intake_id", nullable = false)
    private Long intakeId;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 100)
    private String createdBy;

    @Column(nullable = false, length = 200)
    private String followUpItems;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /* 화면07 동일 메모 재전송 판별용: 클라이언트 키(있으면) + 내용 해시. */
    @Column(name = "request_key", length = 120)
    private String requestKey;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    protected ConsultationMemo() {
    }

    public static ConsultationMemo create(Long intakeId, String content, List<String> followUpItems, String createdBy) {
        return create(intakeId, content, followUpItems, createdBy, null, null);
    }

    public static ConsultationMemo create(Long intakeId, String content, List<String> followUpItems, String createdBy,
                                          String requestKey, String contentHash) {
        ConsultationMemo memo = new ConsultationMemo();
        memo.intakeId = intakeId;
        memo.content = content.trim();
        memo.createdBy = createdBy;
        memo.followUpItems = followUpItems == null ? "" : String.join("|", followUpItems.stream().distinct().toList());
        memo.createdAt = LocalDateTime.now();
        memo.requestKey = requestKey;
        memo.contentHash = contentHash;
        return memo;
    }

    public Long getId() { return id; }
    public Long getIntakeId() { return intakeId; }
    public String getContent() { return content; }
    public String getCreatedBy() { return createdBy; }
    public List<String> getFollowUpItems() {
        return followUpItems.isEmpty() ? List.of() : Arrays.asList(followUpItems.split("\\|"));
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRequestKey() { return requestKey; }
    public String getContentHash() { return contentHash; }
}
