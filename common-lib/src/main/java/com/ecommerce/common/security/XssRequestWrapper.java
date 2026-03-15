package com.ecommerce.common.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequestWrapper that sanitizes query parameters and request body
 * to prevent XSS attacks.
 * Requirements: 15.8
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] sanitizedBody;

    public XssRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // Read and sanitize the request body
        byte[] rawBody = request.getInputStream().readAllBytes();
        String bodyString = new String(rawBody, StandardCharsets.UTF_8);
        String sanitizedBodyString = XssSanitizer.sanitize(bodyString);
        this.sanitizedBody = sanitizedBodyString != null
                ? sanitizedBodyString.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
    }

    @Override
    public String getParameter(String name) {
        return XssSanitizer.sanitize(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] sanitized = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitized[i] = XssSanitizer.sanitize(values[i]);
        }
        return sanitized;
    }

    @Override
    public String getHeader(String name) {
        return XssSanitizer.sanitize(super.getHeader(name));
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sanitizedBody);
        return new ServletInputStream() {
            @Override
            public int read() {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // no-op for synchronous wrapper
            }
        };
    }
}
