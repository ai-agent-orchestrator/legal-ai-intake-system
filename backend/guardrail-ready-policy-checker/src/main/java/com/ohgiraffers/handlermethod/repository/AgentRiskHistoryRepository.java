package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRiskHistoryRepository extends JpaRepository<AgentRiskHistory, Long> {

    List<AgentRiskHistory> findTop50ByOrderByCreatedAtDesc();

    List<AgentRiskHistory> findByRiskLevelOrderByCreatedAtDesc(String riskLevel);

    List<AgentRiskHistory> findByDecisionOrderByCreatedAtDesc(String decision);
}
