# 화면 02~12 · ChatGPT 디자인 프롬프트

2026-10-20 · V4 운영자 엄지척 공통 헤더 · 로그인 01 제외 · 11개 화면 · API 연결 계약 유지.

## 사용법

1. ChatGPT에 사용자가 선택한 로그인 레퍼런스 2장을 함께 제공한다. 같은 대화에서 이미 보이면 다시 첨부하지 않아도 된다. references 폴더에 원본을 보관했다.
2. 아래 개별 TXT는 공통 무드까지 포함한 독립 프롬프트다. 화면 하나씩 복사해 제출한다.
3. 같은 ChatGPT 대화에서 계속 만들 때는 공통 무드를 한 번 적용한 뒤, ALL_PROMPTS.md의 화면별 블록만 순서대로 사용할 수 있다.
4. 산출물은 한국어 UI의 화면 디자인 이미지다. 프롬프트 본문은 영어이며 API 메모는 디자인·React 연결을 위한 내부 기준이다. URL·API 번호·JWT를 고객 화면에 그대로 출력하지 않는다.
5. 운영자 09~12는 references/09_12_OPERATOR_THUMBS_UP_SOURCE.png(두 번째 첨부: 실제 사진)와 references/09_12_OPERATOR_HEADER_CROP_REFERENCE.png(첫 번째 첨부: 가로 헤더 구도만 참조)를 함께 제공한다. 네 화면에 같은 엄지척 사진·크롭·헤더·내비게이션을 반복 사용한다.
6. 기존 대화에서 운영자 디자인만 수정할 때는 09_12_SHARED_HEADER_OVERRIDE_V4.txt를 붙여 넣는다.

이번에 사용자가 선호한 자연광 법률 사무실·네이비/로열블루·흰 패널·세리프 브랜드 제목을 우선한다. 이전의 평면적인 중립톤 후보는 공통 기본값에서 제외했다. 다크모드 API 문서는 제품 UI의 테마 지시로 해석하지 않는다.

## 선택한 디자인 기준

- [디자인 방향과 컬러 기준](DESIGN_REFERENCE_V2.md)
- [데스크톱 로그인 레퍼런스](references/01_LOGIN_DESKTOP_PREFERRED.png)
- [모바일 로그인 레퍼런스](references/01_LOGIN_MOBILE_PREFERRED.png)
- [운영자 엄지척 사진 원본 · 실제 사용](references/09_12_OPERATOR_THUMBS_UP_SOURCE.png)
- [가로 헤더 크롭 구도 · 참조용](references/09_12_OPERATOR_HEADER_CROP_REFERENCE.png)
- [기존 대화에 붙이는 헤더 수정 프롬프트](09_12_SHARED_HEADER_OVERRIDE_V4.txt)
- [운영자 공통 이미지 적용 지침](09_12_OPERATOR_SHARED_IMAGE_GUIDE.md)

배경은 창가 자연광·책장·도심 전망의 사무실 무드를 유지하되, 폼·표·차트는 흰색 고대비 패널로 읽기 쉽게 만든다. 의뢰인 화면은 여백과 안내를, 변호사·운영자 화면은 같은 브랜드 아래 업무 밀도를 조절한다. 각 화면 프롬프트에 별도의 시각 연출 지침을 추가했다.

운영자 화면은 악수 사진 대신 두 번째 첨부의 엄지척 사진을 가로 헤더로만 사용한다. 첫 번째 첨부의 낮고 긴 구도를 참고하되 모니터·두 사람의 표정·엄지척을 함께 살리도록 높이를 조절한다. 원본 상단의 큰 제목·아이콘 줄은 완전히 크롭 밖으로 빼고, 첫 번째 이미지의 편집 버튼도 복제하지 않는다. 화면 제목·KPI·필터·표는 헤더 아래 별도 흰 영역에 배치한다.

모바일 이미지는 반응형 브랜드의 참조다. 기본 산출물은 화면별 데스크톱 1장이고, 모바일이 필요할 때 별도로 요청한다. 참고 이미지의 휴대폰 프레임·손·상태바는 앱 UI에 복제하지 않는다. 비밀번호 재설정·언어 선택·보안 보장 문구도 외관을 이유로 새 기능처럼 추가하지 않는다.

