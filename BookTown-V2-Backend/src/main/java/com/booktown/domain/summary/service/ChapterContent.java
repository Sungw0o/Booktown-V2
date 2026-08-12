package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Chapter;

/**
 * Chapter 엔티티를 트랜잭션 밖으로 안전하게 전달하기 위한 불변 값 객체.
 *
 * <p>요약 생성은 AI 호출 때문에 수 초 이상 걸리므로, 그 구간을 트랜잭션 밖에 두었다.
 * 이때 엔티티를 그대로 들고 나가면 영속성 컨텍스트가 닫힌 뒤 LAZY 프록시 접근에서
 * LazyInitializationException 이 발생한다. 따라서 필요한 값만 복사해 전달한다.
 */
public record ChapterContent(Long chapterId, int chapterNumber, String title, String content) {

    public static ChapterContent from(Chapter chapter) {
        return new ChapterContent(
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                chapter.getContent()
        );
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
