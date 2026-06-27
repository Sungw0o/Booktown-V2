package com.booktown.domain.user.dto;

import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.entity.UserRole;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        UserRole role,
        String profileImageUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getProfileImageUrl()
        );
    }
}
