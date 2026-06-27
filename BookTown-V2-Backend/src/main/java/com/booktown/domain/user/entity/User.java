package com.booktown.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "users",
        indexes = @Index(name = "idx_users_nickname", columnList = "nickname")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "password", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private User(String email, String nickname, String passwordHash, UserRole role, String profileImageUrl) {
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.role = role;
        this.profileImageUrl = profileImageUrl;
    }

    public static User local(String email, String nickname, String passwordHash) {
        return new User(email, nickname, passwordHash, UserRole.USER, null);
    }

    public static User social(String email, String nickname, String profileImageUrl) {
        return new User(email, nickname, null, UserRole.USER, profileImageUrl);
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
