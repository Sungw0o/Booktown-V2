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
    private static final List<String> KEY = List.of("auth:refresh:" + USER_ID);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RefreshTokenService refreshTokenService = new RefreshTokenService(redisTemplate);

    @Test
    void rotate_accepts_success_result() {
        whenScriptReturns(1L);

        refreshTokenService.rotate(USER_ID, "old-token", "new-token", 1000L);
    }

    @Test
    void rotate_maps_missing_result_to_not_found() {
        whenScriptReturns(0L);

        assertThatThrownBy(() -> refreshTokenService.rotate(USER_ID, "old-token", "new-token", 1000L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    @Test
    void rotate_maps_reuse_result_to_reused_error() {
        whenScriptReturns(-1L);

        assertThatThrownBy(() -> refreshTokenService.rotate(USER_ID, "old-token", "new-token", 1000L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));
    }

    private void whenScriptReturns(Long result) {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(KEY),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(result);
    }
}
