package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.LegalProcedure;
import com.ohgiraffers.handlermethod.legal.LegalProcedureDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalProcedureRepository extends JpaRepository<LegalProcedure, Long> {

    Optional<LegalProcedure> findByCode(String code);

    List<LegalProcedure> findByDomainOrderByCode(LegalProcedureDomain domain);
}
