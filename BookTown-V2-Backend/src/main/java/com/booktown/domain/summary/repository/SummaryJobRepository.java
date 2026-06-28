package com.booktown.domain.summary.repository;

import com.booktown.domain.summary.entity.SummaryJob;
import com.booktown.domain.summary.entity.SummaryJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SummaryJobRepository extends JpaRepository<SummaryJob, Long> {

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, List<SummaryJobStatus> statuses);

    int countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    List<SummaryJob> findAllByBookIdAndStatusOrderByCreatedAtDesc(Long bookId, SummaryJobStatus status);

    Optional<SummaryJob> findByIdAndUserId(Long id, Long userId);
}
