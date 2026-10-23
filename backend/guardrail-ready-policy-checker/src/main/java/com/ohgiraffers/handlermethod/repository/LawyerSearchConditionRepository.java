package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.LawyerSearchCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LawyerSearchConditionRepository extends JpaRepository<LawyerSearchCondition, Long> {

    Optional<LawyerSearchCondition> findFirstByIntakeIdOrderByIdDesc(Long intakeId);
}
