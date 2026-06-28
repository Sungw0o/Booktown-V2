package com.booktown.domain.summary.repository;

import com.booktown.domain.summary.document.SummaryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SummaryDocumentRepository extends MongoRepository<SummaryDocument, String> {

    List<SummaryDocument> findAllByBookIdAndUserIdOrderByCreatedAtDesc(Long bookId, Long userId);

    Optional<SummaryDocument> findByIdAndUserId(String id, Long userId);
}
