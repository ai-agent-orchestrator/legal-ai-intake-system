# 제출용 Mermaid 도표 구성

기준: 2026-10-22. 도표는 **답하려는 질문**으로 나눈다. 백엔드 업무와 보안 판단을 분리해 다음 4개를 본문 도표로 사용한다. 같은 요청의 일부 구간이 겹쳐도 각 도표의 설명 목적은 다르다.

학습 연결은 [Top · Middle · Bottom 학습 가이드](C:/myLectureWs/legal-ai-intake-security-server/docs/TOP_MIDDLE_BOTTOM_STUDY_GUIDE.md)를 기준으로 한다. 전체 Top → 모듈·관점별 Top → 현재 모듈의 Middle 업무·정책 매핑 → Bottom 코드·Postman 실행 확인 순서로 진행하고, 결과를 흐름도와 매핑표에 반영한다.

| 순서 | 도표 / 파일 | 답하는 질문 | 발표·제출에서의 용도 |
|---|---|---|---|
| 1 | [전체 서비스 구성·사용자 흐름](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/01_SYSTEM_OVERVIEW.md) | 누가 무엇을 사용하고 시스템이 어떻게 연결되는가? | 제품·PM 관점의 첫 장 |
| 2 | [백엔드 업무·데이터 처리](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02A_BACKEND_FLOW.md) | 무엇을 처리하고 어디에 저장·조회하는가? | Controller·Service·Repository·DB·비동기 Job 설명 |
| 3 | [Security 판단·차단](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02B_SECURITY_FLOW.md) | 누구의 어떤 입력·세션·출력을 허용하거나 차단하는가? | 인증·AI Guard·출력 보호·중단 조건 설명 |
| 4 | [Cost Control 중심](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/03_COST_CONTROL_FLOW.md) | 호출 전 제한·예산 예약·호출 후 정산은 어떻게 하는가? | 비용 통제 설계·현재 한계 설명 |

