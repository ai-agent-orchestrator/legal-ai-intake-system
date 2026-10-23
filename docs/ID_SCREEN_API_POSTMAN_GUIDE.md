# ID · 화면 · API · Postman 입력 가이드

2026-10-22 · 현재 Controller·DTO·SecurityConfig 기준. 이 문서 작성에서는 HTTP 요청이나 유료 외부 호출을 실행하지 않았다.

**GET은 조회 메서드이며 ID 자체가 아니다.** 같은 ID를 조회할 때는 GET, 관련 기록을 만들 때는 POST, 상태를 바꿀 때는 PATCH를 사용한다. `accessToken`은 DB의 ID가 아닌 로그인 인증 토큰이다.

Postman 변수 `baseUrl`은 기본 백엔드 주소 `http://127.0.0.1:8080`으로 설정한다. 아래 `{{intakeId}}` 등의 변수에는 **실제 응답값**을 넣는다. URL의 `id` 자리와 JSON Body의 필드는 별개다.

## 1. 화면·기능·ID 구분

| 값 | 어떤 기능의 값인가 | 어디에서 받는가 | 사용하는 화면·API | 입력 위치 |
|---|---|---|---|---|
| `intakeId` | 접수된 문의 1건 | 화면03 C03 `POST /api/intakes` 응답 `id`; 화면06 C09 `GET /api/intakes` 항목 `id` | 화면04·07 C05 상세, 화면05 C06 검색 요청 생성·P02 이력, 화면08 C10 상태·C11 메모, 공통 파일·행동 | 대부분 URL Path. P06 SUMMARY_VIEWED는 JSON Body의 intakeId |
| `conditionId` | 문의에 저장된 변호사 검색 조건 1건 | C03/C05 응답 `lawyerSearchCondition.id` | 화면05 C06에서 서버가 문의의 검색 조건을 검색 요청과 연결 | 현재 클라이언트 입력값이 아님. C06에는 intakeId만 전달 |
| `searchRequestId` | 같은 문의 안의 검색 요청 1건 | 화면05 C06 생성 응답 `id`; P02 이력 항목 `id` | 화면05 C07 요청·결과 조회, C08 결과 링크 직접 저장, P03 실제 검색 실행 | URL Path |
| `incidentId` | 운영자가 검토할 위험 Incident 1건 | 화면12 C18 `GET /api/incidents` 항목 `id` | 화면12 C19 확인·C20 종결 | URL Path. Body 없음 |
| `accessToken` | 로그인한 사용자임을 확인하는 인증 값 | 화면01 C01 로그인 POST 응답 `accessToken` | 화면01 P01, 화면05 P02·P03, 공통 P04·P05, S01~S04. C03 소유자 기록에도 사용 가능 | Authorization의 Bearer Token. C16 ADMIN Basic과 구분 |

화면09 C12 운영 요약, 화면10 C13 마케팅, 화면11 C14·C15·C16 조회에는 이 표의 업무 ID를 입력하지 않는다. 화면13도 C16·C17 전체 조회이므로 intakeId·incidentId를 넣지 않는다. 화면13의 요청별 점수는 C17 위험 이력 ID와 연결되며, Incident 상태 변경은 화면12의 C18 `id`를 사용한다.

## 2. intakeId · 문의 상세·상태·메모·검색 요청

문의 등록 요청은 **POST**다. 예시는 필수값 중심이며 소유자를 남기려면 C01에서 받은 Bearer Token을 함께 보낸다.

```http
POST {{baseUrl}}/api/intakes
Content-Type: application/json
Authorization: Bearer {{accessToken}}
```

```json
{
  "sessionId": "postman-intake-01",
  "inquiryPurpose": "절차 안내",
  "caseType": "CIVIL",
  "partyPosition": "원고",
  "coreStatement": "돈을 빌려줬는데 돌려받지 못했어요.",
  "desiredNextStep": "법적 절차가 어떻게 진행되는지 알고 싶어요.",
  "preferredRegion": "서울특별시",
  "documents": ["계약서", "이체 내역"]
}
```

응답의 최상위 `id`를 intakeId에 넣는다. `lawyerSearchCondition.id`와 구분한다. 같은 문의 ID를 다음처럼 재사용한다.

| 화면·기능 | API | Postman 요청 | Body·인증 |
|---|---|---|---|
| 04·07 문의 상세 | C05 | `GET {{baseUrl}}/api/intakes/{{intakeId}}` | Body none; 현재 No Auth |
| 05 검색 요청 생성 | C06 | `POST {{baseUrl}}/api/intakes/{{intakeId}}/search-requests` | raw JSON `{}` 필수; 현재 No Auth |
| 05 문의별 검색 이력 | P02 | `GET {{baseUrl}}/api/intakes/{{intakeId}}/search-requests` | Body none; Bearer + 소유자/ADMIN |
| 08 문의 상태 변경 | C10 | `PATCH {{baseUrl}}/api/intakes/{{intakeId}}/status` | raw JSON `{"status":"REVIEWED"}`; 현재 No Auth |
| 08 상담 메모 추가 | C11 | `POST {{baseUrl}}/api/intakes/{{intakeId}}/memo` | raw JSON `{"content":"자료 확인이 필요합니다.","followUpItems":["REQUEST_DOCUMENTS"]}`; 현재 No Auth |

