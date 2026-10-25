package com.ohgiraffers.handlermethod.config;

import com.ohgiraffers.handlermethod.interceptor.TraceIdInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TraceIdInterceptor traceIdInterceptor;

    public WebMvcConfig(TraceIdInterceptor traceIdInterceptor) {
        this.traceIdInterceptor = traceIdInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor)
                .addPathPatterns("/api/**");
    }

    /**
     * Browser origins allowed to call /api and /actuator. Defaults to local development origins on any port
     * (React dev/preview servers and QA servers); set CORS_ALLOWED_ORIGIN_PATTERNS for a deployed front end.
     * Credentials (cookies) are never allowed; authentication uses the Authorization header only.
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin-patterns}") String[] allowedOriginPatterns) {
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOriginPatterns(List.of(allowedOriginPatterns));
        api.setAllowCredentials(false);
        api.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        api.setAllowedHeaders(List.of("*"));
        api.setExposedHeaders(List.of("Content-Disposition", "X-Trace-Id", "Idempotent-Replayed"));

        CorsConfiguration actuator = new CorsConfiguration(api);
        actuator.setAllowedMethods(List.of("GET", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", api);
        source.registerCorsConfiguration("/actuator/**", actuator);
        return source;
    }
}
