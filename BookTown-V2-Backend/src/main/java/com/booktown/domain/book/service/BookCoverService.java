package com.booktown.domain.book.service;

import com.booktown.domain.admin.dto.GeneratedCoverResponse;
import com.booktown.domain.book.document.BookCoverImageDocument;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.repository.BookCoverImageRepository;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.illustration.service.ImageGenerationClient;
import com.booktown.domain.illustration.service.BookVisualPromptFactory;
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
    private final ImageGenerationClient imageGenerationClient;
    private final BookVisualPromptFactory bookVisualPromptFactory;

    @Transactional
    public GeneratedCoverResponse generateCover(Long bookId) {
        Book book = findBook(bookId);
        ImageGenerationClient.GeneratedImage image = imageGenerationClient.generateImage(
                bookVisualPromptFactory.createCoverPrompt(book)
        );

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

    public record CoverImage(String mimeType, byte[] data) {
        public MediaType mediaType() {
            return MediaType.parseMediaType(mimeType);
        }
    }
}
