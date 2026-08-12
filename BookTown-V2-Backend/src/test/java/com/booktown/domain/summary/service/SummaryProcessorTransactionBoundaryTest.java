package com.booktown.domain.summary.service;

import com.booktown.domain.summary.document.SummaryDocument;
import com.booktown.domain.summary.repository.SummaryDocumentRepository;
import com.booktown.domain.summary.service.SummaryJobTransactionService.SummaryJobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

/**
 * SummaryProcessor 의 트랜잭션 경계 재설계에 대한 회귀 방지 테스트.
 *
 * <p>검증 목적은 "요약 내용이 맞는가"가 아니라
 * <b>AI 호출이 트랜잭션 밖에서 일어나는 구조가 유지되는가</b>이다.
 */
class SummaryProcessorTransactionBoundaryTest {

    private SummaryJobTransactionService transactionService;
    private SummaryDocumentRepository summaryDocumentRepository;
    private ChatClient chatClient;
    private SummaryProcessor processor;

    private static final Long JOB_ID = 42L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transactionService = mock(SummaryJobTransactionService.class);
        summaryDocumentRepository = mock(SummaryDocumentRepository.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        ObjectProvider<RagService> ragProvider = mock(ObjectProvider.class);
        when(ragProvider.getIfAvailable()).thenReturn(null); // RAG 미사용 → 원문 fallback 경로

        ObjectProvider<ChatClient> chatProvider = mock(ObjectProvider.class);
        when(chatProvider.getIfAvailable()).thenReturn(chatClient);

        processor = new SummaryProcessor(
                transactionService, summaryDocumentRepository, ragProvider, chatProvider);
    }

    /** 제목·소개에 한글이 있으면 메타데이터 번역 AI 호출을 건너뛰므로 AI 호출이 1회로 고정된다. */
    private SummaryJobContext koreanContext() {
        return new SummaryJobContext(
                JOB_ID, 7L, 3L,
                "위대한 개츠비", "피츠제럴드", "한국어 소개 문장입니다.",
                List.of(new ChapterContent(1L, 1, "1장", "챕터 본문 내용이 충분히 들어 있다."))
        );
    }

    private void givenAiReturns(String content) {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(content);
    }

