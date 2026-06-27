package com.booktown.global.exception;

import com.booktown.global.response.ErrorResponse;
import com.booktown.global.logging.TraceIdFilter;
import com.booktown.global.observability.BooktownMetrics;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final BooktownMetrics metrics;

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e, HttpServletRequest request) {
        log.warn("CustomException: {} - {}", e.getErrorCode(), e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        recordAuthFailure(errorCode);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, extractTraceId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = Stream.concat(
                e.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage())),
                e.getBindingResult().getGlobalErrors().stream()
                        .map(ge -> new ErrorResponse.FieldError(ge.getObjectName(), ge.getDefaultMessage()))
        ).toList();
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors, extractTraceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, extractTraceId(request)));
    }

    private String extractTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        return (traceId != null) ? traceId : MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
    }

    private void recordAuthFailure(ErrorCode errorCode) {
        if (errorCode == ErrorCode.UNAUTHORIZED
                || errorCode == ErrorCode.FORBIDDEN
                || errorCode == ErrorCode.INVALID_TOKEN
                || errorCode == ErrorCode.EXPIRED_TOKEN
                || errorCode == ErrorCode.REFRESH_TOKEN_NOT_FOUND
                || errorCode == ErrorCode.REFRESH_TOKEN_REUSED
                || errorCode == ErrorCode.OAUTH2_LOGIN_FAILED) {
            metrics.recordAuthFailure(errorCode.name());
        }
    }
}
