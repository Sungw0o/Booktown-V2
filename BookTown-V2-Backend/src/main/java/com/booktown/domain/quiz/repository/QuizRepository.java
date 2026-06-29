package com.booktown.domain.quiz.repository;

import com.booktown.domain.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByIdAndUserId(Long id, Long userId);
}
