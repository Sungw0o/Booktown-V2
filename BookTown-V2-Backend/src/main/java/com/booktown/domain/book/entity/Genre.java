package com.booktown.domain.book.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Genre {
    POETRY("시"),
    PROSE("산문"),
    NOVEL("소설"),
    DRAMA("희곡"),
    ESSAY("수필"),
    HISTORY("역사");

    private final String displayName;
}
