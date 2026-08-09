package com.example.bulletinboard.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「users」テーブル（ユーザー情報）と1対1で対応するJPAエンティティクラスです。
 * ユーザー名とハッシュ化されたパスワードなどの認証情報を保持し、オブジェクトとしてプログラム内で扱えるようにします。
 *
  * 【追記・補足ポイント】
 * - データベース制約（ユーザー名の必須・一意性、パスワードの必須）をJavaコード側で宣言しています。
 * - 主キー（ID）はデータベース側の自動採番（Auto Increment等）に委ねる設計になっています。
 * - Lombok（@Data）を利用することで、定型コードを自動生成し記述を簡略化しています。
 * - 【重要】他エンティティとの双方向リレーションを設定する場合は、無限ループ（スタックオーバーフロー）を
 *   防止するため、@Data から @Getter / @Setter への切り替えを推奨します。

 */
@Entity // このクラスがJPAのエンティティ（DBのテーブルとマッピングされるオブジェクト）であることを宣言
@Getter//@Dataから変更
@Setter//@Dataから変更
@Table(name = "users")//テーブル名を明示的に指定
public class User {

    @Id // このフィールド（id）がテーブルの主キー（Primary Key）であることを指定
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 主キーの値をデータベース側（MySQLのAUTO_INCREMENTなど）で自動割り振りする設定
    private Long id; // ユーザーを一意に識別するためのID

    //ユーザー名（username）のバリデーション・DB定義
    @NotNull //必須チェック（null不可）
    @Column(nullable = false, unique = true) // DBのカラム設定：NOT NULL（必須）かつ UNIQUE（重複不可）制約を付与
    private String username; // ログイン等に使用するユーザー名

    //パスワード（password）のバリデーション・DB定義
    @NotNull //必須チェック（null不可）
    @Column(nullable = false) // DBのカラム設定：NOT NULL（必須）制約を付与
    private String password; // ハッシュ化されたパスワード文字列を保持するフィールド

    // ログイン失敗回数（初期値 0）
    @Column(nullable = false)
    private int failedAttempt = 0;

    // アカウントがロックされていないか（true: 通常 / false: ロック中、初期値 true）
    @Column(nullable = false)
    private boolean accountNonLocked = true;

     // 👇 追加：1人のユーザーは複数のコメントを持つ（1対多）
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>(); // Null回避のため初期化 [3]

    // ユーザー削除時に、その人が押したいいねデータも自動で消去されます
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Like> likes = new ArrayList<>();

}