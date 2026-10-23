# Legal Procedure Viewer

> 화면13 실제 시계열: 기존 Prometheus를 재사용하고 ADMIN 전용 `/actuator/guardtimeseries`를 연결했다. **모의 자료 보기**는 API·과금 없이 그래프를 시연한다. [설정](../../monitoring/README.md) · [최신 검수](../../docs/SCREEN13_OBSERVABILITY_REVIEW.md).

> 2026-10-22 화면12 공통 디자인의 화면13 시안을 실제 React·C16/C17 연결에 적용했다. 반복 패턴 건수와 작은 발생 분포 그래프를 표시하며 운영 설명은 화면13 안에 통합했다. 화면14 메뉴·전용 컴포넌트는 제거했다. [구현·검수](../../docs/SCREEN13_OBSERVABILITY_REVIEW.md).

> 2026-10-21 P03: 화면05 실행 버튼·진행 조회·결과별 AI 설명·설명 재생성을 연결했다. 실제 제공자 키 미설정 상태의 로컬 모의 검증이며, 이전의 P03 비활성 기록은 당시 상태다. [계약·설정·검수](../../docs/P03_EXTERNAL_SEARCH_REVIEW.md).

> 2026-10-21 화면01~04 통합 재검수: **공유 API/React 128개·실제 API 13개·빌드 통과**. 로그인→접수→안내 브라우저 흐름과 4개 화면 반응형 확인. 02·03 R02 분야 대조와 미분류 참고 안내 보완. C05는 현재 공개 조회이며 소유자 인가는 별도다. [이번 통합 필드·ID·인증·구현 검수](../../docs/SCREEN01_04_INTEGRATION_REVIEW.md). 아래 개별 수치·미확인 기록은 이전 회차의 기록이다.

## 화면01 · 로그인·내 정보 (2026-10-21)

