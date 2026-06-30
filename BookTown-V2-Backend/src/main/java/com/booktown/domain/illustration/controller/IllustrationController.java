package com.booktown.domain.illustration.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.illustration.controller.api.IllustrationApi;
import com.booktown.domain.illustration.dto.CreateIllustrationRequest;
import com.booktown.domain.illustration.dto.IllustrationJobResponse;
import com.booktown.domain.illustration.dto.IllustrationResponse;
import com.booktown.domain.illustration.dto.SceneResponse;
import com.booktown.domain.illustration.service.IllustrationService;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class IllustrationController implements IllustrationApi {

    private final IllustrationService illustrationService;

    @Override
    public ApiResponse<Page<SceneResponse>> getScenes(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<SceneResponse> result = illustrationService.getScenes(bookId, page, size);
        return ApiResponse.success(result, PageMeta.of(result));
    }

    @Override
    public ResponseEntity<ApiResponse<IllustrationJobResponse>> createIllustration(
            @PathVariable Long sceneId,
            @RequestBody CreateIllustrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        IllustrationJobResponse job = illustrationService.createIllustration(principal.getId(), sceneId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(job));
    }

    @Override
    public ApiResponse<IllustrationJobResponse> getIllustrationJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(illustrationService.getIllustrationJob(principal.getId(), jobId));
    }

    @Override
    public ApiResponse<List<IllustrationResponse>> getIllustrations(
            @PathVariable Long sceneId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(illustrationService.getIllustrations(principal.getId(), sceneId));
    }

    @Override
    public ResponseEntity<ApiResponse<IllustrationJobResponse>> regenerateIllustration(
            @PathVariable String illustrationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        IllustrationJobResponse job = illustrationService.regenerateIllustration(principal.getId(), illustrationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(job));
    }
}
