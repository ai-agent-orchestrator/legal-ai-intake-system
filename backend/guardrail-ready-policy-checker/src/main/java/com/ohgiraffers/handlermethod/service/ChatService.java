package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.ChatRequest;
import com.ohgiraffers.handlermethod.dto.ChatResponse;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    public ChatResponse chat(ChatRequest request) {
        String answer = request.userId() + "님의 요청을 " + request.model()
                + " 모델로 처리했습니다. message = " + request.message();

        return new ChatResponse(
                request.userId(),
                request.model(),
                answer,
                Thread.currentThread().toString(),
                TraceContext.currentTraceId()
        );
    }
}
