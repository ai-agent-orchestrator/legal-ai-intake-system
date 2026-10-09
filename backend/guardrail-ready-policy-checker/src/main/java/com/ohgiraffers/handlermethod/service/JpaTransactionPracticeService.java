package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.PracticeMenuCreateRequest;
import com.ohgiraffers.handlermethod.dto.PracticeMenuResponse;
import com.ohgiraffers.handlermethod.dto.PracticeMenuUpdateRequest;
import com.ohgiraffers.handlermethod.entity.PracticeMenu;
import com.ohgiraffers.handlermethod.repository.PracticeMenuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JpaTransactionPracticeService {

    private final PracticeMenuRepository practiceMenuRepository;

    public JpaTransactionPracticeService(PracticeMenuRepository practiceMenuRepository) {
        this.practiceMenuRepository = practiceMenuRepository;
    }

    public PracticeMenuResponse create(PracticeMenuCreateRequest request) {
        PracticeMenu menu = PracticeMenu.create(request.name(), request.price());
        PracticeMenu savedMenu = practiceMenuRepository.save(menu);

        return PracticeMenuResponse.from(savedMenu, "created by repository.save()");
    }

    @Transactional(readOnly = true)
    public PracticeMenuResponse find(Long id) {
        PracticeMenu menu = findMenu(id);

        return PracticeMenuResponse.from(menu, "current database value");
    }

    @Transactional
    public PracticeMenuResponse goodUpdate(Long id, PracticeMenuUpdateRequest request) {
        PracticeMenu menu = findMenu(id);
        menu.changeName(request.name());

        return PracticeMenuResponse.from(menu, "GOOD: changed inside @Transactional. Dirty Checking will update DB.");
    }

    public PracticeMenuResponse badUpdate(Long id, PracticeMenuUpdateRequest request) {
        PracticeMenu menu = findMenu(id);
        menu.changeName(request.name());

        return PracticeMenuResponse.from(menu, "BAD: response object changed, but DB will not be updated by Dirty Checking.");
    }

    private PracticeMenu findMenu(Long id) {
        return practiceMenuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "menu not found"));
    }
}
