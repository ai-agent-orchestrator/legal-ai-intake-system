# 화면 09~12 운영자 통합 검수

> 2026-10-23 운영자 권한 완료: C12~C15·C17~C20 업무8개 API는 ADMIN Bearer JWT 필수다. React 관리자 로그인·P01 확인·로그아웃·인증 실패 시 자료 제거를 연결했다. C16·guardtimeseries는 ADMIN Basic 유지. 아래 공개 접근 판정은 당시 검수 기록으로 읽는다. [최신 인증 계약·225/46 및 실제 브라우저 검증](OPERATOR_ADMIN_AUTH_REVIEW_2026-10-23.md).

> 2026-10-23 후속 완료: C17 userInput 원문은 DTO/JSON에서 제거했다. C19/C20 없는 숫자 ID는404·INCIDENT_NOT_FOUND, 잘못된 ID형식400이다. 아래 원문 노출/없는 ID500은 이전 회차 발견 기록이며 해당2건은 해결했다. ADMIN 인가와 상태 역행은 미완료다. [구현·검수](FOUR_HARDENING_REVIEW_2026-10-23.md).

후속 화면 13 Observability 구현·검수는 [SCREEN13_OBSERVABILITY_REVIEW.md](SCREEN13_OBSERVABILITY_REVIEW.md)에 기록했다. 후속 운영자 자동 검사 총 26개·빌드 97모듈·브라우저 21개가 통과했으며 화면 11 공통 인증창도 회귀 확인했다. 아래 20개·57개·93모듈은 화면 09~12 검수 당시의 증거로 보존한다.

2026-10-21 · Asia/Seoul. 기존 API·요청/응답·ID·인증 대조에 **React 공통 구현, 빌드, 실제 브라우저, 입력 검증, 로딩·빈 결과·오류·복구 검수**를 통합했다. 첨부 표와 흐름도는 확인할 범위의 기준으로 사용했으며 실행 증거와 구분했다.

**최종 결과: 프로덕션 빌드 성공, 운영자 자동 테스트 20/20, 브라우저 최종 시나리오 57/57 통과.** 검수 중 발견한 화면 10 모바일 가로 넘침과 잘못된 C12 응답으로 인한 흰 화면을 수정했다. 다만 기존 서버 문제 4건은 남아 있으며, 응답 유실 시험에서 같은 PATCH의 중복 도달도 관찰했다. 기능 연결 확인을 관리자 보안·서버 중복 처리 보장 완료로 해석하면 안 된다.

## 1. 화면별 판정과 실행 환경

| 화면 | React·라우트·API | 최신 판정 |
|---|---|---|
| 09 운영 요약 | `OverviewPage` · `operator.html#/overview` · C12 | 실제 집계·분포·이동, 0건, 로딩, 500, 잘못된 200 응답, 15초 제한·재조회 확인. 잘못된 응답의 흰 화면 수정 |
| 10 마케팅 집계 | `MarketingPage` · `#/marketing` · C13 | 문의/요청/고유 문의/현재 상태의 단위 구분, 6단계, 3개 분포, 로딩·0건·500·복구 확인. 모바일 KPI 설명 넘침 수정 |
| 11 보안·위험·비용 | `SecurityPage` · `#/security` · C14/C15/C16 | DB 두 조회와 별도 ADMIN Basic 지표 연결, 필수 입력·형식, 권한 거절, 빈 지표·HTML 오류·재연결·이동 후 계정 초기화 확인 |
| 12 위험 이력·Incident | `AuditPage` · `#/audit` · C17~C20 | 서버 조건/로컬 필터·ID 연결·확인/해결·저장 잠금·재조회, 빈 목록·500·응답 유실 복구 확인. 서버 인가·상태 전이·중복 처리 보장은 별도 |

최신 브라우저 검수는 `http://127.0.0.1:5188/operator.html`의 **Vite 개발 서버 → 로컬 QA 프록시 8089 → 실제 Spring 서버 18088**에서 수행했다. H2 메모리 DB `operatorReact0912`에 가상 문의 1개, 검색 요청 2개, 위험 이력 4개, Incident 2개, 보고 행동 이벤트 3개를 생성했다. 외부 LLM·검색 API나 유료 호출은 실행하지 않았다. S05의 가상 분석 플래그는 로컬 규칙 분석용이며 외부 통신의 증거가 아니다.

