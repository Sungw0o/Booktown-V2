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
                INTENDED USE
                Create original Korean webtoon cover key art for a classic-literature mobile and web reading app.
                Design for a 1024x1536 portrait canvas with a 2:3 aspect ratio.

                %s

                SUBJECT AND STORY
                Series identity: %s by %s.
                Genre: %s.
                Story context: %s

                COMPOSITION
                - depict one iconic, emotionally dramatic story moment rather than a decorative portrait
                - use one primary character or one focal character pair with a strong, immediately readable silhouette
                - use a cinematic eye-level or subtle low-angle perspective with clear foreground, middle ground, and background depth
                - reserve calm negative space near the top for the app overlay, but do not render any text in that space
                - make the image read clearly at small book-card thumbnail size

                CHARACTER AND PERIOD DIRECTION
                - infer period-authentic clothing, architecture, props, and atmosphere from the book metadata
                - prioritize expressive eyes, controlled facial acting, elegant costume silhouettes, and dynamic fabric or hair movement
                - keep the result unmistakably like polished Korean webtoon cover art, not generic fantasy concept art

                OUTPUT CONSTRAINTS
                - no readable text, title lettering, captions, speech bubbles, logos, author portraits, watermarks, borders, or publisher marks
                - no photorealism, live-action photography, 3D render, western superhero comic style, manga screentones, chibi style, or collage
                - do not imitate or reproduce an existing published cover or a living artist's signature style
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
