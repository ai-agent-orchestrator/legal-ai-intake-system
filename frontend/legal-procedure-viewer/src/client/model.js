export const PURPOSES = [
  { value: '절차 안내', icon: 'document', description: '어떤 절차가\n필요한지 알고 싶어요' },
  { value: '필요한 자료 확인', icon: 'folder', description: '준비해야 할 자료를\n알고 싶어요' },
  { value: '문서 검토', icon: 'document', description: '작성한 문서를\n검토받고 싶어요' },
  { value: '변호사 선임·계약 검토', icon: 'people', description: '변호사 선임이나\n계약 내용을 알고 싶어요' },
  { value: '전자소송 제출 안내', icon: 'monitor', description: '전자소송 절차와\n제출 방법을 알고 싶어요' },
];

// C02 uses these values, not the separate MinimalLegalIntake case-type enum.
export const CASE_TYPES = [
  { value: 'CIVIL', label: '민사', icon: 'scales', description: '대여금·계약\n손해배상 등' },
  { value: 'CRIMINAL', label: '형사', icon: 'gavel', description: '사기·경찰 조사\n형사 고소 등' },
  { value: 'FAMILY', label: '가사', icon: 'people', description: '이혼·양육비\n상속·가정 문제 등' },
  { value: 'ELECTRONIC_LITIGATION', label: '전자소송', icon: 'document', description: '전자소송 제출\n관련 절차 등' },
  { value: 'UNKNOWN', label: '잘 모르겠어요', icon: 'help', description: '정확한 사건 유형을\n모르겠습니다' },
];

export const MAX_CASE_STORY_CHARS = 100;
export const INITIAL_DRAFT = { inquiryPurpose: PURPOSES[0].value, caseType: 'UNKNOWN', coreStatement: '' };
export const PARTY_POSITIONS = ['피해자', '피고소인', '원고', '피고', '신청인', '상대방', '잘 모르겠음'];
export const DOCUMENT_OPTIONS = ['계약서', '이체 내역', '문자·대화', '사진·영상', '관련 서류', '없음'];
export const NEXT_STEPS = ['법적 절차가 어떻게 진행되는지 알고 싶어요.', '준비해야 할 자료를 확인하고 싶어요.', '작성한 문서를 검토받고 싶어요.', '변호사 상담을 받고 싶어요.', '전자소송 제출 방법을 알고 싶어요.'];
export const CURRENT_STAGES = ['상대방에게 변제 요청 중', '상담 전 / 준비 중', '합의·조정 진행 중', '수사 진행 중', '소송 진행 중', '판결·결정 후', '잘 모르겠음'];
export const INTAKE_STATUSES = { SUBMITTED: '접수 완료', REVIEWED: '검토 완료', NEED_MORE_INFO: '추가 자료 필요', CONSULTATION_READY: '상담 준비 완료', CLOSED: '종료' };
export const INITIAL_INTAKE_FORM = {
  inquiryPurpose: '', caseType: '', coreStatement: '', partyPosition: '', desiredNextStep: '',
  incidentDate: '', counterpart: '', amountOrDamage: '', currentStage: '', clientName: '',
  clientPhone: '', preferredRegion: '', officeType: '', experienceLevel: '',
  publicCareerPreference: '', consultationStyle: '', lawyerSearchPurpose: '', documents: [],
};
export const INTAKE_FIELDS = {
  inquiryPurpose: [100, '문의 목적', true], caseType: [50, '사건 유형', true],
  partyPosition: [50, '당사자 위치', true], coreStatement: [MAX_CASE_STORY_CHARS, '핵심 내용', true],
  desiredNextStep: [100, '원하는 다음 단계', true], incidentDate: [30, '사건 발생일'],
  counterpart: [100, '상대방'], amountOrDamage: [100, '금액·피해 규모'],
  currentStage: [100, '현재 진행 단계'], clientName: [100, '이름'], clientPhone: [50, '연락처'],
  preferredRegion: [100, '선호 지역'], officeType: [100, '사무소 유형'], experienceLevel: [100, '경력 조건'],
  publicCareerPreference: [150, '공직·수사기관 경력 선호'], consultationStyle: [150, '상담 방식'],
  lawyerSearchPurpose: [300, '변호사 검색 목적'],
};

export function selectProcedureCode(procedures, selected) {
  return procedures?.some(item => item.code === selected) ? selected : procedures?.[0]?.code;
}

export function validateClassification(draft) {
  if (!draft.coreStatement.trim()) return '현재 상황을 간단히 적어 주세요.';
  // Match Java @Size / HTML maxlength: UTF-16 code units, including emoji.
  if (draft.coreStatement.length > MAX_CASE_STORY_CHARS) return `현재 상황은 ${MAX_CASE_STORY_CHARS.toLocaleString('ko-KR')}자 이내로 적어 주세요.`;
  if (!CASE_TYPES.some(option => option.value === draft.caseType)) return '사건 유형을 다시 선택해 주세요.';
  return '';
}

