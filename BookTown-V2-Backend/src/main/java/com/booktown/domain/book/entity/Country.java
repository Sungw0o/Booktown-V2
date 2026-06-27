package com.booktown.domain.book.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Country {
    KOREA("한국"),
    CHINA("중국"),
    JAPAN("일본"),
    WESTERN("서양"),
    OTHER("기타");

    private final String displayName;
}