    // ------------------------------------------------------------------
    // 1. 구조 자체에 대한 검증 — 이 테스트가 깨지면 병목이 되돌아온 것이다
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SummaryProcessor 에는 @Transactional 이 없어야 한다 (AI 호출이 트랜잭션에 갇히지 않도록)")
    void processor_must_not_be_transactional() {
        assertThat(SummaryProcessor.class.getAnnotation(Transactional.class))
                .as("클래스 레벨 @Transactional 이 붙으면 AI 호출 내내 DB 커넥션을 점유하게 된다")
                .isNull();

        for (Method method : SummaryProcessor.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(Transactional.class))
                    .as("메서드 %s 에 @Transactional 이 붙으면 안 된다", method.getName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("트랜잭션 메서드는 별도 빈에 있어야 한다 (self-invocation 시 프록시 미적용 방지)")
    void transactional_methods_live_in_a_separate_bean() {
        assertThat(SummaryJobTransactionService.class)
                .as("같은 클래스 내부 호출은 Spring AOP 프록시를 거치지 않아 트랜잭션이 적용되지 않는다")
                .isNotEqualTo(SummaryProcessor.class);

        assertThat(SummaryJobTransactionService.class.getDeclaredMethods())
                .anyMatch(m -> m.getAnnotation(Transactional.class) != null);
    }

    // ------------------------------------------------------------------
    // 2. 실행 순서 — 커밋이 AI 호출 앞뒤로 나뉘는지
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PROCESSING 커밋 → AI 호출 → COMPLETED 커밋 순서로 실행된다")
    void short_transactions_wrap_the_ai_call() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(koreanContext());
        givenAiReturns("생성된 요약");
        SummaryDocument saved = mock(SummaryDocument.class);
        when(saved.getId()).thenReturn("summary-1");
        when(summaryDocumentRepository.save(any())).thenReturn(saved);

        processor.process(JOB_ID, false);

        InOrder order = inOrder(transactionService, summaryDocumentRepository);
        order.verify(transactionService).startProcessing(JOB_ID);       // 짧은 TX #1
        order.verify(summaryDocumentRepository).save(any());            // TX 밖 (Mongo)
        order.verify(transactionService).markCompleted(JOB_ID, "summary-1"); // 짧은 TX #2
        order.verifyNoMoreInteractions();
    }

    // ------------------------------------------------------------------
    // 3. 상태 정합성 — QUEUED / PROCESSING / COMPLETED / FAILED
    // ------------------------------------------------------------------

    @Test
    @DisplayName("정상 완료 시 COMPLETED 로 전이하고 FAILED 는 기록하지 않는다")
    void completes_successfully() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(koreanContext());
        givenAiReturns("생성된 요약");
        SummaryDocument saved = mock(SummaryDocument.class);
        when(saved.getId()).thenReturn("summary-1");
        when(summaryDocumentRepository.save(any())).thenReturn(saved);

        processor.process(JOB_ID, false);

        verify(transactionService).markCompleted(JOB_ID, "summary-1");
        verify(transactionService, never()).markFailed(anyLong(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("AI 호출이 실패하면 FAILED 로 전이하고 COMPLETED 로 가지 않는다")
    void marks_failed_when_ai_call_throws() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(koreanContext());
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("upstream timeout"));

        processor.process(JOB_ID, false);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), anyBoolean());
        verify(transactionService, never()).markCompleted(anyLong(), anyString());
        verify(summaryDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 호출은 성공했지만 결과 저장이 실패하면 FAILED 로 전이한다")
    void marks_failed_when_result_persistence_fails() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(koreanContext());
        givenAiReturns("생성된 요약");
        when(summaryDocumentRepository.save(any()))
                .thenThrow(new RuntimeException("mongo unavailable"));

        processor.process(JOB_ID, false);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), anyBoolean());
        verify(transactionService, never()).markCompleted(anyLong(), anyString());
    }

    @Test
    @DisplayName("AI 응답이 비어 본문 컨텍스트를 만들 수 없으면 FAILED 로 전이한다")
    void marks_failed_when_book_content_is_empty() {
        SummaryJobContext emptyChapters = new SummaryJobContext(
                JOB_ID, 7L, 3L, "위대한 개츠비", "피츠제럴드", "한국어 소개입니다.",
                List.of(new ChapterContent(1L, 1, "1장", "   "))
        );
        when(transactionService.startProcessing(JOB_ID)).thenReturn(emptyChapters);

        processor.process(JOB_ID, false);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), anyBoolean());
        verify(transactionService, never()).markCompleted(anyLong(), anyString());
    }

    @Test
    @DisplayName("시작 트랜잭션 자체가 실패하면 AI 를 호출하지 않고 종료한다")
    void aborts_without_calling_ai_when_start_fails() {
        when(transactionService.startProcessing(JOB_ID))
                .thenThrow(new IllegalStateException("SummaryJob not found: 42"));

        processor.process(JOB_ID, false);

        verify(summaryDocumentRepository, never()).save(any());
        verify(transactionService, never()).markCompleted(anyLong(), anyString());
        verify(transactionService, never()).markFailed(anyLong(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("FAILED 기록마저 실패해도 예외를 밖으로 던지지 않는다 (Executor 스레드 보호)")
    void swallows_exception_when_marking_failed_fails() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(koreanContext());
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("upstream timeout"));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(transactionService).markFailed(anyLong(), anyString(), anyBoolean());

        processor.process(JOB_ID, false); // 예외가 전파되면 테스트 실패

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), anyBoolean());
    }
}
