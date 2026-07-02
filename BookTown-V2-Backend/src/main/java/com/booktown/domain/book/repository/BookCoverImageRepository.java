package com.booktown.domain.book.repository;

import com.booktown.domain.book.document.BookCoverImageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BookCoverImageRepository extends MongoRepository<BookCoverImageDocument, String> {

    Optional<BookCoverImageDocument> findByBookId(Long bookId);

    void deleteAllByBookId(Long bookId);
}
