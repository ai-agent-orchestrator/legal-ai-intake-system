# 화면04 · 안내 확인 React / API 대조·검수

> 2026-10-21 화면01~04 통합 재검수: **공유 API/React 128개·실제 API 13개·빌드 통과**. 로그인→접수→안내 브라우저 흐름과 4개 화면 반응형 확인. 02·03 R02 분야 대조와 미분류 참고 안내 보완. C05는 현재 공개 조회이며 소유자 인가는 별도다. [이번 통합 필드·ID·인증·구현 검수](SCREEN01_04_INTEGRATION_REVIEW.md). 아래 개별 수치·미확인 기록은 이전 회차의 기록이다.

2026-10-21. 사용자 첨부 화면04를 기준으로 기존 구현을 보완하고 실제 Spring API와 브라우저에서 검수했다. 공통 헤더·사무실 배경·3단계 표시, 접수 내용 표·보호된 핵심 내용·개인정보 안내, 간단 안내·관련 절차·다음 화면 이동을 구현했다. 예시 문의 번호·이름·상태·자료·절차를 제품 데이터로 사용하지 않는다.

## 동작별 API·코드·ID·인증

| 동작 | API | React 연결 | ID·요청 | 성공 응답·처리 | 구현·인증 |
|---|---|---|---|---|---|
| 접수 내용 확인 | C05 `GET /api/intakes/{id}` | `IntakeReceiptPage` / `IntakeReviewPage` → `api.receiptIntake` | C03 반환 문의 ID 또는 직접 접근 경로 ID. 본문 없음 | 200 보호 DTO로 표·상태·핵심 내용·공지 표시. 응답 ID와 경로 ID 대조 | 연결·실연동 완료. 현재 공개 GET, 로그인 시 Bearer 선택 전송 |
| 간단 안내 생성 | C04 `POST /api/legal-intake/minimal` | `SimpleGuide` → `api.minimal` → `minimalPayload` | C05의 보호된 표시 필드 10개. 문의 ID를 새로 생성하거나 C04 응답에서 얻지 않음 | 200 확인 내용·추천 자료·다음 정리 방향. 문의 저장·LLM 호출 없음 | 연결·실연동 완료. 공개 POST, JWT 전송 안 함 |
| 참고 절차 코드 조회 | R01 `GET /api/legal/procedures?domain={caseType}` | `RelatedProcedures` → `api.procedures` | 저장된 사건 유형. `UNKNOWN`이면 query 없이 전체 목록 조회 | 200 실제 절차 코드·제목 배열. 미분류 전체 조회라는 안내 표시 | 공통 보조 API 재사용 완료. 공개 GET |
| 절차 펼치기 | R02 `GET /api/legal/procedures/{code}` | `ProcedureDetail` → `api.procedure(code, signal, domain)` | R01에서 반환한 코드 URL 인코딩. 코드와 R01 행의 domain 대조 | 200 요약·정렬된 단계 표시. 접으면 상세 컴포넌트 요청 취소 | 연결·실연동 완료. 공개 GET |
| 형사 참고 체크리스트 | R06 `GET /api/legal/criminal/checklist/supplementary-investigation` | `CriminalChecklist` → `api.criminalChecklist` | 형사 문의에서만 호출. 본문·문의 ID 없음 | 200 `CRIMINAL/COMPLAINANT/SUPPLEMENTARY_INVESTIGATION_AFTER_TRANSFER` 대조 후 항목 표시 | 연결·실연동 완료. 공개 GET. 고소인·검찰 송치 후 보완수사 참고용 |
| 핵심 내용 더 보기·접기 | API 없음 | `ProtectedStatement` | 이미 받은 `coreStatementMasked`만 펼침 | 원문 복원·복호화·추가 조회 없음 | 구현·브라우저 검수 완료 |
| 이전 | C05 재사용 | `#/intake/{id}/review` | 동일 문의 ID 유지 | 읽기 전용 접수 내용 화면. C03 재등록·수정 없음 | 구현·검수 완료 |
| 검색 요청 준비 | 다음 화면에서 C05 재사용 | `#/intake/{id}/search` | 동일 문의 ID 전달 | 화면05 저장 조건·검색 요청 폼 표시. 이동만으로 C06을 자동 실행하지 않음 | 구현·왕복 검수 완료 |
| 새 문의 작성 | 즉시 호출 API 없음 | `onNewInquiry` → `#/intake/type` | 이전 입력·분류를 초기화. 저장된 문의는 삭제 안 함 | 새 사건 분류에서 다시 시작. 제출할 때만 C03 | 구현·컴포넌트 검수 완료 |

첨부의 기본 연결 4개는 C04/C05/R02/R06이고, R01은 하드코딩 없이 R02 코드를 얻기 위해 사용하는 공통 보조 조회다. R02 자체 응답에 단계가 있으므로 화면04에서 R03을 중복 호출하지 않는다. 화면05의 C06/C07/C08/P02와 개발 전 P03은 [화면05 검수서](SCREEN05_REACT_REVIEW.md)를 따른다.

