package com.example.bulletinboard.repository;
import com.example.bulletinboard.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;
import java.util.List;

/*
 * 【クラス（インターフェース）の役割】
 * データベースの `post` テーブルに対するデータ操作（CRUD: 保存・取得・更新・削除など）を担当するリポジトリインターフェース。
 * `JpaRepository` を継承することで、SQL文を直接記述することなく、Javaのオブジェクト操作を通じて
 * 安全かつ効率的にデータベースとのデータ送受信を行います。
 * 【追記】
 * 検索条件（部分一致・前方一致・後方一致）を選べる検索機能」のためのメソッドを追加
 */
@Repository
// @Repository: このインターフェースをSpringのDIコンテナにデータベース操作用コンポーネントとして登録するアノテーション

public interface PostRepository extends JpaRepository<Post, Long> {
// インターフェース定義: Postエンティティを扱い、主キーの型が Long である JpaRepository を継承

//部分一致（Containing）: タイトルまたは本文にキーワードを含むメソッド
 List<Post> findByTitleContainingOrContentContaining(String title, String content, Sort sort);

//前方一致（StartingWith）: タイトルまたは本文がキーワードで始まるメソッド
 List<Post> findByTitleStartingWithOrContentStartingWith(String title, String content, Sort sort);

 //後方一致 (EndingWith): タイトルまたは本文がキーワードで終わる
 List<Post> findByTitleEndingWithOrContentEndingWith(String title, String content, Sort sort);
}
// PostRepositoryインターフェースの定義終了