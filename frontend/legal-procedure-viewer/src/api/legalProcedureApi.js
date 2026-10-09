const DEFAULT_API_BASE = "http://localhost:8080";

export const DOMAIN_OPTIONS = [
  "ALL",
  "CIVIL",
  "CRIMINAL",
  "FAMILY",
  "ELECTRONIC_LITIGATION"
];

export function normalizeApiBase(apiBase) {
  return (apiBase || DEFAULT_API_BASE).replace(/\/$/, "");
}

export async function fetchJson(url) {
  /*
     공통 GET API 호출 함수.

     fetch는 404/500이어도 자동으로 catch로 가지 않는다.
     그래서 response.ok를 직접 확인하고 실패면 Error를 던진다.

     React 화면:
     fetchProcedures(...)

     실제 HTTP:
     GET http://localhost:8080/api/legal/procedures
   */
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }

  return response.json();
}

export function fetchProcedures(apiBase, domain) {
  /*
     절차 목록 조회.

     domain = ALL
     -> GET /api/legal/procedures

     domain = CIVIL
     -> GET /api/legal/procedures?domain=CIVIL
   */
  const query = domain && domain !== "ALL" ? `?domain=${encodeURIComponent(domain)}` : "";
  return fetchJson(`${normalizeApiBase(apiBase)}/api/legal/procedures${query}`);
}

export function fetchProcedureDetail(apiBase, code) {
  /*
     절차 상세 조회.

     code 예:
     civil-litigation

     실제 HTTP:
     GET /api/legal/procedures/civil-litigation
   */
  return fetchJson(`${normalizeApiBase(apiBase)}/api/legal/procedures/${code}`);
}

export function fetchSupplementaryInvestigationChecklist(apiBase) {
  /*
     실용형 checklist 전용 API.

     다음 LLM 상담 챗봇 프로젝트에서는 사용자가
     "검찰 송치 후 보완수사 단계"라고 입력했을 때
     이 API 결과를 챗봇 응답 재료로 사용할 수 있다.
   */
  return fetchJson(`${normalizeApiBase(apiBase)}/api/legal/criminal/checklist/supplementary-investigation`);
}