데스크톱 1672×941, 모바일 390×844에서 확인했다. 모바일 최종 네 화면의 문서 폭은 375px로 뷰포트 안에 있었고 긴 표는 내부 가로 스크롤을 사용했다. 프로덕션 번들은 별도 빌드에 성공했으며, 배포된 `dist`를 브라우저에서 검수한 것은 아니다.

이전 실제 API 검수는 별도 서버 18087 / H2 `operator0912`에서 **10개 검증 그룹·52회 요청**으로 수행했다. 아래 API 계약·보안 결함의 근거로 보존했고, 이번 브라우저 호출 98회와 합산하지 않았다. 이전 데이터는 위험 이력 3개·Incident 1개로 최신 검수 데이터와 다르다.

## 2. 각 동작의 API·요청/응답·ID·인증·구현

고유 API는 **C12~C20, 9개**다. 모든 정상 성공 응답은 200이며 C16만 텍스트, 나머지는 JSON이다. R/H/I의 전체 필드는 다음 절에 기재했다.

| 화면·동작 | API · 실제 React 함수 | 요청·ID 전달 | 응답·화면 반영 | 인증·검수 결과 |
|---|---|---|---|---|
| 09 진입·새로고침 | C12 `GET /api/admin/dashboard/summary` · `getAdminDashboardSummary` | Body/Path/Query 없음 | 네 건수와 `riskSummary:R` → 4 KPI·3분포 | 현재 **공개**, 인증 헤더 없음. 실제 서버 연결·형식 검증·갱신 확인 |
| 10 진입·새로고침 | C13 `GET /api/admin/dashboard/marketing-funnel` · `getMarketingFunnel` | 입력·ID 없음 | 네 집계, `stages`, 세 분포, 집계 기준·보고 이벤트 수 | 현재 **공개**. 문의 1/요청 2/고유 문의 1/상담 준비 1 확인 |
| 11 보안 DB 조회 | C14 `GET /api/admin/dashboard/security-events` · `getSecurityEvents` | 입력·ID 없음 | 보안 이벤트 배열. 최신 최대 50건. `id`는 행 key | 현재 **공개**. 실제 인증 실패·접근 거절 기록과 C12 집계 연동 확인 |
| 11 위험 DB 조회 | C15 `GET /api/agent/risk/summary` · `getRiskSummary` | 입력·ID 없음 | R → 등급·판정·권고 조치 분포 | 현재 **공개**. 최신 total 4 / HIGH 2 / MEDIUM 1 / LOW 1 |
| 11 지표 연결·새로고침 | C16 `GET /actuator/prometheus` · `getPrometheusMetrics` | `Authorization: Basic base64(username:password)`만 이 요청에 전달. Body/Query 없음 | 텍스트 → `parseMetrics` → `readSecurityMetrics` → 비용·예산·토큰·시간 | **ADMIN Basic 필수**. 최신 브라우저 USER 403·ADMIN 200. 이전 실제 API에서 익명 401·ADMIN JWT 401도 확인 |
| 12 위험 이력·조건 조회 | C17 `GET /api/agent/risk/history` · `getRiskHistory` | 선택 Query `riskLevel` **또는** `decision`. React `riskQuery`가 지원 enum 하나만 인코딩 | H 배열, H.id로 선택 상세. 필터 없을 때만 최신 최대 50건 | 현재 **공개**. 브라우저 HIGH/SAFE 각각 2건. 원문 `userInput`도 서버 응답에 포함 |
| 12 Incident 목록·선택 | C18 `GET /api/incidents` · `getIncidents` | 입력 없음. 상태·심각도는 받은 최근 50건에 대한 **클라이언트 필터** | I 배열의 **I.id** 선택. 받은 DTO로 상세 표시, 상세 GET 없음 | 현재 **공개**. 실제 2개 Incident와 연관 위험 이력·Trace 대조 |
| 12 확인 처리 | C19 `PATCH /api/incidents/{incidentId}/ack` · `ackIncident` → `changeIncidentAndReload` | **C18 I.id**를 Path로 전달. **JSON Body 없음** | 같은 I.id 및 `ACKNOWLEDGED` 대조 후 C18+C12 재조회 | 현재 **공개 쓰기**. 정상·500·응답 유실 시 UI 복구 확인. 서버 전이·중복 보장 제한은 6절 |
| 12 해결 처리 | C20 `PATCH /api/incidents/{incidentId}/resolve` · `resolveIncident` → `changeIncidentAndReload` | 같은 I.id, JSON Body 없음 | 같은 I.id 및 `RESOLVED` 대조 후 C18+C12 재조회 | 현재 **공개 쓰기**. 정상·500·수동 재시도 후 복구 및 버튼 잠금 확인 |

