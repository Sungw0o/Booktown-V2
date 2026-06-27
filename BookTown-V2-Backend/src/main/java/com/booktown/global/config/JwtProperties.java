package com.booktown.global.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "booktown.jwt")
public class JwtProperties {

    private final Environment environment;

    private String secret;
    private long accessTokenExpirationMs;
    private long refreshTokenExpirationMs;

    public JwtProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                        "[보안] 운영 환경에서 JWT_SECRET이 설정되지 않았습니다. 기동을 중단합니다.");
            }
            if (accessTokenExpirationMs <= 0) {
                throw new IllegalStateException(
                        "[보안] 운영 환경에서 JWT_ACCESS_TOKEN_EXPIRATION_MS가 올바르지 않습니다.");
            }
            if (refreshTokenExpirationMs <= 0) {
                throw new IllegalStateException(
                        "[보안] 운영 환경에서 JWT_REFRESH_TOKEN_EXPIRATION_MS가 올바르지 않습니다.");
            }
        }
    }
}
