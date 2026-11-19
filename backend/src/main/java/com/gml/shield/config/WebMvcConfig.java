package com.gml.shield.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enregistre les composants utilisés par Spring MVC.
 */

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final WriteRequestAuditInterceptor auditInterceptor;

    public WebMvcConfig(WriteRequestAuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }
}
