# 화면04~08 문의 접근 권한 구현·검수

문서 갱신: 2026-10-23. 검증 근거는 2026-10-22 마지막 실행 기록이다. 이번 문서 갱신으로 전체 시나리오를 다시 실행한 것은 아니다. 이전 통합 문서의 ‘문의 API 공개·익명 등록 가능’ 판정은 이 문서로 대체한다.

## 적용한 권한

| 기능 | USER 의뢰인 | LAWYER 담당 변호사 | ADMIN 사무소 관리자 |
|---|---|---|---|
| 로그인 후 문의 등록 | 가능, JWT 사용자로 소유자 기록 | 가능 | 가능 |
| 문의 목록·상세·검색 이력/결과·파일 | 본인 문의만 | 배정된 문의만 | 전체 |
| 검색 요청 생성·수동 결과 기록·외부 검색 실행 | 본인 문의만 | 배정된 문의만 | 전체 |
| 내부 상담 메모 조회·작성, 상태 변경 | 불가 | 배정된 문의만 | 전체 |
| 담당 변호사 배정·해제 | 불가 | 불가 | 가능 |
| 소유자가 없는 기존 문의 | 불가 | 불가 | 조회·관리 가능, 배정은 409 |

로그인 없는 접수는 401이다. C02 분류 미리보기와 R01~R06 공개 Reference 조회는 유지한다. C04 간단 안내는 JWT를 요구하며 저장 문의를 직접 수정하지 않는다. 관리자 자신이 의뢰인 소유자인 경우에도 ADMIN의 내부 관리 권한은 유지한다.

## API·필드·ID·인증

| 동작 | API | 요청·ID | 응답·검사 |
|---|---|---|---|
| 문의 등록 | C03 `POST /api/intakes` | 기존 문의 DTO + Bearer. 소유자 이름을 본문에서 받지 않음 | 201 문의 DTO. `id`를 이후 경로에 사용 |
| 간단 안내 | C04 `POST /api/legal-intake/minimal` | 기존 안내 DTO + Bearer | 기존 규칙/DB 안내. LLM 추가 호출 없음 |
| 상세·목록 | C05 `GET /api/intakes/{id}`, C09 `GET /api/intakes` | Bearer, 상세의 `id`는 문의 ID | 소유자/배정/ADMIN 범위. 목록은 권한 조건을 적용한 뒤 최신50건 |
| 검색 생성 | C06 `POST /api/intakes/{intakeId}/search-requests` | `{searchPurpose,sourcePolicy}` + 문의 ID + Bearer | 기존 검색 DTO. 반환 `id`가 `searchRequestId` |
| 검색 조회·결과 저장 | C07 `GET /api/intakes/search-requests/{searchRequestId}`, C08 `POST .../{searchRequestId}/results` | 검색 요청 ID, C08 기존 결과 DTO + Bearer | 검색 요청의 부모 문의로 권한 재검사. 요청 ID와 문의 ID를 혼용하지 않음 |
| 검색 이력·실행 | P02 `GET /api/intakes/{intakeId}/search-requests`, P03 `POST /api/intakes/search-requests/{searchRequestId}/execute` | 기존 ID/본문 + Bearer | 소유자·배정 변호사·ADMIN. 실제 외부 호출은 별도 검증 |
| 상태·메모 | C10 `PATCH /api/intakes/{id}/status`, C11 `POST /api/intakes/{id}/memo` | `{status}` 또는 `{content,followUpItems}` + Bearer | 담당 변호사/ADMIN만. 메모 `createdBy`는 JWT에서 기록하고 요청의 작성자 값은 사용하지 않음 |
| 파일 | P04 `POST /api/intakes/{intakeId}/documents`, P05 `GET /api/intakes/{intakeId}/documents/{documentId}/download` | 부모 문의 ID + 실제 파일 ID + Bearer | 부모 문의 접근 권한과 파일 소속 일치 검사 |
| 담당자 배정·해제 | **C21 신규** `PATCH /api/intakes/{id}/assignment` | ADMIN Bearer, `{"assignedLawyerUsername":"lawyer"}` 또는 `{"assignedLawyerUsername":null}` | 200 갱신된 문의 DTO. 실제 활성 LAWYER 계정만 배정. 잘못된 계정400, 비ADMIN403, 기존 무소유 문의409 |

