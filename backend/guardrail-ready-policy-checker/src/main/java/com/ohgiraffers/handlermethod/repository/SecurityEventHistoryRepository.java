package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.SecurityEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityEventHistoryRepository extends JpaRepository<SecurityEventHistory, Long> {

    List<SecurityEventHistory> findTop50ByOrderByCreatedAtDesc();
}
