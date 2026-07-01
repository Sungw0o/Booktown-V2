package com.booktown.domain.admin.gutendex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GutendexPageDto(
        int count,
        String next,
        String previous,
        List<GutendexBookDto> results
) {
}
