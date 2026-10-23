package com.ohgiraffers.handlermethod.config;

import com.ohgiraffers.handlermethod.interceptor.TraceIdInterceptor;
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

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOriginPatterns(List.of("*"));
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
