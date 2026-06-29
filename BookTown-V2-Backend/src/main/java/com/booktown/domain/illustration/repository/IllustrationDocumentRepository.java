package com.booktown.domain.illustration.repository;

import com.booktown.domain.illustration.document.IllustrationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface IllustrationDocumentRepository extends MongoRepository<IllustrationDocument, String> {

    List<IllustrationDocument> findAllBySceneIdAndUserIdOrderByCreatedAtDesc(Long sceneId, Long userId);

    Optional<IllustrationDocument> findByIdAndUserId(String id, Long userId);
}
