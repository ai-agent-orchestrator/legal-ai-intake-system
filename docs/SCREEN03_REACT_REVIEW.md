# 화면03 React · API 대조 및 개발 검수

> 2026-10-22 후속: 현재 분류·접수 사건 본문 한도는 **100자**다. 아래 300/2,000자·2,001자 기록은 최초 검수 당시 기준이며 현행 한도가 아니다. [현재 적용·검수](CASE_STORY_100_CHAR_REVIEW.md).

> 2026-10-21 화면01~04 통합 재검수: **공유 API/React 128개·실제 API 13개·빌드 통과**. 로그인→접수→안내 브라우저 흐름과 4개 화면 반응형 확인. 02·03 R02 분야 대조와 미분류 참고 안내 보완. C05는 현재 공개 조회이며 소유자 인가는 별도다. [이번 통합 필드·ID·인증·구현 검수](SCREEN01_04_INTEGRATION_REVIEW.md). 아래 개별 수치·미확인 기록은 이전 회차의 기록이다.

검수일: 2026-10-21. 실행 경로: `http://127.0.0.1:5175/intake.html#/intake/type` → 분류 확인 → **다음: 문의 작성**.

화면03은 `#/intake/new`이다. 첨부 디자인의 좌측 전체 입력 폼과 우측 절차·체크리스트·양식을 구현했다. 기존 화면02의 배경·헤더·아이콘·진행 단계·API 오류 컴포넌트를 공유한다. 화면02 입력은 300자, 화면03의 수정 가능한 핵심 내용은 C03 DTO에 맞춰 **2,000자**까지 입력한다.

**판정:** 화면03 구현과 현재 API 계약에 대한 기능 검수 통과. 빌드, 자동 테스트 52개, 실제 브라우저의 데스크톱·모바일·등록·입력 오류·지연·서버 오류·빈 응답을 확인했다. 기존 문의 조회 C05/C09의 소유자 인가는 아직 서버에 적용되지 않았으므로 운영 배포 보안 검수까지 완료했다는 뜻은 아니다.

## 1. 동작별 API · 코드 · 인증 · ID

첨부 표의 화면03 핵심 API는 C03/R02/R03/R04/R05 다섯 개다. 화면02에서 재사용하는 R01은 실제 `code`를 얻기 위한 보조 조회로 추가 사용한다. 로그인 C01과 등록 후 C05는 공통·다음 화면 연결이다.

| 동작 | 요청 및 입력 | 응답·화면 사용 | React → Controller → Service | 인증·구현 |
|---|---|---|---|---|
| 참고 절차 선택 | R01 `GET /api/legal/procedures?domain={caseType}`. UNKNOWN은 domain 생략 | `code, domain, title, summary, userRole` 목록. 여러 개면 선택 UI, 없으면 빈 안내 | `api.procedures` → `LegalProcedureController.procedures` → `findProcedures` | 공개 GET, 구현 |
| 관련 절차 안내 | R02 `GET /api/legal/procedures/{code}` | `code,domain,title,summary,userRole,steps[],forms[],knowledgeGraphEdges[]`. 제목과 실제 단계 표시 | `api.procedure` → `procedureDetail` → `findProcedureDetail` | 공개 GET, 구현 |
| 단계별 체크리스트 펼치기 | R03 `GET /api/legal/procedures/{code}/steps` | 단계별 `stepOrder,stepCode,title,description,checklist[]`. 선택한 단계의 준비 항목 표시 | `api.procedureSteps` → `procedureSteps` → `findProcedureSteps` | 공개 GET, 최초 펼침 때 조회, 구현 |
| 관련 양식 목록 | R04 `GET /api/legal/forms?procedure={code}` | `procedureCode,formCode,title,fileType,usageNote` 목록 | `api.forms` → `forms` → `findForms` | 공개 GET, 구현 |
| 양식 선택·상세 | R05 `GET /api/legal/forms/{formCode}` | 동일한 양식 DTO. 제목·용도·파일 형식 표시 | `api.form` → `form` → `findForm` | 공개 GET, 구현 |
| 문의 접수하기 | C03 `POST /api/intakes`, JSON은 아래 필드 표 참고 | **201** `LegalIntakeResponse`. 반환된 `id`로만 `#/intake/{id}` 이동 | `IntakeWritePage.submit` → `intakePayload` → `api.create` → `LegalIntakeRecordController.create` → `LegalIntakeRecordService.create` | JWT 선택. 로그인하면 `Authorization: Bearer …`, 비회원은 헤더 생략. 유효하지 않은 인증을 공급하면 401. 구현 |
| 접수 결과 확인 | C05 `GET /api/intakes/{id}` | 실제 ID 일치 검사 후 `caseTypeLabel,inquiryPurpose,coreStatementMasked,clientGuideMessage,privacyNotice` 표시 | `IntakeReceiptPage` → `api.intake` → `findById` → `findById` | 현재 공개 API. 로그인 시 토큰 전달. 연결 구현 / 소유자 인가 미적용 |

