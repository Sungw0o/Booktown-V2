package com.booktown.domain.illustration.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiImageClientTest {

    private ImageModel imageModel;
    private ObjectProvider<ImageModel> imageModelProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        imageModel = mock(ImageModel.class);
        imageModelProvider = mock(ObjectProvider.class);
        when(imageModelProvider.getIfAvailable()).thenReturn(imageModel);
    }

    @Test
    void generateImageDecodesBase64Response() {
        byte[] expected = "generated-image".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(expected);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(imageResponse(base64));
        OpenAiImageClient client = new OpenAiImageClient(imageModelProvider, "test-key", "image/png");

        ImageGenerationClient.GeneratedImage result = client.generateImage("book cover");

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.bytes()).containsExactly(expected);
    }

    @Test
    void generateImageDataUrlUsesConfiguredMimeType() {
        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(imageResponse(base64));
        OpenAiImageClient client = new OpenAiImageClient(imageModelProvider, "test-key", "image/png");

        assertThat(client.generateImageDataUrl("scene"))
                .isEqualTo("data:image/png;base64," + base64);
    }

    @Test
    void missingApiKeyReturnsAiServiceErrorWithoutCallingModel() {
        OpenAiImageClient client = new OpenAiImageClient(imageModelProvider, " ", "image/png");

        CustomException exception = catchThrowableOfType(
                () -> client.generateImage("book cover"),
                CustomException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_ERROR);
        verifyNoInteractions(imageModel);
    }

    @Test
    void emptyImageResponseReturnsAiServiceError() {
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(new ImageResponse(List.of()));
        OpenAiImageClient client = new OpenAiImageClient(imageModelProvider, "test-key", "image/png");

        CustomException exception = catchThrowableOfType(
                () -> client.generateImage("book cover"),
                CustomException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_ERROR);
    }

    @Test
    void providerFailureReturnsAiServiceError() {
        when(imageModel.call(any(ImagePrompt.class))).thenThrow(new RuntimeException("provider unavailable"));
        OpenAiImageClient client = new OpenAiImageClient(imageModelProvider, "test-key", "image/png");

        CustomException exception = catchThrowableOfType(
                () -> client.generateImage("book cover"),
                CustomException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_ERROR);
    }

    @Test
    void invalidBase64ResponseReturnsAiServiceError() {
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(imageResponse("not-base64"));
        OpenAiImageClient client = new OpenAiImageClient(imageModelProvider, "test-key", "image/png");

        CustomException exception = catchThrowableOfType(
                () -> client.generateImage("book cover"),
                CustomException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_ERROR);
    }

    private ImageResponse imageResponse(String base64) {
        return new ImageResponse(List.of(new ImageGeneration(new Image(null, base64))));
    }
}
