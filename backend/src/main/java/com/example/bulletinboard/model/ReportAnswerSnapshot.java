package com.example.bulletinboard.model;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 回答本文と、通報時に公開されていた親お題の文章・画像原本。 */
@Entity
@Table(name = "report_answer_snapshots")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportAnswerSnapshot {
    @Id
    @Column(name = "report_id")
    private Long reportId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(name = "answer_content", nullable = false, length = 200)
    private String answerContent;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "topic_title", nullable = false, length = 100)
    private String topicTitle;

    @Column(name = "topic_question", nullable = false, length = 200)
    private String topicQuestion;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Lob
    @Column(name = "image_content", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] imageContent;

    public ReportAnswerSnapshot(Report report, Answer answer, Topic topic, TopicImage image) {
        this.report = report;
        this.answerContent = answer.getContent();
        this.topicId = topic.getId();
        this.topicTitle = topic.getTitle();
        this.topicQuestion = topic.getQuestion();
        this.contentType = image.getContentType();
        this.byteSize = image.getByteSize();
        this.imageContent = image.getContent();
    }

    public byte[] getImageContent() { return imageContent.clone(); }
}
