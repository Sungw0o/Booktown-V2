package com.booktown.domain.auth.controller;

import com.booktown.domain.auth.controller.api.AuthApi;
import com.booktown.domain.auth.dto.AuthTokenResponse;
import com.booktown.domain.auth.dto.LoginRequest;
import com.booktown.domain.auth.dto.ReissueRequest;
import com.booktown.domain.auth.dto.SignupRequest;
import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.auth.service.AuthService;
import com.booktown.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;

    @Override
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(SignupRequest request) {
        AuthService.TokenPair tokenPair = authService.signup(request);
        return tokenResponse(tokenPair);
    }

    @Override
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(LoginRequest request) {
        AuthService.TokenPair tokenPair = authService.login(request);
        return tokenResponse(tokenPair);
    }

    @Override
    public ResponseEntity<ApiResponse<AuthTokenResponse>> reissue(
            String cookieRefreshToken,
            ReissueRequest request
    ) {
        String refreshToken = cookieRefreshToken != null ? cookieRefreshToken : (request == null ? null : request.refreshToken());
        AuthService.TokenPair tokenPair = authService.reissue(refreshToken);
        return tokenResponse(tokenPair);
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> logout(UserPrincipal principal) {
        authService.logout(principal.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(ApiResponse.success(null));
    }

    private ResponseEntity<ApiResponse<AuthTokenResponse>> tokenResponse(AuthService.TokenPair tokenPair) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokenPair).toString())
                .body(ApiResponse.success(tokenPair.response()));
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

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
