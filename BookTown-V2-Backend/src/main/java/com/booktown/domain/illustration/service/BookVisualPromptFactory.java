package com.booktown.domain.illustration.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.illustration.entity.IllustrationStyle;
import org.springframework.stereotype.Component;

@Component
public class BookVisualPromptFactory {

    static final String STYLE_BIBLE = """
            Shared visual style bible for this book series:
            - polished Korean webtoon illustration with clean, expressive line art
            - soft cel shading, restrained gradients, cinematic lighting, and a cohesive muted jewel-tone palette
            - elegant period-authentic costumes and environments, simplified into readable webtoon shapes
            - emotionally clear facial expressions and natural body language
            - consistent facial features, hair, costume silhouettes, line weight, palette, and lighting across every image
            - editorial quality for a classic-literature reading app; no photorealism, 3D render, collage, or copied cover art
            """;

    public String createCoverPrompt(Book book) {
        return """
                Create an original vertical cover illustration for the same visual series identified below.
                Do not include readable text, logos, author portraits, watermarks, frames, or publisher marks.
                Composition: one iconic story moment, centered focal character, strong silhouette, generous title-safe negative space.

                %s
                Series identity: %s by %s.
                Genre: %s.
                Story context: %s
                """.formatted(
                STYLE_BIBLE,
                book.getTitle(),
                book.getAuthor(),
                book.getGenre().name(),
                limit(book.getDescription(), 900)
        );
    }

    public String createScenePrompt(Scene scene, IllustrationStyle requestedStyle, String promptHint) {
        Book book = scene.getBook();
        return """
                Create a vertical full-bleed story panel from the same visual series identified below.
                Keep recurring characters visually consistent with the series identity. Show one decisive action or emotional beat.
                Do not include captions, speech bubbles, readable text, logos, frames, or watermarks.

                %s
                Series identity: %s by %s.
                Requested accent: %s. Treat it as a subtle accent without replacing the shared webtoon style bible.
                Scene title: %s.
                Scene context: %s
                Additional direction: %s
                """.formatted(
                STYLE_BIBLE,
                book.getTitle(),
                book.getAuthor(),
                requestedStyle.name().toLowerCase(),
                scene.getTitle(),
                limit(scene.getExcerpt(), 700),
                limit(promptHint, 300)
        );
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "Not provided.";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
