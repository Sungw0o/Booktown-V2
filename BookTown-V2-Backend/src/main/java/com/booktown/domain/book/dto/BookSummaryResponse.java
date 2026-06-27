package com.booktown.domain.book.dto;

import com.booktown.domain.book.entity.Book;

public record BookSummaryResponse(
        Long id,
        String title,
        String author,
        String genre,
        String country,
        String coverImageUrl,
        int bookmarkCount
) {
    public static BookSummaryResponse from(Book book) {
        return new BookSummaryResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getGenre().name(),
                book.getCountry().name(),
                book.getCoverImageUrl(),
                book.getBookmarkCount()
        );
    }
}
