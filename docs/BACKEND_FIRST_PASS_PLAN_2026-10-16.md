# Backend 구현 경과 · Day 6~7 Postman·DDD·매핑 계획

**최신 사용자 일정:** [12일 개발 계획](PROJECT_12_DAY_PLAN.md)을 기준으로 Day 5는 Security·Cost Control 연결·실험 준비, Day 6은 Postman 1차·코드 매핑·Refactoring, Day 7은 DDD·Event Storming·Policy Mapping으로 진행한다. Day 8은 React/API·Metric Dashboard, Day 9~10은 LLM Search Routing, Day 11~12는 통합 검증·산출물이다. 현재 버전은 36개 API와 확정 경로의 보안·비용 코드가 있으며, 계약·메서드 매핑·수동 실험 기록표는 [API_STATUS_SPEC.md](API_STATUS_SPEC.md)로 모았다.

**학습·점검 순서:** Day 6~7에 Postman 실험·코드 매핑을 두 번 진행하고 DDD / Event Storming 초안을 정리한 뒤, **API별 검사 적용 점검과 동기 Chat·SSE 비교**를 진행한다. 재사용할 요청·예상 결과·reason/Metric 확인 방법은 [매핑 이후 후속 점검표](DAY5_AFTER_MAPPING_SECURITY_CHECK.md)에 모았다. Day는 개정 계획의 단계로 사용하고 실제 실행일은 실험 기록에 따로 남긴다.

현재 자동 검증은 2026-10-17 기준 93개 통과이며 수동 Postman 결과는 별도 기록한다. C02 미리보기의 본문은 최대 300자로 조정했고 300자 허용·301자 거절을 확인했다. 아래 개발 시간표와 과거 테스트 수는 당시 구현 경과로 유지한다. 외부 검색 실행은 Day 9~10, SSE 정산 예외 보완은 별도 보류다.

**2026-10-16 구현 갱신:** 아래 체크리스트의 1·2·3·4·6·7번 **6개 구현·자동 검증 완료**. 전체 테스트 **45개 통과**. 5·8·9번은 후속 개발로 남는다. [구현 결과·응답 계약](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_SIX_IMPLEMENTED_2026-10-16.md)을 참조한다.

**추가 구현 갱신:** 5번 SSE Privacy와 8번 usage 누락 처리까지 완료했다. 현재 **8/9개 구현·자동 검증 완료, 71개 테스트 통과**. 9번 중단·오류·날짜 변경 정산 보완은 사용자 요청으로 보류한다. [추가 구현 결과](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_PRIVACY_USAGE_2026-10-16.md).

현재 진행 순서: **API별 요청·응답·DB 확인 → 같은 요청을 코드와 매핑 → 정상·실패·차단 이유 추적 → DDD·정책·코드 연결 → 필요한 수정·재실험**. 결과를 보면서 구조를 익힌다.

| 개정 계획의 단계 | 주목표 | 완료 기준 |
|---|---|---|
| Day 5 | 보안·비용 연결과 DB·실험 준비 | 확정 경로의 연결 코드·매핑 초안·예상 결과·DB 점검 기록 |
| Day 6 | Postman 1차·코드 매핑·Refactoring | 정상·실패·차단 결과, DB·ID 확인, Top–Middle 연결, 수정·재실험 기록 |
| Day 7 | DDD·Event Storming·Policy Mapping | 업무·정책·Read Model·코드·실험 연결, API별 검사 적용·동기/SSE 비교 |

추가 API 5개 구현 및 전체 92개 자동 테스트 통과. [추가 구현 계약·Postman](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_FIVE_APIS_2026-10-16.md). 기존 보완 체크리스트의 9번과 실제 외부 검색은 계속 보류한다.

## 시작 시 확인할 현재 상태

- 제출 대상은 중복 제거 36개 API: 기존 31개 + 사용자 요청으로 추가 구현한 5개.
- 분류 preview·상태 변경·상담 메모·마케팅 기본 집계의 백엔드 코드가 존재한다.
- 마지막 자동 테스트 실행은 총 93개 통과, 실패·오류 0개다. 외부 제공자는 mock·로컬 HTTP/SSE 대역으로 검증한다.
- 제공된 `BACKEND_DRAFT_TODAY.postman_collection.json`은 저장·조회 중심 요청이다. 이것만으로 AI 보안·비용·SSE 실험까지 완료했다고 표시하지 않는다.
- 매핑표의 React 함수 미구현은 백엔드 Controller 미구현과 구분한다.
- 현재 실험용 기본값: 동기·SSE 입력 제한 10자, DTO 최대 30자, 전체 누적 $2, 하루 $1, 세션 $0.3, 출력 최대 256토큰.
- 출력 상한을 256으로 조정해 현재 설정 단가 기준 출력 예약액은 $0.1536이다. 현재 기본 시스템 프롬프트·짧은 정상 요청의 비용 예약을 자동 검증했다. 외부 제공자의 최신 공식 가격을 확인한 결과는 아니다.

