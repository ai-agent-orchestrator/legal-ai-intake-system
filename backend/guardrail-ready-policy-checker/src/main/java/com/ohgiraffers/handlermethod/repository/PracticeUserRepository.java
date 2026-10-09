package com.ohgiraffers.handlermethod.repository;

import com.ohgiraffers.handlermethod.entity.PracticeUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeUserRepository extends JpaRepository<PracticeUser, Long> {
}
