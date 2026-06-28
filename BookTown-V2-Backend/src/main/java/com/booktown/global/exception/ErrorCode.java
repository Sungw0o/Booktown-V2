package com.booktown.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "하나 이상의 의존 서비스가 준비되지 않았습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh Token을 찾을 수 없습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "재사용된 Refresh Token입니다."),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH2_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),

    // 도서
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "도서를 찾을 수 없습니다."),
    BOOK_CONTENT_NOT_READY(HttpStatus.CONFLICT, "도서 원문이 아직 처리되지 않았습니다."),

    // 관리자 / 콘텐츠 업로드
    BOOK_CONTENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 원문이 업로드된 도서입니다."),
    CONTENT_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "콘텐츠 처리 Job을 찾을 수 없습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "TXT 파일만 업로드할 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기는 10MB를 초과할 수 없습니다."),
    FILE_ENCODING_INVALID(HttpStatus.BAD_REQUEST, "UTF-8로 인코딩된 파일만 업로드할 수 있습니다."),

    // 퀴즈
    QUIZ_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "퀴즈 생성 Job을 찾을 수 없습니다."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "퀴즈를 찾을 수 없습니다."),
    QUIZ_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출한 퀴즈입니다."),

    // AI
    AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "외부 AI 서비스 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}