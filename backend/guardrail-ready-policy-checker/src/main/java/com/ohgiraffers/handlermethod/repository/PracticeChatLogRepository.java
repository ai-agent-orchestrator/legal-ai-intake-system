package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.PracticeChatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PracticeChatLogRepository extends JpaRepository<PracticeChatLog, Long> {

    @Query("select c from PracticeChatLog c join fetch c.user order by c.id")
    List<PracticeChatLog> findAllWithUser();
}
