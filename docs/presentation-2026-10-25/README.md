# 발표·촬영 필수 묶음

## 시연·배포 회고와 실행 체크리스트

- [회고·체크리스트 편집 원본](14_DEMO_DEPLOYMENT_RETROSPECTIVE.md): 2026-10-26 오류 11건(코드 2건·실행 환경 9건), 사전 준비, 창 4개 기동 순서, 외부 PC 접속, 스모크 테스트, 종료, 운영 배포 보완을 정리했습니다.
- [다크 모드 PDF · 9쪽](../presentation-pdfs/DEMO_DEPLOYMENT_RETROSPECTIVE_DARK_TABLET.pdf): 기존 발표 자료와 같은 가로 4:3 구성입니다.
- 핵심 교훈: **"내 PC에서 된다"가 "다른 PC에서 된다"를 보장하지 않습니다.** 다른 PC에서 로그인과 문의 접수까지 확인합니다.
- 재생성: 저장소 루트에서 Python으로 scripts/Build-DemoDeploymentRetrospective.py를 실행합니다.

**현재 영상 기준:** [기존8분 + 화면10 마케팅 검증1분, 총9분 편집본](09M_MARKETING_SECURITY_EDIT.md). 07:45–08:45에 가상 고객 여정의 실제 API 집계·중복 방지 증거를 넣고 모바일로 마무리합니다. 보안4분과 timeout 반복 제거는 유지합니다. 무음 검토본이며 이전8분·6분30초본도 보관합니다.

**발표·녹음 가이드:** [시연 흐름도·30장면 타임스탬프·키워드·발표 멘트 매칭표](09M_DEMO_FLOW_TIMESTAMP_KEYWORD.md). 제품 PDF의 백엔드11~13쪽과 연결합니다.

## 제품 소개 추가본

- [PM·보안·운영 핵심 원본](04_PM_SECURITY_CORE.md): PM Positioning, Problem → Solution, 6개 관점, 4개 보안 통제, 역할별 보드, Observability와 현재 구현 범위를 8쪽으로 정리. PDF는 `docs/presentation-pdfs/PM_SECURITY_CORE_DARK_TABLET.pdf`입니다.
- [제품 소개 편집 원본](00_PRODUCT_INTRODUCTION.md): 기존 제품 설명10쪽에 백엔드 구체적 흐름3쪽을 추가했습니다. 8쪽 Current Implementation·9쪽 Boards And Dashboards·10쪽 Observability·11쪽 등록/검색·12쪽 상태/메모·13쪽 AI/SSE 순서에 14쪽 회고·다음 개발 방향을 더한14쪽 구성입니다.
- PDF: `output/pdf/LEGAL_AI_INTAKE_PRODUCT_INTRO_DARK_TABLET.pdf` (1024×768, 가로 4:3, 다크 테마).
- [전체 흐름 Mermaid](PRODUCT_INTAKE_FLOW.mmd) / [온톨로지 Mermaid](MINI_LEGAL_ONTOLOGY.mmd).
- [백엔드 구체적 흐름3개 · Mermaid 편집 원본](BACKEND_CONCRETE_FLOWS.md): HTTP 진입점·서비스·ID·권한·저장·응답·비용/지표 연결. README는 `scripts/Sync-BackendFlowReadme.py`로 동기화합니다.
- 재생성: 저장소 루트에서 `python scripts/Build-ProductIntroduction.py`. Markdown 본문과 Python 레이아웃을 분리해 수정할 수 있습니다.

## 기존 발표·촬영 묶음

2026-10-25. 예전 학습용 전체 API 문서를 모두 들고 발표하지 않고 아래 세 개만 사용합니다.

1. `01_PM_PRODUCT_SECURITY_COST.md` / PDF9쪽: PM 문제 정의·제품 여정·보안6·비용6·대표 화면13·모바일·검증 범위.
2. `02_DEMO_RECORDING_PLAYBOOK.md` / PDF6쪽:6분30초 대본·마케팅 자막·필수 실패 컷·OBS/Clipchamp 편집.
3. `03_VALIDATION_RUNBOOK.md` / PDF5쪽: 더미ID·필수 오류·검증 결과·실제 LLM 연결 전제·촬영 체크.

PDF는 `output/pdf/`의 같은 이름 `_DARK_TABLET.pdf`이며4:3 가로 태블릿용입니다. `DEMO_CAPTIONS_TIMING_DRAFT.srt`는 촬영 전 타이밍 초안이므로 실제 녹음 후 조정합니다.

근거: `GROUND_RULE.md`, `docs/DB_DEPLOYMENT_REVIEW_2026-10-23.md`, 현행 API/화면 매핑, Q01 최신 실행 및95개 집중 검사, 촬영용 모의 공급자 실제 HTTP 결과. 비밀키와 원문 DB 백업은 포함하지 않습니다.

권장 도구 공식 근거: https://obsproject.com/kb/quick-start-guide / https://clipchamp.com/en/blog/clipchamps-auto-captioning-feature/ / https://clipchamp.com/en/blog/screen-and-camera-record/ . ChatGPT는 대본/자막/편집 지시서 작성에 활용하고 실제 서비스 화면 녹화와 타임라인 편집은 전용 도구에서 합니다.

## 2026-10-26 제출 개정

PM·쉬운 운영 관리 → 오프닝 문구 → 제품 소개 순서로 재배치합니다. 중복 PM 뒷부분3쪽은 제거하고 전체 ERD 원본 한 페이지를 추가합니다. 일정은 [14일 수행표](../PROJECT_14_DAY_SCHEDULE_2026-10-26.md)로 대체하며, Day6 Mapping Check·Refactoring, Day11·13 Windows PowerShell 검증 기록을 명시합니다.
