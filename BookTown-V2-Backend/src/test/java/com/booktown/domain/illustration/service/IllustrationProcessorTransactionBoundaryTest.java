package com.booktown.domain.illustration.service;

import com.booktown.domain.illustration.document.IllustrationDocument;
import com.booktown.domain.illustration.entity.IllustrationStyle;
import com.booktown.domain.illustration.repository.IllustrationDocumentRepository;
import com.booktown.domain.illustration.service.IllustrationJobTransactionService.IllustrationJobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

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

/** IllustrationProcessor 트랜잭션 경계 재설계에 대한 회귀 방지 테스트. */
class IllustrationProcessorTransactionBoundaryTest {

    private static final Long JOB_ID = 21L;

    private IllustrationJobTransactionService transactionService;
    private IllustrationDocumentRepository documentRepository;
    private ImageGenerationClient imageGenerationClient;
    private IllustrationProcessor processor;

    @BeforeEach
    void setUp() {
        transactionService = mock(IllustrationJobTransactionService.class);
        documentRepository = mock(IllustrationDocumentRepository.class);
        imageGenerationClient = mock(ImageGenerationClient.class);

        processor = new IllustrationProcessor(
                transactionService, documentRepository,
                imageGenerationClient, new BookVisualPromptFactory());
    }

    private IllustrationJobContext context() {
        return new IllustrationJobContext(
                JOB_ID, 5L, 3L, IllustrationStyle.WATERCOLOR, "따뜻한 분위기",
                "정원의 아침", "정원에서 두 인물이 마주친다.", "위대한 개츠비", "피츠제럴드"
        );
    }

    @Test
    @DisplayName("IllustrationProcessor 에는 @Transactional 이 없어야 한다")
    void processor_must_not_be_transactional() {
        assertThat(IllustrationProcessor.class.getAnnotation(Transactional.class)).isNull();
        for (Method method : IllustrationProcessor.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(Transactional.class))
                    .as("메서드 %s", method.getName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("PROCESSING 커밋 → 이미지 생성 API 호출 → COMPLETED 커밋 순서로 실행된다")
    void short_transactions_wrap_the_image_call() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(imageGenerationClient.generateImageDataUrl(anyString())).thenReturn("data:image/png;base64,AAA");
        IllustrationDocument saved = mock(IllustrationDocument.class);
        when(saved.getId()).thenReturn("illus-1");
        when(documentRepository.save(any())).thenReturn(saved);

        processor.process(JOB_ID, false);

        InOrder order = inOrder(transactionService, imageGenerationClient, documentRepository);
        order.verify(transactionService).startProcessing(JOB_ID);
        order.verify(imageGenerationClient).generateImageDataUrl(anyString());
        order.verify(documentRepository).save(any());
        order.verify(transactionService).markCompleted(JOB_ID, "illus-1");
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 프롬프트를 만들 때 엔티티 접근 없이 값만 사용한다")
    void prompt_is_built_from_plain_values() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(imageGenerationClient.generateImageDataUrl(anyString())).thenReturn("data:image/png;base64,AAA");
        IllustrationDocument saved = mock(IllustrationDocument.class);
        when(saved.getId()).thenReturn("illus-1");
        when(documentRepository.save(any())).thenReturn(saved);

        processor.process(JOB_ID, false);

        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(imageGenerationClient).generateImageDataUrl(prompt.capture());
        assertThat(prompt.getValue())
                .contains("위대한 개츠비")
                .contains("피츠제럴드")
                .contains("정원의 아침");
    }

    @Test
    @DisplayName("이미지 생성 실패 시 FAILED 로 전이하고 문서를 저장하지 않는다")
    void marks_failed_when_image_generation_throws() {
        when(transactionService.startProcessing(JOB_ID)).thenReturn(context());
        when(imageGenerationClient.generateImageDataUrl(anyString()))
                .thenThrow(new RuntimeException("image api timeout"));

        processor.process(JOB_ID, false);

        verify(transactionService).markFailed(eq(JOB_ID), anyString(), eq(true));
        verify(documentRepository, never()).save(any());
        verify(transactionService, never()).markCompleted(anyLong(), anyString());
    }

    @Test
    @DisplayName("시작 트랜잭션 실패 시 이미지 API 를 호출하지 않는다")
    void aborts_without_calling_image_api_when_start_fails() {
        when(transactionService.startProcessing(JOB_ID))
                .thenThrow(new IllegalStateException("IllustrationJob not found: 21"));

        processor.process(JOB_ID, false);

        verify(imageGenerationClient, never()).generateImageDataUrl(anyString());
        verify(transactionService, never()).markFailed(anyLong(), anyString(), anyBoolean());
    }
}
