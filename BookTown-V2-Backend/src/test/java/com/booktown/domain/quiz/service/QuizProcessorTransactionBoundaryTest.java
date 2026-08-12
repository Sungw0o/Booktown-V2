package com.booktown.domain.quiz.service;

import com.booktown.domain.quiz.entity.QuizDifficulty;
import com.booktown.domain.quiz.service.QuizJobTransactionService.ChapterExcerpt;
import com.booktown.domain.quiz.service.QuizJobTransactionService.GeneratedQuestion;
import com.booktown.domain.quiz.service.QuizJobTransactionService.QuizJobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** QuizProcessor 트랜잭션 경계 재설계에 대한 회귀 방지 테스트. */
class QuizProcessorTransactionBoundaryTest {

    private static final Long JOB_ID = 11L;

    private QuizJobTransactionService transactionService;
    private ChatClient chatClient;
    private QuizProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transactionService = mock(QuizJobTransactionService.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        ObjectProvider<ChatClient> chatProvider = mock(ObjectProvider.class);
        when(chatProvider.getIfAvailable()).thenReturn(chatClient);

        processor = new QuizProcessor(transactionService, chatProvider);
    }

    private QuizJobContext context() {
        return new QuizJobContext(
                JOB_ID, 5L, 3L, 2, QuizDifficulty.EASY,
                List.of(new ChapterExcerpt("1장", "챕터 본문"))
        );
    }

    private static final String VALID_AI_JSON = """
            [
              {"content":"문제1","explanation":"해설1","correctOption":1,
               "options":["a","b","c","d"]},
              {"content":"문제2","explanation":"해설2","correctOption":2,
               "options":["a","b","c","d"]}
            ]
            """;

    @Test
    @DisplayName("QuizProcessor 에는 @Transactional 이 없어야 한다")
    void processor_must_not_be_transactional() {
        assertThat(QuizProcessor.class.getAnnotation(Transactional.class)).isNull();
        for (Method method : QuizProcessor.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(Transactional.class))
                    .as("메서드 %s", method.getName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("PROCESSING 커밋 → AI 호출 → 저장+COMPLETED 커밋 순서로 실행된다")
    void short_transactions_wrap_the_ai_call() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(VALID_AI_JSON);
        when(transactionService.saveQuizAndComplete(any(), any())).thenReturn(99L);

        processor.process(JOB_ID);

        InOrder order = inOrder(transactionService);
        order.verify(transactionService).startProcessing(JOB_ID);
        order.verify(transactionService).saveQuizAndComplete(any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("AI 응답을 파싱해 문항 저장까지 한 트랜잭션으로 넘긴다 (부분 저장 방지)")
    void parsed_questions_are_handed_to_a_single_transaction() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(VALID_AI_JSON);
        when(transactionService.saveQuizAndComplete(any(), any())).thenReturn(99L);

        processor.process(JOB_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeneratedQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionService).saveQuizAndComplete(any(), captor.capture());

        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).content()).isEqualTo("문제1");
        assertThat(captor.getValue().get(0).options()).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("AI 호출 실패 시 FAILED 로 전이하고 저장을 시도하지 않는다")
    void marks_failed_when_ai_call_throws() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("upstream timeout"));

        processor.process(JOB_ID);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), eq(true));
        verify(transactionService, never()).saveQuizAndComplete(any(), any());
    }

    @Test
    @DisplayName("AI 응답이 JSON 배열이 아니면 재시도 불가(FAILED, retryable=false)로 기록한다")
    void marks_non_retryable_when_ai_response_is_not_json() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("죄송합니다. 생성할 수 없습니다.");

        processor.process(JOB_ID);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), eq(false));
        verify(transactionService, never()).saveQuizAndComplete(any(), any());
    }

    @Test
    @DisplayName("저장 트랜잭션이 실패하면 FAILED 로 전이한다")
    void marks_failed_when_save_transaction_fails() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(VALID_AI_JSON);
        when(transactionService.saveQuizAndComplete(any(), any()))
                .thenThrow(new RuntimeException("db constraint violation"));

        processor.process(JOB_ID);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("시작 트랜잭션 실패 시 AI 를 호출하지 않고 종료한다")
    void aborts_without_calling_ai_when_start_fails() {
        when(transactionService.startProcessing(JOB_ID))
                .thenThrow(new IllegalStateException("QuizJob not found: 11"));

        processor.process(JOB_ID);

        verify(transactionService, never()).saveQuizAndComplete(any(), any());
        verify(transactionService, never()).markFailed(anyLong(), anyString(), anyBoolean());
    }
}
