package com.booktown.domain.summary.service;

import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.repository.SummaryDocumentRepository;
import com.booktown.domain.summary.service.SummaryJobTransactionService.SummaryJobContext;
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

/**
 * 요약 생성 비동기 처리기.
 *
 * <h2>트랜잭션 경계 설계</h2>
 * <p>이 클래스에는 {@code @Transactional} 이 <b>없다</b>. 의도적이다.
 *
 * <p>이전 구조는 {@code @Async + @Transactional} 이 process() 전체를 감싸고 있어서
 * 아래처럼 동작했다.
 * <pre>
 *   트랜잭션 시작 → DB 커넥션 획득 → OpenAI 호출(수 초~수십 초 대기) → DB 저장 → 트랜잭션 종료
 *                     └────────── 이 구간 내내 커넥션 점유 ──────────┘
 * </pre>
 * AI 워커 스레드 수가 커넥션 풀 크기에 가까워지면 일반 조회 API 가 커넥션을 얻지 못한다.
 *
 * <p>변경 후 구조:
 * <pre>
 *   [짧은 TX] PROCESSING 저장 + 필요한 데이터를 값으로 추출  → 커넥션 반납
 *   [TX 밖 ] Chroma 인덱싱 / OpenAI 호출 / MongoDB 저장     → DB 커넥션 점유 없음
 *   [짧은 TX] COMPLETED 또는 FAILED 전이                     → 커넥션 반납
 * </pre>
 *
 * <p>트랜잭션 메서드를 같은 클래스에 두면 self-invocation 이 되어 프록시를 거치지 않으므로,
 * DB 작업은 모두 별도 빈인 {@link SummaryJobTransactionService} 에 위임한다.
 */
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

    private final SummaryJobTransactionService transactionService;
    private final SummaryDocumentRepository summaryDocumentRepository;
    private final ObjectProvider<RagService> ragServiceProvider;
    private final ObjectProvider<ChatClient> chatClientProvider;

    @Async("summaryProcessingExecutor")
    public void process(Long jobId, boolean isRegeneration) {
        // ── 1단계: 짧은 트랜잭션 — PROCESSING 전이 + 필요한 데이터 값으로 추출 ──
        SummaryJobContext ctx;
        try {
            ctx = transactionService.startProcessing(jobId);
        } catch (Exception e) {
            // Job 자체를 못 읽은 상황이므로 상태를 바꿀 대상도 없다.
            log.error("SummaryJob {} could not start: {}", jobId, e.getMessage(), e);
            return;
        }

        // ── 2단계: 트랜잭션 밖 — 외부 I/O (DB 커넥션 점유 없음) ──
        try {
            String context = buildRagContextIfAvailable(ctx.bookId(), ctx.chapters());
            if (context.isBlank()) {
                context = buildContext(ctx.chapters());
            }
            if (context.isBlank()) {
                throw new IllegalStateException("Book content is empty.");
            }

            ChatClient chatClient = requireChatClient();
            BookMetadata metadata = translateBookMetadataIfNeeded(ctx, chatClient);

            String summaryContent = chatClient.prompt()
                    .user(SUMMARY_PROMPT.formatted(metadata.title(), metadata.author(), context))
                    .call()
                    .content();

            // MongoDB 저장 — RDBMS 커넥션과 무관하다.
            SummaryDocument doc = SummaryDocument.create(
                    ctx.bookId(), ctx.userId(), jobId, summaryContent, isRegeneration);
            SummaryDocument saved = summaryDocumentRepository.save(doc);

            // ── 3단계: 짧은 트랜잭션 — COMPLETED 전이 ──
            transactionService.markCompleted(jobId, saved.getId());
            log.info("SummaryJob {} completed: summaryId={}", jobId, saved.getId());
        } catch (Exception e) {
            log.error("SummaryJob {} failed: {}", jobId, e.getMessage(), e);
            markFailedQuietly(jobId, e);
        }
    }

    /**
     * FAILED 전이는 별도 트랜잭션이며, 여기서 또 예외가 나면 Job 이 PROCESSING 에
     * 영원히 머무르게 된다. 그 경우를 로그로 남겨 추적 가능하게 한다.
     */
    private void markFailedQuietly(Long jobId, Exception cause) {
        try {
            transactionService.markFailed(jobId, cause.getMessage(), isAiServiceError(cause));
        } catch (Exception e) {
            log.error("SummaryJob {} stuck in PROCESSING: failed to persist FAILED state: {}",
                    jobId, e.getMessage(), e);
        }
    }

    private String buildRagContextIfAvailable(Long bookId, List<ChapterContent> chapters) {
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

    private String buildContext(List<ChapterContent> chapters) {
        StringBuilder context = new StringBuilder();
        for (ChapterContent chapter : chapters) {
            if (!chapter.hasContent()) {
                continue;
            }

            String excerpt = excerpt(chapter.content(), MAX_CHAPTER_EXCERPT_CHARS);
            String block = "[%d. %s]%n%s%n%n".formatted(
                    chapter.chapterNumber(),
                    chapter.title(),
                    excerpt
            );
            if (context.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(block);
        }
        return context.toString().trim();
    }

    /**
     * AI 호출(트랜잭션 밖)로 메타데이터를 번역하고, DB 반영만 짧은 트랜잭션으로 위임한다.
     * 번역 실패는 요약 생성 실패로 취급하지 않는다(기존 동작 유지).
     */
    private BookMetadata translateBookMetadataIfNeeded(SummaryJobContext ctx, ChatClient chatClient) {
        BookMetadata original = new BookMetadata(ctx.title(), ctx.author(), ctx.description());
        if (hasKorean(ctx.title()) && hasKorean(ctx.description())) {
            return original;
        }

        try {
            String response = chatClient.prompt()
                    .user(METADATA_PROMPT.formatted(
                            fallback(ctx.title(), "제목 미상"),
                            fallback(ctx.author(), "저자 미상"),
                            fallback(ctx.description(), "소개 없음")
                    ))
                    .call()
                    .content();

            String title = extractLineValue(response, "TITLE").orElse(ctx.title());
            String author = extractLineValue(response, "AUTHOR").orElse(ctx.author());
            String description = extractLineValue(response, "INTRO").orElse(ctx.description());

            transactionService.updateBookMetadata(
                    ctx.bookId(), blankToNull(title), blankToNull(author), blankToNull(description));

            return new BookMetadata(
                    title == null || title.isBlank() ? ctx.title() : title,
                    author == null || author.isBlank() ? ctx.author() : author,
                    description
            );
        } catch (Exception e) {
            log.warn("SummaryJob metadata translation skipped for bookId={}: {}", ctx.bookId(), e.getMessage());
            return original;
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

    private record BookMetadata(String title, String author, String description) {}
}