## Day 4 초기 개발 시간표 — 구현 경과 참고

| 순서 | 작업 | 시간 | 완료 기준 |
|---|---|---:|---|
| 1 | Screen - API Function - Controller Mapping 대조 | 1시간 | 화면 요구・함수 계약・API・Controller・DTO・Service를 연결하고 필요한 검사 범위를 확정 |
| 2 | 현재 범위의 업무 백엔드 잔여 구현 | 1시간 30분 | 누락·DTO·상태 처리·응답 계약 불일치 수정 |
| 3 | Security·Dynamic·Privacy 연결 마무리 | 2시간 | 인증·CORS, Fixed·Dynamic, 입출력 검사·지정 마스킹이 확정한 경로에 적용되도록 보완. 동기·SSE 차이를 반영 |
| 4 | Cost Control 정합성·예외 처리 보완 | 1시간 30분 | 실험 설정 충돌, 예약·정산·해제, usage 미확인, SSE 중단 경로를 현재 범위에 맞게 처리 |
| 5 | 자동 검증·문서 반영·Day 5 실험 준비 | 2시간 | 변경에 필요한 테스트 통과, 매핑·API·Mermaid 갱신, Postman 요청과 예상 결과 준비 |

## Day 4 개발 체크리스트 — 2026-10-16 갱신

기본 업무 API와 Fixed·Dynamic·Cost·Privacy 서비스는 존재한다. 아래 보완 9개 중 8개를 구현했고, 9번은 사용자 요청으로 보류했다.

| 번호 | 영역 | 현재 상태 | 남은 개발 / 완료 기준 |
|---|---|---|---|
| 1 | 공통 응답 계약 | **구현·자동 검증 완료** | 기존 reason 유지, reasonCode·detailCode를 동기·SSE에 추가 |
| 2 | Security / CORS | **구현·자동 검증 완료** | 공통 CORS source를 두 Security 체인에 연결. preflight 허용과 실제 JWT·ADMIN Basic 인증 유지 확인 |
| 3 | 세션 식별 | **구현·자동 검증 완료** | Dynamic·Cost에서 앞뒤 공백 제거, 누락 ID는 같은 anonymous 그룹 |
| 4 | SSE 입력 보안 | **구현·자동 검증 완료** | Dynamic preCheck·Fixed·10자 제한·NeMo 입력 차단을 위험 누적·Metric과 연결. 비동기 완료 시 JWT 복원 |
| 5 | SSE 개인정보 출력 | **구현·자동 검증 완료** | 전체 답변을 모아 Output Guard·Privacy 마스킹 후 token 전송. 5종 PII 청크 경계·수정 출력·차단 확인 |
| 6 | Dynamic 동시성 | **구현·자동 검증 완료** | 세션별 잠금으로 상태 일괄 갱신, 최초 차단만 Metric 기록. 동시 200개 이벤트 누적 확인 |
| 7 | 비용 실험 설정 | **구현·자동 검증 완료** | 사용자 지정 예산·입력 기준 유지, 출력 상한 256으로 조정. 기본 설정 정상 예약·응답 확인 |
| 8 | usage 누락 | **구현·자동 검증 완료** | usageAvailable로 확인 여부 구분. 미확인 정상 완료는 예약 추정액을 별도 집계하고 전체·일·세션 한도에 반영 |
| 9 | 정산·해제 예외 | **사용자 요청으로 보류**. SSE 출력 차단은 예약만 해제, 연결 종료·timeout·날짜 경계의 처리 미완료 | 중단·오류·일자 변경의 비용 귀속 및 중복 정산·해제 방지 |

**업무 백엔드:** 현재 매핑에 있는 접수·자료 종류·검색 조건·목록/상세·검색 요청/결과 기록·상태·메모·기본 집계의 Controller는 존재한다. 매핑 대조에서 요청 필드·반환 필드·실패 계약의 누락이 확인되면 수정한다. 아직 확인되지 않은 새 업무 API를 확정 개발 항목으로 추가하지 않는다.

**자동 검증 상태:** 공통 입력 차단·세션·CORS·인증에 더해 개인정보 청크 경계와 usage 확인/미확인·0의 구분, 추정 집계의 예산 반영까지 71개 테스트로 확인했다. 중단·날짜 변경·중복 정산·해제 보완은 보류한 9번 개발과 함께 검증한다.

