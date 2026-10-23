# D01/D02 · DB 이행·문서·최신 실행본 검수

2026-10-23 / Asia·Seoul. D01과 D02를 실행했다. 실제 사용자 DB와 검수용 DB를 구분하며, 유료 LLM·Guard·네이버 호출은0회다.

## 완료한 범위

- [x] 실제 로컬 MySQL8.0.46 `legal_intake_lab` 접속·변경 전 컬럼/인덱스 확인.
- [x] 전체 MySQL 덤프를 비공개 폴더에 만든 후 추가 방식으로 컬럼8개·복합 UNIQUE2개 반영. 이전 권한 컬럼2개와 이번 재전송/버전 컬럼6개를 구분.
- [x] MySQL UNIQUE·NULL 키·버전 compare-and-set 검증. 가상 쓰기는 트랜잭션 롤백. 기존 문의/메모 행 수0/0 유지(AUTO_INCREMENT 결번은 발생할 수 있음).
- [x] MySQL 변경 전 백업을 별도 `legal_intake_lab_restore_1792752002450`에 실제 복원. 원래 컬럼의 행 수/행 해시를20개 테이블에서 대조.
- [x] 조건부 MySQL 수동 SQL을 두 번 실행해 중복 컬럼/인덱스 오류 없이 재실행 확인.
- [x] 기존8080 H2 메모리 DB의20개 테이블·42개 행을 비공개 백업. 기존 문의3건·검색 요청2건·자료6건 포함.
- [x] 최신 스키마의 새 파일 H2에 복원해 원래 모든 컬럼의 행 수·SHA-256 행 해시 일치, 실제 DB FK 연결 및 IDENTITY 다음값 확인.
- [x] 최종 백업 시 기존 DB 쓰기를 일시 동결하고 복원 확인 뒤 이전 JVM 종료. 기존 데이터 유실 없이8080을 `local-persistent`로 전환.
- [x] 실행 JAR는 빌드 산출물과 분리한 SHA-256별 파일. 실행 파일·빌드 파일 해시 일치, 활성 프로필·포트·PID·기동 시각 기록.
- [x] 실제8080에서 요청 키/버전/409/권한/상태 전이/CORS HTTP35개 검증. 재시작 후 기존 문의3건과 검수용 문의4·메모·종결 Incident 유지 확인.
- [x] 프런트 문의154개·운영자47개 및 production 빌드 통과. 복원 검수 서버에서 실제 Chrome9개 시나리오 통과.
- [x] 최종 독립 서버 회귀31개 suite/260개(실패·오류·건너뜀0) 및 bootJar 통과. 검수 복사본과 활성 JAR 대상의 src/main 파일 해시 및 재빌드 JAR SHA-256 일치 확인.

최신 독립 회귀260개의 JUnit 증거와 검증 요약은 `output/deployment-db-2026-10-23/verification.json`, `backend-junit/`에 기록했다. 공유 폴더에서 다른 테스트 실행이 결과 XML을 바꾸는 현상을 확인해 최종 회귀는 별도 소스 복사본에서 실행했다. 과거225/237개를 최신 수치로 사용하지 않는다.

## 1. 실제 이행 전후

| 대상 | 변경 전 | 변경 후 / 처리 |
|---|---|---|
| 현재 앱 DB | H2 메모리/create-drop | 파일 H2/validate, 기존 전체 업무 데이터 복원 |
| MySQL | 별도 로컬 `legal_intake_lab`, 문의0·메모0 | 대상 컬럼/제약 추가, 문의0·메모0 유지. 앱 DB를 MySQL로 바꾼 것은 아님 |
| legal_intake.version | 없음 | BIGINT NOT NULL, 기존 데이터0. H2 복원 대상과 MySQL에 DEFAULT0 설정 |
| legal_intake.updated_at | 없음 | nullable timestamp/datetime(6). 기존 시각을 만들어 채우지 않음 |
| legal_intake.idempotency_key | 없음 | nullable VARCHAR(120), 기존NULL |
| legal_intake.request_hash | 없음 | nullable VARCHAR(64), 기존NULL, 요청 지문 SHA-256 |
| consultation_memo.request_key | 없음 | nullable VARCHAR(120), 기존NULL |
| consultation_memo.content_hash | 없음 | nullable VARCHAR(64), 기존NULL, 내용/후속 업무 지문 |
| 이전 권한 컬럼 | MySQL에 assigned_lawyer_username/created_by 없음 | 두 nullable VARCHAR(100)도 선행 적용. 기존 작성자·담당자를 추정하지 않음 |
| 문의 유니크 | 없음 | `(owner_username,idempotency_key)` |
| 메모 유니크 | 없음 | `(intake_id,request_key)` |

