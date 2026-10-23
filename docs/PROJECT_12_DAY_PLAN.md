# 12일 개발 계획

개정: 2026-10-17. 발표용 개발 계획과 이후 작업 순서의 기준으로 사용한다. 기능별 실제 구현·연결·검증 상태는 API 정리서와 실험 기록에 별도로 남긴다.

## 전체 일정

**Day 1~2 설계 산출물 → Day 2~4 Backend Draft → Day 5 Security·Cost Control → Day 6~7 Postman 1차·DDD·Mapping·Refactoring → Day 8 Metric Dashboard·React/API 연결 → Day 9~10 LLM 연동·Search Routing → Day 11~12 통합 검증·산출물·시연**

## 상세 계획

| Day | Phase | 목표 | 핵심 작업 | 산출물 |
|---|---|---|---|---|
| 1 | 요구사항·프로젝트 방향 설계 | 프로젝트 목적과 개발 기준 수립 | 시장 문제·사용자·프로젝트 목적 정의, Ground Rule 작성, MVP 범위와 모듈별 개발 방식 정리 | 프로젝트 목적·문제 정의, Ground Rule 초안, 개발 범위·방법론 초안 |
| 2 | 설계 확정·초기 산출물 | DB·화면·API 구조 설계 | Legal Intake 저장 구조 설계, 화면 정의서·ERD·API 기능서 작성, Screen–API–Controller 매핑 초안 작성 | Ground Rule, 화면 정의서, ERD 초안, API 기능서 초안, 화면/API 매핑표 |
| 2~4 | Backend Draft 구현 | 서버 기본 흐름과 DB 저장·조회 구현 | Controller·DTO·Service·Repository·Entity 구성, Legal Intake·자료 종류·검색 조건 저장, 문의 목록·상세 조회, 검색 요청·결과 기록 API 구현 | 백엔드 기본 골격, 백엔드·보안·비용 흐름도 초안, API 기능서, DB 저장·조회 흐름 |
| 5 | Security·Cost Control 연결 | 확정한 API 경로에 보안·비용 통제 연결 | Fixed·Dynamic Guard, Cost Control, 입출력 Guard·Privacy Output Guard 연결, reasonCode·Error Stop Flow 정리, 인증·CORS 설정 정리, 현재 범위의 잔여 백엔드 보완, 프로젝트 DB 연결 확인 | 보안·비용 연결 버전, Screen–API–Controller 매핑표, Postman 실험서, ERD 갱신본, DB 연결 점검 기록 |
| 6 | Postman 1차·코드 매핑·Refactoring | 실제 동작을 확인하며 Top–Middle 연결 | API별 정상·실패·차단 요청 실행, 응답·ID·MySQL 행 확인, 같은 흐름을 두 차례 확인하며 Controller·Service·저장/응답 매핑, 발견 오류 수정·재실험 | Postman 1차 기록, Top–Middle 매핑표, 오류·수정·재실험 기록, API·ERD 보완본 |
| 7 | DDD·Event Storming·Policy Mapping | 익힌 흐름을 도메인과 코드로 정리 | Actor·Command·Domain Event·Policy·Aggregate·Read Model·Bounded Context 정리, Policy–Read Model–Code 매핑, API별 검사 적용과 동기/SSE 정책 비교, 대표 경로의 Top–Middle–Bottom 불일치 수정 | Event Storming, 도메인 경계·매핑표, API별 검사 목록, 동기/SSE 비교 결과, 정책·코드·실험 연결표 |
| 8 | React/API 연결·Metric Dashboard | 핵심 화면에서 입력·조회·운영 지표 확인 | 확정한 핵심 React 화면과 Dashboard 구현, API 함수·state·DTO 필드 매칭, 기존 Metric·Admin Summary·Security Event 조회 연결, 필요한 지표 누락 보완, 브라우저 CORS·preflight 확인 | React 핵심 화면, Metric Dashboard, React/API 연결표·필드 매핑표, 화면 연동 기록·캡처 |
| 9~10 | LLM 연동·LLM Search Routing | 실제 검색 자료와 설명을 제공하는 흐름 구현 | LLM 실서비스 연동, 문의·검색 조건을 활용한 검색문 구성, 외부 검색 API·도구 실행, 검색 결과 저장·조회와 LLM 설명 연결, 출력 보호·호출 실패 처리 | LLM Search Routing 기능, 외부 검색 연동, 검색 결과·설명 API, 호출 흐름도·실험 기록 |
| 11 | Postman 2차·통합 검증·Final Fix | 구현한 모듈의 전체 연결 검증 | 문의 접수·조회, Security·Dynamic·Cost·Privacy, Search Routing·React를 연계 검증, ID·데이터 전달과 정상·실패·차단 흐름 확인, 최종 수정·재실험 | Postman 2차 기록, 통합 검증 결과, Final Fix, 미해결·후속 작업 목록 |
| 12 | 산출물 통합·시연·발표 | 누적 산출물을 정리하고 제출 준비 | 매일 작성한 README·보고서·설계·실험 자료 통합, 화면 캡처·시연 영상·회고·발표자료 정리, 제품기획·마케팅·규제 검토 자료 정돈 | README·결과 보고서, PPT, 시연 영상·화면 캡처, 제품기획서, 마케팅·규제 검토 자료, 회고 |

