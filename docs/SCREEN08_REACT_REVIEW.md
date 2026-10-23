# 화면08 첨부 기준 · 문의 상세 API·React 구현 검수

> 2026-10-21 최신 [화면05~08 통합 재검수](SCREEN05_08_INTEGRATION_REVIEW.md): 요청·응답·ID·공통 React·라우팅·실제 API 연결 확인. 개발용 공개 API·C08 서버 검증 보완과 P03 실제 키 미검증을 구분했다. 아래 개별 회차 기록보다 최신이다.

검수일: 2026-10-21, Asia/Seoul. 사용자 화면08 첨부의 문의 상세 디자인을 기준으로 작성했다. 기존 기능표의 화면07 C05 상세 기능을 확장했고, 첨부 화면07의 상태·메모 UI(C10/C11)는 [별도 검수표](SCREEN07_REACT_REVIEW.md)에 연결한다. 경로·API 식별자는 그대로 유지한다.

## 실행·결과

- 경로: `http://127.0.0.1:5175/intake.html#/lawyer/intakes/{intakeId}`
- 실제 가상 문의 검수: `#/lawyer/intakes/6` (H2가 재시작되면 개발 데이터가 달라질 수 있음)
- 목록 C09의 실제 행 ID로 진입하며, 목록 복귀 시 필터를 유지한다.
- 사건 개요·사실관계·핵심 내용·보유/추천 자료·접수 안내·저장된 변호사 탐색 조건·첨부파일·최근 메모를 C05 응답으로 표시한다.
- ‘상태 · 상담 메모 관리’와 ‘메모 관리하기’는 같은 문의 ID의 `#/lawyer/intakes/{id}/consultation`으로 이동한다.
- 파일의 ID/부모 문의 ID·이름·MIME·크기·URL을 검증하고, JWT 헤더로 파일을 받아 실제 다운로드한다.

## 각 동작의 API·요청·응답·ID·인증 대조

| 동작 | API / 요청 | 응답·ID 전달 | React → Controller → Service | 구현·인증 |
|---|---|---|---|---|
| 상세 진입·페이지 재로딩 | C05 `GET /api/intakes/{id}`, body/query 없음 | 200 `LegalIntakeResponse`; C09 행 `id` → hash route → path. 응답 ID 일치 검사 | `IntakeDetailPage` → `api.detailIntake` → `LegalIntakeRecordController.findById` → `LegalIntakeRecordService.findById` → `toIntakeResponse` | 실제 연결 완료. 현재 공개 GET, 로그인 시 선택적 Bearer |
| 실패 후 재조회 | 같은 C05 GET | 로딩·404/403/5xx/형식 오류 안내·재시도 | 공통 `useResource`/`Feedback` | 이전 요청 abort, 다른 ID 응답이 현재 상세를 덮지 않음 |
| 접수 안내·탐색 조건·자료·최근 메모 | 추가 API 없음 | C05에 저장된 각 필드와 중첩 DTO | 전용 읽기 컴포넌트 | C04/Reference/LLM을 불필요하게 호출하거나 새 결과를 생성하지 않음 |
| 추가 탐색 조건 보기 | 추가 API 없음 | C05의 긴 검색어 전체·경력·상담 방식·출처 정책 | native `details/summary` | 키보드 접근 가능한 펼침/접기 |
| 파일 다운로드 | P05 `GET /api/intakes/{intakeId}/documents/{documentId}/download`, body/query 없음 | 200 binary, 서버의 Content-Type/Length·Content-Disposition·no-store·nosniff. 부모와 파일 ID를 각각 사용 | `Attachments` → `api.downloadDocument` → `IntakeDocumentController.download` → `IntakeDocumentService.download` → `IntakeAccessService.requireAccess`/파일 소속 검사/다운로드 이력 저장 | **JWT 필수, 문의 소유자 또는 ROLE_ADMIN**. 파일 저장 이벤트까지 확인 |
| 비회원의 다운로드 버튼 | C01 로그인 모달 | `{username,password}` → `{accessToken,username,role}` | 기존 `LoginDialog`/`api.login`/AuthController | 로그인 전 파일 GET은 보내지 않음. 로그인 후 사용자가 다운로드를 다시 누름 |
| 상태·메모 관리 이동 | `#/lawyer/intakes/{id}/consultation` | 문의 ID 유지; 메모 ID를 문의 ID로 사용하지 않음 | `IntakeApp` → `IntakeConsultationPage` | C05/C10/C11 구현을 재사용. 자세한 쓰기 계약은 화면07 검수표 참조 |
| 목록으로·공통 메뉴 | `#/lawyer/intakes`, 의뢰인/운영자/도움말 경로 | 공통 routing | `IntakeHeader`, `LawyerShell`, `IntakeApp` | 기존 공통 디자인·계정 상태 재사용 |

