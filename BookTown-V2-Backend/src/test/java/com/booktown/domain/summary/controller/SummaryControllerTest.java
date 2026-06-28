package com.booktown.domain.summary.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.summary.dto.SummaryJobResponse;
import com.booktown.domain.summary.dto.SummaryResponse;
import com.booktown.domain.summary.entity.SummaryJobStatus;
import com.booktown.domain.summary.service.SummaryService;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.exception.GlobalExceptionHandler;
import com.booktown.global.observability.BooktownMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SummaryControllerTest {

    private MockMvc mockMvc;
    private SummaryService summaryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        summaryService = mock(SummaryService.class);
        BooktownMetrics metrics = mock(BooktownMetrics.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SummaryController(summaryService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(metrics))
                .build();
        authenticateAs(1L);
    }

    @Test
    void createSummary_returns_202_with_job() throws Exception {
        SummaryJobResponse jobResponse = summaryJobResponse(SummaryJobStatus.QUEUED.name());
        when(summaryService.createSummary(eq(1L), eq(1L), any())).thenReturn(jobResponse);

        mockMvc.perform(post("/books/1/summaries"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.jobId").value(10))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void createSummary_returns_409_when_duplicate_job() throws Exception {
        when(summaryService.createSummary(any(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.DUPLICATE_SUMMARY_JOB));

        mockMvc.perform(post("/books/1/summaries"))
                .andExpect(status().isConflict());
    }

    @Test
    void createSummary_returns_429_when_rate_limit_exceeded() throws Exception {
        when(summaryService.createSummary(any(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.SUMMARY_RATE_LIMIT_EXCEEDED));

        mockMvc.perform(post("/books/1/summaries"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void getSummaryJob_returns_200_with_status() throws Exception {
        when(summaryService.getSummaryJob(1L, 10L)).thenReturn(summaryJobResponse("COMPLETED"));

        mockMvc.perform(get("/summary-jobs/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void getSummaryJob_returns_404_when_not_found() throws Exception {
        when(summaryService.getSummaryJob(1L, 99L))
                .thenThrow(new CustomException(ErrorCode.SUMMARY_JOB_NOT_FOUND));

        mockMvc.perform(get("/summary-jobs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSummaries_returns_list() throws Exception {
        SummaryResponse doc = new SummaryResponse("docId", 1L, "요약 내용", false, null, LocalDateTime.now());
        when(summaryService.getSummaries(1L, 1L)).thenReturn(List.of(doc));

        mockMvc.perform(get("/books/1/summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].summaryId").value("docId"));
    }

    @Test
    void regenerateSummary_returns_202() throws Exception {
        when(summaryService.regenerateSummary(1L, "docId")).thenReturn(summaryJobResponse("QUEUED"));

        mockMvc.perform(post("/summaries/docId/regenerations"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void saveFeedback_returns_204() throws Exception {
        mockMvc.perform(put("/summaries/docId/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5, "comment", "좋아요"))))
                .andExpect(status().isNoContent());

        verify(summaryService).saveFeedback(eq(1L), eq("docId"), any());
    }

    @Test
    void saveFeedback_returns_400_when_rating_out_of_range() throws Exception {
        mockMvc.perform(put("/summaries/docId/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 6))))
                .andExpect(status().isBadRequest());
    }

    private void authenticateAs(long userId) {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(userId);
        when(principal.getAuthorities()).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private SummaryJobResponse summaryJobResponse(String status) {
        return new SummaryJobResponse(10L, 1L, status, null, null, false,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
