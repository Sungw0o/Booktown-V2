package com.booktown.domain.book.document;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "book_cover_images")
public class BookCoverImageDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long bookId;

    private String mimeType;

    private byte[] data;

    private LocalDateTime createdAt;

    public static BookCoverImageDocument create(Long bookId, String mimeType, byte[] data) {
        BookCoverImageDocument document = new BookCoverImageDocument();
        document.bookId = bookId;
        document.mimeType = mimeType;
        document.data = data;
        document.createdAt = LocalDateTime.now();
        return document;
    }
}
