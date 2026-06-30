package com.booktown.domain.illustration.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.illustration.dto.IllustrationJobResponse;
import com.booktown.domain.illustration.dto.IllustrationResponse;
import com.booktown.domain.illustration.dto.SceneResponse;
import com.booktown.domain.illustration.entity.IllustrationJobStatus;
import com.booktown.domain.illustration.service.IllustrationService;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.exception.GlobalExceptionHandler;
import com.booktown.global.observability.BooktownMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IllustrationControllerTest {

    private MockMvc mockMvc;
    private IllustrationService illustrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        illustrationService = mock(IllustrationService.class);
        BooktownMetrics metrics = mock(BooktownMetrics.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IllustrationController(illustrationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(metrics))
                .build();
        authenticateAs(1L);
    }

    @Test
    void getScenes_returns_page() throws Exception {
        SceneResponse scene = new SceneResponse(1L, 1L, 10L, "첫 번째 장면", "비가 내렸다...");
        when(illustrationService.getScenes(eq(1L), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(scene), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/books/1/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sceneId").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("첫 번째 장면"));
    }

    @Test
    void getScenes_returns_404_when_book_not_found() throws Exception {
        when(illustrationService.getScenes(anyLong(), anyInt(), anyInt()))
                .thenThrow(new CustomException(ErrorCode.BOOK_NOT_FOUND));

        mockMvc.perform(get("/books/99/scenes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createIllustration_returns_202() throws Exception {
        when(illustrationService.createIllustration(eq(1L), eq(1L), any()))
                .thenReturn(jobResponse("QUEUED"));

        mockMvc.perform(post("/scenes/1/illustrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("style", "CLASSIC"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void createIllustration_returns_409_when_duplicate() throws Exception {
        when(illustrationService.createIllustration(any(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.DUPLICATE_ILLUSTRATION_JOB));

        mockMvc.perform(post("/scenes/1/illustrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("style", "CLASSIC"))))
                .andExpect(status().isConflict());
    }

    @Test
    void createIllustration_returns_429_when_rate_limit() throws Exception {
        when(illustrationService.createIllustration(any(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.ILLUSTRATION_RATE_LIMIT_EXCEEDED));

        mockMvc.perform(post("/scenes/1/illustrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("style", "CLASSIC"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void getIllustrationJob_returns_200() throws Exception {
        when(illustrationService.getIllustrationJob(anyLong(), anyLong())).thenReturn(jobResponse("COMPLETED"));

        mockMvc.perform(get("/illustration-jobs/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void getIllustrationJob_returns_404_when_not_found() throws Exception {
        when(illustrationService.getIllustrationJob(anyLong(), anyLong()))
                .thenThrow(new CustomException(ErrorCode.ILLUSTRATION_JOB_NOT_FOUND));

        mockMvc.perform(get("/illustration-jobs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getIllustrations_returns_list() throws Exception {
        IllustrationResponse doc = new IllustrationResponse("docId", 1L, "CLASSIC", "https://img.test/1.png", false, LocalDateTime.now());
        when(illustrationService.getIllustrations(anyLong(), anyLong())).thenReturn(List.of(doc));

        mockMvc.perform(get("/scenes/1/illustrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].illustrationId").value("docId"));
    }

    @Test
    void regenerateIllustration_returns_202() throws Exception {
        when(illustrationService.regenerateIllustration(anyLong(), any())).thenReturn(jobResponse("QUEUED"));

        mockMvc.perform(post("/illustrations/docId/regenerations"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    private void authenticateAs(long userId) {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(userId);
        when(principal.getAuthorities()).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private IllustrationJobResponse jobResponse(String status) {
        return new IllustrationJobResponse(10L, 1L, "CLASSIC", status, null, null, false,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
