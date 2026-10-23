# P03 실제 외부 검색 · LLM 검색문/설명 구현과 검수

> **최신 변경 2026-10-22:** 네이버 일반 웹 검색과 `klaw.or.kr` 대상 검색을 합치는 경로를 추가했다. 백엔드 전체 185개·프런트 137개 검사와 두 빌드가 통과했다. 실제 제공자 키를 사용한 검색은 미검증이다. 아래 이전 회차의 167/169개 수치·브라우저 검수 기록과 이번 결과를 구분한다.

## 2026-10-22 변협 공개 페이지 + 일반 웹 혼합 검색

이번 요청은 기존 네이버 검색을 유지하면서 변협 출처를 함께 찾도록 보완하는 것이다. **변협 전용 API·직접 프로필 크롤러를 추가하지 않고, 같은 네이버 웹문서 API를 두 번 호출한다.** `site:`는 검색어 힌트이며 웹문서 API에서의 실제 결과는 운영 키로 확인해야 한다. 반환된 URL을 다시 검사하므로 연산자가 무시되더라도 일반 페이지가 변협 출처로 분류되지는 않는다.

```text
기존 문의·검색 요청 ID와 소유권 확인
→ 기존 LLM 검색어 1회 생성
→ MixedPublicWebSearchClient
   ① 검색어 + site:klaw.or.kr (변협 '나의 변호사' 공개 페이지 대상)
   ② 검색어 (일반 웹)
→ URL 출처 검사·중복 제거·변협/일반 결과 교대로 배치
→ 기본 최대 3건을 같은 searchRequestId에 저장
→ 기존 결과별 관련성 설명·Guard·비용 정산
→ C07 재조회·화면05 출처 및 부분 조회 실패 안내
```

| 항목 | 변경 후 동작 |
|---|---|
| 검색 제공자 | 기존 Naver `GET /v1/search/webkr.json`, 동일 Client ID/Secret, 기본 요청당 2회·자동 재시도 없음 |
| 변협 대상 | 서버가 `site:klaw.or.kr`를 붙인다. 실제 URL이 `klaw.or.kr`, `koreanbar.or.kr` 또는 해당 하위 도메인인지 확인한다. 제목의 '변협' 문구나 경로 문자열만으로 판정하지 않는다. |
| 출처 필드 | 실제 변협 도메인 결과 `sourceType=KBA_PUBLIC_WEB`, 나머지 `NAVER_WEB`. 둘 다 네이버 인덱스에서 수집한다. `execution.provider=NAVER_WEB` 유지. |
| 중복·정렬 | URL fragment·일반 추적 파라미터·www·후행 슬래시 차이를 정리해 중복을 제거한다. 프로필 식별 query 값은 보존한다. 변협 결과를 먼저 배치하고 일반 결과와 번갈아 선택한다. 양쪽 결과가 충분하면 기본 3건은 변협 2건·일반 1건. |
| 개수·LLM 비용 | `search.max-results` 기본 3·허용 1~5 유지. 결과 3건 기준 기존 검색어 1회+설명 3회이며, 검색 분기에 따른 LLM/Guard 단계 추가 없음. |
| 변협 색인 결과 없음 | 일반 결과로 채운다. 실제 변협 프로필을 만들거나 홈페이지 링크를 검색 적중 결과처럼 저장하지 않는다. 화면의 별도 '변협에서 직접 확인' 링크를 제공한다. |
| 한쪽 검색 실패 | 다른 쪽에 유효한 결과가 있으면 저장·설명을 계속하고 `execution.sourceWarningCode`를 저장한다. 재조회·설명만 재생성 후에도 안내 유지. |
| 양쪽 실패 또는 실패+빈 응답 | 고정 오류 코드로 FAILED. 조회하지 못한 출처가 있는데 NO_RESULTS로 표시하지 않는다. 양쪽 정상 빈 응답만 NO_RESULTS. |
| 개인정보·외부 접근 | 기존 안전한 조건·검색문만 전송한다. 수집된 프로필 URL을 서버가 새로 방문하거나 LLM이 이름·경력·자격을 만들어 넣는 동작은 추가하지 않는다. |

기존 P03/C07/P02 경로·요청 필드·문의/조건/검색 요청 ID 연결·인증 정책은 유지한다. 신규 API 경로는 없다. `execution.sourceWarningCode`는 nullable 추가 필드이며 다음 두 값만 사용한다.

