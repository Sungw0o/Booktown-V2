package com.booktown.domain.illustration.dto;

import com.booktown.domain.book.entity.Scene;

public record SceneResponse(
        Long sceneId,
        Long bookId,
        Long chapterId,
        String title,
        String excerpt
) {
    public static SceneResponse from(Scene scene) {
        return new SceneResponse(
                scene.getId(),
                scene.getBook().getId(),
                scene.getChapter().getId(),
                scene.getTitle(),
                scene.getExcerpt()
        );
    }
}
