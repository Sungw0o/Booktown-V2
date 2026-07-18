package com.booktown.domain.admin.service;

import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ContentChapterSegmenter {

    static final int TARGET_CHAPTER_COUNT = 10;
    private static final Pattern SOURCE_HEADING_PATTERN = Pattern.compile(
            "(?i)^(chapter|letter|book|part)\\s+[ivxlcdm\\d]+(?:[.:\\s-].*)?$"
    );

    public List<ChapterSegment> segment(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Content must not be blank.");
        }

        int preferredUnitSize = Math.max(1, normalized.length() / TARGET_CHAPTER_COUNT);
        List<String> units = splitParagraphs(normalized, preferredUnitSize);
        ensureMinimumUnits(units);

        List<List<String>> groups = partition(units);
        List<ChapterSegment> chapters = new ArrayList<>(TARGET_CHAPTER_COUNT);
        for (int i = 0; i < groups.size(); i++) {
            chapters.add(new ChapterSegment((i + 1) + "부", String.join("\n\n", groups.get(i)).trim()));
        }
        return List.copyOf(chapters);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private List<String> splitParagraphs(String text, int preferredUnitSize) {
        List<String> units = new ArrayList<>();
        String[] paragraphs = text.split("\n\\s*\n");
        for (int index = 0; index < paragraphs.length; index++) {
            String value = paragraphs[index].trim();
            if (value.isBlank()) {
                continue;
            }
            if (SOURCE_HEADING_PATTERN.matcher(value.replaceAll("\\s+", " ")).matches()
                    && index + 1 < paragraphs.length) {
                String followingParagraph = paragraphs[++index].trim();
                if (!followingParagraph.isBlank()) {
                    value = value + "\n\n" + followingParagraph;
                }
            }
            if (value.length() > preferredUnitSize * 3 / 2) {
                units.addAll(splitSentences(value, preferredUnitSize));
            } else {
                units.add(value);
            }
        }
        return units;
    }

    private List<String> splitSentences(String text, int preferredUnitSize) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(text);
        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (sentence.isBlank()) {
                continue;
            }
            if (sentence.length() > preferredUnitSize * 3 / 2) {
                sentences.addAll(splitNearSize(sentence, preferredUnitSize));
            } else {
                sentences.add(sentence);
            }
        }
        return sentences.isEmpty() ? List.of(text) : sentences;
    }

    private List<String> splitNearSize(String text, int preferredUnitSize) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int desiredEnd = Math.min(start + preferredUnitSize, text.length());
            if (desiredEnd < text.length()) {
                int boundary = text.lastIndexOf(' ', desiredEnd);
                if (boundary > start) {
                    desiredEnd = boundary;
                }
            }
            String part = text.substring(start, desiredEnd).trim();
            if (!part.isBlank()) {
                parts.add(part);
            }
            start = desiredEnd;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return parts;
    }

    private void ensureMinimumUnits(List<String> units) {
        while (units.size() < TARGET_CHAPTER_COUNT) {
            int longestIndex = indexOfLongestSplittableUnit(units);
            if (longestIndex < 0) {
                throw new IllegalArgumentException("Content is too short to split into 10 chapters.");
            }
            String longest = units.remove(longestIndex);
            int splitAt = nearestBoundary(longest);
            units.add(longestIndex, longest.substring(0, splitAt).trim());
            units.add(longestIndex + 1, longest.substring(splitAt).trim());
        }
    }

    private int indexOfLongestSplittableUnit(List<String> units) {
        return units.stream()
                .filter(unit -> unit.length() >= 2)
                .max(Comparator.comparingInt(String::length))
                .map(units::indexOf)
                .orElse(-1);
    }

    private int nearestBoundary(String text) {
        int middle = text.length() / 2;
        for (int distance = 0; distance < text.length(); distance++) {
            int right = middle + distance;
            if (right > 0 && right < text.length() && isBoundary(text.charAt(right))) {
                return right;
            }
            int left = middle - distance;
            if (left > 0 && left < text.length() && isBoundary(text.charAt(left))) {
                return left;
            }
        }
        return middle;
    }

    private boolean isBoundary(char value) {
        return Character.isWhitespace(value) || value == '.' || value == '!' || value == '?' || value == ',';
    }

    private List<List<String>> partition(List<String> units) {
        List<List<String>> groups = new ArrayList<>(TARGET_CHAPTER_COUNT);
        int unitIndex = 0;
        int remainingCharacters = units.stream().mapToInt(String::length).sum();

        for (int groupIndex = 0; groupIndex < TARGET_CHAPTER_COUNT; groupIndex++) {
            int remainingGroups = TARGET_CHAPTER_COUNT - groupIndex;
            int unitsToLeave = remainingGroups - 1;
            int targetSize = Math.max(1, remainingCharacters / remainingGroups);
            List<String> group = new ArrayList<>();
            int groupSize = 0;

            while (unitIndex < units.size() - unitsToLeave) {
                String candidate = units.get(unitIndex);
                if (!group.isEmpty() && groupSize >= targetSize) {
                    break;
                }
                group.add(candidate);
                groupSize += candidate.length();
                unitIndex++;
            }

            groups.add(group);
            remainingCharacters -= groupSize;
        }
        return groups;
    }

    public record ChapterSegment(String title, String content) {
    }
}
