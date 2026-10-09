package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.NPlusOneChatLogResponse;
import com.ohgiraffers.handlermethod.dto.NPlusOnePracticeResponse;
import com.ohgiraffers.handlermethod.entity.PracticeChatLog;
import com.ohgiraffers.handlermethod.entity.PracticeUser;
import com.ohgiraffers.handlermethod.repository.PracticeChatLogRepository;
import com.ohgiraffers.handlermethod.repository.PracticeUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class NPlusOnePracticeService {

    private final PracticeUserRepository practiceUserRepository;
    private final PracticeChatLogRepository practiceChatLogRepository;

    public NPlusOnePracticeService(PracticeUserRepository practiceUserRepository,
                                   PracticeChatLogRepository practiceChatLogRepository) {
        this.practiceUserRepository = practiceUserRepository;
        this.practiceChatLogRepository = practiceChatLogRepository;
    }

    @Transactional
    public NPlusOnePracticeResponse createSampleData() {
        practiceChatLogRepository.deleteAll();
        practiceUserRepository.deleteAll();

        List<PracticeChatLog> chatLogs = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            PracticeUser user = practiceUserRepository.save(PracticeUser.create("user-" + i));
            chatLogs.add(practiceChatLogRepository.save(
                    PracticeChatLog.create("message from user-" + i, user)
            ));
        }

        List<NPlusOneChatLogResponse> responses = chatLogs.stream()
                .map(NPlusOneChatLogResponse::from)
                .toList();

        return NPlusOnePracticeResponse.of(
                "sample-data",
                "created 5 users and 5 chat logs",
                responses
        );
    }

    @Transactional(readOnly = true)
    public NPlusOnePracticeResponse badFindAll() {
        List<PracticeChatLog> chatLogs = practiceChatLogRepository.findAll();

        List<NPlusOneChatLogResponse> responses = chatLogs.stream()
                .map(NPlusOneChatLogResponse::from)
                .toList();

        return NPlusOnePracticeResponse.of(
                "bad",
                "findAll() loads chat logs first, then lazy user access can trigger repeated SELECT queries.",
                responses
        );
    }

    @Transactional(readOnly = true)
    public NPlusOnePracticeResponse goodFindAll() {
        List<PracticeChatLog> chatLogs = practiceChatLogRepository.findAllWithUser();

        List<NPlusOneChatLogResponse> responses = chatLogs.stream()
                .map(NPlusOneChatLogResponse::from)
                .toList();

        return NPlusOnePracticeResponse.of(
                "good",
                "join fetch loads chat logs and users together, reducing repeated SELECT queries.",
                responses
        );
    }
}
