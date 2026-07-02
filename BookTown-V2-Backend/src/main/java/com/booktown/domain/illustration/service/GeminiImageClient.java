package com.booktown.domain.illustration.service;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiImageClient {

    private static final String INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions";

    private final RestClient.Builder restClientBuilder;

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    @Value("${booktown.ai.gemini.image.model:gemini-3.1-flash-image}")
    private String model;

    @Value("${booktown.ai.gemini.image.mime-type:image/jpeg}")
    private String mimeType;

    @Value("${booktown.ai.gemini.image.aspect-ratio:3:4}")
    private String aspectRatio;

    @Value("${booktown.ai.gemini.image.image-size:1K}")
    private String imageSize;

    public GeneratedImage generateImage(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        JsonNode response;
        try {
            response = restClientBuilder.build()
                    .post()
                    .uri(INTERACTIONS_URL)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "input", List.of(Map.of("type", "text", "text", prompt)),
                            "response_format", Map.of(
                                    "type", "image",
                                    "mime_type", mimeType,
                                    "aspect_ratio", aspectRatio,
                                    "image_size", imageSize
                            )
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("Gemini image generation failed: status={} body={}",
                    e.getStatusCode().value(), sanitizeResponse(e.getResponseBodyAsString()));
            throw new CustomException(ErrorCode.AI_SERVICE_ERROR);
        }

        String imageData = extractImageData(response);
        if (imageData == null || imageData.isBlank()) {
            throw new IllegalStateException("Gemini image response did not include image data.");
        }

        String responseMimeType = response.path("output_image").path("mime_type").asText(mimeType);
        return new GeneratedImage(responseMimeType, Base64.getDecoder().decode(imageData));
    }

    public String generateImageDataUrl(String prompt) {
        GeneratedImage image = generateImage(prompt);
        return "data:" + image.mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
    }

    private String extractImageData(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode outputImage = node.path("output_image").path("data");
        if (outputImage.isTextual()) {
            return outputImage.asText();
        }
        if (node.has("type") && "image".equals(node.path("type").asText()) && node.path("data").isTextual()) {
            return node.path("data").asText();
        }
        if (node.has("inlineData") && node.path("inlineData").path("data").isTextual()) {
            return node.path("inlineData").path("data").asText();
        }
        if (node.has("inline_data") && node.path("inline_data").path("data").isTextual()) {
            return node.path("inline_data").path("data").asText();
        }
        if (node.isObject() || node.isArray()) {
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String found = extractImageData(values.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String sanitizeResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        String singleLine = responseBody.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 700 ? singleLine.substring(0, 700) + "..." : singleLine;
    }

    public record GeneratedImage(String mimeType, byte[] bytes) {
    }
}
