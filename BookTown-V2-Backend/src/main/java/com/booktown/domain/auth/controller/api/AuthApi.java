package com.booktown.domain.auth.controller.api;

import com.booktown.domain.auth.dto.AuthTokenResponse;
import com.booktown.domain.auth.dto.LoginRequest;
import com.booktown.domain.auth.dto.ReissueRequest;
import com.booktown.domain.auth.dto.SignupRequest;
import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/auth")
@Tag(name = "Auth", description = "이메일 로그인, 토큰 재발급, 로그아웃 API")
public interface AuthApi {

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일과 비밀번호로 가입하고 Access Token과 Refresh Token 쿠키를 발급합니다.")
    ResponseEntity<ApiResponse<AuthTokenResponse>> signup(@Valid @RequestBody SignupRequest request);

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 Access Token과 Refresh Token 쿠키를 발급합니다.")
    ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request);

    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = "Refresh Token 쿠키 또는 요청 본문을 사용해 Access Token을 재발급합니다.")
    ResponseEntity<ApiResponse<AuthTokenResponse>> reissue(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) ReissueRequest request
    );

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token을 폐기하고 쿠키를 만료시킵니다.", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserPrincipal principal);
}
