package com.booktown.domain.auth.service;

import com.booktown.domain.auth.entity.AuthProvider;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OAuth2StateService {

    private static final String KEY_PREFIX = "auth:oauth2:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public void save(AuthProvider provider, String state) {
        redisTemplate.opsForValue().set(key(state), provider.name(), STATE_TTL);
    }

    public void validateAndConsume(AuthProvider provider, String state) {
        if (state == null || state.isBlank()) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }

        String savedProvider = redisTemplate.opsForValue().getAndDelete(key(state));
        if (!provider.name().equals(savedProvider)) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    private String key(String state) {
        return KEY_PREFIX + state;
    }
}
