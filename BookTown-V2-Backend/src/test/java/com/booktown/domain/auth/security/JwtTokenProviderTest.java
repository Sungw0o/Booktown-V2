package com.booktown.domain.auth.security;

import com.booktown.global.config.JwtProperties;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-change-me-change-me-change-me";

    @Test
    void get_user_id_throws_invalid_token_when_subject_is_not_number() {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties());
        String token = Jwts.builder()
                .subject("not-number")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> provider.getUserId(token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties(mock(Environment.class));
        properties.setSecret(SECRET);
        properties.setAccessTokenExpirationMs(3600000);
        properties.setRefreshTokenExpirationMs(1209600000);
        return properties;
    }
}