## 화면별 파일

| 화면 | 이름 | 역할 | 주요 API | 파일 |
|---|---|---|---|---|
| 02 | 사건 분류·절차 안내 | 의뢰인 | C02 · R01 · R02 | [개별 프롬프트](02_CASE_CLASSIFICATION.txt) |
| 03 | 문의 등록·자료 준비 | 의뢰인 | C03 · R02 · R03 · R04 · R05 | [개별 프롬프트](03_INTAKE_AND_DOCUMENTS.txt) |
| 04 | 간단 안내·문의 상세 | 의뢰인 | C04 · C05 · R02 · R06 | [개별 프롬프트](04_GUIDE_AND_INTAKE_DETAIL.txt) |
| 05 | 검색 요청·결과·이력 | 의뢰인 | C06 · C07 · C08 · P02 · P03 | [개별 프롬프트](05_SEARCH_REQUESTS_AND_RESULTS.txt) |
| 06 | 변호사 문의 목록 | 변호사 | C09 | [개별 프롬프트](06_ATTORNEY_INTAKE_LIST.txt) |
| 07 | 변호사 문의 상세 | 변호사 | C05 | [개별 프롬프트](07_ATTORNEY_INTAKE_DETAIL.txt) |
| 08 | 상태 변경·상담 메모 | 변호사 | C10 · C11 | [개별 프롬프트](08_STATUS_AND_CONSULTATION_MEMO.txt) |
| 09 | 운영 요약 | 운영자 | C12 | [개별 프롬프트](09_OPERATOR_OVERVIEW.txt) |
| 10 | 마케팅 집계 | 운영자 | C13 | [개별 프롬프트](10_MARKETING_AGGREGATES.txt) |
| 11 | 보안·위험·비용 지표 | 운영자 | C14 · C15 · C16 | [개별 프롬프트](11_SECURITY_RISK_AND_COST.txt) |
| 12 | 위험 이력·Incident 대응 | 운영자 | C17 · C18 · C19 · C20 | [개별 프롬프트](12_RISK_HISTORY_AND_INCIDENTS.txt) |

- [공통 디자인 기준](00_COMMON_DESIGN.txt)
- [전체 프롬프트](ALL_PROMPTS.md)

## 주요 API 연결표

2026-10-22 연결 정보 보완: 이 묶음의 디자인 프롬프트는 기존 02~12 범위이며, 아래 API 표에는 현재 화면13의 C16·C17 재사용을 추가했다. 최신 운영자 연결표는 [화면09~13 API 연결표](../../SCREEN09_13_API_CONNECTIONS.md)를 참고한다. P03은 현재 구현된 API다.

