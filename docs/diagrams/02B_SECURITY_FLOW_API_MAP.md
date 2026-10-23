# 기본 API·보안·운영 흐름 — 2026-10-22

**2026-10-22 현행 반영:** P03 실행·화면05 연결 구현, 운영자 React 화면09~13 구현. 화면13은 기존 C16/C17 재사용이며 새 API 번호·DB 테이블을 추가하지 않았다. 구현 API는 37개다. 외부 LLM·네이버 실제 키 호출·청구 검증은 아직 별도다.

P03은 명시적으로 실행한 검색 요청에서만 동작한다. 화면02~04는 기존 규칙·DB·템플릿 방식이며 초반 LLM 사건 정리를 추가하지 않는다. C16은 application JWT와 다른 ADMIN Basic 인증이다. 업무 운영 API의 기존 공개 정책은 남아 있다.

![업데이트된 기본 흐름](assets/02B_SECURITY_FLOW_API_MAP.png)

[확대용 SVG](assets/02B_SECURITY_FLOW_API_MAP.svg) · [Mermaid 원본](02B_SECURITY_FLOW_API_MAP.mmd) · [보안 상세 설명](02B_SECURITY_FLOW.md)

<details>
<summary>수정 가능한 Mermaid 보기</summary>

```mermaid
%%{init: {"theme":"base","themeVariables":{"fontSize":"20px","fontFamily":"Malgun Gothic, sans-serif"},"flowchart":{"useMaxWidth":false,"htmlLabels":true,"nodeSpacing":24,"rankSpacing":30,"padding":14}}}%%
flowchart TD
    Request["application API 요청"] --> Entry["CORS · 경로별 공개 / JWT 인증<br/>선언된 DTO 입력 제약 검증"]
    Entry --> Paths
    subgraph Paths["전체 API의 보안 적용 위치 · 구현 37개"]
        direction TB
        subgraph R1["인증 · 업무 · 소유자 확인"]
            direction LR
            Account["로그인 · 사용자<br/>로그인 C01 · 내 정보 P01<br/>계정 인증 후 JWT 발급<br/>P01은 JWT 사용자 조회"]
            Business["문의 · 검색 · 행동 업무<br/>등록 C03 · 상세 C05 · 목록 C09<br/>상태 C10 · 메모 C11<br/>검색 생성 C06 · 상세 C07 · 직접 결과 C08<br/>행동 P06 · 업무별 규칙 적용"]
            Ownership["소유자 · 파일 정책<br/>검색 이력 P02 · 검색 실행 P03<br/>업로드 P04 · 다운로드 P05<br/>JWT · 대상 · 소유자 또는 ADMIN<br/>파일 크기 · 유형 · 연결 확인"]
            Account ~~~ Business ~~~ Ownership
        end
        subgraph R2["규칙 안내 · Reference · 운영자 화면"]
            direction LR
            Rules["규칙 · DB 기반 안내 · 화면02~04<br/>분류 C02 · 간단 안내 C04<br/>고정 체크리스트 R06<br/>초반 LLM 사건 정리 호출 없음"]
            Reference["법률 Reference 조회<br/>목록 R01 · 상세 R02 · 단계 R03<br/>양식 목록 R04 · 상세 R05<br/>현재 공개 GET · JPA 조회"]
            Operations["운영자 React · 화면09~13<br/>09 요약 C12 · 10 마케팅 C13<br/>11 보안·비용 C14 · C15 · C16<br/>12 위험·Incident C17 · C18 · C19 · C20<br/>13 AI 위험 요약 C16 · C17<br/>업무 API 공개 · C16 별도 ADMIN Basic"]
            Rules ~~~ Reference ~~~ Operations
        end
        subgraph R3["AI 질문 · 단독 검사 · 위험 분석"]
            direction LR
            Chat["AI 질문 S01 · SSE S02<br/>JWT · Dynamic · Fixed · 입력 제한<br/>입력·생성·출력별 비용 예약·정산<br/>Output Guard · Privacy 마스킹<br/>SSE 중단·오류도 usage 기준 정산"]
            Standalone["단독 Guardrail 검사<br/>입력 S03 · 출력 S04<br/>JWT · 필수 입력 → 예약·검사·정산<br/>전체 Chat · 별도 Privacy와 구분"]
            Risk["Agent 위험 분석 S05<br/>요청 1건의 신호·점수·판정<br/>기존 위험 이력·조건별 Incident 저장<br/>대화 누적 차단 점수와 별도 기준"]
            Chat ~~~ Standalone ~~~ Risk
        end
        R1 ~~~ R2 ~~~ R3
    end
    Paths --> Result["API별 결과 · 사유 반환<br/>정상 응답 / 업무 오류<br/>정책 차단 / 기술 fallback"]
    Paths -->|"선택한 검색 요청의 P03 호출"| SearchExec["P03 구현 · 화면05 실제 외부 검색 실행<br/>JWT·소유자/ADMIN · 동일 ID 중복 실행 방지<br/>검색 조건 → LLM 검색문 → 네이버 2회 검색<br/>변협 도메인 대상 + 일반 공개 웹<br/>최대 3개 DB 저장 → 결과별 LLM 관련성 설명<br/>각 생성 전후 Guard · 단계별 비용 예약·정산"]
    Result ~~~ SearchExec
    Result --> Metric["경로별 Metric · Trace 관측<br/>지표는 MeterRegistry · 업무 이력은 기존 DB"]
    SearchExec --> Metric
    Metric -->|"별도 지표 조회"| Monitor["C16 GET /actuator/prometheus<br/>별도 Actuator · ADMIN Basic<br/>화면11 · 화면13이 같은 API 재사용"]
    Monitor --> Insights["화면13 · Dynamic Guard / AI 위험 요약<br/>C16 실제 지표 + C17 기존 위험 이력<br/>요청 1건 점수 / 대화 누적 점수를 나란히 표시<br/>지표 조합의 의미 · 증가량 · 검토할 조치<br/>화면13용 신규 API 번호·DB 테이블 없음"]
    classDef main fill:#eaf2ff,stroke:#3465a4,color:#102544
    classDef search fill:#edf8ed,stroke:#38803c,color:#173c20
    class Request,Entry,Account,Business,Ownership,Rules,Reference,Operations,Chat,Standalone,Risk,Result,Metric,Monitor,Insights main
    class SearchExec search
    style R1 fill:#ffffff,stroke:#c8d6e5
    style R2 fill:#ffffff,stroke:#c8d6e5
    style R3 fill:#ffffff,stroke:#c8d6e5
```

