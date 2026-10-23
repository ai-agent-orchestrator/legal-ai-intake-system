# 화면05 React · API 대조 및 검수

> 2026-10-21 최신 [화면05~08 통합 재검수](SCREEN05_08_INTEGRATION_REVIEW.md): 요청·응답·ID·공통 React·라우팅·실제 API 연결 확인. 개발용 공개 API·C08 서버 검증 보완과 P03 실제 키 미검증을 구분했다. 아래 개별 회차 기록보다 최신이다.

> 2026-10-21 후속 P03 구현 완료: 실행·JWT 소유권·LLM 검색문·네이버 검색·저장·설명·진행 폴링을 연결했다. [최신 구현·검수](P03_EXTERNAL_SEARCH_REVIEW.md). 아래 처음 검수에서의 P03 비활성·미구현 설명과 수치는 당시 기록이다.

2026-10-21. 사용자 첨부 검색 요청·이력·결과 화면 기준. **P03은 개발 전이므로 실행 버튼만 비활성 상태로 표시하며 실행 API·폴링·가상 검색 성공 처리는 구현하지 않았다.** 기존 Spring API를 사용했고 이번 작업에서 Java 소스나 서버 인증 정책을 변경하지 않았다.

## 동작과 API 연결

| 동작 | API / React 함수 | 요청·ID 전달 | 성공 응답·화면 반영 | 인증 / 구현 |
|---|---|---|---|---|
| 접수 정보·저장 조건 | C05 `GET /api/intakes/{id}` / `api.intake` | 경로의 실제 문의 ID. 본문 없음 | 보호된 상대방·사건·지역, `lawyerSearchCondition` 표시 | 현재 공개 GET, 로그인 시 Bearer 선택 전송 / 연결 완료 |
| 검색 요청 기록 | C06 `POST /api/intakes/{intakeId}/search-requests` / `api.createSearch` | C05 문의 ID, `{searchPurpose, sourcePolicy}`. 정책은 C05에 저장된 값 | 201 전체 요청 DTO. 반환 `id`로 `#/intake/{intakeId}/search/{id}` 이동 후 C07 조회, P02 갱신 | 현재 공개 POST, Bearer 선택 / 연결 완료 |
| 요청 선택·직접 URL 복구 | C07 `GET /api/intakes/search-requests/{searchRequestId}` / `api.searchRequest` | C06 반환 ID 또는 P02 행 ID. 응답 `intakeId`와 라우트 문의 ID를 대조 | 200 요청·조건 ID·질의·상태·기록 결과. 과거 조건 스냅샷도 조회 | 현재 공개 GET, Bearer 선택 / 연결 완료 |
| 문의별 요청 이력 | P02 `GET /api/intakes/{intakeId}/search-requests` / `api.searchHistory` | 실제 문의 ID. 본문 없음 | 200 최신순 요청 DTO 배열. 선택 라디오로 C07 이동, 결과 수는 `results.length` | JWT 필수, 문의 소유자 또는 `ROLE_ADMIN`. 비회원 로그인 안내, 403 독립 표시 / 연결 완료 |
| 결과 링크 직접 기록 | C08 `POST /api/intakes/search-requests/{searchRequestId}/results` / `api.addSearchResult` | 선택된 실제 요청 ID와 아래 결과 필드. 문의 ID는 응답 부모 대조에 사용 | 201 **전체 갱신 요청 DTO**를 먼저 반영하고 C07 재조회, P02 갱신 | 현재 공개 POST, Bearer 선택 / 연결 완료 |
| 외부 검색 실행 | P03 제안 `POST …/{searchRequestId}/execute` | 호출 없음 | ‘외부 검색 실행 · 준비 중’ 비활성 버튼. 결과 생성·실행 중 표시·폴링 없음 | 백엔드 개발 전 / 프런트 준비 상태만 구현 |
| 복사·정렬·이전 이동 | 추가 API 없음 | 실제 선택 요청의 값만 복사. 저장 순서·최근 기록순 클라이언트 정렬 | Clipboard 실패 시 읽기 전용 복사 영역. 이전 `#/intake/{id}`, 문의 보기 `#/intake/{id}/review` | 같은 문의 ID 유지 / 구현 완료 |

C05/C06/C07/C08의 공개 정책과 P02의 소유자 인가는 다르다. 로그인 UI가 기존 공개 API 전체의 소유자 보호를 제공하는 것은 아니다. 익명으로 등록한 문의는 소유자가 없어 P02를 관리자만 조회할 수 있다. JWT는 탭 메모리에서만 유지하고 URL·저장소·복사 내용·증거 파일에 넣지 않는다.

## 요청·응답 필드

