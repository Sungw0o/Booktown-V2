package com.booktown.domain.user.controller.api;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.user.dto.UpdateProfileRequest;
import com.booktown.domain.user.dto.UserResponse;
import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/users")
@Tag(name = "Users", description = "내 사용자 정보와 프로필 API")
public interface UserApi {

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인 사용자의 프로필 정보를 조회합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal);

    @PatchMapping("/me")
    @Operation(summary = "내 프로필 수정", description = "닉네임과 프로필 이미지 URL을 수정합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request
    );
}
