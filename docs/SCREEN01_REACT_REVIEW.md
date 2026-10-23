# 화면01 · 로그인 React / API 대조·검수

> 2026-10-21 화면01~04 통합 재검수: **공유 API/React 128개·실제 API 13개·빌드 통과**. 로그인→접수→안내 브라우저 흐름과 4개 화면 반응형 확인. 02·03 R02 분야 대조와 미분류 참고 안내 보완. C05는 현재 공개 조회이며 소유자 인가는 별도다. [이번 통합 필드·ID·인증·구현 검수](SCREEN01_04_INTEGRATION_REVIEW.md). 아래 개별 수치·미확인 기록은 이전 회차의 기록이다.

2026-10-21. 사용자 첨부 로그인 화면과 C01/P01 연결표 기준. `intake.html`의 기본 진입점과 `#/login`에 전체 로그인 화면을 구현했다. 공통 사무실 사진·`BrandMark`·`Icon`·버튼·오류 처리를 재사용하고 좌측 소개, 중앙 로그인 카드, 우측 기능 소개를 배치했다. 데스크톱 중앙 카드 위치는 x573/y80/너비532px이다. 별도 변호사 역할은 현재 서버에 없어 데모를 권한 부여로 처리하지 않았다.

## 동작별 API·필드·인증·구현

| 동작 | API / React | 요청·ID·인증 전달 | 성공 응답 / 처리 | 구현 |
|---|---|---|---|---|
| 로그인 제출 | C01 `POST /api/auth/login` / `LoginForm` → `authenticate` → `api.login` | JSON `{username, password}`. 기존 JWT·역할·문의 ID를 보내지 않음. 공개 로그인 API | 200 `{accessToken, username, role}`. 아직 앱 로그인 상태로 확정하지 않고 P01 호출 | 실제 연결·입력/응답 검증·중복 클릭 방지·오류·브라우저 검수 완료 |
| 발급된 토큰 확인 | P01 `GET /api/me` / `api.currentUser(candidateToken)` | `Authorization: Bearer {C01.accessToken}`. 본문·query·수치 사용자 ID 없음 | 200 `{username, role}`. C01의 username·role과 모두 일치해야 앱 세션 확정 | 실제 연결·400/401 경계·불일치/실패/취소 검수 완료 |
| 일반 사용자 로그인 완료 | 추가 API 없음 | P01에서 확인한 `ROLE_USER` | `#/intake/type`로 이동, 공통 헤더에 실제 username 표시 | 구현·브라우저 확인 |
| 관리자 로그인 완료 | P01 계정 조회 재사용 | P01에서 확인한 `ROLE_ADMIN` | `#/account`로 이동. 계정 페이지가 P01을 재조회하고 운영 대시보드 별도 탭 링크 표시 | 구현·브라우저 확인 |
| 내 정보 확인·재시도 | P01 `GET /api/me` / `AccountPage` → `VerifiedAccount` | 현재 탭 메모리 JWT를 Bearer 헤더로 전송 | 계정·역할 표시. 실패 시 재시도는 GET만 수행 | 구현·500 재시도 컴포넌트 검수·정상 브라우저 확인 |
| 기존 화면의 로그인 | 같은 C01 → P01 / `LoginDialog` 안의 공통 `LoginForm` | 전체 화면과 같은 인증 절차 | 현재 화면·문의 ID·작성 중 입력 유지. 확인 성공 후 모달 닫기 | 공통화·실제 브라우저 확인 |
| 비밀번호 표시/숨기기 | API 없음 | 입력 type 전환만 수행 | `password/text` 전환, 접근성 이름·pressed 상태 제공 | 구현·브라우저 확인 |
| 의뢰인 데모 | API 로그인 없음 | `#/intake/type`로 이동 | 공개 사건 접수 화면 체험. 계정·토큰·역할 생성 안 함 | 구현·비회원 이동 확인 |
| 변호사 데모 | API 로그인 없음 | `#/lawyer/intakes`로 이동, 해당 화면은 기존 C09 조회 | 공개 목록 화면 체험. `ROLE_LAWYER`를 만들거나 `ROLE_USER`를 변호사 역할로 변경하지 않음 | 구현·비회원 이동 확인 |
| 관리자 데모 | API 로그인 없음 | `operator.html#/overview`, `target=_blank`, `noopener noreferrer` | 기존 공개 운영 화면을 새 탭으로 연결. 인증 토큰 전달 안 함 | 링크·속성·빌드 확인. 운영 전체 재검수 아님 |
| 로그아웃 | 서버 API 없음 | 탭 메모리 JWT·계정 상태 삭제 | 헤더 비회원, 계정 화면 로그인 안내. 발급 토큰의 서버 폐기·블랙리스트 API로 표현하지 않음 | 구현·브라우저 확인 |

