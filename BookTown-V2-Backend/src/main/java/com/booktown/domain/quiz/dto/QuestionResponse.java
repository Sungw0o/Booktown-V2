package com.booktown.domain.quiz.dto;

import com.booktown.domain.quiz.entity.Question;
import com.booktown.domain.quiz.entity.QuestionOption;

import java.util.List;

public record QuestionResponse(
        Long questionId,
        int questionOrder,
        String content,
        List<OptionResponse> options
) {
    public static QuestionResponse from(Question question, List<QuestionOption> options) {
        return new QuestionResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getContent(),
                options.stream().map(OptionResponse::from).toList()
        );
    }
}