업로드 P04는 기존 서버 기능으로 실제 다운로드 테스트 자료를 준비할 때만 사용했다. 첨부 화면에는 업로드 입력이 없으므로 화면08에 파일 업로드 UI까지 구현했다고 집계하지 않는다.

## C05 요청·응답 필드 전체

요청은 양의 문의 ID path만 사용한다. `sessionId`, 검색 조건 ID, 메모 ID, 파일 ID로 문의를 조회하지 않는다. `Accept: application/json`, `cache:no-store`, `credentials:omit`과 선택적 `Authorization: Bearer {token}`을 사용한다.

`LegalIntakeResponse` 25개 필드:

```text
id, sessionId, clientNameMasked, clientPhoneMasked,
inquiryPurpose, caseType, caseTypeLabel, partyPosition,
incidentDate, counterpartMasked, amountOrDamage, currentStage,
coreStatementMasked, desiredNextStep, preferredRegion, disputeArea,
selectedDocuments[], recommendedDocuments[], lawyerSearchCondition,
clientGuideMessage, privacyNotice, status, createdAt,
consultationMemos[], uploadedDocuments[]
```

| 화면 | 사용 필드·처리 |
|---|---|
| 제목·상태·접수일시 | 실제 `id`·`status`·`createdAt`; 1042/과거 예시 날짜를 하드코딩하지 않음 |
| 사건 개요 | `caseType` 표준 라벨, `disputeArea`, `inquiryPurpose`, `desiredNextStep`, `partyPosition`, `currentStage` |
| 사실관계 | `incidentDate`, `counterpartMasked`, `amountOrDamage`, `coreStatementMasked`. 표준 날짜는 점 구분으로 표시하고 기타 문자열은 그대로 표시 |
| 보유 자료 | `selectedDocuments`의 실제 선택. 선택 종류와 업로드된 파일 개수를 구분 |
| 추천 자료 | `recommendedDocuments`. 완료 상태 필드가 없어 준비 완료 체크로 저장하지 않음 |
| 접수 안내 | 저장된 `clientGuideMessage`. 새 분석/법적 판단 결과로 설명하지 않음 |
| 변호사 탐색 조건 | 아래 `lawyerSearchCondition` 중첩 DTO |
| 첨부파일 수·행 | `uploadedDocuments.length`, 각 실제 첨부 DTO |
| 상담 메모 | `consultationMemos`를 실제 createdAt·id로 정렬해 최근 5건. `contentMasked`만 표시 |

`lawyerSearchCondition`의 12개 필드:

```text
id, intakeId, legalField, disputeType, preferredRegion, officeType,
experienceLevel, publicCareerPreference, consultationStyle,
sourcePolicy, searchQuery, createdAt
```

법률 분야·분쟁 유형·선호 지역·사무소 유형·경력 선호·저장된 검색어를 표에 표시한다. 긴 검색어는 요약하고 ‘추가 탐색 조건 보기’에서 전문과 나머지 조건을 확인한다. **C05에는 검색 목적 필드가 없어 searchQuery를 ‘검색 목적’으로 바꾸어 표시하지 않는다.**

`consultationMemos[]`의 5개 필드:

```text
id, intakeId, contentMasked, followUpItems[], createdAt
```

작성자 필드가 없어 현재 로그인 계정이나 첨부 예시의 변호사 이름을 과거 메모 작성자로 표시하지 않는다. 메모 ID·서버 생성 시각·보호 처리된 내용·실제 후속 항목을 표시한다. null 탐색 조건, 빈 추천 자료·첨부·메모는 각각 빈 상태로 안내한다.

