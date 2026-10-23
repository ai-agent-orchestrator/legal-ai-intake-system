# 화면01~04 API·React·실행 동작 통합 검수

> **2026-10-23 권한 후속 반영:** C03/C04 및 저장 문의 API는 JWT 필수다. 소유자·배정 LAWYER·ADMIN 범위를 서버에 적용했고, 상태/내부 메모는 담당자·ADMIN만 허용한다. 의뢰인 응답에서 내부 메모를 제외한다. ADMIN 배정 API C21과 nullable DB 컬럼2개를 추가했다. 마지막 실행은 백엔드210개·문의142개·운영자35개 및 브라우저 권한14개 통과, 양쪽 빌드 성공이다. 아래 이전 회차의 공개/익명 인증 판정과 테스트 수치는 당시 기록이며 최신 인증 계약은 [권한 통합 검수](SCREEN04_08_ACCESS_CONTROL_REVIEW.md)를 따른다. 전체 과거 브라우저 시나리오를 새로 실행한 것은 아니다.


검수일: 2026-10-21 (Asia/Seoul). 기존 API·요청/응답 필드·ID·인증·React 검수에 이번 빌드·브라우저·입력 검증·로딩·빈 상태·오류 복구 검수를 통합했다. 현재 Spring Controller·DTO·SecurityConfig·React 코드를 기준으로 하며, 사용자 첨부 매핑은 연결 범위의 기준으로 사용했다. 첨부 자료만으로 실행 성공을 판정하지 않았다.

## 결과

- 화면01~04의 서로 다른 **12개 API**가 실제 연결되어 있다. 화면03·04의 R01은 서버가 반환한 절차 코드를 얻는 공통 보조 조회다.
- `npm run test:intake`: **이번 재실행 135/135 통과**. API·모델·React 컴포넌트 검증이며 다른 intake 화면의 공유 회귀도 포함한다. 이전 통합 검수의128개와 구분한다.
- 화면01~04에 한정한 실제 서버 테스트: **이번 재실행 13/13 통과**. 화면05~08의 별도 시나리오는 선택하지 않았다. 기존 화면04→05 ID 인계 테스트에 포함된 검색 요청 경계 확인은 수행됐다.
- `npm run build`: **성공**, 93개 모듈, viewer/operator/intake 3개 진입점 생성.
- 브라우저: **50개 고유 시나리오 통과**(01=9, 02=8, 03=15, 04=18). 빌드된 `dist`를 Vite preview로 제공해 실제 격리 Spring 서버와 연결했다. 로그인→분류→문의 작성→실제 접수 ID `5`→안내·이전 왕복, 형사 문의 `1`의 R06, 실제404 및 지연·빈 응답·500·잘못된JSON·15초 시간 초과 복구를 확인했다. 모든 입력은 가상 검수 자료다.
- 네 화면 모두 데스크톱1672×941·모바일390×844에서 확인했다. 모바일 문서 폭은375px로 페이지 전체 가로 넘침이 없었다.
- 인증의 구현과 인가의 범위는 다르다. **P01은 JWT 필수, C03은 선택 JWT, C05는 현재 공개 상세 조회**다. 문의 소유자만 상세를 조회하도록 제한하는 정책은 아직 적용되지 않았다.

이번 정상 동작은 실제 React·Spring·격리 H2 DB로 확인했고, 지연·빈 데이터·HTTP 오류·잘못된JSON은 검수 전용 프록시에서 주입했다. 이번 검수에서 새 제품 결함은 확인되지 않았지만 **C05 접근 제어, C04 서버 입력 제한, C03 서버 멱등성은 기존 미해결 항목**이다. 로딩·오류 검수 통과가 운영 보안 완료를 의미하지 않는다.

개별 화면 문서와 이전 증거의 테스트 수치는 해당 회차 기록으로 유지한다. 이전 전체 실연동14/17에서 미확인됐던 화면03 참고 API·화면04 C04/R06는 통합 범위의 실제 서버13개 검증에서 확인했고 이번에도 통과했다. 이전 JVM 종료의 근본 원인이나 장기 운영 안정성을 검증한 것은 아니다.

## 동작별 API·요청·응답·구현