### 응답 필드 전체 대조

- **C12:** `agentRunCount, riskHistoryCount, securityEventCount, openIncidentCount, riskSummary`. 위험 요약은 Service 내부 재사용이며 브라우저가 C15를 다시 호출하는 구조가 아니다.
- **C13:** `submittedIntakeCount, searchRequestCount, searchRequestedIntakeCount, consultationReadyIntakeCount, stages, caseTypeDistribution, desiredNextStepDistribution, regionDistribution, measurementBasis, dropOffMeasured, eventCounts`.
- **C13 stages[]:** `stage,count,measurement`. 6단계는 `VISIT, INTAKE_STARTED, INTAKE_SUBMITTED, SUMMARY_VIEWED, LAWYER_SEARCH_REQUESTED, CONSULTATION_READY`. 단위는 `REPORTED_EVENT, STORED_INTAKE, DISTINCT_INTAKE, CURRENT_STATUS`로 구분한다. 다른 단위의 수치를 전환율·이탈률로 계산하지 않는다.
- **C14:** `id,eventType,method,uri,status,username,createdAt`. `status`는 문자열이며 실제 401/403, `eventType`은 `AUTH_FAILURE/ACCESS_DENIED`를 확인했다.
- **R / C15:** `total,high,medium,low,decisionCounts,recommendedActionCounts`. 두 분포는 코드→건수 Map이다.
- **H / C17:** `id,userInput,toolName,decision,riskScore,riskLevel,signals,recommendedAction,guardrailReady,traceId,createdAt`. `signals`는 문자열 배열. **React에서 안 보이는 userInput도 실제 네트워크 응답에는 있다.**
- **I / C18~C20:** `id,incidentType,severity,status,decision,riskScore,recommendedAction,riskHistoryId,traceId,message,createdAt,updatedAt`. 상태는 `OPEN/ACKNOWLEDGED/RESOLVED`.

H.decision은 `SAFE, UNSTABLE_TOOL_LOOP, SUSPICIOUS_RETRY, RISKY_EXTERNAL_ACCESS, APPROVAL_BYPASS_RISK, HIGH_RISK_AGENT_BEHAVIOR, SUSPICIOUS`다. `ALLOW/BLOCK` 등은 recommendedAction이므로 decision Query로 보내지 않는다. 모르는 표시 코드는 원래 값을 표시한다.

이전 실제 API 시험에서 C17에 두 조건을 직접 주면 riskLevel이 우선했고, 미지원 `riskLevel=NOT_A_LEVEL`은 400 대신 200·빈 배열이었다. React는 지원 선택값만 전송한다. 코드상 조건 조회에는 50건 제한이 없으며 이번에 대량 데이터 성능을 시험한 것은 아니다.

### C16 지표 이름·결측값·인증

| 화면 값 | Prometheus 이름·라벨 | 해석·검수 |
|---|---|---|
| 실제·추정·정산·진행 예약 비용 | `ai_cost_actual_usd`, `ai_cost_estimated_usage_usd`, `ai_cost_accounted_usd`, `ai_cost_reserved_usd` | 실제 서버의 0과 지표 부재를 구분. 유료 토큰 발생 시험 아님 |
| 월·일·세션 예산 | `ai_cost_budget_usd`, `ai_cost_daily_budget_usd`, `ai_cost_session_budget_usd` | 이번 서버 설정 2 / 1 / 0.30 USD. 세션 예산은 요청 1회 한도가 아님 |
| 단계별 정산 | `ai_cost_stage_accounted_usd`, stage=`input_guardrail/answer/output_guardrail/search_query/search_explanation` | 비용 LLM 라벨은 `answer`, 시간 LLM 라벨은 `llm` |
| 토큰 | `ai_llm_tokens_total`, type=`prompt/completion` | 일치 샘플 합산. 없는 샘플을 가짜 0으로 만들지 않음 |
| 평균 시간 | `ai_chat_duration_seconds_{sum,count}`, `ai_chat_stage_duration_seconds_{sum,count}` | sum÷count. 관측 0회·결측은 `—`. 동기 Chat 평균이며 SSE 전체 지연·P95 아님 |

