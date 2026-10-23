# 로컬 Prometheus 재사용 · 화면13

기존 설치 `C:/monitoring/prometheus/prometheus.exe`를 그대로 사용한다. 새 다운로드·Grafana 설치는 필요 없다. 원본 `prometheus.yml`과 `data/`는 수정하지 않는다. 새 수집 이력은 프로젝트의 `.runtime/data`에 저장하며 과거 이력을 가져오는 것은 아니다.

1. Spring Boot를8080에서 실행한다. `/actuator/prometheus`와 `/actuator/guardtimeseries`는 기존 ADMIN Basic 계정을 사용한다.
2. 프로젝트 루트 PowerShell에서 아래를 실행한다. 비밀번호는 화면에 보이지 않게 입력한다.

```powershell
$scrapePassword = Read-Host 'Actuator ADMIN password' -AsSecureString
.\monitoring\Start-LocalPrometheus.ps1 -ActuatorUser admin -ActuatorPassword $scrapePassword
```

실행 스크립트는 로컬 암호 파일의 Windows ACL을 현재 사용자로 제한하고 `.runtime/` 전체를 Git에서 제외한다. 비밀번호를 커맨드·URL·로그·추적 문서에 적지 않는다. 포트9090이 이미 사용 중이면 중복 실행하지 않는다. 원래 서버가 가동 중인 환경은 그 서버에 `prometheus.yml`의 legal-ai-intake job과 인증 수집 설정을 병합하고 재사용한다.

Prometheus는 `127.0.0.1:9090`에만 바인딩하고 수집 간격5초, 저장7일 또는256MB를 적용한다. Spring이 다른 주소라면 `prometheus.yml` target을 조정한다. Spring→Prometheus 조회 주소는 `PROMETHEUS_BASE_URL`, job은 `PROMETHEUS_JOB`으로 변경한다. 기본값은 `http://127.0.0.1:9090`, `legal-ai-intake`다. 새 job을 지정했으면 수집 설정과 일치시킨다.

- 준비 상태: `GET http://127.0.0.1:9090/-/ready`
- 대상 확인: `GET http://127.0.0.1:9090/api/v1/targets` → legal-ai-intake health=`up`
- 현재 값: `GET http://127.0.0.1:8080/actuator/prometheus`, ADMIN Basic
- 시간 이력: `GET http://127.0.0.1:8080/actuator/guardtimeseries`, ADMIN Basic, Params/Body 없음
- 화면: `http://127.0.0.1:5175/operator.html#/observability` → 지표 연결
- API 없이 모양 확인: `http://127.0.0.1:5175/operator.html?mock=1#/observability`

새 지표는 최소2회 수집 후 `increase`로 나타난다. 새 카운터의 최초 값이 증가 이력으로 소급 생성되지는 않는다. 관측이 없을 때 실제0으로 채우지 않는다. 신호가 잠잠하면 정상적으로0선이 나타난다.

현재 검수용8080 프로세스는 공급자 호출을 비활성화했다. 실제 LLM 실험은 호출 전 체크리스트와 원래 실행 설정을 사용한다. 상세 증거는 [화면13 통합 검수](../docs/SCREEN13_OBSERVABILITY_REVIEW.md)를 참고한다.
