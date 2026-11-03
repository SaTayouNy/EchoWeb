package org.example.echo.util;

public final class StringNormalizer {
    private StringNormalizer() {
    }

    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
