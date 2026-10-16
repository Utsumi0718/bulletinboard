package com.example.bulletinboard.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.bulletinboard.model.ProfileImage;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, String> {
    Optional<ProfileImage> findByIdAndOwnerUserId(String id, Long ownerUserId);
}
