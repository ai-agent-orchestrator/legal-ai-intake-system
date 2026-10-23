# 화면02 React 구현·검수

> 2026-10-21 화면01~04 통합 재검수: **공유 API/React 128개·실제 API 13개·빌드 통과**. 로그인→접수→안내 브라우저 흐름과 4개 화면 반응형 확인. 02·03 R02 분야 대조와 미분류 참고 안내 보완. C05는 현재 공개 조회이며 소유자 인가는 별도다. [이번 통합 필드·ID·인증·구현 검수](SCREEN01_04_INTEGRATION_REVIEW.md). 아래 개별 수치·미확인 기록은 이전 회차의 기록이다.

검수 기준: 2026-10-21 (Asia/Seoul). 사용자가 첨부한 화면02 이미지와 실제 Java Controller·DTO·SecurityConfig를 대조했다.

## 최신 재검수 판정

**화면02의 API 연결·공통 React 구성·라우팅은 테스트 통과. 운영 배포 보안은 조건부다.** C05 문의 상세와 C09 문의 목록은 현재 백엔드에서 공개되어 있으며, 소유자별 조회 제한이 구현된 것으로 판단하면 안 된다. 아래 미완료 항목을 별도로 관리한다.

이번 재검수에서 수정한 사항:

| 확인된 문제 | 수정 내용 | 확인 방법 |
|---|---|---|
| C03에 잘못된 JWT를 보내도 비회원으로 등록되어 소유자가 빠짐 | Authorization을 보냈는데 인증되지 않았다면 저장 전 401 반환. 헤더 없는 비회원 등록과 정상 JWT 소유자 기록은 유지 | 백엔드 신규 3개, 실서버 invalid JWT → 401 및 문의 수 불변 |
| 문의 작성 화면에서 이전·도움말로 이동하면 입장·다음 단계·선택 자료가 사라짐 | 추가 입력을 IntakeApp의 공통 상태로 이동. 등록 성공 후 초기화하며 재분류 시 맞지 않는 선택 자료를 정리 | React 컴포넌트 이동·복귀·최종 요청 본문 검사 |
| 문의 ID만 있으면 불완전한 응답도 정상 상세로 처리 | C05 화면에 사용하는 필수 문자열과 요청 ID 일치를 검사. 안전하게 표현할 수 없는 숫자 ID 거부 | 누락 필드·다른 ID·정밀도 초과 ID 테스트 |
| 상세 경로의 ID가 바뀌는 순간 이전 문의가 잠시 표시될 수 있음 | 문의 ID를 React key로 사용하여 상세 상태를 분리 | 42 → 43 이동 중 이전 문의 미표시 테스트 |
| 갱신된 목록에 없는 절차 code가 선택 상태로 남을 수 있음 | 현재 목록에 선택 code가 있을 때만 사용하고 없으면 첫 실제 code로 전환. R02 응답 code·R01 domain도 대조 | 목록 갱신·다른 code/domain 응답 거부 테스트 |
| `.env`의 API_PROXY_TARGET이 개발 프록시에 반영되지 않음 | Vite loadEnv 적용. 로컬 build preview에도 같은 `/api` 프록시 사용 | `.env.test` 및 환경변수 우선순위 검사, 빌드 산출물 → 실제 API HTTP 테스트 |
| 문의 등록의 클라이언트 검증이 공백 검사에만 의존 | C03 필수 항목·최대 길이·자료 종류 검증과 요청 필드 화이트리스트 적용 | 필드 상한·중복 자료·UI 전용 필드 제외 검사 |

### 이번 실행 결과

| 검사 | 결과 |
|---|---|
| 프로덕션 빌드 `npm run build` | 통과 |
| API·설정·React 컴포넌트 `npm run test:intake` | 26 / 26 통과 |
| 실제 Spring Boot 연동 `npm run test:intake:live` | 8 / 8 통과 |
| 빌드 산출물 및 preview 프록시 `npm run test:intake:preview` | 1 / 1 통과 |
| 기존 운영자 화면 회귀 `npm run test:operator` | 18 / 18 통과 |
| Spring 통합 테스트: IntakeCreationAuthenticationTest / BackendDraftApiIntegrationTest / AdditionalFiveApiIntegrationTest | 3 + 10 + 21 = 34 / 34 통과 |
| 합계 | **87개 통과, 실패 0개** |

