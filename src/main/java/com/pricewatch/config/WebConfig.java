package com.pricewatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the browser frontend to call the API from another origin. The frontend
 * runs on Vercel (and on localhost during development) while the API is on
 * Railway, so without this every request would be blocked by the browser's
 * same-origin policy. Origins are env-overridable
 * ({@code APP_CORS_ALLOWED_ORIGIN_PATTERNS}) so a custom domain can be added
 * without a code change. Patterns (not plain origins) are used because
 * credentials are allowed, which forbids a bare "*".
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public WebConfig(
        @Value("${APP_CORS_ALLOWED_ORIGIN_PATTERNS:http://localhost:5173,https://*.vercel.app}")
        String[] allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(allowedOriginPatterns)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
