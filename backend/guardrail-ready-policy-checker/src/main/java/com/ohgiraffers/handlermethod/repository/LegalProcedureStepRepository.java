package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.LegalProcedure;
import com.ohgiraffers.handlermethod.entity.LegalProcedureStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalProcedureStepRepository extends JpaRepository<LegalProcedureStep, Long> {

    List<LegalProcedureStep> findByProcedureOrderByStepOrder(LegalProcedure procedure);
}
