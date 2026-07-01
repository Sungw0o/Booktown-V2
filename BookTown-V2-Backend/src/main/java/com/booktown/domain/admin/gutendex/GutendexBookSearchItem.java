package com.booktown.domain.admin.gutendex;

import java.util.List;

public record GutendexBookSearchItem(
        Long gutenbergId,
        String title,
        String author,
        String description,
        String coverImageUrl,
        String textPlainUrl,
        String suggestedGenre,
        String suggestedCountry,
        List<String> subjects,
        List<String> bookshelves,
        Integer downloadCount
) {
}
