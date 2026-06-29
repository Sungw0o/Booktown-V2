package com.booktown.domain.quiz.repository;

import com.booktown.domain.quiz.entity.QuizSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    boolean existsByQuizIdAndUserId(Long quizId, Long userId);

    Optional<QuizSubmission> findByQuizIdAndUserId(Long quizId, Long userId);

    Page<QuizSubmission> findAllByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);
}
