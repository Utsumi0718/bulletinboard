package com.example.bulletinboard.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/*
 * 【クラス全体の役割】
 * データベースの「users」テーブル（ユーザー情報）と1対1で対応するJPAエンティティクラスです。
 * ユーザー名とハッシュ化されたパスワードなどの認証情報を保持し、オブジェクトとしてプログラム内で扱えるようにします。
 *
 * 【追記・補足ポイント】
 * - データベース制約（ユーザー名の必須・一意性、パスワードの必須）をJavaコード側で宣言しています。
 * - 主キー（ID）はデータベース側の自動採番（Auto Increment等）に委ねる設計になっています。
 * - Lombok（@Data）を利用することで、Getter/SetterやtoString等の定型コードを自動生成し、記述を簡略化しています。
 *
 */
@Entity // このクラスがJPAのエンティティ（DBのテーブルとマッピングされるオブジェクト）であることを宣言
@Data // Lombokのアノテーション。Getter/Setter、equals、hashCode、toString等を自動生成する
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

}