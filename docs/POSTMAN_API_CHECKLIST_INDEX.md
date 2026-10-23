# API Mapping First — Minimal Runtime Checks

기준: **2026-10-17, current source + existing test reports**.

**Postman을 안 해도 코드 매핑·자동 테스트로 확인할 수 있는 부분이 많다.** 지금 목표는 Top → Middle → Bottom 기능 이해다. 모든 API·145개 분기를 실행하지 않고, **매핑 후 기본 4요청**, 실제 AI 연결을 확인할 때만 **4요청 추가**로 시작한다. Postman 대신 curl·자동 HTTP 테스트를 사용해도 된다.

이 최소 범위는 현재 학습·로컬 연동 확인 기준이다. 전체 제품의 검증 완료 기준과 구분한다. 이번 문서 수정에서 새 HTTP 요청이나 테스트를 실행한 것은 아니다.

**제출 우선순위:** 시연영상이 필수 산출물이며 Postman은 최소 연결 확인·오류 재현용이다. 아래 4+4는 참고 경로이지 별도 필수 실행 할당량이 아니다. 이미 확보한 자동·실제 환경 검증 및 시연 근거와 겹치는 요청은 반복하지 않는다. 매핑과 핵심 시연 흐름에 시간을 우선 배정하고, 촬영을 막는 연결 문제·변경 기능의 미검증 조건만 추가 실험한다. 촬영 범위는 [시연 흐름과 최소 실행 근거](DEMO_FLOW_AND_EVIDENCE.md)를 따른다.

## 1. First: function mapping

`API ID → HTTP route → Controller.method → Service.method / condition → Repository or Client → DTO / state / Metric`

각 기능에서 다음을 설명하고, 모순이 있으면 해당 부분만 추적한다.

| Mapping | Confirm in code |
|---|---|
| Entry / decision | Request DTO, 인증·인가 위치, Service 조건·reasonCode·상태 전이 |
| Data | 저장·조회 테이블, ID 연결, 트랜잭션. 요청 기록과 실제 외부 실행 구분 |
| Output / cost | 응답 필드·마스킹·SSE 순서, 외부 호출 유무, 예약·보고/추정 정산·모의 토큰 |
| Evidence | 해당 조건의 테스트 메서드. 테스트가 없으면 `Coverage gap` 기록 |

진행 상태는 **Mapped / Automated verified / Runtime verified / Coverage gap**으로 나눈다. 매핑만 끝낸 항목을 실행 검증 완료로 표시하지 않는다.

## 2. Minimum execution: 4 + conditional 4 requests

### A. Application / DB smoke — 4 requests

실제 사용할 서버·DB profile에서 한 번 확인한다. **매핑 중에는 미뤄도 된다.**

| Order | API / request | Only check this |
|---|---|---|
| 1 | **C01** `POST /api/auth/login` | 실제 설정의 계정으로 로그인, accessToken 확보 |
| 2 | **P01** `GET /api/me` with JWT | 실제 서버 필터 통과, username/role 일치 |
| 3 | **C03** `POST /api/intakes` with JWT | 201, intakeId·검색 조건 ID, 지정 필드 마스킹 |
| 4 | **C05** `GET /api/intakes/{id}` | 같은 문의·검색 조건 재조회. 실제 DB의 저장 행·owner와 대조 |

C03 body는 [기존 collection](BACKEND_DRAFT_TODAY.postman_collection.json)에서 가져와 고유 sessionId와 테스트용 개인정보를 사용한다. 기본 DB는 H2 memory다. MySQL 검증이 목적이면 실제 MySQL profile에서 실행한다. **C03/C05는 현재 공개 경로이므로 이 성공이 owner 인가 검증을 뜻하지는 않는다.**

### B. Actual AI wiring — add 4 requests only when checking external integration

A의 JWT를 재사용한다. NeMo/LLM 연결 설정·키·예산이 준비됐을 때 실행하며 **실제 외부 비용이 발생할 수 있다**. 준비가 없으면 `Deferred: external setup`으로 적고 매핑을 계속한다.

