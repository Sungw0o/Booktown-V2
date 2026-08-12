package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.repository.SummaryDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryProcessor {

    private static final String METADATA_PROMPT = """
            아래 고전문학 도서 정보를 한국어 서비스 상세 화면에 맞게 번역하고 다듬어 주세요.

            규칙:
            - 작품명은 널리 쓰이는 한국어 번역명이 있으면 사용합니다.
            - 저자는 한국어 표기가 자연스러우면 한국어로 표기합니다.
            - 소개는 원문의 의미를 유지하되 2~4문장의 한국어 줄거리/소개로 재작성합니다.
            - 자동 생성 문구, 출처 안내, Project Gutenberg 안내는 넣지 않습니다.
            - 반드시 아래 3줄 형식만 반환합니다.

            TITLE: ...
            AUTHOR: ...
            INTRO: ...

            제목: %s
            저자: %s
            소개: %s
            """;
    private static final String SUMMARY_PROMPT = """
            당신은 고전문학 독서 플랫폼의 한국어 요약 에디터입니다.
            아래 도서 원문 발췌를 바탕으로 독자가 작품을 빠르게 이해할 수 있는 요약을 작성해주세요.

            요구사항:
            - 한국어 Markdown 형식
            - 700~1,000자
            - 핵심 줄거리, 주요 인물, 주제 의식을 구분
            - 원문에 없는 사실을 단정하지 말 것
            - 대화체가 아니라 차분한 해설문으로 작성

            [도서]
            제목: %s
            저자: %s

            [참고 원문]
            %s

            [요약]
            """;
    private static final int MAX_CONTEXT_CHARS = 18_000;
    private static final int MAX_CHAPTER_EXCERPT_CHARS = 2_400;
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");

    private final SummaryDocumentRepository summaryDocumentRepository;
    private final SummaryJobTransactionService transactionService;
    private final ObjectProvider<RagService> ragServiceProvider;
    private final ObjectProvider<ChatClient> chatClientProvider;

    @Async("summaryProcessingExecutor")
    public void process(Long jobId, boolean isRegeneration) {
        SummaryJobTransactionService.SummaryWork work = transactionService.start(jobId);

        try {
            String context = buildRagContextIfAvailable(work.bookId(), work.chapters());
            if (context.isBlank()) {
                context = buildContext(work.chapters());
            }
            if (context.isBlank()) {
                throw new IllegalStateException("Book content is empty.");
            }

            ChatClient chatClient = requireChatClient();
            BookMetadata metadata = translateBookMetadataIfNeeded(work, chatClient);
            String summaryContent = chatClient.prompt()
                    .user(SUMMARY_PROMPT.formatted(
                            metadata.title(),
                            metadata.author(),
                            context
                    ))
                    .call()
                    .content();

            SummaryDocument doc = SummaryDocument.create(
                    work.bookId(), work.userId(), jobId, summaryContent, isRegeneration
            );
            summaryDocumentRepository.save(doc);

            transactionService.complete(jobId, doc.getId());
            log.info("SummaryJob {} completed: summaryId={}", jobId, doc.getId());
        } catch (Exception e) {
            log.error("SummaryJob {} failed: {}", jobId, e.getMessage(), e);
            transactionService.fail(jobId, e.getMessage(), isAiServiceError(e));
        }
    }

    private String buildRagContextIfAvailable(Long bookId, List<Chapter> chapters) {
        RagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            return "";
        }

        try {
            ragService.indexChapters(bookId, chapters);
            String context = ragService.retrieveContext(bookId, "이 작품의 핵심 줄거리, 주요 인물, 주제 의식");
            if (!context.isBlank()) {
                log.info("SummaryJob uses Chroma RAG context for bookId={}", bookId);
            }
            return context;
        } catch (Exception e) {
            log.warn("Chroma RAG context unavailable for bookId={}: {}", bookId, e.getMessage());
            return "";
        }
    }

    private String buildContext(List<Chapter> chapters) {
        StringBuilder context = new StringBuilder();
        for (Chapter chapter : chapters) {
            if (chapter.getContent() == null || chapter.getContent().isBlank()) {
                continue;
            }

            String excerpt = excerpt(chapter.getContent(), MAX_CHAPTER_EXCERPT_CHARS);
            String block = "[%d. %s]%n%s%n%n".formatted(
                    chapter.getChapterNumber(),
                    chapter.getTitle(),
                    excerpt
            );
            if (context.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(block);
        }
        return context.toString().trim();
    }

    private BookMetadata translateBookMetadataIfNeeded(
            SummaryJobTransactionService.SummaryWork work,
            ChatClient chatClient
    ) {
        BookMetadata current = new BookMetadata(work.title(), work.author(), work.description());
        if (hasKorean(current.title()) && hasKorean(current.description())) {
            return current;
        }

        try {
            String response = chatClient.prompt()
                    .user(METADATA_PROMPT.formatted(
                            fallback(current.title(), "제목 미상"),
                            fallback(current.author(), "저자 미상"),
                            fallback(current.description(), "소개 없음")
                    ))
                    .call()
                    .content();

            BookMetadata translated = new BookMetadata(
                    blankToNull(extractLineValue(response, "TITLE").orElse(current.title())),
                    blankToNull(extractLineValue(response, "AUTHOR").orElse(current.author())),
                    blankToNull(extractLineValue(response, "INTRO").orElse(current.description()))
            );
            transactionService.updateBookMetadata(
                    work.bookId(), translated.title(), translated.author(), translated.description()
            );
            return translated;
        } catch (Exception e) {
            log.warn("SummaryJob metadata translation skipped for bookId={}: {}", work.bookId(), e.getMessage());
            return current;
        }
    }

    private boolean hasKorean(String value) {
        return value != null && HANGUL_PATTERN.matcher(value).find();
    }

    private Optional<String> extractLineValue(String response, String key) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
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

    private String excerpt(String content, int maxChars) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private boolean isAiServiceError(Exception e) {
        return e.getMessage() != null && (
                e.getMessage().contains("timeout") ||
                e.getMessage().contains("rate limit") ||
                e.getMessage().contains("service unavailable") ||
                e.getMessage().contains("not configured")
        );
    }

    private ChatClient requireChatClient() {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new IllegalStateException("AI chat client is not configured.");
        }
        return chatClient;
    }

    private record BookMetadata(String title, String author, String description) {
    }
}