C21 매핑: `IntakeAssignmentController.assign` → `LegalIntakeRecordService.assignLawyer`. 계정 식별자는 숫자 변호사 ID가 아닌 `username`이다. 신규 번호 API는 기존37개에 C21을 더한38개이며, `/actuator/guardtimeseries` 보조 경로는 별도다.

문의 응답에 `permissions: {canManage,canAssign}`를 추가했다. 비관리 주체의 응답에서는 `consultationMemos`와 `assignedLawyerUsername`를 제외한다. 내부 메모는 네트워크 JSON에서도 빠지며 후속 업무 항목도 함께 노출되지 않는다. 관리 응답의 메모에는 `createdBy`가 추가된다. 기존 작성자 미기록 메모를 임의 이름으로 보충하지 않는다.

## React·경합 처리

로그인 후 같은 화면으로 복귀하고, 사용자 전환·로그아웃 시 이전 문의 상태를 제거한다. 의뢰인의 목록은 ‘나의 문의’로 표시하며 내부 메모·상태 관리 진입을 제한한다. 서버 응답의 권한으로 담당자 배정과 관리 버튼을 표시한다. 관리 중 401/403이 발생하면 이미 표시한 내부 메모도 지운다. 관리자 배정 폼은 중복 제출을 잠그고 재조회로 결과를 확인한다.

상태·메모 저장과 담당자 재배정은 같은 문의 행의 DB 잠금으로 순서를 보장한다. 배정 해제 후 새 요청은 기존 담당자에게 403이다. 이미 전달한 응답이나 내려받은 파일을 회수하는 기능은 아니다. 이 잠금은 요청별 영구 멱등성·기대 버전 계약을 대신하지 않는다.

## DB 변경

기존 테이블에 nullable 문자열 컬럼2개를 추가했다: `legal_intake.assigned_lawyer_username`, `consultation_memo.created_by`(각100자). 새 테이블은 없다. [ERD](BACKEND_ERD.md)와 [MySQL 수동 이전 SQL](../backend/guardrail-ready-policy-checker/src/main/resources/db/manual/2026-10-22-intake-permissions-mysql.sql)을 갱신했다. 이번 실행은 격리 H2이며 실제 MySQL 이전·복구 검증은 남아 있다.

## 검증 결과·한계

- [x] 백엔드 전체210개 검사: 실패0·오류0·제외0, bootJar 성공. 신규 권한 통합8개 검사 포함.
- [x] 프런트 문의142개·운영자35개 검사 및 production 빌드 성공.
- [x] 실제 React↔Spring↔H2 브라우저14개 검사: 익명 거절, 소유자 기록, 관리자 배정, 배정 목록, 메모 작성자, 화면04·05 인증, 의뢰인 JSON/화면에서 메모 제외, 타인 차단, 재배정 권한 회수, 403 후 내부 화면 제거, 로그아웃, 모바일390px/데스크톱 가로 넘침 확인.
- [x] 검색 요청·파일의 부모 ID, 권한 범위를 적용한 최신50건, 무소유 기존 자료, 본문 작성자 위조는 서버 통합 검사로 확인.
- [ ] 실제 MySQL 스키마 적용, 운영 계정 디렉터리·배포 환경 검증.
- [ ] 실제 외부 LLM·Guard·네이버 호출과 청구 검증. 이번 유료 호출0회.

증거: [브라우저14개 기록](../output/intake-permissions/browser-verification.json), [통합 요약](../output/intake-permissions/verification.json), [관리자 배정](../output/intake-permissions/admin-assignment.png), [변호사 내부 메모](../output/intake-permissions/lawyer-internal-memo.png), [의뢰인 상세](../output/intake-permissions/client-detail-no-internal.png).

이 변경은 화면09~13의 운영자 업무 API ADMIN 인가, C17 원문 노출, C04/C08 서버 입력 검증, C03/C10/C11 멱등성, Incident 상태 전이/404를 완료하지 않는다. 해당 항목은 [남은 작업](SCREEN01_12_REMAINING_TASKS.md)에 유지한다.
