package com.booktown.domain.quiz.dto;

import com.booktown.domain.quiz.entity.QuestionOption;

public record OptionResponse(
        Long optionId,
        int optionOrder,
        String content
) {
    public static OptionResponse from(QuestionOption option) {
        return new OptionResponse(option.getId(), option.getOptionOrder(), option.getContent());
    }
}
