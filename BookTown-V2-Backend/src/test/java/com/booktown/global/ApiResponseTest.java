package com.booktown.global;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_response_has_data_and_null_meta() {
        ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("status", "UP"));

        assertThat(response.data()).isEqualTo(Map.of("status", "UP"));
        assertThat(response.meta()).isNull();
    }

    @Test
    void error_response_has_correct_code_and_message() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.BOOK_NOT_FOUND, null);

        assertThat(response.error().code()).isEqualTo("BOOK_NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("도서를 찾을 수 없습니다.");
        assertThat(response.error().fieldErrors()).isEmpty();
    }

    @Test
    void custom_exception_contains_error_code() {
        CustomException e = new CustomException(ErrorCode.USER_NOT_FOUND);

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo(ErrorCode.USER_NOT_FOUND.getMessage());
    }
}
