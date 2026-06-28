package com.booktown.domain.admin.controller.api;

import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.RegisterBookRequest;
import com.booktown.domain.admin.dto.RegisterBookResponse;
import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/admin")
@Tag(name = "Admin", description = "관리자 도서 등록·원문 업로드 API")
@SecurityRequirement(name = "bearerAuth")
public interface AdminBookApi {

    @PostMapping("/books")
    @Operation(summary = "도서 등록", description = "ADMIN 권한으로 도서 메타데이터를 등록합니다.")
    ResponseEntity<ApiResponse<RegisterBookResponse>> registerBook(
            @Valid @RequestBody RegisterBookRequest request
    );

    @PostMapping(value = "/books/{bookId}/contents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "원문 업로드", description = "TXT(UTF-8, 10MB 이하) 파일을 업로드하고 챕터 분리 Job을 시작합니다. 202 Accepted로 jobId를 반환합니다.")
    ResponseEntity<ApiResponse<ContentJobResponse>> uploadContent(
            @PathVariable Long bookId,
            @Parameter(description = "원문 TXT 파일 (UTF-8, 최대 10MB)")
            @RequestParam("file") MultipartFile file
    );

    @GetMapping("/content-jobs/{jobId}")
    @Operation(summary = "원문 처리 Job 조회", description = "jobId로 원문 처리 상태를 조회합니다.")
    ApiResponse<ContentJobResponse> getContentJob(
            @PathVariable Long jobId
    );
}
