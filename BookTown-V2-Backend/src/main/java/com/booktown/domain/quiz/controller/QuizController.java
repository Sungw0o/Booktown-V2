package com.booktown.domain.quiz.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.quiz.controller.api.QuizApi;
import com.booktown.domain.quiz.dto.CreateQuizRequest;
import com.booktown.domain.quiz.dto.QuizDetailResponse;
import com.booktown.domain.quiz.dto.QuizHistoryItem;
import com.booktown.domain.quiz.dto.QuizJobResponse;
import com.booktown.domain.quiz.dto.QuizSubmissionResponse;
import com.booktown.domain.quiz.dto.SubmitQuizRequest;
import com.booktown.domain.quiz.service.QuizService;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuizController implements QuizApi {

    private final QuizService quizService;

    @Override
    public ResponseEntity<ApiResponse<QuizJobResponse>> createQuiz(
            @PathVariable Long bookId,
            @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizJobResponse job = quizService.createQuiz(principal.getId(), bookId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(job));
    }

    @Override
    public ApiResponse<QuizJobResponse> getQuizJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(quizService.getQuizJob(principal.getId(), jobId));
    }

    @Override
    public ApiResponse<QuizDetailResponse> getQuiz(
            @PathVariable Long quizId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(quizService.getQuiz(principal.getId(), quizId));
    }

    @Override
    public ResponseEntity<ApiResponse<QuizSubmissionResponse>> submitQuiz(
            @PathVariable Long quizId,
            @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizSubmissionResponse response = quizService.submitQuiz(principal.getId(), quizId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Override
    public ApiResponse<Page<QuizHistoryItem>> getMyQuizHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<QuizHistoryItem> result = quizService.getMyQuizHistory(principal.getId(), page, size);
        return ApiResponse.success(result, PageMeta.of(result));
    }
}
