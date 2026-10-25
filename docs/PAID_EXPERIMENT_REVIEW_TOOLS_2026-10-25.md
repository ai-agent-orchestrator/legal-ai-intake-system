# ② 비용 대조·③ 실제 검색 1건 검증 도구

## 완료한 개발과 남는 실험

**기존 API·DB·모델·단가는 변경하지 않았다.** 로컬 앱에 로그인하고 기존 C05/C07/P03/C16을 호출하는 별도 검증 도구를 추가했다. OpenAI·네이버 키를 받거나 읽지 않는다. Claude의 실행 서버를 재시작하지 않는다.

- [x] 비용 장부 캡처·오프라인 전후 대조 도구.
- [x] 기존 문의/검색 ID로 실행 1건·저장 결과 재조회 도구.
- [x] 모의/실공급자 구분, 자동 POST 재시도 금지, 서버별 도구 실행 잠금·요청별 영속 intent 기록.
- [x] 모의 자동 검사 19개 및 실제 격리 Spring→모의 LLM/Guard/검색→결과3건→재조회→예약0 확인.
- [x] PowerShell 래퍼 문법 검사 및 실제 읽기 전용 Snapshot 실행.
- [ ] 실제 성공 호출의 전후 장부·usage·공급자 사용량 대조. 이번 작업에서 새 유료 호출은 하지 않았다.
- [ ] 실제 네이버/변협 대상 검색의 적중·관련성·설명 근거 확인. 모의 결과를 실제 추천 품질로 판정하지 않는다.

## 1. 비용 확인 — 생성 없이 먼저 할 수 있는 작업

```powershell
# 실제 앱 서버 포트로 바꾼다. 새 LLM 호출 없이 C16만 읽는다.
.\scripts\Review-PaidExperiment.ps1 -Mode Snapshot `
  -ServerUrl http://127.0.0.1:8080 `
  -OutputDirectory output\paid-review\before
```

앱의 Actuator ADMIN 비밀번호를 **가려진 입력**으로 묻는다. OpenAI 키를 입력하는 곳이 아니다. C16 Basic 인증만 사용한다. Claude가 유료 실험을 완료한 뒤 다른 출력 폴더 `output\paid-review\after`로 다시 실행한다.

```powershell
.\scripts\Review-PaidExperiment.ps1 -Mode Cost `
  -Before output\paid-review\before\snapshot.json `
  -After output\paid-review\after\snapshot.json `
  -Receipt output\paid-review\receipt.json `
  -ProviderUsage output\paid-review\provider-usage.json `
  -OutputDirectory output\paid-review\comparison
```

Cost는 파일만 읽고 네트워크·생성을 수행하지 않는다. Receipt/ProviderUsage는 없으면 생략 가능하며, 이 경우 장부 검사는 하되 실제 청구 대사는 미완료로 표시한다.

- `receipt.json`: `docs/examples/experiment-receipt.template.json`을 복사해 **실제 반환 모델·usageAvailable·토큰 수·costSource·실제/모의 모드**만 옮긴다. 답변 원문·키·토큰 인증값은 넣지 않는다. P03의 기존 응답에는 개별 LLM 모델/토큰 수가 없으므로 값을 추정하지 않는다.
- `provider-usage.json`: `docs/examples/provider-usage.template.json`을 복사한다. OpenAI 사용량에서 실제 확인한 USD 금액·시간창·모델과 해당 창의 다른 작업 부재를 확인한 뒤 `kind`를 `provider_usage_export`로 바꾼다. 단가로 곱한 추정액은 공급자 사용량 증거가 아니다. 템플릿 그대로는 통과하지 않는다.
- 실제 Guard 비용까지 한 수치로 대사한다면 해당 공급자 비용도 포함된 자료에만 `guardIncluded=true`를 쓴다. OpenAI 사용량만으로 다른 공급자의 Guard 비용을 확인했다고 표시하지 않는다.
- 생성은 실제이고 Guard는 모의라면 `providerMode=actual`, `guardMode=mock`. 모의 Guard의 **장부 비용**은 실제 공급자 비교액에서 분리한다. 예산 차단에는 기존 서버 장부 정책이 그대로 적용되며 도구가 이를 변경하지 않는다.
- 같은 서버·같은 프로세스·순서가 맞는 전후 캡처, actual+estimated=accounted, 단계별 합계, 예약 종료, 관측0/결측, 공급자 시간창/모델/금액을 대조한다. 재시작·카운터 감소·다른 예약이 있으면 확정하지 않는다.
- 전역 토큰/비용 지표이므로 **같은 시간에 다른 호출을 하지 않아야** 비용을 해당 실험에 귀속할 수 있다. 도구 밖의 Claude·브라우저 호출까지 잠그는 기능은 아니다.
- 과거 성공 호출의 ‘전’ 캡처가 없으면 현재 비용에서 그 1건을 역산하지 않는다. 현재 Snapshot이나 읽기 전용 검색의 0 증분을 과거 호출 비용 0으로 해석하지 않는다.