| Order | API / request | Only check this |
|---|---|---|
| 5 | **C16** `GET /actuator/prometheus`, ADMIN Basic | 호출 전 비용 Metric snapshot |
| 6 | **S01** `POST /api/ai/chat`, JWT | 실제 Input → generation → Output 연결. 200뿐 아니라 blocked=false, fallback=false, usageAvailable/costSource |
| 7 | **S02** `POST /api/ai/chat/stream`, JWT | 실제 SSE meta/token/done 수신. 본문은 생성·출력 검사·마스킹 후 전송 |
| 8 | **C16** same endpoint | 격리된 두 호출 전후 단계별 비용·예약액 비교. usage 미보고면 ESTIMATED 여부 확인 |

S01/S02 예시: `{"message":"민사소송절차를알려줘","sessionId":"unique-per-request"}`. 현재 실험용 10문자 입력 한도에 맞춘 문구다. 각 호출은 새 sessionId를 사용한다. C16은 앱 JWT와 다른 ADMIN Basic 체인이다.

실제 모델이 PII·usage 누락·예산 소진을 우연히 재현할 때까지 반복하지 않는다. 그런 조건은 아래 고정 테스트로 확인한다. **A/B가 목적에 맞게 통과하면 전체 API 순회를 추가하지 않는다.** 실패는 해당 설정·분기부터 추적하며 fallback을 외부 연결 성공으로 기록하지 않는다.

## 3. Exclude from the default manual run

| Scope | Replacement |
|---|---|
| 번호 API 36개·대표 분기 145개 전체 캡처 | 필요한 API의 기능/분기를 찾는 reference로 사용 |
| DTO 경계값·Fixed/Dynamic 모든 reason·SSE PII 청크·usage/예산/중복 정산 조합 | 해당 자동 테스트·고정 adapter fixture. 코드 변경 후 영향 테스트 재실행 |
| C10 409·C11 마스킹·파일/소유자 인가의 반복 수동 확인 | 기존 통합 테스트와 변경 영향 범위 대조 |
| S03/S04 단독 검사 전수 반복 | 공통 Client·비용 흐름 + 기존 통합 테스트. standalone 계약을 바꾸면 영향 케이스 추가 |
| R01~R06·C02/C04·guide/status·Agent practice/risk-pattern 전체 실행 | 코드에서 규칙·고정 응답·모의 시나리오를 매핑. 변경·의문·미검증 조건만 확인 |
| Transaction / N+1 실습 | 별도 JPA 학습 때 선택 |
| NeMo 직접 2경로·P03·미구현 복구/분산/청구 대사 | 직접 호출은 adapter 장애 진단 때만. 미구현 목표는 설계 mapping |

제외는 **이번 기본 수동 실행에서 제외**한다는 뜻이다. 자동 테스트가 없는 기능이나 수정한 코드를 검증 완료로 간주하지 않는다.

## 4. Add only for the module being mapped or changed

| Trigger | Smallest useful check |
|---|---|
| File / ownership | **P04 → P05** 파일 bytes round trip. 변경·미검증 인가 조건은 각 영향 경로의 owner/non-owner 케이스도 자동 또는 HTTP로 확인 |
| Search-record data linkage | 필요 시 **C06 → C08 → C07**, P02 readback. PREVIEW_READY·snapshot 저장을 실제 외부 검색과 구분 |
| Status / memo / event / dashboard change | 변경 mutation → 관련 GET/집계 readback. 영향받는 409·중복 처리 등은 자동 회귀 테스트 우선 |
| Job / S05 / Incident 등 테스트 증거가 확인되지 않은 업무를 완료 처리 | 먼저 Service·상태 전이 매핑. 대표 mutation → 상태/결과 조회를 자동 또는 HTTP로 확인. Job은 jobId로 terminal까지 poll |
| Frontend / CORS / proxy | 실제 브라우저·해당 proxy 경유로 한 번 확인. Postman 성공만으로 CORS/화면 완료를 판단하지 않음 |

