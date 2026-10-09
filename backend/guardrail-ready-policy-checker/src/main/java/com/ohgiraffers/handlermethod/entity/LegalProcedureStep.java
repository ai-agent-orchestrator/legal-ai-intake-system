package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "legal_procedure_step")
public class LegalProcedureStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procedure_id", nullable = false)
    private LegalProcedure procedure;

    @Column(nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 80)
    private String stepCode;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 1000)
    private String checklist;

    protected LegalProcedureStep() {
    }

    private LegalProcedureStep(LegalProcedure procedure,
                               int stepOrder,
                               String stepCode,
                               String title,
                               String description,
                               String checklist) {
        this.procedure = procedure;
        this.stepOrder = stepOrder;
        this.stepCode = stepCode;
        this.title = title;
        this.description = description;
        this.checklist = checklist;
    }

    public static LegalProcedureStep create(LegalProcedure procedure,
                                            int stepOrder,
                                            String stepCode,
                                            String title,
                                            String description,
                                            String checklist) {
        return new LegalProcedureStep(procedure, stepOrder, stepCode, title, description, checklist);
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getStepCode() {
        return stepCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getChecklist() {
        return checklist;
    }
}
