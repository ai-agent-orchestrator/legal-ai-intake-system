package com.ohgiraffers.handlermethod.dto;

import com.ohgiraffers.handlermethod.entity.PracticeChatLog;

public record NPlusOneChatLogResponse(
        Long chatLogId,
        String message,
        Long userId,
        String userName
) {

    public static NPlusOneChatLogResponse from(PracticeChatLog chatLog) {
        return new NPlusOneChatLogResponse(
                chatLog.getId(),
                chatLog.getMessage(),
                chatLog.getUser().getId(),
                chatLog.getUser().getName()
        );
    }
}
