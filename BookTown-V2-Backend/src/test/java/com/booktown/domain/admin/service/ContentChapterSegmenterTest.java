package com.booktown.domain.admin.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ContentChapterSegmenterTest {

    private final ContentChapterSegmenter segmenter = new ContentChapterSegmenter();

    @Test
    void segmentsManySourceChaptersIntoExactlyTenReadingChapters() {
        String source = IntStream.rangeClosed(1, 61)
                .mapToObj(index -> "CHAPTER " + index + "\n\nMarker-" + index + " tells a complete part of the story with enough detail.")
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow();

        List<ContentChapterSegmenter.ChapterSegment> result = segmenter.segment(source);

        assertThat(result).hasSize(10);
        assertThat(result).extracting(ContentChapterSegmenter.ChapterSegment::title)
                .containsExactly("1부", "2부", "3부", "4부", "5부", "6부", "7부", "8부", "9부", "10부");
        assertThat(result).allSatisfy(chapter -> assertThat(chapter.content()).isNotBlank());

        String recombined = result.stream()
                .map(ContentChapterSegmenter.ChapterSegment::content)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow();
        IntStream.rangeClosed(1, 61)
                .forEach(index -> assertThat(recombined).contains("Marker-" + index));
        assertThat(result).allSatisfy(chapter -> assertThat(chapter.content())
                .doesNotMatch("(?s).*CHAPTER\\s+\\d+\\s*$"));
    }

    @Test
    void splitsOneLongParagraphAtSentenceOrWordBoundaries() {
        String source = IntStream.rangeClosed(1, 40)
                .mapToObj(index -> "Sentence " + index + " carries the narrative forward with a distinct event.")
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();

        List<ContentChapterSegmenter.ChapterSegment> result = segmenter.segment(source);

        assertThat(result).hasSize(10);
        assertThat(result).allSatisfy(chapter -> assertThat(chapter.content()).isNotBlank());
        assertThat(result.stream().mapToInt(chapter -> chapter.content().length()).max().orElseThrow())
                .isLessThan(result.stream().mapToInt(chapter -> chapter.content().length()).min().orElseThrow() * 2);
    }

    @Test
    void rejectsContentTooShortForTenNonEmptyChapters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> segmenter.segment("short"))
                .withMessageContaining("too short");
    }
}
