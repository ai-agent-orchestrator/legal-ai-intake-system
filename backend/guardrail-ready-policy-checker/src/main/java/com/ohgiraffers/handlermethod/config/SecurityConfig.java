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

    /*
     * Application route policy. Every /api route must match one of the lists below (checked in the order used in
     * applicationSecurity, first match wins); anything else under /api is denied (401 anonymous / 403 signed-in),
     * so new endpoints are closed by default. SecurityRouteInventoryTest fails when a controller route is unlisted.
     * Actuator endpoints are handled by actuatorSecurity (ADMIN Basic) and are not listed here.
     */

    /** Anonymous POST: login, guest classification (screen 02) and anonymous funnel events (validated per session). */
    static final String[] PUBLIC_POST_APIS = {
            "/api/auth/login",
            "/api/intakes/classify-preview",
            "/api/intake-events"
    };

    /** Anonymous GET: the shared legal procedure/form reference catalog only. */
    static final String[] PUBLIC_GET_APIS = {
            "/api/legal/procedures", "/api/legal/procedures/**",
            "/api/legal/forms", "/api/legal/forms/**",
            "/api/legal/criminal/**"
    };

    /** ADMIN GET: aggregates across all users' legal-intake analyses (contain case summaries). */
    static final String[] ADMIN_GET_APIS = {
            "/api/legal/intake/history",
            "/api/legal/intake/summary"
    };

    /**
     * ADMIN (any method): operator screens 09-13 and the lab/practice APIs.
     * /api/agent/** writes risk history and incidents that the operator screens display,
     * so anonymous callers must not be able to create them.
     */
    static final String[] ADMIN_APIS = {
            "/api/admin/**",
            "/api/agent/**",
            "/api/incidents", "/api/incidents/**",
            "/api/chat",
            "/api/menus", "/api/menus/**",
            "/api/n-plus-one-practice/**",
            "/api/transaction-practice/**",
            "/api/status",
            "/api/observability/**",
            "/api/security-observability/**"
    };

    /** Any signed-in role. Ownership/assignment rules are enforced in the services. */
    static final String[] AUTHENTICATED_APIS = {
            "/api/me",
            "/api/intakes", "/api/intakes/**",
            "/api/legal-intake/minimal",
            "/api/legal/intake/analyze",
            "/api/legal/guardrail/**",
            "/api/ai/**"
    };

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
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_APIS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_APIS).permitAll()
                        .requestMatchers(HttpMethod.GET, ADMIN_GET_APIS).hasRole("ADMIN")
                        .requestMatchers(ADMIN_APIS).hasRole("ADMIN")
                        .requestMatchers(AUTHENTICATED_APIS).authenticated()
                        // Default deny: a new /api endpoint stays closed until it is listed above.
                        .requestMatchers("/api/**").denyAll()
                        .requestMatchers("/h2-console/**").permitAll()
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
