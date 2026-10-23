# 화면09~13 운영자 ADMIN 권한 구현·검수

2026-10-23. 이번 범위는 남은 개발① 운영자 업무 API8개와 React 인증 연결이다. 문의 재접수·상태/메모 경합·Incident 상태 전이 개발은 포함하지 않는다.

## 서버 계약

| 화면 | API | 필요한 인증 |
|---|---|---|
| 09 | C12 `GET /api/admin/dashboard/summary` | ADMIN Bearer JWT |
| 10 | C13 `GET /api/admin/dashboard/marketing-funnel` | ADMIN Bearer JWT |
| 11 | C14 `GET /api/admin/dashboard/security-events` | ADMIN Bearer JWT |
| 11 | C15 `GET /api/agent/risk/summary` | ADMIN Bearer JWT |
| 12·13 | C17 `GET /api/agent/risk/history` | ADMIN Bearer JWT |
| 12 | C18 `GET /api/incidents` | ADMIN Bearer JWT |
| 12 | C19 `PATCH /api/incidents/{incidentId}/ack` | ADMIN Bearer JWT |
| 12 | C20 `PATCH /api/incidents/{incidentId}/resolve` | ADMIN Bearer JWT |

`SecurityConfig.applicationSecurity`에서 공개 `/api/**` 허용보다 먼저 관리자 경로를 제한한다. 익명·잘못된/만료 JWT는401 `JWT_REQUIRED`, USER·LAWYER JWT는403 `JWT_FORBIDDEN`이다. 거절된 변경 요청은 Controller/Service에 도달하지 않아 Incident를 수정하지 않는다. OPTIONS preflight는 CORS로 처리하며 실제 GET/PATCH 인증 요구는 유지한다.

C16 `/actuator/prometheus`와 보조 `/actuator/guardtimeseries`는 기존 ADMIN Basic을 유지한다. 업무 API에 Basic만 보내거나 Actuator에 JWT만 보내면401이다. 추가 API 번호·DB 스키마 변경은 없다.

## React 흐름

`관리자 로그인 → C01 POST /api/auth/login → P01 GET /api/me → 같은 ADMIN 계정 확인 → 원래 화면09~13`.

JWT는 API 서버 주소별 화면 메모리에만 보관한다. localStorage/sessionStorage/URL에 저장하지 않는다. 업무 요청은 Bearer, 지표는 별도 입력한 Basic만 사용한다. 서버 주소를 변경하면 기존 세션·자료를 지우고 다시 로그인한다. 로그아웃·업무 API401/403은 진행 조회를 취소하고 화면의 이전 자료·내부 상태를 제거한다. 이전 세션의 늦은 응답은 새 로그인 자료를 덮거나 새 세션을 해제하지 않는다. 새로고침 후에는 다시 로그인한다.

`operator.html?mock=1#/observability`의 명시적 모의 화면은 서버 자료 없이 볼 수 있다. ‘실제 자료로 돌아가기’는 관리자 로그인을 요구한다. 모의 화면이나 관리자 메뉴 표시가 실제 API 권한을 대체하지 않는다.

## 검증

- [x] 백엔드 전체225개 통과(실패/오류/제외0), bootJar 성공. 신규 권한 통합7개 검사 포함.
- [x] 운영자 프런트46개 통과. 신규 API 인증6개·React 게이트5개 포함. production 빌드 성공.
- [x] 실제 Spring/H2 HTTP45개 요청: 8개 경로의 익명/USER/LAWYER/잘못된 토큰 거절, ADMIN 조회, 거절된 쓰기 후 기존 OPEN 상태 유지 확인.
- [x] 실제 Chrome 브라우저12개 검수: 로그인 전 요청 없음, USER/LAWYER 진입 거절, ADMIN 로그인/P01, 화면09~13 JWT 조회, ADMIN 확인/해결 저장, 영구 브라우저 저장소 미사용,401/403 주입 후 자료 제거·재로그인, 로그아웃, 모바일390px, 모의→실제 전환 시 로그인, JS 오류 없음.
- [x] C16 Basic과 JWT 분리, 만료 토큰, preflight는 서버 통합 검사로 확인했다. 브라우저의 만료 복구는401 응답 주입 검사이며 실제 만료 시간을 기다린 검사가 아니다.
- [x] 유료 LLM/검색/Guard 호출0회. 검수 서버는18098·격리 H2·빈 키·폐쇄 루프백 공급자·검색 비활성으로 실행하고 검수 후 종료했다.

검사 코드: [서버](../backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/OperatorAuthorizationIntegrationTest.java), [API 인증](../frontend/legal-procedure-viewer/scripts/operator-auth.test.mjs), [React 로그인](../frontend/legal-procedure-viewer/scripts/operator-auth-components.test.mjs). 로컬 증거는 `output/operator-admin-auth/`의 browser-verification.json, verification.json, admin-audit.png, login-mobile.png에 보관한다(Git 제외).

위 인가 검수 당시 사용자 서버는 자동 재시작하지 않았다. 이후 로컬 구동 작업에서 최신 JAR를8080으로 실행했고, 아래 디자인 검수에서도 새 인가 정책의 적용을 확인했다. 기존 통합 문서의 ‘운영자8개 API 공개’는 이전 검수 당시 기록이며 이 문서가 현행 계약이다.

## 관리자 로그인 디자인 공통화 · 2026-10-23 추가 검수

화면01과 운영자 로그인이 `src/shared/LoginLayout.jsx` 및 `intake-login.css`를 함께 사용한다. 사무실 배경, 좌측 소개, 중앙 브랜드·카드, 우측 네 가지 기능 소개, 반응형 구성을 공유한다. 운영자 폼에는 관리자 로그인 문구·접이식 서버 연결 설정·비밀번호 표시 전환·화면01 복귀 링크를 적용했다. 기존 C01→P01 관리자 확인, 메모리 JWT, 기존 경로 복귀와 C16 별도 인증은 유지한다.

- [x] 프런트 운영자46개·의뢰인142개, 합계188개 통과 및 Vite production 빌드 성공.
- [x] 로컬 5175/8080 브라우저: 화면01·운영자 카드 위치/너비·배경 일치, 필수 입력 차단, USER 접근 거부, 실패 후 비밀번호 초기화·숨김, ADMIN C01/P01/C12 정상 확인.
- [x] 390/768/1280/1440/1672px 가로 넘침 없음, 브라우저 JavaScript 오류0. PC·모바일 캡처 육안 확인.
- [x] 유료 LLM 호출0. 백엔드 정책·DB 변경 없음.

증거: `output/shared-login-2026-10-23/verification.json`, `screen01-desktop.png`, `operator-desktop.png`, `operator-mobile.png`. 위 ADMIN 인가 검수 수치는 해당 구현 시점의 기록이다.