브라우저 직접 재검수는 이번 실행에서 기존 탭에 대한 Browser Use URL 보안 정책으로 차단되었다. 차단된 탭을 다른 수단으로 읽거나 조작하지 않았다. 새로 추가한 React 컴포넌트 검사는 실제 소스와 hooks를 실행하는 `react-test-renderer` 검사이며, 탐색·최소 DOM 어댑터와 API 응답 대역을 사용한다. **실제 브라우저 검수와 구분한다.** 디자인·모바일 브라우저 검수는 이전 디자인 작업의 기록이며, 이번 수정 후 브라우저 재확인은 남아 있다.

### 미완료·제한

1. **C05/C09 소유자 조회 권한:** `LegalIntakeRecordController.findById/findRecent`는 소유자 확인 없이 조회하고 `SecurityConfig`가 허용한다. `IntakeAccessService`의 소유자·ADMIN 검사는 파일·검색 이력 API에 적용되어 있으나 이 두 조회에는 연결되지 않았다. 로그인 문의의 소유자/담당자 정책과 비회원 조회 수단을 정한 뒤 서버에서 적용해야 한다. 프런트 로그인 표시만으로 해결되지 않는다.
2. **등록 응답 유실 시 중복 보장:** 진행 중 중복 클릭은 차단하지만, C03에는 서버 멱등 키 계약이 없다. 응답을 잃은 뒤 수동 재등록하는 경우까지 중복 방지를 보장하지 않는다. 자동 POST 재시도는 하지 않는다.
3. **범위:** 화면03은 기본 등록 연결, 화면04는 저장 결과 확인까지만 구현했다. C04 안내 생성·화면05 검색과 나의 상담 등은 아래 표대로 별도 범위다.
4. **메모리 초안:** 화면 이동 시 입력은 유지되지만 새로고침 시 초기화된다. 법률 문의와 토큰을 localStorage에 저장하지 않는다.

## 실행 및 구현 위치

- 화면: `http://127.0.0.1:5175/intake.html#/intake/type`
- 프로젝트: `frontend/legal-procedure-viewer`
- React: `src/client/ClassificationPage.jsx`, `IntakeApp.jsx`, `components.jsx`
- API: `src/client/api.js`
- 디자인 조정: `src/client/intake-reference.css`
- 기존 절차 조회 화면 `index.html`과 운영자 화면 `operator.html`은 별도 진입점으로 유지한다.

```powershell
cd C:\myLectureWs\legal-ai-intake-security-server\frontend\legal-procedure-viewer
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
```

Spring Boot 기본 포트는 8080이다. Vite 개발 서버가 `/api`를 이 서버로 프록시한다. 운영 빌드에는 동일 출처 `/api` 리버스 프록시 또는 `VITE_INTAKE_API_BASE` 설정이 필요하다. 계정 토큰·문의 초안은 메모리에만 유지되며 새로고침하면 초기화된다.

## API·코드 대조

| 동작 | API | 요청·전달 | 응답 사용 | 인증·구현 |
|---|---|---|---|---|
| 사건 분류 | C02 `POST /api/intakes/classify-preview` | `coreStatement` 필수·최대 2,000 UTF-16 단위 (2026-10-22 통일), `caseType` | `caseType`, `caseTypeLabel`, `disputeArea`, `recommendedDocuments`, `clientGuideMessage`, `classificationMethod`, `persisted` | 현재 서버는 공개 API. 구현·실서버 확인 |
| 문의 목적 선택 | 추가 API 없음 | `inquiryPurpose`를 React 상태로 다음 화면에 전달 | C02 요청에 목적 필드를 추가하지 않음 | 구현 |
| 사건 유형 선택 | C02 요청에 포함 | `CIVIL`, `CRIMINAL`, `FAMILY`, `ELECTRONIC_LITIGATION`, `UNKNOWN` | MinimalLegalIntake의 별도 enum과 혼용하지 않음 | 구현 |
| 참고 절차 목록 | R01 `GET /api/legal/procedures?domain=…` | 선택 또는 반환된 유형을 domain으로 전달. UNKNOWN은 전체 목록 | `code`, `domain`, `title`, `summary`, `userRole` | 공개 GET. 구현·실서버 확인 |
| 참고 절차 상세 | R02 `GET /api/legal/procedures/{code}` | R01에서 반환된 실제 code를 URL 인코딩 | `title`, `summary`, `steps`, `forms` | 공개 GET. 구현·실서버 확인 |
| 다음: 문의 작성 | `#/intake/new` | 문의 목적·유형·상황·분류 결과를 메모리로 전달 | C02에서는 문의 ID가 생성되지 않음 | 구현·브라우저 확인 |
| 로그인 | C01 `POST /api/auth/login` | `username`, `password` | `accessToken`, `username`, `role` | 공개 로그인, 토큰은 메모리 보관. 구현·연동 테스트 |
| 다음 화면의 기본 문의 등록 | C03 `POST /api/intakes` | 초안 및 필수 `partyPosition`, `desiredNextStep`, 선택 `documents` | 201 응답의 실제 `id`를 상세 경로로 전달 | JWT가 있으면 Bearer, 없으면 비회원. 잘못된 Authorization은 저장 전 401. 구현·재검수 통과 |
| 등록 확인 | C05 `GET /api/intakes/{id}` | C03 응답의 실제 `id` | 서버의 마스킹 필드·안내·문의 번호 | 현재 SecurityConfig상 공개 조회. 프런트에서 임의 권한 보장을 표시하지 않음 |

