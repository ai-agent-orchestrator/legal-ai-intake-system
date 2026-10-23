package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "lawyer_search_condition")
public class LawyerSearchCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long intakeId;

    @Column(nullable = false, length = 100)
    private String legalField;

    @Column(nullable = false, length = 150)
    private String disputeType;

    @Column(length = 100)
    private String preferredRegion;

    @Column(length = 100)
    private String officeType;

    @Column(length = 100)
    private String experienceLevel;

    @Column(length = 150)
    private String publicCareerPreference;

    @Column(length = 150)
    private String consultationStyle;

    @Column(nullable = false, length = 100)
    private String sourcePolicy;

    @Column(nullable = false, length = 500)
    private String searchQuery;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected LawyerSearchCondition() {
    }

    private LawyerSearchCondition(Long intakeId,
                                  String legalField,
                                  String disputeType,
                                  String preferredRegion,
                                  String officeType,
                                  String experienceLevel,
                                  String publicCareerPreference,
                                  String consultationStyle,
                                  String sourcePolicy,
                                  String searchQuery) {
        this.intakeId = intakeId;
        this.legalField = legalField;
        this.disputeType = disputeType;
        this.preferredRegion = preferredRegion;
        this.officeType = officeType;
        this.experienceLevel = experienceLevel;
        this.publicCareerPreference = publicCareerPreference;
        this.consultationStyle = consultationStyle;
        this.sourcePolicy = sourcePolicy;
        this.searchQuery = searchQuery;
        this.createdAt = LocalDateTime.now();
    }

    public static LawyerSearchCondition create(Long intakeId,
                                               String legalField,
                                               String disputeType,
                                               String preferredRegion,
                                               String officeType,
                                               String experienceLevel,
                                               String publicCareerPreference,
                                               String consultationStyle,
                                               String sourcePolicy,
                                               String searchQuery) {
        return new LawyerSearchCondition(
                intakeId,
                legalField,
                disputeType,
                preferredRegion,
                officeType,
                experienceLevel,
                publicCareerPreference,
                consultationStyle,
                sourcePolicy,
                searchQuery
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIntakeId() {
        return intakeId;
    }

    public String getLegalField() {
        return legalField;
    }

    public String getDisputeType() {
        return disputeType;
    }

    public String getPreferredRegion() {
        return preferredRegion;
    }

    public String getOfficeType() {
        return officeType;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public String getPublicCareerPreference() {
        return publicCareerPreference;
    }

    public String getConsultationStyle() {
        return consultationStyle;
    }

    public String getSourcePolicy() {
        return sourcePolicy;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
