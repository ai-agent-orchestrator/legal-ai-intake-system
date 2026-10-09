package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_event_history")
public class SecurityEventHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(nullable = false, length = 300)
    private String uri;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SecurityEventHistory() {
    }

    private SecurityEventHistory(String eventType,
                                 String method,
                                 String uri,
                                 String status,
                                 String username,
                                 LocalDateTime createdAt) {
        this.eventType = eventType;
        this.method = method;
        this.uri = uri;
        this.status = status;
        this.username = username;
        this.createdAt = createdAt;
    }

    public static SecurityEventHistory create(String eventType,
                                              String method,
                                              String uri,
                                              String status,
                                              String username) {
        return new SecurityEventHistory(
                eventType,
                method,
                uri,
                status,
                username,
                LocalDateTime.now()
        );
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
