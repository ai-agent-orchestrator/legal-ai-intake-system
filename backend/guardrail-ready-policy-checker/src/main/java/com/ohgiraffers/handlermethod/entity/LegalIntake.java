package com.ohgiraffers.handlermethod.entity;

import com.ohgiraffers.handlermethod.legal.IntakeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_intake")
public class LegalIntake {

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
    public void assignLawyer(String username) { assignedLawyerUsername = username; }

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

    public void changeStatus(IntakeStatus next) {
        if (!IntakeStatus.valueOf(status).canChangeTo(next)) {
            throw new IllegalStateException("Cannot change intake status from " + status + " to " + next);
        }
        status = next.name();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
