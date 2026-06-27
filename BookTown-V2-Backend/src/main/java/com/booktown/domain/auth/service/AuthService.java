package com.booktown.domain.auth.service;

import com.booktown.domain.auth.dto.AuthTokenResponse;
import com.booktown.domain.auth.dto.LoginRequest;
import com.booktown.domain.auth.dto.SignupRequest;
import com.booktown.domain.auth.security.JwtTokenProvider;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.config.JwtProperties;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenPair signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = userRepository.save(User.local(
                request.email(),
                request.nickname(),
                passwordEncoder.encode(request.password())
        ));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PASSWORD));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenPair reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        jwtTokenProvider.validateToken(refreshToken);
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return rotateTokens(user, refreshToken);
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }

    TokenPair issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        refreshTokenService.save(user.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpirationMs());
        return new TokenPair(
                new AuthTokenResponse(accessToken, jwtProperties.getAccessTokenExpirationMs()),
                refreshToken,
                jwtProperties.getRefreshTokenExpirationMs()
        );
    }

    private TokenPair rotateTokens(User user, String oldRefreshToken) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        long refreshTokenExpirationMs = jwtTokenProvider.getRefreshTokenExpirationMs();
        refreshTokenService.rotate(user.getId(), oldRefreshToken, refreshToken, refreshTokenExpirationMs);
        return new TokenPair(
                new AuthTokenResponse(accessToken, jwtProperties.getAccessTokenExpirationMs()),
                refreshToken,
                refreshTokenExpirationMs
        );
    }

    public record TokenPair(AuthTokenResponse response, String refreshToken, long refreshTokenExpirationMs) {
    }
}
