package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.MenuCreateRequest;
import com.ohgiraffers.handlermethod.dto.MenuResponse;
import com.ohgiraffers.handlermethod.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menus")
public class MenuApiController {

    private final MenuService menuService;

    public MenuApiController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping
    public MenuResponse createMenu(@Valid @RequestBody MenuCreateRequest request) {
        return menuService.createMenu(request);
    }
}
