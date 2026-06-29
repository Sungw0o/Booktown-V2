package com.booktown.domain.quiz.repository;

import com.booktown.domain.quiz.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findAllByQuestionIdOrderByOptionOrderAsc(Long questionId);

    List<QuestionOption> findAllByQuestionIdIn(List<Long> questionIds);
}
