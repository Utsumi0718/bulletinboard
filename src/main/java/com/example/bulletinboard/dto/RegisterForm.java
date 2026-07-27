package com.example.bulletinboard.dto; //form（データ転送オブジェクト）クラスが所属するパッケージ

import jakarta.validation.constraints.NotNull; // 未入力（null）チェック用のアノテーションをインポート
import jakarta.validation.constraints.Pattern; // 正規表現（文字列形式）チェック用のアノテーションをインポート
import jakarta.validation.constraints.Size; // 文字数制限チェック用のアノテーションをインポート
import lombok.Data; // Getter/Setter等の定型コードを自動生成するLombokアノテーションをインポート

/**
 * 【クラス全体の役割】
 * 新規ユーザー登録画面のフォーム入力値を受け取り、画面用のバリデーション（入力チェック）を行うDTO（Data Transfer Object）クラスです。
 * データベース用のUserエンティティと切り離すことで、パスワードハッシュ化後の桁数エラー等を防ぎ、安全に入力値を管理します。
 */
@Data // Lombokのアノテーション。Getter/Setter、equals、hashCode、toString等を自動生成する
public class RegisterForm { // 画面の入力フォームと1対1で対応するフォーム用DTOクラス

    // --------------------------------------------------
    // ユーザー名（username）のバリデーション設定
    // --------------------------------------------------
    @NotNull(message = "{NotNull.user.username}") // ユーザー名が未入力（null）の場合にエラーメッセージを取得する設定
    @Size(min = 4, max = 10, message = "{Size.user.username}") // ユーザー名の文字数を「4文字以上10文字以内」に制限する設定
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$", message = "{Pattern.user.username}") // ユーザー名に「半角英字」と「半角数字」の両方が最低1文字含まれているか判定する設定
    private String username; // 画面から送信されたユーザー名を保持するフィールド

    // --------------------------------------------------
    // パスワード（password）のバリデーション設定
    // --------------------------------------------------
    @NotNull(message = "{NotNull.user.password}") // パスワードが未入力（null）の場合にエラーメッセージを取得する設定
    @Size(min = 8, max = 20, message = "{Size.user.password}") // パスワードの文字数を「8文字以上20文字以内」に制限する設定
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$", message = "{Pattern.user.password}") // パスワードに「半角英字」と「半角数字」の両方が最低1文字含まれているか判定する設定
    private String password; // 画面から送信されたパスワード（ハッシュ化前）を保持するフィールド
}