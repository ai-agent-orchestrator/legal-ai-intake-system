# Legal Procedure Viewer

React frontend for the legal procedure API in the Spring Boot application.

## Assignment implementation checklist

| Requirement | Implementation | Status |
| --- | --- | --- |
| Spring Boot REST API | Legal procedure and checklist read APIs under `/api/legal/**` | Done |
| React application | `legal-procedure-viewer` Vite + React app | Done |
| React to Spring integration | `src/api/legalProcedureApi.js` separates HTTP request functions from UI components | Done |
| Main screen | Legal procedure list with search and domain filter | Done |
| Detail screen | Procedure detail and step display | Done |
| Additional screen | Criminal supplementary-investigation checklist | Done |
| Shared visual style | Common layout, buttons, panels, status styles in `src/styles.css` | Done |
| Loading state | Loading message while an API request is pending | Done |
| Error state | API failure message and retry action | Done |
| Empty state | No-results message for an empty search or filter result | Done |
| Security study | JWT login, public legal GET policy, protected AI API policy | Done |

The project intentionally replaces the sample shopping-mall domain with a legal-procedure domain. The assignment's technical learning target is preserved:

```text
React screen
-> API helper
-> fetch request
-> Spring REST Controller
-> JSON response
-> React state update
-> screen rendering
```

The domain is legal procedure data because it matches the larger legal AI project. The reusable technical structure is the same as the sample CRUD application, while the screen content, search, checklist, and error states are customized for the intended service.

## Run

Start the Spring Boot server on `http://localhost:8080`, then run:

```powershell
npm install
npm run dev
```

Open the Vite URL shown in the terminal. The API base URL can be changed in the page when the backend uses another port.

## API mapping

| React workflow | Spring API |
| --- | --- |
| Procedure list and domain filter | `GET /api/legal/procedures` |
| Procedure detail | `GET /api/legal/procedures/{code}` |
| Criminal supplementary checklist | `GET /api/legal/criminal/checklist/supplementary-investigation` |

The API functions are separated under `src/api`, while list, detail, checklist, and status states use shared React components and the same design tokens in `src/styles.css`.

The UI includes loading, error, empty, search, filter, reload, detail, and checklist states so the Spring Boot to React integration can be tested without relying on a successful request only.

## JWT security study flow

This assignment uses two different access policies:

```text
Legal knowledge read API
GET /api/legal/**
-> public read access

AI API
/api/ai/**
-> JWT authentication required
```

The reason is practical. The React legal viewer only reads public legal procedure data, while an AI request can consume an external model, use a user's identity, and create cost or audit records. The AI boundary therefore remains protected even though the read-only legal pages are easy to open during the assignment.

### 1. Login flow

```text
Postman or React
-> POST /api/auth/login
-> AuthController
-> AuthenticationManager
-> username/password authentication
-> JwtTokenProvider creates accessToken
-> JSON response
```

Login request:

```json
{
  "username": "user",
  "password": "user123"
}
```

Login response:

```json
{
  "accessToken": "eyJ...",
  "username": "user",
  "role": "ROLE_USER"
}
```

The sample users are defined in `SecurityConfig`:

```text
admin / admin123 -> ROLE_ADMIN
user  / user123  -> ROLE_USER
```

The access token is not the user's password. It is a signed proof that the server can validate on later requests. The client must send it in the HTTP header:

```text
Authorization: Bearer {accessToken}
```

### 2. Request authentication flow

```text
React or Postman request
-> Authorization: Bearer JWT
-> JwtAuthenticationFilter
-> token signature and expiration validation
-> username and role extracted from claims
-> SecurityContext authentication set
-> SecurityConfig URL authorization
-> Controller
```

`JwtAuthenticationFilter` runs before the controller. It reads the `Authorization` header, parses the token, and places the authenticated user and role into Spring Security's `SecurityContext`. The controller does not need to manually parse the JWT.

### 3. Current URL policy

```java
.requestMatchers("/api/auth/login").permitAll()
.requestMatchers(HttpMethod.GET, "/api/legal/**").permitAll()
.requestMatchers("/api/legal/**").authenticated()
.requestMatchers("/api/ai/**").authenticated()
```

Read this from top to bottom:

| Rule | Meaning |
| --- | --- |
| `POST /api/auth/login` | Login must be possible before a token exists |
| `GET /api/legal/**` | Legal viewer read APIs are public for the assignment |
| Other `/api/legal/**` | Future write or protected legal actions require JWT |
| `/api/ai/**` | AI requests always require JWT |

The method matters. A GET legal query and a future POST legal modification do not have to share the same policy.

### 4. 401 and 403 difference

```text
No token or invalid token
-> 401 UNAUTHORIZED
-> authentication failed

Valid token but insufficient role
-> 403 FORBIDDEN
-> identity is known, permission is insufficient
```

Examples:

```text
POST /api/ai/chat without Authorization
-> 401 JSON error

ROLE_USER requests an ADMIN-only endpoint
-> 403 JSON error
```

This distinction is important when debugging React. A 401 usually means the token is missing, expired, or invalid. A 403 usually means the token worked but the user's role is not allowed.

### 5. Postman study sequence

1. Send `POST http://localhost:8080/api/auth/login` with `user/user123`.
2. Copy `accessToken` from the response.
3. Send `GET http://localhost:8080/api/legal/procedures` without a token and confirm the public read response.
4. Send a protected AI request without a token and confirm `401`.
5. Add `Authorization` with type `Bearer Token` and paste the token.
6. Send the AI request again and confirm that authentication passes.
7. Repeat with an expired or modified token and observe the JSON error response.

### 6. React connection point

The legal viewer's read API functions are separated in `src/api/legalProcedureApi.js`. If an AI screen is added later, the request helper should attach the token in one place:

```javascript
export async function requestAiChat(message, accessToken) {
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ message }),
  });

  if (!response.ok) {
    throw new Error(`AI request failed: ${response.status}`);
  }

  return response.json();
}
```

The complete AI request shape is:

```text
React input
-> requestBody
-> JSON.stringify
-> fetch POST + Bearer JWT
-> Spring JWT filter
-> Controller
-> Service / Guardrail / LLM
-> JSON response
-> React state update
```

### 7. Security boundary to remember

```text
Spring Security JWT
-> who may use the API?

Input and output Guardrail
-> what may enter or leave the AI system?

Cost Control
-> how much model usage is allowed?
```

JWT does not replace Guardrails, and Guardrails do not replace JWT. They control different risks and are placed at different stages of the backend flow.
