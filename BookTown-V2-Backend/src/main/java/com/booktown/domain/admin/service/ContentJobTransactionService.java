package com.booktown.domain.admin.service;

import com.booktown.domain.admin.entity.ContentJob;
import com.booktown.domain.admin.repository.ContentJobRepository;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.book.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * ContentProcessor 의 DB 작업만 담당하는 트랜잭션 경계 전용 서비스.
 *
 * <p>기존 구조에서 {@code processFromGutendex} 는 {@code @Transactional} 안에서
 * Gutendex 원문을 HTTP 로 내려받았다(다운로드 타임아웃 10초). 즉 최악의 경우
 * 10초 동안 DB 커넥션을 붙잡은 채 아무 DB 작업도 하지 않는 상태였다.
 * AI 번역 호출까지 포함하면 점유 시간은 더 길어진다.
 */
@Service
@RequiredArgsConstructor
public class ContentJobTransactionService {

    private final ContentJobRepository contentJobRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;

    /** 1단계 (짧은 TX): PROCESSING 전이 + 대상 도서 식별 정보 추출. */
    @Transactional
    public ContentJobContext startProcessing(Long jobId) {
        ContentJob job = contentJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("ContentJob not found: " + jobId));
        job.markProcessing();

        Book book = job.getBook();
        return new ContentJobContext(jobId, book.getId(), book.getTitle(), book.getAuthor());
    }

    /**
     * 2단계 (짧은 TX): 파싱이 끝난 챕터/장면을 저장하고 Job 을 COMPLETED 로 전이한다.
     *
     * <p>도서 메타데이터 갱신, 챕터·장면 저장, has_content 플래그, Job 상태 전이를
     * <b>하나의 트랜잭션</b>으로 묶는다. 챕터만 저장되고 has_content 가 false 로 남거나,
     * 반대로 본문 없이 COMPLETED 가 되는 중간 상태를 만들지 않기 위해서다.
     *
     * @return 저장된 챕터 수
     */
    @Transactional
    public int saveContentAndComplete(Long jobId, Long bookId, ParsedContent parsed) {
        ContentJob job = contentJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("ContentJob not found: " + jobId));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalStateException("Book not found: " + bookId));

        book.updateCatalogMetadata(parsed.title(), parsed.author(), parsed.description());

        List<Chapter> chapters = new ArrayList<>(parsed.chapters().size());
        for (int i = 0; i < parsed.chapters().size(); i++) {
            ParsedChapter pc = parsed.chapters().get(i);
            chapters.add(Chapter.create(book, i + 1, pc.title(), pc.content()));
        }
        chapterRepository.saveAll(chapters);

        List<Scene> scenes = new ArrayList<>(chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            scenes.add(Scene.create(book, chapter, chapter.getTitle(),
                    parsed.chapters().get(i).excerpt(), i + 1));
        }
        sceneRepository.saveAll(scenes);

        book.markContentUploaded();
        job.markCompleted(chapters.size());
        return chapters.size();
    }

    /** 실패 처리 (짧은 TX, REQUIRES_NEW). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage, boolean retryable) {
        contentJobRepository.findById(jobId)
                .ifPresent(job -> job.markFailed(truncate(errorMessage), retryable));
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record ContentJobContext(Long jobId, Long bookId, String bookTitle, String bookAuthor) {}

    /** 파싱 결과 — 트랜잭션 밖에서 만들어져 저장 단계로 전달된다. */
    public record ParsedContent(String title, String author, String description, List<ParsedChapter> chapters) {}

    public record ParsedChapter(String title, String content, String excerpt) {}
}
