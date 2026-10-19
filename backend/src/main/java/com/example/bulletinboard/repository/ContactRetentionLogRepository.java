package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.bulletinboard.model.ContactRetentionLog;

public interface ContactRetentionLogRepository extends JpaRepository<ContactRetentionLog, Long> { }
