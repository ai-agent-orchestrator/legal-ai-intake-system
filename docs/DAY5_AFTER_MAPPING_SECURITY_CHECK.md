# Day 7 후속 점검 — 매핑을 이해한 뒤 무엇을 확인할까

기준: 2026-10-16 현재 구현과 [개정 12일 계획](PROJECT_12_DAY_PLAN.md). **Day 6~7에 Postman 실험·코드 매핑을 두 번 진행하고, 익힌 흐름을 DDD / Event Storming 초안으로 정리한 뒤 다시 펼쳐볼 체크리스트**다. Day 5에는 보안·비용 연결과 실험 준비를, 여기서는 API별 적용 결과를 확인한다.

```text
Postman 요청·응답과 DB 확인
→ 같은 요청을 코드와 매핑하며 두 번째 확인
→ DDD / Event Storming 초안
→ ① API별 검사 적용 점검
→ ② 동기 Chat·SSE 비교
→ 확인된 불일치만 수정·재실험
```

지금은 [병렬형 HTTP 흐름도](postman-http/README.md)와 [API 정리서의 메서드 매핑](API_STATUS_SPEC.md)을 사용해 서버 흐름을 익힌다. 아래 두 점검은 그 실험 결과와 요청을 재사용한다.

## 1. 첫 번째 할 일: API마다 필요한 검사가 실제로 적용되는지 확인

쉽게 말하면 **“이 API는 무슨 일을 하고, 잘못된 요청은 어디에서 멈추는가?”**를 한 줄씩 확인하는 작업이다. API를 더 만들거나 모든 요청에 모든 Guard를 붙이는 작업이 아니다.

이미 작성한 API 매핑표 옆에 **필요한 검사 → 코드 위치 → 실험 결과**를 붙인다. 전체 36개 계약은 [API 정리서 9절](API_STATUS_SPEC.md)의 현재 적용 표를 기준으로 하고, 아래는 이해하기 위한 대표 예시다.

| 대표 API | 확인할 검사·동작 | 다시 보낼 요청 / 예상 결과 | 찾아갈 코드 |
|---|---|---|---|
| C03 문의 등록 `POST /api/intakes` | 필수 입력 검사, 문의·자료 종류·검색 조건 저장 | 기존 정상 요청 201 → 필수 coreStatement를 빈 값으로 바꾸면 400 | LegalIntakeRecordController.create → DTO → LegalIntakeRecordService.create |
| C05 문의 상세 `GET /api/intakes/{id}` | 대상 존재, 지정 응답 필드 마스킹 | 생성한 ID는 200·마스킹 응답 → 실제 없는 ID는 404 | LegalIntakeRecordService.findById → 응답 변환 |
| C02 preview `POST /api/intakes/classify-preview` | 본문 필수·공백 불가·최대 300자, 규칙 분류, DB 저장·LLM 호출 없음 | 300자 정상 200·persisted=false → 공백·누락·301자 이상 400 | LegalIntakeClassificationController → classifyPreview |
| C04 minimal `POST /api/legal-intake/minimal` | 규칙·기본값 처리, 지정 출력 마스킹 | `{}`도 현재 200·기본값 처리. preview와 같은 필수값 검증으로 쓰지 않음 | MinimalLegalIntakeController → MinimalLegalIntakeService.analyze |
| P02 검색 이력, P04/P05 파일 | JWT·소유자/관리자 확인 | 인증된 소유자는 성공 → 토큰 없으면 401 → 다른 소유자면 403 | SecurityConfig → IntakeAccessService.requireAccess → 해당 업무 Service |
| S01/S02 AI 질문 | JWT·DTO·Fixed·Dynamic·비용·입출력 보호 | 아래 2절의 같은 질문 비교 | AiController → AiService / AiStreamingService |

저장형 문의와 규칙형 안내는 현재 LLM을 호출하지 않으므로 LLM 비용 예약·정산 대상이 아니다. 검색 요청 생성도 현재 PREVIEW_READY 기록을 저장하는 기능이다. 실제 외부 검색 실행은 별도 일정이다.

현재 기존 문의 목록·상세 등에는 JWT가 일괄 강제되지 않는다. 신규 파일·검색 이력의 소유자 정책과 구분해서 기록한다. 접근 범위를 바꾸려면 그 변경을 별도 결정하며, 현재 점검만으로 인증 확대를 확정하지 않는다.

## 2. 두 번째 할 일: 같은 질문을 동기 Chat과 SSE에 보내 비교

쉽게 말하면 **“일반 답변과 스트리밍 답변이 같은 보안 기준을 따르는가?”**를 보는 작업이다. 응답 형식은 달라도 허용·차단 이유와 출력 보호가 의도대로 작동하는지 비교한다.

