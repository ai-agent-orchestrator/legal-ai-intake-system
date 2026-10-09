package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.AgentJobHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentJobHistoryRepository extends JpaRepository<AgentJobHistory, Long> {

    List<AgentJobHistory> findTop50ByOrderByCreatedAtDesc();
}