ID 흐름:

```text
화면02 C02 응답(caseType, 준비 자료 등 / id 없음)
  → 화면03 초깃값 설정
  → R01의 실제 code → R02·R03의 path / R04의 procedure query
  → R04의 실제 formCode → R05의 path
  → C03의 서버 생성 id → #/intake/{id} → C05의 path
```

모든 path/query 값은 URL 인코딩한다. 양식 응답의 `procedureCode`와 `formCode`가 요청과 다르면 정상 안내로 표시하지 않는다. 사건 유형 변경 시 이전 참조 조회를 중단하고 오래된 절차·양식을 제거한다.

## 2. C03 요청 필드 대조

| 화면 입력 | JSON 필드 | 필수·최대 길이 | 처리 |
|---|---|---|---|
| 문의 목적 | `inquiryPurpose` | 필수 100 | 화면02 값으로 초기화, 선택 수정 |
| 사건 유형 | `caseType` | 필수 50 | CIVIL / CRIMINAL / FAMILY / ELECTRONIC_LITIGATION / UNKNOWN |
| 당사자 위치 | `partyPosition` | 필수 50 | 라디오 7개, 미선택 차단 |
| 핵심 내용 | `coreStatement` | 필수 2,000 | 화면02 내용으로 초기화, 수정·글자 수 표시 |
| 원하는 다음 단계 | `desiredNextStep` | 필수 100 | 선택 입력 |
| 사건 발생일 | `incidentDate` | 선택 30 | 날짜 입력, 프런트에서 유효한 YYYY-MM-DD 확인 |
| 상대방 | `counterpart` | 선택 100 | 개인·회사명 입력 |
| 금액·피해 규모 | `amountOrDamage` | 선택 100 | 통화·피해 설명을 포함할 수 있는 문자열 |
| 현재 진행 단계 | `currentStage` | 선택 100 | 선택 입력 |
| 이름 | `clientName` | 선택 100 | 계정 이름을 개인 이름으로 자동 대입하지 않음 |
| 연락처 | `clientPhone` | 선택 50 | 프런트에서 7~15자리 숫자, 하이픈·공백·괄호·국가번호 허용 |
| 선호 지역 | `preferredRegion` | 선택 100 | 검색 조건에도 저장 |
| 사무소 유형 | `officeType` | 선택 100 | 접힌 검색 선호 영역 |
| 경력 조건 | `experienceLevel` | 선택 100 | 같은 영역 |
| 공직·수사기관 경력 선호 | `publicCareerPreference` | 선택 150 | 같은 영역 |
| 상담 방식 | `consultationStyle` | 선택 150 | 같은 영역 |
| 변호사 검색 목적 | `lawyerSearchPurpose` | 선택 300 | 같은 영역, 서버 `LegalIntake`에 저장 |
| 보유 자료 | `documents` | 선택, 각 항목 100 | 자료 종류의 문자열 배열. 중복 제거. ‘없음’은 다른 선택을 해제하고 `[]` 전송 |

필수 5개와 선택 12개 문자열, `documents`까지 최대 18개 키를 전송한다. 선택 항목의 빈 문자열은 생략한다. 서버 DTO의 선택 `sessionId`는 이 화면에서 생성하지 않으며, 서버 기본 처리에 맡긴다. 임의 문의 ID, 화면 펼침 상태, UI 변수는 전송하지 않는다. 프런트의 날짜·연락처 형식 검사는 DTO의 길이 검사에 더한 사용자 입력 검사다.

