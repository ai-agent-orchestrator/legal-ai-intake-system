// Synthetic DTOs used by source tests and the separate QA server only.
export function testIntake(id = 42, overrides = {}) {
  return {
    id, version: 0, sessionId: 'qa-session', caseType: 'CIVIL', caseTypeLabel: '민사 사건', inquiryPurpose: '문서 검토',
    partyPosition: '원고', coreStatementMasked: '검수 상황', desiredNextStep: '검수 안내 확인',
    clientGuideMessage: '검수 안내', privacyNotice: '검수 고지', status: 'SUBMITTED', createdAt: '2026-10-21T10:00:00',
    incidentDate: null, counterpartMasked: null, amountOrDamage: null, currentStage: null,
    clientNameMasked: null, clientPhoneMasked: null, preferredRegion: null, disputeArea: '검수 분쟁',
    permissions: { canManage: true, canAssign: false }, selectedDocuments: [], recommendedDocuments: ['검수 준비 자료'], consultationMemos: [], uploadedDocuments: [],
    lawyerSearchCondition: { id: 90, intakeId: id, legalField: '민사', disputeType: '검수 분쟁', preferredRegion: '지역 미정',
      officeType: '유형 미정', experienceLevel: '경력 미정', publicCareerPreference: '선호 미정', consultationStyle: '상담 방식 미정',
      sourcePolicy: 'QA_SOURCE_POLICY', searchQuery: '검수 검색어', createdAt: '2026-10-21T10:00:00' },
    ...overrides,
  };
}
export function testGuide(body, overrides = {}) {
  return { sessionId: body.sessionId, inquiryPurpose: body.inquiryPurpose, caseType: body.caseType, caseTypeLabel: '검수 사건',
    partyRole: body.partyRole, disputeArea: '검수 분쟁', checkedFacts: ['검수된 내용'], recommendedDocuments: ['검수 준비 자료'],
    desiredNextStep: body.desiredNextStep, clientGuideMessage: '검수용 정리 안내', privacyNotice: '검수 보호 안내', securityNotice: '검수 보안 메타데이터', ...overrides };
}
export const testCriminalChecklist = { domain: 'CRIMINAL', role: 'COMPLAINANT', stageCode: 'SUPPLEMENTARY_INVESTIGATION_AFTER_TRANSFER',
  stageLabel: '검찰 송치 후 보완수사 단계', checklist: ['검수 보완 자료 확인'], recommendedAction: 'PREPARE_SUPPLEMENTARY_EVIDENCE_PACKET' };
export function testSearch(intakeId = 42, id = 81, overrides = {}) {
  return { id, intakeId, conditionId: 90, searchPurpose: '검수 검색 목적', sourcePolicy: 'QA_SOURCE_POLICY', status: 'PREVIEW_READY',
    searchQuery: '검수 검색어', results: [], createdAt: '2026-10-21T10:00:00', ...overrides };
}
export function testMemo(intakeId = 42, id = 301, overrides = {}) {
  return { id, intakeId, intakeVersion: 1, contentMasked: '검수용 보호된 상담 메모', followUpItems: ['REQUEST_DOCUMENTS'], createdAt: '2026-10-21T11:00:00', ...overrides };
}
export function testDocument(intakeId = 42, id = 401, overrides = {}) {
  return { id, intakeId, documentType: '계약서', originalFilename: '검수.txt', contentType: 'text/plain', sizeBytes: 4,
    createdAt: '2026-10-21T12:00:00', downloadUrl: `/api/intakes/${intakeId}/documents/${id}/download`, ...overrides };
}
