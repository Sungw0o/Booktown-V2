package com.booktown.domain.auth.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefreshTokenServiceRedisIntegrationTest {

    private static final int REQUEST_COUNT = 32;
    private static final Long USER_ID = 7001L;
    private static final String KEY = "auth:refresh:" + USER_ID;
    private static final String OLD_TOKEN = "old-refresh-token";

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RefreshTokenService refreshTokenService;

    @BeforeAll
    void connectToRedis() {
        assumeTrue(Boolean.parseBoolean(System.getenv("BOOKTOWN_REDIS_INTEGRATION")),
                "Set BOOKTOWN_REDIS_INTEGRATION=true to run the real Redis integration test");

        String host = System.getenv().getOrDefault("BOOKTOWN_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("BOOKTOWN_REDIS_PORT", "16379"));

        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        refreshTokenService = new RefreshTokenService(redisTemplate);
    }

    @AfterAll
    void disconnectFromRedis() {
        if (redisTemplate != null) {
            redisTemplate.delete(List.of(KEY, KEY + ":previous", KEY + ":rotated-at"));
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void parallel_rotation_returns_one_winner_token_to_every_request() throws Exception {
        refreshTokenService.save(USER_ID, OLD_TOKEN, TimeUnit.MINUTES.toMillis(1));

        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);

        try {
            List<Future<RotateResult>> futures = new ArrayList<>();
            for (int index = 0; index < REQUEST_COUNT; index++) {
                futures.add(executor.submit(rotateTask(index, ready, start)));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RotateResult> results = new ArrayList<>();
            for (Future<RotateResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).allMatch(RotateResult::success);
            assertThat(results).extracting(RotateResult::refreshToken).doesNotContainNull();
            assertThat(results).extracting(RotateResult::refreshToken).containsOnly(results.getFirst().refreshToken());
            assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(results.getFirst().refreshToken());
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<RotateResult> rotateTask(int index, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                String effectiveToken = refreshTokenService.rotate(
                        USER_ID,
                        OLD_TOKEN,
                        "new-refresh-token-" + index,
                        TimeUnit.MINUTES.toMillis(1)
                );
                return new RotateResult(true, effectiveToken, null);
            } catch (CustomException exception) {
                return new RotateResult(false, null, exception.getErrorCode());
            }
        };
    }

    private record RotateResult(boolean success, String refreshToken, ErrorCode errorCode) {
    }
}
