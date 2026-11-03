package org.example.echo.tmdb;

public enum TmdbTrendingWindow {
    DAY("day"),
    WEEK("week");

    private final String externalValue;

    TmdbTrendingWindow(String externalValue) {
        this.externalValue = externalValue;
    }

    public String externalValue() {
        return externalValue;
    }

    public static TmdbTrendingWindow from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trending window must not be blank");
        }

        String normalized = value.trim();
        for (TmdbTrendingWindow window : values()) {
            if (window.name().equalsIgnoreCase(normalized)
                || window.externalValue.equalsIgnoreCase(normalized)) {
                return window;
            }
        }

        throw new IllegalArgumentException("Unsupported trending window. Allowed values: day, week");
    }
}
