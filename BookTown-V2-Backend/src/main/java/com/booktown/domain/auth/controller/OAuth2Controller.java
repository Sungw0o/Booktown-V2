package com.booktown.domain.auth.controller;

import com.booktown.domain.auth.controller.api.OAuth2Api;
import com.booktown.domain.auth.service.AuthService;
import com.booktown.domain.auth.service.OAuth2LoginService;
import com.booktown.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class OAuth2Controller implements OAuth2Api {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final OAuth2LoginService oAuth2LoginService;

    @Override
    public ResponseEntity<Void> login(String provider) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oAuth2LoginService.buildAuthorizationUrl(provider)))
                .build();
    }

    @Override
    public ResponseEntity<Void> callback(
            String provider,
            String code,
            String state,
            String error
    ) {
        if (error != null && !error.isBlank()) {
            return redirect(oAuth2LoginService.buildFailureRedirectUri(error));
        }

        try {
            AuthService.TokenPair tokenPair = oAuth2LoginService.loginWithAuthorizationCode(provider, code, state);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(oAuth2LoginService.buildSuccessRedirectUri(tokenPair)))
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(tokenPair).toString())
                    .build();
        } catch (CustomException e) {
            return redirect(oAuth2LoginService.buildFailureRedirectUri(e.getErrorCode().name()));
        }
    }

    private ResponseEntity<Void> redirect(String uri) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(uri))
                .build();
    }

    private ResponseCookie refreshCookie(AuthService.TokenPair tokenPair) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenPair.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMillis(tokenPair.refreshTokenExpirationMs()))
                .build();
    }
}
