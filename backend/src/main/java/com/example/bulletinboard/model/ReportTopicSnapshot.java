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

/** 通報受付時のお題の文章と画像原本。対象編集・削除時も変更しない。 */
@Entity
@Table(name = "report_topic_snapshots")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportTopicSnapshot {
    @Id
    @Column(name = "report_id")
    private Long reportId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Lob
    @Column(name = "image_content", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] imageContent;

    public ReportTopicSnapshot(Report report, Topic topic, TopicImage image) {
        this.report = report;
        this.title = topic.getTitle();
        this.question = topic.getQuestion();
        this.contentType = image.getContentType();
        this.byteSize = image.getByteSize();
        this.imageContent = image.getContent();
    }

    public byte[] getImageContent() { return imageContent.clone(); }
}
