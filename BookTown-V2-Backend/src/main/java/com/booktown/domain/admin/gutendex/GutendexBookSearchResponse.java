package com.booktown.domain.admin.gutendex;

import java.util.List;

public record GutendexBookSearchResponse(
        int count,
        Integer nextPage,
        Integer previousPage,
        List<GutendexBookSearchItem> books
) {
}
