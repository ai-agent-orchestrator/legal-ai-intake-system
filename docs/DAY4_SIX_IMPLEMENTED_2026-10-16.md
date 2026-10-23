# Day 4 — 백엔드·보안 보완 6개 구현 결과

기준: 2026-10-16 로컬 코드. 기존 9개 체크리스트 중 **1·2·3·4·6·7번**을 구현했다.

후속 갱신: 이 파일은 최초 6개 구현 기록이다. 이후 5번 SSE Privacy·8번 usage 누락 처리를 추가 완료했고 총 71개 테스트가 통과했다. 현재 남은 항목은 사용자 요청으로 보류한 9번이다. 최신 상태는 [추가 구현 결과](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_PRIVACY_USAGE_2026-10-16.md)를 따른다.

| 번호 | 구현 항목 | 결과 |
|---|---|---|
| 1 | 공통 응답 계약 | 동기 JSON·SSE 이벤트에 `reasonCode`·`detailCode` 추가. 기존 `reason` 값은 호환성을 위해 유지 |
| 2 | Security / CORS | 공통 CORS source를 application·Actuator Security 체인에 연결. 허용 preflight는 인증 전에 처리하고 실제 요청 인증은 유지 |
| 3 | AI 세션 식별 통일 | Dynamic·Cost에서 `AiSessionIds.normalize()` 사용. 앞뒤 공백 제거, null·빈 문자열·공백은 공통 `anonymous` 그룹 |
| 4 | SSE 입력 보안 | Dynamic 사전 확인 → Fixed → 길이 제한 → NeMo Input → 비용 예약 순서 연결. 입력 차단 시 위험 누적·Metric·LLM 호출 방지 |
| 6 | Dynamic 동시성 | 세션별 잠금으로 점수·총횟수·동일 사유 횟수·차단 상태를 함께 갱신. 차단 전환 Metric은 처음 한 번만 기록 |
| 7 | 비용 실험 설정 | 전체 누적 $2·하루 $1·세션 $0.3 유지. 기본 출력 상한 512 → **256토큰**으로 조정 |

SSE 완료의 비동기 redispatch에서도 JWT 인증을 복원하도록 JWT 필터를 보완했다. CORS는 `/api/**`의 GET·POST·PATCH·OPTIONS와 `/actuator/**`의 GET·OPTIONS를 처리한다. 기존 개발용 origin 패턴을 유지하며, JWT와 Actuator ADMIN Basic의 실제 요청 인증은 계속 적용된다.

## 응답 계약

신규 소비자는 `reasonCode`로 공통 사유를, `detailCode`로 세부 사유를 확인한다. 기존 `reason`을 해석하던 소비자를 위해 그 값은 보존한다.

| 상황 | 기존 `reason` | `reasonCode` | `detailCode` |
|---|---|---|---|
| 입력 길이 초과 | COST_LIMIT_BLOCKED | COST_LIMIT_BLOCKED | REQUEST_TOO_LONG |
| 세션 예산 초과 | SESSION_BUDGET_EXCEEDED | COST_LIMIT_BLOCKED | SESSION_BUDGET_EXCEEDED |
| 전체 누적 예산 초과 | MONTHLY_BUDGET_EXCEEDED | COST_LIMIT_BLOCKED | MONTHLY_BUDGET_EXCEEDED |
| 하루 예산 초과 | DAILY_BUDGET_EXCEEDED | COST_LIMIT_BLOCKED | DAILY_BUDGET_EXCEEDED |
| Fixed 차단 | 해당 Fixed 코드 | 해당 Fixed 코드 | null |
| 반복으로 세션 차단 | DYNAMIC_SESSION_BLOCKED | DYNAMIC_SESSION_BLOCKED | 이번 차단을 일으킨 원인 코드 |
| 기존 차단 세션 사전 거절 | DYNAMIC_SESSION_BLOCKED | DYNAMIC_SESSION_BLOCKED | null |
| NeMo 입력 정책 차단 | INPUT_GUARDRAIL_BLOCKED | INPUT_GUARDRAIL_BLOCKED | NEMO_INPUT_GUARDRAIL |
| 정상 응답 | null | null | null |

정책 차단은 동기 응답·SSE 이벤트 모두 `blocked=true`, `fallback=true`다. 기술 fallback은 `blocked=false`, `fallback=true`다. SSE의 meta 이벤트는 기존 정보용 `reason=OUTPUT_RAIL_BUFFERED`를 유지하며 차단 사유 필드는 null이다.

## 실험용 기본값

| 설정 | 값 |
|---|---|
| 동기·SSE 입력 길이 검사 | 최대 10자: 추정 1페이지 × 10자 |
| 동기·SSE DTO | 최대 30자 |
| 전체 누적 / 하루 / 세션 예산 | $2 / $1 / $0.3 |
| 출력 토큰 상한 | 256 |
| 세션 누락 처리 | Dynamic·Cost 모두 공통 `anonymous`. 서로 다른 익명 사용자를 자동 분리하는 기능은 아님 |

기본 단가 설정에서 최대 출력 예약액은 $0.1536이며 입력 추정 비용이 추가된다. 현재 기본 시스템 프롬프트와 정상 짧은 요청으로 예약·응답 경로를 자동 검증했다. 단가가 외부 제공자의 최신 공식 가격과 일치한다고 검증한 결과는 아니다. 환경변수로 덮어쓴 경우 실제 실험 설정을 별도 기록한다.

10자까지는 다음 검사로 진행한다. 11~30자는 앞선 Dynamic·Fixed 검사를 통과하면 비용 정책에서 차단한다. 31자 이상은 DTO 400으로 먼저 거절한다. 길이 경계값은 새 세션으로, 반복 위험은 같은 세션으로 실험한다. 저장형 Intake·minimal DTO의 길이는 변경하지 않았다.

## 자동 검증

`gradlew.bat test --offline --no-daemon` 성공: **45개 / 실패 0 / 오류 0**.

- 기존 15개 테스트 유지.
- AI Security 통합 23개: 실제 Spring Security·MockMvc와 로컬 NeMo/LLM mock 사용. 동기·SSE 정상 경로, 10·11·30·31자 경계값, transport 간 Fixed·길이·NeMo 반복 누적, 세션 예산 코드, CORS preflight·JWT·ADMIN Basic 유지 확인.
- Dynamic 5개: 점수·반복·총횟수 기준, 세션 분리·정규화, 동시 이벤트 200개 누적과 최초 차단 Metric 1회 확인.
- Cost session 2개: 공백으로 세션 예산을 분리할 수 없는지, 누락 세션과 기존 reserve 메서드가 같은 예산을 사용하는지 확인.

실제 Postman 실행, 브라우저 실사용, 외부 NeMo·LLM 실서비스 응답을 검증한 결과와는 구분한다. Postman 집중 중간점검은 Day 5 일정이다.

## 남은 3개

| 기존 번호 | 남은 개발 |
|---|---|
| 5 | SSE Privacy 마스킹과 청크 경계 개인정보 처리 |
| 8 | usage 누락과 실제 0토큰 구분, 미확인 비용 처리 |
| 9 | SSE 출력 차단·연결 종료·timeout·오류·일자 변경 시 정산·해제 단일 처리 |

현재 SSE에는 NeMo Output Guard가 있지만 PrivacyOutputGuardService는 아직 연결하지 않았다. 비용 usage 누락 0 처리와 SSE 중단 정산의 기존 한계도 위 후속 작업으로 유지한다.
