package com.booktown.global.response;

import com.booktown.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorResponse(ErrorDetail error, String traceId) {

    public record ErrorDetail(String code, String message, List<FieldError> fieldErrors) {}

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(
                new ErrorDetail(errorCode.name(), errorCode.getMessage(), List.of()),
                traceId
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors, String traceId) {
        return new ErrorResponse(
                new ErrorDetail(errorCode.name(), errorCode.getMessage(), fieldErrors),
                traceId
        );
    }
}