| 화면·동작 | API | 요청 필드·식별자 | 성공 응답·화면 처리 | React 연결·판정 |
|---|---|---|---|---|
| 01 로그인 | C01 `POST /api/auth/login` | JSON `username, password`. 이전 JWT 없이 호출 | 200 `accessToken, username, role`. P01 확인 전에는 세션 확정 안 함 | `LoginForm` → `authenticate` → `api.login`. 실연동 통과 |
| 01 계정 확인 | P01 `GET /api/me` | C01의 토큰을 Bearer 헤더로 전달. 본문·query 없음 | 200 `username, role`. C01과 두 값이 같아야 로그인 완료 | `authenticate` → `api.currentUser`. USER/ADMIN·401 검증 통과 |
| 02 사건 유형 확인 | C02 `POST /api/intakes/classify-preview` | JSON `caseType, coreStatement`. 문의 목적은 이 API의 필드가 아니며 React에서 보존 | 200 `caseType, caseTypeLabel, disputeArea, recommendedDocuments[], clientGuideMessage, classificationMethod, persisted`. `persisted=false`; 문의 ID 없음 | `ClassificationPage` → `api.classify`. 규칙 분류·미저장 확인 |
| 02/03/04 참고 절차 목록 | R01 `GET /api/legal/procedures` | `?domain={caseType}`. `UNKNOWN`은 query 없이 전체 조회 | 200 `{code, domain, title, summary, userRole}[]`. 목록의 실제 code 선택 | `ProcedurePanel` / `IntakeReference` / `RelatedProcedures` → `api.procedures`. 4개 분야 확인 |
| 02/03/04 절차 상세 | R02 `GET /api/legal/procedures/{code}` | R01의 code를 URL 인코딩. 기대 domain도 응답 검증에 사용 | 200 `code, domain, title, summary, userRole, steps[], forms[], knowledgeGraphEdges[]`. 단계·참고 안내 표시 | 각 `ProcedureDetail` / `ReferenceBody` → `api.procedure`. code/domain 일치 검증·실연동 통과 |
| 03 체크리스트 펼치기 | R03 `GET /api/legal/procedures/{code}/steps` | 같은 R01 code. 본문 없음 | 200 `{stepOrder, stepCode, title, description, checklist[]}[]`. 실제 stepCode로 단계 선택 | `Checklist` → `api.procedureSteps`. 체크 상태는 현재 화면에서만 유지 |
| 03 관련 양식 목록 | R04 `GET /api/legal/forms` | `?procedure={R01.code}` | 200 `{procedureCode, formCode, title, fileType, usageNote}[]`. 부모 procedureCode 대조 | `Forms` → `api.forms`. 실연동 통과 |
| 03 선택 양식 안내 | R05 `GET /api/legal/forms/{formCode}` | R04의 formCode를 URL 인코딩. 부모 code도 검증에 사용 | 200 R04와 같은 단일 DTO. formCode·procedureCode 대조 후 안내 표시 | `FormDetail` → `api.form`. 다운로드 URL/API가 없어 안내만 제공 |
| 03 문의 접수 | C03 `POST /api/intakes` | 아래 접수 DTO. 로그인 시 Bearer 헤더, 없으면 비회원 접수 | 201 `LegalIntakeResponse`. 실제 응답 `id`로 화면04 이동. 자료 종류와 검색 조건 함께 저장 | `IntakeWritePage` → `intakePayload` → `api.create` → `IntakeApp.created`. 실연동 통과 |
| 04 저장 문의 조회 | C05 `GET /api/intakes/{id}` | C03 응답 id 또는 경로 id. 로그인 시 선택 Bearer | 200 `LegalIntakeResponse`. 요청 id와 응답 id, 타입·상태·시각·표시 필드를 대조 | `IntakeReceiptPage` / `IntakeReviewPage` → `api.receiptIntake`. 비회원 조회·새로고침도 실제 확인 |
| 04 간단 안내 | C04 `POST /api/legal-intake/minimal` | C05의 보호된 값으로 구성한 10개 필드. 아래 변환표 참고 | 200 아래 안내 DTO. 새 문의 ID·저장·LLM 실행 없음 | `SimpleGuide` → `minimalPayload` → `api.minimal`. 저장 문의 불변 확인 |
| 04 형사 참고 안내 | R06 `GET /api/legal/criminal/checklist/supplementary-investigation` | 형사 문의에서만 호출. 문의 ID·본문 없음 | 200 `domain, role, stageCode, stageLabel, checklist[], recommendedAction`. `CRIMINAL/COMPLAINANT/SUPPLEMENTARY_INVESTIGATION_AFTER_TRANSFER` 확인 | `CriminalChecklist` → `api.criminalChecklist`. 실제 참고 범위·브라우저 표시 확인 |

