package com.booktown.domain.auth.service;

import com.booktown.domain.auth.entity.SocialAccount;
import com.booktown.domain.auth.entity.AuthProvider;
import com.booktown.domain.auth.repository.SocialAccountRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2LoginService {

    private final OAuth2Properties properties;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final AuthService authService;
    private final OAuth2UserInfoExtractor userInfoExtractor;
    private final OAuth2StateService stateService;
    private final WebClient.Builder webClientBuilder;

    public String buildAuthorizationUrl(String providerValue) {
        AuthProvider provider = parseProvider(providerValue);
        OAuth2Properties.Provider providerProperties = providerProperties(provider);
        String state = UUID.randomUUID().toString();
        stateService.save(provider, state);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(providerProperties.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", providerProperties.getClientId())
                .queryParam("redirect_uri", providerProperties.getRedirectUri())
                .queryParam("state", state);

        if (!isBlank(providerProperties.getScope())) {
            builder.queryParam("scope", providerProperties.getScope());
        }

        return builder.build().encode().toUriString();
    }

    @Transactional
    public AuthService.TokenPair loginWithAuthorizationCode(String providerValue, String code, String state) {
        if (isBlank(code)) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }

        AuthProvider provider = parseProvider(providerValue);
        stateService.validateAndConsume(provider, state);
        OAuth2Properties.Provider providerProperties = providerProperties(provider);
        Map<String, Object> tokenResponse = requestToken(provider, providerProperties, code, state);
        if (tokenResponse == null) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        String accessToken = stringValue(tokenResponse.get("access_token"));
        if (isBlank(accessToken)) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }

        OAuth2UserInfo userInfo = userInfoExtractor.extract(provider, requestUserInfo(providerProperties, accessToken));
        User user = socialAccountRepository.findByProviderAndProviderId(provider, userInfo.providerId())
                .map(SocialAccount::getUser)
                .orElseGet(() -> connectOrCreateUser(provider, userInfo));

        return authService.issueTokens(user);
    }

    public String buildSuccessRedirectUri(AuthService.TokenPair tokenPair) {
        return UriComponentsBuilder.fromUriString(properties.getSuccessRedirectUri())
                .queryParam("status", "success")
                .build(true)
                .toUriString();
    }

    public String buildFailureRedirectUri(String reason) {
        return UriComponentsBuilder.fromUriString(properties.getFailureRedirectUri())
                .queryParam("reason", reason)
                .build(true)
                .toUriString();
    }

    private User connectOrCreateUser(AuthProvider provider, OAuth2UserInfo userInfo) {
        User user = userRepository.findByEmail(userInfo.email())
                .map(existingUser -> connectVerifiedEmailUser(existingUser, userInfo))
                .orElseGet(() -> userRepository.save(User.social(
                        userInfo.email(),
                        userInfo.nickname(),
                        userInfo.profileImageUrl()
                )));
        socialAccountRepository.save(SocialAccount.connect(user, provider, userInfo.providerId()));
        return user;
    }

    private User connectVerifiedEmailUser(User user, OAuth2UserInfo userInfo) {
        if (!userInfo.emailVerified()) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        return user;
    }

    private Map<String, Object> requestToken(
            AuthProvider provider,
            OAuth2Properties.Provider providerProperties,
            String code,
            String state
    ) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", providerProperties.getClientId());
        formData.add("redirect_uri", providerProperties.getRedirectUri());
        formData.add("code", code);
        if (!isBlank(providerProperties.getClientSecret())) {
            formData.add("client_secret", providerProperties.getClientSecret());
        }
        if (provider == AuthProvider.NAVER && !isBlank(state)) {
            formData.add("state", state);
        }

        try {
            return webClientBuilder.build()
                    .post()
                    .uri(providerProperties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    private Map<String, Object> requestUserInfo(OAuth2Properties.Provider providerProperties, String accessToken) {
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(providerProperties.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    private OAuth2Properties.Provider providerProperties(AuthProvider provider) {
        OAuth2Properties.Provider providerProperties = properties.getProviders()
                .get(provider.name().toLowerCase(Locale.ROOT));
        if (providerProperties == null
                || isBlank(providerProperties.getClientId())
                || isBlank(providerProperties.getRedirectUri())
                || isBlank(providerProperties.getAuthorizationUri())
                || isBlank(providerProperties.getTokenUri())
                || isBlank(providerProperties.getUserInfoUri())) {
            throw new CustomException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
        return providerProperties;
    }

    private AuthProvider parseProvider(String providerValue) {
        try {
            AuthProvider provider = AuthProvider.from(providerValue);
            if (provider == AuthProvider.LOCAL) {
                throw new CustomException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
            }
            return provider;
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
