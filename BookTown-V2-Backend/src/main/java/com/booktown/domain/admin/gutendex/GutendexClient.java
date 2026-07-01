package com.booktown.domain.admin.gutendex;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class GutendexClient {

    private static final String BASE_URL = "https://gutendex.com";
    private static final Duration API_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_PAGE = 1000;
    private static final int MAX_TEXT_BYTES = 10 * 1024 * 1024;

    private final WebClient webClient;

    public GutendexClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
    }

    public GutendexPageDto search(String keyword, int page) {
        String trimmedKeyword = requireKeyword(keyword);
        int safePage = Math.max(1, Math.min(page, MAX_PAGE));
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/books")
                        .queryParam("search", trimmedKeyword)
                        .queryParam("languages", "en")
                        .queryParam("copyright", "false")
                        .queryParam("mime_type", "text/plain")
                        .queryParam("page", safePage)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.createException().map(RuntimeException::new))
                .bodyToMono(GutendexPageDto.class)
                .timeout(API_TIMEOUT)
                .blockOptional()
                .orElse(new GutendexPageDto(0, null, null, java.util.List.of()));
    }

    public GutendexBookDto getBook(Long gutenbergId) {
        if (gutenbergId == null || gutenbergId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        try {
            return webClient.get()
                    .uri("/books/{id}", gutenbergId)
                    .retrieve()
                    .bodyToMono(GutendexBookDto.class)
                    .timeout(API_TIMEOUT)
                    .blockOptional()
                    .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        } catch (WebClientResponseException.NotFound e) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gutendex book fetch failed: id={}, message={}", gutenbergId, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public byte[] downloadText(String textUrl) {
        if (textUrl == null || textUrl.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        try {
            byte[] bytes = webClient.get()
                    .uri(textUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(DOWNLOAD_TIMEOUT)
                    .blockOptional()
                    .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_UNAVAILABLE));
            if (bytes.length > MAX_TEXT_BYTES) {
                throw new CustomException(ErrorCode.FILE_TOO_LARGE);
            }
            return bytes;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gutendex text download failed: url={}, message={}", textUrl, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public Optional<Integer> extractPage(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        int marker = url.indexOf("page=");
        if (marker < 0) return Optional.empty();
        int start = marker + "page=".length();
        int end = start;
        while (end < url.length() && Character.isDigit(url.charAt(end))) {
            end++;
        }
        if (end == start) return Optional.empty();
        return Optional.of(Integer.parseInt(url.substring(start, end)));
    }

    private String requireKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return keyword.trim();
    }
}