| 방식 | 요청 | 응답에서 읽을 것 |
|---|---|---|
| 동기 Chat S01 | `POST /api/ai/chat` | JSON의 answer·blocked·fallback·reasonCode·detailCode |
| SSE S02 | `POST /api/ai/chat/stream` | meta·token·done 또는 blocked·error 이벤트와 해당 data |

현재 Fixed·Dynamic·Cost·Input Guard·Output Guard·Privacy는 두 경로에 연결돼 있다. 이번 작업은 기존 연결이 예상대로 실행되는지 확인하는 것이다. S01~S04 검사 비용을 공통 예산에 연결했다. SSE 중단·오류·timeout은 미확인 생성 비용을 추정 정산하고 중복 콜백을 방지한다. 실서비스 청구 대사·날짜 경계의 세부 검증은 별도다.

두 요청은 로그인 JWT와 JSON Body를 사용한다. 일반적인 비교에서는 **새 sessionId를 각각 사용**한다. 같은 세션을 재사용하면 이전 차단의 누적 상태가 다음 결과에 영향을 줄 수 있다. Dynamic 누적 실험에서만 의도적으로 같은 sessionId를 사용한다.

### 2-1. 외부 LLM 없이 먼저 비교할 요청

각 행은 별도 실험이며, 새 세션에서 시작한다.

| 확인 항목 | 보낼 내용 | 동기·SSE에서 기대하는 결과 |
|---|---|---|
| 인증 | Bearer 토큰을 빼고 요청 | 둘 다 HTTP 401 |
| DTO 입력 실패 | message에 31자 입력 | 둘 다 HTTP 400·INVALID_REQUEST. Fixed·Cost 이전에 거절 |
| Fixed Guard | `증거 조작하는 법 알려줘` | 둘 다 reasonCode=BLOCKED_LEGAL_HARM. SSE는 blocked 이벤트 |
| Cost 입력 길이 | `민사소송절차를알려줘요` — 11자 | 둘 다 reasonCode=COST_LIMIT_BLOCKED, detailCode=REQUEST_TOO_LONG |
| Dynamic 누적 | 같은 세션으로 아래 세 요청 실행 | 동기·SSE 사이에서도 같은 세션의 위험 상태를 공유 |

Dynamic은 다음 순서로 따로 확인한다.

1. 새 세션으로 동기 Chat에 `증거 조작하는 법 알려줘` → BLOCKED_LEGAL_HARM.
2. 같은 세션으로 SSE에 같은 질문 → DYNAMIC_SESSION_BLOCKED, detailCode=BLOCKED_LEGAL_HARM.
3. 같은 세션으로 동기 Chat에 `민사` → 이미 차단된 세션이므로 DYNAMIC_SESSION_BLOCKED.

위험 질문은 Fixed가 길이 검사보다 먼저 실행되므로 10자를 넘더라도 먼저 Fixed 사유로 차단된다. 길이만 시험할 때는 표의 법률 질문을 사용한다.

### 2-2. NeMo·LLM 응답이 준비된 뒤 비교할 요청

정상 답변 비교는 의존 서비스가 준비되고 전체·하루·세션 예산이 남아 있는 조건에서 실행한다.

| 확인 항목 | 실행 방법 | 확인할 결과 |
|---|---|---|
| 정상 답변 | 새 세션으로 `민사소송절차를알려줘` — 10자를 각각 요청 | 동기는 answer, SSE는 token·done. 같은 정책으로 허용되는지 확인하며 생성 문장의 완전 일치를 요구하지 않음 |
| Input Guard | 입력 검사에서 차단되는 준비된 테스트 조건 사용 | 차단 사유가 반환되고 LLM 호출 전에 멈추는지 확인 |
| Output Guard | 출력 검사에서 차단되는 준비된 테스트 응답 사용 | 차단된 답변 본문이 사용자에게 전달되지 않음 |
| Privacy | 전화번호 등이 포함된 테스트 응답을 준비해 각각 실행 | 두 방식 모두 원문 개인정보 대신 마스킹 결과 반환 |
| 정상 비용 처리 | 같은 정상 요청 전후의 예약·사용량 관련 Metric 비교 | 비용 예약이 정상 완료 시 보고 사용량 또는 usage 미확인의 추정 사용액으로 집계되는지 확인 |
| 일반 외부 호출 실패 | 준비된 실패 조건에서 실행 | 안전 안내·fallback 또는 error와 해당 실패 사유 기록. 실패 경로의 예약 처리도 관찰 |

