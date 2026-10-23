package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.IntakeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntakeDocumentRepository extends JpaRepository<IntakeDocument, Long> {

    List<IntakeDocument> findByIntakeIdOrderById(Long intakeId);
}
