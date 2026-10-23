package com.ohgiraffers.handlermethod.entity;

import com.ohgiraffers.handlermethod.legal.IntakeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_intake",
        uniqueConstraints = @UniqueConstraint(name = LegalIntake.IDEMPOTENCY_CONSTRAINT,
                columnNames = {"owner_username", "idempotency_key"}))
public class LegalIntake {

    /* 화면03 재전송 유니크 제약 이름. 동시 재전송 충돌을 다른 무결성 오류와 구분할 때 쓴다. */
    public static final String IDEMPOTENCY_CONSTRAINT = "uk_legal_intake_owner_idempotency";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sessionId;

    @Column(length = 100)
    private String ownerUsername;

    @Column(length = 100)
    private String assignedLawyerUsername;

    public String getAssignedLawyerUsername() { return assignedLawyerUsername; }
    public void assignLawyer(String username) {
        if (!java.util.Objects.equals(assignedLawyerUsername, username)) {
            assignedLawyerUsername = username;
            touch();
        }
    }

    @Column(length = 100)
    private String clientName;

    @Column(length = 50)
    private String clientPhone;

    @Column(nullable = false, length = 100)
    private String inquiryPurpose;

    @Column(nullable = false, length = 50)
    private String caseType;

    @Column(nullable = false, length = 50)
    private String partyPosition;

    @Column(length = 30)
    private String incidentDate;

    @Column(length = 100)
    private String counterpart;

    @Column(length = 100)
    private String amountOrDamage;

    @Column(length = 100)
    private String currentStage;

    @Column(nullable = false, length = 2000)
    private String coreStatement;

    @Column(nullable = false, length = 100)
    private String desiredNextStep;

    @Column(length = 100)
    private String preferredRegion;

    @Column(length = 300)
    private String lawyerSearchPurpose;

    @Column(nullable = false, length = 150)
    private String disputeArea;

    @Column(nullable = false, length = 500)
    private String clientGuideMessage;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /* 화면07 동시 수정 충돌 감지용. 상태 변경·메모 추가·배정 변경마다 1씩 증가한다. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column
    private LocalDateTime updatedAt;

    /* 화면03 재전송 판별용. 같은 사용자 + 같은 키는 한 건만 저장된다. */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    protected LegalIntake() {
    }

    private LegalIntake(String sessionId,
                        String clientName,
                        String clientPhone,
                        String inquiryPurpose,
                        String caseType,
                        String partyPosition,
                        String incidentDate,
                        String counterpart,
                        String amountOrDamage,
                        String currentStage,
                        String coreStatement,
                        String desiredNextStep,
                        String preferredRegion,
                        String lawyerSearchPurpose,
                        String disputeArea,
                        String clientGuideMessage,
                        String status) {
        this.sessionId = sessionId;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.inquiryPurpose = inquiryPurpose;
        this.caseType = caseType;
        this.partyPosition = partyPosition;
        this.incidentDate = incidentDate;
        this.counterpart = counterpart;
        this.amountOrDamage = amountOrDamage;
        this.currentStage = currentStage;
        this.coreStatement = coreStatement;
        this.desiredNextStep = desiredNextStep;
        this.preferredRegion = preferredRegion;
        this.lawyerSearchPurpose = lawyerSearchPurpose;
        this.disputeArea = disputeArea;
        this.clientGuideMessage = clientGuideMessage;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public static LegalIntake create(String sessionId,
                                     String clientName,
                                     String clientPhone,
                                     String inquiryPurpose,
                                     String caseType,
                                     String partyPosition,
                                     String incidentDate,
                                     String counterpart,
                                     String amountOrDamage,
                                     String currentStage,
                                     String coreStatement,
                                     String desiredNextStep,
                                     String preferredRegion,
                                     String lawyerSearchPurpose,
                                     String disputeArea,
                                     String clientGuideMessage) {
        return new LegalIntake(
                sessionId,
                clientName,
                clientPhone,
                inquiryPurpose,
                caseType,
                partyPosition,
                incidentDate,
                counterpart,
                amountOrDamage,
                currentStage,
                coreStatement,
                desiredNextStep,
                preferredRegion,
                lawyerSearchPurpose,
                disputeArea,
                clientGuideMessage,
                "SUBMITTED"
        );
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void assignOwner(String username) {
        if (ownerUsername != null) {
            throw new IllegalStateException("Intake owner is already assigned");
        }
        ownerUsername = username;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public String getInquiryPurpose() {
        return inquiryPurpose;
    }

    public String getCaseType() {
        return caseType;
    }

    public String getPartyPosition() {
        return partyPosition;
    }

    public String getIncidentDate() {
        return incidentDate;
    }

    public String getCounterpart() {
        return counterpart;
    }

    public String getAmountOrDamage() {
        return amountOrDamage;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public String getCoreStatement() {
        return coreStatement;
    }

    public String getDesiredNextStep() {
        return desiredNextStep;
    }

    public String getPreferredRegion() {
        return preferredRegion;
    }

    public String getLawyerSearchPurpose() {
        return lawyerSearchPurpose;
    }

    public String getDisputeArea() {
        return disputeArea;
    }

    public String getClientGuideMessage() {
        return clientGuideMessage;
    }

    public String getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void recordSubmission(String idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    /* 상태 외의 변경(메모 추가 등)도 버전을 올려 다른 상담자의 화면이 오래된 것임을 알 수 있게 한다. */
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public void changeStatus(IntakeStatus next) {
        if (!IntakeStatus.valueOf(status).canChangeTo(next)) {
            throw new IllegalStateException("Cannot change intake status from " + status + " to " + next);
        }
        if (!status.equals(next.name())) {
            status = next.name();
            touch();
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
