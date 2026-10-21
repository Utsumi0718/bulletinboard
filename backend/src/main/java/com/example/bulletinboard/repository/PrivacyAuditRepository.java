package com.example.bulletinboard.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.bulletinboard.model.PrivacyAuditEvent;
public interface PrivacyAuditRepository extends JpaRepository<PrivacyAuditEvent,Long> { }
