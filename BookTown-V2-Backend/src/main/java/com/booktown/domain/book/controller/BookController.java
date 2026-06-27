package com.booktown.domain.book.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.book.controller.api.BookApi;
import com.booktown.domain.book.dto.BookDetailResponse;
import com.booktown.domain.book.dto.BookSummaryResponse;
import com.booktown.domain.book.service.BookService;
import com.booktown.global.response.ApiResponse;
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookController implements BookApi {

    private final BookService bookService;

    @Override
    public ApiResponse<Page<BookSummaryResponse>> getBooks(int page, int size, String sort, String genre) {
        Page<BookSummaryResponse> result = bookService.getBooks(page, size, sort, genre);
        return ApiResponse.success(result, PageMeta.of(result));
    }

    @Override
    public ApiResponse<BookDetailResponse> getBook(Long bookId, UserPrincipal principal) {
        Long userId = (principal != null) ? principal.getId() : null;
        return ApiResponse.success(bookService.getBook(bookId, userId));
    }

    @Override
    public ApiResponse<Page<BookSummaryResponse>> searchBooks(String q, int page, int size) {
        Page<BookSummaryResponse> result = bookService.searchBooks(q, page, size);
        return ApiResponse.success(result, PageMeta.of(result));
    }

    @Override
    public ResponseEntity<Void> addBookmark(Long bookId, UserPrincipal principal) {
        bookService.addBookmark(principal.getId(), bookId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeBookmark(Long bookId, UserPrincipal principal) {
        bookService.removeBookmark(principal.getId(), bookId);
        return ResponseEntity.noContent().build();
    }
}
