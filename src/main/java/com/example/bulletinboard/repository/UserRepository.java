package com.example.bulletinboard.repository;

import com.example.bulletinboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPAの基本リポジトリ機能を読み込み
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional; // 値が存在しない可能性がある（nullの可能性がある）場合に使用するラッパー型

/*
 * 【インターフェース全体の役割】
 * データベースの「users」テーブル（Userエンティティ）に対するデータ操作（CRUD処理）を担当するリポジトリ層のインターフェースです。
 *
 * - 実際には `extends JpaRepository<User, Long>` を継承させて宣言します。
 *   これにより、SQLを手動で書くことなく、保存(save)・削除(delete)・全件取得(findAll)などの標準機能が自動で使えるようになります。
 * - `findByUsername` のようにメソッド名を命名ルールに従って記述するだけで、Spring Data JPAが「ユーザー名で検索するSQL」を自動生成してくれます。
 */

// ※通常は JpaRepository<User, Long> を継承（extends）させて定義します
public interface UserRepository extends JpaRepository<User, Long> {

    // ユーザー名を指定してDBからユーザー情報を1件検索するカスタムメソッド
    // （一致するユーザーがいない場合に安全に処理できるよう Optional で包んでいます）
    Optional<User> findByUsername(String username);

    // 失敗回数を +1 加算する
    @Modifying
    @Query("UPDATE User u SET u.failedAttempt = u.failedAttempt + 1 WHERE u.username = :username")
    void updateFailedAttempts(String username);

    // 失敗回数をリセット（0に戻す）
    @Modifying
    @Query("UPDATE User u SET u.failedAttempt = 0 WHERE u.username = :username")
    void resetFailedAttempts(String username);

    // アカウントのロック状態を変更する
    @Modifying
    @Query("UPDATE User u SET u.accountNonLocked = :nonLocked WHERE u.username = :username")
    void updateAccountNonLocked(String username, boolean nonLocked);

}