`ledgerChecksPassed`는 장부 검사의 결과이고 `billingReconciled`는 별도다. 후자는 실제 공급자 모드·반환 모델·usage 확인·추정 정산 없음·Guard 범위·공급자 비교 자료까지 있어야 true다. 공급자 자료는 사용자가 제공한 파일이며 도구가 OpenAI 관리 API로 진위를 조회하는 것은 아니다.

## 2. 변호사 검색 — 기본은 조회만

화면05에서 **아직 실행하지 않은** 검색 요청을 먼저 기록한다. 문의 응답 `id`와 검색 요청 생성 응답 `id`를 구분한다. 예시의 42/81은 반드시 실제 반환 ID로 바꾼다. 서버 주소도 실제 검색 서버에 맞춘다.

```powershell
# 읽기 전용: 문의·선택 검색·비용만 확인. 외부 검색을 시작하지 않는다.
.\scripts\Review-PaidExperiment.ps1 -Mode Search `
  -ServerUrl http://127.0.0.1:8080 -IntakeId 42 -SearchRequestId 81 `
  -ProviderMode actual -GuardMode mock `
  -OutputDirectory output\paid-search\preview
```

문의 소유자 또는 ADMIN의 **로컬 앱 비밀번호**, 별도의 **Actuator ADMIN 비밀번호**를 가려서 입력한다. GuardMode는 현재 서버가 실제로 연결한 방식으로 선택한다. 기존 `Start-LlmTestServer.ps1`은 검색을 끄는 별도 Chat 시험 서버이므로, 그 서버를 실제 P03 검색 서버로 혼동하지 않는다.

```powershell
# 실제 검색 실행: 동일 ID를 사용한다. 먼저 조회 결과를 보여주고 마지막에 확인 문구를 묻는다.
.\scripts\Review-PaidExperiment.ps1 -Mode Search `
  -ServerUrl http://127.0.0.1:8080 -IntakeId 42 -SearchRequestId 81 `
  -ProviderMode actual -GuardMode mock -Execute `
  -OutputDirectory output\paid-search\run1
```

최종 입력 `ONE_SEARCH_MAY_BILL` 전에는 P03 실행 POST를 보내지 않는다. **P03 HTTP 1회는 LLM 1회가 아니다.** 검색문·최대3개 설명·입출력 Guard와 네이버 두 출처 검색이 연결될 수 있다. 실제 외부 호출 횟수는 기존 C07에 노출되지 않아 결과만으로 ‘두 검색 모두 성공’을 확정하지 않는다.

실행 조건과 복구:

