package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.User;

/*
 * 【インターフェース全体の役割】
 * データベースの「users」テーブルに対するデータ操作を担当する
 * Spring Data JPAのRepositoryインターフェースです。
 *
 * Userエンティティを対象として、
 * 保存・取得・更新・削除などの基本的なCRUD操作に加え、
 * ユーザー名・メールアドレスによる検索や、
 * ログイン失敗回数・アカウントロック状態の更新を行います。
 *
 * 【主な役割】
 * - Userの保存・取得・更新・削除
 * - ユーザー名の重複確認
 * - メールアドレスの重複確認
 * - ユーザー名によるUser取得
 * - メールアドレスによるUser取得
 * - ログイン失敗回数の加算・リセット
 * - ログイン失敗によるアカウントロック状態の変更
 *
 * 【設計上のポイント】
 * - usernameはサービス上で公開される一意なユーザー名として使用します。
 * - emailはログイン認証に使用する一意なメールアドレスとして使用します。
 * - 今後の認証ではemail + passwordによるログインを採用するため、
 *   ログイン失敗回数やアカウントロック状態の更新もemailを基準に行います。
 * - accountNonLockedはログイン失敗によるセキュリティロックを管理します。
 * - ACTIVE / FROZEN / WITHDRAWNなどのaccountStatusを利用した
 *   凍結・退会処理については、認証・アカウント管理機能の実装時に整理します。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * 指定したユーザー名がすでに登録されているか確認します。
     *
     * usernameは重複不可のため、
     * 主にユーザー登録時の重複チェックで使用します。
     */
    boolean existsByUsername(String username);

    /*
     * 指定したメールアドレスがすでに登録されているか確認します。
     *
     * emailも重複不可のため、
     * 主にユーザー登録時の重複チェックで使用します。
     */
    boolean existsByEmail(String email);

    /*
     * ユーザー名からUserを取得します。
     *
     * usernameを利用したプロフィール検索などで
     * 使用することを想定しています。
     */
    Optional<User> findByUsername(String username);

    /*
     * メールアドレスからUserを取得します。
     *
     * 今後のログイン認証ではemailを使用するため、
     * 認証処理の基本となる検索メソッドです。
     */
    Optional<User> findByEmail(String email);

    /*
     * 指定したメールアドレスのユーザーについて、
     * ログイン失敗回数を1増加させます。
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE User u
        SET u.failedAttempt = u.failedAttempt + 1
        WHERE u.email = :email
        """)
    void updateFailedAttempts(@Param("email") String email);

    /*
     * 指定したメールアドレスのユーザーについて、
     * ログイン失敗回数を0に戻します。
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE User u
        SET u.failedAttempt = 0
        WHERE u.email = :email
        """)
    void resetFailedAttempts(@Param("email") String email);

    /*
     * 指定したメールアドレスのユーザーについて、
     * ログイン失敗によるアカウントロック状態を変更します。
     *
     * true  : ロックされていない
     * false : ロックされている
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE User u
        SET u.accountNonLocked = :nonLocked
        WHERE u.email = :email
        """)
    void updateAccountNonLocked(
        @Param("email") String email,
        @Param("nonLocked") boolean nonLocked
    );
}