**사용자 추가 요청 반영:** 내 정보·검색 이력·파일 업로드/다운로드·마케팅 행동 수집 API 5개 구현 완료. 실제 외부 검색은 Day 9~10 모듈에서 진행한다. 비용 장부 영속화·다중 서버 공유·달력 월 초기화는 별도 후속 항목이다. 현재 전체 누적 예산 $2를 달력 월 자동 초기화가 구현된 것으로 설명하지 않는다.

## 매핑 대조 기준

기존 문서 `SCREEN_API_CONTROLLER_MAPPING.md`를 기준으로 다음 항목을 보완한다.

| 항목 | 기록할 내용 |
|---|---|
| 화면 / 함수 | 화면의 행동, React API 함수 이름, 필요한 입력·출력 |
| API / Controller | HTTP Method·경로, 실제 Controller·메서드 |
| 처리 / 저장 | Service, Entity·Repository, 저장 여부·트랜잭션 |
| 적용 검사 | 인증, DTO Validation, 존재 확인, 상태 전이, Fixed·Dynamic·Cost·입출력 Guard 중 실제 적용 항목 |
| 개인정보 | 지정 응답 필드 마스킹 위치, 원문 저장 여부 |
| 실패 계약 | HTTP 상태, 응답 필드 `code`·`reason`, 내부 `reasonCode`·`detailCode` 구분 |
| 개발 상태 | 개발중 / 설계중 유지. React 상태 별도 표시 |
| 검증 상태 | 자동 검증 / Postman 통과 / 실패 / 미실행·의존 서비스 미준비를 별도 기록 |

API마다 필요한 검사를 정한다. 저장형 Intake·preview/minimal·동기 Chat·SSE가 동일한 검사 경로라고 가정하지 않는다. 인증은 JWT와 Actuator ADMIN Basic을 구분하고, 화면 연결 대상의 CORS·preflight 조건도 확인한다. Postman 성공만으로 브라우저 CORS 성공을 판정하지 않는다.

## 잔여 작업 판정

- 먼저 매핑 대상의 실제 누락과 계약 불일치를 확인하고, 현재 범위에 필요한 항목부터 수정한다.
- 현재 동기·SSE의 Fixed·Dynamic·입력 길이·Privacy 연결은 구현돼 있다. 두 차례 매핑 후 [후속 점검표](DAY5_AFTER_MAPPING_SECURITY_CHECK.md)로 적용 결과를 비교하고, 확인된 계약·연결 불일치를 수정한다. 보류한 SSE 정산 예외는 별도 범위로 유지한다.
- 새 기능 확장은 별도 목록으로 유지한다. Day 9~10의 LLM Search Routing 모듈과 이후 기능은 해당 일정에서 진행한다.
- 월 한도 초기화·비용 영속화 등 후속 항목까지 구현한 것으로 표시하지 않는다. 필수로 채택한 항목이 남으면 1차 완료 상태에도 미완료로 명시한다.

## Day 6 — Postman 1차 집중 실행 묶음

| 묶음 | 대표 실험 | 확인할 결과 |
|---|---|---|
| 인증·Reference | 로그인, 절차·단계·양식 조회, 인증 필요 API 접근 | JWT 발급·전달, 정상 조회, 인증 실패 응답 |
| 비저장 안내 | classify-preview, minimal | 규칙 결과, 지정 마스킹, DB 무저장 |
| 문의 저장·조회 | 문의 생성 → 목록·상세 | intakeId, 자료 종류, 검색 조건 동시 저장, 응답 DTO |
| 상담 검토 | 상태 변경·메모 저장·재조회 | 저장 결과, 메모 마스킹, 없는 대상·입력 오류·금지 전이 응답 |
| 검색 기록 | 검색 요청 생성 → 결과 스냅샷 저장 → 조회 | intakeId → searchRequestId, 결과 재조회. 실제 외부 검색 실행과 구분 |
| 운영 읽기 | 마케팅 집계, Agent 요약·이력·이벤트·Incident, Metric | 각 읽기 모델의 데이터 출처와 미수집 상태 구분 |
| AI 보안·비용 | Fixed 대표 사유, Dynamic 반복, 길이·예산 차단, 정상·실패 경로 | 응답 reason, 세션 상태, LLM 호출 여부, 예약·정산·해제 Metric |
| 출력·SSE | 개인정보 패턴, 출력 검사, SSE 정상·일반 실패 응답 | [매핑 이후 후속 점검표](DAY5_AFTER_MAPPING_SECURITY_CHECK.md)에서 비교. 중단·timeout·날짜 경계 정산 보완은 보류, 외부 의존성 미준비 시 미실행 표시 |