R02/R03의 `steps[]`는 `stepOrder, stepCode, title, description, checklist[]`이고, R02의 `forms[]`는 R04/R05와 같은 양식 DTO다. `knowledgeGraphEdges`는 서버 부가 필드로 수신하지만 이 화면에서는 표시하지 않는다. R06은 현재 문의의 당사자·진행 단계를 판정한 결과가 아니라 고소인·검찰 송치 후 보완수사 참고 자료로 표시한다.

### C03 접수 필드와 한도

| 구분 | 필드 | 검증·전달 |
|---|---|---|
| 필수 | `inquiryPurpose(100), caseType(50), partyPosition(50), coreStatement(100), desiredNextStep(100)` | 빈 값·길이 검사 후 전달. caseType은 지원하는 5개 값 확인 |
| 선택 사실관계 | `incidentDate(30), counterpart(100), amountOrDamage(100), currentStage(100)` | 값이 있을 때 전송. 프런트는 실제 날짜 형식도 확인 |
| 선택 연락 정보 | `clientName(100), clientPhone(50), preferredRegion(100)` | 전화번호는 숫자 7~15자리와 허용 기호 검사 |
| 선택 검색 조건 | `officeType(100), experienceLevel(100), publicCareerPreference(150), consultationStyle(150), lawyerSearchPurpose(300)` | 접수 DTO로 전달. 조건 응답은 `lawyerSearchCondition`에 포함; 검색 목적은 문의 레코드에 저장 |
| 보유 자료 종류 | `documents[]` 각 문자열 최대 100 | 중복 제거. ‘없음’ 단독 선택 시 빈 배열. 파일 업로드를 의미하지 않음 |
| 서버 선택 필드 | `sessionId(100)` | 현재 화면에 입력 UI가 없어 C03에서 생략. 서버 기본값 `anonymous` 사용. JWT 소유자 계정과 별개 |

괄호 숫자는 Java/HTML과 같은 UTF-16 길이다. 2026-10-22 후속 변경으로 C02와 C03의 사건 본문은 모두 최대 100자로 통일했다. Java C03 DTO의 `@Valid/@NotBlank/@Size`와 프런트 검증을 대조했다. 날짜·전화 형식은 프런트가 추가로 확인한다. UI 상태·분류 객체·가짜 id·파일 데이터를 C03에 섞어 보내지 않는다.

### C03/C05 응답 필드

```text
id, sessionId, clientNameMasked, clientPhoneMasked,
inquiryPurpose, caseType, caseTypeLabel, partyPosition, incidentDate,
counterpartMasked, amountOrDamage, currentStage, coreStatementMasked,
desiredNextStep, preferredRegion, disputeArea,
selectedDocuments[], recommendedDocuments[], lawyerSearchCondition,
clientGuideMessage, privacyNotice, status, createdAt,
consultationMemos[], uploadedDocuments[]
```

`lawyerSearchCondition`은 `id, intakeId, legalField, disputeType, preferredRegion, officeType, experienceLevel, publicCareerPreference, consultationStyle, sourcePolicy, searchQuery, createdAt`이다. `intakeId`를 부모 문의와 대조한다. 이름·전화·상대방·핵심 내용은 서버의 보호 처리 결과 필드로 표시한다. 접수 직후 `uploadedDocuments`는 빈 배열이고 보유 자료 선택을 실제 업로드 건수로 표현하지 않는다.

`api.create`는 생성 응답의 양의 유효 id를 확인해 경로를 결정한다. 나머지 표시 필드는 C05를 다시 조회하면서 검증한다. C03 응답 전체의 모든 부가 필드를 프런트 생성 함수가 검증한다고 기재하지 않는다. C05가 잘못되면 안내를 성공으로 꾸미지 않고 오류·재시도를 표시한다.

### C04 요청 변환

