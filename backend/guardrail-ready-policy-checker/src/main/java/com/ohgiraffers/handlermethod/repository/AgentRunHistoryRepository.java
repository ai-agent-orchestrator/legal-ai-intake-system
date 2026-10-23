package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.AgentRunHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunHistoryRepository extends JpaRepository<AgentRunHistory, Long> {
    java.util.List<AgentRunHistory> findByTraceIdIn(java.util.Collection<String> traceIds);
}
