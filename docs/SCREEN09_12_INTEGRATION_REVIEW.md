# 화면09~12 API·코드 통합 검수

최신 빌드·브라우저·입력·로딩·빈 결과·오류 처리까지 포함한 통합본은 [SCREEN09_12_ADMIN_INTEGRATION_REVIEW.md](SCREEN09_12_ADMIN_INTEGRATION_REVIEW.md)다. 최신 자동 테스트 20개·브라우저 57개 및 수정 2건, 응답 유실 시 중복 PATCH 관찰을 기록했다. 아래 문서는 이전 API 검수 10그룹·52회와 당시 수치의 증거로 보존한다.

2026-10-21 (Asia/Seoul). 첨부 매핑의 **C12~C20, 고유 API9개**를 현재 Controller·Service·DTO·SecurityConfig와 실제 React 호출 코드에 대조했다. 실제 격리 Spring 서버에서 React의 API 함수·Incident 갱신 함수를 실행해 확인했다. 첨부 표는 연결 범위의 기준이며 실행 성공 증거로 대신하지 않았다.

**연결·필드·ID 전달은 확인했다. 운영 보안 완료 판정은 아니다.** C16만 ADMIN Basic이며 나머지8개는 공개 접근이다. 아래 실제 API 검증10개 그룹·52회 요청의 통과에는 현재 결함을 재현한 단언도 포함한다. 결함까지 정상 기능으로 통과했다는 뜻은 아니다.

## 1. 검수 결과

| 화면 | 구현·연결 | 판정 |
|---|---|---|
| 09 운영 요약 | `OverviewPage` → C12 | 실제 실행/위험/보안/미확인 건수 및 위험 집계 DTO 연결. 관리자 전용 인가 없음 |
| 10 마케팅 집계 | `MarketingPage` → C13 | 저장 문의·검색 요청·고유 문의·현재 상담 준비·보고 이벤트의 단위 구분 확인 |
| 11 보안·위험·비용 | `SecurityPage` → C14/C15, 계정 입력 후 C16 | 보안 DB·위험 DB·Prometheus를 별도 조회. C16의401/403/200·Basic/JWT 차이 확인 |
| 12 위험 이력·Incident | `AuditPage` → C17/C18/C19/C20 | 조건 조회·실제 ID 선택·확인/해결·C18/C12 재조회 연결. 서버 상태 전이·없는 ID 오류 보완 필요 |

이번 실제 서버: `http://127.0.0.1:18087`, 격리 H2 메모리DB `operator0912`. 가상 문의1개·검색 요청2개·분석 이력3개·Incident1개·행동 이벤트3개를 준비했다. 실제 외부 LLM·검색·Guard 호출은 없었다. 데이터 생성에 사용한 S05는 로컬 규칙 분석이며, 플래그 `externalApiCall=true`가 실제 외부 통신을 실행하는 것은 아니다.

## 2. 동작별 API·요청/응답·ID·인증 대조

모든 성공 응답은200이다. C16은 텍스트이고 나머지는JSON이다. 표의 R은 위험 요약, H는 위험 이력, I는Incident DTO로 다음 절에 전체 필드를 기재했다.

