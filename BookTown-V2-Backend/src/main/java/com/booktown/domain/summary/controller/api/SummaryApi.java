package com.booktown.domain.summary.controller.api;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.summary.dto.CreateSummaryRequest;
import com.booktown.domain.summary.dto.SummaryFeedbackRequest;
import com.booktown.domain.summary.dto.SummaryJobResponse;
import com.booktown.domain.summary.dto.SummaryResponse;
import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping
@Tag(name = "Summaries", description = "AI 요약 생성·조회·재생성·피드백 API")
@SecurityRequirement(name = "bearerAuth")
public interface SummaryApi {

    @PostMapping("/books/{bookId}/summaries")
    @Operation(summary = "요약 생성 요청", description = "RAG 기반 AI 요약 Job을 생성합니다. 202 Accepted로 jobId를 반환합니다.")
    ResponseEntity<ApiResponse<SummaryJobResponse>> createSummary(
            @PathVariable Long bookId,
            @RequestBody(required = false) CreateSummaryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @GetMapping("/summary-jobs/{jobId}")
    @Operation(summary = "요약 Job 조회", description = "요약 생성 Job 상태와 완료 시 summaryId를 반환합니다.")
    ApiResponse<SummaryJobResponse> getSummaryJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @GetMapping("/books/{bookId}/summaries")
    @Operation(summary = "도서 요약 목록", description = "로그인 사용자의 해당 도서 요약 목록을 최신순으로 반환합니다.")
    ApiResponse<List<SummaryResponse>> getSummaries(
            @PathVariable Long bookId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @GetMapping("/summaries/{summaryId}")
    @Operation(summary = "요약 상세", description = "요약 본문과 피드백 정보를 반환합니다.")
    ApiResponse<SummaryResponse> getSummary(
            @PathVariable String summaryId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @PostMapping("/summaries/{summaryId}/regenerations")
    @Operation(summary = "요약 재생성", description = "기존 요약을 보존하면서 새 요약 Job을 생성합니다. 202 반환.")
    ResponseEntity<ApiResponse<SummaryJobResponse>> regenerateSummary(
            @PathVariable String summaryId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @PutMapping("/summaries/{summaryId}/feedback")
    @Operation(summary = "요약 피드백", description = "요약에 대한 1~5점 피드백을 저장합니다. 사용자당 1회.")
    ResponseEntity<Void> saveFeedback(
            @PathVariable String summaryId,
            @Valid @RequestBody SummaryFeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );
}
