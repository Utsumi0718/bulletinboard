package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/**
 * [クラスの役割]
 * 「いいね」情報を保持するための中間エンティティクラス。
 * ユーザー（User）と投稿（Post）の「多対多（ManyToMany）のリレーションを中間テーブル（likes）として管理します」
 * 同一ユーザーが同じ投稿に対して重複して「いいね」行えないように、user_id と post_id に対する複合ユニーク制約を定義しています。
 */
@Entity
@Table(name = "likes", uniqueConstraints = {
  // 一人のユーザーが同じ投稿に複数回「いいね」できないよう、複合ユニーク制約を設定
  @UniqueConstraint(columnNames = {"user_id", "post_id"})

})
@Getter
@Setter
@NoArgsConstructor

public class Like{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)//Idをデータベース側で自動的に割り当て
    private Long id;

    //多対一
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist //データーベースに保存する前に呼び出す
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    //コンストラクタ生成用
    public Like(User user, Post post){
     this.user = user;
     this.post = post;
    }


}