C04 안내 생성, C06~C08 검색, C09~C11 상담 목록·상태·메모는 이번 화면02 구현 범위에 포함하지 않는다. ‘나의 상담’은 준비 중으로 표시한다. 파일 종류 선택은 실제 파일 업로드가 아니다.

### React와 백엔드 메서드 연결

| API | React 호출·화면 | 백엔드 처리 | 성공·ID |
|---|---|---|---|
| C01 | LoginDialog → api.login | AuthController.login → AuthenticationManager → JwtTokenProvider | 200; accessToken·username·role. 토큰은 메모리 보관 |
| C02 | ClassificationPage.classify → api.classify | LegalIntakeClassificationController.preview → LegalIntakeRecordService.classifyPreview | 200; RULE_BASED·persisted=false. 문의 ID 없음 |
| R01 | ProcedurePanel → api.procedures | LegalProcedureController.procedures → LegalProcedureService.findProcedures | 200; 실제 code 목록 |
| R02 | ProcedureDetail → api.procedure | LegalProcedureController.procedureDetail → LegalProcedureService.findProcedureDetail | 200; R01의 code를 URL 인코딩하여 전달 |
| C03 | IntakeWritePage.submit → intakePayload → api.create | LegalIntakeRecordController.create → LegalIntakeRecordService.create | 201; 서버 발급 id. 자료 종류·검색 조건 함께 저장 |
| C05 | IntakeReceiptPage → api.intake | LegalIntakeRecordController.findById → LegalIntakeRecordService.findById | 200; C03의 id 또는 상세 경로 id. 없는 ID는 404 |

C03 요청은 `inquiryPurpose`(필수·100), `caseType`(필수·50), `partyPosition`(필수·50), `coreStatement`(필수·2000), `desiredNextStep`(필수·100), `documents`(선택·각 100)를 사용한다. 화면02의 상황 입력은 이와 별도로 C02 상한 300을 적용한다. 이름·전화·지역·경력 등 C03의 나머지 선택 필드는 이 기본 입력 화면에서 보내지 않는다.

### 공통 React 구성 및 라우팅

| 항목 | 코드 | 판정 |
|---|---|---|
| 공통 레이아웃·브랜드·계정·초안 | IntakeApp.jsx / BrandMark.jsx / Icon.jsx | 구현 |
| 버튼·단계·선택 카드·오류·로그인 | components.jsx의 Button / Steps / ChoiceGroup / Feedback / LoginDialog | 공통 사용 |
| 요청·취소·대기 시간·오류·Bearer·응답 검사 | api.js | 공통 사용·테스트 통과 |
| 조회 hooks | components.jsx의 useResource | 로딩·오류·취소·재시도 공통화 |
| 사건 유형 | `#/intake/type` | 기본 경로, 입력 검증·분류·참고 절차 |
| 문의 작성 | `#/intake/new` | 분류 없이 직접 접근 시 이전 단계 안내, 추가 입력 보존 |
| 등록 조회 | `#/intake/{id}` | 실제 서버 ID 사용, ID 변경 시 상세 상태 분리 |
| 도움말·잘못된 경로 | `#/help` / not-found | 안내·복귀 링크 제공 |
| 공통 디자인 | intake.css / intake-reference.css | 기존 디자인 유지. 이번 수정은 기능 검수 중심 |

