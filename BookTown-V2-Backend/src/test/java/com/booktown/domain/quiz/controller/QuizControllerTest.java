package com.booktown.domain.quiz.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.quiz.dto.AnswerResult;
import com.booktown.domain.quiz.dto.QuizDetailResponse;
import com.booktown.domain.quiz.dto.QuizHistoryItem;
import com.booktown.domain.quiz.dto.QuizJobResponse;
import com.booktown.domain.quiz.dto.QuizSubmissionResponse;
import com.booktown.domain.quiz.dto.SubmitQuizRequest;
import com.booktown.domain.quiz.service.QuizService;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.observability.BooktownMetrics;
import com.booktown.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizControllerTest {

    private MockMvc mockMvc;
    private QuizService quizService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        quizService = mock(QuizService.class);
        BooktownMetrics metrics = mock(BooktownMetrics.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuizController(quizService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(metrics))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createQuiz_returns_202_with_job_response() throws Exception {
        authenticateAs(1L);
        QuizJobResponse job = new QuizJobResponse(1L, 1L, "NORMAL", 5, "QUEUED",
                null, null, false, LocalDateTime.now(), LocalDateTime.now());
        when(quizService.createQuiz(anyLong(), anyLong(), any())).thenReturn(job);

        mockMvc.perform(post("/books/1/quizzes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionCount": 5, "difficulty": "NORMAL"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.questionCount").value(5));
    }

    @Test
    void createQuiz_returns_404_when_book_not_found() throws Exception {
        authenticateAs(1L);
        when(quizService.createQuiz(anyLong(), anyLong(), any()))
                .thenThrow(new CustomException(ErrorCode.BOOK_NOT_FOUND));

        mockMvc.perform(post("/books/99/quizzes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionCount": 5, "difficulty": "NORMAL"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void getQuizJob_returns_200_with_job() throws Exception {
        authenticateAs(1L);
        QuizJobResponse job = new QuizJobResponse(1L, 1L, "HARD", 10, "PROCESSING",
                null, null, false, LocalDateTime.now(), LocalDateTime.now());
        when(quizService.getQuizJob(anyLong(), anyLong())).thenReturn(job);

        mockMvc.perform(get("/quiz-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difficulty").value("HARD"))
                .andExpect(jsonPath("$.data.questionCount").value(10));
    }

    @Test
    void getQuizJob_returns_404_when_not_owner() throws Exception {
        authenticateAs(2L);
        when(quizService.getQuizJob(anyLong(), anyLong()))
                .thenThrow(new CustomException(ErrorCode.QUIZ_JOB_NOT_FOUND));

        mockMvc.perform(get("/quiz-jobs/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getQuiz_returns_200_with_questions() throws Exception {
        authenticateAs(1L);
        QuizDetailResponse detail = new QuizDetailResponse(1L, 1L, "EASY", List.of());
        when(quizService.getQuiz(anyLong(), anyLong())).thenReturn(detail);

        mockMvc.perform(get("/quizzes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizId").value(1))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"));
    }

    @Test
    void submitQuiz_returns_201_with_score() throws Exception {
        authenticateAs(1L);
        QuizSubmissionResponse response = new QuizSubmissionResponse(1L, 1L, 50, 1, 2,
                List.of(
                        new AnswerResult(1L, 2, 2, true, "해설1"),
                        new AnswerResult(2L, 1, 3, false, "해설2")
                ),
                LocalDateTime.now());
        when(quizService.submitQuiz(anyLong(), anyLong(), any())).thenReturn(response);

        String body = objectMapper.writeValueAsString(
                new SubmitQuizRequest(List.of(
                        new SubmitQuizRequest.AnswerItem(1L, 2),
                        new SubmitQuizRequest.AnswerItem(2L, 1)
                )));

        mockMvc.perform(post("/quizzes/1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.score").value(50))
                .andExpect(jsonPath("$.data.correctCount").value(1))
                .andExpect(jsonPath("$.data.answers[0].correct").value(true))
                .andExpect(jsonPath("$.data.answers[1].correct").value(false));
    }

    @Test
    void submitQuiz_returns_409_when_already_submitted() throws Exception {
        authenticateAs(1L);
        when(quizService.submitQuiz(anyLong(), anyLong(), any()))
                .thenThrow(new CustomException(ErrorCode.QUIZ_ALREADY_SUBMITTED));

        mockMvc.perform(post("/quizzes/1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubmitQuizRequest(List.of(new SubmitQuizRequest.AnswerItem(1L, 1))))))
                .andExpect(status().isConflict());
    }

    @Test
    void getMyQuizHistory_returns_200_with_page() throws Exception {
        authenticateAs(1L);
        QuizHistoryItem item = new QuizHistoryItem(1L, 1L, 1L, "NORMAL", 80, 4, 5, LocalDateTime.now());
        when(quizService.getMyQuizHistory(anyLong(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/users/me/quizzes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].score").value(80))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    private void authenticateAs(long userId) {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(userId);
        when(principal.getAuthorities()).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
