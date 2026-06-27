package com.booktown.domain.auth.service;

import com.booktown.domain.auth.entity.AuthProvider;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OAuth2UserInfoExtractor {

    public OAuth2UserInfo extract(AuthProvider provider, Map<String, Object> attributes) {
        if (attributes == null) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }

        return switch (provider) {
            case KAKAO -> extractKakao(attributes);
            case GOOGLE -> extractGoogle(attributes);
            case NAVER -> extractNaver(attributes);
            case LOCAL -> throw new CustomException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        };
    }

    private OAuth2UserInfo extractKakao(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = mapValue(attributes.get("kakao_account"));
        Map<String, Object> profile = mapValue(kakaoAccount.get("profile"));
        String providerId = required(attributes.get("id"));
        String email = required(kakaoAccount.get("email"));
        boolean emailVerified = Boolean.TRUE.equals(booleanValue(kakaoAccount.get("is_email_verified")));
        String nickname = fallback(stringValue(profile.get("nickname")), emailPrefix(email), "kakao_" + providerId);
        String profileImageUrl = stringValue(profile.get("profile_image_url"));
        return new OAuth2UserInfo(providerId, email, emailVerified, nickname, profileImageUrl);
    }

    private OAuth2UserInfo extractGoogle(Map<String, Object> attributes) {
        String providerId = required(attributes.get("sub"));
        String email = required(attributes.get("email"));
        boolean emailVerified = Boolean.TRUE.equals(booleanValue(attributes.get("email_verified")));
        String nickname = fallback(stringValue(attributes.get("name")), emailPrefix(email), "google_" + providerId);
        String profileImageUrl = stringValue(attributes.get("picture"));
        return new OAuth2UserInfo(providerId, email, emailVerified, nickname, profileImageUrl);
    }

    private OAuth2UserInfo extractNaver(Map<String, Object> attributes) {
        Map<String, Object> response = mapValue(attributes.get("response"));
        String providerId = required(response.get("id"));
        String email = required(response.get("email"));
        String nickname = fallback(
                stringValue(response.get("nickname")),
                stringValue(response.get("name")),
                emailPrefix(email),
                "naver_" + providerId
        );
        String profileImageUrl = stringValue(response.get("profile_image"));
        return new OAuth2UserInfo(providerId, email, true, nickname, profileImageUrl);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
    }

    private String required(Object value) {
        String stringValue = stringValue(value);
        if (stringValue == null || stringValue.isBlank()) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        return stringValue;
    }

    private String fallback(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.length() <= 50 ? value : value.substring(0, 50);
            }
        }
        throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
    }

    private String emailPrefix(String email) {
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }
}
