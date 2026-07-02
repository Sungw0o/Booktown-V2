package com.booktown.domain.admin.controller;

import com.booktown.domain.admin.controller.api.AdminBookApi;
import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.GeneratedCoverResponse;
import com.booktown.domain.admin.dto.RegisterBookRequest;
import com.booktown.domain.admin.dto.RegisterBookResponse;
import com.booktown.domain.admin.gutendex.GutendexBookSearchResponse;
import com.booktown.domain.admin.gutendex.GutendexImportRequest;
import com.booktown.domain.admin.gutendex.GutendexImportResponse;
import com.booktown.domain.admin.service.AdminBookService;
import com.booktown.domain.book.service.BookCoverService;
import com.booktown.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AdminBookController implements AdminBookApi {

    private final AdminBookService adminBookService;
    private final BookCoverService bookCoverService;

    @Override
    public ResponseEntity<ApiResponse<RegisterBookResponse>> registerBook(RegisterBookRequest request) {
        RegisterBookResponse response = adminBookService.registerBook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Override
    public ApiResponse<GutendexBookSearchResponse> searchGutendexBooks(String keyword, int page) {
        return ApiResponse.success(adminBookService.searchGutendexBooks(keyword, page));
    }

    @Override
    public ResponseEntity<ApiResponse<GutendexImportResponse>> importGutendexBook(Long gutenbergId, GutendexImportRequest request) {
        GutendexImportResponse response = adminBookService.importGutendexBook(gutenbergId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @Override
    public ResponseEntity<ApiResponse<ContentJobResponse>> uploadContent(Long bookId, MultipartFile file) {
        ContentJobResponse response = adminBookService.uploadContent(bookId, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @Override
    public ApiResponse<ContentJobResponse> getContentJob(Long jobId) {
        return ApiResponse.success(adminBookService.getContentJob(jobId));
    }

    @Override
    public ApiResponse<GeneratedCoverResponse> generateCover(Long bookId) {
        return ApiResponse.success(bookCoverService.generateCover(bookId));
    }
}
