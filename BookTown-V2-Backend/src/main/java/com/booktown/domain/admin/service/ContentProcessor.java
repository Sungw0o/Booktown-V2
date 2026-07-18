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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentProcessor {

    private static final int SCENE_EXCERPT_LENGTH = 300;
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?m)^(제\\s*\\d+\\s*장[^\n]*|Chapter\\s+\\d+[^\n]*|CHAPTER\\s+[IVX\\d]+[^\n]*|Letter\\s+\\d+[^\n]*|LETTER\\s+[IVX\\d]+[^\n]*|\\d+\\.\\s+[^\n]+)$"
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?im)^Title:\\s*(.+)$");
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("(?im)^Author:\\s*(.+)$");

    private final ContentJobRepository contentJobRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final GutendexClient gutendexClient;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ContentChapterSegmenter contentChapterSegmenter;

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
            String rawText = new String(rawContent, StandardCharsets.UTF_8);
            String text = stripProjectGutenbergBoilerplate(rawText);
            List<ContentChapterSegmenter.ChapterSegment> segments = contentChapterSegmenter.createSegments(text);

            Book book = job.getBook();
            CatalogMetadata catalogMetadata = translateCatalogMetadata(new CatalogMetadata(
                    normalizeTitle(extractMetadata(rawText, TITLE_PATTERN).orElse(book.getTitle())),
                    normalizeAuthor(extractMetadata(rawText, AUTHOR_PATTERN).orElse(book.getAuthor())),
                    buildIntroDescription(text)
            ));
            book.updateCatalogMetadata(catalogMetadata.title(), catalogMetadata.author(), catalogMetadata.description());

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
        return removeFrontAndBackMatter(stripped.isBlank() ? normalized.trim() : stripped);
    }

    private String removeFrontAndBackMatter(String text) {
        String[] lines = text.split("\n");
        int start = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (isContentStartLine(line)) {
                start = i;
                break;
            }
            if (i > 240) {
                start = 0;
                break;
            }
        }

        int end = lines.length;
        for (int i = start; i < lines.length; i++) {
            String upper = lines[i].trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("END OF THE PROJECT GUTENBERG")
                    || upper.startsWith("*** END")
                    || upper.startsWith("TRANSCRIBER'S NOTE")
                    || upper.startsWith("TRANSCRIBER NOTES")) {
                end = i;
                break;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            String line = lines[i].stripTrailing();
            if (isNoiseLine(line.trim())) {
                continue;
            }
            result.append(line).append('\n');
        }
        return result.toString().trim();
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

    private boolean isContentStartLine(String line) {
        if (line == null) return false;
        return CHAPTER_PATTERN.matcher(line).matches()
                || line.matches("(?i)^Preface\\b.*")
                || line.matches("(?i)^Prologue\\b.*")
                || line.matches("(?i)^Book\\s+[IVX\\d]+.*")
                || line.matches("(?i)^Part\\s+[IVX\\d]+.*");
    }

    private boolean isNoiseLine(String line) {
        if (line == null || line.isBlank()) return false;
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("PRODUCED BY ")
                || upper.startsWith("UPDATED EDITIONS")
                || upper.startsWith("CHARACTER SET ENCODING")
                || upper.startsWith("START OF THE PROJECT GUTENBERG")
                || upper.equals("CONTENTS")
                || upper.equals("TABLE OF CONTENTS");
    }

    private Optional<String> extractMetadata(String rawText, Pattern pattern) {
        Matcher matcher = pattern.matcher(rawText);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1).trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) return title;
        String cleaned = title.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("(?i);\\s*or,.*$", "");
        cleaned = cleaned.replaceAll("(?i)\\s+or,\\s+the\\s+.*$", "");
        cleaned = cleaned.replaceAll("^\"|\"$", "");
        return toTitleCase(cleaned);
    }

    private String normalizeAuthor(String author) {
        if (author == null || author.isBlank()) return author;
        String cleaned = author.replaceAll("\\s+", " ").trim();
        if (cleaned.contains(",")) {
            String[] parts = cleaned.split(",", 2);
            cleaned = (parts[1].trim() + " " + parts[0].trim()).trim();
        }
        return toTitleCase(cleaned);
    }

    private String buildIntroDescription(String text) {
        String[] paragraphs = text.trim().split("\\n\\s*\\n");
        StringBuilder intro = new StringBuilder();
        for (String paragraph : paragraphs) {
            String cleaned = paragraph.replaceAll("\\s+", " ").trim();
            if (cleaned.isBlank() || CHAPTER_PATTERN.matcher(cleaned).matches() || cleaned.length() < 80) {
                continue;
            }
            if (!intro.isEmpty()) {
                intro.append(" ");
            }
            intro.append(cleaned);
            if (intro.length() >= 420) {
                break;
            }
        }
        if (intro.isEmpty()) {
            return null;
        }
        String value = intro.toString();
        if (value.length() > 700) {
            value = value.substring(0, 700).stripTrailing();
        }
        return value + (value.endsWith(".") || value.endsWith("!") || value.endsWith("?") ? "" : "...");
    }

    private CatalogMetadata translateCatalogMetadata(CatalogMetadata metadata) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return metadata;
        }

        try {
            String response = chatClient.prompt()
                    .user("""
                            아래 고전문학 메타데이터를 한국어 서비스 카탈로그용으로 번역하고 다듬어 주세요.

                            규칙:
                            - 작품명은 널리 쓰이는 한국어 번역명이 있으면 사용합니다.
                            - 저자는 한국어 표기가 자연스러우면 한국어로 표기합니다.
                            - 소개는 원문 첫 부분의 의미를 바탕으로 2~4문장의 한국어 줄거리/소개로 재작성합니다.
                            - 출판 홍보 문구, Project Gutenberg 안내, 자동 생성 문구는 넣지 않습니다.
                            - 반드시 아래 3줄 형식만 반환합니다.

                            TITLE: ...
                            AUTHOR: ...
                            INTRO: ...

                            원제: %s
                            저자: %s
                            원문 기반 소개: %s
                            """.formatted(
                            fallback(metadata.title(), "제목 미상"),
                            fallback(metadata.author(), "저자 미상"),
                            fallback(metadata.description(), "소개 없음")
                    ))
                    .call()
                    .content();
            return parseTranslatedMetadata(response, metadata);
        } catch (Exception e) {
            log.warn("Catalog metadata translation skipped: {}", e.getMessage());
            return metadata;
        }
    }

    private CatalogMetadata parseTranslatedMetadata(String response, CatalogMetadata fallback) {
        if (response == null || response.isBlank()) {
            return fallback;
        }

        String title = extractLineValue(response, "TITLE").orElse(fallback.title());
        String author = extractLineValue(response, "AUTHOR").orElse(fallback.author());
        String intro = extractLineValue(response, "INTRO").orElse(fallback.description());
        return new CatalogMetadata(blankToNull(title), blankToNull(author), blankToNull(intro));
    }

    private Optional<String> extractLineValue(String response, String key) {
        Pattern pattern = Pattern.compile("(?im)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.+)$");
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1).replaceAll("\\s+", " ").trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toTitleCase(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    private record CatalogMetadata(String title, String author, String description) {}
}
