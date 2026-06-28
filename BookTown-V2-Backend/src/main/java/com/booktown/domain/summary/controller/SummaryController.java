package com.booktown.domain.summary.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.summary.controller.api.SummaryApi;
import com.booktown.domain.summary.dto.CreateSummaryRequest;
import com.booktown.domain.summary.dto.SummaryFeedbackRequest;
import com.booktown.domain.summary.dto.SummaryJobResponse;
import com.booktown.domain.summary.dto.SummaryResponse;
import com.booktown.domain.summary.service.SummaryService;
import com.booktown.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SummaryController implements SummaryApi {

    private final SummaryService summaryService;

    @Override
    public ResponseEntity<ApiResponse<SummaryJobResponse>> createSummary(
            Long bookId, CreateSummaryRequest request, UserPrincipal principal) {
        CreateSummaryRequest req = request != null ? request : new CreateSummaryRequest(null);
        SummaryJobResponse response = summaryService.createSummary(principal.getId(), bookId, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @Override
    public ApiResponse<SummaryJobResponse> getSummaryJob(Long jobId, UserPrincipal principal) {
        return ApiResponse.success(summaryService.getSummaryJob(principal.getId(), jobId));
    }

    @Override
    public ApiResponse<List<SummaryResponse>> getSummaries(Long bookId, UserPrincipal principal) {
        return ApiResponse.success(summaryService.getSummaries(principal.getId(), bookId));
    }

    @Override
    public ApiResponse<SummaryResponse> getSummary(String summaryId, UserPrincipal principal) {
        return ApiResponse.success(summaryService.getSummary(principal.getId(), summaryId));
    }

    @Override
    public ResponseEntity<ApiResponse<SummaryJobResponse>> regenerateSummary(
            String summaryId, UserPrincipal principal) {
        SummaryJobResponse response = summaryService.regenerateSummary(principal.getId(), summaryId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @Override
    public ResponseEntity<Void> saveFeedback(
            String summaryId, SummaryFeedbackRequest request, UserPrincipal principal) {
        summaryService.saveFeedback(principal.getId(), summaryId, request);
        return ResponseEntity.noContent().build();
    }
}
