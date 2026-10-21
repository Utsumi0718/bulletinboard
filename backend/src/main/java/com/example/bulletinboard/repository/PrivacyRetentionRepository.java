package com.example.bulletinboard.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.example.bulletinboard.model.PrivacyRetentionRecord;
import com.example.bulletinboard.model.PrivacyRetentionRecord.Target;

public interface PrivacyRetentionRepository extends JpaRepository<PrivacyRetentionRecord,Long> {
    List<PrivacyRetentionRecord> findByTargetTypeAndTargetId(Target type, String target);
    List<PrivacyRetentionRecord> findByUserId(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PrivacyRetentionRecord r where r.id=:id")
    Optional<PrivacyRetentionRecord> lock(@Param("id") Long id);
}
