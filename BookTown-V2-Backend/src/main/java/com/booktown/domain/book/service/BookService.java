package com.booktown.domain.book.service;

import com.booktown.domain.book.dto.BookDetailResponse;
import com.booktown.domain.book.dto.BookSummaryResponse;
import com.booktown.domain.book.dto.BookmarkItemResponse;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Bookmark;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.BookmarkRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<BookSummaryResponse> getBooks(int page, int size, String sort, String genre) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Book> books = (genre != null && !genre.isBlank())
                ? bookRepository.findAllByGenre(Genre.valueOf(genre.toUpperCase()), pageable)
                : bookRepository.findAll(pageable);
        return books.map(BookSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public BookDetailResponse getBook(Long bookId, Long userId) {
        Book book = findBook(bookId);
        List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(bookId);
        Boolean isBookmarked = (userId != null)
                ? bookmarkRepository.existsByUserIdAndBookId(userId, bookId)
                : null;
        return BookDetailResponse.of(book, isBookmarked, chapters);
    }

    @Transactional(readOnly = true)
    public Page<BookSummaryResponse> searchBooks(String q, int page, int size) {
        if (!StringUtils.hasText(q)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return bookRepository.searchByTitleOrAuthor(q.trim(), pageable)
                .map(BookSummaryResponse::from);
    }

    @Transactional
    public void addBookmark(Long userId, Long bookId) {
        if (bookmarkRepository.existsByUserIdAndBookId(userId, bookId)) {
            return;
        }
        User user = findUser(userId);
        Book book = findBook(bookId);
        bookmarkRepository.save(Bookmark.create(user, book));
        book.increaseBookmarkCount();
    }

    @Transactional
    public void removeBookmark(Long userId, Long bookId) {
        bookmarkRepository.findByUserIdAndBookId(userId, bookId).ifPresent(bookmark -> {
            bookmarkRepository.delete(bookmark);
            findBook(bookId).decreaseBookmarkCount();
        });
    }

    @Transactional(readOnly = true)
    public Page<BookmarkItemResponse> getMyBookmarks(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return bookmarkRepository.findAllByUserId(userId, pageable)
                .map(BookmarkItemResponse::from);
    }

    private Sort resolveSort(String sort) {
        if (sort == null) return Sort.by("createdAt").descending();
        return switch (sort.toLowerCase()) {
            case "popular" -> Sort.by("bookmarkCount").descending();
            case "title" -> Sort.by("title").ascending();
            default -> Sort.by("createdAt").descending();
        };
    }

    private Book findBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public record BooksPage(List<BookSummaryResponse> items, PageMeta meta) {}
}
