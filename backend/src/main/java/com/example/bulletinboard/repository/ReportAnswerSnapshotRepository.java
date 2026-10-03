package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.ReportAnswerSnapshot;

/** 回答通報時の文章と親お題の画像原本を保持する。 */
public interface ReportAnswerSnapshotRepository extends JpaRepository<ReportAnswerSnapshot, Long> { }
