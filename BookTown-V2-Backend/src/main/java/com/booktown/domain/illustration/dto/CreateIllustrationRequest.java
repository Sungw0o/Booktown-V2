package com.booktown.domain.illustration.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIllustrationRequest(
        @NotBlank String style,
        String promptHint
) {}