`uploadedDocuments[]`의 8개 필드:

```text
id, intakeId, documentType, originalFilename, contentType,
sizeBytes, createdAt, downloadUrl
```

파일명, 실제 MIME에서 정한 PDF/PNG/JPEG/TXT 라벨, 실제 bytes에 따른 B/KB/MB, 서버 업로드 시각을 표시한다. 선택 자료 3개를 파일 3개로 간주하지 않는다. `clientNameMasked/clientPhoneMasked/sessionId/privacyNotice` 등 C05에 있지만 이 디자인에 별도 위치가 없는 필드를 다른 의미로 끼워 넣지 않는다.

## 다운로드 구현·인가

1. C05의 파일 부모 ID가 현재 문의 ID와 같고, downloadUrl이 `/api/intakes/{id}/documents/{fileId}/download`와 정확히 같은지 검사한다.
2. 실제 요청 path는 검증된 문의 ID와 파일 ID로 다시 구성한다. 응답의 임의 외부 URL을 따라가거나 token을 URL/query에 넣지 않는다.
3. 로그인 token은 메모리에 두고 `Authorization` 헤더로만 전송한다. Blob 요청은 `Accept:*/*`, `cache:no-store`, `credentials:omit`을 사용한다.
4. 200 body의 MIME·byte 길이가 파일 메타데이터와 같은지 검사한다. HTML 성공 페이지나 잘못된 길이를 파일로 저장하지 않는다. MIME/확장자·파일명 제어문자·경로·부모 ID·중복 파일 ID도 검증한다.
5. 파일명을 Windows 경로/예약 이름에 맞게 정리한 뒤 object URL과 download link로 저장한다. object URL은 해제한다. 동시 다운로드 버튼을 잠그고 페이지 이동·계정 변경 시 요청을 취소한다.
6. ‘다운로드를 시작했습니다’로 표시한다. 브라우저가 저장을 끝냈는지 서버 응답만으로 단정하지 않는다.

파일 오류는 첨부 영역에 표시하고 사건 개요 등 C05 조회 결과는 유지한다. 401/403/404·네트워크·15초 timeout·형식 오류를 공통 API에서 처리한다. 오류 시 자동 다운로드 재시도는 하지 않는다.

실제 서버 테스트는 USER 소유 문의의 다운로드와 ADMIN 다운로드 성공, 비회원 401, 비소유자 403, 없는 파일 404를 확인했다. 브라우저에서 C01 로그인 후 `화면08-검수.txt`를 저장했으며, 실제 다운로드 경로의 파일 내용이 개발 검수용 31 bytes와 일치했다.

**C05 조회와 C10/C11 쓰기는 현재 공개 API인 반면 파일 다운로드는 JWT+소유자/관리자 인가가 적용된다.** 변호사 역할이나 담당 문의 인가를 새로 구현한 것으로 설명하지 않는다. 내부 메모의 비공개 제공 정책과 C05 전체 권한 제한은 서버 보강 사항이다.

## React·공통 디자인·라우팅 파일

| 파일 | 역할 |
|---|---|
| `src/client/IntakeDetailPage.jsx` | 전체 상세·개요/사실/자료/탐색/첨부/메모 컴포넌트, 파일별 다운로드 상태 |
| `src/client/intakeDetailModel.js` | MIME/파일명/path/표시 크기·브라우저 저장 helper |
| `src/client/api.js` | `detailIntake`, `downloadDocument`, C05 중첩 DTO 검증·JSON/Blob 공통 오류·취소 |
| `src/client/intake-detail.css` | 첨부 기준 양쪽 카드 레이아웃·표·자료 목록·반응형 |
| `src/client/IntakeApp.jsx` | 기존 상세 경로를 전체 상세로 확장, 같은 문의의 상담 관리 연결 |
| `src/client/IntakeHeader.jsx`, `IntakeListPage.jsx` | 공통 계정 헤더와 LawyerShell/목록 재사용 |
| `src/client/IntakeConsultationPage.jsx`, `consultationModel.js` | 앞선 상태·메모 화면과 코드·정렬·라벨 공유 |

