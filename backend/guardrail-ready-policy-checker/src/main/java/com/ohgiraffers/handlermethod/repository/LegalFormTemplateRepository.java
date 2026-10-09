package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.LegalFormTemplate;
import com.ohgiraffers.handlermethod.entity.LegalProcedure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalFormTemplateRepository extends JpaRepository<LegalFormTemplate, Long> {

    List<LegalFormTemplate> findByProcedureOrderByFormCode(LegalProcedure procedure);

    Optional<LegalFormTemplate> findByFormCode(String formCode);
}
