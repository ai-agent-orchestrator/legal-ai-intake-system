# 화면07 첨부 기준 · 상태 변경·상담 메모 구현 검수

> 2026-10-21 최신 [화면05~08 통합 재검수](SCREEN05_08_INTEGRATION_REVIEW.md): 요청·응답·ID·공통 React·라우팅·실제 API 연결 확인. 개발용 공개 API·C08 서버 검증 보완과 P03 실제 키 미검증을 구분했다. 아래 개별 회차 기록보다 최신이다.

검수일: 2026-10-21, Asia/Seoul. 이 문서는 사용자가 화면07로 첨부한 상태 변경·상담 메모 UI를 따른다. 기존 기능표에서는 C10·C11을 화면08로 분류했으며, API 식별자와 경로는 그대로 유지한다. 사용자가 이어서 화면08로 첨부한 문의 상세는 [화면08 검수](SCREEN08_REACT_REVIEW.md)에 기록했다.

## 실행·공통 React 구성

- 경로: `intake.html#/lawyer/intakes/{intakeId}/consultation`
- 상세의 ‘상태 · 상담 메모 관리’/‘메모 관리하기’ → 같은 문의 ID의 이 경로 → ‘문의 상세로 돌아가기’ → `#/lawyer/intakes/{intakeId}`
- `IntakeConsultationPage.jsx`, `consultationModel.js`, `intake-consultation.css`를 추가했다. `IntakeApp`의 hash routing, `IntakeHeader`, `LawyerShell`, Icon, Button, Feedback, 메모리 JWT를 공유한다.
- 사무실 배경·업무 메뉴·문의 요약·좌측 상태/우측 메모 배치를 첨부에 맞췄다. 현재 계정·문의 번호·접수 시각·상태·메모는 실제 응답으로 표시한다.

## API·요청·응답·ID·인증 대조

| 동작 | 요청 | 응답 / ID | React → Controller → Service |
|---|---|---|---|
| 문의 요약·현재 상태·저장 메모 | C05 `GET /api/intakes/{id}`, body 없음 | `LegalIntakeResponse`, 실제 문의 `id`, `status`, `consultationMemos[]` | `api.consultationIntake` → `LegalIntakeRecordController.findById` → `LegalIntakeRecordService.findById` → 보호 DTO |
| 상태 변경 저장 | C10 `PATCH /api/intakes/{id}/status`, `{status}` | 200 `{id,previousStatus,status,changed}` | `api.updateStatus` → `LegalIntakeStatusController.update` → `LegalIntakeRecordService.updateStatus` → `LegalIntake.changeStatus` → `IntakeStatus.canChangeTo` |
| 메모 추가 저장 | C11 `POST /api/intakes/{id}/memo`, `{content,followUpItems}` | 201 `{id,intakeId,contentMasked,followUpItems,createdAt}` | `api.createMemo` → `ConsultationMemoController.create` → `LegalIntakeRecordService.createMemo` → `ConsultationMemoRepository.save` → `PrivacyOutputGuardService.mask` |
| 저장 후 최신 정보 | C05 GET 재조회 | 상태·메모 목록을 DB 기준으로 다시 확인 | 같은 `api.consultationIntake`; 상태 변경과 메모 추가는 독립 요청 |
| 목록/상세 이동 | hash route | 항상 문의 ID 유지 | `IntakeApp`/공통 `LawyerShell` |

세 업무 API는 **현재 서버에서 공개 접근**이다. 로그인 token이 있으면 선택적으로 Bearer 헤더를 보낸다. Controller는 작성자를 Authentication으로 저장하거나 변호사 역할·담당 범위를 검사하지 않는다. `ROLE_LAWYER`도 현재 없다. UI 메뉴와 JWT 전송을 변호사 전용 인가 완료로 표현하지 않는다. 공통 C01 로그인과 401/403 처리는 기존 구현을 재사용한다.

C10의 `id`는 문의 ID, C11 응답 `id`는 메모 ID, `intakeId`는 부모 문의 ID다. 클라이언트는 이 둘을 대조하고 응답 형식을 검증한다. `sessionId`, 검색 조건 ID를 path에 넣지 않는다. 상태 응답은 요청한 상태와 같아야 하며 `changed === (previousStatus !== status)`도 대조한다.

## 입력·상태 정책

서버 표준 상태는 `SUBMITTED`, `REVIEWED`, `NEED_MORE_INFO`, `CONSULTATION_READY`, `CLOSED`다. 첨부 예시의 제한된 선택지를 그대로 복사하지 않고 실제 `IntakeStatus.canChangeTo`를 사용했다.

