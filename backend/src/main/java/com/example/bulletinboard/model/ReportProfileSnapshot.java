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

/** 通報時の公開プロフィール。退会や編集後も通報時の内容を保持する。 */
@Entity
@Table(name = "report_profile_snapshots")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportProfileSnapshot {
    @Id
    @Column(name = "report_id")
    private Long reportId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "bio", length = 300)
    private String bio;

    // アイコン未設定は両方NULL。設定済みなら参照でなく通報時の原本を保存する。
    @Column(name = "content_type", length = 30)
    private String contentType;

    @Lob
    @Column(name = "image_content", columnDefinition = "MEDIUMBLOB")
    private byte[] imageContent;

    public ReportProfileSnapshot(Report report, Profile profile) {
        this(report, profile, null);
    }

    public ReportProfileSnapshot(Report report, Profile profile, ProfileImage image) {
        this.report = report;
        this.displayName = profile.getUser().getUsername();
        this.bio = profile.getBio();
        if (image != null) {
            this.contentType = image.getContentType();
            this.imageContent = image.getContent();
        }
    }

    public byte[] getImageContent() {
        return imageContent == null ? null : imageContent.clone();
    }

    public boolean hasImage() { return imageContent != null && contentType != null; }
}
