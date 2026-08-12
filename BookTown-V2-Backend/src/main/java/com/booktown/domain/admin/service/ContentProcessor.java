package com.booktown.domain.admin.service;

import com.booktown.domain.admin.gutendex.GutendexClient;
import com.booktown.domain.admin.service.ContentJobTransactionService.ContentJobContext;
import com.booktown.domain.admin.service.ContentJobTransactionService.ParsedChapter;
import com.booktown.domain.admin.service.ContentJobTransactionService.ParsedContent;
import com.booktown.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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

    private final ContentJobTransactionService transactionService;
    private final GutendexClient gutendexClient;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ContentChapterSegmenter contentChapterSegmenter;

    @Async("contentProcessingExecutor")
    public void process(Long jobId, byte[] rawContent) {
        ContentJobContext ctx = startProcessingQuietly(jobId);
        if (ctx == null) {
            return;
        }
        processContent(ctx, rawContent);
    }

    @Async("contentProcessingExecutor")
    public void processFromGutendex(Long jobId, String textUrl) {
        ContentJobContext ctx = startProcessingQuietly(jobId);
        if (ctx == null) {
            return;
        }

        // 다운로드는 트랜잭션 밖에서 수행한다.
        // 이전 구조에서는 이 호출(타임아웃 10초)이 트랜잭션 안에 있어
        // 네트워크 대기 내내 DB 커넥션을 점유했다.
        byte[] rawContent;
        try {
            rawContent = gutendexClient.downloadText(textUrl);
        } catch (CustomException e) {
            log.warn("ContentJob {} failed while downloading Gutendex text: {}", jobId, e.getMessage());
            markFailedQuietly(jobId, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("ContentJob {} failed while importing Gutendex text: {}", jobId, e.getMessage(), e);
            markFailedQuietly(jobId, e.getMessage());
            return;
        }

        processContent(ctx, rawContent);
    }

    private ContentJobContext startProcessingQuietly(Long jobId) {
        try {
            return transactionService.startProcessing(jobId);
        } catch (Exception e) {
            log.error("ContentJob {} could not start: {}", jobId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 파싱과 AI 번역은 트랜잭션 밖에서, DB 저장만 짧은 트랜잭션으로 수행한다.
     */
    private void processContent(ContentJobContext ctx, byte[] rawContent) {
        try {
            // ── 트랜잭션 밖: CPU 파싱 + AI 번역 호출 ──
            String rawText = new String(rawContent, StandardCharsets.UTF_8);
            String text = stripProjectGutenbergBoilerplate(rawText);
            List<ContentChapterSegmenter.ChapterSegment> segments = contentChapterSegmenter.createSegments(text);

            CatalogMetadata catalogMetadata = translateCatalogMetadata(new CatalogMetadata(
                    normalizeTitle(extractMetadata(rawText, TITLE_PATTERN).orElse(ctx.bookTitle())),
                    normalizeAuthor(extractMetadata(rawText, AUTHOR_PATTERN).orElse(ctx.bookAuthor())),
                    buildIntroDescription(text)
            ));

            List<ParsedChapter> parsedChapters = new ArrayList<>(segments.size());
            for (ContentChapterSegmenter.ChapterSegment segment : segments) {
                parsedChapters.add(new ParsedChapter(
                        segment.title(),
                        segment.content(),
                        buildExcerpt(segment.content())
                ));
            }

            // ── 짧은 트랜잭션: 저장 + COMPLETED ──
            int chapterCount = transactionService.saveContentAndComplete(
                    ctx.jobId(),
                    ctx.bookId(),
                    new ParsedContent(
                            catalogMetadata.title(),
                            catalogMetadata.author(),
                            catalogMetadata.description(),
                            parsedChapters
                    )
            );
            log.info("ContentJob {} completed: {} chapters, {} scenes",
                    ctx.jobId(), chapterCount, chapterCount);
        } catch (Exception e) {
            log.error("ContentJob {} failed: {}", ctx.jobId(), e.getMessage(), e);
            markFailedQuietly(ctx.jobId(), e.getMessage());
        }
    }

    private void markFailedQuietly(Long jobId, String message) {
        try {
            transactionService.markFailed(jobId, message, true);
        } catch (Exception e) {
            log.error("ContentJob {} stuck in PROCESSING: failed to persist FAILED state: {}",
                    jobId, e.getMessage(), e);
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
