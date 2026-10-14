# Screen Design Draft

Project: Security-Aware Legal AI Intake System  
Version: Draft v0.1  
Date: 2026-10-14  
Purpose: 화면 정의서 초안 / 발표 산출물 / 구현 범위 조정 기준

## 1. Screen Design Goal

이 프로젝트의 화면은 단순한 챗봇 UI가 아니라, 법률 문의가 고객 입력에서 변호사 검토, 보안/비용 통제, 운영 dashboard까지 이어지는 흐름을 보여주는 것을 목표로 한다.

핵심 화면 구조는 다음 3가지 사용자 관점으로 나눈다.

```text
Client Version
-> 고객이 선택형 Legal Intake Form을 통해 자신의 사건을 정리한다.

Attorney Version
-> 변호사가 상담 전 사건 요약, 보유 자료, 원하는 도움을 확인한다.

Admin / Marketing / Security Dashboard
-> 변호사 또는 운영자가 문의 흐름, 전환 손실, 보안 차단, 비용 차단을 확인한다.
```

## 2. Recommended Screen Count

제출용 화면 정의서는 총 12장으로 구성한다.

| Layer | Screen Count | Purpose |
| --- | ---: | --- |
| Common / Auth | 1 | 로그인 및 역할 진입 |
| Client | 4 | 고객 intake 입력, 챗봇형 안내, 변호사 탐색 |
| Attorney | 3 | 변호사 상담 전 사건 검토 |
| Admin / Dashboard | 4 | 마케팅, 보안, 비용, audit 관측 |
| Total | 12 | 제출용 화면 설계 초안 |

### MVP Priority

| Priority | Screens |
| --- | --- |
| Must | Login, Client Intake Form, Client Result, Attorney Intake Detail, Security/Cost Dashboard |
| Should | Client Search Routing, Attorney Intake List, Marketing Dashboard, Audit Log |
| Could | Client History, Attorney Memo, Advanced Search Result Explanation |

## 3. Overall Screen Flow

```mermaid
flowchart LR
    A[Login] --> B{Role}

    B --> C1[Client Intake Start]
    C1 --> C2[Client Legal Intake Form]
    C2 --> C3[Client AI Summary Result]
    C3 --> C4[Lawyer Search Routing]

    B --> L1[Attorney Intake Dashboard]
    L1 --> L2[Attorney Intake Detail]
    L2 --> L3[Attorney Consultation Memo]

    B --> D1[Admin Overview Dashboard]
    D1 --> D2[Marketing Funnel Dashboard]
    D1 --> D3[Security Cost Dashboard]
    D3 --> D4[Audit ReasonCode Log]
```

## 4. Role Definition

| Role | Main Goal | Main Screens |
| --- | --- | --- |
| Client | 법률 용어를 몰라도 문의를 구조화하고 안내를 받는다 | Intake Start, Intake Form, AI Summary, Search Routing |
| Attorney | 상담 전 사건 유형, 자료, 고객 의도를 빠르게 파악한다 | Intake Dashboard, Intake Detail, Memo |
| Admin / Owner Attorney | 문의 흐름, 전환율, 보안 차단, 비용 차단을 관측한다 | Marketing Dashboard, Security/Cost Dashboard, Audit Log |

## 5. Screen List Summary

| No | Screen Name | Role | Main Function |
| ---: | --- | --- | --- |
| 1 | Login | Common | JWT 로그인, 역할 기반 진입 |
| 2 | Client Intake Start | Client | 문의 목적과 사건 유형 선택 |
| 3 | Client Legal Intake Form | Client | 선택형 intake 입력 |
| 4 | Client AI Summary Result | Client | 챗봇형 사건 요약 및 준비자료 안내 |
| 5 | Lawyer Search Routing | Client | 변호사 탐색 조건 및 검색 결과 설명 |
| 6 | Attorney Intake Dashboard | Attorney | 접수된 문의 목록 확인 |
| 7 | Attorney Intake Detail | Attorney | 사건 상세, 자료, AI 요약 확인 |
| 8 | Attorney Consultation Memo | Attorney | 상담 전 메모, 상태 변경 |
| 9 | Admin Overview Dashboard | Admin | 전체 운영 현황 요약 |
| 10 | Marketing Funnel Dashboard | Admin | 문의 전환 흐름과 이탈 지점 관측 |
| 11 | Security Cost Dashboard | Admin | 보안 차단, 비용 차단, 토큰 사용 관측 |
| 12 | Audit ReasonCode Log | Admin | reasonCode, 차단 사유, 로그 추적 |

