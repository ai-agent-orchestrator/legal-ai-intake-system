package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.IntakeEvent;
import com.ohgiraffers.handlermethod.legal.IntakeEventType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeEventRepository extends JpaRepository<IntakeEvent, String> {
    long countByEventType(IntakeEventType eventType);
}
