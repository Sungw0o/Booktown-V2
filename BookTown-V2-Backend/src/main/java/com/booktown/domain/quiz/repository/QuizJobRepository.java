package com.booktown.domain.quiz.repository;

import com.booktown.domain.quiz.entity.QuizJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizJobRepository extends JpaRepository<QuizJob, Long> {

    Optional<QuizJob> findByIdAndUserId(Long id, Long userId);
}