## 디자인 대조

원본 해상도 1672 × 941에서 주요 위치와 크기를 직접 대조했다. 데스크톱은 동일한 비율로 축소하고, 1000px 이하에서는 세로 배치를 사용한다.

| 요소 | 구현 기준 |
|---|---|
| 전체 접수 패널 | x=113, y=97, 폭=1468px |
| 입력 패널 | x=125, y=183, 폭=880px |
| 첫 선택 카드 행 | y=337, 높이=150px |
| 사건 유형 카드 행 | y=545, 높이=142px |
| 결과 패널 | x=1017, y=109, 폭=558px, 높이=456px |
| 사건 유형 확인 버튼 | x=638, y≈854, 폭=344px, 높이=49px |
| 다음 버튼 | x=1349, 폭=272px, 높이=46px |

영문 브랜드, 단일 문서 방패 로고, 파란 선 아이콘, 선택 카드 테두리·배경, 흰 패널, 단계 표시와 배경을 조정했다. 실제 계정과 API 응답을 유지하므로 원본의 예시 계정명, 서류 문구, 참고 절차 개수는 서버 데이터와 다를 수 있다. 서버 데이터가 없을 때 예시 성공 결과로 대체하지 않는다.

## 검수 내용

- `npm run build`: 다중 진입점 프로덕션 빌드.
- `npm run test:intake`: API·설정·React 컴포넌트 26개.
- `npm run test:intake:live`: 로컬 Spring Boot 실연동 8개. 실행마다 가상의 검수 문의 1건을 생성하므로 로컬 테스트 DB에서 실행한다.
- `npm run test:intake:preview`: 빌드 완료 후 실행. 로컬 preview 서버를 임시 포트로 시작하고 HTML·JavaScript 산출물 및 실제 분류 API 프록시를 검사한 뒤 종료한다.
- 브라우저 정상 흐름: 유형 선택 → 실제 C02 결과 → R01/R02 절차 → 화면03 값 전달 → C03 생성 → 발급 ID로 C05 상세 조회.
- 입력: 빈 문자열·공백·301자 차단, 글자 수 표시, 오류 위치 포커스.
- 상태: 로딩 및 중복 제출 차단, 빈 절차 목록, 서버 500, 재시도, 입력 유지, 입력 변경 후 이전 결과 제거.
- 네트워크 오류·400/401/403/404/429/500·잘못된 JSON/HTML 응답·15초 타임아웃은 API 단위 테스트로 확인한다. 로딩·빈 목록·500 브라우저 상태는 검수 전용 `scripts/intake-fixture.mjs`로 구분해 확인했다.
- 참조 해상도 및 모바일에서 디자인을 확인하고 가로 넘침을 검사했다. 로컬 서버·브라우저 확인은 운영 배포 검증을 의미하지 않는다.

이전 디자인 작업의 검수 캡처: `output/intake-02/reference-desktop.png`, `reference-mobile.png`. 이번 기능 수정 후 새 브라우저 캡처를 생성한 것으로 해석하지 않는다.

## 배경 자산 생성 기록

- 도구: 내장 ImageGen. 첨부 화면을 편집 대상으로 사용해 UI만 제거한 배경을 생성했다.
- 저장 경로: `frontend/legal-procedure-viewer/src/client/assets/intake-office.png`.
- UI·로고·아이콘·텍스트는 React/SVG/CSS로 구현한다. 배경에 화면 스크린샷을 깔아 기능을 대신하지 않는다.
- 최종 프롬프트:

> Use case: precise-object-edit. Asset type: full-bleed photographic background for an implemented React legal intake application. Input image 1 is the edit target. Remove ALL the white UI panels, header/navigation, buttons, interface text, icons, logos and progress indicators from the attached screenshot, reconstructing ONLY the office interior behind them. Preserve the EXACT original photograph's composition, perspective, crop and warm/cool lighting: dark warm-wood shelves and blue legal books along the left edge, tall sunlit windows with defocused city skyline toward the center and right, greenery, and warm wooden desktop along the bottom, black office chair at right. Retain the visible left and right edge scene as closely as possible; fill the currently obscured middle naturally with bright windows, daylight and the desktop. Landscape 16:9, same framing as reference. Photorealistic, softly defocused. No UI, no text, no logo, no overlays, no watermark, no people. This is a clean reusable background asset, NOT a screenshot or application mockup.