슬래시로 나열한 라벨은 가능한 값의 표기이며 그런 Query를 서버에 보내지 않는다. C16은 진입 시 자동 호출하지 않고 계정 입력 후 연결한다. 계정은 SecurityPage 메모리에만 보관하며 이동·연결 해제 시 초기화한다. URL·스토리지에 저장하지 않는다. 최신 프록시 기록에서 Basic 요청 6회는 모두 `/actuator/prometheus`였고 다른 업무 API로 전파되지 않았다. 기록에는 인증 **종류**만 보관했다.

## 3. ID 연결과 저장·재조회 증거

최신 격리 DB에서 **Incident 1 ↔ riskHistory 2**, **Incident 2 ↔ riskHistory 4**로 서로 다른 ID를 사용했다. `INC-00001`, `RH-00002`는 표시 문자열이며 API Path ID가 아니다.

```text
C18 I.id=1 선택 → PATCH /api/incidents/1/ack     (Body 0byte)
                → GET /api/incidents + GET /api/admin/dashboard/summary
같은 I.id=1     → PATCH /api/incidents/1/resolve (Body 0byte)
                → GET /api/incidents + GET /api/admin/dashboard/summary
I.riskHistoryId=2 ↔ C17 H.id=2, Trace 보존
```

브라우저에서 필터로 사라진 선택 Incident도 상세를 유지하고 범위 밖 안내를 표시했다. 연관 위험 이력이 현재 조회에 없으면 그 사실을 안내하며, 임의의 다른 위험 행을 연결하지 않았다. 목록의 다른 ID와 표시 ID를 혼동하지 않았다.

초기 운영 요약은 실행 4 / 위험 4 / 보안 1 / 미확인 2였다. C16 USER 권한 거절로 실제 보안 기록이 추가되어 보안 2가 되었고, Incident 1 처리 후 미확인 1, 두 Incident 해결 후 **4 / 4 / 2 / 0**으로 재조회됐다. `openIncidentCount`는 미확인 OPEN 수이며 확인됨도 제외한다.

최신 [호출 기록](../output/operator-09-12-react-review/traffic.json)은 OPTIONS·정상 조회·오류 주입·저장을 포함해 **98회**다. PATCH는 **7회**로 모두 Body 0byte·인증 없음이었다. 6번의 저장 조작 중 응답을 끊은 ack가 프록시에 두 번 도달했다. 이것은 테스트 98개 또는 저장 7번을 사용자가 클릭했다는 뜻이 아니다. 세부 해석은 6.5절에 기록했다.

## 4. React 공통 디자인·컴포넌트·라우팅

