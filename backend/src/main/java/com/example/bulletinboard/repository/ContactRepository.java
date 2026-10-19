package com.example.bulletinboard.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;
/*
 * 【クラス（インターフェース）の役割】
 * データベースの「contacts」テーブルに対するデータ操作を担当する
 * Spring Data JPAのRepositoryインターフェースです。
 *
 * Contactエンティティを対象として、
 * お問い合わせ情報の保存・取得・更新・削除などの
 * 基本的なCRUD操作を行います。
 *
 * 【主な役割】
 * - お問い合わせの保存
 * - お問い合わせのIDによる取得
 * - お問い合わせ一覧の取得
 * - お問い合わせ対応状況の更新
 * - ステータスを条件としたお問い合わせ一覧の取得
 * - ページネーションに対応した管理画面用一覧取得
 *
 * 【設計上のポイント】
 * - お問い合わせはログインユーザー・未ログインユーザーの
 *   どちらからでも送信できるため、Userとのリレーションは持ちません。
 * - statusにはUNANSWERED / IN_PROGRESS / RESOLVEDを想定しています。
 * - 管理画面では新しいお問い合わせを確認しやすいように、
 *   createdAtを基準とした並び替えをPageableから指定できます。
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Contact c where c.id = :id")
    java.util.Optional<Contact> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select c.id from Contact c where c.status = com.example.bulletinboard.model.ContactStatus.RESOLVED "
            + "and c.createdAt <= :cutoff and timestampadd(year, 1, c.createdAt) <= :now "
            + "and (c.legalHoldUntil is null or c.legalHoldUntil <= :now) order by c.id")
    java.util.List<Long> findExpiredIds(@org.springframework.data.repository.query.Param("cutoff") java.time.LocalDateTime cutoff,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now, Pageable pageable);

    /*
     * 指定したステータスのお問い合わせを取得します。
     *
     * Pageableを使用することで、
     * ページネーションやcreatedAtによる並び替えにも対応できます。
     */
    Page<Contact> findByStatus(
        ContactStatus status,
        Pageable pageable
   );
}
