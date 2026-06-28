package com.booktown.domain.admin.dto;

public record RegisterBookResponse(
        Long bookId,
        String title,
        String author,
        String genre,
        String country
) {}
