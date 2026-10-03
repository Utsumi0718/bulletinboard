package com.example.bulletinboard.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.TopicImage;

public interface TopicImageRepository extends JpaRepository<TopicImage, String> {
    // 本体を読み込まずに所有者を確認する。
    boolean existsByIdAndOwnerUserId(String id, Long ownerUserId);

    Optional<TopicImage> findByIdAndOwnerUserId(String id, Long ownerUserId);
}
