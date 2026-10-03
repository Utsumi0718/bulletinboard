package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;

import jakarta.persistence.LockModeType;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterUserIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    @EntityGraph(attributePaths = "reporterUser")
    Page<Report> findByStatusAndReporterUserId(ReportStatus status, Long reporterUserId, Pageable pageable);

    @EntityGraph(attributePaths = "reporterUser")
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "reporterUser")
    Page<Report> findByReporterUserId(Long reporterUserId, Pageable pageable);

    @EntityGraph(attributePaths = "reporterUser")
    Page<Report> findAll(Pageable pageable);

    long countByReporterUserId(Long reporterUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);
}
