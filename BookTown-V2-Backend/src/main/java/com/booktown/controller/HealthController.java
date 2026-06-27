package com.booktown.controller;

import com.booktown.controller.api.HealthApi;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.client.port:8000}")
    private int chromaPort;

    private static final RestClient CHROMA_CLIENT;

    static {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        CHROMA_CLIENT = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Boolean> services = checkServices();
        boolean healthy = services.values().stream().allMatch(Boolean::booleanValue);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", healthy ? "UP" : "DEGRADED",
                "services", services
        )));
    }

    @Override
    public ResponseEntity<?> checkReadiness() {
        Map<String, Boolean> services = checkServices();
        boolean healthy = services.values().stream().allMatch(Boolean::booleanValue);

        if (healthy) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP")));
        }

        return ResponseEntity.status(503).body(
                ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE, null)
        );
    }

    private Map<String, Boolean> checkServices() {
        return Map.of(
                "mysql", checkMysql(),
                "redis", checkRedis(),
                "mongodb", checkMongo(),
                "chroma", checkChroma()
        );
    }

    private boolean checkMysql() {
        try {
            jdbcTemplate.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            log.warn("MySQL readiness check failed");
            return false;
        }
    }

    private boolean checkRedis() {
        RedisConnection connection = null;
        try {
            connection = redisTemplate.getConnectionFactory().getConnection();
            connection.ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis readiness check failed");
            return false;
        } finally {
            if (connection != null) {
                RedisConnectionUtils.releaseConnection(connection, redisTemplate.getConnectionFactory());
            }
        }
    }

    private boolean checkMongo() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.warn("MongoDB readiness check failed");
            return false;
        }
    }

    private boolean checkChroma() {
        try {
            CHROMA_CLIENT.get()
                    .uri(chromaHost + ":" + chromaPort + "/api/v2/heartbeat")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("ChromaDB readiness check failed");
            return false;
        }
    }
}
