package com.booktown.domain.auth.service;

public record OAuth2UserInfo(
        String providerId,
        String email,
        boolean emailVerified,
        String nickname,
        String profileImageUrl
) {
}
