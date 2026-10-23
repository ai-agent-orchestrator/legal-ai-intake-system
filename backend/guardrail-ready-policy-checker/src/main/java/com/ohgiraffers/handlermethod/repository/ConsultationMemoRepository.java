package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.ConsultationMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultationMemoRepository extends JpaRepository<ConsultationMemo, Long> {
    List<ConsultationMemo> findByIntakeIdOrderByCreatedAtDescIdDesc(Long intakeId);

    Optional<ConsultationMemo> findByIntakeIdAndRequestKey(Long intakeId, String requestKey);

    Optional<ConsultationMemo> findFirstByIntakeIdAndCreatedByAndContentHashAndCreatedAtAfterOrderByIdDesc(
            Long intakeId, String createdBy, String contentHash, LocalDateTime createdAfter);
}
