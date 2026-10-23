package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.ErrorResponse;
import com.ohgiraffers.handlermethod.support.ApiConflictException;
import com.ohgiraffers.handlermethod.support.TraceContext;
import com.ohgiraffers.handlermethod.support.IncidentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleMissingIncident(IncidentNotFoundException exception, HttpServletRequest request) {
        return new ErrorResponse("INCIDENT_NOT_FOUND", "Incident not found", 404,
                request.getRequestURI(), TraceContext.currentTraceId(), Map.of(), Instant.now());
    }

    @ExceptionHandler(ApiConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ApiConflictException exception, HttpServletRequest request) {
        return new ErrorResponse(exception.getCode(), exception.getMessage(), 409,
                request.getRequestURI(), TraceContext.currentTraceId(), Map.of(), Instant.now(), exception.getDetails());
    }

    /* @Version 검사에 걸린 경우(잠금 없이 읽은 뒤 동시에 저장된 경우)도 같은 409 형식으로 돌려준다. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return new ErrorResponse("CONCURRENT_MODIFICATION", "The record was changed by another request. Reload and try again.", 409,
                request.getRequestURI(), TraceContext.currentTraceId(), Map.of(), Instant.now());
    }

    /*
     * 잠금 대기 시간 초과·쿼리 시간 초과 같은 일시적 DB 오류. 재전송 성공(201)이나 충돌(409)로 위장하지 않고 503으로 구분한다.
     * 문의 접수·메모는 같은 Idempotency-Key로 다시 보내면 안전하다. SQL·값은 응답에 넣지 않는다.
     */
    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleTransientDatabaseFailure(Exception exception, HttpServletRequest request) {
        return new ErrorResponse("DATABASE_BUSY", "The database is busy. Retry the same request with the same Idempotency-Key.", 503,
                request.getRequestURI(), TraceContext.currentTraceId(), Map.of(), Instant.now());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ErrorResponse handleUploadLimit(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return new ErrorResponse("FILE_TOO_LARGE", "Uploaded file exceeds multipart limit", 413,
                request.getRequestURI(), TraceContext.currentTraceId(), Map.of(), Instant.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        return new ErrorResponse(
                "INVALID_REQUEST",
                "Request validation failed",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                TraceContext.currentTraceId(),
                fieldErrors,
                Instant.now()
        );
    }
}
