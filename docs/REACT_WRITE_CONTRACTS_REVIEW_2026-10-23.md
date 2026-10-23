# Codex 담당 React 요청 키·버전·충돌 복구 구현

2026-10-23. [분담표](REFACTORING_HANDOFF_BY_SCREEN_ROLE_2026-10-23.md)의 C01-a/b, U01/U02, L01~L05, A01/A02 총11개 완료. 서버 소스·DB·LLM 설정은 수정하지 않았다. 현재 작업 폴더에 있는 서버 계약을 기준으로 프런트를 연결했다.

## 화면별 적용

| 화면 / 역할 | API | 적용 내용 |
|---|---|---|
| 03 USER·LAWYER·ADMIN | C03 | 논리적인 접수 시도별 메모리 요청 키. 응답 유실·화면 이동 후 같은 내용 재전송은 같은 키. 성공한 ID로 화면04 이동 |
| 03 공통 | C03 | 불확실한 저장 후 내용 변경을 새 키로 자동 전송하지 않음. 기존 문의 확인과 명시적 새 접수 동작 제공 |
| 07 LAWYER·ADMIN | C05→C10 | 조회 version을 expectedVersion으로 전송. 저장 응답 version 즉시 반영. 저장 후 GET 실패와 쓰기 실패 구분 |
| 07 LAWYER·ADMIN | C11 | 메모 시도별 요청 키와 expectedVersion 전송. intakeVersion 반영, 재전송 메모ID 중복 제거·원래 작성 정보 유지 |
| 07 공통 | C10/C11 | 409에 본문·체크·선택 유지, 최신 조회 후 사용자가 확인해야 재저장 가능. 자동 POST/PATCH 재시도 없음 |
| 07 공통 | C05 | 버전이 없으면 0을 만들어 보내지 않고 저장 잠금·최신 서버 확인 안내. 명시된 잘못된 버전은 API 응답 오류 |
| 08 LAWYER·ADMIN | C05/C21 | 상세→관리에서 새 조회. 배정 회수 뒤403에서는 내부 메모·초안 제거. 배정 쓰기의401/403에도 상세 재조회 |
| 12 ADMIN | C19/C20 | 구조화된409 한국어 안내. 이미 종결된 건은 최신 조회 후 처리 버튼 비활성. 선택 ID 유지 |
| 12→09 ADMIN | C18/C12 | PATCH1회 뒤 목록과 요약 각각 GET. 저장 여부와 두 조회의 성공/실패를 따로 안내 |

## 실제 서버 계약과 프런트 호출

공통 API의 기존 인수는 유지하고 마지막 options 인수를 추가했다. 화면07은 version 없는 쓰기를 호출하지 않는다. 서버의 expectedVersion 생략 허용 정책을 프런트에서 바꾼 것은 아니다.

```javascript
api.create(payload, signal, { idempotencyKey });
// POST /api/intakes
// Idempotency-Key: UUID
// 201 { id, ... }, Idempotent-Replayed: false|true

api.updateStatus(id, status, signal, { expectedVersion });
// PATCH /api/intakes/{id}/status
// { status, expectedVersion } -> { id, previousStatus, status, changed, version }

api.createMemo(id, content, followUpItems, signal, { idempotencyKey, expectedVersion });
// POST /api/intakes/{id}/memo
// { content, followUpItems, expectedVersion } -> memo + intakeVersion
```

예상 버전은 음수가 아닌 안전한 정수이며0도 유효하다. 반환 버전은 요청 버전보다 작을 수 없다. 키는 서버 형식8~100자에 맞춘다. 재전송 헤더가 있으면 true/false만 허용하며 실제 메타데이터를 `replayed`로 전달한다. 없는 헤더를 임의 false로 보충하지 않는다.

ApiError와 운영자 오류에는 검증된 code 및 상태/버전/숫자ID만 담은 details를 전달한다. 서버 message·사용자 원문·임의 details는 표시하지 않는다. 기존 fieldErrors·traceId·401/403·취소·시간 초과 처리를 유지한다.

요청 키와 초안은 메모리에만 있다. 로그인 화면 재구성/같은 작성 화면 재진입에는 부모의 접수 시도를 유지하고, 로그아웃·사용자 전환·새 문의 시작·성공에는 초기화한다. 브라우저 새로고침·탭 종료 후 동일 시도를 복원하는 영구 기능은 추가하지 않았다.