`username`이 로그인 계정 식별자다. C01/P01에는 `userId`, 이메일 프로필, 변호사 ID, 문의 ID가 없다. 사용자명·역할은 응답을 사용하며 입력한 아이디나 데모 버튼에서 역할을 추론하지 않는다. 토큰은 API query·hash·복사 내용·외부 링크에 넣지 않는다.

## 실제 서버 계약과 범위

- `security/AuthController.login`: `@Valid LoginRequest(@NotBlank username, @NotBlank password)` → `AuthenticationManager.authenticate` → `JwtTokenProvider.createToken` → `LoginResponse(accessToken, username, role)`.
- C01은 기존 JWT 없이 호출한다. 빈 값은 프런트에서 차단하고 서버도 400으로 검증한다. 잘못된 자격 증명은 실제 서버에서 401이다. 사용자는 아이디 앞뒤 공백을 정리해 제출하며 비밀번호 내용은 변경하지 않는다. 별도 이메일 형식·비밀번호 복잡도·최대 길이 규칙을 서버 계약으로 추가하지 않았다.
- `security/CurrentUserController.me`: 인증된 `Authentication`의 name과 첫 authority를 반환한다. P01은 JWT 검증 결과를 확인하는 API이며 토큰 갱신·별도 계정 DB 재조회 API가 아니다.
- `SecurityConfig`의 현재 개발 계정은 `ROLE_USER`, `ROLE_ADMIN` 두 종류다. 변호사 역할·변호사 계정 생성·회원가입·비밀번호 재설정 API는 현재 없다. 프런트 데모에 테스트 계정 비밀번호를 하드코딩하지 않았다.
- P01은 JWT 필수이며 비회원·잘못된 JWT는 401이다. 응답의 빈 username·지원하지 않는 role·잘못된 형식은 성공으로 표시하지 않는다. C01과 P01의 계정 또는 역할이 다르면 인증을 완료하지 않는다.
- C01·P01이 성공한 후에만 `IntakeApp`의 token ref와 user 상태를 갱신한다. 이전 로그인과 다른 후보 토큰의 401이 기존 계정을 잘못 지우지 않도록 공통 API의 현재 토큰 대조를 유지했다.
- 토큰은 탭 메모리에만 유지한다. `localStorage`, `sessionStorage`, 쿠키에 저장하지 않으며 새로고침하면 다시 로그인한다. 요청별 15초 제한·취소·JSON 형식·401/403/서버 오류 처리를 공통 API 계층에서 재사용한다.
- 로그인 중 입력과 제출을 잠그고 단계별 ‘로그인 중/계정 정보 확인 중’을 표시한다. 실패·계정 확인 단계·성공 시 비밀번호 입력을 비운다. 실패 시 아이디는 유지한다. 페이지·모달을 떠나면 요청을 취소하고 늦은 성공으로 이동하지 않는다. 화면 이동 시 열린 로그인 모달도 닫는다.
- 계정 확인 실패 후에는 발급 토큰을 버리고 로그인 화면에 머문다. 자동 로그인 재전송이나 토큰 재발급은 수행하지 않는다. 화면 최초 진입에 예시 오류 메시지를 미리 표시하지 않는다.

전체 업무 API에 역할 인가를 추가한 작업은 아니다. 기존 C05/C09 등 공개 정책은 그대로이며, P02/P04/P05는 기존 소유자/관리자 정책을 따른다. 운영 화면 업무 API의 현재 공개 정책과 C16의 별도 ADMIN Basic 인증도 변경하지 않았다. 관리자 계정 화면에서 운영 탭을 열어도 JWT·비밀번호를 새 탭으로 전달하지 않으며, 지표 인증은 운영 화면에서 진행한다.

## React·공통 컴포넌트·라우팅