- `KBA_SEARCH_UNAVAILABLE`: 변협 대상 조회 실패. 일반 검색에서 수집한 결과 표시.
- `GENERAL_SEARCH_UNAVAILABLE`: 일반 웹 조회 실패. 변협 대상 검색에서 수집한 결과 표시.

화면05는 `대한변협 공개 페이지 · 네이버 수집`과 `네이버 공개 웹 검색`을 구분한다. 수동 C08 데이터가 변협 sourceType을 주장하더라도 URL 도메인이 맞지 않으면 공식 출처 표시를 하지 않는다. 출처가 공식 도메인이라는 뜻이며, 모든 결과가 개별 변호사 프로필이거나 최신 자격·경력 검증을 마쳤다는 뜻은 아니다.

### 검증·적용 범위

- [x] 혼합·우선 배치·최대 개수·같은 프로필 중복 제거·서로 다른 프로필 ID 보존.
- [x] 유사 도메인·URL 사용자 정보·경로에 공식 도메인을 적은 위장 결과 제외.
- [x] 변협/일반 한쪽 장애 대체·명시적 안내·취소 후 다른 검색 미실행.
- [x] 양쪽 정상 빈 응답과 일부 조회 실패를 구분.
- [x] 실제 HTTP 어댑터를 로컬 모의 제공자에 연결하여 출처별 저장·동일 ID·C07 재조회·설명 재시도 시 안내 유지 확인.
- [x] 백엔드 전체 `185/185`, 실패·오류·skip 0, `bootJar` 성공. 프런트 `137/137`, Vite build 성공. React 렌더 검사에서 출처 표시·부분 조회 안내·직접 확인 링크 검증.
- [ ] 실제 네이버 키로 `site:klaw.or.kr` 검색 적중률·결과의 지역/분야 관련성·프로필 포함 여부 확인. 실제 LLM·네이버 호출 및 이번 변경의 브라우저 직접 검수는 수행하지 않았다.

근거: `output/p03-mixed-search/verification.json`, 같은 폴더의 검색 관련 JUnit XML. 기존 사용자 서버는 이번 변경에서 직접 재시작하지 않았다. 새 백엔드를 적용해야 혼합 검색이 실행된다. 새 nullable DB 열은 `ai_search_request.source_warning_code VARCHAR(100)`이며 현재 MySQL 프로필의 `ddl-auto=update`가 기동 시 반영한다. 모델·단가·예산·키 설정은 이번 변경 대상이 아니다.

아래는 최초 구현·이전 검수 기록이며, 제공자 경로·출처 설명은 이번 혼합 검색 기준으로 갱신했다.

2026-10-21 (Asia/Seoul). **연결 코드와 로컬 모의 제공자 검증 완료. 실제 LLM·네이버 운영 키를 사용한 외부 검색은 미검증**이다. 사용자가 키 미설정 상태에서 구현·모의 검증 진행을 선택했다. 결과 화면의 `example.com` 링크는 모의 검수 자료다.

## 구현 흐름

`C05 문의의 저장 조건 → C06 검색 요청 생성 → P03 실행 접수 → LLM 검색문 생성 → 변협 대상/일반 네이버 웹문서 검색 → 병합 → 기존 요청 ID에 결과 저장 → LLM 결과별 관련성 설명 → C07 조회·화면05 갱신`

- 새 실행 API: `POST /api/intakes/search-requests/{searchRequestId}/execute`, **202 Accepted**. C06으로 먼저 만든 요청을 사용한다.
- 서버의 `SearchExecutionController → SearchExecutionService → SearchExecutionStore / SearchLlmService / MixedPublicWebSearchClient → NaverWebSearchClient`를 연결했다.
- 외부 I/O는 DB 트랜잭션 밖에서 수행한다. 요청 행을 잠근 짧은 트랜잭션으로 실행을 선점하고, 조건·문의 연결을 검사한다.
- 기본 worker 2개·대기열 16개. 기본 전체 작업 제한 180초. 큐 포화는 429이며 실행 선점을 되돌린다.
- 페이지 이동·브라우저 종료는 서버 작업을 취소하지 않는다. 화면 재진입 시 C07로 현재 상태를 조회한다. SSE의 연결 종료 취소 정책과 구분한다.

