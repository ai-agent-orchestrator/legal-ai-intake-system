package com.ohgiraffers.handlermethod.config;

import com.ohgiraffers.handlermethod.support.SecurityMetricRecorder;
import com.ohgiraffers.handlermethod.security.JwtAuthenticationFilter;
import com.ohgiraffers.handlermethod.security.JwtJsonAccessDeniedHandler;
import com.ohgiraffers.handlermethod.security.JwtJsonAuthenticationEntryPoint;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final SecurityMetricRecorder securityMetricRecorder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtJsonAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtJsonAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(SecurityMetricRecorder securityMetricRecorder,
                          JwtTokenProvider jwtTokenProvider,
                          JwtJsonAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          JwtJsonAccessDeniedHandler jwtAccessDeniedHandler) {
        this.securityMetricRecorder = securityMetricRecorder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurity(HttpSecurity http,
                                         UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            securityMetricRecorder.recordAuthFailure(request);
                            response.setHeader("WWW-Authenticate", "Basic realm=\"actuator\"");
                            response.sendError(401, "Unauthorized actuator access");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            securityMetricRecorder.recordAccessDenied(request);
                            response.sendError(403, "Forbidden actuator access");
                        }))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurity(HttpSecurity http,
                                            UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**", "/api/agent/risk/history", "/api/agent/risk/summary",
                                "/api/incidents", "/api/incidents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/intakes/classify-preview").permitAll()
                        .requestMatchers("/api/intakes", "/api/intakes/**", "/api/legal-intake/minimal").authenticated()
                        .requestMatchers("/api/me", "/api/intakes/*/documents/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/intakes/*/search-requests").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/intakes/search-requests/*/execute").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/legal/**").permitAll()
                        .requestMatchers("/api/legal/**").authenticated()
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers("/api/**", "/h2-console/**").permitAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    InMemoryUserDetailsManager users(org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .build();
        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("user123"))
                .roles("USER")
                .build();

        UserDetails lawyer = User.withUsername("lawyer").password(passwordEncoder.encode("lawyer123")).roles("LAWYER").build();
        UserDetails lawyer2 = User.withUsername("lawyer2").password(passwordEncoder.encode("lawyer2123")).roles("LAWYER").build();
        UserDetails user2 = User.withUsername("user2").password(passwordEncoder.encode("user2123")).roles("USER").build();
        return new InMemoryUserDetailsManager(admin, user, lawyer, lawyer2, user2);
    }

    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Bean
    org.springframework.security.authentication.AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
