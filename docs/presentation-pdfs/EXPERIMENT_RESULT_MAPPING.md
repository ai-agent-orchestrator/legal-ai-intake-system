# 실험 제목·결과·비용 증거 매칭표

원본: Legal_AI_Intake_비용통제_실험보고서_2026-10-25.pdf (2026-10-25). 새 서버 실행·유료 호출 없이 기존 기록을 재편집했습니다.

각 PDF는 요약 매칭 목록 다음에 실험 조건·확인 의미·관측 결과·원본 캡처·비용을 한 묶음으로 배치합니다.

| 실험 | 제목·확인 의미 | 모드 | 관측 결과 | 실험 증거 | 비용·OpenAI 대조 |
|---|---|---|---|---|---|
| A1 | 입력 101자 - 유료 호출 전 입력 검증 / 입력 한도 위반이 유료 클라이언트까지 도달하지 않는지 확인합니다. 응답의 세부 오류 코드 필드는 해당 캡처에서 미확인입니다. | 모의 | HTTP 400. DTO 요청 검증에서 중단됐고 비용 장부는 0으로 유지됐습니다. | 원문 7쪽 캡처 5 / [Error Stop Flow PDF](ERROR_STOP_FLOW_DARK_TABLET.pdf) | 앱 장부 증가 0. 모의 공급자 실험이므로 OpenAI 과금 증가를 직접 측정한 실험은 아닙니다. |
| A2 | 비밀 요청 - Fixed Guard 사전 차단 / 비밀 요청을 규칙으로 차단해 답변 생성 경로를 실행하지 않는지 확인합니다. | 모의 | BLOCKED_SECRET_REQUEST, model=none. 비용 장부는 0입니다. | 원문 8쪽 캡처 6 / [Error Stop Flow PDF](ERROR_STOP_FLOW_DARK_TABLET.pdf) | 앱 장부 증가 0. OpenAI 실호출·청구 대조 대상이 아닌 모의 차단 실험입니다. |
| A3-1 | 같은 세션 비밀 요청 반복 - 누적 차단 / 같은 세션의 위험 요청을 기억하고 반복 시 세션 차단으로 전환하는지 확인합니다. 요청 80점 권고가 아니라 대화 누적 트랙 실험입니다. | 모의 | DYNAMIC_SESSION_BLOCKED / BLOCKED_SECRET_REQUEST. 비용 장부 0입니다. | 원문 8쪽 캡처 7 / [Dynamic Guard PDF](DYNAMIC_GUARD_DARK_TABLET.pdf) | 앱 장부 증가 0. 모의 실험이므로 OpenAI 비용 증가 실측은 해당하지 않습니다. |
| A3-2 | 차단된 세션의 정상 질문 - 차단 유지 / 위험 반복 후 정상 문장으로 바꿔 차단을 우회할 수 없는지 확인합니다. | 모의 | DYNAMIC_SESSION_BLOCKED. 정상 질문으로 바꿔도 기존 세션 차단이 유지됐습니다. | 원문 9쪽 캡처 8 / [Dynamic Guard PDF](DYNAMIC_GUARD_DARK_TABLET.pdf) | 앱 장부 증가 0. OpenAI 실호출·청구 대조 대상이 아닌 모의 실험입니다. |
| A4 | 세션 예산 부족 - 첫 유료 단계 차단 / 세션 잔여 예산 부족을 확인합니다. 원본 캡처는 수정 전 input_guardrail 단계의 차단 기록입니다. | 모의 | COST_LIMIT_BLOCKED / SESSION_BUDGET_EXCEEDED. 장부 0입니다. | 원문 9쪽 캡처 9 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 앱 장부 증가 0. 모의 공급자이며 OpenAI 과금 실측은 아닙니다. |
| A5 | 일일 예산 부족 - 첫 유료 단계 차단 / 일일 잔여 예산 부족을 확인합니다. 원본 캡처는 수정 전 input_guardrail 단계의 차단 기록입니다. | 모의 | COST_LIMIT_BLOCKED / DAILY_BUDGET_EXCEEDED. 장부 0입니다. | 원문 10쪽 캡처 10 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 앱 장부 증가 0. 모의 실험이며 OpenAI 과금 증감 대조 대상은 아닙니다. |
| A6 | 월 예산 부족 - 첫 유료 단계 차단 / 해당 실행본의 월 예산 제한을 확인합니다. 메모리 장부이므로 재시작을 넘어선 공급자 월 청구 한도를 증명하지 않습니다. | 모의 | COST_LIMIT_BLOCKED / MONTHLY_BUDGET_EXCEEDED. 장부 0입니다. | 원문 11쪽 캡처 11 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 앱 장부 증가 0. 원본은 모의 input_guardrail 차단 캡처입니다. |
| A7 | 두 번째 요청 예산 부족 - 생성 후 차단 개선 / 수정 전후 예산 조건이 다릅니다. 단순 비용 절감률로 비교하지 않습니다. 원본 보고서의 claude/request-budget-precheck 재실험 증거이며 현재 작업 소스 병합 증거는 아닙니다. | 모의·별도 수정 브랜치 | 수정 전 답변 2회분 $0.0000306 → 수정 후 1회분 $0.0000153. 수정 후 두 번째 요청은 request_precheck에서 차단, reserved=0입니다. | 원문 13쪽 캡처 12 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 모의 장부 누적 $0.00003645 → $0.0000192. 실제 OpenAI 비용 절감 실측이 아닙니다. 수정 전은 원문 텍스트 기록만 있습니다. |
| V02 | 실제 OpenAI 단독 생성 - 연결·토큰 확인 / 키 인증과 실제 생성 가능 여부를 구분합니다. 이 호출은 앱 Guard 포함 전체 흐름 검증과는 별개입니다. | 실제 LLM | 보고서 기록: 입력 14 / 출력 1토큰. 모델 생성 성공을 확인했습니다. | 원문 5쪽 캡처 1 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 토큰 기준 계산 $0.0000027. OpenAI 화면 누적 요청 1회 / 입력 14토큰. Spend 표시만으로 이 소액을 직접 읽을 수는 없습니다. |
| V02~V03 | 실제 앱 생성 - usage 정산·예약 종료 / 앱 경로의 실제 응답·정산을 확인합니다. 해당 앱 호출 단독 터미널 캡처는 별도 제공되지 않아 원문 표와 공급자 누적 화면을 연결합니다. | 실제 LLM·모의 Guard | 입력 59 / 출력 64토큰. costSource=REPORTED, 호출 후 reserved=0으로 기록됐습니다. | 원문 5쪽 캡처 3 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 답변 단계 계산 $0.00004725. OpenAI 누적 요청 1→2 / 입력 14→73(+59). Guard는 모의이며 실제 공급자 비용에 합산하지 않습니다. |
| B2 | 시간 제한 1ms - 오류 후 추정 정산 / 응답 usage가 없을 때 비용을 0으로 단정하지 않고 예약액 기준 추정 정산하는지 확인합니다. 실제 청구액과 추정 장부를 분리합니다. | 실제 LLM 설정·미도달 관측 | fallback=true, LLM_UNAVAILABLE, model=unknown. 원문 기록상 답변 추정 비용 $0.0000834, 장부 $0.00008535, reserved=0입니다. | 원문 14쪽 캡처 14 / [Error Stop Flow PDF](ERROR_STOP_FLOW_DARK_TABLET.pdf) | 보고서의 OpenAI 누적 요청·입력은 증가하지 않았습니다. 이 관측을 시간 초과가 항상 무료라는 보장으로 일반화하지 않습니다. |
| C1-16 | 출력 상한 16 - 비용 제한과 품질 손실 / 출력 상한은 비용뿐 아니라 안내 품질도 제한합니다. C1-300은 실행 기록이 없어 검증 완료로 표시하지 않습니다. | 실제 LLM·모의 Guard | gpt-4o-mini-2024-07-18, 입력 59 / 출력 16토큰, REPORTED. 응답은 문장 중간에서 끊겼습니다. | 원문 16쪽 캡처 16 / [Cost Control PDF](COST_CONTROL_DARK_TABLET.pdf) | 답변 단계 계산 $0.00001845. OpenAI 누적 요청 2→3 / 입력 73→132(+59). 확인된 3회 합계 입력132·출력81, 계산 $0.0000684; 화면 Spend는 $0.00 반올림입니다. |

