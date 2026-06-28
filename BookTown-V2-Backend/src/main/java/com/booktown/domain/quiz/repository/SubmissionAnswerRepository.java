package com.booktown.domain.quiz.repository;

import com.booktown.domain.quiz.entity.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {

    List<SubmissionAnswer> findAllBySubmissionId(Long submissionId);
}