`C03` 성공 DTO는 `id,sessionId,clientNameMasked,clientPhoneMasked,inquiryPurpose,caseType,caseTypeLabel,partyPosition,incidentDate,counterpartMasked,amountOrDamage,currentStage,coreStatementMasked,desiredNextStep,preferredRegion,disputeArea,selectedDocuments[],recommendedDocuments[],lawyerSearchCondition,clientGuideMessage,privacyNotice,status,createdAt,consultationMemos[],uploadedDocuments[]`이다. 현재 화면은 이 중 `id`로 다음 조회를 연결한다. `lawyerSearchCondition.id`와 문의 `id`를 혼용하지 않는다.

`lawyerSearchCondition`은 `id,intakeId,legalField,disputeType,preferredRegion,officeType,experienceLevel,publicCareerPreference,consultationStyle,sourcePolicy,searchQuery,createdAt`을 포함한다. 검색 조건 저장은 실제 외부 변호사 검색 실행과 별개다.

## 3. React 파일과 상태

| 파일 | 역할 |
|---|---|
| `frontend/legal-procedure-viewer/src/client/IntakeWritePage.jsx` | 전체 폼·검증·접수·필드 오류·중복 제출 차단 |
| `src/client/IntakeReference.jsx` | 절차 선택·단계·체크리스트·양식 목록·상세·영역별 로딩/빈 값/오류/재조회 |
| `src/client/intake-write.css` | 첨부 1672×941 기준 배치와 390px 모바일 폼 |
| `src/client/model.js` | 초기값, 필드 한도·선택값, C03 요청 화이트리스트·검증 |
| `src/client/api.js` | C03/R01~R05/C05, 타임아웃·요청 중단·응답 형식·ID 검증 |
| `src/client/IntakeApp.jsx` | 해시 라우팅, 화면02→03 초깃값, 입력값 보존, 로그인 토큰 메모리 보관 |
| `src/client/components.jsx` | 진행 단계·버튼·오류 안내·조회 훅·로그인 공통 UI |

사건 유형과 핵심 내용을 화면03에서 바꿀 수 있다. 수정한 값은 C03에 반영한다. 이전 분류와 내용이 달라지면 배너에 ‘직접 선택’을 표시하여 이전 분류 결과를 새 판단처럼 보여주지 않는다. 이전/도움말 왕복은 입력을 유지하고, 성공 접수 후에는 초기화한다. 새로고침 시 임시 입력과 로그인 토큰은 초기화된다. 화면02 진행 없이 화면03 주소로 접근하면 복귀 안내를 표시한다.

체크리스트와 양식의 접기·펼치기는 선택을 유지한다. 준비 여부 체크는 화면 안의 메모리 상태이며 서버 저장 API를 호출하지 않는다. 참조 조회 실패는 입력이나 접수를 막지 않는다. C03 처리 중 입력과 이전·접수 버튼을 잠그고 자동 POST 재시도는 하지 않는다.

## 4. 실행한 자동 검수 — 52개 통과

| 실행 | 결과 | 범위 |
|---|---:|---|
| `npm run build` | 성공 | viewer/operator/intake 3개 진입점 |
| `npm run test:intake` | 38/38 | API/모델 25 + 설정 1 + React 렌더링·이벤트 12 |
| `npm run test:intake:live` | 9/9 | 실제 Spring Boot C02/C01/C03/C05/R01~R05. 4개 분야의 단계·체크리스트·양식 일치. 핵심 내용 2,000자, 선택 정보·검색 조건·자료 저장 |
| `npm run test:intake:preview` | 1/1 | 빌드된 intake HTML·JS 및 실제 API 프록시 |
| `gradlew.bat --offline test --tests '*IntakeCreationAuthenticationTest' --console=plain` | 4/4 | 비회원 등록·정상 JWT 소유자·잘못된 인증 차단·선택 정보/검색 목적/조건의 실제 저장 |

React 자동 검수는 소스 컴포넌트 렌더링 테스트이며, 아래 실제 브라우저 검수와 구분한다. 테스트에는 가상 데이터만 사용했다. live 테스트에서 로컬 DB에 가상 문의 1건을 생성했고, 실제 브라우저로 추가 가상 문의 1건을 생성했다. 백엔드 트랜잭션 테스트의 등록 데이터는 롤백한다.

