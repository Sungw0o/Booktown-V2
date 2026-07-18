package com.booktown.domain.illustration.service;

import java.util.Base64;

public interface ImageGenerationClient {

    GeneratedImage generateImage(String prompt);

    default String generateImageDataUrl(String prompt) {
        GeneratedImage image = generateImage(prompt);
        return "data:" + image.mimeType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes());
    }

    record GeneratedImage(String mimeType, byte[] bytes) {
    }
}
