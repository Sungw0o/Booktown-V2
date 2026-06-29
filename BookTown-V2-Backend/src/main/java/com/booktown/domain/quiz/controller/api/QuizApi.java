package com.booktown.domain.quiz.controller.api;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.quiz.dto.CreateQuizRequest;
import com.booktown.domain.quiz.dto.QuizDetailResponse;
import com.booktown.domain.quiz.dto.QuizHistoryItem;
import com.booktown.domain.quiz.dto.QuizJobResponse;
import com.booktown.domain.quiz.dto.QuizSubmissionResponse;
import com.booktown.domain.quiz.dto.SubmitQuizRequest;
import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Quizzes", description = "퀴즈 생성·채점·히스토리 API")
@SecurityRequirement(name = "bearerAuth")
public interface QuizApi {

    @Operation(summary = "객관식 퀴즈 생성 요청")
    @PostMapping("/books/{bookId}/quizzes")
    ResponseEntity<ApiResponse<QuizJobResponse>> createQuiz(
            @PathVariable Long bookId,
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "퀴즈 생성 Job 조회")
    @GetMapping("/quiz-jobs/{jobId}")
    ApiResponse<QuizJobResponse> getQuizJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "퀴즈 상세 조회 (정답·해설 미포함)")
    @GetMapping("/quizzes/{quizId}")
    ApiResponse<QuizDetailResponse> getQuiz(
            @PathVariable Long quizId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "퀴즈 답안 제출 및 서버 채점")
    @PostMapping("/quizzes/{quizId}/submissions")
    ResponseEntity<ApiResponse<QuizSubmissionResponse>> submitQuiz(
            @PathVariable Long quizId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "내 퀴즈 히스토리 조회")
    @GetMapping("/users/me/quizzes")
    ApiResponse<Page<QuizHistoryItem>> getMyQuizHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    );
}
