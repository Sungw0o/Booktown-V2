package com.booktown.domain.user.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.user.controller.api.UserApi;
import com.booktown.domain.user.dto.UpdateProfileRequest;
import com.booktown.domain.user.dto.UserResponse;
import com.booktown.domain.user.service.UserService;
import com.booktown.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public ApiResponse<UserResponse> getMe(UserPrincipal principal) {
        return ApiResponse.success(userService.getMe(principal.getId()));
    }

    @Override
    public ApiResponse<UserResponse> updateMe(
            UserPrincipal principal,
            UpdateProfileRequest request
    ) {
        return ApiResponse.success(userService.updateMe(principal.getId(), request));
    }
}
