package com.booktown.domain.admin.gutendex;

import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.io.ByteArrayOutputStream;
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
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_TEXT_BYTES))
                .build();
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .exchangeStrategies(strategies)
                .build();
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
            return webClient.get()
                    .uri(textUrl)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .reduce(new ByteArrayOutputStream(), this::appendChunk)
                    .map(ByteArrayOutputStream::toByteArray)
                    .timeout(DOWNLOAD_TIMEOUT)
                    .blockOptional()
                    .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_UNAVAILABLE));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            Throwable unwrapped = Exceptions.unwrap(e);
            if (unwrapped instanceof CustomException customException) {
                throw customException;
            }
            log.warn("Gutendex text download failed: url={}, message={}", textUrl, e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private ByteArrayOutputStream appendChunk(ByteArrayOutputStream output, DataBuffer buffer) {
        try {
            int readableBytes = buffer.readableByteCount();
            if (output.size() + readableBytes > MAX_TEXT_BYTES) {
                throw new CustomException(ErrorCode.FILE_TOO_LARGE);
            }
            byte[] chunk = new byte[readableBytes];
            buffer.read(chunk);
            output.write(chunk, 0, chunk.length);
            return output;
        } finally {
            DataBufferUtils.release(buffer);
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
