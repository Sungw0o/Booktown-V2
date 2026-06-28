package com.booktown.domain.illustration.controller.api;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.illustration.dto.CreateIllustrationRequest;
import com.booktown.domain.illustration.dto.IllustrationJobResponse;
import com.booktown.domain.illustration.dto.IllustrationResponse;
import com.booktown.domain.illustration.dto.SceneResponse;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.PageMeta;
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

import java.util.List;

@Tag(name = "Illustrations", description = "장면 일러스트 생성 API")
@SecurityRequirement(name = "bearerAuth")
public interface IllustrationApi {

    @Operation(summary = "도서 장면 목록 조회")
    @GetMapping("/books/{bookId}/scenes")
    ApiResponse<Page<SceneResponse>> getScenes(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "장면 일러스트 생성 요청")
    @PostMapping("/scenes/{sceneId}/illustrations")
    ResponseEntity<ApiResponse<IllustrationJobResponse>> createIllustration(
            @PathVariable Long sceneId,
            @Valid @RequestBody CreateIllustrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "일러스트 생성 Job 조회")
    @GetMapping("/illustration-jobs/{jobId}")
    ApiResponse<IllustrationJobResponse> getIllustrationJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "장면 일러스트 목록 조회")
    @GetMapping("/scenes/{sceneId}/illustrations")
    ApiResponse<List<IllustrationResponse>> getIllustrations(
            @PathVariable Long sceneId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(summary = "일러스트 재생성 요청")
    @PostMapping("/illustrations/{illustrationId}/regenerations")
    ResponseEntity<ApiResponse<IllustrationJobResponse>> regenerateIllustration(
            @PathVariable String illustrationId,
            @AuthenticationPrincipal UserPrincipal principal
    );
}
