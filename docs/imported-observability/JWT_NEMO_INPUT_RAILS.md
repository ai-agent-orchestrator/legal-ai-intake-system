# JWT + NeMo Input Rail Integration

## Request flow

```text
React
  -> POST /api/auth/login
  -> Bearer access token
  -> POST /api/legal/guardrail/input
  -> Spring JWT filter
  -> Spring RestClient
  -> NeMo Guardrails /v1/input-rails/check
  -> PASSED, MODIFIED, or BLOCKED
```

The new `/api/legal/**` routes require a JWT. Existing practice APIs remain available so the lab's earlier observability exercises do not break.

## Start NeMo Guardrails

```powershell
cd nemo-guardrails-service
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:OPENAI_API_KEY = "your-provider-key"
python -m uvicorn app:app --host 127.0.0.1 --port 8000
```

The provider key is read from the process environment. It is not stored in Git.

## Test with an HTTP client

Login:

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "user",
  "password": "user123"
}
```

Copy `accessToken` from the response and use it here:

```http
POST http://localhost:8080/api/legal/guardrail/input
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "message": "민사소송 준비서류를 알려줘"
}
```

Expected response for an allowed message:

```json
{
  "status": "PASSED",
  "allowed": true,
  "content": "민사소송 준비서류를 알려줘",
  "rail": null,
  "traceId": "..."
}
```

The Spring adapter records `ai.input.rail.decisions`, `ai.input.rail.errors`, and `ai.input.rail.duration` through Micrometer.

## AI chat endpoint

The authenticated chat endpoint runs the complete minimum flow:

```text
POST /api/ai/chat
  -> Input Rail
  -> OpenAI-compatible LLM call
  -> Output Rail
  -> success or fallback JSON
```

Set the provider key in the Spring process environment before starting the app:

```powershell
$env:LLM_API_KEY = "your-provider-key"
$env:LLM_MODEL = "gpt-4o-mini"
```

Request:

```http
POST http://localhost:8080/api/ai/chat
Authorization: Bearer <accessToken>
Content-Type: application/json

{"message":"내용증명 작성 절차를 알려줘"}
```

The LLM key is read only by Spring. React sends the JWT but never receives the provider key.

When the output rail blocks a response, Spring returns a stable fallback contract:

```json
{
  "answer": "해당 답변은 제공할 수 없습니다. 일반적인 법률 정보만 안내하며, 구체적인 사건은 전문가 상담이 필요합니다.",
  "blocked": true,
  "fallback": true,
  "traceId": "...",
  "model": "gpt-4o-mini",
  "reason": "OUTPUT_POLICY_BLOCKED"
}
```

## Important boundary

The authenticated Input Rail, Output Rail, and minimum `/api/ai/chat` integration are available. SSE is not implemented yet. When a rail blocks a request, or the LLM/Guardrails service is unavailable, the API returns a controlled fallback JSON response.
