# 오늘 녹화 시작 순서

> 최신 범위는 [기존8분 + 마케팅 검증1분, 총9분 영상](09M_MARKETING_SECURITY_EDIT.md)입니다. 마케팅07:45–08:45 → 모바일08:45–09:00. 보안·마케팅은 보고서의 과거 실험 증거로 표시하며 무음 검토본입니다.

## 현재 준비됨

- 필수 PDF3종:9쪽 제품/PM,6쪽 대본/편집,5쪽 검증/운영.4:3 가로 다크 태블릿용.
- USER 더미 문의10건, LAWYER 배정·상태·내부 메모, ADMIN 위험 이력.
- 문의1 / 검색 요청1: 로컬 모의 LLM·네이버·Guard를 거친 P03 COMPLETED.
- 기존 Prometheus 실행 파일 재사용. 별도 시연 TSDB/config로 수집하며 원래 TSDB는 열지 않았다.
- Q01 14개, 보안/비용/검색95개, 프런트201개, 시연 필수15개 확인. 과거260개 전체 회귀와 합산하지 않는다.

## 열 주소

- 의뢰인/변호사: http://127.0.0.1:5176/intake.html#/login
- 운영자: http://127.0.0.1:5176/operator.html#/observability
- 모의 공급자 상태: http://127.0.0.1:18083/__demo/status
- Prometheus: http://127.0.0.1:9091

현재18080은 **더미 전용 별도 DB**다. 원래 `active-db.mv.db`는 그대로 보존했다. 화면의 “시연 더미·LLM/검색 모의·실제 과금0”을 녹화에서도 유지한다. 실제 OpenAI 키를 이 서버에 주입하지 않는다.

## 촬영

1. 대본의6개 클립으로 나눠 촬영한다. 전부13화면을 같은 길이로 설명할 필요는 없다.
2. 모바일 장면은 실제 검수한390px 브라우저다. 실제 폰에서는 localhost가 PC를 가리키지 않으며 LAN 바인딩·방화벽·실기기 검수가 추가로 필요하다.
3. 화면13은 지표 연결에서 ADMIN Basic을 별도 입력한다. 패스워드 입력 구간은 녹화에서 제외한다.
4. 401/403/409, Fixed 차단·비용 차단은 Postman/API 보조 클립으로 보여주고 UI 기능처럼 말하지 않는다.
5. ChatGPT에 대본·SRT·컷 길이 수정을 맡기고, 실제 서비스 화면 녹화와 편집은 OBS/Clipchamp에서 한다.

## 재준비·정상 종료

프로젝트 루트에서 실행한다. 실행 중인 서버를 덮어쓰거나 DB를 삭제하지 않는다.

```powershell
./scripts/Build-PresentationDemo.ps1
node scripts/Start-PresentationDemo.cjs
# 다른 PowerShell7 창에서 기존 Prometheus 실행 파일 재사용
./scripts/Start-PresentationPrometheus.ps1
# 이미 실행 중인 데모의 고정 문의/검색 자료 재확인 및 모의 비용 검증
node scripts/Start-PresentationDemo.cjs --seed-only
# 실제 Prometheus 수집에 위험 신호3회 파동을 유한하게 추가(유료 호출 없음)
node scripts/Seed-PresentationMetrics.mjs
node scripts/Verify-PresentationEssentials.mjs
# 이 시연의 검증된 PID/시각만 정상 종료. 파일 DB 보존
./scripts/Stop-PresentationDemo.ps1
```

최초 시연 준비에서 예산 차단 반복20회가 충분하지 않아 seed를40회 이내로 수정하고 재실행해 통과했다. 첫 실패 로그를 보존했으며 최종 판정은 `demo-data.json`과 `essential-verification.json`이다. 비용 장부는 메모리이므로 재시작하면 모의 비용을 다시 준비한다. 위 재준비는 문의 접수 키를 재사용하지만 위험 이력 파동은 새 기록을 추가한다.

## Claude와 작업 분리

OpenAI 설정·실제 호출·모델·단가·실제 공급자 연결은 사용자와 Claude가 진행합니다. Codex는 이 영역의 파일이나 설정을 수정하거나 호출하지 않습니다.

- 실제 연결 작업용:8080 /5175 (현재 모의 시연이 사용하지 않음)
- Codex 모의 시연:백엔드18080 /화면5176 /Prometheus9091 /모의 공급자18083
- 시연 프런트 빌드: `backend/guardrail-ready-policy-checker/.local/presentation-2026-10-25/dist`
- 원래 프런트 dist와 application.properties는 이 분리 작업에서 변경하지 않았습니다.
- 실제 결과가 확인되면 검색/생성 컷만 바꾸고, 모의 검증을 실제 청구 검증으로 표시하지 않습니다.
