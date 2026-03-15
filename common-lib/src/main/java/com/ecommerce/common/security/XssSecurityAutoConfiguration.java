package com.ecommerce.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers the {@link XssProtectionFilter}
 * for any web application that includes common-lib on its classpath.
 *
 * Requirements: 15.8
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class XssSecurityAutoConfiguration {

    @Bean
    public XssProtectionFilter xssProtectionFilter() {
        return new XssProtectionFilter();
    }
}
