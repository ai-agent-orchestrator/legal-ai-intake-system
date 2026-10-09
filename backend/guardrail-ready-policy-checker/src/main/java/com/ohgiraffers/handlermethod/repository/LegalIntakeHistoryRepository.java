package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.LegalIntakeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalIntakeHistoryRepository extends JpaRepository<LegalIntakeHistory, Long> {

    List<LegalIntakeHistory> findTop50ByOrderByCreatedAtDesc();

    List<LegalIntakeHistory> findByReadinessLevelOrderByCreatedAtDesc(String readinessLevel);

    List<LegalIntakeHistory> findByDecisionOrderByCreatedAtDesc(String decision);
}