| 화면·동작 | API / React 함수 | 요청 필드·ID 전달 | 성공 응답·화면 사용 | 인증·구현 여부 |
|---|---|---|---|---|
| 09 운영 요약·새로고침 | C12 `GET /api/admin/dashboard/summary` / `getAdminDashboardSummary` | Body·Path·Query 없음 | `{agentRunCount,riskHistoryCount,securityEventCount,openIncidentCount,riskSummary:R}`. 네 KPI·세 분포. Service가 위험 요약과 OPEN 건수를 내부 재사용 | **공개**, React도 인증 헤더 없음. 실제200·전체 필드·집계 확인 |
| 10 접수 여정·분포·새로고침 | C13 `GET /api/admin/dashboard/marketing-funnel` / `getMarketingFunnel` | 입력·ID 없음 | 문의·요청·고유 문의·현재 상태 건수, `stages`, 세 분포, 집계 기준·이벤트 수. DTO 전체는 아래 | **공개**, 인증 헤더 없음. 실제200·문의1/요청2/고유1/상담 준비1·중복 이벤트 제외 확인 |
| 11 최근 보안 이벤트 | C14 `GET /api/admin/dashboard/security-events` / `getSecurityEvents` | 입력·ID 없음 | `{id,eventType,method,uri,status,username,createdAt}[]`. 최신 최대50건. id는 행 key이며 변경 API로 보내지 않음 | **공개**. 실제 인증 실패2·권한 거절1과 C12 보안 건수3 일치 |
| 11 위험 요약 | C15 `GET /api/agent/risk/summary` / `getRiskSummary` | 입력·ID 없음 | R. 전체/등급/판정/권고 조치 분포 | **공개**. 실제 total3·HIGH1·MEDIUM1·LOW1, C12의riskSummary와 일치 |
| 11 비용·성능 지표 조회 | C16 `GET /actuator/prometheus` / `getPrometheusMetrics` | Body·Query 없음. `Authorization: Basic base64(username:password)`만 이 요청에 전달 | Prometheus 텍스트를 `parseMetrics` → `readSecurityMetrics`로 변환. 비용·예약·예산·단계·토큰·Timer | **ADMIN Basic 필수**. 익명401·USER Basic403·ADMIN JWT401·ADMIN Basic200. JWT로 대체되지 않음 |
| 12 위험 수준/판정 조건 조회 | C17 `GET /api/agent/risk/history` / `getRiskHistory` | 선택Query `riskLevel` 또는 `decision`. React의 `riskQuery`는 지원 enum 하나만 URL 인코딩. ID 없음 | H 배열. 선택 상세는 반환된 H.id 사용. 무조건50건이 아니라 필터 없을 때만 최신 최대50건 | **공개**. HIGH·판정·동시Query의riskLevel 우선·원문 필드 반환 확인 |
| 12 Incident 목록·필터·선택 | C18 `GET /api/incidents` / `getIncidents` | Body·Query 없음. 상태/심각도 필터는 최근50건을 React에서 처리 | I 배열. 행의 **I.id**를 선택. 이미 받은 DTO로 상세 표시하며 별도 상세GET 없음 | **공개**. 실제200·전체 필드·연관 H.id/traceId 확인 |
| 12 확인 처리 | C19 `PATCH /api/incidents/{incidentId}/ack` / `ackIncident` → `changeIncidentAndReload` | C18의 **I.id**를Path에 전달. **JSON Body 없음**. RH-/INC- 표시 접두사·H.id를 전달하지 않음 | 같은 I.id·status=ACKNOWLEDGED 확인 후 C18+C12 재조회 | **공개 쓰기**. 실제200·동일 부모 연결·재조회 확인. RESOLVED 역행도 서버에서 허용되어 결함으로 기록 |
| 12 해결 처리 | C20 `PATCH /api/incidents/{incidentId}/resolve` / `resolveIncident` → `changeIncidentAndReload` | 같은 I.id, JSON Body 없음 | 같은 I.id·status=RESOLVED 확인 후 C18+C12 재조회. 정상 흐름 후 미확인0 | **공개 쓰기**. 실제200·동일ID/위험 이력/Trace 보존 확인 |

### 실제 응답 필드

