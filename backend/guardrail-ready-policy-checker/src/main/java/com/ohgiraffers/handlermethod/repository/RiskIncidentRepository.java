package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskIncidentRepository extends JpaRepository<RiskIncident, Long> {

    List<RiskIncident> findTop50ByOrderByCreatedAtDesc();

    long countByStatus(IncidentStatus status);
}
