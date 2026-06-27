package com.booktown.domain.auth.service;

import com.booktown.domain.auth.entity.AuthProvider;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2UserInfoExtractorTest {

    private final OAuth2UserInfoExtractor extractor = new OAuth2UserInfoExtractor();

    @Test
    void extract_kakao_user_info() {
        OAuth2UserInfo userInfo = extractor.extract(AuthProvider.KAKAO, Map.of(
                "id", 12345,
                "kakao_account", Map.of(
                        "email", "kakao@example.com",
                        "is_email_verified", true,
                        "profile", Map.of(
                                "nickname", "카카오유저",
                                "profile_image_url", "https://example.com/kakao.png"
                        )
                )
        ));

        assertThat(userInfo.providerId()).isEqualTo("12345");
        assertThat(userInfo.email()).isEqualTo("kakao@example.com");
        assertThat(userInfo.emailVerified()).isTrue();
        assertThat(userInfo.nickname()).isEqualTo("카카오유저");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/kakao.png");
    }

    @Test
    void extract_google_user_info() {
        OAuth2UserInfo userInfo = extractor.extract(AuthProvider.GOOGLE, Map.of(
                "sub", "google-sub",
                "email", "google@example.com",
                "email_verified", true,
                "name", "Google User",
                "picture", "https://example.com/google.png"
        ));

        assertThat(userInfo.providerId()).isEqualTo("google-sub");
        assertThat(userInfo.email()).isEqualTo("google@example.com");
        assertThat(userInfo.emailVerified()).isTrue();
        assertThat(userInfo.nickname()).isEqualTo("Google User");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/google.png");
    }

    @Test
    void extract_naver_user_info() {
        OAuth2UserInfo userInfo = extractor.extract(AuthProvider.NAVER, Map.of(
                "response", Map.of(
                        "id", "naver-id",
                        "email", "naver@example.com",
                        "nickname", "네이버유저",
                        "profile_image", "https://example.com/naver.png"
                )
        ));

        assertThat(userInfo.providerId()).isEqualTo("naver-id");
        assertThat(userInfo.email()).isEqualTo("naver@example.com");
        assertThat(userInfo.emailVerified()).isTrue();
        assertThat(userInfo.nickname()).isEqualTo("네이버유저");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/naver.png");
    }

    @Test
    void throws_when_required_email_is_missing() {
        assertThatThrownBy(() -> extractor.extract(AuthProvider.GOOGLE, Map.of(
                "sub", "google-sub"
        )))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OAUTH2_LOGIN_FAILED);
    }
}
