package com.booktown.domain.admin.controller.api;

import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.GeneratedCoverResponse;
import com.booktown.domain.admin.dto.RegisterBookRequest;
import com.booktown.domain.admin.dto.RegisterBookResponse;
import com.booktown.domain.admin.gutendex.GutendexBookSearchResponse;
import com.booktown.domain.admin.gutendex.GutendexImportRequest;
import com.booktown.domain.admin.gutendex.GutendexImportResponse;
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

    @GetMapping("/gutendex/books")
    @Operation(summary = "Gutendex 도서 검색", description = "Project Gutenberg 도서를 Gutendex API로 검색합니다. 영어 public domain 텍스트 보유 도서만 조회합니다.")
    ApiResponse<GutendexBookSearchResponse> searchGutendexBooks(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "1") int page
    );

    @PostMapping("/gutendex/books/{gutenbergId}/import")
    @Operation(summary = "Gutendex 도서 가져오기", description = "Gutendex 도서 메타데이터를 Book으로 등록하고 원문 다운로드/챕터 분리 Job을 시작합니다. AI 키 없이 동작합니다.")
    ResponseEntity<ApiResponse<GutendexImportResponse>> importGutendexBook(
            @PathVariable Long gutenbergId,
            @RequestBody(required = false) GutendexImportRequest request
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

    @PostMapping("/books/{bookId}/cover")
    @Operation(summary = "AI 표지 생성", description = "AI 이미지 모델로 도서 표지를 생성하고 도서 coverImageUrl을 갱신합니다.")
    ApiResponse<GeneratedCoverResponse> generateCover(
            @PathVariable Long bookId
    );
}
