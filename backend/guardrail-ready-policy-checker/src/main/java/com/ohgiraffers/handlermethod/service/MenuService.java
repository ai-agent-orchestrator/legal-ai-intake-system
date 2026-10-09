package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.MenuCreateRequest;
import com.ohgiraffers.handlermethod.dto.MenuResponse;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;

@Service
public class MenuService {

    public MenuResponse createMenu(MenuCreateRequest request) {
        String message = request.name() + " 메뉴를 "
                + request.categoryCode() + "번 카테고리에 "
                + request.price() + "원으로 등록했습니다.";

        return new MenuResponse(
                request.name(),
                request.price(),
                request.categoryCode(),
                request.orderableStatus(),
                message,
                TraceContext.currentTraceId()
        );
    }
}
