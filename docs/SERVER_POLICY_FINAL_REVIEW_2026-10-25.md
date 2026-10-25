# 서버 정책·동시성 최종 대조 — B01~B03

## 결론

현재 구현을 유지하고 호환 정책과 보장 범위를 확정했다. 서버 기능은 이미 구현돼 있었으며, 이번에는 독립 소스 복사본·새 H2에서 순차/실제 병렬 HTTP 검사 **35개가 통과**했다. 실행 중인 유료 실험 서버·모델·키·원본 DB·릴리스 JAR를 변경하지 않았다.

## 확정 계약

| 동작 | 확정 정책 |
|---|---|
| C03 접수 키 | `(소유자, Idempotency-Key)` 범위. 같은 키/내용은 같은 문의 ID, 다른 내용은 409 `IDEMPOTENCY_KEY_REUSED`. 키 없는 접수는 새 문의로 저장되며 중복 방지를 보장하지 않음 |
| C03 재전송 응답 | 최초 응답 스냅샷이 아니라 **같은 문의의 현재 상세**. 이후 상태·메모·버전이 바뀌면 재전송 응답에도 반영. 최초/재전송 모두 201, `Idempotent-Replayed`로 구분 |
| C10 상태 버전 | 요청 `{status, expectedVersion}`. 버전 불일치는 409 `INTAKE_VERSION_CONFLICT`. **같은 상태 재요청은 버전 검사보다 먼저 no-op 200**, 버전 증가 없음 |
| 버전 생략 | C10/C11에서 하위 호환으로 허용. 생략 호출에는 오래된 화면 수정 차단 보장을 하지 않음. 현 React는 조회한 버전을 전송하므로 충돌 복구 적용 |
| C11 메모 키 | 유니크 범위는 `(문의, 키)`. 같은 작성자/내용의 재전송은 같은 메모 ID. 같은 키를 다른 작성자 또는 다른 내용으로 재사용하면 409. 같은 내용도 다른 키면 별도 메모 |
| 키 없는 메모 | 같은 문의·작성자·정규화 내용/후속업무 해시가 기본 **120초** 안에 반복되면 중복 추정. 시간 경과 후에는 새 저장이 가능하므로 영구 멱등성 아님 |
| C11 재전송·버전 | 재전송 판별이 버전 검사보다 우선. 최초 메모 ID·작성자·작성시각을 유지하고 `intakeVersion`은 문의의 현재 버전. 새 저장은 문의 버전 증가 |
| 상태·메모·배정 경합 | 문의 행 잠금 아래 권한·재전송·버전·저장 판단. 미배정/배정 회수 후 LAWYER는 403. 두 관리자의 다른 변경은 성공/409로 직렬화 |
| C19/C20 Incident | OPEN→ACKNOWLEDGED/RESOLVED, ACKNOWLEDGED→RESOLVED 허용. RESOLVED→ACKNOWLEDGED는 409 `INCIDENT_ALREADY_RESOLVED`. 같은 상태 요청은 200 no-op이며 updatedAt 유지 |
| Incident 권한/누락 | 익명 401, USER/LAWYER 403, ADMIN의 없는 ID 404. 실제 동시 ack/resolve에서 최종 RESOLVED 유지 |

멱등 접수의 필수 보장은 같은 ID와 저장 부작용 중복 방지다. 반환 JSON 전체가 최초 응답과 영구 동일하다는 보장은 하지 않는다. 키 없는 메모의 120초 정책은 코드·설정 대조와 창 안 재전송 검사로 확인했으며 시간 경과 경계의 MySQL 실험을 새로 실행한 것은 아니다.

## 실행 증거와 기존 260개 대조

| 검사 클래스 | 현재 실행 | 대응 범위 |
|---|---:|---|
| ResubmitConflictIncidentIntegrationTest | 16 통과 | 순차 키/지문, 현재 상세 재전송, 버전 충돌·같은 상태, 메모 재전송, Incident 계약 |
| IntakeConcurrencyIntegrationTest | 13 통과 | 같은 키/다른 내용 병렬 저장, 소유자 격리, unique 경합 후 커밋/롤백, 상태/메모 경쟁, 키 없는 메모, 배정 경합 |
| IncidentTransitionConcurrencyIntegrationTest | 6 통과 | 순차 전이·권한/404, 양쪽 잠금 순서, 동시 ack/resolve·폭주, DB/응답/대시보드 일치·원문 비노출 |

- **2026-10-23 전체 260개**는 `output/deployment-db-2026-10-23/verification.json`의 `backend.tests=260`, 31 suites, failures/errors/skipped 0 기록이다.
- **2026-10-25 현재 35개**는 위 세 클래스의 독립 재실행이다. 기존 260개를 오늘 전체 회귀로 다시 표시하거나 295개로 합산하지 않는다.
- 증거: `output/q-final-2026-10-25/backend-policy-summary.json`, `backend-junit/`, `backend-policy-tests.log`, `backend-source-hashes.json`.
- 소스 복사본: `.local/q-final-2026-10-25/backend`. 원본의 `src`·Gradle 정의만 복사하고 키·런타임 DB는 복사하지 않았다. 현재 검사는 H2이며 MySQL 부하/운영 경합 검증으로 확장 해석하지 않는다.

이 계약이 프런트의 키·버전 전달 및 수동 충돌 복구와 일치함을 확인했다. API 필드·인증·상태코드는 변경하지 않았다.
