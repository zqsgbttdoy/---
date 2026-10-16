package com.supervision.repository;

import com.supervision.entity.TokenRevocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TokenRevocationRepository extends JpaRepository<TokenRevocation, String> {

    void deleteByExpiresAtBefore(LocalDateTime time);
}
