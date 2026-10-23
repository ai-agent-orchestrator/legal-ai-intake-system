package com.ohgiraffers.handlermethod.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LegalIntakeResponse(
        Long id,
        String sessionId,
        String clientNameMasked,
        String clientPhoneMasked,
        String inquiryPurpose,
        String caseType,
        String caseTypeLabel,
        String partyPosition,
        String incidentDate,
        String counterpartMasked,
        String amountOrDamage,
        String currentStage,
        String coreStatementMasked,
        String desiredNextStep,
        String preferredRegion,
        String disputeArea,
        List<String> selectedDocuments,
        List<String> recommendedDocuments,
        LawyerSearchConditionResponse lawyerSearchCondition,
        String clientGuideMessage,
        String privacyNotice,
        String status,
        LocalDateTime createdAt,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<ConsultationMemoResponse> consultationMemos,
        List<IntakeDocumentResponse> uploadedDocuments,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String assignedLawyerUsername,
        IntakePermissions permissions,
        /* 화면07 동시 수정 감지용. 상태 변경·메모 요청에 expectedVersion으로 다시 보낸다. */
        long version
) {
    public record IntakePermissions(boolean canManage, boolean canAssign) {}
}
