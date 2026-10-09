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
@Table(name = "practice_chat_log")
public class PracticeChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_user_id", nullable = false)
    private PracticeUser user;

    protected PracticeChatLog() {
    }

    private PracticeChatLog(String message, PracticeUser user) {
        this.message = message;
        this.user = user;
    }

    public static PracticeChatLog create(String message, PracticeUser user) {
        return new PracticeChatLog(message, user);
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public PracticeUser getUser() {
        return user;
    }
}
