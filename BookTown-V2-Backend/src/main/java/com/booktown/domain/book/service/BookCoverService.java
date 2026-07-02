package com.booktown.domain.book.service;

import com.booktown.domain.admin.dto.GeneratedCoverResponse;
import com.booktown.domain.book.document.BookCoverImageDocument;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.repository.BookCoverImageRepository;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.illustration.service.GeminiImageClient;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCoverService {

    private final BookRepository bookRepository;
    private final BookCoverImageRepository bookCoverImageRepository;
    private final GeminiImageClient geminiImageClient;

    @Transactional
    public GeneratedCoverResponse generateCover(Long bookId) {
        Book book = findBook(bookId);
        GeminiImageClient.GeneratedImage image = geminiImageClient.generateImage(buildCoverPrompt(book));

        bookCoverImageRepository.deleteAllByBookId(bookId);
        bookCoverImageRepository.save(BookCoverImageDocument.create(bookId, image.mimeType(), image.bytes()));

        String coverImageUrl = "/api/v1/books/" + bookId + "/cover-image";
        book.updateCoverImageUrl(coverImageUrl);
        bookRepository.save(book);

        return new GeneratedCoverResponse(bookId, coverImageUrl);
    }

    @Transactional(readOnly = true)
    public CoverImage getCoverImage(Long bookId) {
        BookCoverImageDocument document = bookCoverImageRepository.findByBookId(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
        return new CoverImage(document.getMimeType(), document.getData());
    }

    private Book findBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
    }

    private String buildCoverPrompt(Book book) {
        String description = book.getDescription() == null ? "" : book.getDescription();
        String trimmedDescription = description.length() > 900
                ? description.substring(0, 900)
                : description;
        return """
                Create an original vertical book cover illustration for a classic literature reading app.
                Do not copy any existing cover art. Do not include readable text, logos, author portraits, or publisher marks.
                Mood: cinematic, literary, elegant, emotionally rich, suitable for a public domain classic.
                Composition: centered cover art, strong silhouette, refined colors, high contrast, no typography.
                Title: %s
                Author: %s
                Genre: %s
                Description: %s
                """.formatted(book.getTitle(), book.getAuthor(), book.getGenre().name(), trimmedDescription);
    }

    public record CoverImage(String mimeType, byte[] data) {
        public MediaType mediaType() {
            return MediaType.parseMediaType(mimeType);
        }
    }
}