export function classificationPayload(draft) {
  const error = validateClassification(draft);
  if (error) throw new Error(error);
  return { caseType: draft.caseType, coreStatement: draft.coreStatement.trim() };
}

export function intakeValues(draft, preview, form) {
  return { ...form, inquiryPurpose: form.inquiryPurpose || draft.inquiryPurpose,
    caseType: form.caseType || preview?.caseType,
    coreStatement: form.coreStatement ?? draft.coreStatement };
}

export function intakePayload(draft, preview, form) {
  const values = intakeValues(draft, preview, form);
  const fieldErrors = {};
  const requiredMessages = { inquiryPurpose: '문의 목적을 선택해 주세요.', caseType: '사건 유형을 선택해 주세요.', partyPosition: '당사자 위치를 선택해 주세요.', coreStatement: '핵심 내용을 입력해 주세요.', desiredNextStep: '원하는 다음 단계를 선택해 주세요.' };
  const payload = {};
  for (const [key, [max, label, required]] of Object.entries(INTAKE_FIELDS)) {
    const value = values[key];
    if (required && (typeof value !== 'string' || !value.trim())) fieldErrors[key] = requiredMessages[key];
    else if (value != null && (typeof value !== 'string' || value.length > max)) fieldErrors[key] = `${label} 항목은 ${max}자 이내로 입력해 주세요.`;
    else if (typeof value === 'string' && value.trim()) payload[key] = value.trim();
  }
  if (!CASE_TYPES.some(type => type.value === payload.caseType)) fieldErrors.caseType = '사건 유형을 다시 확인해 주세요.';
  if (payload.incidentDate && (!/^\d{4}-\d{2}-\d{2}$/.test(payload.incidentDate)
      || Number.isNaN(Date.parse(`${payload.incidentDate}T00:00:00Z`))
      || new Date(`${payload.incidentDate}T00:00:00Z`).toISOString().slice(0, 10) !== payload.incidentDate)) {
    fieldErrors.incidentDate = '사건 발생일을 올바른 날짜로 입력해 주세요.';
  }
  if (payload.clientPhone && (!/^\+?[\d ()-]+$/.test(payload.clientPhone) || !/^\d{7,15}$/.test(payload.clientPhone.replace(/\D/g, '')))) {
    fieldErrors.clientPhone = '연락처를 숫자 7~15자리로 입력해 주세요. 하이픈과 국가번호를 사용할 수 있습니다.';
  }
  if (!Array.isArray(form.documents) || form.documents.some(value => typeof value !== 'string' || !value.trim() || value.length > 100)) {
    fieldErrors.documents = '선택한 자료 종류를 다시 확인해 주세요.';
  } else {
    payload.documents = [...new Set(form.documents.map(value => value.trim()))];
    if (payload.documents.includes('없음') && payload.documents.length > 1) fieldErrors.documents = '자료가 없으면 ‘없음’만 선택해 주세요.';
    if (payload.documents.includes('없음')) payload.documents = [];
  }
  if (Object.keys(fieldErrors).length) {
    const error = new Error(Object.values(fieldErrors)[0]);
    error.fieldErrors = fieldErrors;
    throw error;
  }
  return payload;
}

export function routeFromHash(hash) {
  const path = hash.replace(/^#/, '') || '/login';
  if (path === '/login' || path === '/') return { name: 'login' };
  if (path === '/account') return { name: 'account' };
  if (path === '/intake/type') return { name: 'classify' };
  if (path === '/intake/new') return { name: 'write' };
  if (path === '/lawyer/intakes') return { name: 'intake-list' };
  const lawyerDetail = path.match(/^\/lawyer\/intakes\/([1-9]\d*)$/);
  if (lawyerDetail) return { name: 'lawyer-detail', id: lawyerDetail[1] };
  const consultation = path.match(/^\/lawyer\/intakes\/([1-9]\d*)\/consultation$/);
  if (consultation) return { name: 'consultation', id: consultation[1] };
  const match = path.match(/^\/intake\/([1-9]\d*)$/);
  if (match) return { name: 'receipt', id: match[1] };
  const review = path.match(/^\/intake\/([1-9]\d*)\/review$/);
  if (review) return { name: 'review', id: review[1] };
  const search = path.match(/^\/intake\/([1-9]\d*)\/search(?:\/([1-9]\d*))?$/);
  if (search) return { name: 'search', id: search[1], ...(search[2] ? { searchRequestId: search[2] } : {}) };
  if (path === '/help') return { name: 'help' };
  return { name: 'not-found' };
}

// C04 consumes C05's protected display fields, never a retained raw form copy.
export function minimalPayload(intake) {
  return {
    sessionId: intake.sessionId || '', inquiryPurpose: intake.inquiryPurpose, caseType: intake.caseType,
    partyRole: intake.partyPosition, incidentDate: intake.incidentDate || '',
    counterpart: intake.counterpartMasked || '', amountOrDamage: intake.amountOrDamage || '',
    documents: intake.selectedDocuments.filter(document => !['없음', '선택 자료 없음'].includes(document)),
    desiredNextStep: intake.desiredNextStep, coreFact: intake.coreStatementMasked,
  };
}
