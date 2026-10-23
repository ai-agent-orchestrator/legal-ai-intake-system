# 남은 개발 중 4건 구현·검수

2026-10-23. 사용자가 선택을 위임한 작은 범위4건: 기존 목록②·③·④·⑧. 운영자 ADMIN 인가, 문의 중복 접수, 상태/메모 경합·중복 처리, Incident 상태 전이는 이번 범위에 포함하지 않았다.

| 완료 항목 | API·계약 | 확인 결과 |
|---|---|---|
| ② 위험 이력 입력 원문 제외 | C17 `GET /api/agent/risk/history` | `AgentRiskHistoryResponse`의 `userInput` 필드와 매핑 자체를 제거. 기본/위험등급/판정 필터 모두 JSON에서 제외. ID·점수·신호·Trace·점수 분해는 유지 |
| ③ 간단 안내 서버 검증 | C04 `POST /api/legal-intake/minimal` | JWT + `@Valid`. 잘못된 요청은400 `INVALID_REQUEST`, `fieldErrors` 제공. 정상 안내는 기존 규칙·DB 방식 |
| ④ 수동 결과 URL·순서 검증 | C08 `POST /api/intakes/search-requests/{searchRequestId}/results` | HTTP(S)·호스트·포트 검사. 사용자정보, 공백/제어문자 및 인코딩된 제어문자, 위험 프로토콜 거절. `displayOrder`는 양수. 위반400·DB 미저장 |
| ⑧ 없는 Incident 오류 | C19/C20 `PATCH /api/incidents/{incidentId}/ack`, `.../resolve` | 없는 숫자ID는404 `INCIDENT_NOT_FOUND`, 기존 ErrorResponse의 path/traceId 포함. 잘못된 ID형식400. 기존 Incident 상태·건수 불변 |

## C04 필드 제한

| 필드 | 계약 |
|---|---|
| `coreFact` | 필수·공백 금지, 최대100 UTF-16 단위. 기존 사건 본문 공통 상수 사용 |
| `caseType` | 생략/null은 기존 본문 기반 분류. 제공하면 `CIVIL/CRIMINAL/FAMILY/ELECTRONIC_LITIGATION/UNKNOWN`만 허용(대소문자 허용). 빈 문자열·다른 코드400 |
| `sessionId`, `inquiryPurpose`, `counterpart`, `amountOrDamage`, `desiredNextStep` | 선택·각100자 |
| `partyRole`, `incidentDate` | 선택·각50/30자. 자유 텍스트 필드이며 새 enum 계약으로 바꾸지 않음 |
| `documents` | 선택/null 허용·최대20개, 각 항목 필수·공백 금지·최대100자 |

문의 생성 응답→상세 조회→보호된 필드로 C04 요청하는 실제 화면04 방식도 HTTP로 확인했다. 이 API는 문의를 추가 저장하거나 LLM을 호출하지 않는다.

## C08 URL·순서 계약

기존 URL 최대1000자·필수 제한에 `@HttpResultUrl`을 추가했다. URI 파서로 HTTP(S), 호스트, userInfo 미포함, 포트 생략 또는1~65535를 확인한다. 리터럴 공백·제어문자와 `%00~%1f`, `%7f`를 거절한다. 일반 쿼리·프로필ID·프래그먼트와 정상 `%20` 경로/쿼리는 허용한다. URL을 DNS 조회하거나 서버에서 내려받는 기능은 추가하지 않았다. 이 검증을 모든 사설 주소·리다이렉트 목적지 검증으로 설명하지 않는다.

`displayOrder`는 생략/null이면 기존 자동 순서를 유지하고, 명시하면 양수 Java Integer다. URL 위반은 `fieldErrors.url`, 순서 위반은 `fieldErrors.displayOrder`로 전달한다. 기존 자료를 일괄 삭제·수정하거나 표시 순서를 재배열하지 않는다.

## 검수

- [x] 백엔드 전체 **218개 통과**, 실패/오류/제외0. 새 `FourHardeningIntegrationTest`8개 포함. 서버 bootJar 성공.
- [x] 프런트 문의142개·운영자35개 통과, production 빌드 성공. 화면12/13은 제거한 원문 필드에 의존하지 않음.
- [x] 격리 Spring/H2 `127.0.0.1:18094`에서 **실제 HTTP27개 요청 단언 통과**. C04 정상/400 및 C03→C05→C04 인계, C08 거절 후 결과0건·정상 자동 순서, C17 세 조회의 원문 제외, C19/C20 404·형식400·기존 목록 불변 확인.
- [x] 외부 검색/LLM/Guard 호출0회. 테스트 서버는 공급자 주소를 폐쇄된 루프백으로 지정하고 검색 비활성·빈 키로 실행했다.
- [x] 테스트 서버만 종료. 기존 앱 서버의 포트·데이터는 변경하지 않았다.

실행 파일: [API 통합 검사](../backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/FourHardeningIntegrationTest.java), [실제 HTTP 검수 도구](../scripts/Verify-FourHardening.mjs). 가상 자료·직접 HTTP 근거는 로컬 `output/hardening-four/live-api-verification.json`과 `verification.json`에 보관한다(Git 제외).

HTTP 검수 도구는 명시한 폐기 가능한 로컬 서버에서만 실행한다. `HARDENING_API_BASE=http://127.0.0.1:18094`를 지정하고 `node scripts/Verify-FourHardening.mjs`를 실행한다. 과거 `intake-0508-audit.mjs`는 공개 정책·검증 결함을 재현한 당시 증거용 코드이며 현행 계약의 합격 기준으로 사용하지 않는다.

## 남은 개발4건

- [ ] ① 운영자 업무 API8개 ADMIN 인가 및 React 인증 연결.
- [ ] ⑤ 문의 접수의 재전송/동시 요청 중복 저장 방지.
- [ ] ⑥ 상태 변경 충돌 및 메모 재전송 중복 처리.
- [ ] ⑦ Incident 허용 상태 전이·동시 변경·중복 처리.

서버 빌드와 격리 API를 검증했다. 현재 사용 중인 앱 서버에 대한 자동 재시작이나 실제 공급자 연결·유료 호출 검수는 이번 범위가 아니다. DB 스키마 변경은 없다. C17의 DB 원문 보관 정책도 이번 응답 제거와 별도다.
