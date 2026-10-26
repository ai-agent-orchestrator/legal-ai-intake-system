package com.ohgiraffers.handlermethod.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every controller route under /api must be classified in SecurityConfig.
 * An unclassified route is denied by default (safe), but it is almost always a forgotten policy decision,
 * so this test fails and names the route instead of letting it silently return 401/403.
 */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
class SecurityRouteInventoryTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void everyApiRouteHasAnExplicitPolicy() {
        List<PathPattern> classified = Stream.of(
                        SecurityConfig.PUBLIC_POST_APIS, SecurityConfig.PUBLIC_GET_APIS, SecurityConfig.ADMIN_GET_APIS,
                        SecurityConfig.ADMIN_APIS, SecurityConfig.AUTHENTICATED_APIS)
                .flatMap(Arrays::stream)
                .map(PathPatternParser.defaultInstance::parse)
                .toList();

        List<String> routes = mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .filter(pattern -> pattern.startsWith("/api/"))
                .distinct()
                .toList();
        assertThat(routes).as("controller routes under /api").isNotEmpty();

        List<String> unclassified = routes.stream()
                .filter(pattern -> {
                    PathContainer concrete = PathContainer.parsePath(pattern.replaceAll("\\{[^}]+}", "1"));
                    return classified.stream().noneMatch(policy -> policy.matches(concrete));
                })
                .sorted()
                .toList();

        assertThat(unclassified).as("routes missing from SecurityConfig route lists").isEmpty();
    }
}
