package com.booktown.domain.admin.service;

import com.booktown.domain.admin.gutendex.GutendexClient;
import com.booktown.domain.admin.service.ContentJobTransactionService.ContentJobContext;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContentProcessor 트랜잭션 경계 재설계에 대한 회귀 방지 테스트.
 *
 * <p>이 처리기의 핵심 개선점은 Gutendex 원문 다운로드(타임아웃 10초)가
 * 트랜잭션 밖으로 나왔다는 것이다.
 */
class ContentProcessorTransactionBoundaryTest {

    private static final Long JOB_ID = 7L;

    private ContentJobTransactionService transactionService;
    private GutendexClient gutendexClient;
    private ContentProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transactionService = mock(ContentJobTransactionService.class);
        gutendexClient = mock(GutendexClient.class);

        ObjectProvider<ChatClient> chatProvider = mock(ObjectProvider.class);
        when(chatProvider.getIfAvailable()).thenReturn(null); // AI 번역 미사용 경로

        processor = new ContentProcessor(
                transactionService, gutendexClient, chatProvider, new ContentChapterSegmenter());
    }

    private ContentJobContext context() {
        return new ContentJobContext(JOB_ID, 3L, "Original Title", "Original Author");
    }

    /**
     * ContentChapterSegmenter 는 본문이 최소 1,000자 이상이어야 분할을 수행하므로
     * (MIN_SOURCE_TEXT_LENGTH) 실제 도서에 준하는 길이의 본문을 만들어 준다.
     */
    private byte[] sampleBook() {
        StringBuilder body = new StringBuilder();
        for (int chapter = 1; chapter <= 12; chapter++) {
            body.append("CHAPTER ").append(chapter).append("\n\n");
            for (int paragraph = 1; paragraph <= 3; paragraph++) {
                body.append("This is paragraph ").append(paragraph)
                        .append(" of chapter ").append(chapter)
                        .append(". It contains enough narrative prose for the segmenter to treat it ")
                        .append("as genuine book content, with several sentences of reasonable length ")
                        .append("so that paragraph and sentence splitting both behave realistically.\n\n");
            }
        }

        return ("""
                Title: Sample Book
                Author: Someone Great

                *** START OF THE PROJECT GUTENBERG EBOOK SAMPLE ***

                %s
                *** END OF THE PROJECT GUTENBERG EBOOK SAMPLE ***
                """.formatted(body)).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("ContentProcessor 에는 @Transactional 이 없어야 한다")
    void processor_must_not_be_transactional() {
        assertThat(ContentProcessor.class.getAnnotation(Transactional.class)).isNull();
        for (Method method : ContentProcessor.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(Transactional.class))
                    .as("메서드 %s", method.getName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("Gutendex 다운로드는 PROCESSING 커밋 이후, 저장 트랜잭션 이전에 수행된다")
    void download_happens_between_the_two_transactions() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(gutendexClient.downloadText("http://example.test/book.txt")).thenReturn(sampleBook());
        when(transactionService.saveContentAndComplete(anyLong(), anyLong(), any())).thenReturn(2);

        processor.processFromGutendex(JOB_ID, "http://example.test/book.txt");

        InOrder order = inOrder(transactionService, gutendexClient);
        order.verify(transactionService).startProcessing(JOB_ID);                 // 짧은 TX #1
        order.verify(gutendexClient).downloadText(anyString());                   // TX 밖 (HTTP)
        order.verify(transactionService).saveContentAndComplete(eq(JOB_ID), eq(3L), any()); // 짧은 TX #2
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("다운로드 실패 시 저장을 시도하지 않고 FAILED 로 전이한다")
    void marks_failed_when_download_fails() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(gutendexClient.downloadText(anyString()))
                .thenThrow(new CustomException(ErrorCode.INVALID_INPUT));

        processor.processFromGutendex(JOB_ID, "http://example.test/book.txt");

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), eq(true));
        verify(transactionService, never()).saveContentAndComplete(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("시작 트랜잭션 실패 시 다운로드조차 시도하지 않는다")
    void aborts_without_downloading_when_start_fails() {
        when(transactionService.startProcessing(JOB_ID))
                .thenThrow(new IllegalStateException("ContentJob not found: 7"));

        processor.processFromGutendex(JOB_ID, "http://example.test/book.txt");

        verify(gutendexClient, never()).downloadText(anyString());
        verify(transactionService, never()).saveContentAndComplete(anyLong(), anyLong(), any());
        verify(transactionService, never()).markFailed(anyLong(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("저장 트랜잭션이 실패하면 FAILED 로 전이한다")
    void marks_failed_when_save_transaction_fails() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(transactionService.saveContentAndComplete(anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("db down"));

        processor.process(JOB_ID, sampleBook());

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), eq(true));
    }

    @Test
    @DisplayName("업로드 경로도 파싱을 트랜잭션 밖에서 하고 저장만 트랜잭션으로 처리한다")
    void upload_path_uses_the_same_boundary() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(transactionService.saveContentAndComplete(anyLong(), anyLong(), any())).thenReturn(2);

        processor.process(JOB_ID, sampleBook());

        InOrder order = inOrder(transactionService);
        order.verify(transactionService).startProcessing(JOB_ID);
        order.verify(transactionService).saveContentAndComplete(eq(JOB_ID), eq(3L), any());
        order.verifyNoMoreInteractions();
    }
}
