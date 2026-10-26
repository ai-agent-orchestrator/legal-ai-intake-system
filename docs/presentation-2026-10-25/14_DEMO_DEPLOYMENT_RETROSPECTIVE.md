# 시연·배포 서버 체크리스트

## 회고 | 2026-10-26

오늘 오류 11건 중 코드 버그는 2건뿐이고, 나머지 9건은 실행 위치·순서·접속 주소 등 실행 환경 문제였습니다.

> 핵심 교훈: "내 PC에서 된다"가 "다른 PC에서 된다"를 보장하지 않는다.

오류 건수와 수정 완료 상태는 제공받은 회고 기록 기준입니다. 이 문서는 실행 절차를 정리한 것이며, 실제 시연 직전 점검은 별도로 수행합니다.

## 오늘 겪은 오류

| 증상 | 원인 | 예방 |
| --- | --- | --- |
| 스크립트 경로 오류 | 관리자 창이 C:\WINDOWS\system32에서 시작 | 먼저 cd로 작업 폴더 이동 |
| vite 명령 없음 | 새 작업 폴더에 node_modules 없음 | npm install 먼저 실행 |
| npm run dev 실패 | 저장소 루트에서 실행 | frontend\legal-procedure-viewer에서 실행 |
| 127.0.0.1:5176 안 열림 | Vite가 localhost / ::1에만 바인딩 | --host 0.0.0.0으로 실행 |
| 옛 Previewer 화면 | 주소를 /로 열음 | /intake.html 또는 /operator.html 사용 |
| 외부검색 버튼 회색 | CSS가 항상 흐리게 표시 (코드 버그) | :disabled일 때만 회색 - 수정 완료 |
| 외부 PC 로그인 실패 | CORS 허용 출처에 LAN 주소 없음 | 시작 전 CORS_ALLOWED_ORIGIN_PATTERNS 설정 |
| 방화벽 규칙 거부 | 일반 PowerShell에서 실행 | 관리자 PowerShell에서 실행 |
| 외부 PC 문의 접수 실패 | crypto.randomUUID는 HTTPS·localhost에서만 사용 가능 (코드 버그) | 대체 UUID 생성 - 수정 완료 |
| 다운로드 파일 없음 | 오늘 받은 파일을 확인하지 않음 | 받은 파일 이름·날짜 확인 |
| 비용 숫자 초기화 | 메모리 예산 장부가 재시작 시 0으로 초기화 | 실제 비용은 OpenAI 사용량 화면에서 확인 |

## 사전 준비 (시연 전날)

시작 스크립트의 STOP 조건을 미리 없애는 단계입니다.

- [ ] **작업 폴더**: cd로 프로젝트 작업 폴더로 이동합니다. system32에서 시작하지 않습니다.

- [ ] **최신 코드**: git status가 깨끗한지 확인합니다. 패치는 적용 전에 검사합니다.

```powershell
git status
git apply --check <패치파일>
```

- [ ] **백엔드 JAR**: 백엔드 폴더에서 실행 파일을 빌드합니다.

```powershell
cd backend\guardrail-ready-policy-checker
.\gradlew.bat bootJar
```

- [ ] **프론트 의존성**: 프론트 폴더에서 먼저 의존성을 설치합니다.

```powershell
cd frontend\legal-procedure-viewer
npm install
```

- [ ] **키 파일**: .local\secrets\openai-key.dpapi가 있어야 합니다. 없으면 Set-LocalOpenAiKey.ps1을 실행합니다.

- [ ] **환경변수 충돌**: Windows 환경변수에 OPENAI_API_KEY·LLM_API_KEY가 없어야 합니다. 있으면 시작 스크립트가 STOP으로 멈춥니다.

