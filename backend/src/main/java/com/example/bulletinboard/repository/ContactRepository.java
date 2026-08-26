package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.Contact;

/**
 * 【クラスの役割】
 * お問い合わせ情報（`Contact` エンティティ）に対するデータベース操作全般を担当する Repository インターフェース。
 * Spring Data JPA の `JpaRepository` を継承することで、SQLを直接記述することなく
 * 基本的な CRUD（登録・検索・更新・削除）処理を提供します。
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
}