| 대상 | 구현과 최신 확인 |
|---|---|
| 실제 React 진입 | `operator.html` → `src/operator/main.jsx`의 `createRoot` → `OperatorApp`. Vite의 별도 operator 빌드 진입점 확인 |
| 공통 셸·디자인 | `OperatorApp`, `BrandMark`, `Icon`, 공통 CSS와 화면별 CSS. 헤더·사이드바·서버 설정·아이콘·카드·표는 실제 React. 사진 자산을 공통 배경으로 사용 |
| 공통 조회 상태 | `useResource` + `Resource`의 idle/loading/ok/error, 로딩 `role=status`, 오류 `role=alert`, 빈 결과 안내·다시 조회. 새 조회·이동 시 기존 요청 취소 및 늦은 응답 무효화 |
| 공통 새로고침 | `PageRefresh`와 화면별 갱신. 로딩 중 버튼 비활성화. 실패 데이터를 성공한 0건으로 표시하지 않음 |
| API 계층 | `operatorApi.js`에서 9개 API·15초 제한·Abort·HTTP 오류·Trace 안내·JSON/텍스트 구분·C16 Basic 분리 |
| 응답 검증 보강 | C12/C13/C15 필수 집계·분포·중첩 구조 확인, 배열 API의 null/비객체 행 거절. 검증 실패는 공통 오류·재조회로 표시 |
| 해시 라우트 | overview/marketing/security/audit. 빈 해시는 overview로 이동. 브라우저 뒤로/앞으로, marketing 직접 주소·새로고침, 미등록 경로의 명시적 안내 확인 |
| 이동·접근성 기본 동작 | 활성 메뉴 `aria-current`, 화면 H1 하나, 이동 후 H1 포커스, 데스크톱/모바일 메뉴와 인증 dialog 확인. 전체 접근성 표준 준수 판정은 아님 |
| 저장 중 보호 | `AuditPage`의 mutation ref + pending으로 재클릭·필터·새로고침·앱 메뉴 이동 잠금. 강제 클릭 시험에서도 앱 메뉴로 다른 화면에 이동하지 않음. 브라우저 자체 종료/강제 URL 변경의 차단 보장은 아님 |
| 쓰기 응답·읽기 복구 | `auditModel.js`에서 id/목표 status 대조 후 C18/C12 재조회. 실패·응답 유실 시에도 읽기로 실제 상태 확인. React 코드에서 PATCH 자동 재시도 없음 |
| 반응형 | 1672px의 4 KPI·분포·11의 3열·12의 좌우 목록/상세, 390px의 세로 배치 확인. 12 표의 내부 폭 700px/컨테이너 326px·overflow auto 확인 |

응답 검증은 모든 DTO 행의 enum·날짜·부모 ID를 엄격하게 검증하는 전체 스키마가 아니다. JSX 전반을 보호하는 공통 ErrorBoundary도 이번에 추가하지 않았다. 따라서 확인한 잘못된 응답 경로의 복구와 임의 렌더링 오류 전체 복구를 구분한다.

관련 코드: [OperatorApp.jsx](../frontend/legal-procedure-viewer/src/operator/OperatorApp.jsx), [operatorApi.js](../frontend/legal-procedure-viewer/src/operator/operatorApi.js), [useResource.js](../frontend/legal-procedure-viewer/src/operator/useResource.js), [components.jsx](../frontend/legal-procedure-viewer/src/operator/components.jsx), [auditModel.js](../frontend/legal-procedure-viewer/src/operator/auditModel.js), [AuditPage.jsx](../frontend/legal-procedure-viewer/src/operator/AuditPage.jsx), [SecurityPage.jsx](../frontend/legal-procedure-viewer/src/operator/SecurityPage.jsx).

## 5. 빌드·입력·로딩·빈 결과·오류·브라우저 검수

| 구분 | 실행·결과 | 증거 |
|---|---|---|
| 프로덕션 빌드 | 수정 후 `npm run build` 성공, 93모듈. operator HTML/CSS/JS 번들 생성 | `dist/operator.html`, `dist/assets/operator-BONkc8Ha.css`, `dist/assets/operator-BFti8Ia_.js` |
| 자동 테스트 | 수정 후 `npm run test:operator` **20개 통과·실패 0**. API/ID/인증/분포/지표/저장 복구 + 새 응답 형식 회귀 2개 | [operator-api.test.mjs](../frontend/legal-procedure-viewer/scripts/operator-api.test.mjs), [audit-workflow.test.mjs](../frontend/legal-procedure-viewer/scripts/audit-workflow.test.mjs) 등 4개 파일 |
| 브라우저 | 최종 고유 시나리오 **57개 통과·실패 0** | [browser-checks.json](../output/operator-09-12-react-review/browser-checks.json) |
| 원시 시도 | 총 62개. 최종 시나리오와 중복 합산하지 않음. 수정 전 모바일 결함, 검수 기대문구/타이밍 교정, 스크린샷 갱신 시도를 보존 | [raw-browser-attempts.json](../output/operator-09-12-react-review/raw-browser-attempts.json) |
| 콘솔 | 잘못된 C12의 흰 화면 재현 시 오류 4개 보존. 응답 검증 수정 이후 수집된 warn/error는 0개 | [console.json](../output/operator-09-12-react-review/console.json). 전체 검수에서 오류 0개라고 표현하지 않음 |
| 검수 도구 문법 | `node --check scripts/operator-0912-browser-qa.mjs` 성공 | 로컬 전용 프록시·가상 데이터 준비 도구. 제품 번들에 포함하지 않음 |

