# 화면06 React 구현·API 대조·검수

> 2026-10-21 최신 [화면05~08 통합 재검수](SCREEN05_08_INTEGRATION_REVIEW.md): 요청·응답·ID·공통 React·라우팅·실제 API 연결 확인. 개발용 공개 API·C08 서버 검증 보완과 P03 실제 키 미검증을 구분했다. 아래 개별 회차 기록보다 최신이다.

후속 구현: 최소 문의 상세는 [전체 상세 검수](SCREEN08_REACT_REVIEW.md)의 `IntakeDetailPage`로 확장했고, 상태·메모는 [상태·메모 검수](SCREEN07_REACT_REVIEW.md)에 연결했다. 아래 수치와 후속 범위는 화면06 검수 당시 기록이다.

검수일: 2026-10-21 (Asia/Seoul). 첨부한 화면06 디자인은 화면 구성 참고 자료로 사용했다. API 계약은 실제 Controller, Service, DTO, Repository, SecurityConfig를 읽어 확인했다.

## 실행 경로와 구현 범위

- 목록: `http://127.0.0.1:5175/intake.html#/lawyer/intakes`
- 상세 연결: `#/lawyer/intakes/{id}` → C05의 보호 처리된 접수 내용 표시
- 같은 React/Vite 진입점 `intake.html`을 사용한다. 기존 의뢰인 화면02~04와 운영자 `operator.html`을 유지한다.
- 화면06의 목록·통계·필터·정렬·새로고침·실제 ID 상세 연결을 구현했다. 상세는 목록에서 내용을 확인하는 읽기 전용 화면이며, 화면07 전체 디자인이나 화면08의 상태 변경·상담 메모 편집을 완성한 것으로 집계하지 않는다.
- 사이드바 ‘상담 준비’는 목록을 `CONSULTATION_READY` 조건으로 전환한다. 변호사 홈·자료 관리·내 정보는 ‘준비 중’으로 표시한다.

## 동작별 API·코드 대조

| 동작 | API / 요청 | 응답 사용 / ID 전달 | React → 서버 코드 | 인증 / 구현 |
|---|---|---|---|---|
| 최초 목록 조회 | C09 `GET /api/intakes`, body·query·path ID 없음 | 200 `LegalIntakeResponse[]`, 최근 최대 50건 | `IntakeListPage` → `api.intakes` → `LegalIntakeRecordController.findRecent` → `LegalIntakeRecordService.findRecent` → `LegalIntakeRepository.findTop50ByOrderByCreatedAtDesc` → `toIntakeResponse` | 현재 공개 GET. 로그인했다면 선택적 Bearer 전송. 연결·실제 서버 검증 완료 |
| 새로고침·오류 재시도 | 같은 C09 GET 재조회 | 새 응답으로 목록과 조회 시각 갱신, 필터 유지 | 공통 `useResource.retry`, 이전 요청 abort | 중복 클릭은 로딩 중 비활성화. 연결 완료 |
| 사건 유형·상태·다음 단계·기간·검색어 | 추가 HTTP 요청 없음 | 받은 최대 50건에 AND 필터 | `filterIntakes`, `listFilterError` | 서버가 지원하지 않는 검색 query를 만들지 않음. 구현 완료 |
| 통계 4개 | 추가 HTTP 요청 없음 | 조회 전체 건수, `SUBMITTED`, `NEED_MORE_INFO`, `CONSULTATION_READY` 개수 | `listCounts` → `ListStats` | 필터 결과 수는 별도 표시. DB 전체/전체 기간 통계가 아님 |
| 접수 시각 정렬 | 추가 HTTP 요청 없음 | `createdAt` 내림차순/오름차순, 동일 문자열 시각이면 실제 ID로 순서 결정 | `filterIntakes`, `aria-sort` | 원본 배열 변경 없음. 구현 완료 |
| 상세 보기 | C05 `GET /api/intakes/{id}` | C09 행의 `id` → hash route → API path. 응답 `id` 일치 검증 | `LawyerIntakeDetail` → `api.intake` → `LegalIntakeRecordController.findById` → `LegalIntakeRecordService.findById` → `toIntakeResponse` | 현재 공개 GET, 선택적 Bearer. 404·오류 재시도 구현 |
| 상세에서 목록 복귀 | `#/lawyer/intakes` | 같은 앱 세션의 필터 보존, C09 재조회 | `IntakeApp`의 `listFilters` | 원본 문의 폼을 재등록하거나 수정하지 않음 |
| 로그인·로그아웃 | C01 `POST /api/auth/login`, `{username,password}` | `{accessToken,username,role}` | 공통 `LoginDialog` → `api.login` → `AuthController.login` → AuthenticationManager/JwtTokenProvider | 기존 구현 재사용. token은 메모리에 보관, 로그아웃 시 삭제 |
| 상단 화면 전환 | 의뢰인 `#/intake/type`, 변호사 `#/lawyer/intakes`, 운영자 `operator.html#/overview`, 도움말 `#/help` | 라우팅 이동 | 공통 `IntakeHeader` | 화면 전환은 서버의 역할 인가를 대체하지 않음 |