## API · 필드 · ID · 인증 대조

| 동작 | 요청 | 응답·사용 필드 | ID·인증·구현 |
|---|---|---|---|
| 조건 조회 C05 | `GET /api/intakes/{id}` | `lawyerSearchCondition`, 사건·분쟁·지역 | 문의 ID로 저장 조건 표시. 기존 공개 경로 |
| 요청 기록 C06 | `POST /api/intakes/{intakeId}/search-requests`, `{searchPurpose?,sourcePolicy?}` | 기존 요청 DTO, `id`, `intakeId`, `conditionId`, `status=PREVIEW_READY` | 반환 요청 ID로 화면 이동. 조건 ID는 서버 결정. 기존 공개 경로 |
| 실행 P03 | `POST /api/intakes/search-requests/{searchRequestId}/execute`, 본문 생략·`{}`·`{retryExplanation:false}` | 202 기존 DTO + `execution`, 현재 `status`, `searchQuery`, `results` | **Bearer JWT 필수, 문의 소유자 또는 ROLE_ADMIN**. 경로 요청 ID에서 문의·조건을 역조회. 본문 ID는 사용하지 않음 |
| 설명만 재생성 P03 | 같은 경로, `{retryExplanation:true}` | PARTIAL의 저장된 결과 중 설명이 없는 항목만 처리 | 검색문 생성·외부 검색 재실행 없음. 결과 ID·실행 batch ID 유지, 내부 lease 교체 |
| 진행·결과 조회 C07 | `GET /api/intakes/search-requests/{searchRequestId}` | `execution.id/stage/provider/errorCode/startedAt/finishedAt/sourceWarningCode`, 상태·실제 검색어·결과 | 화면은 URL의 문의 ID와 응답 `intakeId`, 요청 ID와 `id`, 결과의 `searchRequestId`를 대조. 기존 공개 조회 정책 유지 |
| 이력 P02 | `GET /api/intakes/{intakeId}/search-requests` | 요청·결과 배열 | JWT·소유자/관리자. 실행 접수·종료 시 이력 갱신 |
| 직접 결과 기록 C08 | `POST …/{searchRequestId}/results`, 기존 sourceType/title/url/snippet/displayOrder | 기존 DTO + 결과 | 기존 공개 쓰기 정책 유지. P03 실행 중 동일 행 잠금·409. 완료 후 수동 결과 추가 가능 |

`SearchResultSnapshotResponse`에 nullable `explanation`을 추가했다. `snippet`은 제공자 요약, `explanation`은 AI가 생성한 관련성 설명이다. `sourceType=NAVER_WEB/KBA_PUBLIC_WEB`, `title`, `url`, `snippet`, `displayOrder`, `createdAt`은 실제 수집 결과를 저장한다. 설명 모델이 제목·URL을 교체할 수 없도록 분리했다. 수동 기록과 수집 결과는 별도 레코드이며, 한 수집 batch 내 중복 URL은 제거한다.

### 실행 상태

| 상태 | 의미·다음 동작 |
|---|---|
| PREVIEW_READY | 아직 실행하지 않음 |
| QUEUED / RUNNING | 선점·실행 중. 중복 POST는 현재 DTO만 반환 |
| COMPLETED | 수집·설명 완료. 중복 POST로 재검색하지 않음 |
| NO_RESULTS | 유효한 빈 검색 응답. 설명 호출 없음. 조건을 조정하여 새 요청 생성 |
| FAILED | 결과 저장 전 실패. 고정 errorCode 제공. 다시 검색하려면 새 C06 요청 생성 |
| PARTIAL | 수집 결과는 저장됨. 설명 생성·Guard·비용·timeout 등 실패. `retryExplanation:true`로 미완성 설명만 재생성 |

단계: `CONDITION_CHECK → QUERY_GENERATION → EXTERNAL_SEARCH → RESULT_PERSISTENCE → EXPLANATION → FINISHED`. 실패 시 마지막 단계와 고정 오류 코드가 남는다. 실행별 lease가 일치하고 실행 상태일 때만 DB를 변경하므로, timeout 이후 늦은 콜백이 종료 상태·새 설명 작업을 덮어쓰지 않는다. 지속 DB 사용 시 서버 시작 시점의 중단 작업은 FAILED/PARTIAL로 표시하며 자동 재검색하지 않는다. **현재 실행기는 단일 서버용**이며 분산 작업 큐·재시작 시 이어 실행은 구현 범위 밖이다.