복합 UNIQUE를 각 컬럼의 단독 UNIQUE로 표기하지 않는다. 기존NULL 키는 여러 행에 있을 수 있다. 새 화면의 키 입력은8~100자이며 저장 공간120자와 구분한다. FK·테이블 수를 새로 늘린 변경은 아니다. 화면13의 관측 지표 때문에 생긴 컬럼도 아니다.

## 2. 이행·백업·복구 원칙

1. 대상은 `legal_intake_lab`과 별도의 로컬 H2 파일로 제한했다. MySQL root 비밀번호는 기존 Git 제외 파일에서 프로세스 안에서 읽었고 출력/명령 인수에 넣지 않았다.
2. `INFORMATION_SCHEMA.COLUMNS/STATISTICS`로 컬럼·NULL·기본값·인덱스 순서와 유니크 여부를 확인했다. 없는 컬럼만 추가했고 동명 인덱스의 다른 정의는 자동 교체하지 않는다.
3. MySQL은 DDL이 암묵적으로 커밋되므로 트랜잭션 rollback을 이행 복구로 설명하지 않는다. 전체 백업을 별도 스키마에 복원해 검증했다. 중복 데이터는 삭제해서 제약을 맞추지 않는다.
4. 기존 H2 웹 콘솔은404였다. 신규 공개 API를 만들지 않고 로컬 JVM attach 방식으로 표/컬럼별 데이터 SQL과 행 해시 manifest를 비공개 경로에 내보냈다. 이 백업은 스키마를 포함하지 않는 데이터 전용이며, 같은 버전/검증된 최신 스키마로 초기화한 새 DB에만 복원한다.
5. 복원 도구는 `.local`의 별도 대상만 허용하고 이미 복원한 대상·초기 Reference 이외의 자료가 있는 대상은 비우지 않는다. 기존 DB를 DROP/TRUNCATE하거나 백업으로 덮어쓰지 않았다.
6. 새 스키마에서 version DEFAULT0을 설정한 뒤 원래 컬럼으로 복원하고, 기존 컬럼 기준 해시·행 수·Reference FK를 대조했다. 숫자 IDENTITY 다음값을 MAX(id)+1로 맞춰 기존ID 충돌을 방지했다.
7. 최종 동결 백업→새 파일 복원/대조→이전 JVM 종료→새8080 시작 순서로 전환했다. 평소 재시작은 정상 종료 도구를 사용한다.

백업은 `backend/guardrail-ready-policy-checker/.local/deployment-2026-10-23/` 아래에 있으며 Git 제외와 Windows 사용자 전용 ACL을 적용했다. MySQL 임시 인증 파일은 종료 후 제거했다. 원문 SQL 백업을 공개 저장소·채팅·검수 화면에 첨부하지 않는다.

복구가 필요하면 현재 신규 데이터를 먼저 별도 보존하고, 새 경로/새 스키마에 백업을 복원해 행 해시와 API를 검증한 뒤 실행 대상을 바꾼다. 단순히 새 컬럼을 DROP하거나 현재 DB 위에 오래된 덤프를 실행하지 않는다. 이행 전 MySQL 복원 검증 스키마는 비교용으로 남겼으며 현재 앱이 사용하지 않는다.

