package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.PracticeMenu;
import com.ohgiraffers.handlermethod.support.TraceContext;

public record PracticeMenuResponse(
        Long id,
        String name,
        int price,
        String message,
        String traceId
) {

    public static PracticeMenuResponse from(PracticeMenu menu, String message) {
        return new PracticeMenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getPrice(),
                message,
                TraceContext.currentTraceId()
        );
    }
}
