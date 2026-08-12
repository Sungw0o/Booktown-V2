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
    private static final String PREVIOUS_KEY_SUFFIX = ":previous";
    private static final String ROTATED_AT_KEY_SUFFIX = ":rotated-at";
    private static final long GRACE_WINDOW_MS = 15_000L;
    private static final String MISSING_RESULT = "__MISSING__";
    private static final String REUSED_RESULT = "__REUSED__";

    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String refreshToken, long expirationMs) {
        redisTemplate.delete(List.of(previousKey(userId), rotatedAtKey(userId)));
        redisTemplate.opsForValue()
                .set(key(userId), refreshToken, Duration.ofMillis(expirationMs));
    }

    public String rotate(Long userId, String oldRefreshToken, String newRefreshToken, long expirationMs) {
        String result = redisTemplate.execute(
                rotateScript(),
                List.of(key(userId), previousKey(userId), rotatedAtKey(userId)),
                oldRefreshToken,
                newRefreshToken,
                String.valueOf(expirationMs),
                String.valueOf(GRACE_WINDOW_MS),
                MISSING_RESULT,
                REUSED_RESULT
        );
        if (result == null || MISSING_RESULT.equals(result)) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        if (REUSED_RESULT.equals(result)) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        return result;
    }

    public void delete(Long userId) {
        redisTemplate.delete(List.of(key(userId), previousKey(userId), rotatedAtKey(userId)));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String previousKey(Long userId) {
        return key(userId) + PREVIOUS_KEY_SUFFIX;
    }

    private String rotatedAtKey(Long userId) {
        return key(userId) + ROTATED_AT_KEY_SUFFIX;
    }

    private DefaultRedisScript<String> rotateScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setResultType(String.class);
        script.setScriptText("""
                local current = redis.call('GET', KEYS[1])
                if not current then
                  return ARGV[5]
                end

                if current == ARGV[1] then
                  local time = redis.call('TIME')
                  local now = (time[1] * 1000) + math.floor(time[2] / 1000)
                  redis.call('SET', KEYS[2], current, 'PX', ARGV[4])
                  redis.call('SET', KEYS[3], now, 'PX', ARGV[4])
                  redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                  return ARGV[2]
                end

                local previous = redis.call('GET', KEYS[2])
                local rotatedAt = redis.call('GET', KEYS[3])
                if previous == ARGV[1] and rotatedAt then
                  local time = redis.call('TIME')
                  local now = (time[1] * 1000) + math.floor(time[2] / 1000)
                  if now - tonumber(rotatedAt) <= tonumber(ARGV[4]) then
                    return current
                  end
                end

                redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
                return ARGV[6]
                """);
        return script;
    }
}
