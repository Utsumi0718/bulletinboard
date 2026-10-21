package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.bulletinboard.model.Profile;

import jakarta.persistence.LockModeType;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    boolean existsByIcon(String icon);
    Optional<Profile> findByUserId(Long userId);

    @Query("select p from Profile p join fetch p.user where p.id = :id")
    Optional<Profile> findWithUserById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p join fetch p.user where p.user.id = :userId")
    Optional<Profile> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select (count(p) > 0) from Profile p where p.icon = :icon and p.user.accountStatus <> :withdrawn")
    boolean existsVisibleIcon(@Param("icon") String icon,
            @Param("withdrawn") com.example.bulletinboard.model.AccountStatus withdrawn);

    /** 管理対応と通報受付が同じProfile行を直列に扱う。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p join fetch p.user where p.id = :id")
    Optional<Profile> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p join fetch p.user where p.id = :id")
    Optional<Profile> findByIdForReport(@Param("id") Long id);
}