## LLM·외부 검색 연결

- 검색문: 기존 OpenAI 호환 `LlmClient.chat()`를 재사용한다. `{"query":"…"}`만 허용하며 법률 분야 포함, 길이·문자·PII 패턴을 검증한 뒤 검색 API에 전달한다.
- 조건: 서버가 정한 법률 분야·분쟁 분류, 넓은 시도 단위 지역, 허용된 사무소·경력·상담 선호만 전달한다. **문의 원문, 이름, 연락처, 상세 주소, 세션·문의·요청 ID, 자유 입력 검색 목적은 외부 프롬프트·검색문 입력에서 제외**한다. 세부 지역은 현재 시도 수준으로 축소된다.
- 네이버: `GET https://openapi.naver.com/v1/search/webkr.json?query=…&display=3&start=1`. 서버 전용 `X-Naver-Client-Id`, `X-Naver-Client-Secret` 헤더. 제공자의 `items[].title/link/description`을 각각 제목·URL·요약으로 저장한다. [공식 웹문서 검색 계약](https://developers.naver.com/docs/serviceapi/search/web/web.md)을 기준으로 구현했다.
- 저장된 `KBA_NAVER_PUBLIC_WEB` 정책은 네이버를 통한 변협 공개 페이지 대상 검색 + 일반 웹 검색의 병합에 매핑한다. **변협 전용 API·자격·경력 검증은 아니다.** 다른 정책은 422로 차단한다.
- 네이버 응답 본문 1 MiB 제한, 기본 10초 timeout, redirect 금지. 기본 3개·설정 상한 5개. HTML을 텍스트로 바꾸고 알려진 PII 패턴을 마스킹한다. 부적절한 URL·중복 결과를 제거한다. 전부 잘못된 응답은 빈 성공으로 처리하지 않는다.
- 결과 URL은 서버가 방문·스크래핑하지 않는다. 모델에는 제목·요약만 참고 데이터로 전달한다. 입력 데이터 안의 지시를 따르지 않도록 프롬프트를 구성한다.
- 설명: 결과별로 제목·요약과 안전한 조건을 전달하여 관련성 2문장을 생성한다. Guard 수정 결과를 다음 단계에 전달하고, 출력 Guard·PII 마스킹 후 저장한다. 화면에는 **AI 관련성 설명 · 원문 확인 필요**로 구분하여 텍스트 렌더링한다.

## 비용·오류 처리

- 검색문 `SEARCH_QUERY`, 설명 `SEARCH_EXPLANATION`을 기존 비용 장부에 추가했다. 운영자 비용 단계 표시에도 연결했다. 입력·출력 Guard 비용도 기존 공통 처리로 정산한다.
- 사용자별 안정적인 내부 비용 세션 `search:{username}`을 사용하여 요청을 새로 만들 때마다 세션 한도가 초기화되는 것을 막는다.
- LLM 발송 전 예약, 보고된 usage는 실제 토큰으로 정산, 발송 후 오류·usage 미확인은 예약 추정액으로 정산한다. 발송 전 취소만 해제한다. 파싱 실패·출력 차단 시에도 이미 사용한 비용을 남긴다. 예약 종료·날짜 귀속은 [SSE 비용 상태 관리](SSE_COST_LIFECYCLE_REVIEW.md)를 재사용한다.
- 키 미설정·기능 비활성은 실행 선점·유료 호출 전에 503. 인증 401, 소유권 403, 대상 없음 404, 정책 미지원 422, 큐 포화 429.
- 원격 오류 본문·키·프롬프트를 사용자 오류에 포함하지 않는다. 고정 오류 코드로 표시한다.
- **현재 비용 장부는 메모리 기반**이다. 실제 청구 대사·추정액 사후 보정·서버 재시작을 넘는 비용 원장은 후속이다.

## React·라우팅

공통 Header·Steps·Button·Feedback·기존 화면05 레이아웃을 재사용했다. 경로는 `#/intake/{intakeId}/search/{searchRequestId}`를 유지한다.

- 실행 버튼 활성화, 로그인 필요 시 기존 모달 표시. 선택한 요청이 없거나 조회 실패·실행 중·종료 상태이면 실행을 제한한다.
- 상태·단계·모델 설명을 기존 요청 상세에 표시한다. QUEUED/RUNNING일 때만 1.5초 간격으로 C07을 조회한다. 조회가 실패하면 마지막 확인 데이터를 남기고 명시적 재시도를 제공한다.
- 경로 변경·언마운트는 오래된 조회/진행 갱신을 취소한다. POST를 자동 재전송하지 않는다. 응답이 불확실하면 기존 최신 저장 정보 확인 흐름을 사용한다.
- 진행 중 직접 결과 기록 폼을 잠근다. 설명 실패는 수집된 링크를 지우지 않으며, 설명만 재생성 버튼을 제공한다.
- 빈 결과·외부 오류·예산 차단·잘못된 검색문·timeout 등을 각각 안내한다. 설정 누락은 서버 LLM·네이버 설정 필요 메시지로 표시한다.

## 실행 설정

서버 환경 변수에 `LLM_API_KEY`, `NAVER_SEARCH_CLIENT_ID`, `NAVER_SEARCH_CLIENT_SECRET`을 설정하고 서버를 재시작한다. 기존 `LLM_BASE_URL`, `LLM_MODEL`, 요금 단가·예산도 실제 사용 모델에 맞춰 설정한다. **프런트 VITE 변수나 저장소에 비밀값을 넣지 않는다.** 기존 입력·출력 Guard 서비스도 `NEMO_GUARDRAILS_URL`에서 정상 실행되어야 한다. Guard 장애 시 안전하게 실패 처리한다.

선택 설정: `SEARCH_ENABLED`, `SEARCH_MAX_RESULTS`(1~5), `SEARCH_REQUEST_TIMEOUT_MS`(100~60000), `SEARCH_EXECUTION_TIMEOUT_MS`(100~600000). 네이버 기본 URL은 공식 HTTPS다. loopback HTTP override는 로컬 모의 검수용이다.

기본 H2는 재시작 시 데이터가 삭제된다. 기존 MySQL 실험 프로필은 `ddl-auto=update`로 nullable 실행·설명 컬럼을 추가하지만, **이번 MySQL 실행·운영 DB 마이그레이션은 검증하지 않았다.** 운영 배포에는 별도 스키마 이행 검토가 필요하다.

## 검증 결과

| 항목 | 결과 |
|---|---|
| 백엔드 전체·실행 JAR | **167개 통과 / 실패·오류·건너뜀 0**, 실행 JAR 빌드 통과. [검증 JSON](../output/p03-search/verification.json) |
| 새 P03 서버 테스트 | 실제 로컬 HTTP LLM·네이버 어댑터, 필드·헤더·ID·인증, 정상·빈 결과·원격 오류·잘못된 검색문, 중복 POST·동시 수동 쓰기, 저장 후 설명 실패·설명만 재시도, timeout·늦은 응답, 응답 크기·URL·PII, 예산 차단·Guard 수정 반영 |
| React·API | `npm run test:intake`: **135개 통과**. P03 7개 포함. `npm run test:operator`: **18개 통과** |
| 프런트 빌드 | `npm run build` 통과 |
| 실제 Spring HTTP + 모의 제공자 | `npm run test:p03:local`: **3개 통과**. 정상+멱등·빈 응답+오류·설명 복구. [호출 순서·ID 결과](../output/p03-search/local-http.json) |
| 브라우저 | 실제 React+Spring+로컬 모의 제공자. 로그인 요구, 실행 잠금·자동 갱신, 결과·설명 분리, 이력 ID 이동, NO_RESULTS·FAILED 안내 확인. 1672×941 / 390×844, 가로 넘침 없음. 캡처 당시 콘솔 오류·경고 없음. [브라우저 기록](../output/p03-search/browser.json) |
| 실제 유료 제공자 | **미실행**. 키 설정 후 실제 네이버 응답·LLM 모델 호환·쿼터·요금·Guard 정책을 확인해야 함 |

모의 HTTP 재현은 `scripts/p03-mock-providers.mjs`(:18181), 격리 Spring(:18083), React(:5185)를 사용한다. 검수 종료 후 해당 임시 서버를 정리했다. 기존 사용자의 개발 서버와 DB는 이 모의 검수에 사용하지 않았다.

![P03 모의 검수 완료 화면](../output/p03-search/browser-completed.png)
