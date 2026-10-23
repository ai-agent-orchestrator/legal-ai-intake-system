package com.ohgiraffers.handlermethod.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_result_snapshot")
public class SearchResultSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long searchRequestId;

    @Column(nullable = false, length = 100)
    private String sourceType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 1000)
    private String snippet;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 36) private String executionId;
    @Column(length = 2000) private String explanation;
    public static SearchResultSnapshot collected(Long requestId, String sourceType, String title, String url, String snippet, int order, String executionId) {
        SearchResultSnapshot result = create(requestId, sourceType, title, url, snippet, order);
        result.executionId = executionId; return result;
    }
    public String getExecutionId() { return executionId; }
    public String getExplanation() { return explanation; }
    public void explain(String text) { explanation = text; }

    protected SearchResultSnapshot() {
    }

    private SearchResultSnapshot(Long searchRequestId,
                                 String sourceType,
                                 String title,
                                 String url,
                                 String snippet,
                                 int displayOrder) {
        this.searchRequestId = searchRequestId;
        this.sourceType = sourceType;
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.displayOrder = displayOrder;
        this.createdAt = LocalDateTime.now();
    }

    public static SearchResultSnapshot create(Long searchRequestId,
                                              String sourceType,
                                              String title,
                                              String url,
                                              String snippet,
                                              int displayOrder) {
        return new SearchResultSnapshot(searchRequestId, sourceType, title, url, snippet, displayOrder);
    }

    public Long getId() {
        return id;
    }

    public Long getSearchRequestId() {
        return searchRequestId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getSnippet() {
        return snippet;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