| 요청 필드 | 실제 원천 |
|---|---|
| `sessionId` | C05 sessionId 또는 빈 문자열 |
| `inquiryPurpose` | C05 inquiryPurpose |
| `caseType` | C05 caseType |
| `partyRole` | C05 **partyPosition** |
| `incidentDate` | C05 incidentDate 또는 빈 문자열 |
| `counterpart` | C05 **counterpartMasked** 또는 빈 문자열 |
| `amountOrDamage` | C05 amountOrDamage 또는 빈 문자열 |
| `documents` | C05 **selectedDocuments**, 없음 표시 제외 |
| `desiredNextStep` | C05 desiredNextStep |
| `coreFact` | C05 **coreStatementMasked** |

C04 응답: `sessionId, inquiryPurpose, caseType, caseTypeLabel, partyRole, disputeArea, checkedFacts[], recommendedDocuments[], desiredNextStep, clientGuideMessage, privacyNotice, securityNotice`.

요청의 세션·목적·역할·다음 단계와 응답을 대조하고 배열·문자열 형식을 검증한다. 저장된 유형이 명확하면 응답 유형도 같아야 한다. 저장 유형이 UNKNOWN이면 규칙 추론을 허용한다. C04에는 현재 Bean Validation 어노테이션·Controller `@Valid`가 없다. 화면04는 검증된 C05로부터 요청을 만들지만 독립 API의 서버 입력 제한 보강은 별도다. `securityNotice`를 LLM/Guard 실행이나 DB 암호화의 증거로 사용하지 않는다.

## 인증·ID 전달

| API | 현재 서버 정책 | 프런트 전달·검수 |
|---|---|---|
| C01 | 공개 로그인 | username/password만 전송. 빈 값 400·잘못된 계정 401 확인 |
| P01 | JWT 필수 | Bearer 헤더. 비회원·잘못된 토큰 401, USER/ADMIN 정상 응답 확인 |
| C02, C04, R01~R06 | 공개 | JWT를 보내지 않음. 공개 호출 성공 확인 |
| C03 | JWT 선택 | 로그인 시 Bearer, 비회원 허용. Authorization을 보냈는데 인증 실패하면 401이며 익명 접수로 바꾸지 않음 |
| C05 | 공개 | 로그인 시 선택 Bearer. 로그인 여부·소유자와 무관하게 조회 가능한 현재 정책을 실제 비회원 새로고침으로 확인 |

C01/P01의 계정 식별자는 `username`이다. 숫자 userId나 변호사 ID는 없다. 현재 서버 역할은 ROLE_USER/ROLE_ADMIN이며 ROLE_LAWYER는 없다. 데모 버튼은 공개 화면으로 이동하며 권한을 만들지 않는다. JWT는 탭 메모리에 유지하고 URL·스토리지·검수 JSON에 저장하지 않는다. 새로고침하면 로그인 상태가 초기화된다. 로그아웃은 메모리 정리이며 서버 토큰 폐기 API는 없다.

```text
01 C01.accessToken → P01 Bearer → username/role 일치 → 메모리 세션
02 C02(미저장, id 없음) → React draft/preview → #/intake/new
03 C03.id → #/intake/{id} → 04 C05 요청 id = 응답 id
04 이전 → #/intake/{같은 id}/review → 같은 안내 화면
04 검색 준비 → #/intake/{같은 id}/search
R01.code → R02/R03/R04.procedure → R04.formCode → R05
```

`sessionId`, 문의 id, 절차 code, 양식 formCode는 서로 바꾸지 않는다. 문의 번호를 이미지의 INQ-2024-00123으로 하드코딩하지 않고 실제 id를 표시한다. 화면04로 이동·다시 읽기·안내 생성은 C03 재등록을 수행하지 않는다.

## React 공통 디자인·컴포넌트·라우팅