- [ ] **포트**: 18090(Spring)·18181(모의)·18282(브리지)·5176(Vite)가 비어 있는지 확인합니다.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 18090,18181,18282,5176
```

- [ ] **서버 IP**: ipconfig로 서버 PC의 IP를 확인합니다. 오늘 기록: 192.168.10.48. IP가 달라지면 뒤의 예시도 바꿉니다.

```powershell
ipconfig
```

- [ ] **남은 크레딧**: OpenAI 사용량 화면에서 남은 크레딧을 확인합니다.

## 기동 순서 (창 4개, 이 순서 그대로)

각 단계는 앞 단계가 실행 중이어야 시작됩니다. 순서가 바뀌면 스크립트가 STOP으로 멈춥니다.

- [ ] **창 ① | 모의 provider**: Guard용 무료 모의 provider입니다. 저장소 루트에서 실행하며 18181 포트를 사용합니다.

```powershell
node frontend\legal-procedure-viewer\scripts\p03-mock-providers.mjs
```

이 창을 켜 둔 상태에서 다음 창을 시작합니다.

- [ ] **창 ② | 검색 브리지**: 실제 검색은 유료입니다. 저장소 루트에서 실행하고, 확인 질문에 y를 입력합니다. 18282 포트를 사용합니다.

```powershell
.\scripts\Start-OpenAiSearchBridge.ps1 -MaxSearches 20
```

최대 검색 횟수는 20회입니다. 브리지가 실행된 뒤 Spring을 시작합니다.

- [ ] **창 ③ | Spring**: 저장소 루트에서 CORS를 먼저 설정한 뒤 같은 창에서 서버를 시작합니다.

```powershell
$env:CORS_ALLOWED_ORIGIN_PATTERNS = (
  'http://127.0.0.1:[*],http://localhost:[*],' +
  'http://192.168.10.48:[*]'
)
.\scripts\Start-LlmTestServer.ps1 -Mode RealLlm `
  -SearchBridgePort 18282
```

READY: health=UP / lawyer search = OpenAI web search bridge 두 줄을 확인합니다.

- [ ] **창 ④ | Vite**: frontend\legal-procedure-viewer 폴더에서 실행합니다.

```powershell
cd frontend\legal-procedure-viewer
$env:API_PROXY_TARGET='http://127.0.0.1:18090'
npm run dev -- --host 0.0.0.0 --port 5176
```

Network: http://192.168... 줄이 보이는지 확인합니다.

CORS나 프록시 값을 바꾸면 해당 서버를 끄고 다시 시작해야 반영됩니다. Spring을 재시작하면 메모리 DB와 예산 장부도 초기화됩니다.

## 외부 PC 접속

외부에는 5176 하나만 엽니다. Spring(18090)과 브리지(18282)는 127.0.0.1에 두고 Vite 프록시를 통해 연결합니다.

- [ ] **방화벽**: 관리자 PowerShell에서 5176만 엽니다. 네트워크 프로필과 -Profile을 맞춥니다.

```powershell
New-NetFirewallRule -DisplayName 'demo-5176' -Direction Inbound `
  -Protocol TCP -LocalPort 5176 -Action Allow -Profile Private
