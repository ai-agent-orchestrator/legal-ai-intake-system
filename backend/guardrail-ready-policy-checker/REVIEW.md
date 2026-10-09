# Review Checklist

## Main Flow

- [ ] HTTP request enters the server.
- [ ] `TraceIdInterceptor.preHandle()` runs before the controller.
- [ ] The interceptor reads `X-Trace-Id` or creates a new traceId.
- [ ] The interceptor stores `startTime`.
- [ ] The controller handles the REST API request.
- [ ] The service can read the current traceId through `TraceContext`.
- [ ] Success response includes the traceId.
- [ ] ErrorResponse includes the traceId.
- [ ] `TraceIdInterceptor.afterCompletion()` logs status and elapsed time.
- [ ] `TraceContext.clear()` removes request data after completion.

## Observability Questions

- [ ] Which request failed?
- [ ] Which URL was called?
- [ ] Which HTTP method was used?
- [ ] What status code was returned?
- [ ] How many milliseconds did it take?
- [ ] Which traceId connects response and logs?
- [ ] Was the request handled on a Virtual Thread?

## One-Line Memory

```text
Observability means a single request can be traced through logs, response traceId, status, and elapsed time.
```
