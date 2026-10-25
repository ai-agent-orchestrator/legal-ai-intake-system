# 운영자 09~13 교차 검수 · 경로 권한 보완 (2026-10-25)

작성: Claude (별도 브랜치 `claude/operator-route-hardening`). 검수 항목 ④ "운영자 09~13 교차 검수" 중 코드로 확인 가능한 부분을 검토해 발견한 문제를 고쳤다. 이 작업공간에서는 Gradle·Maven 다운로드가 막혀 **백엔드 테스트를 직접 실행하지 못했다.** 적용 후 로컬에서 아래 검증을 실행해야 완료로 본다.

## 발견 사항

| # | 심각도 | 내용 | 조치 |
|---|---|---|---|
| F1 | 높음 | `POST /api/agent/risk/analyze` 등 `/api/agent/**` 쓰기가 익명 허용. 호출하면 위험 이력·Incident가 저장되어 화면12·13과 C12/C15 집계에 그대로 표시됨. 운영 조회는 막혀 있어도 **누구나 운영 데이터를 만들거나 부풀릴 수 있었음** | `/api/agent/**` 전체 ADMIN JWT |
| F2 | 높음 | `GET /api/legal/intake/history`·`summary`가 익명 허용. GET `/api/legal/**` 전체를 법률 카탈로그용으로 공개하면서 함께 열림. **모든 사용자의 사건 요약(summary)이 로그인 없이 조회됨** | 두 경로 ADMIN JWT. 공개 GET은 절차·서식·형사 체크리스트 카탈로그로 한정 |
| F3 | 중간 | 마지막 규칙 `/api/** permitAll` 때문에 새 API가 기본 공개됨. 실습·안내 API(`/api/chat`, `/api/menus`, N+1·트랜잭션 실습, `/api/status`, 관측 안내)도 익명 허용 | `/api/**` 기본 차단(denyAll), 실습·안내 API는 ADMIN |
| F4 | 낮음 | CORS 허용 출처가 `*` | 설정값 `app.cors.allowed-origin-patterns`(기본 127.0.0.1·localhost 모든 포트), 자격 증명 쿠키 불허 |
| F5 | 문서 | `API_STATUS_SPEC.md`에 운영 API·Agent 쓰기를 "현재 개발용 공개"로 기재 | 실제 정책으로 정정 |
| F6 | 검수 도구 | `operator-0912-live-review.mjs`, `operator-0912-browser-qa.mjs`, `observability13-live-review.mjs`는 P1-02 이전 기준(익명 조회 허용을 통과 조건으로 검사) | 상단에 SUPERSEDED 표시. 근거 자료로 사용하지 않음 |

## 변경 후 경로 정책 (`SecurityConfig`)

목록 다섯 개로 관리한다. 목록에 없는 `/api/**`는 익명 401 `JWT_REQUIRED`, 로그인 403 `JWT_FORBIDDEN`이다.

| 목록 | 경로 |
|---|---|
| `PUBLIC_POST_APIS` (익명) | `/api/auth/login`, `/api/intakes/classify-preview`, `/api/intake-events` |
| `PUBLIC_GET_APIS` (익명) | `/api/legal/procedures/**`, `/api/legal/forms/**`, `/api/legal/criminal/**` |
| `ADMIN_GET_APIS` | `/api/legal/intake/history`, `/api/legal/intake/summary` |
| `ADMIN_APIS` | `/api/admin/**`, `/api/agent/**`, `/api/incidents/**`, `/api/chat`, `/api/menus/**`, `/api/n-plus-one-practice/**`, `/api/transaction-practice/**`, `/api/status`, `/api/observability/**`, `/api/security-observability/**` |
| `AUTHENTICATED_APIS` (모든 역할) | `/api/me`, `/api/intakes/**`, `/api/legal-intake/minimal`, `/api/legal/intake/analyze`, `/api/legal/guardrail/**`, `/api/ai/**` |

React 화면이 쓰는 경로(`client/api.js`, `operator/operatorApi.js`, `api/legalProcedureApi.js`)는 모두 기존과 같은 권한으로 남는다. `/api/intake-events`는 비로그인 유입 측정 설계를 유지하며 세션·문의 일치 검증은 Service에 있다. Actuator(C16·guardtimeseries)는 별도 체인의 ADMIN Basic 그대로다.

## 영향

- Postman: `SCREEN09_13_POSTMAN_EXPERIMENTS`의 "S05 가상 분석 기록 생성"을 `{{adminAccessToken}}` Bearer로 변경. 다른 컬렉션·수동 실험에서 `/api/agent/**`, `/api/status`, 실습 API를 익명으로 호출하던 경우 ADMIN 토큰이 필요하다.
- `scripts/Verify-FourHardening.mjs`, `scripts/Verify-LocalDeployment.mjs`는 이미 ADMIN으로 S05를 호출하므로 영향 없음.
- CORS: 화면을 `127.0.0.1` 또는 `localhost`(포트 무관)로 열면 기존과 같다. 다른 PC에서 내부망 IP(예: `http://192.168.0.5:5176`)로 열어 시연하면 Vite 프록시를 거쳐도 Origin이 달라 403이 날 수 있으므로, 그때는 백엔드 실행 시 `CORS_ALLOWED_ORIGIN_PATTERNS=http://127.0.0.1:[*],http://localhost:[*],http://192.168.0.5:[*]`처럼 추가한다.
- `/h2-console/**`는 기존대로 둠(기본 프로필 메모리 DB, local-persistent 프로필은 비활성). 공유 네트워크에서 기본 프로필을 0.0.0.0으로 띄우지 않는 운영 수칙은 별도.

## 테스트

- 신규 `SecurityRouteAccessIntegrationTest`: 익명·USER·LAWYER의 Agent 쓰기 7개 차단과 DB 건수 불변, ADMIN 기록 가능, 사건 요약 집계 ADMIN 전용, 실습·안내 API ADMIN 전용, 공개 카탈로그·게스트 흐름 유지, 미등록 경로 기본 차단(ADMIN 포함), CORS 로컬 출처만 허용·자격 증명 헤더 없음.
- 신규 `SecurityRouteInventoryTest`: 모든 컨트롤러 `/api` 경로가 다섯 목록 중 하나에 속하는지 검사. 새 API의 정책 누락을 빌드에서 잡는다.
- 수정 `AgentRiskThresholdIntegrationTest`: S05 호출에 ADMIN 토큰 추가.

## 로컬 검증 (미실행 · 적용 후 필수)

```powershell
cd backend\guardrail-ready-policy-checker
.\gradlew.bat --offline test --rerun-tasks
```

추가로 화면01~08(Q01)과 운영자 화면09~13을 한 번씩 열어 로그인·조회·Incident 확인/종결이 기존과 같은지 확인한다.
