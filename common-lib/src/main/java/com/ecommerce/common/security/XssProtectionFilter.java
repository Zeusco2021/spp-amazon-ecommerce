package com.ecommerce.common.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;

import java.io.IOException;

/**
 * Servlet filter that wraps every incoming HTTP request with {@link XssRequestWrapper}
 * to sanitize parameters, headers, and the request body against XSS attacks.
 *
 * <p>Registered via Spring Boot auto-configuration ({@link XssSecurityAutoConfiguration})
 * so it is automatically applied to any web service that includes common-lib.
 *
 * Requirements: 15.8
 */
@Order(1)
public class XssProtectionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest) {
            chain.doFilter(new XssRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