## 6. Detailed Screen Draft

## Screen 1. Login

### Purpose

로그인 사용자가 어떤 역할로 서비스에 들어가는지 결정한다.

### User

Client, Attorney, Admin

### Main UI Elements

- Email / ID input
- Password input
- Login button
- Demo role shortcut
  - Client demo
  - Attorney demo
  - Admin demo
- Login error message

### Main Action

```text
사용자 로그인
-> JWT 발급
-> role 확인
-> role별 main screen으로 이동
```

### API

```text
POST /api/auth/login
GET /api/me
```

### Security Point

```text
JWT 없음
-> 401

권한 부족
-> 403
```

## Screen 2. Client Intake Start

### Purpose

고객이 긴 주관식 설명을 시작하기 전에 문의 목적과 사건 유형을 선택한다.

### User

Client

### Main UI Elements

- Inquiry purpose select
  - 절차 안내
  - 필요한 자료 확인
  - 작성 문서 검토
  - 변호사 선임/계약 검토
  - 전자소송 제출 안내
- Case type select
  - 민사
  - 형사
  - 가사
  - 전자소송
  - 잘 모르겠음
- Next button

### Main Action

```text
고객이 문의 목적과 사건 유형 선택
-> Legal Intake Form으로 이동
```

### PM Value

```text
고객은 법률 용어를 몰라도 선택할 수 있다.
시스템은 선택값을 사건 구조로 바꿀 준비를 한다.
```

## Screen 3. Client Legal Intake Form

### Purpose

고객이 사건 정보를 선택형/짧은 서술형으로 입력한다.

### User

Client

### Main UI Elements

- Party position
  - 피해자
  - 피고소인
  - 원고
  - 피고
  - 신청인
  - 상대방
  - 잘 모르겠음
- Core facts
  - 사건 발생일
  - 상대방
  - 문제가 된 금액 또는 피해 규모
  - 현재 진행 단계
  - 핵심 내용 한 문장
- Available documents checkbox
  - 계약서
  - 입금 내역
  - 카카오톡/문자
  - 사진/영상
  - 소장/고소장/통지서
  - 없음
- Desired next step
  - 상담
  - 서면 검토
  - 소송 준비
  - 전자소송 제출
  - 변호사 선임
- Submit button

### Main Action

```text
Legal Intake 제출
-> DTO Validation
-> Fixed Guard
-> Cost Control
-> Legal Intake DB 저장
-> AI Summary 생성 또는 템플릿 응답 생성
```

### API

```text
POST /api/intakes
```

### Validation

```text
필수 입력 누락
-> 400 Bad Request

입력 길이 초과
-> REQUEST_TOO_LONG 또는 COST_LIMIT_BLOCKED
```

## Screen 4. Client AI Summary Result

### Purpose

고객이 입력한 선택값을 챗봇형 자연어 안내문으로 정리해 보여준다.

### User

Client

### Main UI Elements

- Current case type summary
- Dispute area summary
- Reason text
- Recommended documents
- Next organization direction
- Privacy masking notice
- Search lawyer button
- Save / copy summary button

### Example Output

```text
현재 문의는 민사 사건에 가까워 보입니다.

금전 반환 또는 대여금 분쟁 가능성이 있습니다.
상담 전에는 입금 내역, 카카오톡/문자 대화,
상대방에게 반환을 요청한 기록을 날짜순으로 정리해 주세요.

입력하신 개인정보는 답변에 그대로 표시하지 않고 마스킹 처리됩니다.
```

### Security Point

```text
Privacy Output Guard
-> 주민번호, 계좌번호, 전화번호 등 출력 마스킹
```

### API

```text
GET /api/intakes/{id}
POST /api/ai/intake-summary
```

## Screen 5. Lawyer Search Routing

### Purpose

고객 입력을 변호사 탐색 조건으로 바꾸고, 검색 결과를 설명한다.

### User

Client

### Main UI Elements

- Search condition preview
  - 법률 분야
  - 분쟁 유형
  - 지역
  - 전문분야
  - 공직/수사기관 경력 선호 여부
