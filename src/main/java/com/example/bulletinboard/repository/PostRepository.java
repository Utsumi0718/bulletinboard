package com.example.bulletinboard.repository; // パッケージ宣言: このインターフェースが所属するグループ・フォルダ構造（リポジトリ層）を定義
import com.example.bulletinboard.model.Post;// インポート: データベース操作の対象となるエンティティクラス（Post）を読み込み
import org.springframework.data.jpa.repository.JpaRepository; // インポート: Spring Data JPAが提供する標準的なCRUD機能を持つ親インターフェースを読み込み
import org.springframework.stereotype.Repository; // インポート: このクラス/インターフェースがデータアクセス層であることを示すSpringのアノテーションを読み込み
/*
 * 【クラス（インターフェース）の役割】
 * データベースの `post` テーブルに対するデータ操作（CRUD: 保存・取得・更新・削除など）を担当するリポジトリインターフェース。
 * `JpaRepository` を継承することで、SQL文を直接記述することなく、Javaのオブジェクト操作を通じて
 * 安全かつ効率的にデータベースとのデータ送受信を行います。
 */
@Repository
// @Repository: このインターフェースをSpringのDIコンテナにデータベース操作用コンポーネントとして登録するアノテーション

public interface PostRepository extends JpaRepository<Post, Long> {
// インターフェース定義: Postエンティティを扱い、主キーの型が Long である JpaRepository を継承

}
// PostRepositoryインターフェースの定義終了