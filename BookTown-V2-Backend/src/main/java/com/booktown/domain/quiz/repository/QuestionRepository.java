package com.booktown.domain.quiz.repository;

import com.booktown.domain.quiz.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByQuizIdOrderByQuestionOrderAsc(Long quizId);
}