</details>

## 운영자 화면09~13 연결

| 화면 | React 경로 | API | 표시 내용 |
|---|---|---|---|
| 09 | `operator.html#/overview` | C12 | 운영 요약 |
| 10 | `operator.html#/marketing` | C13 | 문의·검색·행동 집계 |
| 11 | `operator.html#/security` | C14·C15·C16 | 보안 이력·위험 집계·비용/지표 |
| 12 | `operator.html#/audit` | C17·C18·C19·C20 | 위험 이력·Incident 조회/확인/종결 |
| 13 | `operator.html#/observability` | C16·C17 | 지표 조합의 의미·변화량·요청 점수/대화 누적 점수 |

화면13의 요청 점수는 기존 DB 이력, 세션 누적 점수는 서버 메모리 관측값이다. 서로 다른 기준이므로 합산하지 않는다. 네이버를 통한 변협 공개 페이지 검색은 변협 직접 API나 자격·경력 보증이 아니다.

## 함께 갱신한 문서

- README 한글·영문, 전체 서비스도, 백엔드·보안 지도, 통합 API 기능서의 중복 그림.
- 전체 기능표·화면별 API 연결표, 개별 P03 flow, 비용 적용 위치, 운영자 화면09~13 흐름과 매핑표.
- 전체 파일 목록은 아래 갱신 기록에서 확인한다. 과거 PDF/DOCX는 별도 판본으로 유지한다.

## 구현 근거·검증 범위

- [P03 구현·검수](../P03_EXTERNAL_SEARCH_REVIEW.md), [화면13 구현·DB 영향](../SCREEN13_OBSERVABILITY_REVIEW.md).
- [첫 호출 전 준비](../LLM_PAID_CALL_PRECHECK_2026-10-22.md), [현재 시연 비용·실험 목록](../DB_RULE_P03_COST_AND_EXPERIMENTS_2026-10-22.md).
- [이번 문서·도표 갱신 기록](../../output/basic-flow-update/verification.json). 기존 PDF/DOCX는 이번 Markdown·Mermaid 갱신에 포함하지 않았다.
