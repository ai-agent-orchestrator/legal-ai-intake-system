# Multi-Agent Orchestration - Dark Flowcharts

2026-10-20. Proposed expansion, not an implemented integration. Existing API Flow and DDD PDFs are unchanged.

## Korean

Google이 비즈니스 전략과 도메인 방향을 정하고, Claude가 기술 아키텍처와 설계 원칙을 검토한다. Codex는 개발 팀장으로서 프런트엔드·백엔드·AI 연동·자동화·테스트·배포 코드 등 소프트웨어 개발 전반의 구현과 통합을 담당한다. Spring·Python은 이번 프로젝트의 기술 스택이며 Codex 역할의 범위를 제한하지 않는다.

Alibaba Cloud/Qwen은 실행 계층의 Worker로 배치해, 할당된 API 예산 안에서 대량 추론 작업을 처리한다. 정보 추출·의미 기반 태깅·요약·리포트 초안 생성 등을 맡고, Python 오케스트레이터가 동시 실행 수·비용·재시도·결과 검증을 관리한다.

NVIDIA NeMo Guardrails는 모델 입·출력 콘텐츠 검사를 담당한다. NemoClaw/OpenShell은 지원되는 에이전트 런타임의 도구·파일·네트워크·프로세스와 추론 경로를 제한하는 실행 환경으로 구분한다. NVIDIA 두 구성요소는 초록색으로 표시한다. Python은 회청색, Alibaba/Qwen은 고동색을 유지한다.

## English

Google defines the business strategy and domain direction, while Claude reviews the technical architecture and design principles. Codex serves as the engineering lead for end-to-end coding and integration, covering frontend, backend, AI integration, automation, testing, and deployment code. Spring and Python describe this project's implementation stack; the assigned engineering role spans software development as a whole.

Alibaba Cloud/Qwen forms the execution worker layer, handling high-volume inference within an allocated API budget. Its responsibilities include information extraction, semantic tagging, summarization, and report drafting. The Python orchestrator manages concurrency, spending limits, retries, and output validation.

NVIDIA NeMo Guardrails checks content on the model input and output paths. NemoClaw configures supported agents inside OpenShell sandboxes, with policy controls over tools, filesystem access, network egress, processes, and inference routing. NVIDIA components are green; Python is slate, and Alibaba/Qwen remains dark reddish brown. Ordinary API jobs can call Qwen directly; the dashed agent path uses a supported sandboxed agent. The remote Qwen provider remains outside that sandbox.

## Color system

The established project palette uses a dark navy canvas, distinct role colors, visible borders, and light text. Alibaba/Qwen is dark reddish brown (고동색), with a warm border and a slightly thicker outline. NVIDIA Guardrails and sandbox nodes use green fills and bright green borders.

| Role | Fill | Border |
|---|---|---|
| Google / Gemini | `#173b3e` | `#66cfc8` |
| Claude | `#342a4c` | `#b39ae5` |
| Codex / Spring | `#18344b` | `#5ba7db` |
| Python | `#26313d` | `#a1afbf` |
| NVIDIA NeMo Guardrails / NemoClaw | `#263c17` | `#91cc45` |
| Alibaba / Qwen | `#4b2b22` | `#d39a6a` |
| Queue / control decisions | `#42351e` | `#ddb965` |
| Validated results / accounting | `#1a2837` | `#78d5ef` |

## Architecture

![Dark architecture diagram](../../output/diagrams/05_MULTI_AGENT_ORCHESTRATION_ARCHITECTURE_DARK.png)

Source: [Mermaid](05_MULTI_AGENT_ORCHESTRATION_ARCHITECTURE.mmd) · [SVG](../../output/diagrams/05_MULTI_AGENT_ORCHESTRATION_ARCHITECTURE_DARK.svg)

The dashed edge means Codex designs and implements the runtime. It does not mean Google, Claude, and Codex are called in sequence on every production request. The human project owner retains final decision authority.

## Runtime flow

![Dark runtime flowchart](../../output/diagrams/05_MULTI_AGENT_ORCHESTRATION_RUNTIME_DARK.png)

Source: [Mermaid](05_MULTI_AGENT_ORCHESTRATION_RUNTIME.mmd) · [SVG](../../output/diagrams/05_MULTI_AGENT_ORCHESTRATION_RUNTIME_DARK.svg)

Deterministic parsing, chunking, formatting, sorting, and numerical aggregation use ordinary code. Models handle tasks requiring language interpretation. Every provider call attempt must be accounted for, including errors or rejected outputs. Missing usage remains estimated or pending reconciliation and must not be treated as free. Retry attempts return through the budget gate.

If a NeMo rail invokes a model, that call also consumes the shared budget and requires usage accounting. Guardrail rejection must not erase already incurred cost. The diagrams show the responsibility boundaries rather than every accounting call.

## Integration status and references

The Java project already contains HTTP integration points in `NemoInputGuardrailClient` and `NemoOutputGuardrailClient`. This confirms adapter code, not a verified deployment of NeMo Guardrails. Python orchestration, Qwen routing, and NemoClaw deployment in this diagram are planned extensions; the architecture update does not implement them.

- [NVIDIA NeMo Guardrails: rail categories and configuration](https://docs.nvidia.com/nemo/guardrails/latest/configure-guardrails/yaml-schema/guardrails-configuration)
- [NVIDIA NemoClaw: architecture and OpenShell protection layers](https://docs.nvidia.com/nemoclaw/latest/user-guide/openclaw/about/how-it-works)

PDF: [Two-page dark edition](../../output/pdf/MULTI_AGENT_ORCHESTRATION_DARK.pdf)