공식 근거: [MySQL DDL의 암묵적 커밋](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html), [mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html), [H2 SCRIPT/RUNSCRIPT](https://h2database.github.io/html/commands.html). H2 SCRIPT의 실행 중 JAR 클래스 로딩 문제가 있어 실제 메모리 DB 백업은 위 데이터 전용 SQL 방식으로 검증했다.

## 3. 새 Postman 계약

| API | 요청 | 응답/재전송/충돌 |
|---|---|---|
| C03 POST /api/intakes | USER 등 Bearer + Idempotency-Key={{intakeRequestKey}} + 기존 접수 JSON | 최초201·재전송201, 같은id, Idempotent-Replayed false/true. 같은 키·다른 내용409 IDEMPOTENCY_KEY_REUSED |
| C05 GET /api/intakes/{id} | 본인/배정/ADMIN Bearer, Body없음 | id·status·version. 이 version을 다음 상태/메모 요청에 사용 |
| C10 PATCH /api/intakes/{id}/status | 담당 LAWYER/ADMIN Bearer, `{"status":"REVIEWED","expectedVersion":0}` | id·previousStatus·status·changed·version.0은 예제이며 실제 조회값 사용. 오래된 다른 변경409 INTAKE_VERSION_CONFLICT |
| C11 POST /api/intakes/{id}/memo | 담당 LAWYER/ADMIN Bearer + Idempotency-Key={{memoRequestKey}}, `{"content":"가상 메모","followUpItems":[],"expectedVersion":1}` | 기존 메모 필드+intakeVersion, 재전송 동일memoId·헤더true. 성공 후 다음 버전 갱신 |
| C19/C20 PATCH /api/incidents/{id}/ack 또는 resolve | ADMIN Bearer, Body없음 | 같은 상태는 변경 없는200. 종결 후ack409 INCIDENT_ALREADY_RESOLVED. 없는ID404 INCIDENT_NOT_FOUND |

서버의 expectedVersion 생략은 하위 호환 경로다. 생략한 직접 API 요청까지 오래된 화면의 수정 방지를 보장한다고 해석하지 않는다. React는 version 없이 저장하지 않는다. 정상 저장 후 GET 실패를 POST/PATCH 재실행 사유로 사용하지 않는다.

409는 `code`, `details.currentStatus/currentVersion/expectedVersion` 등 실제 반환 필드를 사용한다. 초기 접수 시도·메모 시도의 요청 키는 서로 다른 변수이며 intakeId/memoId/incidentId와 구분한다. 화면07=상태·메모, 화면08=상세 기준으로 최신 매핑을 맞췄다.

## 4. 현재 실행본

- URL: `http://127.0.0.1:8080`, React `http://127.0.0.1:5175/intake.html#/login`.
- 활성 프로필: **local-persistent**, DB: **H2 파일**, DDL: **validate**. 기본 프로필의 메모리/create-drop 설정은 별도로 유지한다.
- 릴리스 SHA-256: `7792f10cb64bffe0d44b1e9e6f7b567ba68465c68061f02110170c99f85f96f7`.
- 빌드 JAR 시각: 2026-10-23 19:45:16 KST. 재시작 검증 후 PID13092, 기동19:49:42 KST. 현재 값은 `.local/runtime-logs/server-8080.json`과 evidence JSON이 기준이다.
- 실제 DB 경로: `backend/guardrail-ready-policy-checker/.local/deployment-2026-10-23/runtime/active-db.mv.db`.
- 실사용 검증을 위해 **배포 검수용 가상 문의4번·메모1건·종결 Incident1건**을 추가했고 재시작 후 유지 확인. 기존 문의1~3번은 보존했다.

```powershell
# 정상 종료: 프로세스 이름/PID 시작 시각을 확인하고 JVM shutdown hook을 실행한다.
powershell.exe -NoProfile -File "C:\myLectureWs\legal-ai-intake-security-server\scripts\Stop-LocalServer.ps1" -Port 8080

# 기존 복원 DB로 시작. 빌드 JAR를 SHA별 실행 파일로 복사하고 validate로 기동한다.
powershell.exe -NoProfile -File "C:\myLectureWs\legal-ai-intake-security-server\scripts\Start-LocalServer.ps1" -Port 8080 -DatabasePath "C:\myLectureWs\legal-ai-intake-security-server\backend\guardrail-ready-policy-checker\.local\deployment-2026-10-23\runtime\active-db"
```

`-Initialize`는 새 검수 DB를 만드는 경우에만 사용하며 기존 파일에 사용할 수 없다. 이전 실행 중 JAR를 빌드가 교체하면서 지연 클래스 로딩 오류가 발생했던 문제를 실행 파일 분리로 방지했다. 현재 실행 파일을 덮어쓰거나 이동/삭제하지 않는다.

이번 파일 DB 전환은 문의·자료·검색·메모 등 JPA 데이터 보존이다. Dynamic 세션·비용 예산/예약/정산 메모리 장부와 Micrometer 누적값은 여전히 프로세스 메모리이며 재시작 영속 원장을 구현한 것은 아니다. 실제 OpenAI 키 주입/유료 생성 및 전체 앱의 MySQL 프로필 전환은 이번 작업과 별도다.

## 5. 증거

공개 가능한 요약은 `output/deployment-db-2026-10-23/verification.json`, `active-http.json`, `rehearsal-http.json`, `browser/browser-verification.json`, 독립 JUnit XML, 프런트 검사/빌드 로그다. 원문 백업과 DB 파일은 비공개 `.local`에만 있다. 임시 검수 포트18096/18097/18099는 종료했다. 현재8080·기존5175는 실행 중이다.
