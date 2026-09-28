package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.TopicImage;

public interface TopicImageRepository extends JpaRepository<TopicImage, String> {
    // 本体を読み込まずに所有者を確認する。
    boolean existsByIdAndOwnerUserId(String id, Long ownerUserId);
}
