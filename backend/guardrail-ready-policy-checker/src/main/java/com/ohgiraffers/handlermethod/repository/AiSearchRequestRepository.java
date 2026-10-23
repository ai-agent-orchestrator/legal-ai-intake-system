package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.AiSearchRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AiSearchRequestRepository extends JpaRepository<AiSearchRequest, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AiSearchRequest r where r.id = :id")
    java.util.Optional<AiSearchRequest> findForExecution(@org.springframework.data.repository.query.Param("id") Long id);

    List<AiSearchRequest> findByStatusIn(java.util.Collection<String> statuses);
    List<AiSearchRequest> findByIntakeIdOrderByCreatedAtDesc(Long intakeId);

    List<AiSearchRequest> findByIntakeIdOrderByCreatedAtDescIdDesc(Long intakeId);

    @Query("select count(distinct r.intakeId) from AiSearchRequest r")
    long countDistinctIntakes();
}
