package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.example.bulletinboard.model.PasswordResetToken;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @org.springframework.data.jpa.repository.Query("select t.user.id from PasswordResetToken t where t.tokenHash=:hash")
    Optional<Long> findOwnerId(@org.springframework.data.repository.query.Param("hash") String hash);
    Optional<PasswordResetToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
