package com.booktown.domain.auth.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Long USER_ID = 7L;
    private static final List<String> KEYS = List.of(
            "auth:refresh:" + USER_ID,
            "auth:refresh:" + USER_ID + ":previous",
            "auth:refresh:" + USER_ID + ":rotated-at"
    );

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RefreshTokenService refreshTokenService = new RefreshTokenService(redisTemplate);

    @Test
    void rotate_returns_new_token_for_first_rotation() {
        whenScriptReturns("new-token");

        assertThat(refreshTokenService.rotate(USER_ID, "old-token", "new-token", 1000L))
                .isEqualTo("new-token");
    }

    @Test
    void rotate_returns_current_token_for_duplicate_request_in_grace_window() {
        whenScriptReturns("winner-token");

        assertThat(refreshTokenService.rotate(USER_ID, "old-token", "loser-token", 1000L))
                .isEqualTo("winner-token");
    }

    @Test
    void rotate_maps_missing_result_to_not_found() {
        whenScriptReturns("__MISSING__");

        assertThatThrownBy(() -> refreshTokenService.rotate(USER_ID, "old-token", "new-token", 1000L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    @Test
    void rotate_maps_reuse_result_to_reused_error() {
        whenScriptReturns("__REUSED__");

        assertThatThrownBy(() -> refreshTokenService.rotate(USER_ID, "old-token", "new-token", 1000L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));
    }

    private void whenScriptReturns(String result) {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(KEYS),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(result);
    }
}
