# 14일 수행 일정 · 발표·제출 개정본

개정: 2026-10-26. 사용자 확정 단계표를 반영한 수행 일정입니다. 기존 12일 계획은 이력으로 보존합니다. 이 표는 단계·활동·산출물의 정리이며, 표에 적힌 모든 검증이 실제로 새로 실행됐다는 증거는 아닙니다. 완료 여부는 Windows PowerShell 기록·체크리스트·응답/DB·기존 실험 증거로 확인합니다.

| Day | Phase | 목표 | 핵심 작업 | 산출물 |
|---|---|---|---|---|
| 1 | 요구사항·프로젝트 방향 설계 | 프로젝트 목적과 개발 기준 수립 | 시장 문제·사용자·목적 정의, Ground Rule 작성, MVP 범위·모듈별 개발 방식 정리 | 프로젝트 목적·문제 정의, Ground Rule 초안, 개발 범위·방법론 초안 |
| 2 | 설계 확정·초기 산출물 | DB·화면·API 구조 설계 | Legal Intake 저장 구조, 화면 정의서·ERD·API 기능서, Screen-API-Controller 매핑 초안 작성 | Ground Rule, 화면 정의서, ERD 초안, API 기능서 초안, 화면/API 매핑표 |
| 2~4 | Backend Draft 구현 | 서버 기본 흐름과 DB 저장·조회 구현 | Controller·DTO·Service·Repository·Entity 구성, 문의·자료·검색 조건 저장, 목록·상세·검색 요청·결과 기록 API 구현 | 백엔드 기본 골격, 보안·비용 흐름도 초안, API 기능서, DB 저장·조회 흐름 |
| 5 | Security·Cost Control 연결 | API 경로에 보안·비용 통제 연결 | Fixed·Dynamic·Cost·입출력 Guard·Privacy 연결, reasonCode·Error Stop Flow, 인증·CORS, 잔여 백엔드 보완·DB 연결 확인 | 보안·비용 연결 버전, Screen-API-Controller 매핑표, Postman 실험서, ERD 갱신본, DB 연결 점검 기록 |
| 6 | Screen-API-Controller Mapping Check + Refactoring | API 기능 위치·흐름 매핑, Top-Middle 연결 | 정상·실패·차단 요청, 응답·ID·DB 행 확인, 같은 흐름을 재확인하며 Controller·Service·저장/응답 매핑, 오류 수정·재실험 | API Mapping Check, Top-Middle 매핑표, 오류·수정·재실험 기록, API·ERD 보완본 |
| 7 | DDD·Event Storming·Mapping + Controller-Service 매핑 | Top-Middle-Bottom 이해 강화 | Actor·Command·Domain Event·Policy·Aggregate·Read Model·경계 정리, Policy-Read Model-Code 연결, API 검사·대표 경로 불일치 수정 | Event Storming, 도메인 경계·매핑표, API별 검사 목록, Controller-Service 매핑 |
| 8 | React/API 연결 + 운영자 Dashboard | 프런트 화면 검수 | React 화면·운영자 Dashboard, API 함수·state·DTO 필드 매칭, 1차 기본 조회 연결 검수, 필요한 지표 누락 보완 | React 핵심 화면, Metric Dashboard, React/API 연결표 |
| 9~10 | LLM 연동·LLM Search Routing | 검색 자료와 설명을 제공하는 흐름 구현 | 실제 LLM 연동, 문의·조건 기반 검색문 구성, 외부 검색 호출, 결과 저장·조회·설명, 출력 보호·실패 처리 | LLM Search Routing 기능, 외부 검색 연동, 결과·설명 API, 호출 흐름도·실험 기록 |
| 11 | Windows PowerShell 통합 검증 + Refactoring | 모듈 전체 연결 검증 | PowerShell로 접수·조회·Security·Dynamic·Cost·Privacy·Search·React 연계 요청, ID·데이터·정상/실패/차단 확인, 최종 수정·재실험 | Windows PowerShell 실행 기록, 통합 검증 결과, Refactoring + Checklist, 오류 흐름 검증 |
| 12 | Refactoring + Checklist | 누적 산출물 정리·제출 준비 | README·보고서·설계·실험 통합, 화면 캡처·시연 영상·회고·발표자료, 제품기획·마케팅·규제 검토 자료 정돈 | README·결과보고서, PPT, 제품기획서, 마케팅·규제 검토 자료, 회고 |
| 13 | Windows PowerShell 통합 검증 + 시연 + Refactoring | 시연 촬영·PowerShell 통합 테스트 | 시연 흐름 촬영, PowerShell 통합 테스트·실행 결과 대조, 오류·수정·체크리스트 재확인 | 시연 영상, Windows PowerShell 통합 테스트 기록, Refactoring·체크리스트 갱신본 |
| 14 | 발표 품질 강화 | 제출·발표 전달력 최종 정리 | 캡처 보강, README 정리, 발표 문장 다듬기, 산출물 최종 정리 | 발표·제출 확정본, 최종 캡처·README·발표 문구·산출물 묶음 |

## 기록 기준

- Day6은 Postman 회차가 아니라 Screen-API-Controller Mapping Check·Refactoring으로 표기합니다.
- Day11과 Day13은 Windows PowerShell 통합 검증과 실행 기록을 명시합니다.
- DB 행 검증은 H2/MySQL 등 실제 적용 환경을 구분합니다. H2 결과만으로 MySQL 이행 성공을 주장하지 않습니다.
- 실제 LLM·검색, 모의 공급자, 브라우저 검증은 서로 다른 실행 증거로 관리합니다.
- 기존 12일 원본: [PROJECT_12_DAY_PLAN.md](PROJECT_12_DAY_PLAN.md).
