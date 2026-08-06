package com.example.bulletinboard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts") //テーブル名を明示的に指定
@Getter
@Setter

/*
 * 【クラスの役割】
 * 掲示板システムにおける「投稿データ（Post）」を表現するドメインモデル（エンティティクラス）です。
 * データベースの `post` テーブル構造と1対1でマッピングされ、投稿のID、タイトル、本文、
 * および作成日時・更新日時の保持と自動タイムスタンプ管理を担います。
 * Post エンティティに「投稿者の情報」を保存する場所作成
 * 【追記・補足ポイント】
 * ・双方向リレーション（Post ↔ Comment など）を設定する場合、Lombokの @Data は使用せず、
 *   @Getter と @Setter に切り替えることが推奨されます。
 *   ※ @Data が自動生成する toString() や hashCode() が原因で、互いのデータを
 *      呼び出し続けて無限ループ（スタックオーバーフロー）が発生するのを防ぐためです。

 *
 */
public class Post{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)// IDをデータベース側で自動採番 [2, 3]
    private Long id;

    @NotBlank
    private String title; // 投稿のタイトル
    @NotBlank
    private String content; // 投稿の内容

    // 👇 ここを追加
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime createdAt; // 投稿の作成日時
    private LocalDateTime updatedAt; // 投稿の更新日時

    @PrePersist// データベースに保存する前に実行されるメソッド
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate// データベースに更新する前に実行されるメソッド
    protected void onUpdate(){
        updatedAt = LocalDateTime.now();
    }

    //追加：一つの投稿に対して複数のコメントが紐づく関係を定義
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();



}