- Search source policy
  - 대한변협 공식 검색
  - 네이버 공개 검색
  - OpenAI web search summary
- Search result explanation
- Official source URL
- Important notice
  - 특정 변호사 추천이 아니라 공개 정보 탐색 기준 제공

### Main Action

```text
Intake 입력
-> Lawyer Search Condition 생성
-> AI Search Request 저장
-> 공개 검색 결과 요약
-> 출처 URL 표시
```

### API

```text
POST /api/intakes/{intakeId}/search-requests
GET /api/intakes/search-requests/{searchRequestId}
POST /api/intakes/search-requests/{searchRequestId}/results
```

### Risk Control

```text
실제 수임 알선처럼 보이지 않게 설계
-> 추천이 아니라 검색 조건과 공개 정보 비교 기준 제공
```

## Screen 6. Attorney Intake Dashboard

### Purpose

변호사가 접수된 문의를 목록으로 확인한다.

### User

Attorney

### Main UI Elements

- Intake list table
  - 접수 시간
  - 사건 유형
  - 분쟁 영역
  - 고객 위치
  - 원하는 다음 단계
  - 보유 자료 수
  - 상태
- Filter
  - caseType
  - desiredNextStep
  - status
  - createdAt
- Sort
  - 최신순
  - 상담 준비 완료순
  - 위험 차단 제외

### Main Action

```text
접수 목록 조회
-> 사건 상세 화면으로 이동
```

### API

```text
GET /api/intakes
```

## Screen 7. Attorney Intake Detail

### Purpose

변호사가 상담 전 사건 구조를 빠르게 파악한다.

### User

Attorney

### Main UI Elements

- Client intake summary
- Case type
- Party position
- Core facts
- Available documents
- Desired next step
- AI-generated summary
- Privacy-masked output
- Search condition preview
- Audit / security status badge

### Main Action

```text
Intake detail 확인
-> 상담 전 검토
-> 메모 작성 또는 상태 변경
```

### PM Value

```text
변호사는 기본 사실관계 질문 시간을 줄이고,
쟁점 파악과 전략 검토에 시간을 쓸 수 있다.
```

## Screen 8. Attorney Consultation Memo

### Purpose

변호사가 상담 전 메모와 처리 상태를 관리한다.

### User

Attorney

### Main UI Elements

- Attorney memo textarea
- Status select
  - NEW
  - REVIEWED
  - NEED_MORE_INFO
  - CONSULTATION_READY
  - CLOSED
- Follow-up checklist
  - 자료 보완 요청
  - 상담 예약 필요
  - 서면 검토 필요
  - 전자소송 안내 필요
- Save button

### API

```text
PATCH /api/intakes/{id}/status
POST /api/intakes/{id}/memo
```

### MVP Note

이 화면은 실제 구현이 늦어지면 `Attorney Intake Detail` 안의 section으로 합칠 수 있다.

## Screen 9. Admin Overview Dashboard

### Purpose

운영자가 전체 서비스 상태를 한눈에 본다.

### User

Admin / Owner Attorney

### Main UI Elements

- Total inquiries
- Today inquiries
- Consultation-ready count
- Blocked request count
- Cost-blocked count
- Average intake completion rate
- Recent risk alerts

### Main Action

```text
운영 현황 확인
-> marketing / security / audit detail로 이동
```

### Data Source

```text
legal_intake
ai_search_request
security metric
cost metric
audit log
```

## Screen 10. Marketing Funnel Dashboard

### Purpose

변호사 또는 사무소 운영자가 문의가 어디서 끊기는지 확인한다.

### User

Admin / Owner Attorney

### Main UI Elements

- Funnel
  - 방문
  - Intake 시작
  - Intake 제출
  - AI Summary 확인
  - Lawyer Search Routing 클릭
  - 상담 준비 완료
- Case type distribution
- Desired next step distribution
- Drop-off point chart
- Region / case type heatmap draft

### PM Value

```text
이 시스템은 단순 챗봇이 아니라,
초기 문의가 어디서 끊기는지 관찰하는 intake system이다.
```

### Metrics

```text
intake_started_total
intake_submitted_total
intake_summary_viewed_total
lawyer_search_requested_total
consultation_ready_total
```

## Screen 11. Security Cost Dashboard

### Purpose

AI 보안 차단과 비용 차단을 운영자가 확인한다.

### User

