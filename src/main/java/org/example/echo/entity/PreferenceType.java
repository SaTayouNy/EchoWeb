package org.example.echo.entity;

import java.util.Locale;

public enum PreferenceType {
    MOVIE, TV, ACTOR, DIRECTOR;

    public boolean isRelease() {
        return this == MOVIE || this == TV;
    }

    public static PreferenceType from(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
