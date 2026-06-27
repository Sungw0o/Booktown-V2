package com.booktown.domain.auth.controller;

import com.booktown.domain.auth.dto.AuthTokenResponse;
import com.booktown.domain.auth.dto.LoginRequest;
import com.booktown.domain.auth.dto.SignupRequest;
import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signup_binds_request_body_and_sets_refresh_cookie() throws Exception {
        when(authService.signup(any(SignupRequest.class))).thenReturn(tokenPair());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(
                                "user@example.com",
                                "책고을",
                                "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInMs").value(3600000));

        verify(authService).signup(eq(new SignupRequest("user@example.com", "책고을", "password123")));
    }

    @Test
    void login_binds_request_body_and_sets_refresh_cookie() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokenPair());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(
                                "user@example.com",
                                "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        verify(authService).login(eq(new LoginRequest("user@example.com", "password123")));
    }

    @Test
    void reissue_prefers_refresh_cookie_over_request_body() throws Exception {
        when(authService.reissue("cookie-refresh-token")).thenReturn(tokenPair());

        mockMvc.perform(post("/auth/reissue")
                        .cookie(new Cookie("refreshToken", "cookie-refresh-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        verify(authService).reissue("cookie-refresh-token");
    }

    @Test
    void reissue_uses_request_body_when_cookie_is_missing() throws Exception {
        when(authService.reissue("body-refresh-token")).thenReturn(tokenPair());

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        verify(authService).reissue("body-refresh-token");
    }

    @Test
    void logout_uses_authenticated_principal_and_expires_refresh_cookie() throws Exception {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Max-Age=0"))))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout(7L);
    }

    private AuthService.TokenPair tokenPair() {
        return new AuthService.TokenPair(
                new AuthTokenResponse("access-token", 3600000),
                "refresh-token",
                1209600000
        );
    }
}
