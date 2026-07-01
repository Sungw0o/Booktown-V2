package com.booktown.domain.admin.service;

import com.booktown.domain.admin.entity.ContentJob;
import com.booktown.domain.admin.gutendex.GutendexClient;
import com.booktown.domain.admin.repository.ContentJobRepository;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.book.repository.SceneRepository;
import com.booktown.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentProcessor {

    private static final int MAX_CHUNK_SIZE = 5000;
    private static final int SCENE_EXCERPT_LENGTH = 300;
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?m)^(제\\s*\\d+\\s*장[^\n]*|Chapter\\s+\\d+[^\n]*|CHAPTER\\s+[IVX\\d]+[^\n]*|\\d+\\.\\s+[^\n]+)$"
    );

    private final ContentJobRepository contentJobRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final GutendexClient gutendexClient;

    @Async("contentProcessingExecutor")
    @Transactional
    public void process(Long jobId, byte[] rawContent) {
        ContentJob job = contentJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        contentJobRepository.save(job);
        processContent(job, rawContent);
    }

    @Async("contentProcessingExecutor")
    @Transactional
    public void processFromGutendex(Long jobId, String textUrl) {
        ContentJob job = contentJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        contentJobRepository.save(job);

        try {
            byte[] rawContent = gutendexClient.downloadText(textUrl);
            processContent(job, rawContent);
        } catch (CustomException e) {
            log.warn("ContentJob {} failed while downloading Gutendex text: {}", jobId, e.getMessage());
            job.markFailed(e.getMessage(), true);
            contentJobRepository.save(job);
        } catch (Exception e) {
            log.error("ContentJob {} failed while importing Gutendex text: {}", jobId, e.getMessage(), e);
            job.markFailed(e.getMessage(), true);
            contentJobRepository.save(job);
        }
    }

    private void processContent(ContentJob job, byte[] rawContent) {
        try {
            String text = stripProjectGutenbergBoilerplate(new String(rawContent, StandardCharsets.UTF_8));
            List<ChapterSegment> segments = parseChapters(text);

            Book book = job.getBook();
            List<Chapter> chapters = new ArrayList<>(segments.size());
            for (int i = 0; i < segments.size(); i++) {
                chapters.add(Chapter.create(book, i + 1, segments.get(i).title(), segments.get(i).content()));
            }
            chapterRepository.saveAll(chapters);

            List<Scene> scenes = new ArrayList<>(chapters.size());
            for (int i = 0; i < chapters.size(); i++) {
                Chapter chapter = chapters.get(i);
                String excerpt = buildExcerpt(chapter.getContent());
                scenes.add(Scene.create(book, chapter, chapter.getTitle(), excerpt, i + 1));
            }
            sceneRepository.saveAll(scenes);

            book.markContentUploaded();
            bookRepository.save(book);

            job.markCompleted(chapters.size());
            contentJobRepository.save(job);
            log.info("ContentJob {} completed: {} chapters, {} scenes", job.getId(), chapters.size(), scenes.size());
        } catch (Exception e) {
            log.error("ContentJob {} failed: {}", job.getId(), e.getMessage(), e);
            job.markFailed(e.getMessage(), true);
            contentJobRepository.save(job);
        }
    }

    private String buildExcerpt(String content) {
        if (content == null || content.isBlank()) return "";
        String trimmed = content.trim();
        return trimmed.length() <= SCENE_EXCERPT_LENGTH
                ? trimmed
                : trimmed.substring(0, SCENE_EXCERPT_LENGTH) + "...";
    }

    private List<ChapterSegment> parseChapters(String text) {
        Matcher matcher = CHAPTER_PATTERN.matcher(text);
        List<int[]> headerPositions = new ArrayList<>();

        while (matcher.find()) {
            headerPositions.add(new int[]{matcher.start(), matcher.end()});
        }

        if (!headerPositions.isEmpty()) {
            return splitByHeaders(text, headerPositions);
        }
        return splitBySize(text);
    }

    private String stripProjectGutenbergBoilerplate(String text) {
        String normalized = text.replace("\r\n", "\n");
        int start = findAfter(normalized,
                "*** START OF THE PROJECT GUTENBERG EBOOK",
                "*** START OF THIS PROJECT GUTENBERG EBOOK",
                "*** START OF THE PROJECT GUTENBERG");
        int end = findBefore(normalized,
                "*** END OF THE PROJECT GUTENBERG EBOOK",
                "*** END OF THIS PROJECT GUTENBERG EBOOK",
                "*** END OF THE PROJECT GUTENBERG");
        String stripped = normalized.substring(start, end).trim();
        return stripped.isBlank() ? normalized.trim() : stripped;
    }

    private int findAfter(String text, String... markers) {
        String upper = text.toUpperCase();
        for (String marker : markers) {
            int index = upper.indexOf(marker);
            if (index >= 0) {
                int lineEnd = text.indexOf('\n', index);
                return lineEnd >= 0 ? lineEnd + 1 : index + marker.length();
            }
        }
        return 0;
    }

    private int findBefore(String text, String... markers) {
        String upper = text.toUpperCase();
        int end = text.length();
        for (String marker : markers) {
            int index = upper.indexOf(marker);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return end;
    }

    private List<ChapterSegment> splitByHeaders(String text, List<int[]> headers) {
        List<ChapterSegment> segments = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int titleStart = headers.get(i)[0];
            int titleEnd = headers.get(i)[1];
            int contentEnd = (i + 1 < headers.size()) ? headers.get(i + 1)[0] : text.length();
            String title = text.substring(titleStart, titleEnd).trim();
            String content = text.substring(titleEnd, contentEnd).trim();
            segments.add(new ChapterSegment(title, content));
        }
        return segments;
    }

    private List<ChapterSegment> splitBySize(String text) {
        List<ChapterSegment> segments = new ArrayList<>();
        int total = text.length();
        int chapterNum = 1;
        for (int start = 0; start < total; start += MAX_CHUNK_SIZE) {
            int end = Math.min(start + MAX_CHUNK_SIZE, total);
            segments.add(new ChapterSegment("Chapter " + chapterNum++, text.substring(start, end).trim()));
        }
        return segments.isEmpty() ? List.of(new ChapterSegment("Chapter 1", text.trim())) : segments;
    }

    private record ChapterSegment(String title, String content) {}
}