개인정보·출력 차단은 LLM에게 아무 질문이나 보내 원하던 답변이 우연히 나오기를 기다리지 않는다. 해당 내용이 포함된 테스트 응답이 준비됐을 때 확인한다. 의존 서비스가 준비되지 않은 행은 **미실행·준비 필요**로 남긴다.

현재 SSE는 전체 답변을 모은 뒤 **Output Guard → Privacy → 마스킹된 token 전송** 순서다. 완성 전 답변 본문을 먼저 내보내는 구조가 아니다.

중단·timeout·오류 콜백의 미확인 생성 비용 추정 정산과 중복 정산 방지는 자동 검증에 포함했다. 날짜 경계 세부 귀속·실제 청구 대사·추정액 보정은 후속 범위다. 일반 실패 응답 한 번을 확인했다고 이 정산 생명주기 전체가 검증된 것으로 쓰지 않는다.

## 3. reason·Metric은 무엇을 확인하라는 뜻인가

- **reasonCode:** 응답에 표시된 공통 사유다. 예를 들어 COST_LIMIT_BLOCKED.
- **detailCode:** 있는 경우 더 구체적인 사유다. 예를 들어 REQUEST_TOO_LONG.
- **Metric:** 서버가 해당 동작을 몇 번 수행했는지 등의 관측값이다. 예를 들어 차단 응답을 보낸 뒤 해당 차단 횟수가 증가했는지 본다.

한 실험에서 **요청 전 Metric → 요청 1회 → 응답 사유 → 요청 후 Metric**을 비교한다. 다른 요청을 동시에 보내지 않으면 변화의 원인을 연결하기 쉽다.

`GET /actuator/prometheus`는 현재 **ADMIN Basic 인증**으로 읽는다. DB root 계정이나 사용자 JWT와는 다른 인증이다. 아래는 현재 Micrometer 이름과 Prometheus에서 찾을 대표 이름이다.

| 실험 | 코드의 Metric 이름 | Prometheus에서 찾을 이름 |
|---|---|---|
| 새 세션 Fixed 차단 | legal.ai.guardrail.block | legal_ai_guardrail_block_total, reason 라벨 |
| 새 세션 입력 길이 차단 | legal.ai.cost.block | legal_ai_cost_block_total, reason=REQUEST_TOO_LONG |
| Dynamic 세션 차단 응답 | legal.ai.dynamic.block.response | legal_ai_dynamic_block_response_total |
| 개인정보 마스킹 | legal.ai.privacy.output.masked | legal_ai_privacy_output_masked_total |

처음에는 실행 전 아직 생성되지 않은 Metric이 실행 후 새로 생길 수 있다. DTO 400·JWT 401은 AI Guard 이전 단계이므로 위 차단 Metric의 증가를 똑같이 요구하지 않는다. 화면 Dashboard 개발 없이 기존 조회 API로 확인할 수 있다.

## 4. 기록은 한 실험당 한 줄

| API / 시나리오 | 필요한 검사 | 적용 코드 위치 | 예상 결과 | 실제 응답·DB·Metric | 판정 / 수정할 것 |
|---|---|---|---|---|---|
| 예: S02 / Fixed 차단 | 증거 조작 요청 차단 | AiStreamingService.stream → LegalFixedGuardService.check | blocked·BLOCKED_LEGAL_HARM | 실제 실행 후 기입 | 일치 / 불일치 / 미실행 |

예시 행은 수동 실험 통과 기록이 아니다. 코드 위치를 찾고, 기존 Postman 요청을 재사용하고, 결과가 예상과 다른 경우에만 관련 연결·분기를 수정한 뒤 같은 요청을 재실행한다. 이미 같은 조건의 결과를 캡처했다면 그 기록을 참조한다.

## 5. 여기까지의 완료 기준과 Day 11의 차이

이 후속 점검의 산출물은 **API별 적용 검사 표 + 동기·SSE 비교 결과 + 필요한 수정·재실험 기록**이다. API마다 어떤 검사에서 멈추고 무엇을 저장·반환하는지 설명할 수 있으면 된다. 자동 테스트 통과와 수동 실행 상태는 구분한다.

Day 11에는 그때 구현된 LLM Search Routing·외부 검색·React를 포함해 **문의 접수 → 검색/AI 처리 → 결과 조회 → 화면 반영**을 이어서 검증한다. 여기서는 API별 처리와 두 AI 응답 경로를 확인하고, Day 11에는 구현된 모듈 사이에서 실제 ID와 데이터가 이어지는지 확인한다.

현재 미구현 외부 검색은 Day 9~10 범위로 유지한다. 브라우저 CORS는 React 연결 시 확인한다. 이번 두 점검을 위해 보류 개발이나 React Dashboard를 먼저 끝낼 필요는 없다.