| 영역 | 실제 구현 |
|---|---|
| 공통 디자인 | `BrandMark`, `Icon`, 실제 사무실 배경 이미지와 공통 intake CSS. 02~04 `IntakeHeader`, `Steps`, 패널·버튼·피드백 재사용 |
| 로그인 공통화 | `LoginPage`와 `LoginDialog`가 같은 `LoginForm`·`authModel` 사용. 모달 로그인 후 작성 폼·현재 경로 유지 |
| 데이터 계층 | `api.js`가 URL 인코딩·선택/필수 JWT·JSON/응답 검증·HTTP 오류·15초 제한·AbortSignal 처리 |
| 분류/작성 상태 | `IntakeApp`에서 draft/preview/intakeForm 보존. 같은 분류 반복 시 03의 긴 수정 내용을 덮지 않음. 03 직접 진입은 분류 안내 표시 |
| 참고 영역 | `IntakeReference`의 절차·체크리스트·양식 컴포넌트. 실제 code 사용, 빈 목록 안내, 개별 GET 재시도 |
| 접수 결과 | `IntakeReceiptPage`, `IntakeReviewPage`, `IntakeSummary`, `ProtectedStatement`, `SimpleGuide`, `RelatedProcedures`, `CriminalChecklist` |
| 라우팅 | 기본 진입/`#/login`, `#/account`, `#/intake/type`, `#/intake/new`, `#/intake/{id}`, `#/intake/{id}/review`. 미지원 경로는 명시적 안내 |
| 상태 처리 | 요청 중 로딩·제출 잠금·중복 제출 방지, 화면 이동/입력 변경 시 취소, 이전 응답이 다른 문의 화면을 덮지 않음 |

이전 통합 API 검수에서 수정했고 이번 자동 회귀에서도 유지 확인한 부분:

1. 화면02·03의 R02 요청도 R01에서 선택한 행의 **domain**을 전달해, code가 같아도 다른 분야의 상세가 반환되면 오류로 처리한다. 화면04와 같은 정책으로 맞췄다.
2. 화면02·03에서 UNKNOWN 전체 분야 참고 조회임을 명시하고, 참고 절차 선택이 사건 유형 확정이 아님을 표시했다.

두 경우는 React 회귀 검증에 포함되어 있다. 잘못된 상세는 표시하지 않고 참고 API 실패가 문의 작성 자체를 막지 않는 것도 확인했다. 이번 추가 작업에서는 제품 코드를 바꾸지 않고 검수 지원 스크립트·증거·문서만 추가/갱신했다.

## 빌드·실제 API 재검증

아래 명령은 프런트 디렉터리 기준이며 **이번 회차에 모두 재실행했다.**

```powershell
npm run test:intake
npm run build
$env:INTAKE_TEST_API_BASE='http://127.0.0.1:18086'
node --test --test-name-pattern '^(Screen0[14] |real )' scripts/intake-live.test.mjs
```

| 구분 | 이번 결과·범위 |
|---|---|
| React·API·모델·라우팅 | **135개 통과 / 실패0**. 화면01~04와 다른 intake 화면의 공통 회귀 포함 |
| 프로덕션 빌드 | **성공**, 93모듈. viewer/operator/intake 진입점 생성 |
| 실제 로컬 Spring API | **13개 통과 / 실패0**. 가상 데이터만 사용 |
| 실제 브라우저 | **50개 고유 시나리오 통과**. 아래 화면별 행렬·JSON에 상세 기록 |
| 프록시 API 기록 | **84개 요청**. 페이지 왕복·재조회·지연·주입 오류 포함이며 독립 테스트84개라는 의미는 아님 |
| 브라우저 실행 환경 | 빌드된 dist → Vite preview5187 → 검수 프록시8088 → Spring18086, H2 메모리DB `browser0104`. 외부 제공자 호출 없음, 검색 실행 비활성 |
| 검수 지원 코드 | `scripts/intake-0104-browser-qa.mjs`, `node --check` 통과. 루프백 전용이며 제품 코드에서 import하지 않음 |

실연동13개는 C01/P01 USER·ADMIN, 틀린 계정·비회원 P01·잘못된 JWT·빈 로그인, C02 미저장/5개 유형/400, R01/R02 전 분야, 인증 C03→C05 ID·필드 보존, C03 잘못된 인증401/미저장, 없는 C05의404, R02/R03/R04/R05 전 분야 코드 연결, C04의5개 유형·저장 문의 불변, 형사 R06을 검증했다. 이전 전체17개를 재실행한 결과는 아니다.

## 브라우저·입력·로딩·빈 상태·오류 복구

