# Virtual Thread Job Polling UI

This page explains the frontend and backend async flow used by:

```text
docs/frontend/react-virtual-thread-job-polling.html
```

The goal is to show asynchronous API integration without using SSE or WebSocket.

```text
POST /api/agent/jobs
-> receive jobId
-> poll GET /api/agent/jobs/{jobId}
-> display job status
-> show final risk decision
```

## 1. Overall Flow

```mermaid
flowchart TD
    A[User clicks Start Async Job] --> B[React builds JSON request]
    B --> C[POST /api/agent/jobs]
    C --> D[Spring saves job as PENDING]
    D --> E[Spring returns jobId immediately]
    E --> F[React stores jobId in state]
    F --> G[React starts polling every 1 second]
    G --> H[GET /api/agent/jobs/jobId]
    H --> I{Job status}
    I -->|PENDING| G
    I -->|RUNNING| G
    I -->|COMPLETED| J[Show final risk decision]
    I -->|FAILED| K[Show error message]
```

## 2. Backend Flow

```mermaid
flowchart TD
    A[POST /api/agent/jobs] --> B[AgentJobController]
    B --> C[AgentJobService start]
    C --> D[Save AgentJobHistory PENDING]
    D --> E[Return AgentJobStartResponse]
    D --> F[Submit task to virtual thread executor]
    F --> G[Set job RUNNING]
    G --> H[AgentRiskDecisionService analyze]
    H --> I[Record metrics]
    H --> J[Save risk history]
    H --> K[Set job COMPLETED]
```

The important point:

```text
The first HTTP request does not wait for the whole analysis.
It only creates the job and returns jobId.
The real work continues on a Java 21 virtual thread.
```

## 3. Frontend State Flow

```mermaid
flowchart TD
    A[idle] --> B[submitting]
    B --> C[polling]
    C --> D{Backend status}
    D -->|PENDING| C
    D -->|RUNNING| C
    D -->|COMPLETED| E[completed]
    D -->|FAILED| F[failed]
    B -->|POST error| G[error]
    C -->|GET error| G
```

Frontend state meaning:

```text
idle
-> the user has not started a job yet

submitting
-> React is sending POST /api/agent/jobs

polling
-> React has received jobId and is checking the job status every 1 second

completed
-> backend finished the virtual thread job and returned a risk decision

failed
-> backend job failed

error
-> frontend could not call the backend API
```

## 4. Why This Is Async

Synchronous API:

```text
Frontend sends request
-> backend finishes all work
-> backend returns final response
```

Example:

```text
POST /api/agent/risk/analyze
-> returns decision immediately after analysis
```

Asynchronous job API:

```text
Frontend sends request
-> backend accepts job
-> backend returns jobId first
-> backend keeps working in the background
-> frontend repeatedly checks status
```

Example:

```text
POST /api/agent/jobs
-> returns jobId

GET /api/agent/jobs/{jobId}
-> returns PENDING or RUNNING or COMPLETED
```

## 5. Why Polling Is Enough Here

SSE and WebSocket are useful when the server must push events to the browser in real time.

This project does not need that yet.

Polling is enough because:

```text
the job is short
the frontend only needs status updates
the backend already has a job status API
the concept is easier to explain in a portfolio
```

Portfolio meaning:

```text
This UI proves that the frontend can connect to a backend async job model.
It also connects JavaScript async fetch with Java 21 virtual thread backend execution.
```

## 6. Code Reading Map

Frontend:

```text
startJob
-> sends POST /api/agent/jobs
-> receives jobId
-> starts setInterval
```

```text
pollJob
-> sends GET /api/agent/jobs/{jobId}
-> updates React state
-> stops polling on COMPLETED or FAILED
```

Backend:

```text
AgentJobController
-> receives API request
```

```text
AgentJobService start
-> saves PENDING job
-> returns jobId
-> submits background work
```

```text
AgentJobService process
-> sets RUNNING
-> analyzes risk
-> sets COMPLETED or FAILED
```

