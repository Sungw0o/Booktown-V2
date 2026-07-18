package com.booktown.domain.illustration.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.illustration.entity.IllustrationStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookVisualPromptFactoryTest {

    private final BookVisualPromptFactory promptFactory = new BookVisualPromptFactory();

    @Test
    void coverAndSceneShareTheSameWebtoonStyleBibleAndSeriesIdentity() {
        Book book = Book.create(
                "오만과 편견",
                "제인 오스틴",
                "엘리자베스와 다아시가 오해와 편견을 넘어 서로를 이해해 가는 이야기",
                null,
                Genre.NOVEL,
                Country.WESTERN
        );
        Chapter chapter = Chapter.create(book, 1, "1부", "무도회에서 두 사람이 처음 만난다.");
        Scene scene = Scene.create(book, chapter, "첫 무도회", "엘리자베스와 다아시가 붐비는 무도회장에서 마주친다.", 1);

        String coverPrompt = promptFactory.createCoverPrompt(book);
        String scenePrompt = promptFactory.createScenePrompt(scene, IllustrationStyle.WATERCOLOR, "warm candlelight");

        assertThat(coverPrompt).contains(BookVisualPromptFactory.STYLE_BIBLE.trim());
        assertThat(scenePrompt).contains(BookVisualPromptFactory.STYLE_BIBLE.trim());
        assertThat(coverPrompt).contains("Series identity: 오만과 편견 by 제인 오스틴");
        assertThat(scenePrompt).contains("Series identity: 오만과 편견 by 제인 오스틴");
        assertThat(coverPrompt).contains(
                "Korean webtoon cover key art",
                "1024x1536 portrait canvas",
                "make the image read clearly at small book-card thumbnail size",
                "no readable text",
                "not generic fantasy concept art"
        );
        assertThat(scenePrompt).contains("vertical full-bleed story panel", "Requested accent: watercolor");
    }

    @Test
    void scenePromptLimitsUserProvidedHint() {
        Book book = Book.create("Alice", "Lewis Carroll", null, null, Genre.NOVEL, Country.WESTERN);
        Chapter chapter = Chapter.create(book, 1, "1부", "content");
        Scene scene = Scene.create(book, chapter, "Rabbit hole", "Alice follows the rabbit.", 1);

        String prompt = promptFactory.createScenePrompt(scene, IllustrationStyle.INK, "x".repeat(500));

        assertThat(prompt).contains("Requested accent: ink");
        assertThat(prompt).doesNotContain("x".repeat(301));
    }
}
