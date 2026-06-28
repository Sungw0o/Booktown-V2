package com.booktown.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterBookRequest(
        @NotBlank String title,
        @NotBlank String author,
        String description,
        String coverImageUrl,
        @NotBlank String genre,
        @NotBlank String country
) {}