C06 본문은 서버에서 `{}`도 허용한다. `searchPurpose` 선택 300자, `sourcePolicy` 선택 100자이며 화면에서는 저장된 정책을 그대로 보낸다. 목적을 비우면 서버는 문의의 `lawyerSearchPurpose`, 그것도 없으면 `LAWYER_SEARCH_ROUTING`을 사용한다. 정책 기본값은 저장 조건의 `sourcePolicy`다. 정책 선택 목록은 현재 저장된 값 하나만 표시하여 미정의 실행 정책을 추가하지 않았다.

C06/C07/C08 및 P02 각 행의 공통 응답:

```text
id, intakeId, conditionId, searchPurpose, sourcePolicy,
status, searchQuery, results[], createdAt

results[]:
id, searchRequestId, sourceType, title, url, snippet,
displayOrder, createdAt
```

ID의 유효성·요청/문의 부모 연결·결과의 `searchRequestId`·배열·문자열·유효 시각·정수 순서·중복 ID를 검증한다. C07은 요청 ID를, P02는 각 행의 문의 ID를 확인한다. 현재 C05 조건과 과거 요청의 `conditionId`가 다르면 이전 조건 번호를 안내하며 정상 스냅샷을 차단하지 않는다. C05에서 사용하는 조건 필드는 `id, intakeId, legalField, disputeType, preferredRegion, officeType, experienceLevel, sourcePolicy, searchQuery`다. 카드에서 긴 값은 생략 표시하되 `title`과 원래 DOM 텍스트를 유지한다.

| C08 입력 | 서버 DTO | 프런트 검증·전송 |
|---|---|---|
| `sourceType` | 필수, 최대 100자 문자열. 서버 enum 아님 | 필수 선택. `LEGAL_INFORMATION`, `LAW_FIRM_SITE`, `CASE_LAW`, `PUBLIC_WEB`는 UI 입력값이며 기존 다른 서버 값도 그대로 읽어 표시 |
| `title` | 필수, 최대 300자 | 공백만 입력 차단, 앞뒤 공백 정리 |
| `url` | 필수, 최대 1,000자. 서버는 프로토콜 제한 없음 | HTTP(S)·호스트 필수. 사용자 정보 포함 URL, 공백·제어문자, javascript/file/data 등 차단 |
| `snippet` | 선택, 최대 1,000자 | 글자 수·상한 표시. 빈 값 생략. **디자인 예시의 500자 대신 실제 DTO 1,000자 적용** |
| `displayOrder` | 선택 Integer, 서버 @Min 없음 | 비우면 필드 생략하여 서버의 max+1 사용. 입력 시 1~2,147,483,647 정수로 제한. 기존 음수 기록은 읽기 가능 |

글자 수는 Java 문자열 검증과 맞춘 UTF-16 단위다. 기존 결과의 안전하지 않은 URL은 텍스트로만 표시하고 링크를 만들지 않는다. 정상 외부 링크는 새 창과 `noopener noreferrer`를 사용하며 JWT를 보내지 않는다. 링크 내용을 자동 수집하거나 검증하지 않는다.

서버 C06은 `PREVIEW_READY`를 저장하며 C08도 이 상태를 바꾸지 않는다. 따라서 결과가 있어도 ‘검색 준비 완료’가 유지되고 결과 건수는 별도 표시한다. `createdAt`은 요청 생성·링크 기록 시각이며 자료 발행일로 표시하지 않는다. 가상의 변호사 추천·검색 실행 완료 상태·예시 결과를 제품 코드에 넣지 않았다.

## React·라우팅·상태 처리

- `frontend/legal-procedure-viewer/src/client/IntakeSearchPage.jsx`: 검색 조건·C06 폼, 인증된 `SearchHistory`, 선택 요청과 `ResultLinkForm`. 공통 `IntakeHeader`, `Steps`, `Button`, `Feedback`, `useResource`, 로그인 모달을 재사용한다.
- `src/client/searchModel.js`: 결과 입력 규칙, 안전한 URL, 정렬·출처/상태 표기·복사 문자열. `src/client/api.js`: 기존 fetch 계층으로 경로·본문·선택/필수 JWT·응답 연결 검증을 처리한다.
- `IntakeApp.jsx`: `#/intake/{id}/search`, `#/intake/{id}/search/{requestId}`. 직접 접근 시 C05/C07로 복구하며 하드코딩한 업무 번호를 사용하지 않는다.
- `src/client/intake-search.css`: 첨부의 공통 사무실 배경·브랜드·파랑 단계 표시, 좌측 조건/폼/이력과 우측 상세/결과/직접 입력 구성을 반영했다. 1,000px 이하 단일 열, 모바일 입력 단일 열, 이력 표 내부 가로 스크롤을 제공한다.
- C05·C07·P02의 로딩·오류·빈 상태와 재시도를 분리했다. 다른 요청 ID로 이동하면 이전 결과를 즉시 숨기고 오래된 GET을 취소한다. 페이지 이탈은 진행 중 요청을 취소한다.
- 쓰기 중 생성·링크 저장·이력 선택을 잠그며 ref로 중복 POST를 차단한다. C08은 ‘결과 저장 중’을 표시한다. 성공 응답 후 폼을 초기화하고, 실패 시 입력을 보존한다.
- C08 저장 응답 성공 뒤 C07 갱신만 실패하면 마지막 확인 결과와 저장 성공을 유지한다. 재시도는 GET만 수행한다.
- 네트워크·서버 오류·잘못된 성공 응답으로 저장 여부가 불명확하면 자동 재전송하지 않는다. 최신 이력/결과 조회를 시도하고 사용자가 중복 가능성을 확인한 뒤 편집을 재개한다. 서버에 멱등 키가 없어 재조회만으로 중복을 완전히 배제할 수 없다. 생성 응답 유실 후 P02 조회는 로그인·소유권이 필요하다.

