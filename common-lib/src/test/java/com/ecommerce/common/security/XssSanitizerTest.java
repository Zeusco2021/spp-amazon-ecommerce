package com.ecommerce.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for XssSanitizer.
 * Requirements: 15.8
 */
class XssSanitizerTest {

    // -------------------------------------------------------
    // Null / clean input
    // -------------------------------------------------------

    @Test
    void sanitize_nullInput_returnsNull() {
        assertThat(XssSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_cleanText_returnsTextWithEncodedSpecialChars() {
        // Plain text with no dangerous content should come back (with & encoded if present)
        String result = XssSanitizer.sanitize("Hello World");
        assertThat(result).isEqualTo("Hello World");
    }

    // -------------------------------------------------------
    // Script tag removal (Req 15.8)
    // -------------------------------------------------------

    @Test
    void sanitize_scriptTag_isRemoved() {
        String input = "Hello <script>alert('xss')</script> World";
        String result = XssSanitizer.sanitize(input);

        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("</script>");
        assertThat(result).doesNotContain("alert");
    }

    @Test
    void sanitize_scriptTagWithAttributes_isRemoved() {
        String input = "<script type=\"text/javascript\">evil()</script>";
        String result = XssSanitizer.sanitize(input);

        assertThat(result).doesNotContain("evil()");
    }

    // -------------------------------------------------------
    // Inline event handler removal (Req 15.8)
    // -------------------------------------------------------

    @Test
    void sanitize_onclickHandler_isRemoved() {
        String input = "<button onclick=\"stealCookies()\">Click me</button>";
        String result = XssSanitizer.sanitize(input);

        assertThat(result).doesNotContain("onclick");
        assertThat(result).doesNotContain("stealCookies");
    }

    @Test
    void sanitize_onmouseoverHandler_isRemoved() {
        String input = "<img src=\"x\" onmouseover=\"alert(1)\">";
        String result = XssSanitizer.sanitize(input);

        assertThat(result).doesNotContain("onmouseover");
    }

    // -------------------------------------------------------
    // javascript: URI removal (Req 15.8)
    // -------------------------------------------------------

    @Test
    void sanitize_javascriptUri_isRemoved() {
        String input = "<a href=\"javascript:alert('xss')\">link</a>";
        String result = XssSanitizer.sanitize(input);

        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    void sanitize_javascriptUriCaseInsensitive_isRemoved() {
        String input = "<a href=\"JAVASCRIPT:void(0)\">link</a>";
        String result = XssSanitizer.sanitize(input);

        assertThat(result).doesNotContainIgnoringCase("javascript:");
    }

    // -------------------------------------------------------
    // HTML special character encoding (Req 15.8)
    // -------------------------------------------------------

    @Test
    void sanitize_ampersand_isEncoded() {
        assertThat(XssSanitizer.sanitize("a & b")).contains("&amp;");
    }

    @Test
    void sanitize_lessThan_isEncoded() {
        // After script removal, remaining < should be encoded
        String result = XssSanitizer.sanitize("a < b");
        assertThat(result).contains("&lt;");
    }

    @Test
    void sanitize_greaterThan_isEncoded() {
        String result = XssSanitizer.sanitize("a > b");
        assertThat(result).contains("&gt;");
    }

    @Test
    void sanitize_doubleQuote_isEncoded() {
        String result = XssSanitizer.sanitize("say \"hello\"");
        assertThat(result).contains("&quot;");
    }

    @Test
    void sanitize_singleQuote_isEncoded() {
        String result = XssSanitizer.sanitize("it's fine");
        assertThat(result).contains("&#x27;");
    }
}
