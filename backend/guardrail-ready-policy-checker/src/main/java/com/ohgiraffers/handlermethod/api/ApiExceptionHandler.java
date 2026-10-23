package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.ErrorResponse;
import com.ohgiraffers.handlermethod.support.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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
