package com.booktown.domain.book.dto;

import com.booktown.domain.book.entity.Bookmark;

import java.time.format.DateTimeFormatter;

public record BookmarkItemResponse(
        Long bookmarkId,
        Long bookId,
        String title,
        String author,
        String coverImageUrl,
        String genre,
        String bookmarkedAt
) {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static BookmarkItemResponse from(Bookmark bookmark) {
        return new BookmarkItemResponse(
                bookmark.getId(),
                bookmark.getBook().getId(),
                bookmark.getBook().getTitle(),
                bookmark.getBook().getAuthor(),
                bookmark.getBook().getCoverImageUrl(),
                bookmark.getBook().getGenre().name(),
                bookmark.getCreatedAt().format(ISO)
        );
    }
}
