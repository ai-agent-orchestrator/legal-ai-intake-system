package com.ohgiraffers.handlermethod.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
    @GetMapping("/api/me")
    public CurrentUserResponse me(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName(), authentication.getAuthorities().stream()
                .findFirst().orElseThrow().getAuthority());
    }

    public record CurrentUserResponse(String username, String role) { }
}
