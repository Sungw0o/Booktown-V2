package com.booktown.domain.auth.security;

import com.booktown.global.config.JwtProperties;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, jwtProperties.getAccessTokenExpirationMs());
    }

    public String createRefreshToken(Long userId, String email) {
        return createToken(userId, email, jwtProperties.getRefreshTokenExpirationMs());
    }

    public Long getUserId(String token) {
        try {
            return Long.valueOf(parseClaims(token).getSubject());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long getRefreshTokenExpirationMs() {
        return jwtProperties.getRefreshTokenExpirationMs();
    }

    public void validateToken(String token) {
        parseClaims(token);
    }

    private String createToken(Long userId, String email, long expirationMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    private SecretKey signingKey() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