## 검수 결과와 증거

| 검수 | 결과 |
|---|---|
| `npm run build` | viewer/operator/intake 진입점, 88개 모듈 빌드 성공 |
| `npm run test:intake` | **106/106 통과**. 기존 흐름 + 화면05 API/모델 6개와 React 8개. 필수·한도·URL·ID·인증·정렬·과거 조건·로딩/빈/오류·쓰기 중복·응답 유실·GET 재시도·이탈 취소 포함 |
| `npm run test:intake:live` | **15/15 통과**. 실제 로컬 Spring. 화면05 생성 2건, 결과 3건, P02 소유자/관리자 200·비회원 401·비소유자 403, 입력 400, C07 부모 불일치 차단. 서버 복구 후 다시 통과 |
| `npm run test:intake:preview` | **1/1 통과**. 최신 빌드 정적 진입점과 실제 API 프록시. 서버 복구 후 다시 통과 |
| 기존 운영자 회귀 `test:operator` | sandbox에서 11/18 통과. 로컬 통신이 필요한 7개 실패. 권한을 허용한 재실행 요청은 사용자 거절로 수행하지 않음. **이번 회차 전체 통과로 집계하지 않음** |
| 실제 브라우저 | 데스크톱 1672×941·모바일 390×844. C06 반환 ID 이동, C08 직접 입력/저장/폼 초기화, 이력·정렬·복사, 필수·위험 URL 차단, 빈 결과, 화면04 왕복, P03 비활성 확인 |

화면05와 공유 intake 검수 범위는 **106 + 15 + 1 = 122개 통과**다. 이전 화면 문서의 97/125개는 당시 회차 기록이다. 실패 주입·응답 유실·지연·취소는 React/API 테스트로 검증했으며 실제 브라우저에서 모두 재현했다고 주장하지 않는다.

검수 중 기존 Spring JVM이 종료 코드 `0xC0000005`로 중단되었다. 화면은 API 오류·재시도를 표시했고, 서버 재실행 후 실제 API 검수와 최종 화면 확인을 반복했다. 원인 진단·JDK 변경은 이번 프런트 작업 범위에 포함하지 않았다. 현재 H2 `jdbc:h2:mem:…`이므로 JVM 종료 때 이전 데이터가 소실되었으며 최종 증거는 다시 생성한 가상 데이터다.

- [실제 API 증거](../output/intake-05/api-evidence.json): 최종 문의 1, 조건 1, 요청 1·2, 결과 1·2·3. 비밀키·토큰 없음. H2 재시작 시 ID는 달라질 수 있다.
- [데스크톱 캡처](../output/intake-05/screen05-live-desktop.png), [모바일 입력 캡처](../output/intake-05/screen05-mobile.png).
- [브라우저 검수 기록](../output/intake-05/browser-evidence.json): 서버 복구 후 문의 1에서 요청 4를 새로 만들고 결과 링크 1건을 직접 저장했다. 최종 데스크톱에는 요청 4·2·1 이력과 선택 요청 1의 결과 3건이 표시된다.
- 최종 확인 URL: `http://127.0.0.1:5175/intake.html#/intake/1/search/1`. 새 탭·새로고침은 비회원이므로 P02 이력은 로컬 검수 계정 로그인 후 확인한다.
- 결과 URL은 모두 `example.com`의 개발용 가상 참고 링크이며 외부 사이트에 접속하거나 P03을 호출하지 않았다.

백엔드 대조 근거: `LegalIntakeRecordController`, `IntakeSearchHistoryController`, `LegalIntakeRecordService.createSearchRequest/findSearchRequest/addSearchResult/findSearchRequests`, `IntakeAccessService`, `AiSearchRequestCreateRequest`, `AiSearchRequestResponse`, `SearchResultSnapshotRequest/Response`, `SecurityConfig`.