- **C12**: `agentRunCount, riskHistoryCount, securityEventCount, openIncidentCount, riskSummary`. riskSummary는 R이며 C12에서 C15를 다시 HTTP 호출하는 구조가 아니다.
- **C13**: `submittedIntakeCount, searchRequestCount, searchRequestedIntakeCount, consultationReadyIntakeCount, stages, caseTypeDistribution, desiredNextStepDistribution, regionDistribution, measurementBasis, dropOffMeasured, eventCounts`.
- `stages[]`: `stage,count,measurement`. 실제6단계는 VISIT / INTAKE_STARTED / INTAKE_SUBMITTED / SUMMARY_VIEWED / LAWYER_SEARCH_REQUESTED / CONSULTATION_READY. 측정 단위는 REPORTED_EVENT / STORED_INTAKE / DISTINCT_INTAKE / CURRENT_STATUS다.
- **C14**: `id,eventType,method,uri,status,username,createdAt`. status는 DTO의문자열이며 이번 결과는401/403이다. eventType은 AUTH_FAILURE/ACCESS_DENIED였다.
- **R / C15**: `total,high,medium,low,decisionCounts,recommendedActionCounts`. 분포는 실제 코드→건수Map이다.
- **H / C17**: `id,userInput,toolName,decision,riskScore,riskLevel,signals,recommendedAction,guardrailReady,traceId,createdAt`. signals는 문자열 배열이다. **userInput 원문도 실제 응답에 포함된다.**
- **I / C18~C20**: `id,incidentType,severity,status,decision,riskScore,recommendedAction,riskHistoryId,traceId,message,createdAt,updatedAt`. 현재 Incident 상태는 OPEN / ACKNOWLEDGED / RESOLVED다.

H.decision은 SAFE / UNSTABLE_TOOL_LOOP / SUSPICIOUS_RETRY / RISKY_EXTERNAL_ACCESS / APPROVAL_BYPASS_RISK / HIGH_RISK_AGENT_BEHAVIOR / SUSPICIOUS다. ALLOW/BLOCK 등은 recommendedAction이며 decision 값으로 보내지 않는다. React는 모르는 표시 코드를 원래 값으로 표시한다.

C17에 두 Query를 직접 보내면 서버는 riskLevel을 우선한다. 지원하지 않는 `riskLevel=NOT_A_LEVEL`은400이 아니라200의빈 배열이었다. 프런트는 지원 값만 선택하게 제한한다. 코드상 조건 조회는50건 제한이 없으며, 이번에 대량 데이터 성능·상한을 재현한 것은 아니다.

### C16 텍스트→지표 대조

| 화면 사용 값 | 실제 Prometheus 이름·라벨 | 판정 |
|---|---|---|
| 실사용·추정·정산·진행 예약 비용 | `ai_cost_actual_usd`, `ai_cost_estimated_usage_usd`, `ai_cost_accounted_usd`, `ai_cost_reserved_usd` | 실제 반환 지표 파싱 확인. 이번 유료 호출 없음 |
| 월·일·세션 예산 | `ai_cost_budget_usd`, `ai_cost_daily_budget_usd`, `ai_cost_session_budget_usd` | 실제 설정 값 조회. 세션 예산을 요청1회 한도로 해석하지 않음 |
| 단계별 정산 | `ai_cost_stage_accounted_usd{stage="input_guardrail\|answer\|output_guardrail\|search_query\|search_explanation"}` | 코드 라벨 대조. 비용LLM 라벨은 answer, TimerLLM 라벨은llm으로 구분 |
| 누적 토큰 | `ai_llm_tokens_total{type="prompt\|completion"}` | 라벨별 합산. 없는 지표는0으로 만들지 않음 |
| 평균 시간 | `ai_chat_duration_seconds_{sum,count}`, `ai_chat_stage_duration_seconds_{sum,count}` | sum÷count. S01 동기Chat Timer이며 SSE 전체 지연·P95는 아님 |

표의 `\|`는 가능한 라벨 값을 나열한 표기이며 정규식 Query를 전송한다는 의미가 아니다. 화면은 매칭되는 실제 샘플을 읽는다. 관측이 없는 Timer/토큰은 `—`, 실제0은0으로 구분하는 기존 자동 검사도 통과했다. 이번 검수에서 새 LLM 토큰·처리 시간을 만들어 검증하지는 않았다.

## 3. ID 전달·인증·React 구현 대조

실제 가상 데이터에서 **Incident id=1, riskHistoryId=2**였다. 위험 이력 ID와 Incident ID를 일부러 다르게 만든 뒤 React의 실제 갱신 함수를 실행했다.

