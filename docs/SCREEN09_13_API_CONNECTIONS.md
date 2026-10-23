# 화면09~13 · API 연결표

> 2026-10-23 운영자 권한 완료: C12~C15·C17~C20 업무8개 API는 ADMIN Bearer JWT 필수다. React 관리자 로그인·P01 확인·로그아웃·인증 실패 시 자료 제거를 연결했다. C16·guardtimeseries는 ADMIN Basic 유지. 아래 공개 접근 판정은 당시 검수 기록으로 읽는다. [최신 인증 계약·225/46 및 실제 브라우저 검증](OPERATOR_ADMIN_AUTH_REVIEW_2026-10-23.md).

2026-10-22 · 화면13 추가 · AI·Guardrail 역할 표기 정리. 운영 설명은 화면13에 통합했으며 화면14는 폐지했다. 실제 시계열용 보조 Actuator API1개가 추가되었다.

Postman의 메서드·URL·Params·Body·인증 입력과 정확한 Controller 메서드는 [화면01~13 전체 매핑표](SCREEN_API_CONTROLLER_MAPPING.md#3-운영자-화면0913-매핑--postman-실험-입력)에서 확인한다. [가져오기용 운영자 Postman 컬렉션](SCREEN09_13_POSTMAN_EXPERIMENTS.postman_collection.json)과 [ID별 입력 가이드](ID_SCREEN_API_POSTMAN_GUIDE.md)를 함께 제공한다.

아래 표는 실제 시계열 연결까지 반영한 최신 계약이다.

| 화면 번호 / 역할 | 연결 수 | API 식별자와 HTTP 메서드·경로 전체 |
|---|---|---|
| 09 / 운영 요약 | 1개 | **C12** `GET /api/admin/dashboard/summary` |
| 10 / 마케팅 집계 | 1개 | **C13** `GET /api/admin/dashboard/marketing-funnel` |
| 11 / 보안·위험·비용 지표 | 3개 | **C14** `GET /api/admin/dashboard/security-events`<br>**C15** `GET /api/agent/risk/summary`<br>**C16** `GET /actuator/prometheus` |
| 12 / 위험 이력·Incident | 4개 | **C17** `GET /api/agent/risk/history`<br>**C18** `GET /api/incidents`<br>**C19** `PATCH /api/incidents/{incidentId}/ack`<br>**C20** `PATCH /api/incidents/{incidentId}/resolve` |
| 13 / AI 위험 요약 · Observability | 기존2개 + 보조1개 | **C16** `GET /actuator/prometheus`<br>**C17** `GET /api/agent/risk/history`<br>**보조 Actuator 신규** `GET /actuator/guardtimeseries` |
| AI·Guardrail / 별도 화면 불필요 | 5개 | **S01** `POST /api/ai/chat`<br>**S02** `POST /api/ai/chat/stream`<br>**S03** `POST /api/legal/guardrail/input`<br>**S04** `POST /api/legal/guardrail/output`<br>**S05** `POST /api/agent/risk/analyze` |
| 공통 / 파일·행동·현재 사용자 | 4개 | **P01** `GET /api/me`<br>**P04** `POST /api/intakes/{intakeId}/documents`<br>**P05** `GET /api/intakes/{intakeId}/documents/{documentId}/download`<br>**P06** `POST /api/intake-events` |

화면13은 **AI 위험 요약(Observability)** 화면이며, 경로는 `operator.html#/observability`다. **C16**의 현재 세션 누적 점수·신규 보조 API의 실제 시계열과 **C17**의 요청별 점수·위험 신호를 함께 보여준다. C16·C17을 재사용하고, 신규 보조 Actuator guardtimeseries가 기존 Prometheus query_range를 읽는다. 번호가 붙은 API37개와 별도로 추가된 읽기 경로1개다.

**AI·Guardrail / 별도 화면 불필요**는 현재 서비스 범위에서 전용 화면을 만들 필요가 없다는 뜻이다. 화면 지정을 보류했거나 앞으로 화면 개발이 남았다는 뜻이 아니다. S01~S05는 AI 처리·보안 검사·검증용 백엔드 API로 구분한다. 화면02~04의 분류·안내는 기존 규칙·DB 기반을 유지하며, P03은 공통 LLM·Guard 서비스를 사용한다. 이것을 S01~S05 HTTP API 전체를 호출한다는 의미로 해석하지 않는다.

화면13 조회가 S05 분석을 새로 실행하는 것은 아니다. C16은 ADMIN Basic으로 읽고, C17은 현재 개발 서버의 기존 공개 조회 정책을 따른다. 기존 보안 정책의 보완 사항은 [화면13 검수 기록](SCREEN13_OBSERVABILITY_REVIEW.md)에 구분해 두었다.

P01은 화면01·공통에서, C05는 화면04·07에서, Reference API는 여러 화면에서 재사용한다. **화면별 연결 수의 합은 고유 API 수가 아니다.** 고유 API는 **37개 = C01~C20(20) + R01~R06(6) + S01~S05(5) + P01~P06(6)**이며, 구현된 P03을 포함한다. P03은 화면05의 실제 외부 검색 실행 API다.

- [화면01~13 전체 연결표와 API 계약](BACKEND_DRAFT_STATUS_AND_API_SPEC.md#화면-번호-하나에-연결된-모든-api)
- [화면13의 지표·점수 및 구현 검수](SCREEN13_OBSERVABILITY_REVIEW.md)
- [P03·화면13을 반영한 기본 흐름](diagrams/02B_SECURITY_FLOW_API_MAP.md)

이 표는 API 연결 범위를 정리한 문서다. 각 API의 실제 호출 성공 여부·유료 외부 호출 여부는 검수 기록과 구분한다.