| 화면 | 정상 동작·라우팅 | 입력 검증 | 로딩·중복 조작 | 빈 상태 | 오류·복구 |
|---|---|---|---|---|---|
| 01 · 9개 | 실제 C01→P01 성공 후 화면02 이동, 실제 계정명 표시 | 빈 아이디 로그인 요청 차단. 실제 잘못된 비밀번호401은 오류 표시·아이디 유지·비밀번호 초기화 | C01 지연은 '로그인 중', P01 지연은 '계정 정보 확인 중'. 입력·로그인 버튼 비활성 | 미로그인 초기 화면은 로그인·공개 데모 안내. 로그인 응답의 필수값 누락은 정상 빈 목록이 아니며 자동 계약 검사에서 거절 | P01 500 시 로그인 화면에 남고 비밀번호 초기화. 정상 재로그인 후 계정 확인·화면02 이동 복구 |
| 02 · 8개 | 실제 민사 분류·RULE_BASED·미저장 표시, R01/R02 참고 안내, 화면03 인계 | 빈 현재 상황 제출 차단. 입력 최대300 및300/301 경계는 기존 자동 검사로 유지 | C02 지연 시 결과 영역 로딩, 분류·다음 단계 버튼 잠금 | 분류 전 도움말과 R01 `[]`의 '등록된 참고 절차가 없습니다.'를 구분 | C02 500 시 현재 상황 보존·오류·재시도. 정상 응답 복구 후 다음 단계 이동 가능 |
| 03 · 15개 | 분류한 유형·내용 인계, 선택 필드 편집·R02/R04/R05 실제 표시, R03 체크 선택은 접기/펼치기 후 유지. C03 성공 후 실제 ID5로04 이동 | 당사자/다음 단계 누락·잘못된 전화번호 `abc` 차단. '계약서' 선택 후 '없음'은 다른 자료 해제. 분류 없이 직접 진입하면02 안내 | R01 로딩 중 작성 폼 유지. C03 지연 시 fieldset·이전·접수 버튼 잠금 | R01 `[]`, R03 단계 `[]`, R04 양식 `[]` 각각 빈 안내. 참고 자료가 없어도 작성 가능 | C03 500 시 작성 내용·지역·선택값 보존, 자동 POST 재전송 없음. R01 500도 접수 버튼을 막지 않음. 참고 재조회 복구 및 사용자 재접수 성공 |
| 04 · 18개 | C05 ID5·보호된 값·C04 안내 표시, 핵심 내용/절차 펼치기, review↔같은 안내 ID 왕복. 형사 ID1의 실제 R06 참고 범위 표시 | 읽기 화면이며 별도 제출 입력 없음. C05 잘못된 JSON은 접수 성공 제목을 표시하지 않고 오류로 거절 | C05 로딩과 C04 간단 안내 로딩을 별도 표시 | C04의 checkedFacts/recommendedDocuments `[]`, R01 `[]` 각각 없음 안내. 접수 본문 유지 | C04 500 후 안내만 재시도 복구. C05 잘못된JSON·실제404·15초 시간 초과 안내와 재시도. 정상 재조회 후 같은 ID5 복구 |

**브라우저 직접 조작과 자동 검사의 범위를 구분한다.** 300/301·2000/2001자 등 길이 경계, 날짜 형식, 지원하지 않는 enum/코드, 다른 부모 ID·분야·양식, 취소·늦은 응답, 추가 경로, 400/403/429 등은 자동 검사의 근거를 유지한다. 이번에 해당 조합을 모두 수동 입력하거나 운영 서버의 장애를 재현한 것으로 집계하지 않았다.

지연은 검수 프록시에서5초, 시간 초과는17초 응답 지연으로 주입했다. 프런트의15초 제한 이후 로딩이 종료되고 오류·다시 시도가 표시되는 것을 확인했다. 주입 오류는 실제 운영 장애가 아니며, 이 경로에서의 UI 처리를 검증한 것이다.

C03 기록은 오류 주입 POST1회(500·백엔드 미전달), 사용자가 다시 누른 POST1회(실제201)다. 이후 화면04 조회·간단 안내·이전 화면 왕복은 C03 재등록 없이 처리됐다. **프런트 중복 클릭 방지와 서버 멱등성은 별개**이며, 응답 유실 후 수동 재접수에 대한 중복 저장 보장은 남아 있다.