```text
C18 I.id=1 선택 → PATCH /api/incidents/1/ack (Body 없음)
                → GET /api/incidents + GET /api/admin/dashboard/summary
같은 I.id=1     → PATCH /api/incidents/1/resolve (Body 없음)
                → GET /api/incidents + GET /api/admin/dashboard/summary
I.riskHistoryId=2 ↔ C17 H.id=2, traceId도 보존
INC-00001 / RH-00002는 화면 표시이며 API ID가 아님
```

| 검수 대상 | 코드·실제 확인 |
|---|---|
| 경로 | `operator.html#/overview`, `#/marketing`, `#/security`, `#/audit`. `OperatorApp`이 실제 React 컴포넌트를 렌더링 |
| 공통 API 계층 | [operatorApi.js](../frontend/legal-procedure-viewer/src/operator/operatorApi.js)가9개 API·15초 제한·취소·HTTP 오류·텍스트/JSON 구분을 관리 |
| 조건·필터 | C17은 서버Query 하나만 전달. C18 상태·심각도는 클라이언트 필터. Incident 상세·연관 위험 선택은 이미 읽은 DTO를 사용 |
| 쓰기·응답 대조 | [auditModel.js](../frontend/legal-procedure-viewer/src/operator/auditModel.js)의 `changeIncidentAndReload`가 응답 id·목표 status 대조 후 C18/C12 재조회. 실제 서버 확인·해결 흐름 통과 |
| 중복·불확실한 쓰기 | [AuditPage.jsx](../frontend/legal-procedure-viewer/src/operator/AuditPage.jsx)가 mutation ref와 pending으로 재클릭 차단. 응답 유실 시 PATCH 자동 재전송 없이 읽기로 확인하는 자동 검사 통과 |
| 자격 증명 분리 | C16만 Basic 헤더. 일반8개 API는 Basic·JWT 헤더를 보내지 않음. Basic을 다른 업무 API로 전파하지 않는18개 자동 검사 중 관련 항목 통과 |
| 계정 보관 | C16 계정은 SecurityPage 메모리에 보관, 이동 시 페이지 해제. 입력Dialog는 별도 상태. URL·스토리지에 자격 증명을 저장하는 코드 없음 |
| 원문 표시 | RiskDetail 등 JSX는 userInput을 렌더링하지 않음. **서버 응답의 원문 노출까지 해결된 것은 아님** |
| 응답 검증 범위 | 공통 요청은 JSON 객체/배열의 기본 형태를 검사하고 PATCH는 id·상태를 추가 검사. 모든 중첩 DTO 필드·부모 ID를 엄격하게 검증하는 공통 스키마가 구현된 것은 아님. 이번 정상 서버 필드는 검수 스크립트가 별도로 대조 |
| CORS | API PATCH·Actuator GET의 Authorization 프리플라이트 성공. 현재 서버는 Origin 와일드카드. 이것은 인가 검증을 대신하지 않음 |

## 4. 미해결 발견 사항

### P1 · 운영자 업무 API8개 공개 접근

익명으로 C12/C13/C14/C15/C17/C18 조회와 C19/C20 변경이 성공했다. USER JWT로도 C12가200이었다. C16만 익명401·USER Basic403·ADMIN JWT401·ADMIN Basic200을 구분했다. 메뉴의 ‘관리자’ 표시나 `/api/admin` 경로는 서버 권한 검사가 아니다.

근거: [SecurityConfig.java:78](/C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:78), [실제 호출 기록](../output/operator-09-12-api-review/verification.json).

운영 배포 전8개 업무 API의 관리자 인가를 적용하고 프런트에도 해당 인증 세션·헤더 전달을 연결해야 한다. 현재 프런트 요청을 그대로 두고 서버만 인증 필수로 바꾸면 조회가401이 되므로 함께 정리해야 한다. 이번 검수에서 정책을 임의 변경하지 않았다.

### P1 · C17 원문 userInput 반환

가상 입력 `SYNTHETIC_PRIVATE_MARKER_010-1234-5678`을 넣은 뒤 익명 C17 HIGH 조회에서 같은 원문 문자열이 반환되는 것을 확인했다. 실제 개인정보를 사용하지 않았다. 화면에 안 보이더라도 네트워크 응답으로 읽을 수 있다.

