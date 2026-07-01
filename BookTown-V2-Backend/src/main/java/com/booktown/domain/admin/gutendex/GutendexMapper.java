package com.booktown.domain.admin.gutendex;

import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class GutendexMapper {

    public GutendexBookSearchItem toSearchItem(GutendexBookDto book) {
        return new GutendexBookSearchItem(
                book.id(),
                nullToBlank(book.title()),
                firstAuthor(book),
                firstSummary(book),
                findCoverUrl(book.formats()).orElse(null),
                findTextPlainUrl(book.formats()).orElse(null),
                suggestGenre(book).name(),
                Country.WESTERN.name(),
                safeList(book.subjects()),
                safeList(book.bookshelves()),
                book.downloadCount()
        );
    }

    public Genre suggestGenre(GutendexBookDto book) {
        String joined = String.join(" ", safeList(book.subjects())) + " " + String.join(" ", safeList(book.bookshelves()));
        String text = joined.toLowerCase(Locale.ROOT);
        if (containsAny(text, "drama", "plays", "tragedy", "comedy")) return Genre.DRAMA;
        if (containsAny(text, "poetry", "poems", "verse")) return Genre.POETRY;
        if (containsAny(text, "essay", "essays", "letters", "biography", "autobiography")) return Genre.ESSAY;
        if (containsAny(text, "history", "historical", "war", "civilization")) return Genre.HISTORY;
        if (containsAny(text, "fiction", "novel", "romance", "adventure", "mystery", "literature")) return Genre.NOVEL;
        return Genre.NOVEL;
    }

    public String firstAuthor(GutendexBookDto book) {
        return safeList(book.authors()).stream()
                .map(GutendexAuthorDto::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("Unknown");
    }

    public String firstSummary(GutendexBookDto book) {
        return safeList(book.summaries()).stream()
                .filter(summary -> summary != null && !summary.isBlank())
                .findFirst()
                .orElseGet(() -> fallbackDescription(book));
    }

    public Optional<String> findTextPlainUrl(Map<String, String> formats) {
        if (formats == null || formats.isEmpty()) return Optional.empty();
        return formats.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).startsWith("text/plain"))
                .filter(entry -> isHttpUrl(entry.getValue()))
                .sorted(Comparator.comparingInt(entry -> textFormatRank(entry.getKey(), entry.getValue())))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Optional<String> findCoverUrl(Map<String, String> formats) {
        if (formats == null || formats.isEmpty()) return Optional.empty();
        return formats.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).startsWith("image/"))
                .map(Map.Entry::getValue)
                .filter(this::isHttpUrl)
                .findFirst();
    }

    private int textFormatRank(String key, String value) {
        String combined = (key + " " + value).toLowerCase(Locale.ROOT);
        if (combined.contains("utf-8")) return 0;
        if (combined.contains("us-ascii")) return 1;
        return 2;
    }

    private String fallbackDescription(GutendexBookDto book) {
        List<String> subjects = safeList(book.subjects());
        return subjects.isEmpty() ? null : String.join(", ", subjects.subList(0, Math.min(3, subjects.size())));
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