| 화면 | API | 메서드 | 경로 |
|---|---|---|---|
| 02 | C02 | POST | `/api/intakes/classify-preview` |
| 02 | R01 | GET | `/api/legal/procedures` |
| 02 | R02 | GET | `/api/legal/procedures/{code}` |
| 03 | C03 | POST | `/api/intakes` |
| 03 | R02 | GET | `/api/legal/procedures/{code}` |
| 03 | R03 | GET | `/api/legal/procedures/{code}/steps` |
| 03 | R04 | GET | `/api/legal/forms` |
| 03 | R05 | GET | `/api/legal/forms/{formCode}` |
| 04 | C04 | POST | `/api/legal-intake/minimal` |
| 04 | C05 | GET | `/api/intakes/{id}` |
| 04 | R02 | GET | `/api/legal/procedures/{code}` |
| 04 | R06 | GET | `/api/legal/criminal/checklist/supplementary-investigation` |
| 05 | C06 | POST | `/api/intakes/{intakeId}/search-requests` |
| 05 | C07 | GET | `/api/intakes/search-requests/{searchRequestId}` |
| 05 | C08 | POST | `/api/intakes/search-requests/{searchRequestId}/results` |
| 05 | P02 | GET | `/api/intakes/{intakeId}/search-requests` |
| 05 | P03 | POST | `/api/intakes/search-requests/{searchRequestId}/execute` |
| 06 | C09 | GET | `/api/intakes` |
| 07 | C05 | GET | `/api/intakes/{id}` |
| 08 | C10 | PATCH | `/api/intakes/{id}/status` |
| 08 | C11 | POST | `/api/intakes/{id}/memo` |
| 09 | C12 | GET | `/api/admin/dashboard/summary` |
| 10 | C13 | GET | `/api/admin/dashboard/marketing-funnel` |
| 11 | C14 | GET | `/api/admin/dashboard/security-events` |
| 11 | C15 | GET | `/api/agent/risk/summary` |
| 11 | C16 | GET | `/actuator/prometheus` |
| 12 | C17 | GET | `/api/agent/risk/history` |
| 12 | C18 | GET | `/api/incidents` |
| 12 | C19 | PATCH | `/api/incidents/{incidentId}/ack` |
| 12 | C20 | PATCH | `/api/incidents/{incidentId}/resolve` |
| 13 | C16 (재사용) | GET | `/actuator/prometheus` |
| 13 | C17 (재사용) | GET | `/api/agent/risk/history` |

P01 GET /api/me는 공통 로그인 사용자 문맥이다. P04/P05 파일 API와 P06 이벤트는 해당 화면의 공통 보조 흐름으로 설명했다. 기본 화면별 연결 수를 새 고유 API 수처럼 합산하지 않는다. S01~S05는 AI 처리·보안 검사·검증용 백엔드 API로 **별도 화면 불필요**에 해당한다. 향후 화면 지정 과제가 아니며, 자유 채팅 화면을 새로 추가하지 않는다.

## 이전 초안에서 현재 계약에 맞춘 점

- C02는 규칙 기반 미리보기이며 저장/외부 LLM 호출이 없다. C03만 문의 ID를 생성한다.
- C04는 규칙 기반 간단 안내이고 C05는 저장 문의 조회다. 일반 채팅이나 저장 API로 표시하지 않는다.
- 화면 05의 구현 범위는 요청 기록·조회·결과 스냅샷·이력이다. P03 외부 검색 실행은 준비 중으로 비활성화한다.
- 화면 06 목록은 최근 최대 50건이며 필터·집계의 범위를 명시한다. 상태는 NEW가 아닌 SUBMITTED를 사용한다.
- 화면 08의 C10 상태 변경과 C11 메모 추가는 별도 저장이다. 메모 content와 followUpItems도 별도 필드다.
- 화면 09는 C12의 실행·위험·보안 이력 건수와 OPEN Incident, riskSummary만 사용한다. 없는 문의 총계·일별 추이를 만들지 않는다.
- 화면 10은 현재 DB 집계이며 집계 단위가 다른 단계에서 전환율·이탈률을 계산하지 않는다.
- 화면 11은 C14/C15 DB 응답과 C16 Prometheus 메모리 지표를 구분한다. C16은 ADMIN Basic이며 결측을 0으로 꾸미지 않는다.
- 화면 12는 실제 RiskHistory/Incident 필드를 사용한다. Incident 선택은 React 내부 처리이고 C19/C20은 해당 id로 상태만 바꾼다.

## 대조한 로컬 자료

- ../../SCREEN_WIREFRAME_PROMPTS.md (기존 디자인 방향과 12개 화면 초안)
- ../../SCREEN_API_CONTROLLER_MAPPING.md (화면/API 연결표)
- ../../diagrams/04_OPERATOR_OPERATIONS_MARKETING_AUDIT_FLOW.md (운영자 흐름)
- backend/guardrail-ready-policy-checker의 API Controller, 요청/응답 DTO, IntakeStatus, 집계 Service.

이번 갱신은 디자인 방향과 화면별 시각 지침에 한정했다. 11개 API HANDOFF 블록은 이전 버전과 동일함을 확인했다. 구현 코드, 기존 로그인, 193페이지 API Flow PDF는 수정하지 않았다.
