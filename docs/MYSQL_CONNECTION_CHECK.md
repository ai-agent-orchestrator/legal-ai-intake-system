# MySQL 연결 준비와 Postman 1차 실험

> 2026-10-23 D01 갱신: 실제 MySQL8.0.46의 legal_intake_lab에 추가 컬럼8개·유니크2개를 반영하고 백업 복원 및 SQL 무결성을 검증했다. 현재 앱8080은 자료 보존을 위해 파일 H2/local-persistent를 사용한다. 아래 2026-10-16 준비 절차·연결 상황은 당시 기록이며 [최신 DB·실행본 보고서](DB_DEPLOYMENT_REVIEW_2026-10-23.md)를 우선한다.

기준일: 2026-10-16. 사용자의 **root 계정**으로 프로젝트 전용 DB `legal_intake_lab`을 사용한다. 별도 수업용 계정이나 GRANT 절차는 필요하지 않다. 오늘은 서버 연결을 준비·확인하고, 문의 등록부터 이어지는 Postman 실험은 10월 17일에 진행한다.

## 1. 확인 대상

| 확인 대상 | 확인 방법 | 완료 기준 |
|---|---|---|
| IntelliJ → MySQL | root Data Source → Test Connection → SQL Console | 연결 성공, DB·계정 확인 |
| Spring Boot → MySQL | `mysql` 프로필로 실행 | 실제 JDBC 연결, JPA 테이블 준비, 서버 기동, Reference API 조회 |
| Postman → API → JPA → MySQL | 문의 등록 → API·SQL 조회 → 재시작 후 재조회 | intakeId와 DB 행 일치, 데이터 유지 |

IDE와 서버의 연결 설정은 각각 지정한다. 서버는 `mysql` 프로필을 활성화해야 MySQL을 사용한다.

## 2. 실행 설정

| 항목 | 기본 실행 | MySQL 실험 |
|---|---|---|
| 프로필 | 지정 없음 | `mysql` |
| DB | H2 메모리 | MySQL `legal_intake_lab` |
| Host / Port | 해당 없음 | `127.0.0.1:3306` |
| 계정 | `sa` | **root** |
| DDL | `create-drop` | `update` |
| 재시작 후 DB 데이터 | 초기화 | 유지 |
| H2 Console | 사용 | 비활성화 |

[MySQL 프로필](../backend/guardrail-ready-policy-checker/src/main/resources/application-mysql.properties)의 기본 JDBC URL:

```text
jdbc:mysql://127.0.0.1:3306/legal_intake_lab?connectionTimeZone=Asia/Seoul&createDatabaseIfNotExist=true
```

- root로 접속해 전용 DB가 없으면 생성하고, JPA가 테이블을 준비한다. 기존 전용 DB가 있으면 사용한다.
- [01_PREPARE_LAB.sql](mysql/01_PREPARE_LAB.sql)은 IntelliJ에서 DB를 먼저 만들고 싶은 경우의 선택 사항이다. 권한 부여나 계정 생성은 하지 않는다.
- MySQL JDBC 드라이버를 Gradle에 추가했다. 기존 Spring Boot의 의존성 관리에 따른 현재 버전은 Connector/J 9.7.0이다.
- `update`는 로컬 실험용 테이블 준비 설정이다. 운영 DB 마이그레이션은 별도다.
- Dynamic Guard·Cost Control의 메모리 상태까지 MySQL에 저장하는 설정은 아니다.

## 3. 사용자가 root 비밀번호 입력

백엔드 폴더의 [.mysql-local.properties](../backend/guardrail-ready-policy-checker/.mysql-local.properties)를 열고 root 비밀번호를 입력한 후 저장한다.

```properties
spring.datasource.username=root
spring.datasource.password=자신의_root_비밀번호
```

이 파일은 Git 제외를 확인했다. 비밀번호를 채팅·문서·Collection에 적지 않는다. 값에 따옴표를 씌우지 않으며, 비밀번호에 역슬래시가 있으면 properties 형식에서 `\\`로 입력한다.

환경변수 `MYSQL_PASSWORD`를 사용할 수도 있다. 로컬 파일에 비밀번호 항목이 있으면 그 값이 우선하므로, 환경변수로 전환할 때는 해당 항목을 제거한다. 주소·계정 변경이 필요하면 `MYSQL_URL`, `MYSQL_USER`를 사용할 수 있다. 이 PC의 로컬 파일은 root 계정을 직접 지정한다.

## 4. Spring Boot 실행

```powershell
Set-Location C:\myLectureWs\legal-ai-intake-security-server\backend\guardrail-ready-policy-checker
.\gradlew.bat bootRun --args="--spring.profiles.active=mysql"
```

IntelliJ 실행 구성은 Gradle Reload 후 Active profiles에 `mysql`을 넣는다. 해당 입력란이 없는 구성은 Program arguments에 `--spring.profiles.active=mysql`을 넣는다. 작업 폴더는 위 백엔드 폴더로 맞춘다.

`mysql` 프로필·Hikari 연결·EntityManagerFactory 초기화·`Started ...Application`을 확인하고 실제 HTTP 응답도 확인한다. DevTools에서는 앱 기동 실패 후에도 Gradle의 `BUILD SUCCESSFUL`이 나올 수 있으므로 이 문구만으로 성공을 판정하지 않는다. DDL 오류 시 기동을 중단하도록 설정했다.

## 5. IntelliJ 연결

