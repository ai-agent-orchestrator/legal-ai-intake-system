# 화면05~08 API · React · 실행 동작 통합 검수

> **2026-10-23 권한 후속 반영:** C03/C04 및 저장 문의 API는 JWT 필수다. 소유자·배정 LAWYER·ADMIN 범위를 서버에 적용했고, 상태/내부 메모는 담당자·ADMIN만 허용한다. 의뢰인 응답에서 내부 메모를 제외한다. ADMIN 배정 API C21과 nullable DB 컬럼2개를 추가했다. 마지막 실행은 백엔드210개·문의142개·운영자35개 및 브라우저 권한14개 통과, 양쪽 빌드 성공이다. 아래 이전 회차의 공개/익명 인증 판정과 테스트 수치는 당시 기록이며 최신 인증 계약은 [권한 통합 검수](SCREEN04_08_ACCESS_CONTROL_REVIEW.md)를 따른다. 전체 과거 브라우저 시나리오를 새로 실행한 것은 아니다.


> 2026-10-22 화면05 후속 변경: 변협 공개 페이지 대상/일반 네이버 검색을 합치고 출처·부분 조회 실패 안내를 연결했다. 기존 경로·인증·ID 전달은 유지하며 `execution.sourceWarningCode`만 nullable 추가했다. 백엔드 전체 185개·프런트 137개 검사와 빌드 통과. 이번 변경은 모의 제공자 및 React 렌더 검증이며 아래 이전 브라우저 51개 기록을 재실행한 것은 아니다. [변경 상세·검증 범위](P03_EXTERNAL_SEARCH_REVIEW.md#2026-10-22-변협-공개-페이지--일반-웹-혼합-검색).

2026-10-21 (Asia/Seoul). 이전 API·필드·ID·인증 검수에 이번 빌드·실제 브라우저·입력·로딩·빈 상태·오류 복구 검수를 통합했다. **프런트 빌드, React 테스트 135개, 브라우저 시나리오 51개가 통과했다. API 연결은 확인했지만 운영용 인증까지 완료됐다는 판정은 아니다.** C05/C06/C07/C08/C09/C10/C11은 현재 개발용 공개 정책이며, P02/P03/P05는 JWT·소유권 검사를 적용한다.

정상 동작은 실제 React와 격리 Spring 서버·H2 DB로 확인했다. 지연·오류·빈 응답은 검수용 프록시에서 주입했고, P03의 LLM·검색 제공자는 로컬 모의 서버를 사용했다. 실제 외부 제공자 키·유료 호출은 사용하지 않았다. 기존 발견 사항 2건은 유지하며, 이번 브라우저 검수에서 새 제품 결함은 확인되지 않았다.

| 화면 | 현재 React 기능·컴포넌트 | 판정 |
|---|---|---|
| 05 | 검색 요청·이력·결과 / `IntakeSearchPage` | API·라우팅 연결 확인. 브라우저 19개 통과: 실행·부분 실패·설명 재생성·빈 결과·저장 오류·시간 초과 복구. 실제 외부 제공자는 키 설정 후 확인 필요 |
| 06 | 접수된 문의 목록 / `IntakeListPage` | C09 연결. 브라우저 11개 통과: 목록·필터·상세 왕복·날짜 검증·로딩·빈 목록·오류 복구 |
| 07 | 상태 변경·상담 메모 / `IntakeConsultationPage` | C05/C10/C11 연결. 브라우저 11개 통과: 필수 입력·저장·동시 제출 잠금·초안 보존·재조회 복구·빈 메모 |
| 08 | 문의 상세 / `IntakeDetailPage` | C05/P05 연결. 브라우저 10개 통과: 실제 자료·메모·첨부파일·로그인·다운로드·로딩·빈 항목·404/403 처리 |

현재 React와 사용자가 첨부한 화면07·08 이미지를 기준으로 **07=상태·메모, 08=상세**로 표기한다. 예전 전체 API 매핑 이미지의 `07=C05 상세 재사용 / 08=C10·C11`과 번호가 다르며, 이번 확인은 기능·실제 경로를 함께 명시했다.

## 1. 동작별 API · 요청 · 응답 · ID · 인증

표의 S는 아래 검색 응답 DTO, I는 문의 응답 DTO다. Bearer를 선택 전송하는 것과 서버가 인증을 강제하는 것을 구분한다.

| 화면·동작 | API / React 함수 | 요청·전달 ID | 응답·화면 반영 | 서버 인증 / 구현 |
|---|---|---|---|---|
| 05 접수·조건 읽기 | C05 `GET /api/intakes/{id}` / `intake` | URL의 문의 ID, 본문 없음 | I의 `id`, `lawyerSearchCondition`, 사건·분쟁·지역·보호된 상대방 | 공개, JWT 선택 / 연결 확인 |
| 05 검색 요청 기록 | C06 `POST /api/intakes/{intakeId}/search-requests` / `createSearch` | `{searchPurpose,sourcePolicy}`. 목적≤300, 정책≤100. 문의 ID는 C05에서 받은 값 | 201 S. 반환 `id`로 요청 경로 이동 후 C07 조회·P02 갱신. `conditionId`는 서버가 저장 조건에서 결정 | 공개, JWT 선택 / 실제 HTTP 확인 |
| 05 요청 선택·진행·결과 조회 | C07 `GET /api/intakes/search-requests/{searchRequestId}` / `searchRequest` | C06 또는 P02에서 받은 요청 ID. 문의 ID를 요청 ID 대신 보내지 않음 | 200 S. 응답의 요청 ID·문의 ID 및 각 결과의 `searchRequestId` 대조. 이전 조건이면 안내 표시 | 공개, JWT 선택 / 실제 HTTP·React 확인 |
| 05 요청 이력 | P02 `GET /api/intakes/{intakeId}/search-requests` / `searchHistory` | 문의 ID, 본문 없음 | 200 S 배열, 최신순·빈 배열 구분. 선택한 행의 실제 요청 ID로 C07 이동 | JWT 필수, 소유자/ADMIN. 익명401·비소유자403 / 실제 HTTP 확인 |
| 05 직접 결과 기록 | C08 `POST /api/intakes/search-requests/{searchRequestId}/results` / `addSearchResult` | `{sourceType,title,url,snippet?,displayOrder?}`. 길이100/300/1000/1000. React는 HTTP(S), 사용자정보 없는 URL·양의 순서 검사 | 201 갱신된 S. 결과 ID는 서버 반환값. 완료 상태를 임의 생성하지 않음. P03 실행 중 409 | 공개, JWT 선택 / 연결 확인. **서버 URL·순서 검증은 보완 필요** |
| 05 실제 검색 실행 | P03 `POST /api/intakes/search-requests/{searchRequestId}/execute` / `executeSearch` | `{retryExplanation:false}`. 서버는 생략 본문·`{}`·null 플래그도 false 처리. 조건/문의/결과 ID를 본문으로 조작하지 않음 | 202 S + `execution`, 실제 생성 검색문·결과·AI 설명. QUEUED/RUNNING일 때 C07 폴링 | JWT 필수, 소유자/ADMIN. 401·403·미설정/비활성503 확인 / 구현·모의 제공자 검증 |
| 05 미완성 설명 재생성 | 같은 P03 / `executeSearch` | `{retryExplanation:true}`. 선택 요청 ID | PARTIAL의 설명 없는 결과만 처리. 기존 결과·실행 batch ID 유지. 외부 검색을 재실행하지 않음 | 같은 JWT·소유권 정책 / 서버·React 테스트 확인 |
| 06 목록·새로고침 | C09 `GET /api/intakes` / `intakes` | 본문·필터 쿼리 없음 | 200 I 배열. 최신 최대50건, 중복 ID·형식·날짜 검증. 서버 순서·C05 상세 ID 일치 확인 | 공개, JWT 선택 / 실제 HTTP 확인 |
| 06 필터·정렬·통계·상담 준비 메뉴 | 추가 API 없음 | 유형·상태·다음 단계·날짜·검색어를 React state로 관리 | 불러온 최대50건에서 계산. 기간 역전 검증. 상세 왕복 시 목록 조건 유지 | React 구현 확인. 서버 전체 검색·페이지네이션·전체 DB 통계는 아님 |
| 07 현재 상태·저장 메모 조회 | C05 `GET /api/intakes/{id}` / `consultationIntake` | 경로의 문의 ID | 200 I. 상태 enum·메모 ID/부모·날짜·체크 코드 검증 | 공개 / 실제 HTTP·React 확인 |
| 07 상태 저장 | C10 `PATCH /api/intakes/{id}/status` / `updateStatus` | `{status}`만 전송. 문의 ID 유지 | 200 `{id,previousStatus,status,changed}`. 목표 상태·동일 ID·changed 일관성 검사 후 C05 재조회 | 공개, JWT 선택 / 실제 HTTP 확인 |
| 07 메모 추가 | C11 `POST /api/intakes/{id}/memo` / `createMemo` | `{content,followUpItems}`. 공백 금지·content≤4000, 허용 코드≤4. 화면의 이름·날짜·상태 등은 보내지 않음 | 201 `{id,intakeId,contentMasked,followUpItems,createdAt}`. 서버 메모 ID·문의 ID 대조. 보호된 본문·최근5건 표시 후 C05 재조회 | 공개, JWT 선택 / 실제 HTTP에서 익명 저장·전화 마스킹·재조회 확인 |
| 08 상세 읽기 | C05 `GET /api/intakes/{id}` / `detailIntake` | 목록 행·직접 URL의 문의 ID | 200 I. 저장된 사실·조건·안내·자료·메모·실제 업로드 메타데이터 표시. 잘못된 부모 ID·다운로드 URL·파일 형식은 거절 | 공개 / 실제 HTTP·React 확인 |
| 08 첨부 다운로드 | P05 `GET /api/intakes/{intakeId}/documents/{documentId}/download` / `downloadDocument` | 문의 ID + C05 `uploadedDocuments[].id`. 본문 없음. JWT는 헤더에만 전달 | 바이너리. C05의 MIME·바이트 크기 대조 후 안전한 파일명으로 저장. HTML·다른 파일 응답은 성공 처리하지 않음 | JWT·소유자/ADMIN 필수. 익명401, 비소유자403, 다른 문의 파일/누락404, 소유자·관리자 동일 바이트 확인 |
| 08 상태·메모 관리 이동 / 07 상세 복귀 | 추가 API 없음 | 같은 문의 ID로 hash 경로 이동 | 대상 페이지에서 C05 조회. 화면을 바꿔도 다른 문의·파일·메모 ID를 끼워 넣지 않음 | 라우팅·React 테스트 확인 |

**P04 업로드**는 이번 P05 검수의 파일 준비용으로 호출했다. 파일 업로드 UI가 화면08에 구현됐다는 뜻은 아니다. 화면08은 업로드된 메타데이터 표시·다운로드를 구현한다.

### 응답 필드 대조

- **I / LegalIntakeResponse**: `id, sessionId, clientNameMasked, clientPhoneMasked, inquiryPurpose, caseType, caseTypeLabel, partyPosition, incidentDate, counterpartMasked, amountOrDamage, currentStage, coreStatementMasked, desiredNextStep, preferredRegion, disputeArea, selectedDocuments, recommendedDocuments, lawyerSearchCondition, clientGuideMessage, privacyNotice, status, createdAt, consultationMemos, uploadedDocuments`.
- `lawyerSearchCondition`: `id,intakeId,legalField,disputeType,preferredRegion,officeType,experienceLevel,publicCareerPreference,consultationStyle,sourcePolicy,searchQuery,createdAt`.
- **S / AiSearchRequestResponse**: `id,intakeId,conditionId,searchPurpose,sourcePolicy,status,searchQuery,results,createdAt,execution`.
- `results[]`: `id,searchRequestId,sourceType,title,url,snippet,displayOrder,createdAt,explanation`. `snippet`과 AI `explanation`을 구분한다.
- `execution`: nullable. 실행 시 `id,stage,provider,errorCode,startedAt,finishedAt`. 내부 lease·키는 응답하지 않는다.
- `consultationMemos[]`: `id,intakeId,contentMasked,followUpItems,createdAt`. 작성자·수정일·원문을 제공하는 DTO가 아니다.
- `uploadedDocuments[]`: `id,intakeId,documentType,originalFilename,contentType,sizeBytes,createdAt,downloadUrl`. 파일 ID와 문의 ID를 모두 검증한다.
- `status`: SUBMITTED / REVIEWED / NEED_MORE_INFO / CONSULTATION_READY / CLOSED. 같은 상태는 changed=false. CLOSED에서 다른 상태로 변경·다시 SUBMITTED로 변경은 409이며 화면 선택지도 같은 규칙이다.
- 후속 업무 코드: REQUEST_DOCUMENTS / SCHEDULE_CONSULTATION / DOCUMENT_REVIEW / E_LITIGATION_GUIDE. 코드 저장이 실제 알림·일정 생성을 실행하지는 않는다.

## 2. 공통 React · 디자인 · 라우팅 · API 구현

| 검수 대상 | 확인 내용 | 판정 |
|---|---|---|
| 공통 디자인 | Header·BrandMark·Icon과 공통 색상·Button·Feedback 사용. 화면05 Steps, 화면06~08 LawyerShell 재사용. 화면별 CSS는 공통 intake 스타일 위에 적용 | 코드·빌드 및 실제 화면 확인 |
| 공통 요청 계층 | `createIntakeApi`의 한 요청 함수에서 base URL·Bearer·JSON/바이너리·timeout·AbortSignal·401 처리·오류 메시지 통일 | React/API 테스트 확인 |
| 인증 전달 | token은 메모리 ref에 보관, 헤더에만 사용. URL에 token을 넣지 않음. P02/P03/P05는 클라이언트도 로그인 preflight | 확인. 일반 요청의 authenticated:true가 서버의 인증 강제를 의미하지 않음 |
| 응답 계약 | 문의·요청·조건·메모·파일의 부모 ID·중복 ID·형식 검증. 파일은 임의 외부 다운로드 URL에 JWT를 보내지 않도록 서버 경로와 일치 검사 | 확인 |
| 데이터 갱신 | 상태·메모는 저장 성공과 후속 GET 실패를 분리. 불확실한 저장 응답은 재조회 후 확인하며 쓰기 자동 재전송 없음. P03은 진행 상태에서만 폴링 | React 테스트 및 브라우저 저장·재조회·복구 확인 |
| 화면 이동 | hash router와 경로별 제목·초점 처리. 문의 ID를 key로 페이지 인스턴스 분리. 경로 변경 시 이전 요청 취소, 오래된 결과를 새 문의에 표시하지 않음 | React 테스트 확인 |
| 디자인 범위 | 1672×941 데스크톱과 390×844 모바일에서 화면05~08 확인. 모바일 카드·입력 배치 전환, 목록·첨부 등의 표는 내부 가로 스크롤. 네 화면 모두 문서 폭375≤뷰포트390 | 배치·가독성·주요 조작 확인. 첨부 시안과 픽셀 단위 일치 판정은 아님 |

경로는 다음과 같다.

```text
05  intake.html#/intake/{intakeId}/search[/{searchRequestId}]
06  intake.html#/lawyer/intakes
07  intake.html#/lawyer/intakes/{intakeId}/consultation
08  intake.html#/lawyer/intakes/{intakeId}
```

모든 ID 자리는 실제 서버 ID다. INQ-/SR- 같은 표기 접두사는 API ID에 붙이지 않는다. 유효하지 않은·추가 경로가 붙은 주소는 not-found로 분기한다.

## 3. 발견 사항과 처리

### 수정 완료: P03 빈 JSON 기본값 불일치

기존 `SearchExecuteRequest(boolean retryExplanation)`에서 빈 `{}`를 보냈을 때 실제 HTTP가 400을 반환했다. 문서의 선택 필드·본문 생략 계약과 달랐다. nullable `Boolean`과 `Boolean.TRUE.equals(...)`로 바꿔 미입력·null을 false로 처리했다.

- 새 서버 테스트: 본문 생략·`{}`·null 플래그에서도 소유권403 유지, 관리자 `{}` 실행 성공, 잘못된 플래그400·제공자 호출 없음.
- 실제 HTTP 재검수: 비소유자 `{}`는 **403**, 소유자 `{}`는 검수 서버의 기능 비활성에 따라 **503**, 상태 PREVIEW_READY·execution=null 유지. 요청 파싱400 문제가 해결됐다.

### P1 · 운영 배포 전 접근 제어 필요

현재 C05/C06/C07/C08/C09/C10/C11은 비회원도 접근할 수 있다. 격리 서버에서 인증 없이 문의 목록·상세200, 검색 요청 생성201·조회200·결과 저장201, 상태 변경200·메모 저장201을 직접 확인했다. 메모의 전화번호 마스킹은 확인했지만, 내부 메모를 조회 주체별로 분리하는 정책은 없다.

이는 현재 개발용 정책과 일치한다. **변호사 메뉴·역할 라벨은 인가 수단이 아니다.** 서버 계정은 현재 USER/ADMIN이며 LAWYER 권한 모델이나 변호사 전용 문의 배정 정책은 없다. 운영용 조회·변경 권한 정책을 정한 뒤 SecurityConfig와 서비스의 소유권·담당자 검사를 적용해야 한다. 이번 검수에서 임의로 접근 정책을 바꾸지는 않았다.

코드 근거: [SecurityConfig.java:78](/C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:78). 직접 HTTP 근거: [인증·검증 기록](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-recheck/auth-and-validation.json).

P02/P03/P05는 별도로 소유권 검사한다. 비회원으로 생성한 문의는 나중에 일반 사용자로 로그인해도 자동으로 소유자가 되지 않으며, 현재 관리자만 해당 보호 기능을 사용할 수 있다.

### P2 · C08 서버 URL·순서 검증 보완 필요

React는 위험 URL·사용자정보 포함 URL·비양수 순서를 차단하며, 이미 저장된 위험 URL도 클릭 링크로 렌더링하지 않는다. 그러나 서버 요청 DTO는 URL의 공백·길이만 검사하고 displayOrder 양수 제약이 없다. 직접 POST에서 `url="javascript:void(0)", displayOrder=-1`이 **201로 저장됨**을 격리 DB에서 확인했다. 브라우저에서 실행하거나 외부에 전송하지 않았다.

서버에도 HTTP(S)·사용자정보 없는 URI·양의 표시 순서를 검증해야 한다. 화면을 거치지 않는 호출에서도 자료 품질과 안전한 링크 정책이 일치해야 하기 때문이다. 이번 작업은 검수이며 이 서버 계약 변경은 남겨 두었다.

코드 근거: [SearchResultSnapshotRequest.java:15](/C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/SearchResultSnapshotRequest.java:15).

### 실제 외부 검색 확인 대기

P03 코드·React·로컬 모의 제공자 흐름은 구현됐지만, 사용자가 운영 키 미설정 상태임을 알렸다. 이전 직접 HTTP 검수 서버(18084)는 `search.enabled=false`로 실행했다. 이번 브라우저 검수 서버(18085)는 P03을 활성화하되 LLM·검색·Guard 주소를 모두 로컬 모의 제공자(18181)로 지정했다. 실제 키·쿼터·모델 호환·외부 응답 품질은 미검증이다. [P03 상세 구현·이전 모의 HTTP/브라우저 검수](/C:/myLectureWs/legal-ai-intake-security-server/docs/P03_EXTERNAL_SEARCH_REVIEW.md).

기타 범위: 상태 변경 이력은 화면에서 확인한 임시 이력이며 서버 감사 이력이 아니다. 작성자·최근 수정일·서버 전체 목록 필터·업로드 UI·내 정보/자료 관리 등의 준비 중 메뉴는 이번 연결 완료 판정에 포함하지 않는다.

## 4. 브라우저 동작 · 입력 · 로딩 · 빈 상태 · 오류 처리

브라우저에서 직접 클릭·입력·이동한 고유 시나리오 **51개 전부 통과**(05=19, 06=11, 07=11, 08=10). 51개는 UI 확인 시나리오 수이며 HTTP 호출 수와 다르다. 각 시나리오의 단언·URL·입력값·화면 폭은 [browser-checks.json](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/browser-checks.json)에 기록했다. 검수 도구의 초기 예상 문구·입력값 읽기 오류 2회는 기대값을 바로잡고 재실행했으며, JSON에는 각 시나리오의 마지막 결과를 집계했다.

| 화면 | 정상 동작·라우팅 | 입력 검증 | 로딩·중복 조작 | 빈 상태 | 오류·복구 |
|---|---|---|---|---|---|
| 05 | C05 조건 조회 → C06 새 요청 → 반환된 SR-4 경로 이동 → C07/P02 조회. C08 수동 결과 저장. P03 검색·결과·AI 설명 표시 | 출처 미선택, `javascript:void(0)` URL, 표시 순서 -1을 화면에서 거절. 저장 성공 후 제목·URL 초기화 | C05와 C07 각각 지연 시 로딩 표시. P03 실행 중 외부 실행·수동 저장 버튼 비활성 | 저장 조건이 없으면 생성 버튼 없음. 모의 검색 0건이면 NO_RESULTS·결과0건 표시 | C08 500 시 제목·URL 보존, 저장 버튼 잠금. 최신 저장 정보 조회·명시적 확인 후 저장 가능. C07 15초 시간 초과 안내 후 재시도 복구. 설명 실패 PARTIAL에서도 원본 링크 유지, 설명만 재생성해 COMPLETED 복구 |
| 06 | 실제 문의8건·통계 표시. 유형/검색어 필터 → 상세 → 목록 복귀 시 필터 유지. 초기화로8건 복구 | 시작일2026-10-23 / 종료일2026-10-19 입력 시 기간 역전 안내 | C09 지연 시 로딩과 새로고침 비활성 | 성공 응답 `[]`는 '아직 접수된 문의가 없습니다.'·0건. 필터 불일치는 '조건에 맞는 문의가 없습니다.'로 구분 | 500 시 오류·다시 시도, 통계는0이 아닌 `—`로 표시. 정상 응답 후8건 복구 |
| 07 | 실제 문의 ID로 상태 PATCH → C05 재조회. 메모 POST → 보호된 본문·체크 항목 표시·초안 초기화. 상세↔메모 경로 이동 | 상태 미선택·빈 메모 저장 거절 | PATCH 지연 중 상태·메모 입력과 두 저장 버튼 모두 비활성 | 저장 메모 `[]`는 '저장된 상담 메모가 없습니다.' | POST 500 시 초안·체크 유지, 자동 재전송 없이 잠금. 저장 결과 조회·편집 계속 확인 후 메모1건 추가 성공. 초기 C05 500 시 오류·재시도 표시, 편집 폼0개로 제출 차단 |
| 08 | 실제 C05 사건·조건·메모·첨부 표시. 익명 다운로드는 로그인 안내, 로그인 후 실제 TXT 다운로드 성공 | 문의/파일 ID·MIME·크기·다운로드 경로 등 계약 검증은 기존 자동/API 검사 근거 유지 | C05 로딩 표시. P05 지연 중 '파일을 준비하고 있습니다.'·다운로드 버튼 비활성 | 조건null·파일[]·메모[] 각각 없음 안내. 사실관계 등 다른 정보 유지 | C05 404 시 조회 오류·재시도, 정상 응답으로 자료 복구. P05 403은 첨부 영역 오류로 표시하며 사건·메모 유지 |

화면07의 입력 전화번호는 저장 후 마스킹되어 표시되는 것을 확인했다. 저장 실패 실험에서 C08·C11 오류 POST는 각각1회였고, 재전송은 사용자가 재조회·확인한 뒤 눌렀을 때만 발생했다. 이는 프런트의 중복 제출 방지 확인이며, 서버의 모든 쓰기에 영구 멱등 키가 구현됐다는 의미는 아니다.

P03은 요청1에서 수동 자료1건+검색 자료2건을 보존해 결과3건을 표시했다. 요청2의 설명 실패 후 재생성 기록은 **`explanation` 2회뿐이며 검색문 생성·외부 검색 호출은0회**였다([설명 재생성 호출 기록](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/traffic-explanation-retry.json)). 요청3은 모의 제공자의 빈 검색 결과를 실제 서버에 NO_RESULTS로 저장하고 화면에서도 확인했다. 모의 제공자 호출 기록은 모드 변경 시 초기화되므로 마지막 traffic.json의 provider.calls만으로 전체 실행 횟수를 계산하지 않는다.

| 구분 | 확인 방법·범위 |
|---|---|
| 브라우저 직접 확인 | 위 표의 정상·필수 입력·URL/순서·기간·지연·빈 응답·500/404/403·15초 시간 초과·복구·모바일51개 |
| 자동 검사로 유지한 범위 | 4000/4001자 등 길이 경계, 허용 enum/코드, 잘못된·중복·부모 ID, 오래된 응답 취소, 응답 유실·저장 후 재조회 실패, 로그인/파일 계약·추가 경로 거절. 이번에 전부 수동 재현한 것으로 집계하지 않음 |
| 오류 주입의 의미 | 프록시가 특정 경로·메서드에만 지연·빈 데이터·HTTP 오류를 제공. 실제 운영 장애 재현이나 유료 제공자 장애 검증은 아님 |
| 브라우저 콘솔 | 도구가 수집한 warn/error 스냅샷은0건. 의도적으로 주입한 HTTP 실패는 별도 traffic.json에 기록. 모든 브라우저·운영 환경에서 오류가 없다는 보장은 아님 |
| 시각 검수 | 데스크톱1672×941, 모바일390×844. 네 모바일 문서 폭375px로 페이지 전체 가로 넘침 없음. 긴 표는 컴포넌트 안에서 스크롤 가능 |

대표 증거: [05 부분 실패](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/05-partial.png), [05 시간 초과](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/05-timeout.png), [06 빈 목록](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/06-empty.png), [06 조회 오류](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/06-error.png), [07 메모 오류](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/07-memo-error.png), [08 빈 항목](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/08-empty.png), [08 다운로드 오류](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/08-download-error.png).

모바일 증거: [05](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/05-mobile.png), [06](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/06-mobile.png), [07](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/07-mobile.png), [08](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/08-mobile.png).

![화면05 실제 React·Spring·모의 제공자 검색 완료](../output/intake-05-08-browser/05-completed-desktop.png)

## 5. 빌드 · 테스트 · 실행 근거와 남은 작업

| 구분 | 통합 결과·실행 시점 |
|---|---|
| React·API·라우팅 | **이번 재실행** `npm run test:intake`: 135개 통과, 실패0. 공통01~04 회귀 포함 |
| 프런트 프로덕션 빌드 | **이번 재실행** `npm run build`: 통과, 93개 모듈. 브라우저는 Vite 개발 서버에서 확인했으며 dist 정적 호스팅 배포 검수는 아님 |
| 백엔드 전체·JAR | **직전 API 검수 결과 유지**: P03 기본값 수정 후169개 통과, 실패·오류·건너뜀0, bootJar 통과. 이번에는 해당 JAR를 실행했으며 백엔드 제품 코드를 변경하지 않아 전체 테스트를 중복 실행하지 않음 |
| 실제 로컬 Spring + React API 함수 | **직전 결과 유지**: `node --test --test-name-pattern 'Screen05\|Screen06\|Screen07\|Screen08' scripts/intake-live.test.mjs` 4개 통과. 결과·이력·상태·메모·파일 바이너리·401/403/404 경계 |
| 추가 직접 HTTP | **직전 결과 유지**: `scripts/intake-0508-audit.mjs` 20개 요청 관찰·단언. 공개 경로·C08 차이는 위 발견 사항으로 유지 |
| 이번 실제 브라우저 | 51개 고유 시나리오 통과. 프록시 API 기록94개(폴링·재조회·주입 실패 포함). 호출94개를 독립 테스트94개로 계산하지 않음 |
| 이번 실행 환경 | Vite5186 → 검수 프록시8087 → Spring18085, 격리 H2 `browser0508`, 제공자 모의18181. 가상 문의8건·검색 요청4건·가상 첨부33바이트 |
| 검수 지원 코드 | `scripts/intake-0508-browser-qa.mjs` 추가, `node --check` 통과. 루프백 전용 데이터 준비·오류 주입·호출 기록 도구이며 제품 코드에서 import하지 않음 |

**남은 작업은 운영 접근 제어(P1), C08 서버 URL·양의 순서 검증(P2), 실제 외부 제공자 키 설정 후 호출·품질 확인이다.** 이 항목들은 빌드나 브라우저 통과로 해소되지 않는다. 상태 변경 영구 감사 이력·실제 작성자/수정일·서버 전체 검색/페이지네이션·파일 업로드 UI는 기존과 같이 구현 완료 범위 밖이다.

이번 근거: [통합 검증 JSON](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/verification.json), [브라우저51개 결과](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/browser-checks.json), [API94개 호출 기록](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/traffic.json), [콘솔 수집 기록](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-browser/console.json).

이전 근거: [API 검수169/135/4/20 결과](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-recheck/verification.json), [직접 HTTP 인증·검증 기록](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-recheck/auth-and-validation.json), [실제 API 개별 증거](/C:/myLectureWs/legal-ai-intake-security-server/output/intake-05-08-recheck/live-api/).

검수 전용 서버는 확인 후 종료했다. 기본 개발 서버·기존 DB에 검수용 데이터나 위험 URL을 추가하지 않았다. 이번 변경은 검수 지원 스크립트·통합 문서·증거 파일이며 제품 동작 수정은 없다.
