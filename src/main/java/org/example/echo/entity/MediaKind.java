package org.example.echo.entity;

public enum MediaKind {
    MOVIE("movie"),
    TV("tv");

    private final String externalValue;

    MediaKind(String externalValue) {
        this.externalValue = externalValue;
    }

    public String externalValue() {
        return externalValue;
    }

    public static MediaKind from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Media type must not be blank");
        }

        String normalized = value.trim();
        for (MediaKind mediaKind : values()) {
            if (mediaKind.name().equalsIgnoreCase(normalized)
                || mediaKind.externalValue.equalsIgnoreCase(normalized)) {
                return mediaKind;
            }
        }

        throw new IllegalArgumentException("Unsupported media type");
    }
}