### 입력 검증

| 입력·동작 | 확인 내용 | 최종 시나리오 |
|---|---|---|
| 공통 서버 주소 | `ftp:` 거절, HTTP(S)만 허용. 코드상 URL 계정/Query/Hash도 거절. 잘못된 설정을 적용해 기존 데이터를 지우지 않음 | `shared-settings-validation` |
| C16 계정 | 빈 필수값은 제출 차단, username의 `:`는 pattern 거절, dialog 유지. USER는 403 안내, ADMIN은 실제 지표 연결 | `11-auth-required`, `11-auth-format`, `11-user-denied`, `11-metrics-connected` |
| C17 조건 | 위험 수준/판정 중 하나만 전송, HIGH/SAFE 실제 결과 각각 2건. 미지원 조건은 자동 테스트로 거절 확인 | `12-risk-level-filter`, `12-decision-filter` |
| Incident 선택·Path ID | 선택한 I.id 사용, 양의 정수 검사, 선택 없음·이미 처리된 상태 버튼 차단 | `12-incident-selection`, `12-linked-history`, `12-resolved` |
| 저장 중 입력·이동 | 5초 지연 저장 중 버튼·조건·앱 메뉴 잠금, 연타 방지. 응답 후 최신 상태에 맞춰 해제 | `12-write-lock`, `12-navigation-locked`, `12-acknowledged` |

### 화면별 로딩·빈 결과·오류·복구

오류·지연·빈 결과·잘못된 응답은 QA 프록시에서 해당 경로에 주입했다. 실제 서버의 정상 조회·쓰기와 연결한 시험이며, 모든 500이 실제 백엔드 결함에서 발생했다는 의미는 아니다.

| 화면 | 로딩 | 정상 빈 결과 | 오류·복구 | 근거 시나리오 |
|---|---|---|---|---|
| 09 | 5초 지연 시 로딩·새로고침 잠금 | 유효 DTO의 0 KPI·빈 분포 유지 | HTTP 500·잘못된 200 응답 모두 셸/오류/재조회 유지. 17초 지연 시 15초 제한 안내 후 정상 복구 | `09-loading/empty/http-error/http-recovery/malformed-handled/timeout/timeout-recovery` |
| 10 | 로딩·새로고침 잠금 | 0 KPI·빈 분포 안내, 측정 단위 유지 | 500 안내 → 다시 조회로 실제 1/2/1/1 복구 | `10-loading/empty/http-error/http-recovery` |
| 11 | C14/C15 조회 상태, C16 별도 연결·로딩 | 보안 []·위험 0, 빈 지표 텍스트는 모든 값 `—` | C14/C15 500 재조회, C16 USER 403·HTML 형식 오류 안내. HTML 원문 렌더링 없이 다른 패널 유지·재연결 성공 | `11-loading/empty/http-error/http-recovery/metrics-empty/metrics-format-error/metrics-recovery` |
| 12 | 두 목록 로딩·새로고침 잠금 | [] 안내·쓰기 버튼 없음 | 조회 500의 개별 재조회. ack/resolve 500은 선택·기존 상태 유지 후 C18/C12 조회. 저장 성공 후 응답만 끊어도 실제 ACK 읽어 복구. 수동 resolve 재시도 성공 | `12-loading/empty/http-error/http-recovery/ack-error/lost-response/resolve-error/write-recovery` |

57개 세부 목록·텍스트 단언·뷰포트·조건 값은 browser-checks.json에 보관했다. 표의 `/`로 축약한 이름은 해당 파일의 독립 시나리오를 뜻한다. 빈 결과는 유효한 서버 DTO/배열을 0/[]로 치환했고, 실제 DB를 삭제한 시험은 아니다.

### 이번에 수정한 두 항목