## 3. conditionId · 변호사 검색 조건

C03은 문의를 만들 때 검색 조건도 저장한다. 응답의 `lawyerSearchCondition.id`가 conditionId다. 이 값은 서버의 문의·검색 조건·검색 요청 연결에 쓰인다.

Postman에서는 **C06 URL에 intakeId를 넣고 Body에 `{}`를 보낸다.** `conditionId`를 Query나 Body에 추가할 필요가 없다. 현재 conditionId를 Path로 받는 별도 조회 API는 없다. 저장된 조건 확인은 C05 응답의 `lawyerSearchCondition`을 읽는다.

## 4. searchRequestId · 검색 요청·결과·실제 실행

C06 생성 응답의 `id`를 searchRequestId에 넣는다. 같은 문의라도 검색 요청을 여러 번 기록하면 각 요청 ID는 다르다. 문의 ID를 검색 요청 ID 자리에 넣지 않는다.

| 기능 | API | Postman 요청 | Body·인증 |
|---|---|---|---|
| 요청·저장 결과 조회 | C07 | `GET {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}` | Body none; 현재 No Auth |
| 결과 링크 직접 저장 | C08 | `POST {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}/results` | sourceType·title·url 필수 JSON; 현재 No Auth |
| 실제 외부 검색 실행 | P03 | `POST {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}/execute` | Body none 또는 `{}`; Bearer + 소유자/ADMIN |
| 설명만 재시도 | P03 | 같은 `/execute`로 POST | raw JSON `{"retryExplanation":true}`; 재시도 가능 상태·같은 ID 사용 |

C08은 제공한 링크를 저장하는 API다. 외부 검색은 P03으로 실행하며, 설정된 외부 LLM·검색 제공자를 호출할 수 있다. 처리 단계와 결과는 같은 searchRequestId로 C07을 다시 조회한다.

## 5. incidentId · 위험 Incident 확인·종결

먼저 **C18 GET 목록**에서 실험할 Incident를 선택한다.

```http
GET {{baseUrl}}/api/incidents
```

Body none, 현재 No Auth다. 선택한 항목의 `id`를 incidentId에 넣는다. 같은 항목의 `riskHistoryId`는 원인이 된 위험 분석 이력 ID이며, PATCH 주소에 쓰는 값이 아니다.

| 기능 | API | Postman 요청 | Body·기대값 |
|---|---|---|---|
| 확인 처리 | C19 | `PATCH {{baseUrl}}/api/incidents/{{incidentId}}/ack` | none; 같은 id, status=ACKNOWLEDGED |
| 종결 처리 | C20 | `PATCH {{baseUrl}}/api/incidents/{{incidentId}}/resolve` | none; 같은 id, status=RESOLVED |
| 변경 확인 | C18 | `GET {{baseUrl}}/api/incidents` | none; 같은 항목 상태 확인 |
| 운영 요약 갱신 | C12 | `GET {{baseUrl}}/api/admin/dashboard/summary` | none; openIncidentCount 확인 |

현재 위 네 요청은 No Auth다. `GET /api/incidents/{{incidentId}}`라는 단건 조회 API는 구현 목록에 없다. 확인·종결을 GET이나 `{"status":...}` Body로 대체하지 않는다.

## 6. accessToken · 로그인 인증

토큰을 받는 요청은 **C01 POST**다. 현재 개발 계정 예시는 user/user123이며, 변경된 서버에서는 해당 계정을 사용한다.

```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json
```

```json
{"username":"user","password":"user123"}
```

응답의 `accessToken`을 Postman 변수에 보관하고 **Authorization → Bearer Token → `{{accessToken}}`**을 설정한다. 이 입력란에 `Bearer `를 한 번 더 붙이지 않는다. 직접 Headers에서 설정할 때는 `Authorization: Bearer {{accessToken}}`이다.

인증 확인은 **P01 GET** `{{baseUrl}}/api/me`로 한다. Body none이며, 응답 username·role을 확인한다. P02·P03·P04·P05와 S01~S04도 해당 JWT를 사용한다. C16 `/actuator/prometheus`는 이 JWT 대신 ADMIN Basic을 사용한다. accessToken은 OpenAI/Naver API 키도 아니다.

## 7. 보조 값과 전체 표

- `documentId`: P04 응답 또는 C05 `uploadedDocuments[].id`. 같은 intakeId와 함께 P05 다운로드 URL에 넣는다.
- `historyId`: S05 응답의 위험 이력 ID. C17의 `id`, C18의 `riskHistoryId`와 연결한다. incidentId와 구분한다.
- `sessionId`: 호출자가 정한 대화·비용 묶음의 문자열이다. intakeId·conditionId·searchRequestId 같은 DB PK와 구분한다.

[화면01~13 전체 API·Controller·Postman 매핑](SCREEN_API_CONTROLLER_MAPPING.md) · [운영자09~13 Import용 Postman 컬렉션](SCREEN09_13_POSTMAN_EXPERIMENTS.postman_collection.json).
