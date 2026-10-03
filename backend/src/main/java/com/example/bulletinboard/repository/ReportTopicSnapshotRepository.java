package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.ReportTopicSnapshot;

public interface ReportTopicSnapshotRepository extends JpaRepository<ReportTopicSnapshot, Long> {
}