## C09 요청·응답 필드

요청은 body와 query가 없는 `GET /api/intakes`이다. `Accept: application/json`, `cache: no-store`, `credentials: omit`을 사용한다. 로그인 token이 있을 때만 `Authorization: Bearer {accessToken}`을 넣는다. API 서버 설정은 기존 `VITE_INTAKE_API_BASE` 또는 Vite의 `/api` proxy를 공유한다.

응답은 페이지 객체가 아닌 배열이다. 백엔드의 `LegalIntakeResponse` 필드 25개는 아래와 같다. C05도 같은 DTO를 사용한다.

```text
id, sessionId, clientNameMasked, clientPhoneMasked,
inquiryPurpose, caseType, caseTypeLabel, partyPosition,
incidentDate, counterpartMasked, amountOrDamage, currentStage,
coreStatementMasked, desiredNextStep, preferredRegion, disputeArea,
selectedDocuments[], recommendedDocuments[], lawyerSearchCondition,
clientGuideMessage, privacyNotice, status, createdAt,
consultationMemos[], uploadedDocuments[]
```

| 화면 위치 | 응답 필드 / 처리 |
|---|---|
| 문의 번호 | 실제 `id`를 `No. {id}`로 표시. 서버에 문의 코드 필드가 없어 `INQ-연도-번호`를 임의 생성하지 않음 |
| 접수 시각 | `createdAt`의 날짜·시·분. 서버의 LocalDateTime 값 그대로 표시하고 접수 날짜로 필터. `incidentDate`와 혼동하지 않음 |
| 사건 유형·분쟁 영역 | `caseType`의 5개 표준 코드에 맞는 라벨, `disputeArea`; 미입력 영역은 ‘분쟁 영역 미정’ |
| 핵심 내용 | `coreStatementMasked`, 긴 문장은 말줄임/보호된 전문 title. HTML 삽입 없이 React text로 렌더링 |
| 의뢰인·상대방 | `clientNameMasked`, `counterpartMasked`; null이면 `-` |
| 보유 자료 수 | `selectedDocuments`의 자료 종류 중 공백·‘없음’ 등을 제외한 고유 개수. 파일 업로드 이후에도 서버가 반환하는 등록된 종류 기준. `uploadedDocuments.length`와 다름 |
| 원하는 다음 단계 | 실제 `desiredNextStep`; 문의 목적 `inquiryPurpose`로 대체하지 않음. 선택지는 조회 데이터의 고유 값으로 구성 |
| 상태 | `status`에 대응하는 표시 라벨과 색상. 모르는 코드는 ‘알 수 없는 상태’로 표시 |

표준 사건 코드: `CIVIL`, `CRIMINAL`, `FAMILY`, `ELECTRONIC_LITIGATION`, `UNKNOWN`.

상태 표시: `SUBMITTED` 접수됨, `REVIEWED` 검토됨, `NEED_MORE_INFO` 보완 필요, `CONSULTATION_READY` 상담 준비, `CLOSED` 종료. 상단 ‘검토 대기’는 `SUBMITTED`만 센다. 검토됨·종료는 다른 세 상태 통계에 합산하지 않는다.

`sessionId`, `lawyerSearchCondition.id`, 첨부파일 ID, 상담 메모 ID를 문의 ID로 사용하지 않는다. 상세 링크와 C05 path는 항상 해당 행의 `id`다.

### 클라이언트 응답 검증

