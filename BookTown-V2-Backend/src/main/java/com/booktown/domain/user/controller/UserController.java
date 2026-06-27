package com.booktown.domain.user.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.book.dto.BookmarkItemResponse;
import com.booktown.domain.book.service.BookService;
import com.booktown.domain.user.controller.api.UserApi;
import com.booktown.domain.user.dto.UpdateProfileRequest;
import com.booktown.domain.user.dto.UserResponse;
import com.booktown.domain.user.service.UserService;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;
    private final BookService bookService;

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

    @Override
    public ApiResponse<Page<BookmarkItemResponse>> getMyBookmarks(
            UserPrincipal principal,
            int page,
            int size
    ) {
        Page<BookmarkItemResponse> result = bookService.getMyBookmarks(principal.getId(), page, size);
        return ApiResponse.success(result, PageMeta.of(result));
    }
}