1. **화면 10 모바일 가로 넘침:** 긴 KPI 설명이 줄바꿈 없이 카드 밖으로 나가 390px 뷰포트에서 문서 폭이 커졌다. [marketing.css](../frontend/legal-procedure-viewer/src/operator/marketing.css)의 모바일 설명에 폭 제한·줄바꿈·긴 단어 줄바꿈을 적용했다. 수정 후 문서 폭 375px, 설명이 카드 안에 있음을 확인했다. [수정 전 측정](../output/operator-09-12-react-review/10-mobile-before.json) · [수정 후 화면](../output/operator-09-12-react-review/10-mobile.png).
2. **잘못된 C12 200 응답의 흰 화면:** `{invalid:true}`를 정상 응답으로 받으면 `riskSummary.total` 참조에서 React가 중단됐다. 공통 API 계층에 C12/C13/C15 필수 구조·숫자·Map·단계 검사와 배열의 null 행 거절을 추가했다. 이제 셸을 유지하며 오류·다시 조회를 표시한다. 정상 0건은 계속 허용한다. 두 자동 회귀 테스트와 브라우저 C12 오류·정상 복구를 통과했다. [수정 전](../output/operator-09-12-react-review/09-malformed-before.png) · [수정 후](../output/operator-09-12-react-review/09-malformed-handled.png).

## 6. 남은 서버 문제와 추가 검증

### 6.1 P1 · 운영자 업무 API 8개 공개 접근

익명으로 C12/C13/C14/C15/C17/C18 조회와 C19/C20 변경이 성공했다. 이전 실제 API 시험에서는 USER JWT의 C12도 200이었다. C16만 익명 401·USER Basic 403·ADMIN JWT 401·ADMIN Basic 200을 구분한다. 메뉴의 관리자 표시나 `/api/admin` 경로는 서버 인가를 대신하지 않는다.

