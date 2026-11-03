package org.example.echo.util;

import java.util.regex.Pattern;

public final class IpAddressUtils {
    private static final Pattern IPV4_PATTERN = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^(?=.*:)[0-9A-Fa-f:.]+$");

    private IpAddressUtils() {
    }

    public static boolean isIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String candidate = value.trim();
        return IPV4_PATTERN.matcher(candidate).matches() || IPV6_PATTERN.matcher(candidate).matches();
    }
}