기존 root 연결을 사용하거나 Database → New → Data Source → MySQL에서 설정한다.

| 필드 | 값 |
|---|---|
| Name | Legal Intake MySQL Lab |
| Host | `127.0.0.1` |
| Port | `3306` |
| User | **root** |
| Password | 사용자의 root 비밀번호 |
| Database | `legal_intake_lab` |

Test Connection 후 SQL Console에서 [02_VERIFY_CONNECTION.sql](mysql/02_VERIFY_CONNECTION.sql)을 실행한다. 서버 첫 기동 후 테이블 목록을 Refresh한다.

```sql
SELECT DATABASE(), CURRENT_USER(), VERSION(), @@port;
```

DB가 `legal_intake_lab`, 계정이 root인지 확인한다. 앱이 확인한 로컬 서버는 `DESKTOP-4C5OS21:3306`이다.

## 6. 내일 Postman 실험

[병렬형 HTTP 흐름도](postman-http/README.md)와 [API 기능서](API_STATUS_SPEC.md)의 예상 응답을 사용하고 MySQL 행을 함께 확인한다.

| 순서 | 실행 | 기록 |
|---|---|---|
| 1 | MySQL 프로필 서버 실행 | 프로필·포트·DB |
| 2 | 로그인 → 문의 등록 C03 | 201, token·intakeId·sessionId |
| 3 | 문의 상세 C05·목록 C09 | 200, 입력값·ID 일치 |
| 4 | SQL로 문의·자료 종류·검색 조건 조회 | 같은 intakeId에 연결된 행 |
| 5 | 검색 요청 C06 → 상세 C07·이력 P02 | searchRequestId·conditionId·intakeId 연결 |
| 6 | 메모·상태·파일·이벤트 개별 실험 | 정상·실패 응답, 저장·차단 결과 |
| 7 | 같은 프로필로 서버 재시작 → 재조회 | 같은 intakeId 유지 |

실제 외부 검색과 SSE 정산 예외 보완은 기존 보류 범위를 유지한다.

## 7. 오류 확인

| 결과 | 확인할 것 |
|---|---|
| 1045 Access denied | root 비밀번호·접속 host |
| 1049 Unknown database | 기본 URL의 DB 자동 생성 옵션, URL을 별도로 덮어썼는가 |
| 통신 실패 | MySQL 서비스·host·3306 포트 |
| H2로 실행 | `mysql` 프로필 활성화 |
| DDL·타입 오류 | 실제 MySQL 생성 SQL·컬럼 타입 |
| 재시작 후 데이터 없음 | 같은 DB·프로필인지, create-drop으로 덮어썼는가 |

DB 접속 오류 뒤에 `Unable to determine Dialect without JDBC metadata`가 나오면 먼저 최초 접속 오류를 해결한다.

## 8. 실제 확인 상태

| 항목 | 결과 |
|---|---|
| MySQL 서비스·3306 | 실행·열림 확인 |
| MySQL 버전 | 8.0.46 |
| root 사용 | 사용자 확정, 프로필·로컬 입력 파일·SQL 반영 |
| root 비밀번호 | 사용자 입력 완료. 로컬 파일 Git 제외 확인 |
| 프로젝트 DB | `legal_intake_lab` 접속 확인, utf8mb4 / utf8mb4_0900_ai_ci |
| 실제 JDBC 계정 | **root@localhost**, MySQL 8.0.46 / TCP 3306 |
| JPA 테이블 | **20개 확인** |
| 기존 자동 테스트 | 드라이버 추가 후 **92개 통과, 실패 0·오류 0**. H2 기준 |
| Spring Boot → MySQL(root) | **연결·EntityManagerFactory 초기화·서버 기동 성공** |
| Health | **HTTP 200, 전체 UP / db UP / DB 제품 MySQL** |
| Reference API의 MySQL 조회 | `GET /api/legal/procedures` **HTTP 200, 4건**. 요청 로그 SQL 1회 확인 |
| 기본 DB 데이터 | 법률 절차 4건 / 단계 15건 / 양식 4건 |
| 종료·재시작 후 유지 | 종료 중 직접 JDBC 조회와 재시작 후 API·JDBC 조회에서 같은 기본 데이터 수 확인. Seed 중복 증가 없음 |
| 파일 컬럼 | MySQL `longblob`, 용량 4,294,967,295바이트. 실제 5 MiB 파일 업로드 실험은 내일 |
| IntelliJ root 연결 | 사용자 연결 가능 확인. 프로젝트 DB 조회 결과는 별도 확인 |
| 수동 Postman | 10월 17일 예정 |

연결 점검은 임시 HTTP 포트 **18080**에서 수행했고 점검 서버는 종료했다. 내일 기본 실행 명령은 8080을 사용하므로 Postman baseUrl을 실제 실행 포트에 맞춘다. 현재 문의는 0건이며 오늘 문의 등록 실험을 통과한 것으로 기록하지 않는다.

비밀번호를 제외한 실제 조회 결과와 HTTP 상태는 [연결 확인 기록 JSON](mysql/CONNECTION_CHECK_2026-10-16.json)에 저장했다. H2 자동 테스트, 실제 MySQL 연결 점검, 내일 수동 Postman은 각각 구분한다.

## 9. 설정 참고

- [Spring Boot SQL Database 설정](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [Spring Boot 데이터 초기화](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [MySQL Connector/J 연결 옵션](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-connection.html)
- [IntelliJ MySQL 연결](https://www.jetbrains.com/help/idea/mysql.html)