API 목록·입출력·ID 전달은 [전체 기능표](BACKEND_DRAFT_STATUS_AND_API_SPEC.md#api-기능표), [화면별 API 연결표](BACKEND_DRAFT_STATUS_AND_API_SPEC.md#화면-번호-하나에-연결된-모든-api), [개별 API 흐름](postman-http/README.md)에서 확인한다. 기능표와 겹치고 가독성이 낮은 전체 API 호출순서도는 제외했다.

각 파일은 설명이 포함된 `.md`와 순수 Mermaid 코드인 같은 이름의 `.mmd`를 함께 제공한다. 기존 [전체 백엔드 상세도](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_FULL_FLOW.md)와 [백엔드·보안 통합도](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02_BACKEND_SECURITY_FLOW.md)는 연결을 확인하는 부록으로 유지한다.

백엔드도에서는 보안을 공통 진입 경계로 참조하고, 보안도에서는 실제 업무 처리를 하나의 상자로 참조한다. 동일한 Guard 단계와 테이블 목록을 양쪽에서 모두 상세하게 반복하지 않는다. 도표를 분리하는 것이 실행 코드나 서버를 별도로 분리한다는 의미는 아니다.

## 2026-10-22 기본 흐름 갱신

**2026-10-22 현행 반영:** P03 실행·화면05 연결 구현, 운영자 React 화면09~13 구현. 화면13은 기존 C16/C17 재사용이며 새 API 번호·DB 테이블을 추가하지 않았다. 구현 API는 37개다. 외부 LLM·네이버 실제 키 호출·청구 검증은 아직 별도다.

[첨부 기본 그림의 최신 독립 문서·PNG·SVG](diagrams/02B_SECURITY_FLOW_API_MAP.md)를 기준으로 한다. 같은 Mermaid가 들어간 README·02-B·백엔드/보안 통합도와 전체 서비스도·통합 기능서·개별 P03 흐름을 함께 동기화했다. 운영자 화면09~13의 출처는 [운영자 흐름](diagrams/04_OPERATOR_OPERATIONS_MARKETING_AUDIT_FLOW.md)에 표시한다. Markdown·Mermaid·SVG/PNG만 이번에 갱신하며 기존 PDF/DOCX는 이전 판본이다.

**Cost Control 변경을 다른 도표에 반영하는 기준**

비용 금액·계산식·상세 정산 상태는 [03 비용 도표](diagrams/03_COST_CONTROL_FLOW.md)를 기준으로 관리한다. 다른 도표는 각 설명 수준에서 호출 순서·정책 적용 위치·중단 조건을 맞춘다. 단순 흐름의 큰 구조가 같으면 상자와 연결선을 늘릴 필요는 없지만, S03·S04를 비용 통제와 무관하다고 표시하거나 구현한 SSE 중단 정산을 보류라고 설명하는 내용은 수정해야 한다.

| 도표 | 이번 변경의 반영 수준 |
|---|---|
| [02-A 백엔드 업무 흐름](diagrams/02A_BACKEND_FLOW.md) | Controller → Service → DB/외부 호출 → 응답의 단순·제출용 그림 유지. 확대 부록의 AI·단독 검사에는 공통 비용 서비스 적용을 표시 |
| [02-B 보안 흐름](diagrams/02B_SECURITY_FLOW.md) | 단계별 도표와 4절의 전체 통합 상세도에 입력·출력 검사 전 예산 확인, S03·S04 공통 예산, 출력 차단 시 비용 유지, SSE 중단 추정 정산을 반영. 비용 계산식은 03으로 연결 |
| [백엔드+보안 통합도](diagrams/02_BACKEND_SECURITY_FLOW.md) | 첫 단순 그림 유지. API 번호 지도·처리표·확대 부록의 예약·정산 순서와 SSE 오류·중단 설명 갱신 |
| [전체 백엔드 상세 부록](BACKEND_FULL_FLOW.md) | 실제 호출 순서를 나타내므로 입력·생성·출력의 비용 예약·정산과 단독 검사 비용 경로를 반영 |
| [03 Cost Control](diagrams/03_COST_CONTROL_FLOW.md) | 관련 API 전체 범위, USD 장부와 모의 토큰의 구분, 예산·보고/추정 정산·지표·후속 범위를 상세 설명 |

`ORIGINAL`로 구분한 과거 도표는 과거 기록으로 보존한다. 현재 동작 설명에는 본문 도표와 연결된 현행 확대 부록을 사용한다. 현행 그림을 수정한 경우 대응하는 `.mmd`도 같은 내용으로 맞춘다.

전체 보안은 [02-B 4절 통합 상세도](diagrams/02B_SECURITY_FLOW.md#4-전체-보안-상세-흐름도--통합-mermaid)와 [확대용 SVG](diagrams/assets/02B_SECURITY_FLOW.svg)에서 확인한다. Security 모듈 확대도는 [Fixed Guard](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02B1_FIXED_GUARD_FLOW.md)· [Dynamic Guard](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02B2_DYNAMIC_GUARD_FLOW.md) · [Privacy Protection](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02B3_PRIVACY_FLOW.md)으로 분리했다. 전체 Security의 위치를 먼저 보고, 현재 요청 검사와 세션 이력 누적을 각각 확대해 읽는다. 본문 도표 4개의 하위 학습 자료로 사용한다.

**상태를 표기하는 공통 원칙**

- 개발중: 코드·설정이 있는 기능. 구현·프런트 연결·실행 검증 상태는 설명에서 구분한다.
- 설계중: 아직 연결되거나 구현되지 않은 기능. 주황색과 점선으로 표시한다.
- 외부 NeMo/LLM 연결 코드의 존재를 실서비스 연동 검증 완료로 표시하지 않는다.
- 사용자 화면 설계와 백엔드 API 구현을 같은 완료 상태로 표시하지 않는다.
- 예산·권한·DB 영속화 등 목표 설계가 현재 코드와 다르면 차이를 설명한다.

**보조 도표 — 데이터 관계와 도메인 설명**

| 우선순위 | 보조 도표 | 필요한 이유 | 현재 상태 |
|---|---|---|---|
| 1 | [ERD / 데이터 관계도](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_ERD.md) | 핵심 업무 8개 테이블의 ID 연결, 파일·다운로드 기록·행동 이벤트, Reference 3개 테이블의 실제 FK 설명 | 2026-10-16 작성 완료. 핵심 업무의 논리 참조와 Reference의 실제 FK를 Entity·생성 DDL로 구분. Mermaid 원본·H2 DDL 포함 |
| 2 | DDD Context Map + Event Storming | 업무 명령·정책·Aggregate 후보·발생한 사실과 코드의 연결을 설명. | [DDD·Event Storming 문서](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DDD_EVENT_STORMING.md)에 초안 있음. |

상태 전이도는 기존 DDD 문서에 포함되어 있으므로 현재는 별도 제출 파일을 늘릴 필요가 없다. 보안 차단 흐름은 02-B에 포함하고, 향후 장애 분석이 복잡해지면 운영 추적 도표(traceId → log → metric → Incident)를 추가한다.

현재 제출·학습 자료는 **본문 흐름도 4개 + ERD + DDD·상태 전이 보조 자료**다. ERD와 API 정리서를 함께 보며 Top에서 Middle의 데이터·코드 연결로 내려간다. 발표 순서는 전체도 → 백엔드 업무 → 보안 판단 → 비용 통제 → 기능표·개별 API 시연 → DDD/데이터 설계가 이해하기 쉽다.

**Postman 1차 실험의 진행 지도**

매핑 우선순위와 기본 4요청·AI 연결 시 추가 4요청은 [Postman API 체크리스트 색인](C:/myLectureWs/legal-ai-intake-security-server/docs/POSTMAN_API_CHECKLIST_INDEX.md)에 정리했다. [번호가 있는 API 체크리스트](C:/myLectureWs/legal-ai-intake-security-server/docs/POSTMAN_NUMBERED_API_CHECKLIST.md)와 [기존 보조 Controller API 30개·별도 Actuator/NeMo 경로](C:/myLectureWs/legal-ai-intake-security-server/docs/POSTMAN_AUXILIARY_API_CHECKLIST.md)를 분리하고, API마다 Code mapping·Setup·기능/비용/토큰 확인 항목을 둔다. 두 상세 파일은 필요한 기능을 찾는 reference이며 전수 수동 실행 목록이 아니다. 기존 대표 분기 145개도 현재 매핑·변경 범위에서 선택한다.

[오늘 버전 Backend Draft 문서](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_STATUS_AND_API_SPEC.md)의 3번은 실험용 흐름도다. 3-1의 업무 API 병렬 지도에서 묶음을 고르고, 구현 API 37개의 개별 HTTP 요청·응답 도표로 정상·오류를 비교·캡처한다. P03은 구현된 검색 실행 흐름으로 구분하며, 실키 검증 상태는 별도로 표시한다. [캡처용 분리본](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/README.md)도 제공한다. 이후 3-2 실패·정책 분기 → 3-3 AI·SSE → 3-4 Reference·운영으로 확장한다. 도표의 C/R/S/P 번호는 API 정리서의 37개 API와 연결되며 실제 수동 결과는 API 정리서 11절에 기록한다.