Admin / Owner Attorney

### Main UI Elements

- Fixed Guard blocked count
- Dynamic Guard blocked count
- Cost Control blocked count
- Privacy Output masked count
- Token estimated / actual usage
- Session budget usage
- Daily budget usage
- Strict mode status
- Top reasonCode chart

### ReasonCode Examples

```text
BLOCKED_JAILBREAK
BLOCKED_PROMPT_INJECTION
BLOCKED_LEGAL_HARM
BLOCKED_PRIVACY_RISK
BLOCKED_AUTH_BYPASS
COST_LIMIT_BLOCKED
REQUEST_TOO_LONG
DYNAMIC_SESSION_BLOCKED
```

### Metrics

```text
ai_fixed_guard_block_total
ai_dynamic_guard_block_total
ai_cost_block_total
ai_privacy_mask_total
ai_estimated_token_total
ai_actual_token_total
```

## Screen 12. Audit ReasonCode Log

### Purpose

어떤 요청이 왜 차단되었는지 추적한다.

### User

Admin / Owner Attorney

### Main UI Elements

- Log table
  - timestamp
  - sessionId
  - userId
  - requestType
  - allowed
  - reasonCode
  - policyProfile
  - estimatedTokens
  - actualTokens
- Filter
  - reasonCode
  - date
  - user/session
  - blocked only
- Detail drawer
  - sanitized input
  - policy decision
  - fallback response

### Main Action

```text
차단 사유 확인
-> metric과 실제 요청 mapping
-> Postman 실험 evidence로 활용
```

### Security Note

Audit 화면에서도 원본 개인정보를 그대로 노출하지 않는다.

## 7. Screen-to-Backend Mapping

| Screen | Main Backend Module |
| --- | --- |
| Login | Spring Security / JWT |
| Client Intake Start | React state / DTO |
| Client Legal Intake Form | LegalIntakeRecordController |
| Client AI Summary Result | MinimalLegalIntakeService / AiService |
| Lawyer Search Routing | LawyerSearchCondition / AiSearchRequest |
| Attorney Intake Dashboard | LegalIntakeRepository |
| Attorney Intake Detail | LegalIntakeRecordService |
| Attorney Consultation Memo | Future memo/status module |
| Admin Overview Dashboard | Metric / aggregate query |
| Marketing Funnel Dashboard | Intake event metric |
| Security Cost Dashboard | AiMetricRecorder / CostControl |
| Audit ReasonCode Log | Audit log / reasonCode event |

## 8. Screen-to-ERD Mapping

| Entity | Used Screens |
| --- | --- |
| legal_intake | Client Form, Attorney Dashboard, Attorney Detail |
| intake_document | Client Form, Attorney Detail |
| lawyer_search_condition | Lawyer Search Routing, Attorney Detail |
| ai_search_request | Lawyer Search Routing, Admin Dashboard |
| search_result_snapshot | Lawyer Search Routing, Audit/Review |
| future audit_log | Security Dashboard, Audit Log |

## 9. Implementation Priority

### Day 1-4 Draft Implementation

```text
Login placeholder
Client Intake Form
Client AI Summary Result
Attorney Intake Dashboard
Attorney Intake Detail
Security Cost Dashboard draft
```

### Day 5-7 Postman + Refactoring

```text
Normal intake request
Fixed Guard block
Cost Control block
Privacy masking
Dynamic Guard draft
LLM Search Routing preview
```

### Day 8-10 Stabilization

```text
Marketing Dashboard
Audit ReasonCode Log
Demo data
Screenshots
Presentation capture
```

## 10. Final Screen Scope Recommendation

이번 프로젝트의 화면은 많아 보이지만, 실제 구현에서는 다음 방식으로 줄일 수 있다.

```text
Client side
-> 4 screens

Attorney side
-> 2 screens + memo section

Admin side
-> 3 dashboards + audit table

Login
-> 1 screen
```

따라서 발표용 화면 정의서는 12장으로 제시하되, 실제 구현은 8~10개의 React view로 압축할 수 있다.

핵심은 화면 수가 아니라 다음 흐름이 보이는 것이다.

```text
고객은 선택한다.
챗봇은 정리한다.
변호사는 구조화된 사건 정보를 본다.
운영자는 문의 흐름, 보안 차단, 비용 차단을 dashboard로 본다.
```