## 검수 결과

- [x] 문의 API·React **154개**, 운영자 **47개**, 합계 **201개 통과**, 실패/제외0.
- [x] Vite production 빌드 성공.
- [x] 신규 검사는 요청 키의 수명/범위, 헤더·JSON·0버전, 오류 code/details 안전 처리, 응답 유실,409 입력 보존, 새 버전 재저장, 새 메모의 새 키, 조회 실패 뒤 버전 유지, 버전 누락 잠금을 포함한다.
- [x] 실제 Chrome + production React + 격리 Spring/H2 브라우저 **9개 시나리오** 통과. 브라우저 JavaScript 오류0.
- [x] 실제 저장 직후 응답만 끊은 C03 재전송에서 문의1건·같은ID 확인. C11도 같은 키 재전송은 메모1건, 별도 새 키는 같은 내용이어도 새 메모 확인.
- [x] 두 LAWYER 화면의 오래된 버전409, 메모·체크 보존, 확인 후 최신 버전 전송. ADMIN 재배정/해제 후 이전 담당자403·내부 자료 제거. USER JSON에 메모 제외 유지.
- [x] 실제 Incident를 다른 요청으로 먼저 종결한 뒤 오래된 관리자 화면에서 ack:409 한국어 안내, 브라우저 PATCH1회, 목록·요약 갱신 및 종결 버튼 비활성.
- [x] 화면07 충돌 상태 PC·390px 모바일 캡처와 모바일 가로 넘침 없음 확인.
- [x] 유료 OpenAI·Guard·네이버 호출0. 검수 서버는 빈 LLM키·폐쇄 루프백 공급자 주소·검색 비활성으로 실행.

집중85개는 전체201개에 포함된다. 브라우저9개는 시나리오 수이며 내부 모든 HTTP 수를 의미하지 않는다. 보조 직접 HTTP14개 기록은 같은 검수 실행의 일부다. 서버 전체 회귀나 Claude 담당 병렬 트랜잭션 검사를 대신하지 않는다.

## 실행 서버와 남은 통합

**D01/D02 후속 완료:** 현재8080은 최신 소스의 SHA별 실행 JAR 및 `local-persistent` 프로필이다. 기존 메모리 DB20표/42행을 파일 H2에 보존했고 실제 요청 키·버전·409·Incident 전이 HTTP35개 및 재시작 후 데이터 유지를 확인했다. [DB·배포 검수서](DB_DEPLOYMENT_REVIEW_2026-10-23.md).

최종 독립 서버 회귀260개·bootJar, 프런트201개·production 빌드가 통과했다. 같은 빌드와 원본 데이터 복원 검수 서버18097에서 Chrome9개를 다시 통과했다. 이전18099 검수는 최초 React 연동 이력이며 현재 실행본의 근거와 구분한다.18097/18099 검수 서버는 종료했다.

남은 별도 범위: B01/B02/B03 담당자의 최종 정책 검토, Q01~Q03의 전체 화면 범위, 실제 공급자 V01~V05. 현재 전체 회귀에는 문의/Incident 병렬 검사도 포함하지만 이 문서만으로 다른 담당 항목의 완료 체크를 일괄 바꾸지 않는다. 실제 유료 호출은0회다.

## 파일과 증거

- 공통: `src/shared/writeContracts.js`, `src/client/api.js`.
- 화면03: `IntakeApp.jsx`, `IntakeWritePage.jsx`, `intake-write.css`.
- 화면07~08: `IntakeConsultationPage.jsx`, `IntakeAssignment.jsx`.
- 운영자: `operatorApi.js`, `auditModel.js`, `AuditPage.jsx`.
- 검사: `write-contracts.test.mjs`, `intake-components.test.mjs`, `intake-test-data.mjs`, `audit-workflow.test.mjs`; `package.json`의 문의 검사에 신규 파일 포함.
- 로컬 증거: `output/react-write-contracts/verification.json`, `browser-verification.json`, `intake-tests.log`, `operator-tests.log`, `frontend-build.log`, `snapshot-build.log`, `screen03-uncertain.png`, `screen07-conflict.png`, `screen07-conflict-mobile.png`, `screen12-conflict.png`.