입력 길이 경계값은 10·11·30·31자다. 길이 실험마다 새 sessionId를 사용하고 Dynamic 반복 실험만 같은 sessionId를 사용한다. Fixed Guard 예문도 DTO 30자 이내로 준비한다. 예산별 차단 실험은 다른 한도가 먼저 차단하지 않도록 실험 설정을 분리하고, 실행에 쓴 값을 기록한다.

응답에서는 HTTP 상태와 업무 결과를 함께 본다. 예를 들어 AI 정책 차단은 HTTP 200과 `blocked=true`, `fallback=true` 조합으로 표현될 수 있다. 동기·SSE에는 공통 사유 `reasonCode`와 세부 사유 `detailCode`를 추가했고 기존 `reason`은 호환용으로 유지한다.

기본 H2 데이터와 Dynamic·비용 메모리 상태는 서버 수명에 영향을 받는다. [MySQL 프로필](MYSQL_CONNECTION_CHECK.md)의 root·전용 DB는 연결·재시작 후 기본 데이터 유지를 확인했다. 사용 DB·프로필과 재시작 여부를 기록한다. 외부 NeMo·LLM 응답을 준비하지 못한 시나리오는 fallback 확인 또는 미실행으로 기록하며 정상 업무 성공으로 합산하지 않는다.

## Day 6~7 — 백엔드 학습·DDD·매핑

각 대표 요청을 흐름도와 실제 코드에서 추적한다.

1. **문의 등록:** JSON → Controller·DTO Validation → Service → Entity·Repository → JPA·DB → 마스킹 Response DTO.
2. **AI Chat:** DTO → Dynamic 사전 확인 → Fixed → 길이 검사 → Input Guard → 비용 예약 → LLM → 정산 → Output Guard → Privacy → 응답. 중간 차단과 Metric도 연결한다.
3. **운영 집계:** 조회 요청 → 집계 Service·Repository → Read Model → 응답. 마케팅 기본 집계와 Agent 보안 요약의 차이를 설명한다.

Top은 전체 경로, Middle은 클래스·메서드의 책임, Bottom은 검증 조건·저장 필드·실패 분기에서 하나씩 골라 설명한다.

## 백엔드 구현 단계 산출물 — 구현 경과 참고

- 갱신된 Screen / API Function / Controller / 검사 매핑.
- 현재 구현을 반영한 API 정리서와 Mermaid.
- 자동 테스트 결과와 현재 범위의 구현·미완료·후속 작업 목록.
- Day 6 Postman 요청 묶음, 실험 설정, 예상 상태·reason, 결과 기록표.

백엔드 구현 단계의 목표는 **현재 범위의 백엔드·보안·Dynamic Guard 코드 구현과 자동 검증을 마치고, 집중 수동 검증을 시작할 수 있는 상태**다.

## Day 6~7 종료 시 남길 산출물

**실제 학습 운영:** 실험·매핑에 3일을 배정하고, 기존 전체·모듈별 flowchart와 API별 병렬형 흐름도를 보며 같은 경로를 두 번 확인한다. 발견한 오류의 수정·재실험도 이 기간에 포함한다. 흐름도는 계속 사용하는 학습 자료이자 갱신 대상이며, 실험마다 화면을 전부 캡처하는 것은 필수 산출물로 두지 않는다.

- Postman 1차 실행 기록: 실제 상태·reason, DB·Metric 근거, 수정·재실행 결과.
- 실험 결과로 보완한 API 정리서·매핑·Mermaid.
- 현재 범위의 통과·실패·미검증·후속 작업 목록.
- 대표 요청 3개의 Top / Middle / Bottom 학습 메모.
- DDD·Event Storming, Policy / Read Model / Code 매핑표, API별 검사 적용·동기/SSE 비교 결과.

DB 근거는 조회 SQL·대상 ID·실제 결과, Metric 근거는 관련 이름·라벨·실험 전후 값으로 기록할 수 있다. 필요한 응답 본문·로그를 함께 남기며, 대표 화면 캡처는 발표 자료를 정리할 때 선택한다.

Day 6~7의 완료 목표는 **현재 범위의 동작을 직접 확인하고, 오류를 수정·재실험하며 업무·정책·코드·데이터 연결을 설명할 수 있는 1차 중간점검 완료**다. React/API·Dashboard는 Day 8, 외부 검색 모듈은 Day 9~10에 진행한다.
