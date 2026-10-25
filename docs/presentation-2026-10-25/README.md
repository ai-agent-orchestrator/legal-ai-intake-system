# 발표·촬영 필수 묶음

## 제품 소개 추가본

- [PM·보안·운영 핵심 원본](04_PM_SECURITY_CORE.md): PM Positioning, Problem → Solution, 6개 관점, 4개 보안 통제, 역할별 보드, Observability와 현재 구현 범위를 8쪽으로 정리. PDF는 `docs/presentation-pdfs/PM_SECURITY_CORE_DARK_TABLET.pdf`입니다.
- [제품 소개 편집 원본](00_PRODUCT_INTRODUCTION.md): Problem → Solution, 제품 정의, 입력 6개 항목, 온톨로지, 안내 예시, 역할별 흐름, 현재 범위·12월 계획을 8쪽으로 구성.
- PDF: `output/pdf/LEGAL_AI_INTAKE_PRODUCT_INTRO_DARK_TABLET.pdf` (1024×768, 가로 4:3, 다크 테마).
- [전체 흐름 Mermaid](PRODUCT_INTAKE_FLOW.mmd) / [온톨로지 Mermaid](MINI_LEGAL_ONTOLOGY.mmd).
- 재생성: 저장소 루트에서 `python scripts/Build-ProductIntroduction.py`. Markdown 본문과 Python 레이아웃을 분리해 수정할 수 있습니다.

## 기존 발표·촬영 묶음

2026-10-25. 예전 학습용 전체 API 문서를 모두 들고 발표하지 않고 아래 세 개만 사용합니다.

1. `01_PM_PRODUCT_SECURITY_COST.md` / PDF9쪽: PM 문제 정의·제품 여정·보안6·비용6·대표 화면13·모바일·검증 범위.
2. `02_DEMO_RECORDING_PLAYBOOK.md` / PDF6쪽:6분30초 대본·마케팅 자막·필수 실패 컷·OBS/Clipchamp 편집.
3. `03_VALIDATION_RUNBOOK.md` / PDF5쪽: 더미ID·필수 오류·검증 결과·실제 LLM 연결 전제·촬영 체크.

PDF는 `output/pdf/`의 같은 이름 `_DARK_TABLET.pdf`이며4:3 가로 태블릿용입니다. `DEMO_CAPTIONS_TIMING_DRAFT.srt`는 촬영 전 타이밍 초안이므로 실제 녹음 후 조정합니다.

근거: `GROUND_RULE.md`, `docs/DB_DEPLOYMENT_REVIEW_2026-10-23.md`, 현행 API/화면 매핑, Q01 최신 실행 및95개 집중 검사, 촬영용 모의 공급자 실제 HTTP 결과. 비밀키와 원문 DB 백업은 포함하지 않습니다.

권장 도구 공식 근거: https://obsproject.com/kb/quick-start-guide / https://clipchamp.com/en/blog/clipchamps-auto-captioning-feature/ / https://clipchamp.com/en/blog/screen-and-camera-record/ . ChatGPT는 대본/자막/편집 지시서 작성에 활용하고 실제 서비스 화면 녹화와 타임라인 편집은 전용 도구에서 합니다.
