package com.booktown.domain.auth.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String refreshToken, long expirationMs) {
        redisTemplate.opsForValue()
                .set(key(userId), refreshToken, Duration.ofMillis(expirationMs));
    }

    public void rotate(Long userId, String oldRefreshToken, String newRefreshToken, long expirationMs) {
        Long result = redisTemplate.execute(
                rotateScript(),
                List.of(key(userId)),
                oldRefreshToken,
                newRefreshToken,
                String.valueOf(expirationMs)
        );
        if (result == null || result == 0L) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        if (result < 0L) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private DefaultRedisScript<Long> rotateScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                local current = redis.call('GET', KEYS[1])
                if not current then
                  return 0
                end
                if current ~= ARGV[1] then
                  redis.call('DEL', KEYS[1])
                  return -1
                end
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
                """);
        return script;
    }
}
