package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.LegalIntake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface LegalIntakeRepository extends JpaRepository<LegalIntake, Long> {

    List<LegalIntake> findTop50ByOrderByCreatedAtDesc();
    List<LegalIntake> findTop50ByOwnerUsernameOrderByCreatedAtDesc(String username);
    List<LegalIntake> findTop50ByAssignedLawyerUsernameAndOwnerUsernameIsNotNullOrderByCreatedAtDesc(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LegalIntake i where i.id = :id")
    java.util.Optional<LegalIntake> findForUpdate(Long id);

    long countByStatus(String status);

    java.util.Optional<LegalIntake> findByOwnerUsernameAndIdempotencyKey(String ownerUsername, String idempotencyKey);

    @Query("select i.caseType as bucket, count(i) as total from LegalIntake i group by i.caseType order by i.caseType")
    List<IntakeDistribution> countByCaseType();

    @Query("select i.desiredNextStep as bucket, count(i) as total from LegalIntake i group by i.desiredNextStep order by i.desiredNextStep")
    List<IntakeDistribution> countByDesiredNextStep();

    @Query("select coalesce(i.preferredRegion, '지역 미정') as bucket, count(i) as total from LegalIntake i group by coalesce(i.preferredRegion, '지역 미정') order by bucket")
    List<IntakeDistribution> countByPreferredRegion();
}