기능 이해 중에는 gap을 기록하고 넘어갈 수 있다. 해당 기능의 **구현·검증 완료를 선언하기 전에는 gap을 확인**한다. 대표 API 하나의 통과가 다른 API의 인가·상태 전이를 대신 검증하지는 않는다.

## 5. Existing tests replace repeated manual cases

| Source | Encoded checks |
|---|---|
| [AiSecurityIntegrationTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/AiSecurityIntegrationTest.java) | JWT/Basic·CORS, 길이/위험 누적, SSE 마스킹·출력 차단, S01~S04 비용·예산·중단, C16 |
| [AiCostUsageTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/cost/AiCostUsageTest.java) · [AiCostSessionTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/cost/AiCostSessionTest.java) · [GuardrailCostTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/cost/GuardrailCostTest.java) | reported/estimated/0, 세션 예산, 검사 실패 추정·중복 정산 |
| [GuardrailUsageClientTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/guardrail/GuardrailUsageClientTest.java) · [LlmUsageClientTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/llm/LlmUsageClientTest.java) | 로컬 HTTP fixture의 usage 계약·누락/0·stream parsing |
| [BackendDraftApiIntegrationTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/BackendDraftApiIntegrationTest.java) | C02 경계·비저장, C10 전이/409, C11 저장·마스킹, 검색 snapshot readback·일부 집계 |
| [AdditionalFiveApiIntegrationTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/AdditionalFiveApiIntegrationTest.java) | C01/P01, P02 권한, P04/P05 파일·인가·형식, P06 중복/동시성·집계 |

이번에 읽은 기존 XML **6개 합계는 82 tests, 실패·오류·skip 0**이다. 위 표의 앞 3행 테스트가 포함된 focused report이며, 뒤 2행은 소스만 확인했다. 새 실행 결과나 현재 소스 버전의 전체 통과 보장이 아니다. 코드 수정 후 관련 테스트를 다시 실행한다.

AI 통합 테스트는 NeMo/LLM mock, adapter 테스트는 로컬 HTTP fixture, 업무 통합 테스트는 MockMvc/JPA·H2 환경을 사용한다. 실제 provider·MySQL·배포 전송은 별도 환경 확인이 필요하므로 A/B를 둔다. **모든 기능이 자동 검증됐다고 가정하지 않는다.**

## 6. Record briefly; open catalogs only when needed

`API / feature → code chain → main condition → Mapped / Automated verified / Runtime verified / Coverage gap → evidence / refactor`

실행했다면 환경·결과를 한 줄 덧붙인다. 전체 응답·DB·Metric을 매번 캡처할 필요는 없다. 불일치·실패·변경 정책·외부 연결에 필요한 증거만 남긴다.

**최종 시연 범위:** 최소 4+4 요청은 현재 학습·연결 확인의 시작점이다. 제출 시연에는 **필수 흐름(React–Spring Boot·CORS / LLM·비동기 / 지연 통제·가상 스레드·관측)**과 **P03 구현 후 실제 LLM·검색 실행 및 Cost Control 예약·정산·차단**을 포함한다. P03은 현재 설계 상태이며, 구현 후 실제 실행 경로를 추가 검증한다. 상세 장면·근거는 [시연 흐름과 최소 실행 근거](DEMO_FLOW_AND_EVIDENCE.md)를 따른다.

- [Numbered API reference](POSTMAN_NUMBERED_API_CHECKLIST.md): 구현 36개 + 설계 P03. 전체 수동 실행 목록 아님.
- [Auxiliary API reference](POSTMAN_AUXILIARY_API_CHECKLIST.md): 보조 Controller 30개, 별도 Actuator/NeMo. 현재 모듈만 선택.
- [Route inventory](BACKEND_ROUTE_INVENTORY.md) · [Per-API Mermaid](postman-http/README.md) · [API contracts](BACKEND_DRAFT_STATUS_AND_API_SPEC.md)
- [Cost Control mapping](diagrams/03_COST_CONTROL_FLOW.md) · [Privacy mapping](diagrams/02B3_PRIVACY_FLOW.md)