현재 `SecurityConfig`에서 위 API는 공개다. C05의 선택 Bearer 전송·401 처리와 로그인 UI가 문의 소유자 인가를 뜻하지 않는다. 파일 다운로드 P05·검색 이력 P02의 소유자/관리자 정책은 별도다. 이번 프런트 작업에서 서버 인증·역할·소유자 정책을 변경하지 않았다. JWT는 탭 메모리에 유지하고 URL이나 증거 파일에 넣지 않는다.

## 요청·응답 필드 대조

C05 응답 전체 계약:

```text
id, sessionId, clientNameMasked, clientPhoneMasked, inquiryPurpose,
caseType, caseTypeLabel, partyPosition, incidentDate, counterpartMasked,
amountOrDamage, currentStage, coreStatementMasked, desiredNextStep,
preferredRegion, disputeArea, selectedDocuments[], recommendedDocuments[],
lawyerSearchCondition, clientGuideMessage, privacyNotice, status, createdAt,
consultationMemos[], uploadedDocuments[]
```

화면04는 접수 내용·보호 필드·상태·개인정보 공지를 사용한다. 검색 조건·메모·실제 첨부는 다른 화면의 기능이며 보유 자료 선택을 실제 파일 업로드로 표시하지 않는다. `receiptIntake`는 필수 표시 문자열·사건 유형·문자열 배열·선택 필드 타입·검색 조건 부모 ID, 응답 문의 ID, 유효 생성 시각과 5개 상태를 검증한다. 상태는 `SUBMITTED/REVIEWED/NEED_MORE_INFO/CONSULTATION_READY/CLOSED`를 실제 값으로 표시하고, 검토/추가 자료/종료의 색상을 구분한다. 모르는 상태나 잘못된 응답은 성공 화면으로 표시하지 않는다.

| C04 요청 필드 | C05에서 가져오는 값 |
|---|---|
| `sessionId` | `sessionId` 또는 빈 문자열. 세션 식별자이며 문의 ID와 다름 |
| `inquiryPurpose` | `inquiryPurpose` |
| `caseType` | `caseType` |
| `partyRole` | `partyPosition` |
| `incidentDate` | `incidentDate` 또는 빈 문자열 |
| `counterpart` | **`counterpartMasked`** 또는 빈 문자열 |
| `amountOrDamage` | `amountOrDamage` 또는 빈 문자열 |
| `documents` | `selectedDocuments` 중 ‘없음/선택 자료 없음’ 제외 |
| `desiredNextStep` | `desiredNextStep` |
| `coreFact` | **`coreStatementMasked`** |

원래 작성 폼의 이름·전화번호·원문을 가져와 다시 보내지 않는다. C04 요청 DTO에는 현재 Bean Validation 어노테이션이 없고 Controller에도 `@Valid`가 없다. 화면04는 입력 폼이 아닌 저장 결과 조회 화면이며 검증된 C05 필드만 C04에 전달한다. 화면03 입력 검증 한도를 C04 서버의 필수·최대 길이 제한으로 잘못 기재하지 않았다.

C04 응답은 `sessionId, inquiryPurpose, caseType, caseTypeLabel, partyRole, disputeArea, checkedFacts[], recommendedDocuments[], desiredNextStep, clientGuideMessage, privacyNotice, securityNotice`다. 화면에서는 확인 내용·추천 자료·안내·다음 단계를 사용한다. 응답 배열·문자열과 세션·목적·당사자·다음 단계의 연결을 검증한다. 저장 유형이 명확하면 C04의 유형도 일치해야 하고, `UNKNOWN`일 때는 서버의 규칙 추론 결과를 허용한다. 개인정보 공지는 C05의 저장 응답을 표시한다. C04의 `securityNotice` 문구는 C04가 실제 Guard/LLM을 호출했다는 실행 증거로 사용하지 않는다.

참고 API 계약:

| API | 응답 |
|---|---|
| R01 | `{code, domain, title, summary, userRole}[]` |
| R02 | `{code, domain, title, summary, userRole, steps[], forms[], knowledgeGraphEdges[]}` |
| R02 `steps[]` | `{stepOrder, stepCode, title, description, checklist[]}`. 화면은 순서·제목·설명을 사용 |
| R02 `forms[]` | `{procedureCode, formCode, title, fileType, usageNote}`. 반환 부모 코드 검증, 화면04에서 별도 다운로드 UI 없음 |
| R06 | `{domain, role, stageCode, stageLabel, checklist[], recommendedAction}`. 화면은 참고 범위·단계명·항목 표시 |

절차는 실제 DB 목록만 표시한다. 첨부 예시에 있는 절차가 DB에 없으면 임의로 추가하지 않는다. R06은 현재 문의의 진행 단계나 역할을 판정한 응답이 아니므로 고소인·특정 단계의 참고 자료라는 범위를 안내한다.

## 공통 React·디자인·라우팅