1. loopback 서버, 실제 문의·조건·검색 ID 일치, 신규 PREVIEW_READY·실행 이력 없음, 혼합 검색 정책 확인.
2. 현재 예약0·양수 예산·전체 장부 한도 확인. 이것만으로 일/세션 잔여액 또는 새 호출의 최대 청구액을 보장하지 않는다. 서버의 기존 예산 차단이 적용된다.
3. 서버별 도구 잠금 후 다시 장부 캡처. 요청별 intent를 디스크에 쓴 다음 **P03 POST 1회**.
4. 진행 상태는 GET으로만 최대180초(설정 가능1~600초) 확인. PARTIAL은 결과 보존·별도 설명 재시도 필요로 보고하며 자동 유료 재시도하지 않는다.
5. terminal 상태와 저장 결과 ID를 재조회하고 비용 후 캡처. 정상 종료·예약0이면 도구 잠금만 해제한다. 요청 intent는 유지한다.
6. 응답 유실·시간 초과·정산 미종료 때는 자동 실행 재시도 금지. 같은 ID를 **읽기 전용**으로 재조회한다. `.local/experiment-review/`의 intent와 server lock은 확인 없이 삭제하지 않는다. 담당자가 종료·예약0·이전 실행 여부를 확인한 뒤 잠금 해제를 판단한다.

이 도구의 잠금은 앱 서버의 다른 클라이언트 실행을 막지 않는다. 동시에 다른 유료 실험을 하지 않는다. 스크립트 자체는 공급자 청구 상한이 아니다.

## 3. 결과 확인

`search-review.json`에서 확인한다.

- 정확한 intakeId / conditionId / searchRequestId / 저장 result ID 연결.
- status, 결과 수(최대3), URL 안전성·중복·변협 출처 라벨과 실제 도메인 일치, 제목/설명 존재 여부.
- 부분 출처 장애·검색 실패·빈 결과를 구분. NO_RESULTS는 기술 흐름 종료일 수 있지만 추천 품질 성공은 아니다.
- `qualityReview`는 항상 수동 확인 대기다. 실제 프로필 여부·희망 지역/분야·경력 근거·설명이 원문에 맞는지는 사람이 확인한다.
- 파일에는 사건 원문·고객 연락처·JWT/Basic·원문 제목/설명·URL query를 저장하지 않는다. 결과 sourceUrl은 query 없는 확인용 주소라 개별 프로필 링크로 완전하지 않을 수 있다. 원문 전체 URL은 기존 화면05/C07에서 확인한다.

실행 후 기술 검사 미완료는 종료코드2, 도구/인증/안전 조건 오류는1이다. 읽기 전용·오프라인 명령의 종료0은 실제 과금 검증까지 완료됐다는 뜻이 아니다. 각 결과의 boolean과 미완료 사유를 확인한다.

## 4. 이번 모의 실행 증거

- `node --test scripts/experiment-review.test.mjs` **19개 통과**. 결측/0·서버 재시작·단계 합계·실제/모의 Guard 비용 분리·잘못된 모델/시간창·1000배 금액 차이·위험 URL·ID 오류·확인 없는 실행 차단·응답 유실·프로세스 간 재실행 방지·동시 도구 잠금·오프라인 대조 포함.
- `Verify-ExperimentReviewDemo.mjs`: 격리 18080 Spring + 18083 모의 공급자에서 P03 1회, **COMPLETED·결과3건·변협 도메인2/일반 웹1·저장 재조회·예약0·장부 합계 일치**.
- 증거: `output/paid-experiment-review-demo-2026-10-25/verification.json`, `before.json`, `after.json`, `search-review.json`. 이 비용은 **모의 usage와 설정 단가로 계산한 장부 숫자**이며 실제 청구는0이다. `billingReconciled=false`를 유지했다.
- PowerShell Snapshot은 같은 격리 서버 C16을 실제 조회했다. 시스템 실행 정책·비밀키 설정은 변경하지 않았다.

Node 직접 실행이 필요하면 `Review-PaidExperiment.mjs --action cost --before ... --after ...`를 사용할 수 있다. Snapshot/Search의 앱 JWT와 Actuator 인증은 래퍼가 프로세스 환경으로만 전달하고 종료 후 복원한다. 비밀번호·JWT를 명령 인수나 채팅에 넣지 않는다.