- 같은 상태 선택은 허용한다. C10이 `changed:false`면 변경 없음으로 안내한다.
- `SUBMITTED`에서는 다섯 상태를 선택할 수 있다.
- 검토됨·보완 필요·상담 준비에서는 `SUBMITTED`로 되돌릴 수 없다. 그 외 네 상태로 변경할 수 있다.
- `CLOSED`에서는 `CLOSED`만 가능하며 재개는 409다.
- 상태 미선택, 메모 공백, 4,001자 이상은 HTTP 요청 전에 안내한다. 길이는 Java/HTML과 같은 UTF-16 단위다.
- C11 `content`는 필수·최대 4,000자, `followUpItems`는 선택·최대 4개다. 전송 코드는 `REQUEST_DOCUMENTS`, `SCHEDULE_CONSULTATION`, `DOCUMENT_REVIEW`, `E_LITIGATION_GUIDE` 네 개다. 화면 라벨을 요청 값으로 보내지 않는다.
- 후속 항목은 메모에 저장되는 참고 정보다. 이 Service는 알림 발송이나 일정 생성을 하지 않는다.

## 저장·오류 처리

상태와 메모의 버튼은 한 번의 저장이 진행되는 동안 함께 잠근다. 동기 ref로 빠른 중복 제출도 차단한다. 저장 성공 응답을 받은 뒤 C05를 재조회하며, C11 응답의 보호된 메모를 우선 반영하고 입력 메모/체크를 초기화한다.

400/401/403/404는 오류 안내와 함께 입력을 유지한다. 409는 최신 상태를 읽어 선택 가능 상태를 다시 적용한다. 5xx·네트워크 실패·timeout·성공 응답의 형식 오류처럼 저장 여부를 알 수 없는 경우에는 자동 재전송을 하지 않는다. ‘저장 결과 조회’로 C05의 최신 상태와 메모를 확인하고 ‘확인 후 편집 계속’을 누르면 다시 편집할 수 있다. 확인 전 메모 입력은 유지된다.

저장 성공 후 C05만 실패한 경우에는 ‘저장은 완료됐지만 최신 내용을 다시 조회하지 못했습니다’를 표시한다. 이때 재시도 버튼은 GET만 수행한다. 성공한 POST/PATCH를 다시 보내지 않는다. 화면 이동 시 요청을 취소하지만 서버에 이미 도착한 쓰기의 취소를 보장하는 것으로 설명하지 않는다.

## 첨부 예시와 실제 계약의 차이

- C05/C10에 `updatedAt`, 상태 변경 시각·변경자·과거 이력 배열이 없다. 최근 수정 시각과 과거 변경 이력을 임의 생성하지 않았다.
- 좌측 ‘이 화면에서 확인한 변경’은 성공한 C10의 실제 이전/다음 상태와 **클라이언트가 응답을 확인한 시각**의 최근 5건이다. 새로고침하면 초기화된다는 안내가 있다. DB 상태 이력으로 표시하지 않는다.
- C11에 작성자 필드가 없다. 김지훈 변호사 등을 복사하거나 현재 로그인 사용자를 과거 메모 작성자로 단정하지 않는다. 실제 메모 ID·createdAt·contentMasked·followUpItems를 표시한다.
- C05가 메모를 공개 조회 응답에 포함하므로 ‘의뢰인에게 절대 전달되지 않는 비공개 메모’라는 보장은 UI에 넣지 않았다. ‘메모는 이 문의 조회 응답에 포함된다’고 표시한다.

## 검수

기능 전용 API/모델 7개·React 8개, 총 15개 회귀 검증이 포함되어 있다. 최신 전체 검수는 화면08과 함께 코드/React/설정 92개, 실제 서버 14개, 빌드 preview 1개, 운영자 회귀 18개 = **125개 통과**, 3개 진입점 빌드 성공이다. Java 소스는 변경하지 않았다.

실제 서버에서 상태 변경·동일 상태 changed:false·접수 상태로의 역전환 409·종료 후 변경 409·메모 저장·마스킹·재조회·공백/4,001자/잘못된 후속 코드/5개 항목 400을 확인했다. 브라우저에서는 상태 미선택·메모 공백, 실제 상태 저장, 메모 저장·마스킹·초기화, 새로고침 후 메모 유지, 상세 복귀를 확인했다. 모바일 390×844에서 문서 가로 넘침이 없었다.

오류 응답 유실·성공 후 재조회 실패·초기 403·중복 저장·취소 처리는 컴포넌트 테스트로 검증했다. 별도 가상 API 서버 실행이 승인되지 않아 해당 상태를 브라우저에서도 확인했다고 기록하지 않는다.

증거: `output/intake-07/api-evidence.json`, `screen07-live-desktop.png`, `screen07-mobile.png`. API 증거 JSON에는 token과 원문 개인 정보를 넣지 않았다. 실제 API 검수용 가상 문의만 변경했으며 기존 개발 자료는 삭제하지 않았다.

운영 전 남은 사항: C05/C10/C11의 역할·담당 범위 인가, 영구 상태 이력과 작성자·수정 시각 계약, 비공개 내부 메모 정책, 다중 담당자의 동시 수정 충돌 제어와 쓰기 idempotency. 현재 서버는 기대 이전 상태/버전 키를 받지 않으므로 클라이언트의 중복 클릭 방지만으로 서버 동시 수정까지 보장하지 않는다.
