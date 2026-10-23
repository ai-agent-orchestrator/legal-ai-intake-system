package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.ConsultationMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationMemoRepository extends JpaRepository<ConsultationMemo, Long> {
    List<ConsultationMemo> findByIntakeIdOrderByCreatedAtDescIdDesc(Long intakeId);
}
