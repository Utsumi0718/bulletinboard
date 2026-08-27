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

/*
 * 【クラス全体の役割】
 * データベースの「likes」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、ユーザーが大喜利の回答に対して行う
 * 「いいね」情報を管理します。
 *
 * 【主な役割】
 * - どのユーザーがいいねを行ったかを保持する
 * - どの回答に対するいいねかを保持する
 * - いいねが作成された日時を保持する
 *
 * 【設計上のポイント】
 * - LikeはUserと多対1の関係です。
 *   1人のユーザーは複数の回答にいいねできます。
 * - LikeはAnswerとも多対1の関係です。
 *   1つの回答には複数ユーザーからいいねが付くことがあります。
 * - 同じユーザーが同じ回答に複数回いいねできないように、
 *   user_idとanswer_idの組み合わせにUNIQUE制約を設定します。
 * - 自分自身の回答へのいいね禁止などの業務ルールは、
 *   DB制約ではなくService層でチェックする予定です。
 * - UserやAnswerが削除された際にLikeを自動的に連鎖削除する
 *   CascadeType.ALLやorphanRemoval=trueは使用しません。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "likes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_likes_user_answer",
            columnNames = {"user_id", "answer_id"}
        )
    }
)
public class Like {

    /*
     * いいねID。
     * likesテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * いいねを行ったユーザー。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * いいねの対象となる回答。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    /*
     * いいねを行った日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * Likeを初めてDBへ保存する直前に呼び出され、
     * createdAtが未設定の場合は現在日時を設定します。
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /*
     * いいね作成時にUserとAnswerを指定するためのコンストラクタです。
     */
    public Like(User user, Answer answer) {
        this.user = user;
        this.answer = answer;
    }
}