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
@Table(name = "legal_form_template")
public class LegalFormTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procedure_id", nullable = false)
    private LegalProcedure procedure;

    @Column(nullable = false, unique = true, length = 80)
    private String formCode;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 80)
    private String fileType;

    @Column(nullable = false, length = 1000)
    private String usageNote;

    protected LegalFormTemplate() {
    }

    private LegalFormTemplate(LegalProcedure procedure,
                              String formCode,
                              String title,
                              String fileType,
                              String usageNote) {
        this.procedure = procedure;
        this.formCode = formCode;
        this.title = title;
        this.fileType = fileType;
        this.usageNote = usageNote;
    }

    public static LegalFormTemplate create(LegalProcedure procedure,
                                           String formCode,
                                           String title,
                                           String fileType,
                                           String usageNote) {
        return new LegalFormTemplate(procedure, formCode, title, fileType, usageNote);
    }

    public String getProcedureCode() {
        return procedure.getCode();
    }

    public String getFormCode() {
        return formCode;
    }

    public String getTitle() {
        return title;
    }

    public String getFileType() {
        return fileType;
    }

    public String getUsageNote() {
        return usageNote;
    }
}
