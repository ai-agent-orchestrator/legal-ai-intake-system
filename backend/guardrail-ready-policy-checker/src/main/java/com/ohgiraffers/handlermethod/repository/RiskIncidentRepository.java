package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.incident.IncidentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiskIncidentRepository extends JpaRepository<RiskIncident, Long> {

    List<RiskIncident> findTop50ByOrderByCreatedAtDesc();

    long countByStatus(IncidentStatus status);

    /* ack/resolve가 동시에 들어와도 순서대로 처리되도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RiskIncident i where i.id = :id")
    Optional<RiskIncident> findForUpdate(@Param("id") Long id);
}