Day 2는 설계 확정과 초기 구현을 병행하는 구간이다. Day 6~7의 실험과 매핑은 같은 요청·기록을 반복 활용하며 진행한다.

## 단계별 진행 기준

### Day 5: 구현·연결과 실험 준비

확정한 범위의 Guard·비용 통제·개인정보 출력 보호를 필요한 API에 연결하고, 요청·예상 결과·DB 연결을 준비한다. 코드·서버 설정의 연결 여부를 정리하며, 실제 API별 적용 결과는 Day 6~7의 실험·매핑에서 확인한다.

저장형 문의·규칙형 안내·AI Chat은 처리 경로가 다르므로 각각의 필요한 검사를 기록한다. 고난도 보류 항목은 별도 목록으로 유지한다.

**2026-10-17 비용 제어 보완 — 기존 API 4개 개선 + C16 지표 확장**

| API | 구현·연결한 내용 | 코드·검증 매핑 |
|---|---|---|
| S01 동기 Chat | 입력 Guardrail·답변 생성·출력 Guardrail을 각각 예산 검사·예약·보고/추정 정산 | AiService → CostedGuardrailService·AiCostControlService. 출력 차단·출력 예산 부족에도 생성 비용 유지 |
| S02 SSE Chat | 같은 3단계 정산, 완료 후 출력 검사, 오류·timeout·연결 종료 시 미확인 생성 비용 추정 정산 | AiStreamingService. 늦은 완료·중복 콜백의 중복 정산 방지 |
| S03 입력 단독 검사 | sessionId 선택·공통 예산·검사 비용 정산·비용 차단 필드 | LegalGuardrailController → CostedGuardrailService |
| S04 출력 단독 검사 | 같은 비용 통제, 로컬 출력 정책은 유료 호출 전에 적용 | LegalOutputGuardrailController → CostedGuardrailService·OutputGuardrailPolicy |
| C16 지표 | 단계별 예약·보고·추정·합계와 전체 합계 | Micrometer stage 태그, ADMIN Basic Prometheus 경로 유지 |

새 HTTP API는 추가하지 않는다. 구현 API 36개·설계중 P03 1개는 유지한다. 자동 검증은 GuardrailCostTest·GuardrailUsageClientTest·AiSecurityIntegrationTest에 연결했고, 2026-10-17 전체 백엔드 테스트 124개가 통과했다. 외부 NeMo의 실제 usage 보고·실청구 대사와 Postman 실서비스 실험은 별도 확인 사항이다. 상세 흐름·설정·제약은 [Cost Control 흐름도](diagrams/03_COST_CONTROL_FLOW.md)를 기준으로 읽는다.

