package com.booktown.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractConfigTest {

    @Test
    void apiContractCustomizerAddsFrontendContractPaths() {
        OpenAPI openAPI = new OpenApiConfig().openAPI();

        new OpenApiContractConfig()
                .apiContractCustomizer()
                .customise(openAPI);

        assertThat(openAPI.getPaths())
                .containsKeys(
                        "/books",
                        "/books/search",
                        "/books/{bookId}",
                        "/books/{bookId}/bookmark",
                        "/users/me/bookmarks"
                );
        assertThat(openAPI.getComponents().getSchemas())
                .containsKeys(
                        "BookPageResponse",
                        "BookDetailResponse",
                        "ErrorResponse"
                );
    }
}