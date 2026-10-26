export const RESULT_SOURCE_TYPES = [
  { value: 'LEGAL_INFORMATION', label: '법률 정보 사이트' }, { value: 'LAW_FIRM_SITE', label: '법률사무소 홈페이지' },
  { value: 'CASE_LAW', label: '판례 정보' }, { value: 'PUBLIC_WEB', label: '기타 공개 정보' },
];
export const EMPTY_RESULT_FORM = { sourceType: '', title: '', url: '', snippet: '', displayOrder: '' };
export function safeSearchUrl(value) {
  if (typeof value !== 'string' || /[\x00-\x20\x7f]/.test(value)) return '';
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) && url.hostname && !url.username && !url.password ? url.href : '';
  } catch { return ''; }
}
export function searchResultPayload(form) {
  const errors = {};
  for (const [key, max, label] of [['sourceType', 100, '출처 유형'], ['title', 300, '제목'], ['url', 1000, 'URL']]) {
    if (typeof form[key] !== 'string' || !form[key].trim()) errors[key] = `${label} 항목을 입력해 주세요.`;
    else if (form[key].length > max) errors[key] = `${label} 항목은 ${max}자 이내로 입력해 주세요.`;
  }
  if (!errors.url && !safeSearchUrl(form.url.trim())) errors.url = '사용자 정보가 없는 올바른 http:// 또는 https:// URL을 입력해 주세요.';
  if (typeof form.snippet !== 'string' || form.snippet.length > 1000) errors.snippet = '짧은 설명은 1,000자 이내로 입력해 주세요.';
  const order = form.displayOrder;
  if (order !== '' && (typeof order !== 'string' || !/^[1-9]\d*$/.test(order) || Number(order) > 2147483647)) errors.displayOrder = '표시 순서는 1~2,147,483,647 사이의 정수로 입력해 주세요.';
  if (Object.keys(errors).length) { const error = new Error(Object.values(errors)[0]); error.fieldErrors = errors; throw error; }
  return { sourceType: form.sourceType.trim(), title: form.title.trim(), url: form.url.trim(), ...(form.snippet.trim() ? { snippet: form.snippet.trim() } : {}), ...(order !== '' ? { displayOrder: Number(order) } : {}) };
}
const compareId = (a, b) => String(a).length - String(b).length || String(a).localeCompare(String(b));
export function sortSearchResults(items, mode) {
  return [...items].sort((a, b) => mode === 'newest' ? b.createdAt.localeCompare(a.createdAt) || compareId(b.id, a.id) : a.displayOrder - b.displayOrder || compareId(a.id, b.id));
}
export function searchSourceLabel(value, sourceUrl) {
  if (value === 'KBA_PUBLIC_WEB') {
    try {
      const url = new URL(safeSearchUrl(sourceUrl));
      if (['klaw.or.kr', 'koreanbar.or.kr'].some(host => url.hostname === host || url.hostname.endsWith(`.${host}`)))
        return '대한변협 공개 페이지 · 웹 검색 수집';
    } catch { /* A manually recorded source label alone does not establish an official source. */ }
    return '공개 웹 · 출처 확인 필요';
  }
  return value === 'NAVER_WEB' ? '공개 웹 검색' : RESULT_SOURCE_TYPES.find(item => item.value === value)?.label || value;
}
export function searchSourceWarningLabel(code) {
  return ({ KBA_SEARCH_UNAVAILABLE: '변협 대상 검색에 연결하지 못했습니다. 일반 웹 검색에서 수집한 결과를 표시합니다.',
    GENERAL_SEARCH_UNAVAILABLE: '일반 웹 검색에 연결하지 못했습니다. 변협 대상 검색에서 수집한 결과를 표시합니다.' })[code]
    || '일부 출처를 조회하지 못했습니다. 수집된 결과를 표시합니다.';
}
export const SEARCH_STAGES = { CONDITION_CHECK: '검색 조건 확인', QUERY_GENERATION: '검색문 생성', EXTERNAL_SEARCH: '외부 자료 검색', RESULT_PERSISTENCE: '결과 저장', EXPLANATION: '결과 설명 생성', FINISHED: '완료' };
export const searchRunning = request => ['QUEUED', 'RUNNING'].includes(request?.status);
export function searchStatusLabel(value) { return ({ PREVIEW_READY: '검색 준비 완료', QUEUED: '실행 대기', RUNNING: '검색 진행 중', COMPLETED: '검색 완료', NO_RESULTS: '검색 결과 없음', PARTIAL: '결과 저장·설명 미완료', FAILED: '검색 실패' })[value] || value; }
export function searchFailureLabel(code) { return ({ SEARCH_TIMEOUT: '처리 시간을 초과했습니다.', SEARCH_INTERRUPTED: '서버 작업이 중단되었습니다.', SEARCH_COST_BLOCKED: '사용 가능한 AI 비용 한도를 초과했습니다.', SEARCH_GUARD_BLOCKED: '안전 검토에서 요청 또는 설명이 차단되었습니다.', INVALID_QUERY: '생성된 검색문 형식을 확인할 수 없습니다.', SEARCH_PROVIDER_ERROR: '외부 검색 서비스에 연결하지 못했습니다.', SEARCH_RESPONSE_INVALID: '외부 검색 결과 형식을 확인할 수 없습니다.', SEARCH_LLM_ERROR: 'AI 생성 서비스를 사용할 수 없습니다.', EXPLANATION_INVALID: '생성된 설명 형식을 확인할 수 없습니다.' })[code] || '검색 실행 중 오류가 발생했습니다.'; }
export function searchRequestCopy(request) {
  return [`검색 요청 #${request.id}`, `문의 #${request.intakeId}`, `조건 #${request.conditionId}`, `생성일시: ${request.createdAt}`, `상태: ${request.status}`,
    `검색 목적: ${request.searchPurpose}`, `검색어: ${request.searchQuery}`, `출처 정책: ${request.sourcePolicy}`, `저장된 결과: ${request.results.length}건`].join('\n');
}
