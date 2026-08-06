package com.example.bulletinboard.model;


import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "comments")
@Getter
@Setter

/*
*【クラスの役割】
*コメントの内容、投稿日時、および
*「どの投稿に対するコメントか」「誰が書いたコメントか」という情報を保持します
*/

public class Comment{

   @Id //主キーの指定
   @GeneratedValue(strategy = GenerationType.IDENTITY) //主キーの値をデータベース側に自動で採番させます。
    private Long id; //コメントのID

   @Column(nullable = false) //、データベースの列（カラム）に対してnullを許容しない
    private String content; //コメントの内容

   @ManyToOne //多対一の関係を示すアノテーション
   @JoinColumn(name = "post_id", nullable = false) //外部キーのカラム名を指定
    private Post post; //コメントが属する投稿


   @ManyToOne //多対一の関係を示すアノテーション
   @JoinColumn(name = "user_id", nullable = false) //外部キーのカラム名を指定
    private User user; //コメントを書いたユーザー

   @Column(name = "created_at", nullable = false, updatable = false) //作成日時のカラムを指定
    private LocalDateTime createdAt; //コメントの作成日時


    @Column(name = "updated_at", nullable = false, updatable = false) //更新日時のカラムを指定
    private LocalDateTime updatedAt; //コメントの更新日時


    //保存前の日時も自動設定
    @PrePersist //エンティティが永続化される前に呼び出されるメソッド

    protected void  onPrePersist() {
        this.createdAt = LocalDateTime.now(); //現在の日時を取得して設定
        this.updatedAt = LocalDateTime.now(); //現在の日時を取得して設定
    }

    //更新前の日時も自動設定

    @PreUpdate //エンティティが更新される前に呼び出されるメソッド
    protected void onPreUpdate() {
        this.updatedAt = LocalDateTime.now(); //現在の日時を取得して設定
    }


}