package com.booktown.global.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void masks_common_secret_values() {
        String masked = SensitiveDataMasker.mask(
                "password=123456 token=abc client_secret=secret api_key=key authorization=Bearer jwt"
        );

        assertThat(masked)
                .contains("password=****")
                .contains("token=****")
                .contains("client_secret=****")
                .contains("api_key=****")
                .contains("authorization=****")
                .doesNotContain("123456")
                .doesNotContain("Bearer jwt");
    }
}
