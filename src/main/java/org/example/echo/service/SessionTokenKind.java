package org.example.echo.service;

public enum SessionTokenKind {
    ACCESS("access"),
    REFRESH("refresh");

    private final String code;

    SessionTokenKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SessionTokenKind fromCode(String code) {
        for (SessionTokenKind value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported token kind: " + code);
    }
}
