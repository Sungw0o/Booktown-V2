package com.booktown.domain.illustration.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Slf4j
@Component
public class OpenAiImageClient implements ImageGenerationClient {

    private final ObjectProvider<ImageModel> imageModelProvider;
    private final String apiKey;
    private final String mimeType;

    public OpenAiImageClient(
            ObjectProvider<ImageModel> imageModelProvider,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${booktown.ai.image.mime-type:image/png}") String mimeType
    ) {
        this.imageModelProvider = imageModelProvider;
        this.apiKey = apiKey;
        this.mimeType = mimeType;
    }

    @Override
    public GeneratedImage generateImage(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CustomException(ErrorCode.AI_SERVICE_ERROR);
        }

        try {
            ImageModel imageModel = imageModelProvider.getIfAvailable();
            if (imageModel == null) {
                throw new IllegalStateException("OpenAI image model is not configured.");
            }

            ImageResponse response = imageModel.call(new ImagePrompt(prompt));
            Image image = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput();
            String base64 = image == null ? null : image.getB64Json();
            if (base64 == null || base64.isBlank()) {
                throw new IllegalStateException("OpenAI image response did not include image data.");
            }

            return new GeneratedImage(mimeType, Base64.getDecoder().decode(base64));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI image generation failed: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.AI_SERVICE_ERROR);
        }
    }
}
