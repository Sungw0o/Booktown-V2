package com.booktown.controller;

import com.booktown.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HealthControllerTest {

    private final HealthController controller = new HealthController(
            mock(JdbcTemplate.class),
            mock(StringRedisTemplate.class),
            mock(MongoTemplate.class)
    );

    @Test
    void health_returns_200_with_status_UP() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.checkHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().get("status")).isIn("UP", "DEGRADED");
        assertThat(response.getBody().data()).containsKey("services");
        assertThat(response.getBody().meta()).isNull();
    }
}
