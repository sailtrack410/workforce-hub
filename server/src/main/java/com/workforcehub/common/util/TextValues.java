package com.workforcehub.common.util;

import org.springframework.util.StringUtils;

public final class TextValues {

    private TextValues() {
    }

    public static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static String safeSummary(String value) {
        return safeSummary(value, null);
    }

    public static String safeSummary(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }
}
