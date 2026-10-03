package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.bulletinboard.model.Profile;

import jakarta.persistence.LockModeType;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Optional<Profile> findByUserId(Long userId);

    /** 管理対応と通報受付が同じProfile行を直列に扱う。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p join fetch p.user where p.id = :id")
    Optional<Profile> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p join fetch p.user where p.id = :id")
    Optional<Profile> findByIdForReport(@Param("id") Long id);
}