## 확인 범위

- V02·V02~V03·C1-16의 확인분: 입력 132, 출력 81토큰, 보고서 단가로 계산한 $0.0000684. OpenAI 화면 Spend $0.00은 반올림 표시입니다.
- 앱 경로의 Guard는 모의입니다. Guard 장부 수치를 실제 OpenAI 청구액에 더하지 않습니다.
- A1~A7은 모의 실험입니다. 장부 0은 해당 모의 실행의 비용 통제 결과이며, 실제 키로 실행한 차단 검증과 다릅니다.
- A7 수정 전·후 예산이 다르고 수정 전 원본 캡처가 없습니다. 개선 결과는 별도 Claude 수정 브랜치 증거이며 현재 작업 소스의 병합 완료로 해석하지 않습니다.
- B1·C1-300은 기록이 없어 미검증입니다. B2의 미증가 관측은 모든 timeout에서 청구가 없다는 보장이 아닙니다.
- 원본 이미지 내용·픽셀을 보존하고 새 페이지에 재배치했습니다. 키·서버 설정은 읽거나 변경하지 않았습니다.
- 편집 원본: EXPERIMENT_RESULT_MAPPING.json. 재생성: python scripts/Build-ExperimentEvidencePdfs.py --source <원본 보고서 PDF 경로>
