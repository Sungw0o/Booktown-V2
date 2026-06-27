package com.booktown.global.logging;

import java.util.List;
import java.util.regex.Pattern;

public final class SensitiveDataMasker {

    private static final String MASK = "$1=****";
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(password|passwd|pwd)\\s*=\\s*[^\\s,}]+"),
            Pattern.compile("(?i)\\b(token|access_token|refresh_token|id_token)\\s*=\\s*[^\\s,}]+"),
            Pattern.compile("(?i)\\b(secret|client_secret|api_key|apikey)\\s*=\\s*[^\\s,}]+"),
            Pattern.compile("(?i)\\b(authorization)\\s*=\\s*bearer\\s+[^\\s,}]+"),
            Pattern.compile("(?i)\\b(set-cookie|cookie)\\s*=\\s*[^\\n]+")
    );

    private SensitiveDataMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String masked = value;
        for (Pattern pattern : SECRET_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(MASK);
        }
        return masked;
    }
}
