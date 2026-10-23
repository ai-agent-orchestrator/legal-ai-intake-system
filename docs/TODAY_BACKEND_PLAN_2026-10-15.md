# 2026-10-15 백엔드 작업 목표 및 제출 묶음

**오늘 목표:** 공유된 2~4일차 Backend Draft 계획을 기준으로 기존 저장·조회 구조를 확인하고, 화면 매핑의 미구현 API 4개를 연결한다. 결과는 전체 흐름도, 개발 상태별 API 정리서, DDD·Event Storming 학습 문서로 설명한다.

오늘 대화에서 확인된 계획표는 2~4일차 행이다. 별도의 전체 일정표 파일은 확인하지 못했으므로, 다른 일차의 업무까지 완료했다고 판단하지 않는다.

| 순서 | 오늘 할 일 | 목적 / 종료 기준 | 현재 결과 |
|---|---|---|---|
| 1 | 백엔드 전체 흐름도 | 접수·검색·검토·AI 보안·운영의 현재 연결과 설계중 연결을 설명 | 문서 및 Mermaid 원본 작성 |
| 2 | API 정리서 | 개발중과 설계중을 분리하고 화면·Controller·입출력을 연결 | 제출 대상 31개 API 및 후속 설계 6개 정리 |
| 3 | 매핑의 미구현 API 4개 | 분류 미리보기, 상태 변경, 상담 메모, 마케팅 기본 집계 | 코드 추가, 자동 통합 테스트 통과 |
| 4 | DDD·Event Storming | Actor → Command → Event → Policy → Aggregate → Read Model → 실제 코드 설명 | 현재 코드에 대응하는 초안 작성 |
| 5 | 직접 Postman 복습 | 정상·실패 요청을 보내 응답·DB·traceId를 연결해 설명 | 실행용 Collection 제공, 수동 실행 결과는 별도 기록 필요 |

**제출 파일**

| 제출물 | 파일 |
|---|---|
| 전체 백엔드 Mermaid flowchart | [BACKEND_FULL_FLOW.md](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_FULL_FLOW.md) / [Mermaid 원본](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_FULL_FLOW.mmd) |
| 개발중 / 설계중 API 정리서 | [API_STATUS_SPEC.md](C:/myLectureWs/legal-ai-intake-security-server/docs/API_STATUS_SPEC.md) |
| 화면·API·Controller 매핑 | [SCREEN_API_CONTROLLER_MAPPING.md](C:/myLectureWs/legal-ai-intake-security-server/docs/SCREEN_API_CONTROLLER_MAPPING.md) |
| DDD·Event Storming 학습 자료 | [BACKEND_DDD_EVENT_STORMING.md](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DDD_EVENT_STORMING.md) |
| 실제 Controller 전체 경로 부록 | [BACKEND_ROUTE_INVENTORY.md](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_ROUTE_INVENTORY.md) |
| Postman 재현용 요청 | [BACKEND_DRAFT_TODAY.postman_collection.json](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_TODAY.postman_collection.json) |

**개발중과 설계중의 기준.** 개발중은 코드가 존재하는 상태이다. 자동 검증 여부를 별도로 표기하며, 프런트 연결·외부 서비스 연동·운영 검증까지 모두 끝났다는 의미로 사용하지 않는다. 설계중은 Controller가 아직 없거나, 기존 API의 확장으로 제안한 동작이다. 제안 경로는 확정된 계약으로 표시하지 않는다.

**오늘 구현한 범위**

- `POST /api/intakes/classify-preview`: 저장 없이 기존 접수 분류 규칙으로 사건 유형·분쟁 영역·권장 자료 안내.
- `PATCH /api/intakes/{id}/status`: 검토·자료 보완·상담 준비·종료 상태 변경. 같은 상태 재요청은 성공 처리하며 종료 후 재개와 접수 상태로 되돌리기는 409.
- `POST /api/intakes/{id}/memo`: 메모 및 후속 조치 코드 저장. 응답과 Intake 조회의 메모 본문 패턴 마스킹.
- `GET /api/admin/dashboard/marketing-funnel`: 제출 문의 수, 검색 요청 수, 중복 제거한 검색 요청 문의 수, 현재 상담 준비 상태 수 및 유형·요청 목적·지역 분포.
- 검색 목적 길이를 요청 DTO·DB 컬럼 모두 최대 300자로 맞춰, Intake 목적 상속 시 길이 초과 문제 보완.

마케팅의 방문·시작·요약 조회는 아직 이벤트 수집이 없어 `count=null`, `measurement=UNTRACKED`로 표시한다. 이탈률이나 단계별 전환율은 계산하지 않는다. 상담 준비 건수는 현재 상태 집계이므로 과거 누적 도달 수와 다르다.

**검증 결과**

`gradlew.bat test --offline --no-daemon` 성공. 모드 선택 기능 제거 후 최신 결과는 총 15개 테스트: 신규 통합 테스트 9개, 기본 테스트 6개. 실패·오류 0개.

신규 테스트는 Spring Security 필터를 포함한 MockMvc 요청과 H2/JPA 저장·조회를 사용한다. 분류의 무저장, 입력 오류, 상태 전이, 메모 마스킹·조회, 없는 문의 처리, 퍼널 중복 제거·미수집 표현, 빈 DB, 300자 목적 상속·검색 결과 재조회가 포함된다. 실제 Postman 앱 호출, 외부 NeMo/LLM 응답, 운영 환경 연결을 검증한 결과는 아니다.

**직접 이해를 다지는 권장 순서**

1. 20분: 흐름도에서 저장형 Intake, 비저장형 minimal/preview, AI Chat 경로를 각각 소리 내어 설명한다.
2. 40분: Postman Collection을 순서대로 실행하고 Intake ID → 조건 ID → Search Request ID를 적는다.
3. 30분: 상태를 REVIEWED → CONSULTATION_READY → CLOSED로 바꾸고, 종료 후 재개 요청의 409를 확인한다.
4. 20분: 같은 Intake에 검색 요청을 두 번 생성해 요청 건수와 문의 건수가 다른 이유를 설명한다.
5. 30분: Event Storming 표에서 이벤트 하나를 골라 실제 메서드·저장 테이블·정책·실패 응답까지 연결한다.

제출 설명 예시: “Backend Draft의 저장·조회 골격에 분류 미리보기, 상담 검토, 기본 운영 집계를 연결했습니다. 현재 코드가 있는 API는 개발중으로, 파일 업로드·실제 검색 실행·행동 이벤트 수집 등은 설계중으로 구분했습니다. 자동 통합 테스트와 DDD/Event Storming 매핑으로 동작과 설계 근거를 함께 설명합니다.”

**후속 일정 변경 — 사용자 확정:** Day 4인 2026-10-16에는 현재 범위의 백엔드·Security·Cost Control·Dynamic Guard 구현과 자동 검증을 마무리한다. Postman 1차 집중 실험과 구조 복습은 Day 5인 2026-10-17에 진행한다. 상세 실행 순서는 [Day 4·5 계획](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_FIRST_PASS_PLAN_2026-10-16.md)을 따른다.