| 공통 검수 | 확인 결과 |
|---|---|
| 데스크톱·모바일 | 1672×941·390×844에서 네 화면 확인. 모바일 scrollWidth375≤innerWidth390. 긴 폼·참고 자료는 세로 스크롤, 내용·버튼 접근 가능 |
| 공통 디자인 | 브랜드·배경·단계·패널·피드백·버튼 재사용 및 모바일 배치 확인. 첨부 시안과 픽셀 단위 일치를 수치화한 검사는 아님 |
| 콘솔 | 도구가 수집한 최종 warn/error 스냅샷0건. 의도적 HTTP 실패는 traffic.json으로 별도 기록. 모든 브라우저에서 오류가 없다는 보장은 아님 |
| 집계 방식 | 54회 기록 중 고유 시나리오50개. 초기 기대 문구/응답 시점 오류4회는 실제 표시를 확인하고 수정·재실행했으며 마지막 결과를 집계. 제품 결함으로 분류하지 않음 |
| 제품 변경 | 이번 추가 검수에서 제품 코드 수정 없음. 이전 API·React 수정과 기존 미해결 항목을 유지 |

이번 증거는 [output/intake-01-04-browser](../output/intake-01-04-browser/)에 보관했다. [통합 검증 JSON](../output/intake-01-04-browser/verification.json), [브라우저50개 결과](../output/intake-01-04-browser/browser-checks.json), [API84개 기록](../output/intake-01-04-browser/traffic.json), [콘솔 수집 기록](../output/intake-01-04-browser/console.json), [실제 API 증거 사본](../output/intake-01-04-browser/live-api/)을 함께 확인할 수 있다. 호출 기록에는 메서드·경로·상태·주입 시나리오만 남기고 요청 본문·JWT·비밀번호는 저장하지 않았다. 입력/화면 증거의 이름·전화·사건 내용은 가상 자료다.

대표 오류 증거: [01 계정 확인 실패](../output/intake-01-04-browser/01-profile-error.png), [02 분류 실패](../output/intake-01-04-browser/02-error.png), [03 접수 실패](../output/intake-01-04-browser/03-create-error.png), [03 빈 체크리스트](../output/intake-01-04-browser/03-checklist-empty.png), [04 빈 안내](../output/intake-01-04-browser/04-guide-empty.png), [04 잘못된 응답](../output/intake-01-04-browser/04-malformed.png), [04 시간 초과](../output/intake-01-04-browser/04-timeout.png).

모바일 증거: [01](../output/intake-01-04-browser/01-mobile.png), [02](../output/intake-01-04-browser/02-mobile.png), [03](../output/intake-01-04-browser/03-mobile.png), [04](../output/intake-01-04-browser/04-mobile.png).

![실제 빌드 화면04 접수·안내 결과](../output/intake-01-04-browser/04-completed-desktop.png)

이전 근거는 [output/intake-01-04](../output/intake-01-04/)에 유지했다. 이전128개·접수ID4·형사ID2·브라우저 새로고침/비회원 조회는 그 회차 기록이며, 이번135개·접수ID5·형사ID1과 구분한다.

## 남은 제한

- C05의 소유자/역할 인가가 없다. 마스킹·로그인 UI만으로 비공개 문의 접근 제어가 완료됐다고 볼 수 없다. 이번 검수에서 서버 공개 정책을 바꾸지 않았다.
- C04는 별도 서버 입력 제한이 없고, C02/C04는 규칙 기반 처리다. AI/LLM 실행·DB 암호화를 검증한 작업이 아니다.
- C03은 중복 클릭을 막지만 서버 idempotency key 계약은 없다. 응답 유실 후 수동 재접수까지 중복 저장을 보장해 막는 기능은 없다. 자동 재전송은 하지 않는다.
- H2 메모리 DB는 서버 재시작 시 사라진다. 이번 문의ID1~5는 격리 DB의 임시 가상 레코드이며 증거 PNG/JSON을 기준으로 확인한다. 검수 전용 서버와 탭은 종료했고 기본 개발 서버·기존 DB는 변경하지 않았다. 이전 JVM 비정상 종료의 근본 원인과 운영 환경 장기 안정성은 이번 통과 결과에 포함하지 않는다.
- 빌드 산출물의 로컬 preview는 확인했지만 운영 배포 서버의 정적 파일·프록시·보안 헤더·실제 계정/권한 검증은 별도다. 화면05~08·운영자 전체 브라우저 회귀는 이번 화면01~04 범위 밖이다.
