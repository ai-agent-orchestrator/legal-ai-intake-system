# 화면03 접수 멱등성 · 화면07 동시 수정 계약 (B01 / B02)

검증 테스트: `ResubmitConflictIncidentIntegrationTest`(순차, MockMvc), `IntakeConcurrencyIntegrationTest`(실제 병렬 HTTP, 테스트 트랜잭션 없음).
가상 자료만 사용한다. 기본 실행 DB는 로컬 H2 인메모리이며, 로컬 MySQL로 같은 검사를 돌리려면
환경 변수 `SPRING_PROFILES_ACTIVE=mysql`을 설정하고 `./gradlew test --tests "*IntakeConcurrencyIntegrationTest"`를 실행한다(전용 실험 스키마만 사용).

## 화면03 C03 문의 접수 `POST /api/intakes`

| 상황 | 결과 |
|---|---|
| 키 없음 | 매번 새 문의 저장(기존 동작) |
| 같은 사용자 + 같은 `Idempotency-Key` + 같은 본문 | `201`, `Idempotent-Replayed: true`, 같은 문의ID |
| 같은 사용자 + 같은 키 + 다른 본문 | `409 IDEMPOTENCY_KEY_REUSED`, 저장 없음 |
| 다른 사용자 + 같은 키 | 서로 독립된 문의(키 범위는 사용자별) |
| 키 형식 오류(8~100자 `[A-Za-z0-9._:-]` 아님) | `400`, 저장 없음 |
| 잠금/쿼리 시간 초과 등 일시적 DB 오류 | `503 DATABASE_BUSY` — 같은 키로 다시 보내면 안전 |
| 그 밖의 DB 무결성 오류 | `500` — 재전송 성공으로 숨기지 않음 |

- **필수 보장**: (사용자, 키)당 문의 1건과 관련 자료(`intake_document`)·검색조건(`lawyer_search_condition`) 1벌. 접수는 이벤트를 만들지 않는다.
- **재전송 응답 본문**: 처음 저장된 같은 문의의 **현재 상세**를 다시 조회한 값이다. 최초 응답 스냅샷을 따로 보관하지 않으므로,
  그 사이 상담자가 상태를 바꿨으면 바뀐 상태가 보인다.
- **지문**: 요청 DTO의 모든 필드(이름 + 값, 길이 접두 인코딩)의 SHA-256(`intake-v2`). 해시만 저장하고 원문은 저장·로그하지 않는다.
  2026-10-23 이전에 `record toString` 기반(`intake-v1`)으로 저장된 행도 같은 본문이면 재전송으로 인정한다.
- **동시 요청**: `(owner_username, idempotency_key)` 유니크 제약이 한 건만 커밋시킨다. 진 요청은 롤백된 뒤 새 읽기 트랜잭션에서
  먼저 커밋된 문의를 조회해 돌려준다. 이 경로는 해당 유니크 제약 위반일 때만 타며, 로그에는 문의ID만 남긴다.

## 화면07 C10 상태 변경 `PATCH /api/intakes/{id}/status`

모든 쓰기(상태·메모·재배정)는 접수 행 `SELECT ... FOR UPDATE` 잠금을 먼저 잡고, 그 안에서 권한 → 현재 상태 → `expectedVersion` 순으로 확인한다.

| 상황 | 결과 |
|---|---|
| 요청 상태 = 현재 상태 | `200 changed=false`, version 그대로(버전 검사 생략 — 재전송 보호) |
| `expectedVersion` ≠ 현재 version | `409 INTAKE_VERSION_CONFLICT` + `details.currentStatus/currentVersion` |
| 허용되지 않는 전이 | `409 INTAKE_STATUS_TRANSITION_NOT_ALLOWED` |
| 같은 version으로 두 관리자가 서로 다른 상태 | 하나 `200`, 다른 하나 `409`, version +1 |
| `expectedVersion` 생략 | 버전 검사 없이 처리(기존 호출 호환, 마지막 요청 반영) — R01 결정이 바뀌면 여기와 테스트를 함께 수정 |

## 화면07 C11 상담 메모 `POST /api/intakes/{id}/memo`

| 상황 | 결과 |
|---|---|
| 같은 키 + 같은 작성자 + 같은 내용 | `201`, `Idempotent-Replayed: true`, 같은 memoId (version 검사보다 먼저 판별) |
| 같은 키 + 다른 내용 | `409 IDEMPOTENCY_KEY_REUSED` |
| 같은 키 + 다른 작성자 | `409 IDEMPOTENCY_KEY_REUSED` (남의 메모를 돌려주지 않음) |
| 다른 키 + 같은 내용 | 별도 메모 저장 |
| 같은 키, 다른 문의 | 별도 범위(키 범위는 문의 + 작성자) |
| 키 없음 + 같은 작성자·같은 내용이 120초 안에 다시 옴 | 재전송으로 간주(`intake.memo.resubmit-window-seconds`) |

키 없는 120초 규칙은 **기존 호출을 위한 보조 정책**이다. 창이 지나면 같은 내용도 새로 저장되므로 영구 멱등 보장이 아니다.
화면은 메모 저장 버튼을 누를 때 키를 만들어 재시도 동안 유지해야 한다.

## 재배정 `PATCH /api/intakes/{id}/assignment` (ADMIN)

같은 행 잠금을 쓰므로 이전 담당 LAWYER의 메모·상태 저장과 직렬화된다. 재배정이 먼저 커밋되면 이전 담당자의 요청은 잠금을 얻은 뒤
권한 확인에서 `403`, 나중이면 정상 저장된다. 담당자가 바뀌면 version +1이고 응답 `version`은 커밋된 값과 같다(같은 담당자 재배정은 변경 없음).
