package com.booktown.domain.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        long accessTokenExpiresInMs
) {
}