검증한 오류·경계는 공백 필수값, 2,001자 거부/2,000자 허용, 날짜·연락처·선택 문자열 길이, ‘없음’과 자료 선택의 배타성, C03 400 필드 오류/401 로그인 해제/500 입력 유지, 중복 클릭, 서버 ID 이동, 빈 단계·양식·절차 목록, 참조 조회 재시도, 사건 유형 변경 시 오래된 응답 차단이다.

## 5. 실제 브라우저 검수

기존 연결 오류 탭은 URL 보안 정책에 의해 차단되어 접근하지 않았다. 사용자가 정상 HTTP 페이지를 열었다고 확인한 후, 정상 탭에서 아래를 직접 확인했다.

| 항목 | 확인 결과·증거 |
|---|---|
| 데스크톱 | 1672×941, `scrollWidth=1672`, `scrollHeight=941`. 하단 접수 버튼까지 표시. [화면](../output/intake-03/desktop.png) |
| 모바일 | 390×844, 내용 폭 375(세로 스크롤바 제외), 가로 넘침 없음. 세로 폼, 실제 접수 버튼 동작. [화면](../output/intake-03/mobile.png) |
| 필수·형식 검증 | 당사자/다음 단계 누락 및 연락처 `abc` 입력 시 오류와 첫 오류 포커스, 접수 차단. [오류 화면](../output/intake-03/validation.png) |
| 실제 참조 API | 민사소송의 실제 3단계, 소송구조신청서의 실제 안내. 체크리스트 선택 후 접기·펼치기 유지. [체크리스트](../output/intake-03/checklist.png) |
| 실제 등록 | 가상 문의를 실제 C03으로 등록 → 반환 ID **4** → `#/intake/4` → C05 조회·등록 완료 표시. ID는 이 로컬 실행의 증거이며 코드 상수 아님. [완료 화면](../output/intake-03/receipt.png) |
| 500 오류 | 별도 검수 서버(5176→8086)에서 재현. 오류·참조 번호 표시, 입력 유지. [화면](../output/intake-03/server-error.png) |
| 빈 응답 | 검수 서버에서 단계/체크리스트/양식 `[]` 재현. 각각 빈 안내 표시, 접수 가능. [화면](../output/intake-03/empty-reference.png) |
| 로딩·복구 | 검수 서버 C03을 5초 지연. ‘문의 접수 중’과 입력·버튼 잠금 확인. 요청 수 1회, 성공 후 검수 ID 777의 완료 화면. [로딩](../output/intake-03/loading.png), [복구](../output/intake-03/qa-recovery.png) |
| 콘솔 | 실제 5175 화면의 error/warn 로그 없음 |

검수 서버는 `scripts/intake-fixture.mjs`이며 실제 운영 화면의 데이터 대체나 성공 폴백으로 연결하지 않는다. 5175는 실제 Spring Boot 8080에 연결한다.

## 6. 확인된 현재 한계

- C05/C09 조회는 현재 서버에서 공개되어 있고 소유자 검사가 없다. 이 기존 정책은 화면03 작업에서 변경하지 않았다. 운영 배포 전 인가 정책과 구현이 필요하다.
- R05는 현재 양식 메타데이터만 반환하며 실제 파일 URL·바이트·다운로드 API가 없다. 화면은 제공되는 안내와 파일 형식을 표시한다. 첨부 이미지의 예시 양식/절차를 가짜 응답으로 추가하지 않았다.
- 보유 자료 체크는 자료 종류 저장이다. 실제 파일 업로드는 별도 API/화면 범위이며 여기에서 실행하지 않는다.
- C03에 서버 중복 방지 키 계약이 없으므로 응답 유실 후 사용자가 수동 재접수하면 중복 저장 가능성이 남는다. 요청 중 버튼 잠금과 자동 재접수 금지를 적용했다.
- 화면04 전체 디자인·간단 안내 생성(C04), 외부 검색 실행·검색 결과 화면은 이번 화면03 범위 밖이다. 현재 접수 후에는 실제 문의 조회 결과를 표시한다.
