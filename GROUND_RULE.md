# Project Ground Rule

## 1. Technical PM Positioning

This project is not designed only as a developer-oriented feature implementation task.

As a technical PM project, the goal is to design market needs, technical architecture, AI security, cost control, operations, validation, and project artifacts together.

This project is intended to demonstrate the following capabilities.

1. Discovering hidden market needs and opportunities

   The project identifies information cost, search cost, and consultation conversion loss in the legal intake process, and defines them as an AI service opportunity.

2. Implementing a Spring Boot-based AI service

   The project implements a Legal Intake Form-based AI chatbot and Search Routing flow using Spring Boot, JPA, React, and LLM API integration.

3. Designing and building AI security and cost control

   The project focuses on AI security and token cost control, which will become increasingly important in the AI service market. It aims to implement a 6-layer AI security structure and a 6-layer cost control structure.

4. Designing operational metrics and dashboards

   The project uses operational metrics and dashboards to improve operator visibility, service observability, and marketing decision-making.

5. Verifying and refactoring through Postman

   The project verifies normal flows, failure flows, and blocking flows through Postman experiments, then uses feedback to refactor the system.

6. Building AI-era project artifacts

   The project creates artifacts that explain the server structure, operational flow, and technical PM decision-making process.

In short, a technical PM is not only someone who proposes ideas.

A technical PM should be able to connect user behavior, domain events, policies, data, code, and operational metrics into one explainable structure.

## 2. Market-Driven Positioning

This project is not just a feature implementation assignment.

It aims to design an AI service with real market value and commercial positioning.

The project starts from a real market problem in legal intake: clients face high information cost, high search cost, and weak consultation conversion flow.

The goal is to reduce the client's legal search burden and improve the early consultation conversion process of small law offices by applying appropriate AI technology to a real-life business problem.

## 3. AI-Native Development Method

In the AI era, development speed is increasingly accelerated by AI tools.

Therefore, the human role should focus more on defining market value, designing security and cost reduction from the beginning, improving user experience through refactoring, handling failure cases, and building an operational feedback loop.

This project applies a practical variation of TDD learned in class, adapted to the AI development era.

```text
AI modular draft
-> Postman verification
-> failure case check
-> feedback record
-> refactoring
-> documentation
-> demo
```

This method is not a way to reduce development effort.

It is a way to put more weight on the areas where humans should focus in the AI era: market value, security, cost control, observability, validation, and refactoring.

## 4. Flexible Early, Strict Late

This project does not try to fully fix every structure before development begins.

An AI service connects Spring Boot, JPA, LLM APIs, security policies, cost control, metrics, and screen flows. Because these parts interact with each other, the project follows this operating principle.

```text
Early stage:
Implement while understanding.

Middle stage:
Experiment and structure through Postman and Event Storming.

Late stage:
Freeze the MVP scope and focus on stabilization, validation, refactoring, documentation, and demo preparation.
```

The project stays flexible while the structure is still being discovered, then becomes strict once the MVP scope is confirmed.

## 5. Development Weight Balance

This project gives similar weight to implementation, real operation, cost management, validation, and refactoring.

It is not enough for a feature to simply work.

The project checks the following questions.

```text
Can a normal request be processed?
Can a risky request be blocked?
Can an over-budget request be blocked before the LLM call?
Is personal information masked in the output?
Does the system fallback when an external API fails?
Can the decision be explained through reasonCode and metrics?
Where should the server be checked when an error happens?
Can the design be reduced or rerouted when needed?
```

In this project, a feature is considered complete only when the normal flow, failure flow, blocking reason, and operational visibility are verified.

## 6. Top / Middle / Bottom Understanding Rule

This project is not understood as a flat list of features.

It is understood through three layers: Top, Middle, and Bottom. This helps build practical server operation sense and a clear understanding of the full flow.

### Top Layer

The Top Layer explains the full server and AI flow through Mermaid flow charts.

```text
Client
-> Spring Security
-> Controller
-> DTO Validation
-> Service
-> JPA DB
-> Fixed Guard
-> Cost Control
-> Dynamic Guard
-> LLM Search Routing
-> Output Guardrail
-> Privacy Output Guard
-> Metric / Dashboard
-> Response
```

### Middle Layer

The Middle Layer uses DDD and Event Storming to identify how user behavior becomes commands and domain events.

```text
Actor
-> Command
-> Domain Event
-> Policy
-> Aggregate
-> Read Model
-> Bounded Context
```

### Bottom Layer

The Bottom Layer connects policies, required data, code locations, Postman verification, reasonCode, and metrics.

```text
Policy
-> required data
-> code location
-> Postman verification
-> reasonCode / metric check
```

This structure connects the full flow, domain events, policies, data, code, and validation into one explainable system.

## 7. Event-to-Code Mapping Rule

In this project, Event Storming results are not left only as documents.

Each event is connected to policy, read model, code, and Postman verification so that the actual server behavior can be checked.

This mapping table is a study artifact, an operational debugging artifact, and a core technical PM artifact.

The final goal is to explain the following chain.

```text
overall flow
-> domain event
-> policy
-> required data
-> code location
-> Postman verification
-> refactoring
```

The core goal is to build the ability to map user behavior into technical structure and verify how that structure works in the real server.

## 8. Module Completion Rule

A feature is not complete just because the code has been written.

Each module should be completed as one bundle.

```text
module design
-> module implementation
-> Postman normal request
-> Postman failure / blocked request
-> reasonCode check
-> log / metric check
-> feedback record
-> artifact update
```

Only a module that passes this process is considered implemented.

## 9. Error Stop Flow Rule

This project verifies not only successful behavior, but also whether the system stops the next step correctly when an error or block occurs.

Representative stop flows are as follows.

```text
No JWT
-> 401 before Controller

DTO Validation failure
-> 400 before Service

Fixed Guard block
-> no LLM call

Cost Control block
-> no LLM call
-> no actual token increase

Dynamic Guard block
-> subsequent requests blocked

OpenAI API failure
-> fallback response
-> no server-wide failure

Privacy Output Guard
-> no raw personal information exposure
```

Through this process, the project builds the operational ability to identify which server layer caused an error and apply an alternative design when needed.

## 10. Final Project Build-Up Rule

This project is not an isolated project.

It is a foundation for the later two-month final AI orchestration project.

Therefore, this project does not force advanced RAG, Python actions, or complex multi-agent architecture too early. Instead, it builds the following foundations.

```text
Spring Boot-based AI server flow
JPA DB design
LLM API integration
AI security policy
Cost Control
Privacy Output Guard
Prometheus / Grafana observability
Postman verification
DDD / Event Storming
Policy / Read Model / Code mapping
```

Based on this foundation, the final project can later expand into Python, RAG, Vector DB, LangChain / LangGraph, NeMo actions, and medical / legal AI security.

## 11. Final Goal

The goal of this project is not simply to complete a chatbot.

The goal is to directly understand and implement the flow of an AI security server that can structure legal inquiries, reduce search cost through LLM Search Routing, and control operational risk through Fixed Guard, Dynamic Guard, Cost Control, Privacy Output Guard, reasonCode, and Prometheus / Grafana metrics.

The final outcome should show that the project is not only an AI chatbot, but a security-aware, cost-aware, observable AI service architecture.
