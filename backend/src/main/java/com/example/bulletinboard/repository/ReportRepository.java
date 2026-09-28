package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportTargetType;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterUserIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
}
