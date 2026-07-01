package com.booktown.domain.admin.gutendex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GutendexBookDto(
        Long id,
        String title,
        List<GutendexAuthorDto> authors,
        List<String> summaries,
        List<String> subjects,
        List<String> bookshelves,
        List<String> languages,
        Boolean copyright,
        Map<String, String> formats,
        @JsonProperty("download_count") Integer downloadCount
) {
}
