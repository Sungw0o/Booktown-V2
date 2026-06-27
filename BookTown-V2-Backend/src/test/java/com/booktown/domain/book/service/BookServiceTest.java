package com.booktown.domain.book.service;

import com.booktown.domain.book.dto.BookDetailResponse;
import com.booktown.domain.book.dto.BookSummaryResponse;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Bookmark;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.BookmarkRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookServiceTest {

    private BookRepository bookRepository;
    private ChapterRepository chapterRepository;
    private BookmarkRepository bookmarkRepository;
    private UserRepository userRepository;
    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        chapterRepository = mock(ChapterRepository.class);
        bookmarkRepository = mock(BookmarkRepository.class);
        userRepository = mock(UserRepository.class);
        bookService = new BookService(bookRepository, chapterRepository, bookmarkRepository, userRepository);
    }

    @Test
    void getBooks_without_genre_calls_findAll() {
        when(bookRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleBook()), PageRequest.of(0, 20), 1));

        Page<BookSummaryResponse> result = bookService.getBooks(0, 20, "latest", null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("소나기");
        verify(bookRepository).findAll(any(Pageable.class));
        verify(bookRepository, never()).findAllByGenre(any(), any());
    }

    @Test
    void getBooks_with_blank_genre_also_calls_findAll() {
        when(bookRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        bookService.getBooks(0, 20, "latest", "  ");

        verify(bookRepository).findAll(any(Pageable.class));
        verify(bookRepository, never()).findAllByGenre(any(), any());
    }

    @Test
    void getBooks_with_genre_calls_findAllByGenre() {
        when(bookRepository.findAllByGenre(eq(Genre.PROSE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleBook()), PageRequest.of(0, 20), 1));

        bookService.getBooks(0, 20, "popular", "PROSE");

        verify(bookRepository).findAllByGenre(eq(Genre.PROSE), any(Pageable.class));
        verify(bookRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getBook_without_user_returns_null_isBookmarked() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook()));
        when(chapterRepository.findAllByBookIdOrderByChapterNumberAsc(1L)).thenReturn(List.of());

        BookDetailResponse result = bookService.getBook(1L, null);

        assertThat(result.isBookmarked()).isNull();
        verify(bookmarkRepository, never()).existsByUserIdAndBookId(any(), any());
    }

    @Test
    void getBook_with_user_checks_and_returns_bookmark_status() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook()));
        when(chapterRepository.findAllByBookIdOrderByChapterNumberAsc(1L)).thenReturn(List.of());
        when(bookmarkRepository.existsByUserIdAndBookId(42L, 1L)).thenReturn(true);

        BookDetailResponse result = bookService.getBook(1L, 42L);

        assertThat(result.isBookmarked()).isTrue();
        verify(bookmarkRepository).existsByUserIdAndBookId(42L, 1L);
    }

    @Test
    void getBook_not_found_throws_book_not_found() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBook(99L, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void searchBooks_with_blank_query_throws_invalid_input() {
        assertThatThrownBy(() -> bookService.searchBooks("   ", 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void searchBooks_trims_query_and_delegates_to_repository() {
        when(bookRepository.searchByTitleOrAuthor(eq("소나기"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleBook()), PageRequest.of(0, 20), 1));

        Page<BookSummaryResponse> result = bookService.searchBooks(" 소나기 ", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(bookRepository).searchByTitleOrAuthor(eq("소나기"), any(Pageable.class));
    }

    @Test
    void addBookmark_when_already_exists_is_idempotent() {
        when(bookmarkRepository.existsByUserIdAndBookId(1L, 10L)).thenReturn(true);

        bookService.addBookmark(1L, 10L);

        verify(bookmarkRepository, never()).save(any());
        verify(bookRepository, never()).findById(any());
    }

    @Test
    void addBookmark_creates_new_bookmark_and_increments_count() {
        Book book = sampleBook();
        User user = mock(User.class);
        when(bookmarkRepository.existsByUserIdAndBookId(1L, 10L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

        bookService.addBookmark(1L, 10L);

        verify(bookmarkRepository).save(any(Bookmark.class));
        assertThat(book.getBookmarkCount()).isEqualTo(1);
    }

    @Test
    void removeBookmark_when_exists_deletes_and_decrements_count() {
        Book book = sampleBook();
        book.increaseBookmarkCount();
        Bookmark bookmark = mock(Bookmark.class);
        when(bookmarkRepository.findByUserIdAndBookId(1L, 10L)).thenReturn(Optional.of(bookmark));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

        bookService.removeBookmark(1L, 10L);

        verify(bookmarkRepository).delete(bookmark);
        assertThat(book.getBookmarkCount()).isEqualTo(0);
    }

    @Test
    void removeBookmark_when_not_exists_is_idempotent() {
        when(bookmarkRepository.findByUserIdAndBookId(1L, 10L)).thenReturn(Optional.empty());

        bookService.removeBookmark(1L, 10L);

        verify(bookmarkRepository, never()).delete(any());
        verify(bookRepository, never()).findById(any());
    }

    private Book sampleBook() {
        return Book.create("소나기", "황순원", "맑은 소년과 소녀의 이야기", null, Genre.PROSE, Country.KOREA);
    }
}
