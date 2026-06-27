package com.booktown.domain.book.controller.api;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.book.dto.BookDetailResponse;
import com.booktown.domain.book.dto.BookSummaryResponse;
import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/books")
@Tag(name = "Books", description = "도서 탐색·상세·검색·찜하기 API")
public interface BookApi {

    @GetMapping
    @Operation(summary = "도서 목록", description = "페이지 형식으로 도서 목록을 반환합니다. sort: popular·latest·title, genre: POETRY·PROSE·NOVEL·DRAMA·ESSAY·HISTORY")
    ApiResponse<Page<BookSummaryResponse>> getBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @Parameter(description = "장르 코드 (없으면 전체)") @RequestParam(required = false) String genre
    );

    @GetMapping("/{bookId}")
    @Operation(summary = "도서 상세", description = "도서 메타데이터, 챕터 목록, 제공 기능을 반환합니다. 인증 시 isBookmarked 포함.")
    ApiResponse<BookDetailResponse> getBook(
            @PathVariable Long bookId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @GetMapping("/search")
    @Operation(summary = "도서 검색", description = "제목·저자 키워드 검색. 빈 검색어는 400 반환.")
    ApiResponse<Page<BookSummaryResponse>> searchBooks(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );

    @PostMapping("/{bookId}/bookmark")
    @Operation(summary = "찜 추가", description = "멱등하게 찜 상태를 생성합니다. 이미 찜한 경우 무시합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Void> addBookmark(
            @PathVariable Long bookId,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @DeleteMapping("/{bookId}/bookmark")
    @Operation(summary = "찜 취소", description = "멱등하게 찜 상태를 제거합니다. 찜하지 않은 경우 무시합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Void> removeBookmark(
            @PathVariable Long bookId,
            @AuthenticationPrincipal UserPrincipal principal
    );
}