| 파일 | 역할 |
|---|---|
| `src/client/LoginPage.jsx` | 첨부의 로그인 화면·실제 공개 데모 링크, `AccountPage`·P01 계정 확인 |
| `src/client/LoginForm.jsx` | 전체 화면과 모달의 공통 로그인 폼, 입력 검증·표시 전환·로딩·오류·취소 |
| `src/client/LoginDialog.jsx` | 기존 문의 작성/파일/이력 화면의 공통 로그인 모달 |
| `src/client/authModel.js` | 입력 정리, C01→P01 응답 대조, 실제 역할별 완료 경로 |
| `src/client/api.js` | C01·P01 HTTP 계약, 후보/현재 JWT 헤더 전달과 응답 검증 |
| `src/client/IntakeApp.jsx` | 탭 세션 확정·초기화, 로그인·내 정보 라우팅, 기존 문의 입력 상태 유지 |
| `src/client/IntakeHeader.jsx` | 실제 username → 내 정보 링크, 로그인 모달·로그아웃 |
| `src/client/model.js` | 빈 hash·`#/`·`#/login` → 로그인, `#/account` → 계정. 기존 문의 ID 경로 유지 |
| `src/client/intake-login.css` | 첨부 3열 구성·중앙 카드·네이비 버튼·기능 아이콘·모바일 단일 열 |

코드 경로는 `frontend/legal-procedure-viewer/` 기준이다. 공통 `BrandMark`, `Icon`, `Button`, `Feedback`, `useResource`와 `src/client/assets/intake-office.png`를 재사용했다. 첨부의 AI·역할 소개 문구는 현재 제공 기능에 맞춰 표시한다. 데모는 공개 체험임을 화면에 명시한다. 로그인 목업 PNG는 제품 화면으로 사용하지 않는다.

## 검수 결과·제한

| 검수 | 실제 결과 |
|---|---|
| 최종 `npm run build` | **성공**, viewer/operator/intake 3개 진입점·93개 모듈 |
| 최종 `npm run test:intake` | **126/126 통과**. 기존 111개와 화면01 API/모델 7개·React 8개. 필수값·비밀번호 보존/초기화·후보 토큰·서버 역할·계정 불일치·중복/로딩·401/403/500·취소·데모·로그아웃·입력 보존 포함 |
| 실제 C01/P01 검수 | **통과**. USER/ADMIN 로그인→내 정보 일치, 잘못된 비밀번호 401, 비회원 P01 401, 잘못된 JWT 401, 빈 C01 본문 400 |
| 전체 `test:intake:live` 회귀 | **14/17 통과, 3개 연결 실패**. 마지막 기존 화면03 R02/R03/R04/R05, 화면04 C04 유형·R06 검사 중 Spring JVM이 `0xC0000005`로 종료됨. 재실행 요청은 사용자가 거절하여 재실행하지 않음. 전체 통과로 집계하지 않는다 |
| 복구 후 브라우저 | 실제 USER/ADMIN C01·P01, 사용자→화면02·관리자→계정, 역할 표시, 로그아웃, 새로고침 초기화, 공통 모달, 비회원 데모, 빈 입력·잘못된 비밀번호·표시 전환 확인 |
| 데스크톱 | 1672×941. 중앙 카드 x573/y80/너비532, 전체 높이941, 가로 넘침 없음, 초기 오류 없음 |
| 모바일 | 390×844. 카드 너비347px(스크롤바 제외), 입력·데모·기능 소개 단일 흐름, 가로 넘침 없음 |

전체 실연동 재실행이나 별도 preview/운영자 검수를 다른 경로로 대체 실행하지 않았다. 실패·지연·응답 불일치·취소의 세부 조건은 API/React 테스트로 검증했으며 전부 브라우저에서 주입했다고 주장하지 않는다. 이전 문서의 122/128/125개는 당시 검수 회차 기록이다.

JVM 종료 후 로컬 Spring 서버를 다시 실행했고 H2 메모리 검수 데이터는 초기화되었다. 반복된 JVM 종료의 원인은 이번 프런트 작업에서 해결한 것으로 주장하지 않는다. Java·JDK·서버 인가 코드는 변경하지 않았다.

- 진입: `http://127.0.0.1:5175/intake.html` 또는 `intake.html#/login`.
- [실제 API 증거](../output/intake-01/api-evidence.json), [브라우저 기록](../output/intake-01/browser-evidence.json). 비밀번호·JWT 없음.
- [데스크톱](../output/intake-01/screen01-live-desktop.png), [실제 오류](../output/intake-01/screen01-login-error.png), [모바일](../output/intake-01/screen01-mobile.png), [관리자 계정](../output/intake-01/screen01-admin-account.png).
