package com.booktown.domain.book.controller;

import com.booktown.domain.auth.security.UserPrincipal;
import com.booktown.domain.book.dto.BookDetailResponse;
import com.booktown.domain.book.dto.BookSummaryResponse;
import com.booktown.domain.book.service.BookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookControllerTest {

    private MockMvc mockMvc;
    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = mock(BookService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookController(bookService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getBooks_returns_paged_books_with_meta() throws Exception {
        BookSummaryResponse book = new BookSummaryResponse(1L, "소나기", "황순원", "PROSE", "KOREA", null, 5);
        when(bookService.getBooks(0, 20, "latest", null))
                .thenReturn(new PageImpl<>(List.of(book), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("소나기"))
                .andExpect(jsonPath("$.data.content[0].author").value("황순원"))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.page").value(0));
    }

    @Test
    void getBooks_applies_genre_filter() throws Exception {
        when(bookService.getBooks(0, 20, "latest", "PROSE"))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/books").param("genre", "PROSE"))
                .andExpect(status().isOk());

        verify(bookService).getBooks(0, 20, "latest", "PROSE");
    }

    @Test
    void getBook_without_principal_passes_null_user_id() throws Exception {
        when(bookService.getBook(1L, null)).thenReturn(bookDetailResponse(null));

        mockMvc.perform(get("/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("소나기"))
                .andExpect(jsonPath("$.data.isBookmarked").isEmpty());

        verify(bookService).getBook(1L, null);
    }

    @Test
    void getBook_with_principal_passes_user_id_and_returns_bookmark_status() throws Exception {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(42L);
        when(principal.getAuthorities()).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        when(bookService.getBook(1L, 42L)).thenReturn(bookDetailResponse(true));

        mockMvc.perform(get("/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isBookmarked").value(true));

        verify(bookService).getBook(1L, 42L);
    }

    @Test
    void searchBooks_delegates_query_to_service() throws Exception {
        when(bookService.searchBooks(eq("소나기"), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/books/search").param("q", "소나기"))
                .andExpect(status().isOk());

        verify(bookService).searchBooks("소나기", 0, 20);
    }

    @Test
    void addBookmark_delegates_to_service_and_returns_no_content() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/books/1/bookmark"))
                .andExpect(status().isNoContent());

        verify(bookService).addBookmark(7L, 1L);
    }

    @Test
    void removeBookmark_delegates_to_service_and_returns_no_content() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(delete("/books/1/bookmark"))
                .andExpect(status().isNoContent());

        verify(bookService).removeBookmark(7L, 1L);
    }

    private void authenticateAs(long userId) {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(userId);
        when(principal.getAuthorities()).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private BookDetailResponse bookDetailResponse(Boolean isBookmarked) {
        return new BookDetailResponse(
                1L,
                "소나기",
                "황순원",
                "PROSE",
                "KOREA",
                "맑은 소년과 소녀의 이야기",
                null,
                5,
                isBookmarked,
                new BookDetailResponse.AvailableFeatures(true, true, true),
                List.of()
        );
    }
}