### Day 6~7: 실행 → 이해 → 도메인 정리 → 적용 점검

```text
1회차: 요청·응답·DB·ID 확인
→ 2회차: 같은 요청을 Controller·Service·검사·저장/응답과 매핑
→ 익힌 업무 흐름을 DDD·Event Storming으로 정리
→ API별 검사 적용·동기/SSE 정책 비교
→ 불일치 수정·같은 요청 재실험
```

Day 6은 Postman과 코드 매핑, Day 7은 도메인·정책·코드·실험의 연결에 중심을 둔다. Bottom은 대표 흐름의 검증 조건·저장 필드·실패 분기를 실제 결과와 연결한다. DDD 산출물은 업무 명령·사실·정책과 현재 코드 위치를 설명하는 자료로 작성한다.

### Day 8: 기존 백엔드를 화면에 연결

확정한 핵심 화면과 Dashboard를 구현하고 기존 조회·Metric API를 연결한다. React 입력 state → 요청 JSON → 응답 DTO → 화면 갱신을 확인하며 실제 브라우저에서 CORS도 확인한다. 화면/API 필드 매핑과 관련 DDD Read Model 설명을 연결 결과에 맞춰 갱신한다.

### Day 9~10: LLM과 검색 도구의 역할 연결

- LLM: 문의·검색 조건을 바탕으로 검색문을 구성하고 검색 결과를 설명한다.
- 외부 검색 API·도구: 실제 자료를 조회한다.
- 백엔드: 검색 요청·실행 결과·설명을 연결하고 저장·조회와 출력 보호를 처리한다.

기존 검색 요청 저장·조회와 실제 외부 검색 실행을 구분해 기능과 실험 결과를 기록한다.

### Day 11~12: 통합 확인과 발표 준비

Day 11에는 그때 구현된 API를 화면 흐름에 맞춰 호출하며 intakeId·searchRequestId 등 ID와 데이터 전달을 확인한다. 문의 등록이 검색 요청이나 LLM 호출을 자동 실행한다고 가정하지 않고 실제 호출 순서를 기준으로 검증한다. Day 12에는 매일 작성한 자료를 모아 설명·시연·제출 구성을 완성한다.

## 범위와 기록

- SSE 중단·timeout·오류의 미확인 비용 추정 정산과 중복 정산 방지는 구현·자동 검증 범위에 포함했다. 날짜 경계의 세부 귀속 검증·실제 제공자 청구 대사·추정액 후속 보정·영속 장부는 후속 범위다. 일반 실패 응답 확인과 실서비스 전체 검증을 구분하고, 최종 자료에 미해결 범위를 기록한다.
- 설계·구현·실제 연결·자동 테스트·수동 실험 상태는 각각 기록한다. 발표용 일정표가 미실행 실험의 통과 기록을 대신하지 않는다.
- 문서·캡처·실험 결과는 매일 누적하고 Day 12에 통합한다.

## 연결 자료

- [현재 API 계약·Controller/Service 매핑·실험 기록표](API_STATUS_SPEC.md)
- [백엔드 흐름도와 병렬형 HTTP 실험 지도](BACKEND_DRAFT_STATUS_AND_API_SPEC.md)
- [API별 검사 적용·동기/SSE 후속 점검표](DAY5_AFTER_MAPPING_SECURITY_CHECK.md)
- [DDD·Event Storming 초안](BACKEND_DDD_EVENT_STORMING.md)
- [프로젝트 DB 연결과 MySQL 실험 준비](MYSQL_CONNECTION_CHECK.md)
- [Screen–API–Controller 매핑](SCREEN_API_CONTROLLER_MAPPING.md)
- [Top–Middle–Bottom 학습 가이드](TOP_MIDDLE_BOTTOM_STUDY_GUIDE.md)
- [기존 구현 경과와 실험 상세 계획](BACKEND_FIRST_PASS_PLAN_2026-10-16.md)
