package com.booktown.domain.auth.entity;

import java.util.Locale;

public enum AuthProvider {
    LOCAL,
    KAKAO,
    GOOGLE,
    NAVER;

    public static AuthProvider from(String value) {
        return AuthProvider.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
