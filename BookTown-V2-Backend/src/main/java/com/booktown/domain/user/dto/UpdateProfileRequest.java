package com.booktown.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 50) String nickname,
        @Size(max = 500) String profileImageUrl
) {
}
