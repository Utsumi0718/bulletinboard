package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.ReportProfileSnapshot;

public interface ReportProfileSnapshotRepository extends JpaRepository<ReportProfileSnapshot, Long> {
}
