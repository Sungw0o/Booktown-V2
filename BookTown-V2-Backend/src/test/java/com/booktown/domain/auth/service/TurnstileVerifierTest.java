package com.booktown.domain.auth.service;

import com.booktown.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnstileVerifierTest {

    @Test
    void verify_skips_when_disabled() {
        TurnstileVerifier verifier = new TurnstileVerifier(RestClient.builder(), false, "");

        verifier.verify(null);
    }

    @Test
    void verify_rejects_blank_token_when_enabled() {
        TurnstileVerifier verifier = new TurnstileVerifier(RestClient.builder(), true, "secret");

        assertThatThrownBy(() -> verifier.verify(""))
                .isInstanceOf(CustomException.class);
    }
}
