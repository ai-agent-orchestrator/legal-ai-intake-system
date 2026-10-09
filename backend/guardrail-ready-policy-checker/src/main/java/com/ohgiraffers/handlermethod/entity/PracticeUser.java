package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "practice_user")
public class PracticeUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    protected PracticeUser() {
    }

    private PracticeUser(String name) {
        this.name = name;
    }

    public static PracticeUser create(String name) {
        return new PracticeUser(name);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
