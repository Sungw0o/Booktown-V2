package com.booktown.domain.illustration.repository;

import com.booktown.domain.illustration.entity.IllustrationJob;
import com.booktown.domain.illustration.entity.IllustrationJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IllustrationJobRepository extends JpaRepository<IllustrationJob, Long> {

    Optional<IllustrationJob> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndSceneIdAndStatusIn(Long userId, Long sceneId, List<IllustrationJobStatus> statuses);

    int countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}
