package com.booktown.domain.summary.dto;

public record CreateSummaryRequest(
        String scope
) {
    public boolean isFullBook() {
        return scope == null || scope.isBlank() || scope.equalsIgnoreCase("full");
    }
}