배열·최대 50건·양의 ID·중복 ID·필수 문자열·지원 사건 코드·문자열 자료 배열·검색 조건의 부모 intakeId·접수 시각을 검사한다. `createdAt`은 유효한 `YYYY-MM-DDTHH:mm:ss[.fraction]` LocalDateTime이어야 한다. 누락·잘못된 형식은 오류로 표시한다. 사용하지 않는 중첩 DTO 전체를 새로 검증하는 범위는 아니다. C05는 요청 ID와 응답 ID를 대조한다.

## 인증의 실제 범위

`SecurityConfig.applicationSecurity`는 `/api/**` 기본 공개 정책을 사용한다. **C09 목록과 C05 상세에는 변호사 전용 역할 검사·담당 문의 범위 검사가 없다.** 현재 계정은 `ROLE_USER`, `ROLE_ADMIN`이며 `ROLE_LAWYER`는 없다. UI의 ‘변호사’ 메뉴를 누르는 것은 화면 전환일 뿐 권한 획득이 아니다.

실제 서버에서 익명 요청과 유효한 USER JWT 요청 모두 C09 200을 반환하며 동일한 목록 ID를 받는 것을 확인했다. 만료/잘못된 token을 JWT 필터가 인증하지 않더라도 이 공개 GET에서는 401이 보장되지 않는다. 클라이언트는 401이 실제 반환되면 해당 token의 로그인 상태를 지우고 안내하며, 403은 접근 권한 오류로 표시한다.

마스킹은 `toIntakeResponse`의 `PrivacyOutputGuardService.maskDisplayName/mask` 결과를 그대로 사용한다. 원문 복원·추가 개인 정보 조회 API를 호출하지 않는다. 보호 처리와 접근 권한 통제는 별개다. **운영 배포 전 C09/C05의 역할·담당 범위 인가는 백엔드에서 추가해야 한다.** 이번 화면 구현에서 기존 서버 보안 정책을 변경하거나 인증을 제거하지 않았다.

## 공통 React·디자인·라우팅

| 파일 | 역할 |
|---|---|
| `src/client/IntakeApp.jsx` | 목록/상세 hash route, 화면 제목, 화면 전환 포커스, 메모리 필터 상태 |
| `src/client/IntakeHeader.jsx` | 기존 브랜드/로그인·계정 공통 컴포넌트, 변호사 업무 메뉴 변형 |
| `src/client/IntakeListPage.jsx` | 목록·통계·필터·표, `LawyerShell`, 최소 읽기 전용 상세 |
| `src/client/intakeListModel.js` | 필터·날짜 검증·정렬·통계·자료 종류 개수·상태 라벨 |
| `src/client/api.js` | C09/C05 요청·응답 검증, 선택적 JWT, 15초 timeout, 오류·취소 |
| `src/client/components.jsx` | 기존 Button/Feedback/useResource/LoginDialog 재사용 |
| `src/client/IntakeReceiptPage.jsx` | 기존 `IntakeSummary`를 상세에서 재사용 |
| `src/client/Icon.jsx`, `BrandMark.jsx` | 기존 SVG 아이콘·브랜드 확장/공유 |
| `src/client/intake-list.css` | 화면06에 한정한 업무 레이아웃, navy 사이드바, 통계 색상, 표·필터 반응형 |

첨부 이미지의 사무실 배경·상단 브랜드·선택된 변호사 메뉴·왼쪽 업무 메뉴·흰 패널·통계 4개·필터·표 배치를 맞췄다. 1672×941에서 업무 패널은 x190/y99, 표 시작은 약 y437이다. 첨부의 23건·8건·5건·7건, 과거 조회 시각, 김지훈 변호사 신원은 예시여서 복사하지 않고 실제 응답과 로그인 상태를 표시한다.

모바일 390×844에서는 메뉴/표 내부만 가로 스크롤하며 본문 폭이 화면 밖으로 늘어나지 않는다. 통계는 2열, 필터는 2열/전체 폭으로 배치한다. 표의 열과 상세 링크를 숨기지 않는다.

## 입력·로딩·빈 화면·오류 처리

