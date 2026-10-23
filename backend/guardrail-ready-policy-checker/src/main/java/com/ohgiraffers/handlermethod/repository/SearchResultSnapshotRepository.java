package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.SearchResultSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchResultSnapshotRepository extends JpaRepository<SearchResultSnapshot, Long> {

    List<SearchResultSnapshot> findBySearchRequestIdOrderByDisplayOrder(Long searchRequestId);
}