Get-NetConnectionProfile
```

- [ ] **CORS와 바인딩**: CORS에 서버 LAN 주소가 있고, Vite가 --host 0.0.0.0으로 실행 중이어야 합니다. IP 변경 시 CORS도 변경하고 Spring을 재시작합니다.

- [ ] **시연 PC에서 접속**: 오늘 서버 IP 기준입니다. 시연 PC와 서버 PC가 같은 Wi-Fi 또는 스위치에 연결돼 있어야 합니다.

```powershell
http://192.168.10.48:5176/intake.html
http://192.168.10.48:5176/operator.html
```

- [ ] **로그인부터 쓰기까지**: 시연 PC에서 로그인과 문의 접수까지 끝까지 확인합니다. 읽기만 되고 쓰기는 실패할 수 있습니다. Ctrl+F5로 새 코드도 받습니다.

브라우저는 HTTP LAN 주소를 보안 연결로 보지 않아 일부 기능을 제한합니다. 반드시 다른 PC에서 끝까지 확인합니다.

방화벽 규칙의 -Profile은 네트워크 프로필과 맞아야 합니다. Get-NetConnectionProfile 결과가 Public이면 -Profile Public으로 바꿉니다.

## 시연 직전 스모크 테스트 (10분)

시연에서 보여 줄 흐름을 시연 PC에서 한 번씩 끝까지 확인합니다. 유료 검색은 1회(약 $0.01)만 사용하는 계획이며 실제 청구액은 사용량 화면에서 확인합니다.

- [ ] **서버 상태**: 서버 PC에서 health가 UP인지 확인합니다.

```powershell
Invoke-RestMethod http://127.0.0.1:18090/actuator/health
```

- [ ] **사용자 흐름**: 시연 PC에서 사용자 로그인 → 선택형 질문 → 문의 접수까지 성공합니다.

- [ ] **실제 LLM 안내**: 시연 PC에서 AI 간단 안내에 실제 LLM 답변이 나오는지 확인합니다.

- [ ] **외부 검색 1회**: 외부검색 버튼이 진한 파란색인지 확인하고, 변호사 검색 1회 후 출처 링크가 나오는지 확인합니다. 최대 90초 기다립니다.

- [ ] **정책 차단**: 탈옥 문장 1회가 BLOCKED_JAILBREAK으로 차단되는지 확인합니다.

- [ ] **운영자 화면**: 관리자 로그인 후 /operator.html의 화면13 그래프와 Incident가 보이는지 확인합니다.

- [ ] **남은 검색 횟수·비용**: 브리지 창의 검색 횟수와 비용 로그를 확인하고, 남은 횟수가 충분한지 확인합니다.

- [ ] **화면 노출 점검**: 비밀번호·키·민감한 터미널 창이 화면에 노출되지 않도록 확인합니다.

하나라도 실패하면 오늘 겪은 오류 표에서 증상을 찾습니다. 해결되지 않으면 시연 영상으로 대체합니다.

## 종료·정리

시연이 끝나면 열어 둔 포트와 유료 프로세스를 바로 닫습니다.

- [ ] **검색 브리지 종료**: 총 검색 횟수·비용을 기록합니다.

```powershell
.\scripts\Start-OpenAiSearchBridge.ps1 -Stop
```

- [ ] **Spring 종료**: 서버를 종료합니다.

```powershell
.\scripts\Stop-LlmTestServer.ps1
```

- [ ] **Vite·모의 provider 종료**: 각 실행 창에서 Ctrl+C를 누릅니다.

- [ ] **방화벽 규칙 삭제**: 관리자 PowerShell에서
삭제합니다.

```powershell
Remove-NetFirewallRule -DisplayName 'demo-5176'
```

- [ ] **포트 종료 확인**: 시연용 포트가 모두
닫혔는지 확인합니다.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 18090,18181,18282,5176
```

- [ ] **실제 비용 확인**: OpenAI 사용량 화면에서 오늘 사용한 비용을 확인합니다.

## 운영 배포로 갈 때 추가할 것

HTTPS와 고정 배포 환경으로 실행 환경 차이를 줄입니다.

- [ ] **HTTPS**: 서비스를 HTTPS로 제공해 secure context 문제를 제거합니다.

- [ ] **정적 프론트 배포**: npm run build 결과물을 정적 배포합니다. Vite 개발 서버 대신 배포용 환경을 사용합니다.

- [ ] **동일 도메인**: 프론트와 API를 같은 도메인으로 묶어 CORS 설정을 최소화합니다.

- [ ] **JWT 비밀값**: JWT_SECRET을 비밀 저장소에서 주입합니다. 개발용 기본값을 사용하지 않습니다.

- [ ] **계정·관리자 인증**: 데모 계정을 제거하고 회원 DB와 관리자 MFA를 적용합니다.

- [ ] **업무·예산 영속 저장**: H2 메모리 DB를 영속 DB로 전환하고 예산 장부도 영속 저장합니다.

- [ ] **H2 콘솔**: 운영 배포에서 H2 콘솔을 비활성화합니다.

- [ ] **배포 전 회귀 검증**: 프론트 테스트·빌드와 백엔드 테스트를 모두 통과합니다.

```powershell
npm run test:intake
npm run test:operator
npm run build
.\gradlew.bat test
```

테스트 실행 위치: 프론트 명령은 frontend\legal-procedure-viewer, Gradle 명령은 backend\guardrail-ready-policy-checker입니다.
