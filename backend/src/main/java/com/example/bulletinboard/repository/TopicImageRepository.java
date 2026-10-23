package com.example.bulletinboard.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.TopicImage;

public interface TopicImageRepository extends JpaRepository<TopicImage, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from TopicImage i where i.id = :id and i.ownerUserId = :owner")
    Optional<TopicImage> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("owner") Long owner);
    // 本体を読み込まずに所有者を確認する。
    boolean existsByIdAndOwnerUserId(String id, Long ownerUserId);

    Optional<TopicImage> findByIdAndOwnerUserId(String id, Long ownerUserId);
}
