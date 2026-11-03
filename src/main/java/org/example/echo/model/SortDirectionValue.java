package org.example.echo.model;

import org.springframework.data.domain.Sort;

import java.util.Locale;

public enum SortDirectionValue {
    ASC("asc", Sort.Direction.ASC),
    DESC("desc", Sort.Direction.DESC);

    private final String externalValue;
    private final Sort.Direction direction;

    SortDirectionValue(String externalValue, Sort.Direction direction) {
        this.externalValue = externalValue;
        this.direction = direction;
    }

    public Sort.Direction direction() {
        return direction;
    }

    public static SortDirectionValue from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Sort direction must not be null");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SortDirectionValue candidate : values()) {
            if (candidate.externalValue.equals(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Invalid sort direction");
    }
}
