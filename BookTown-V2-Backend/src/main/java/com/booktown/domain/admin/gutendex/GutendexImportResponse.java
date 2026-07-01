package com.booktown.domain.admin.gutendex;

import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.RegisterBookResponse;

public record GutendexImportResponse(
        RegisterBookResponse book,
        ContentJobResponse contentJob
) {
}