- `frontend/legal-procedure-viewer/src/client/IntakeReceiptPage.jsx`: `IntakeReceiptPage`, 읽기 전용 `IntakeReviewPage`, 재사용 `IntakeSummary`, `ProtectedStatement`, `SimpleGuide`, `RelatedProcedures`, `ProcedureDetail`, `CriminalChecklist`.
- `IntakeApp.jsx`: C03 실제 반환 ID → `#/intake/{id}`. 직접 URL·새로고침도 C05로 복구. 이전/검색 이동에서 ID 유지, ID 변경 시 페이지 컴포넌트를 교체한다.
- `IntakeHeader`, `Steps`, `Button`, `Feedback`, `useResource`, `Icon`과 공통 fetch·선택 JWT·15초 타임아웃·오류 처리를 재사용한다.
- `intake-receipt.css`: 첨부의 좌우 패널, 접수 완료 아이콘, 정보 표, 파랑/초록/노랑 안내 카드와 하단 버튼을 반영했다. 브라우저에서 확인한 표의 균등 열 문제를 `colgroup`으로 수정했다. 모바일은 `colgroup`을 숨기고 표를 항목/값 두 열로 전환한다.
- 1,000px 이하 단일 패널 흐름, 작은 화면의 이전/새 문의 버튼 2열·검색 준비 버튼 전체 너비를 적용했다. 긴 사실·준비 자료 목록은 독립 스크롤이며 키보드로 접근할 수 있다.
- C05 로딩/404/오류/재시도, C04 간단 안내 실패/재시도, R01 빈 목록, R02 빈 단계/오류, R06 빈 항목/오류를 분리한다. C04나 참고 자료 오류가 저장 문의를 지우지 않는다. ID·접힌 절차·화면 이탈에 따른 오래된 요청을 취소한다.

## 검수

| 항목 | 결과 |
|---|---|
| 최신 `npm run build` | viewer/operator/intake 3개 진입점, 88개 모듈 빌드 성공 |
| `npm run test:intake` | **111/111 통과**. C05 표시 계약·ID·상태/날짜, C04 보호 필드·타입/연결·UNKNOWN 추론, R01/R02 도메인/코드, 로딩/빈/오류·재시도·취소·이동 포함 |
| `npm run test:intake:live` | **16/16 통과**. 실제 로컬 Spring, 민사·형사 가상 문의 생성. C05→C04→R01/R02·R06, 보호 필드, C04 후 저장 문의 불변 검증 |
| `npm run test:intake:preview` | **1/1 통과**. 빌드 정적 진입점과 실제 API 프록시 |
| 브라우저 데스크톱 | 1672×941, 민사 문의 8. 표 열 너비 147/253/135/307px, 가로 넘침 없음, 하단 버튼까지 표시 |
| 브라우저 동작 | 보호 내용 펼치기 후 원문 전화번호 미표시, 실제 R02 3단계, 읽기 전용 review, 화면05 ID 유지, 형사 문의 9의 R06 범위·항목 표시 확인 |
| 브라우저 오류 | 없는 실제 ID의 C05 404·참조 번호·재시도. 이전 문의 표·간단 안내가 남지 않음 |
| 모바일 | 390×844, 본문 가로 넘침 없음, 항목/값 표와 343px 검색 버튼·167px 이전/새 문의 버튼 확인 |

이번 intake 공유 검수는 **111 + 16 + 1 = 128개 통과**다. 기존 화면 문서의 122/125개는 해당 회차 기록이다. 운영자 전용 테스트와 백엔드 전체 Gradle 테스트를 이번 변경에서 추가 실행한 것으로 집계하지 않았다. 실패·빈 데이터·타입 불일치·지연 취소는 API/React 테스트로 검증했으며 모든 실패 조건을 실제 브라우저에 주입했다고 주장하지 않는다.

- [실제 API 증거](../output/intake-04/api-evidence.json): 문의 8, 형사 문의 9, 절차 `civil-litigation` 3단계, C04 확인 내용 4개·추천 자료 7개. 비밀키·토큰 없음.
- [데스크톱](../output/intake-04/screen04-live-desktop.png), [모바일 본문](../output/intake-04/screen04-mobile.png), [모바일 버튼](../output/intake-04/screen04-mobile-actions.png), [브라우저 기록](../output/intake-04/browser-evidence.json).
- 최종 화면: `http://127.0.0.1:5175/intake.html#/intake/8`. 문의 번호는 검수용이며 실제 사용에서는 접수·목록의 반환 ID를 사용한다. H2 메모리 서버를 재시작하면 데이터·번호가 달라질 수 있다.

백엔드 대조: `legal/MinimalLegalIntakeController`, `MinimalLegalIntakeRequest/Response`, `MinimalLegalIntakeService.analyze`, `api/LegalIntakeRecordController.findById`, `dto/LegalIntakeResponse`, `api/LegalProcedureController`, `service/LegalProcedureService`, `config/SecurityConfig`.