근거: [SecurityConfig.java:78](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java#L78), [기존 실제 API 증거](../output/operator-09-12-api-review/verification.json), 최신 traffic.json. 운영 전 관리자 인가와 프런트 인증 전달을 함께 연결해야 한다. 서버만 인증 필수로 바꾸면 현재 일반 API 호출은 401이 되므로 동시 정리가 필요하다. 이번 검수에서 권한 정책을 임의 변경하지 않았다.

### 6.2 P1 · C17에 userInput 원문 포함

이전 실제 API 시험에서 `SYNTHETIC_PRIVATE_MARKER_010-1234-5678`이라는 가상 입력을 익명 HIGH 조회로 그대로 받았다. React는 원문을 렌더링하지 않지만 네트워크 응답에서는 읽을 수 있다. 운영자 DTO의 원문 제외/마스킹·최소 필드와 인가를 함께 보강해야 한다.

근거: [AgentRiskHistoryResponse.java](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskHistoryResponse.java), 기존 verification.json. 실제 개인정보는 사용하지 않았다.

### 6.3 P2 · RESOLVED → ACKNOWLEDGED 역행 허용

React는 해결된 Incident의 두 버튼을 막지만, 이전 실제 익명 `PATCH /api/incidents/1/ack`는 RESOLVED를 ACKNOWLEDGED로 되돌렸다. 서버 허용 전이·잘못된 전이의 409·동시성 제어가 필요하다. 가상 데이터는 시험 후 RESOLVED로 복원했다.

근거: [RiskIncident.java:101](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/entity/RiskIncident.java#L101). 이번 브라우저 버튼 보호 통과는 서버 전이 보호 통과가 아니다.

### 6.4 P2 · 없는 Incident가 404 대신 500

이전 실제 `PATCH /api/incidents/999999/ack`는 500, 잘못된 형식 `not-an-id`는 400이었다. 없는 레코드의 IllegalArgumentException을 404로 매핑해 대상 없음과 서버 장애를 구분해야 한다.

근거: [RiskIncidentService.java:81](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java#L81). 이번 화면의 500 처리·재조회 구현이 서버 오류 계약 자체를 수정하지는 않는다.

### 6.5 추가 관찰 · 응답 유실 시 같은 PATCH 두 번 도달

응답 유실 시나리오는 upstream의 실제 저장 성공 후 QA 프록시가 응답 소켓을 끊었다. **`PATCH /api/incidents/2/ack`가 20ms 간격으로 두 번 프록시에 도달했고 둘 다 실제 upstream 200으로 기록됐다.** 이후 C18/C12를 읽어 ACKNOWLEDGED로 복구했다. 원시 호출을 숨기거나 쓰기 1회로 집계하지 않았다.

`changeIncidentAndReload`는 PATCH를 한 번 호출하고 실패 뒤 두 GET을 수행하며 자동 재시도 루프가 없다. 자동 테스트도 해당 함수 수준에서 재전송 없음을 확인한다. 그러나 이번 브라우저·전송 경로에서 중복 요청이 관측됐으므로 **이 근거만으로 네트워크 전체에서 쓰기가 정확히 한 번이라고 보장할 수 없다.** 정확한 재전송 주체는 이번 시험으로 특정하지 않았다.

서버의 반복 요청 무해성, 전이 검증, 동시 요청 충돌 처리·필요 시 요청 식별자 기반 중복 방지는 추가 검증 대상이다. 같은 상태 ack는 최종 상태가 같더라도 두 요청이 처리된 사실과 구분해야 한다. 위 4건의 기존 결함과 별도로 기록하며, 신규 중복 방지 구현 완료 판정은 하지 않는다.

## 7. 증거·변경 범위·제한

| 자료 | 내용 |
|---|---|
| [통합 실행 요약](../output/operator-09-12-react-review/verification.json) | 최신 20 테스트·빌드·57 브라우저·98 호출·7 PATCH, 수정·미해결·제한 |
| [브라우저 결과](../output/operator-09-12-react-review/browser-checks.json) | 최종 57개, 조건 단언·활성 메뉴·폭·포커스·텍스트 |
| [실제 호출·최종 DTO](../output/operator-09-12-react-review/traffic.json) | 인증 종류/경로/메서드/Body 길이/주입 종류/상태. 최신 운영 요약과 두 Incident |
| [가상 준비 데이터](../output/operator-09-12-react-review/seed.json) | 문의·검색·위험·Incident의 실제 연결 ID와 초기 집계 |
| [이전 실제 API 검증](../output/operator-09-12-api-review/verification.json) | 10그룹·52회, DTO 필드·인증 조합·결함 재현. 이번에 전체 재실행한 것은 아님 |
| [이전 API 보고서](SCREEN09_12_INTEGRATION_REVIEW.md) | 이전 회차의 18 테스트·API 52회 증거 보존 |
| [이전 디자인·구현 기록](OPERATOR_09_12_API_REACT_MAPPING.md) | 과거 회차 수치·디자인 비교 보존. 현재 수치와 합산하지 않음 |

최신 실행 명령은 프런트 디렉터리에서 `npm run test:operator`, `npm run build`, `node --check scripts/operator-0912-browser-qa.mjs`다. 브라우저 조작은 실제 앱 브라우저에서 수행했고, QA 프록시 도구는 `serve/seed/fault/mode/evidence`를 지원한다. 재현하려면 전용 격리 DB에서 seed를 **한 번** 실행해야 하며 기존 DB에 중복 실행하지 않는다. 로컬 환경의 통신 권한을 허용한 실행 환경에서 검증했다.

제품 수정은 operatorApi 응답 검증과 모바일 marketing CSS, 해당 회귀 테스트·fixture에 한정했다. 백엔드 인가·DTO·상태 전이는 변경하지 않았다. C16 로그에는 비밀번호·Basic 문자열·JWT를 저장하지 않았다. 기존 사용자 개발 서버·DB를 바꾸지 않고 검수용 서버만 정리했다.

실제 외부 검색/LLM 비용, 유료 사용량·SSE, 장기 데이터·동시성 부하, 모든 브라우저/해상도, 배포 호스팅, 전체 접근성, 모든 비정상 DTO 조합은 이번 검수 범위가 아니다. S01~S05는 운영자 진입 시 자동 실행하지 않으며 P01/P04/P05/P06을 9개 API에 중복 포함하지 않았다. P06은 C13 집계 준비에만 사용했다.

최종 데스크톱 화면은 아래 스크린샷을 직접 확인했다. 일부 원시 fullPage 캡처의 일시적 레이아웃 차이는 최종 UI 판정 근거로 쓰지 않고 DOM 단언·최종 일반 캡처와 함께 판단했다.

![화면09 최종 데스크톱 검수](../output/operator-09-12-react-review/09-final-desktop.png)
