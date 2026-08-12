package com.booktown.domain.quiz.service;

import com.booktown.domain.quiz.service.QuizJobTransactionService.ChapterExcerpt;
import com.booktown.domain.quiz.service.QuizJobTransactionService.GeneratedQuestion;
import com.booktown.domain.quiz.service.QuizJobTransactionService.QuizJobContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 퀴즈 생성 비동기 처리기.
 *
 * <h2>트랜잭션 경계 설계</h2>
 * <p>이 클래스에는 {@code @Transactional} 이 <b>없다</b>. 의도적이다.
 * AI 호출이 응답을 기다리는 동안 DB 커넥션을 점유하지 않도록,
 * DB 작업은 전부 {@link QuizJobTransactionService} 에 위임해 짧은 트랜잭션으로 쪼갠다.
 *
 * <pre>
 *   [짧은 TX] PROCESSING 전이 + 챕터 본문 추출   → 커넥션 반납
 *   [TX 밖 ] OpenAI 호출 + JSON 파싱             → DB 커넥션 점유 없음
 *   [짧은 TX] 퀴즈/문항/선택지 저장 + COMPLETED   → 커넥션 반납
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizProcessor {

    private static final String QUIZ_PROMPT = """
            당신은 한국 문학 교육 전문가입니다. 아래 도서 내용을 바탕으로 객관식 문제를 생성해주세요.

            도서 내용:
            %s

            요구사항:
            - 문항 수: %d개
            - 난이도: %s
            - 각 문제는 4지 선다형
            - 반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트 없이 JSON 배열만 출력하세요.

            JSON 형식:
            [
              {
                "content": "문제 내용",
                "explanation": "해설 (정답 이유 설명)",
                "correctOption": 1,
                "options": ["선택지1", "선택지2", "선택지3", "선택지4"]
              }
            ]
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final QuizJobTransactionService transactionService;
    private final ObjectProvider<ChatClient> chatClientProvider;

    @Async("quizProcessingExecutor")
    public void process(Long jobId) {
        // ── 1단계: 짧은 트랜잭션 ──
        QuizJobContext ctx;
        try {
            ctx = transactionService.startProcessing(jobId);
        } catch (Exception e) {
            log.error("QuizJob {} could not start: {}", jobId, e.getMessage(), e);
            return;
        }

        // ── 2단계: 트랜잭션 밖 — AI 호출 ──
        try {
            String context = buildContext(ctx.chapters(), ctx.questionCount());
            String prompt = String.format(QUIZ_PROMPT, context, ctx.questionCount(), ctx.difficulty().name());

            ChatClient chatClient = requireChatClient();
            String aiResponse = chatClient.prompt().user(prompt).call().content();
            String json = extractJson(aiResponse);
            List<GeneratedQuestion> questions = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

            // ── 3단계: 짧은 트랜잭션 — 저장 + COMPLETED ──
            Long quizId = transactionService.saveQuizAndComplete(ctx, questions);
            log.info("QuizJob {} completed: quizId={}", jobId, quizId);
        } catch (Exception e) {
            log.error("QuizJob {} failed: {}", jobId, e.getMessage(), e);
            boolean retryable = !(e instanceof IllegalArgumentException);
            markFailedQuietly(jobId, e.getMessage(), retryable);
        }
    }

    private void markFailedQuietly(Long jobId, String message, boolean retryable) {
        try {
            transactionService.markFailed(jobId, message, retryable);
        } catch (Exception e) {
            log.error("QuizJob {} stuck in PROCESSING: failed to persist FAILED state: {}",
                    jobId, e.getMessage(), e);
        }
    }

    private String buildContext(List<ChapterExcerpt> chapters, int questionCount) {
        int maxCharsPerChapter = Math.max(500, 3000 / Math.max(chapters.size(), 1));
        StringBuilder sb = new StringBuilder();
        for (ChapterExcerpt ch : chapters) {
            if (ch.hasContent()) {
                String excerpt = ch.content().length() > maxCharsPerChapter
                        ? ch.content().substring(0, maxCharsPerChapter)
                        : ch.content();
                sb.append("[").append(ch.title()).append("]\n").append(excerpt).append("\n\n");
            }
        }
        return sb.toString();
    }

    private ChatClient requireChatClient() {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new IllegalStateException("AI chat client is not configured.");
        }
        return chatClient;
    }

    private String extractJson(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start == -1 || end == -1 || start > end) {
            throw new IllegalArgumentException("AI 응답에서 JSON 배열을 찾을 수 없습니다.");
        }
        return response.substring(start, end + 1);
    }
}
