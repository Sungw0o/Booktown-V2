package com.booktown.domain.auth.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class TurnstileVerifier {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient;
    private final boolean enabled;
    private final String secretKey;

    public TurnstileVerifier(
            RestClient.Builder restClientBuilder,
            @Value("${TURNSTILE_ENABLED:false}") boolean enabled,
            @Value("${TURNSTILE_SECRET_KEY:}") String secretKey
    ) {
        this.restClient = restClientBuilder.build();
        this.enabled = enabled;
        this.secretKey = secretKey;
    }

    public void verify(String token) {
        if (!enabled) {
            return;
        }
        if (secretKey == null || secretKey.isBlank() || token == null || token.isBlank()) {
            throw new CustomException(ErrorCode.TURNSTILE_VERIFICATION_FAILED);
        }

        try {
            TurnstileVerifyResponse response = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TurnstileVerifyRequest(secretKey, token))
                    .retrieve()
                    .body(TurnstileVerifyResponse.class);

            if (response == null || !response.success()) {
                log.warn("Turnstile verification failed: {}", response == null ? List.of("empty-response") : response.errorCodes());
                throw new CustomException(ErrorCode.TURNSTILE_VERIFICATION_FAILED);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Turnstile verification request failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.TURNSTILE_VERIFICATION_FAILED);
        }
    }

    private record TurnstileVerifyRequest(String secret, String response) {
    }

    private record TurnstileVerifyResponse(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
