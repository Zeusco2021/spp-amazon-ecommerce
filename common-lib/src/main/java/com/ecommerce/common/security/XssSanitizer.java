package com.ecommerce.common.security;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing user input to prevent XSS attacks.
 * Requirements: 15.8
 */
public final class XssSanitizer {

    private static final Pattern SCRIPT_PATTERN =
            Pattern.compile("<script[^>]*>[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_EVENT_PATTERN =
            Pattern.compile("\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_PATTERN =
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);

    private XssSanitizer() {
        // utility class
    }

    /**
     * Sanitizes the given input string by:
     * 1. Removing {@code <script>} tags and their content
     * 2. Removing inline event handlers (e.g. {@code onclick="..."})
     * 3. Removing {@code javascript:} URI schemes
     * 4. HTML-encoding {@code <}, {@code >}, {@code "}, {@code '}, and {@code &}
     *
     * @param input the raw user input; may be {@code null}
     * @return sanitized string, or {@code null} if input was {@code null}
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input;

        // Remove <script>...</script> blocks
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");

        // Remove inline event handlers like onclick="..."
        sanitized = ON_EVENT_PATTERN.matcher(sanitized).replaceAll("");

        // Remove javascript: URI schemes
        sanitized = JAVASCRIPT_PATTERN.matcher(sanitized).replaceAll("");

        // Encode dangerous HTML characters
        sanitized = sanitized
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");

        return sanitized;
    }
}
