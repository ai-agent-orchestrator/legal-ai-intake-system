# Legal AI Intake Security Server

> Draft branch: this repository is currently a design and migration draft, not a finished product.

This repository is a Spring Java based Legal Intake and Guide System.

It is not designed to replace legal consultation. Its goal is to improve the weak first-contact experience of a law firm help desk:

```text
visitor question
-> legal intake classification
-> procedure and document guidance
-> safe marketing-ready next step
-> security and cost controlled AI response
```

## Project Positioning

Many law firm intake desks lose potential clients before a lawyer ever sees the case.

This project treats the AI system as a controlled intake and guidance tool:

```text
legal domain classification
-> user intent classification
-> required document guidance
-> court procedure content routing
-> consultation-ready summary
```

The system is intentionally scoped to:

- legal procedure guidance
- document and checklist guidance
- case type classification
- consultation preparation
- safe marketing conversion support

It is not scoped to:

- replacing a lawyer
- giving final legal opinions
- generating harmful legal tactics
- helping users evade law, fabricate evidence, or pressure opponents

## Security Architecture

The project uses a three-layer security model.

```text
1. Fixed Guard
   - Jailbreak Guard
   - Prompt Injection Guard
   - limited Legal Harm Guard
   - topic and privacy checks

2. Metric-Linked Dynamic Guard
   - repeated block count
   - session risk score
   - Micrometer metrics
   - Prometheus evidence

3. Cost Control
   - estimated token check
   - request limit
   - session budget
   - LLM call prevention before cost explosion
```

## Implementation Direction

This project intentionally avoids Python for the current phase.

```text
Current phase:
Spring Java RiskEngine
+ NVIDIA NeMo Guardrails Colang policy flow

Future phase:
Python actions.py custom actions after Python training
```

Java owns the actual decision logic:

```text
risk scoring
block decision
metrics
audit history
token budget
session state
```

Colang owns the guardrail conversation policy:

```text
safe legal intake flow
jailbreak refusal flow
prompt injection refusal flow
legal harm refusal flow
output safety flow
```

## E-Court Based Intake Ontology

The current draft uses the already collected court-procedure content as a small intake ontology.

It is intentionally shallow and practical:

```text
domain
-> CIVIL / CRIMINAL / FAMILY / ELECTRONIC_LITIGATION

case type
-> civil-litigation
-> criminal-supplementary-investigation
-> family-procedure-aid
-> electronic-litigation

intake output
-> matched procedure
-> required documents
-> intake questions
-> recommended next steps
```

The intake API now connects a user request to this template data:

```text
POST /api/legal/intake/analyze
-> readiness decision
-> matchedCaseType
-> matchedProcedureCode
-> requiredDocuments
-> intakeQuestions
-> procedureNextSteps
```

This is not a deep legal knowledge graph. It is a lightweight ontology for law-firm help desk intake and procedure guidance.

Electronic litigation is slightly deeper than the other categories because it is useful as a common entry path:

```text
user registration
-> case type selection
-> document drafting
-> evidence attachment
-> electronic signature and submission
-> service / record review
-> supplementary filing
```

The project still keeps legal content intentionally limited. The main focus remains security architecture, not legal-content expansion.

## Repository Contents

```text
backend/guardrail-ready-policy-checker
-> Spring Boot Java backend copied from the merged legal/security work

frontend/legal-procedure-viewer
-> React legal procedure viewer and intake UI base

nemo-colang
-> NeMo Guardrails Colang/config only, without Python custom actions

docs/evidence/risk-patterns
-> Prometheus/Postman evidence copied from the observability lab

DESIGN_DRAFT.md
-> full architecture and portfolio positioning draft
```

## Core Message

```text
This project demonstrates that a legal AI intake system can combine
legal ontology based classification,
fixed security guards,
metric-linked dynamic defense,
and token cost control
without over-scoping into a full legal consultation chatbot.
```
