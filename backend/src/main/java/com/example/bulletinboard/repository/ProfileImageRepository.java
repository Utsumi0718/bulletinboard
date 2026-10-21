package com.example.bulletinboard.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.bulletinboard.model.ProfileImage;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, String> {
    java.util.List<ProfileImage> findByOwnerUserId(Long userId);
    @org.springframework.data.jpa.repository.Query("select i.id from ProfileImage i where i.ownerUserId=:id")
    java.util.List<String> findIdsByOwner(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<ProfileImage> findByIdAndOwnerUserId(String id, Long ownerUserId);
}
