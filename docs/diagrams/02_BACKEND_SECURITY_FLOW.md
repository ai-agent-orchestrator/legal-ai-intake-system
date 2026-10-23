# 02. 백엔드·보안 중심 flowchart

**2026-10-22 현행 반영:** P03 실행·화면05 연결 구현, 운영자 React 화면09~13 구현. 화면13은 기존 C16/C17 재사용이며 새 API 번호·DB 테이블을 추가하지 않았다. 구현 API는 37개다. 외부 LLM·네이버 실제 키 호출·청구 검증은 아직 별도다.

기준: **2026-10-17(Asia/Seoul)**. **단순 흐름 → API 번호 지도 → 필요한 세부 처리** 순서로 읽는다.

읽는 질문: **어느 요청에 어떤 검사를 적용하고, 허용·차단·실패 결과를 어떻게 구분하는가?**

## 1. 단순 버전 — 큰 흐름

```mermaid
%%{init: {"themeVariables":{"fontSize":"20px"},"flowchart":{"useMaxWidth":false,"htmlLabels":true,"nodeSpacing":22,"rankSpacing":28,"padding":12}}}%%
flowchart TD
    Request["API 요청"] --> Gate{"경로별 인증·입력 검사"}
    Gate -->|"실패"| Stop["거절·차단·실패 안내"]
    Gate -->|"통과"| Kind{"요청한 기능"}
    Kind --> Business["일반 업무<br/>권한·대상·상태 규칙 확인"]
    Business --> Response["해당 경로의 응답"]
    Kind --> Input["AI 질문<br/>입력 보안·비용 검사"]
    Input -->|"허용"| LLM["LLM 호출"]
    Input -->|"차단"| Stop
    LLM --> Output["출력 검사·개인정보 보호"]
    Output -->|"허용"| Response
    Output -->|"차단"| Stop
    Stop --> Response
    classDef main fill:#eaf2ff,stroke:#3465a4
    classDef blocked fill:#fff0ee,stroke:#bc4b40
    class Request,Business,Input,LLM,Output,Response main
    class Stop blocked
```

보안의 큰 갈래만 표시한다. AI 질문과 P03의 검색문·설명 생성에는 각각 LLM 전후 검사가 이어진다. 단독 Guardrail 검사는 전체 Chat 파이프라인과 다르며 다음 번호 지도에서 분리한다. 비용 정책은 입력 검사·답변 생성·출력 검사 각각에 적용하며, 단계별 예약·정산은 [Cost Control 도표](03_COST_CONTROL_FLOW.md)에서 확인한다.

## 2. API 번호를 붙인 버전 — 기능 위치

아래는 단순 흐름의 **기능별 검사**를 API 위치로 펼친 지도다. 구현 API 37개와 운영자 화면09~13을 표시한다. C16은 별도 Actuator Basic 인증 경로다.

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

[단순 버전 Mermaid](02_BACKEND_SECURITY_FLOW_SIMPLE.mmd) · [API 번호 지도 Mermaid](02_BACKEND_SECURITY_FLOW.mmd) · [기존 세부 처리도](02_BACKEND_SECURITY_FLOW_EXPANDED.md)

**그림에서 빠진 API 번호:** 없음. C/R/S/P 37개를 표시하며, 번호 묶음은 자동 호출 순서가 아니다. P03은 명시적인 검색 실행 요청이다. [기본 흐름 PNG·SVG](02B_SECURITY_FLOW_API_MAP.md).

**API별 검사 범위를 구분한다**

| 경로·API | 현재 처리 | 읽을 때 주의할 점 |
|---|---|---|
| 로그인 C01 / 내 정보 P01 | C01 공개 진입 후 계정 인증·JWT 발급, P01 JWT 사용자 조회 | 모든 API가 먼저 로그인 API를 자동 호출하는 것은 아님 |
| C03/C05/C09/C10/C11 및 C06/C07/C08 | DTO·대상 확인·상태/메모/저장 규칙, 지정 응답 필드 마스킹 | 전체 Chat Guard를 공통 적용하지 않음. 현재 개발 설정 공개 |
| C02/C04 | 규칙 분류·안내, 무저장 | LLM 미호출. C04에는 C02와 같은 필수 DTO 제약이 없음 |
| P02/P04/P05 | JWT·대상 존재·소유자/관리자, 파일별 크기·유형·연결 확인 | JWT 실패 401과 대상 없음 404, 소유자 불일치 403을 구분 |
| S01 | Dynamic → Fixed → 길이 → 입력 검사 예약·정산 → 생성 예약·정산 → 출력 검사 예약·정산 → Privacy | 각 유료 호출 전에 예산 확인. 차단도 HTTP 200이므로 blocked·fallback·reasonCode·detailCode 확인 |
| S02 | 로컬 입력 보안 → 입력 검사 예약·정산 → 생성 예약·전체 버퍼·정산 → 출력 검사 예약·정산 → Privacy → token/done | 출력 차단에도 생성 비용 유지. 오류·timeout·연결 종료는 미확인 생성 비용 추정 정산·중복 정산 방지 |
| S03/S04 | JWT·필수 입력 → 해당 검사 비용 예약 → Input/Output Client → 보고/추정 정산 | 같은 전체·일일·세션 예산 공유. 전체 Chat의 Fixed·Dynamic·답변 생성·별도 Privacy는 실행하지 않음. S04 로컬 출력 정책은 예약 전에 적용 |
| S05 및 C12/C14/C15/C17/C18/C19/C20 | 별도 Agent 위험·실행·Incident 모델의 분석·조회·상태 변경 | Chat Dynamic 메모리 세션 점수와 다름. S05의 권장 조치와 실제 도구 집행을 구분 |
| C16 | 별도 Actuator ADMIN Basic 인증 | 사용자 JWT·DB root와 다른 인증 |

CORS preflight 성공과 실제 요청 인증을 구분하며 Postman 성공만으로 브라우저 연결 성공을 판정하지 않는다. 기존 Reference·Incident의 대상 없음과 단독 Guard 외부 실패는 모든 오류가 신규 Intake와 같은 404/200 fallback으로 통일되어 있지 않다.

근거: [API 기능서 flow·기능mapping표](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_STATUS_AND_API_SPEC.md), [SecurityConfig](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java), [02-B 보안 확대도](C:/myLectureWs/legal-ai-intake-security-server/docs/diagrams/02B_SECURITY_FLOW.md).
