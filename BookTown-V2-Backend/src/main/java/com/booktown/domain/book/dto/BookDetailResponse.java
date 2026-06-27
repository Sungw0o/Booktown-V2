package com.booktown.domain.book.dto;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;

import java.util.List;

public record BookDetailResponse(
        Long id,
        String title,
        String author,
        String genre,
        String country,
        String description,
        String coverImageUrl,
        int bookmarkCount,
        Boolean isBookmarked,
        AvailableFeatures availableFeatures,
        List<ChapterResponse> chapters
) {
    public record AvailableFeatures(boolean summary, boolean illustration, boolean quiz) {
        public static AvailableFeatures from(boolean hasContent) {
            return new AvailableFeatures(hasContent, hasContent, hasContent);
        }
    }

    public record ChapterResponse(Long id, int chapterNumber, String title) {
        public static ChapterResponse from(Chapter chapter) {
            return new ChapterResponse(chapter.getId(), chapter.getChapterNumber(), chapter.getTitle());
        }
    }

    public static BookDetailResponse of(Book book, Boolean isBookmarked, List<Chapter> chapters) {
        return new BookDetailResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getGenre().name(),
                book.getCountry().name(),
                book.getDescription(),
                book.getCoverImageUrl(),
                book.getBookmarkCount(),
                isBookmarked,
                AvailableFeatures.from(book.isHasContent()),
                chapters.stream().map(ChapterResponse::from).toList()
        );
    }
}