[화면01 열기](http://127.0.0.1:5175/intake.html#/login). 이제 `intake.html`의 빈 hash 기본 진입점도 로그인이다. 첨부의 중앙 로그인 카드·좌우 소개를 구현하고, 전체 화면/기존 모달의 공통 `LoginForm`에서 C01 → P01 계정·역할 일치 확인 후 탭 메모리 세션을 확정한다. USER는 화면02, ADMIN은 내 정보로 이동한다. 비밀번호 표시 전환·입력 검증·로딩·실패/초기화·취소·내 정보·로그아웃을 연결했다.

데모는 공개 화면 체험이며 권한을 만들지 않는다. 서버에는 USER/ADMIN만 있고 변호사 역할은 없다. 운영 화면은 별도 탭으로 열어 JWT를 전달하지 않으며 기존 지표 Basic 인증을 유지한다. [화면01 API·필드·ID·인증·검수](../../docs/SCREEN01_REACT_REVIEW.md).

이번 회차는 API/React **126개 통과**, 빌드 성공, C01/P01 실연동·브라우저 검수 완료다. 전체 실연동 회귀는 JVM 종료로 **14/17 통과**이며, 재실행 권한 요청이 거절되어 나머지 3개는 미확인이다. 서버는 복구했지만 H2 메모리 검수 데이터는 초기화되었다. 아래 이전 테스트 수는 각 화면 당시 기록이다.

## 화면04 · 안내 확인 (2026-10-21)

[화면04 열기](http://127.0.0.1:5175/intake.html#/intake/8). `IntakeReceiptPage.jsx`의 C05 접수 내용·보호 핵심 내용·상태, C04 간단 안내, R01/R02 실제 절차, 형사 R06을 연결했다. 공통 헤더·배경·단계·오류/재시도를 재사용하며, 이전 읽기 전용 확인과 화면05 이동에 같은 문의 ID를 유지한다. 표 열 너비·모바일 2열 표시와 응답 상태/시각·유형·절차 domain 대조를 보완했다.

[화면04 API·필드·ID·인증·React 검수](../../docs/SCREEN04_REACT_REVIEW.md). 최신 검수는 `test:intake` 111 + `test:intake:live` 16 + `test:intake:preview` 1 = **128개 통과**, 빌드 성공이다. 화면04 API는 현재 서버의 공개 정책을 사용한다. C04는 규칙 안내이며 문의 저장·LLM 호출을 하지 않는다. 아래 이전 테스트 수는 각 화면 구현 당시의 기록이다.

## 화면05 · 검색 요청·이력·결과 (2026-10-21)

[화면05 열기](http://127.0.0.1:5175/intake.html#/intake/1/search/1). C05 저장 조건, C06 요청 기록, C07 상세, C08 결과 링크 직접 저장, P02 문의별 이력을 첨부 디자인에 연결했다. 실제 문의·요청 ID를 라우트에 유지하며 로그인·필수값/URL 검증·로딩/빈/오류·중복 저장 방지·재시도를 처리한다. P03은 개발 전이므로 **외부 검색 실행 버튼은 비활성**이고 실행 API를 호출하지 않는다.

[화면05 API·코드·인증·React 검수](../../docs/SCREEN05_REACT_REVIEW.md). 최신 회차는 `test:intake` 106 + `test:intake:live` 15 + `test:intake:preview` 1 = **122개 통과**, 3개 진입점 빌드 성공이다. 기존 운영자 테스트는 sandbox 11/18 통과, 로컬 통신 7개는 재실행 권한 요청이 거절되어 미확인이다. 아래 125개는 이전 화면07·08 검수 시점의 기록이다.

P02는 JWT·소유자/관리자 필수, C05/C06/C07/C08은 현재 공개 API다. 검수 중 JVM 종료 후 서버를 복구했고 H2 메모리 검수 데이터는 새로 생성했다. 예시 URL의 ID는 최종 검수 데이터이며 실제 사용 시 화면04 또는 이력의 반환 ID를 사용한다.

## 첨부 화면07·08 · 상태 메모와 전체 문의 상세 (2026-10-21)

사용자 첨부 기준 [화면08 문의 상세](http://127.0.0.1:5175/intake.html#/lawyer/intakes/6)는 C05의 개요·사실관계·보유/추천 자료·안내·탐색 조건·실제 첨부·최근 메모를 표시한다. 실제 문의 번호는 목록에서 선택한다. P05 다운로드는 로그인한 소유자 또는 관리자만 가능하며, Bearer 헤더로 받은 binary를 검증해 파일로 저장한다.

상세에서 `#/lawyer/intakes/{id}/consultation`으로 이동하면 사용자 첨부 화면07의 C10 상태 변경·C11 상담 메모를 저장한다. 성공 후 C05를 재조회하며, 응답이 끊긴 쓰기는 자동 재전송하지 않는다. 영구 상태 이력·메모 작성자·최근 수정 시각은 현재 API에 없어 임의 표시하지 않는다. 기존 기능표의 07 상세/08 메모와 사용자 첨부 번호가 달라 경로와 API 식별자를 함께 기록했다.

[화면08 API·코드·React 검수](../../docs/SCREEN08_REACT_REVIEW.md) · [화면07 상태·메모 검수](../../docs/SCREEN07_REACT_REVIEW.md). 최신 검수는 `test:intake` 92개 + `test:intake:live` 14개 + `test:intake:preview` 1개 + `test:operator` 18개 = **125개 통과**, 3개 진입점 빌드 성공이다. C05/C10/C11의 현재 공개 정책과 파일 다운로드 인가는 서로 다르다. 아래 화면06의 97개는 그 구현 시점의 기록이다.

## 화면06 · 접수된 문의 (2026-10-21)

[문의 목록 열기](http://127.0.0.1:5175/intake.html#/lawyer/intakes). `npm run dev -- --host 127.0.0.1 --port 5175 --strictPort`로 실행한다. Spring 서버는 기존 8080을 사용한다.

공통 브랜드·로그인 헤더와 변호사 업무 레이아웃에서 C09 `GET /api/intakes`의 최신 최대 50건을 표시한다. 목록 기준 통계, 사건 유형·상태·다음 단계·접수 기간·검색어 필터, 정렬·새로고침, 실제 ID의 C05 상세와 목록 복귀를 연결했다. 필터는 받은 목록 안에서 동작한다. C09/C05는 현재 서버 설정상 공개 API이며 변호사 전용 인가는 별도 보강이 필요하다.

요청·응답 전체 필드, ID 전달, 인증 범위, 컴포넌트·라우팅·테스트·브라우저 증거는 [화면06 React 검수](../../docs/SCREEN06_REACT_REVIEW.md)를 참조한다. 이번 검수는 `test:intake` 66개 + `test:intake:live` 12개 + `test:intake:preview` 1개 + `test:operator` 18개, 총 97개다. `npm run build`는 viewer/operator/intake 3개 진입점을 빌드한다. 아래 이전 기록의 테스트 수와 후속 계획은 당시 범위를 뜻한다.

React frontend for the legal procedure API in the Spring Boot application.

## Reuse in the Legal Intake project

This earlier Spring Boot–React assignment is reused as the Legal Reference module of the larger Legal Intake project. The six existing reference APIs are included in its current total of 36 APIs. This viewer already calls the procedure list, procedure detail, and criminal checklist APIs.

The Intake project will use the reference data in screens 2–4 for case-type guidance, preparation checklists, and related procedure information. Wiring those new screens is a later React task. Classification, intake persistence, and external search remain separate modules. See the [screen/API mapping](C:/myLectureWs/legal-ai-intake-security-server/docs/SCREEN_API_CONTROLLER_MAPPING.md) for the current implementation status.

## Assignment implementation checklist

| Requirement | Implementation | Status |
| --- | --- | --- |
| Spring Boot REST API | Legal procedure and checklist read APIs under `/api/legal/**` | Done |
| React application | `legal-procedure-viewer` Vite + React app | Done |
| React to Spring integration | `src/api/legalProcedureApi.js` separates HTTP request functions from UI components | Done |
| Main screen | Legal procedure list with search and domain filter | Done |
| Detail screen | Procedure detail and step display | Done |
| Additional screen | Criminal supplementary-investigation checklist | Done |
| Shared visual style | Common layout, buttons, panels, status styles in `src/styles.css` | Done |
| Loading state | Loading message while an API request is pending | Done |
| Error state | API failure message and retry action | Done |
| Empty state | No-results message for an empty search or filter result | Done |
| Security study | JWT login, public legal GET policy, protected AI API policy | Done |

The project intentionally replaces the sample shopping-mall domain with a legal-procedure domain. The assignment's technical learning target is preserved:

```text
React screen
-> API helper
-> fetch request
-> Spring REST Controller
-> JSON response
-> React state update
-> screen rendering
```

The domain is legal procedure data because it matches the larger legal AI project. The reusable technical structure is the same as the sample CRUD application, while the screen content, search, checklist, and error states are customized for the intended service.

## Run

Start the Spring Boot server on `http://localhost:8080`, then run:

```powershell
npm install
npm run dev
```

Open the Vite URL shown in the terminal. The API base URL can be changed in the page when the backend uses another port.

## 운영자 화면 09~12

같은 Vite 프로젝트의 별도 진입점인 [운영자 워크스페이스](http://127.0.0.1:5173/operator.html#/overview)를 제공한다. 기존 Previewer는 `/`에서 사용한다.

| 화면 | 해시 경로 | 연결 API |
|---|---|---|
| 09 운영 요약 | `/operator.html#/overview` | C12 |
| 10 마케팅 집계 | `/operator.html#/marketing` | C13 |
| 11 보안·위험·비용 | `/operator.html#/security` | C14·C15, 인증 후 C16 |
| 12 위험 이력·Incident 대응 | `/operator.html#/audit` | C17·C18, 선택 후 C19·C20, 저장 후 C18·C12 재조회 |

공통 엄지척 헤더·네이비 메뉴·흰 업무 패널과 반응형 CSS를 공유한다. 연결 설정 또는 `VITE_API_BASE_URL`로 API 서버를 지정한다. C16은 ADMIN Basic을 별도 입력하며, 현재 나머지 운영 업무 API는 서버 설정상 공개 접근이다.

`npm run build`로 두 진입점을 함께 빌드하고, `npm run test:operator`로 API 클라이언트 6개·분포 표시 4개·C16 지표 해석 3개·Incident 흐름 5개, 총 18개를 검증한다. 화면 11은 C15 위험·C14 보안 이벤트·C16 비용/성능의 3열이다. C16의 연결 설정에서 관리자 인증을 입력하며 화면을 떠나면 지운다. 화면 12는 왼쪽 C17 위험 이력·상세, 오른쪽 C18 Incident·상세이며, C19 확인·C20 해결 후 C18·C12를 별도로 재조회한다. 요청·응답·ID·인증·Controller–Service·검증 기록은 [운영자 API·React 매핑](../../docs/OPERATOR_09_12_API_REACT_MAPPING.md)을 참조한다.

## Previewer API mapping

| React workflow | Spring API |
| --- | --- |
| Procedure list and domain filter | `GET /api/legal/procedures` |
| Procedure detail | `GET /api/legal/procedures/{code}` |
| Criminal supplementary checklist | `GET /api/legal/criminal/checklist/supplementary-investigation` |

The API functions are separated under `src/api`, while list, detail, checklist, and status states use shared React components and the same design tokens in `src/styles.css`.

The UI includes loading, error, empty, search, filter, reload, detail, and checklist states so the Spring Boot to React integration can be tested without relying on a successful request only.

## JWT security study flow

This assignment uses two different access policies:

```text
Legal knowledge read API
GET /api/legal/**
-> public read access

AI API
/api/ai/**
-> JWT authentication required
```

The reason is practical. The React legal viewer only reads public legal procedure data, while an AI request can consume an external model, use a user's identity, and create cost or audit records. The AI boundary therefore remains protected even though the read-only legal pages are easy to open during the assignment.

### 1. Login flow

```text
Postman or React
-> POST /api/auth/login
-> AuthController
-> AuthenticationManager
-> username/password authentication
-> JwtTokenProvider creates accessToken
-> JSON response
```

Login request:

```json
{
  "username": "user",
  "password": "user123"
}
```

Login response:

```json
{
  "accessToken": "eyJ...",
  "username": "user",
  "role": "ROLE_USER"
}
```

The sample users are defined in `SecurityConfig`:

```text
admin / admin123 -> ROLE_ADMIN
user  / user123  -> ROLE_USER
```

The access token is not the user's password. It is a signed proof that the server can validate on later requests. The client must send it in the HTTP header:

```text
Authorization: Bearer {accessToken}
```

### 2. Request authentication flow

```text
React or Postman request
-> Authorization: Bearer JWT
-> JwtAuthenticationFilter
-> token signature and expiration validation
-> username and role extracted from claims
-> SecurityContext authentication set
-> SecurityConfig URL authorization
-> Controller
```

`JwtAuthenticationFilter` runs before the controller. It reads the `Authorization` header, parses the token, and places the authenticated user and role into Spring Security's `SecurityContext`. The controller does not need to manually parse the JWT.

### 3. Current URL policy

```java
.requestMatchers("/api/auth/login").permitAll()
.requestMatchers(HttpMethod.GET, "/api/legal/**").permitAll()
.requestMatchers("/api/legal/**").authenticated()
.requestMatchers("/api/ai/**").authenticated()
```

Read this from top to bottom:

| Rule | Meaning |
| --- | --- |
| `POST /api/auth/login` | Login must be possible before a token exists |
| `GET /api/legal/**` | Legal viewer read APIs are public for the assignment |
| Other `/api/legal/**` | Future write or protected legal actions require JWT |
| `/api/ai/**` | AI requests always require JWT |

The method matters. A GET legal query and a future POST legal modification do not have to share the same policy.

### 4. 401 and 403 difference

```text
No token or invalid token
-> 401 UNAUTHORIZED
-> authentication failed

Valid token but insufficient role
-> 403 FORBIDDEN
-> identity is known, permission is insufficient
```

Examples:

```text
POST /api/ai/chat without Authorization
-> 401 JSON error

ROLE_USER requests an ADMIN-only endpoint
-> 403 JSON error
```

This distinction is important when debugging React. A 401 usually means the token is missing, expired, or invalid. A 403 usually means the token worked but the user's role is not allowed.

### 5. Postman study sequence

1. Send `POST http://localhost:8080/api/auth/login` with `user/user123`.
2. Copy `accessToken` from the response.
3. Send `GET http://localhost:8080/api/legal/procedures` without a token and confirm the public read response.
4. Send a protected AI request without a token and confirm `401`.
5. Add `Authorization` with type `Bearer Token` and paste the token.
6. Send the AI request again and confirm that authentication passes.
7. Repeat with an expired or modified token and observe the JSON error response.

### 6. React connection point

The legal viewer's read API functions are separated in `src/api/legalProcedureApi.js`. If an AI screen is added later, the request helper should attach the token in one place:

```javascript
export async function requestAiChat(message, accessToken) {
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ message }),
  });

  if (!response.ok) {
    throw new Error(`AI request failed: ${response.status}`);
  }

  return response.json();
}
```

The complete AI request shape is:

```text
React input
-> requestBody
-> JSON.stringify
-> fetch POST + Bearer JWT
-> Spring JWT filter
-> Controller
-> Service / Guardrail / LLM
-> JSON response
-> React state update
```

### 7. Security boundary to remember

```text
Spring Security JWT
-> who may use the API?

Input and output Guardrail
-> what may enter or leave the AI system?

Cost Control
-> how much model usage is allowed?
```

JWT does not replace Guardrails, and Guardrails do not replace JWT. They control different risks and are placed at different stages of the backend flow.

## 화면02 사건 유형 확인

`intake.html#/intake/type`에서 화면02를 실행한다. 기존 절차 조회 및 운영자 화면과 독립된 React 진입점이다.

```powershell
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
```

- 접속: `http://127.0.0.1:5175/intake.html#/intake/type`
- Spring Boot: `http://localhost:8080` (개발 서버에서 `/api` 프록시)
- API 검수: `npm run test:intake`
- React 컴포넌트만 검수: `npm run test:intake:components`
- 로컬 실연동 검수: `npm run test:intake:live` (가상 문의 1건 생성)
- 빌드 완료 후 preview 검수: `npm run test:intake:preview` (실행 중인 Spring Boot 필요)
- 디자인: `src/client/intake-reference.css`, 배경: `src/client/assets/intake-office.png`
- API·인증·ID 전달·디자인·상태 검수 및 배경 생성 프롬프트: [화면02 검수 기록](../../docs/SCREEN02_REACT_REVIEW.md)

배포 시 동일 출처 `/api` 리버스 프록시를 구성하거나 빌드 전에 `VITE_INTAKE_API_BASE`를 설정한다. 서버 오류 시 예시 성공 데이터를 표시하지 않는다.

2026-10-21 재검수에서 화면 이동 시 추가 입력 보존, 상세 ID·필드 검증, 잘못된 JWT의 비회원 등록 차단, `.env` 프록시 설정을 수정했다. C05/C09의 소유자 조회 권한은 현재 서버에 적용되지 않았으며, 운영 배포 전 별도 정책·구현이 필요하다. 상세 판정과 테스트 범위는 위 검수 기록을 따른다.

## 화면03 문의 작성

화면02에서 분류 후 ‘다음: 문의 작성’을 누르면 `#/intake/new`의 전체 접수 폼으로 이동한다. 필수 5개·선택 정보·변호사 검색 선호·보유 자료를 C03에 연결했고, R02/R03/R04/R05로 실제 절차·체크리스트·양식 안내를 조회한다. R01은 절차 코드를 얻는 공통 조회로 재사용한다.

- 핵심 코드: `src/client/IntakeWritePage.jsx`, `IntakeReference.jsx`, `intake-write.css`
- `npm run test:intake`: API·모델·React·설정 38개
- `npm run test:intake:live`: 실제 연동 9개, 선택 정보와 검색 조건을 포함한 가상 문의 1건 생성
- API별 요청·응답·인증·ID·구현 및 실제 브라우저 증거: [화면03 검수 기록](../../docs/SCREEN03_REACT_REVIEW.md)
- 임시 입력은 탭 메모리에 유지되고 새로고침하면 초기화된다. R05는 현재 양식 안내만 반환하며 실제 파일 다운로드는 제공하지 않는다.
