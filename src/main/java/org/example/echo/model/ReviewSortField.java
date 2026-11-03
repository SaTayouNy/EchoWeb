package org.example.echo.model;

import java.util.Locale;

public enum ReviewSortField {
    CREATED_AT("createdAt", "createdAt"),
    RATING("rating", "rating");

    private final String externalValue;
    private final String property;

    ReviewSortField(String externalValue, String property) {
        this.externalValue = externalValue;
        this.property = property;
    }

    public String property() {
        return property;
    }

    public static ReviewSortField from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Review sort field must not be null");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ReviewSortField field : values()) {
            if (field.externalValue.toLowerCase(Locale.ROOT).equals(normalized)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Invalid review sort field");
    }
}