근거: [AgentRiskHistoryResponse.java:26](/C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskHistoryResponse.java:26). 운영자 DTO의 원문 제외/마스킹·최소 필드 정책과 인가를 함께 보강해야 한다.

### P2 · 해결된 Incident가 확인됨으로 역행

React는 RESOLVED의 ack/resolve 버튼을 막는다. 그러나 **익명 `PATCH /api/incidents/1/ack`가 이미 RESOLVED인 레코드를200·ACKNOWLEDGED로 되돌렸다.** 기존 문서의 서버 보강 필요 사항을 실제로 재현한 결과다. 가상 레코드는 확인 후 RESOLVED로 복원했다.

근거: [RiskIncident.java:101](/C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/entity/RiskIncident.java:101). 서버의 허용 상태 전이·잘못된 전이409·동시성 제어가 필요하다. 이번에는 동시 요청 레이스를 별도로 부하 시험하지 않았다.

### P2 · 없는 Incident ID가404 대신500

실제 `PATCH /api/incidents/999999/ack`는500이었다. 형식이 잘못된 `not-an-id`는400이었다. 없는 레코드의 `IllegalArgumentException`이404로 매핑되지 않아 클라이언트가 ‘대상 없음’과 서버 장애를 구분할 수 없다.

근거: [RiskIncidentService.java:81](/C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:81). 없는 ID는404로 응답하고 기존 레코드는 변경하지 않는 계약이 필요하다.

## 5. 실행 증거·범위

| 항목 | 이번 결과 |
|---|---|
| 운영자 자동 검사 | `npm run test:operator` **18개 통과**, 실패0. ID·Body·Basic 분리·401/403·필터·지표·응답 유실 재조회 포함 |
| 실제 서버 API 검수 | `node scripts/operator-0912-live-review.mjs` **10개 검증 그룹 / 52회 요청**. 모든 단언 완료. 결함 재현 단언도 포함 |
| 프로덕션 빌드 | `npm run build` 성공,93모듈. 별도operator 진입점 생성 |
| 지원 스크립트 | `node --check scripts/operator-0912-live-review.mjs` 통과. 고정 루프백 격리 서버·가상 데이터만 사용 |
| 변경 | 검수 스크립트·문서·증거 파일 추가. 제품 API·인가·상태 전이는 수정하지 않음 |
| 브라우저 | 이번 요청은 API·필드·ID·인증 검수다. 새 브라우저 조작·픽셀/모바일/전체 오류 주입 검수는 실행하지 않음. 기존 브라우저 근거는 아래 이전 문서에 보존 |

초기 자동 검사에서는 샌드박스의 로컬 통신 제한으로7개가 실패해, 허용된 실행 환경에서 재실행해18개 모두 통과했다. 실제 서버 검수의 첫 준비 데이터는 SUMMARY_VIEWED의필수 문의ID를 누락해400이었고, 세션·문의ID를 맞춰 격리DB를 재시작한 뒤 전체10개를 통과했다. 두 경우는 검수 환경·준비 데이터 수정이며 제품 코드 수정은 아니다.

[실제 검증 JSON](../output/operator-09-12-api-review/verification.json)에52회 호출의메서드·경로·상태·인증방식·필드명과 응답 스냅샷을 기록했다. 비밀번호·Basic 값·JWT 값은 저장하지 않았다. 원문 재현 증거는 가상 입력이다. 검수 전용 서버는 종료했으며 기존 개발 서버·DB는 변경하지 않았다.

이전 디자인·브라우저·구현 기록: [OPERATOR_09_12_API_REACT_MAPPING.md](OPERATOR_09_12_API_REACT_MAPPING.md). 과거6개/10개/18개 등 수치는 해당 회차 기록이며 이번18개·실제10개그룹과 합산하지 않는다. S01~S05는 운영자 화면 진입 시 자동 실행되는 API가 아니고, P01/P04/P05/P06도9개 매핑에 중복 포함하지 않았다. P06은 이번 C13 집계 확인을 위한 준비 호출이다.