- 필터는 AND 조합이며 최대 50건 안에서만 검색한다. 전체 DB 검색·서버 페이지네이션을 구현한 것으로 표현하지 않는다.
- 기간은 접수 날짜 기준이며 시작일/종료일 당일 전체를 포함한다. 잘못된 날짜·역순 범위는 안내하고 결과를 표시하지 않는다.
- 검색어는 100자 제한, 공백 정리·대소문자 무시. 서버가 가린 원본 이름으로는 검색할 수 없다.
- 초기 조회/새로고침 중 통계는 `—`, 버튼은 비활성화된다. 오류를 0건/성공으로 표시하지 않는다.
- 목록 없음과 필터 결과 없음은 문구를 구분한다. 조건 초기화와 재시도를 제공한다.
- HTTP 400/401/403/404/429/5xx, 네트워크 실패, timeout, JSON·DTO 형식 오류를 공통 API 클라이언트에서 처리한다. traceId가 있으면 문의 참조 번호로 표시한다.
- 화면을 떠나거나 재조회하면 이전 요청을 취소한다. 늦은 목록 응답이 다른 상세 내용을 덮어쓰지 않는다.
- 목록→상세→목록에서는 필터를 보존한다. 페이지 전체 새로고침 시 필터·JWT는 메모리에서 초기화하며, route ID는 C05로 다시 조회한다.

## 검수 결과

| 검증 | 결과 |
|---|---|
| `npm run build` | 성공: viewer/operator/intake 3개 진입점 |
| `npm run test:intake` | 66/66: API·모델·React·설정. 화면06 신규 12개 포함 |
| `npm run test:intake:live` | 실제 Spring 서버 12/12. C09 익명·선택 JWT·정렬·보호 필드·C05 ID 검증 포함 |
| `npm run test:intake:preview` | 빌드 결과·proxy 실제 API 검증 1/1. C09→C05 포함 |
| `npm run test:operator` | 기존 운영자 회귀 18/18 |
| 실제 브라우저, 실제 서버 | C09 목록/통계, 실제 ID 상세, 목록 복귀, 검색 결과 없음, 날짜 역순 오류, 초기화·새로고침 확인 |
| 실제 브라우저, 별도 가상 API | 8행 디자인, 복합 유형+상태 필터, 정렬, 로딩 잠금/통계 —, 500+traceId+재시도, 정상 빈 목록 확인 |
| 반응형 | 1672×941 및 390×844. 모바일 문서 가로 넘침 없음, 표 키보드 가로 스크롤 확인 |

자동 테스트 총 **97개 통과**(66+12+1+18). Java 소스는 변경하지 않아 백엔드 Gradle 전체 테스트는 이번 검수에서 다시 실행하지 않았다. 로컬 네트워크 연결은 실행 환경의 제한으로 일반 실행 시 EACCES가 발생하여, 승인된 localhost 접근 실행으로 재검증했다.

실제 서버 통합 테스트 전체 실행은 기존 시나리오에 따라 가상 문의 1건과 검색 요청 1건을 로컬 DB에 추가한다. 화면06 전용 테스트와 브라우저 목록 검수는 조회만 하며 가상 API의 8행을 실제 DB에 넣지 않는다. H2의 기존 개발 데이터는 삭제하지 않았다.

### 브라우저 증거

- `output/intake-06/screen06-live-desktop.png`: 실제 API 목록
- `output/intake-06/screen06-fixture-desktop.png`: 별도 가상 API, 디자인 비교용 8행
- `output/intake-06/screen06-mobile.png`: 별도 가상 API, 모바일
- `output/intake-06/screen06-loading.png`, `screen06-error.png`, `screen06-empty.png`: 별도 가상 API의 상태별 화면

가상 API는 production 코드에서 import하지 않는다. 재현은 `npm run qa:intake`로 별도 5176/8086 서버를 띄우고 `npm run qa:intake:mode -- list-slow|list-error|list-empty|list-forbidden|list-malformed|success` 중 한 모드를 선택한 뒤 UI에서 새로고침한다. 실제 화면은 5175/8080을 사용한다.

## 남아 있는 범위

변호사 역할·담당 문의 인가는 운영 적용 전 필수 백엔드 작업이다. 최신 50건을 넘는 서버 검색/페이지네이션, 전체 DB 대시보드 집계, 화면07 전체 디자인, 화면08 상태·메모 편집, 홈·자료 관리·내 정보의 업무 기능은 이 화면06 작업에서 구현했다고 표시하지 않는다.
