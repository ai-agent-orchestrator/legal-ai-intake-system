package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "consultation_memo")
public class ConsultationMemo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long intakeId;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 100)
    private String createdBy;

    @Column(nullable = false, length = 200)
    private String followUpItems;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ConsultationMemo() {
    }

    public static ConsultationMemo create(Long intakeId, String content, List<String> followUpItems, String createdBy) {
        ConsultationMemo memo = new ConsultationMemo();
        memo.intakeId = intakeId;
        memo.content = content.trim();
        memo.createdBy = createdBy;
        memo.followUpItems = followUpItems == null ? "" : String.join("|", followUpItems.stream().distinct().toList());
        memo.createdAt = LocalDateTime.now();
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
}