기존 최소 `LawyerIntakeDetail`은 전용 `IntakeDetailPage`로 확장했다. 상태·메모 경로는 `/consultation`으로 유지하여 중복 폼을 만들지 않았다. 목록 필터는 App 상태로 보존한다. 페이지 새로고침 시 JWT/필터는 메모리에서 초기화하고, 문의 ID는 route에서 C05로 다시 조회한다.

첨부의 사무실 배경·상단 변호사 메뉴·좌측 메뉴·흰 업무 패널·양쪽 카드·상태 배지·주요 버튼을 맞췄다. 1672×941에서 패널 x190/y99, 상세 본문 카드 시작 y233이다. 실제 텍스트 길이·자료 수·메모 수에 따라 세로 길이는 늘어날 수 있다.

모바일 390×844에서는 카드가 한 열이며 사건 개요는 라벨/값 두 칸으로 바뀐다. 최초 시각 검수에서 colgroup 때문에 모바일 값 열이 너무 좁아진 문제를 수정하고 재검수했다. 문서 가로 넘침이 없고, 첨부 표만 내부 가로 스크롤로 모든 열을 확인한다. 핵심 전문·최근 메모 스크롤 영역에는 키보드 포커스를 제공한다.

## 검수 결과와 증거

| 검증 | 결과 |
|---|---|
| `npm run build` | viewer/operator/intake 3개 진입점 성공, 86 modules |
| `npm run test:intake` | 92/92: 기존 화면, 상태·메모, 상세·다운로드 회귀. 화면08 신규 API/모델 7개 + React 4개 포함 |
| `npm run test:intake:live` | 14/14: 실제 Spring API, 화면08 C05/P04 테스트 자료 준비/P05 권한 경계 포함 |
| `npm run test:intake:preview` | 1/1: 최종 빌드 entry와 proxy/C05 연결 |
| `npm run test:operator` | 기존 운영자 회귀 18/18 |
| 실제 브라우저 | 실제 ID 상세, C01 로그인, 파일 저장 이벤트와 31-byte 파일 확인, 추가 조건 펼침·접기, 상태·메모 이동/복귀, 모바일·첨부 표 스크롤 확인 |

자동 검증 총 **125개 통과**(92+14+1+18). Java 소스는 변경하지 않아 이번 화면 작업에서 Gradle 전체 테스트는 다시 실행하지 않았다.

단위·컴포넌트 테스트는 C05 로딩/404/재시도, 빈 조건·첨부·추천·메모, 임의 외부 URL·부모 ID·중복 파일·MIME/길이 오류, 비회원 로그인 연결, 중복 다운로드 방지, 403 시 기존 상세 유지, 요청 취소를 포함한다. 별도 가상 API 서버 실행은 승인되지 않아 이 실패 상태들을 브라우저 가상 서버에서도 검증했다고 표시하지 않는다.

증거 파일:

- `output/intake-08/api-evidence.json`: 실제 문의/파일 ID, 크기, 권한 응답 결과 (token 제외)
- `output/intake-08/screen08-live-desktop.png`: 실제 API와 로그인 상태
- `output/intake-08/screen08-mobile.png`: 모바일 재검수 결과
- 브라우저 저장 파일: `C:/Users/Codelab/Downloads/화면08-검수.txt`, 31 bytes, 개발 검수용 내용

현재 전체 실제 API 테스트는 회당 가상 문의 5건(기존 흐름 1·상태 메모 2·상세 다운로드 2), 검색 요청 1건, 메모 3건, 첨부 1건과 다운로드 감사 기록을 로컬 DB에 추가한다. 앞선 상태·메모 브라우저 검수에서는 그 검수용 문의에 메모 1건을 추가했다. 기존 개발 자료를 삭제하거나 재시작으로 초기화하지 않았다.

## 계약상 남는 범위

`updatedAt`·영구 상태 이력·메모 작성자는 현재 DTO에 없다. 검색 목적은 C05에 없다. 이 항목을 예시로 꾸미지 않는다. 파일 업로드 UI, 추천 자료 준비 완료 저장, 변호사 역할·담당 범위 인가, 비공개 메모 정책은 별도 작업이다. 실제 계약의 빈 필드와 권한 제약을 디자인 예시로 덮지 않는다.
