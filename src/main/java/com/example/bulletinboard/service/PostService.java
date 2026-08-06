package com.example.bulletinboard.service;
// パッケージ宣言: このクラスが所属するグループ・フォルダ構造（サービス層）を定義

import com.example.bulletinboard.model.Post;
// インポート: 扱うデータモデルである Post エンティティを読み込み

import com.example.bulletinboard.repository.PostRepository;
// インポート: データベース操作を担う PostRepository インターフェースを読み込み

import org.springframework.stereotype.Service;
// インポート: ビジネスロジック層のコンポーネントであることを示す Spring のアノテーションを読み込み

import java.util.List;
// インポート: 複数件のデータを扱うための Java 標準 List コレクションを読み込み

import java.util.Optional;
// インポート: 値が null になる可能性があることを安全に扱うための Optional クラスを読み込み

/*
 * 【クラスの役割】
 * 掲示板システムの「ビジネスロジック（業務処理）」を担うサービス層のクラスです。
 * コントローラー層からの要求を受け取り、`PostRepository` を介してデータベースとのデータ疎通を行い、
 * 投稿一覧の取得・個別取得・新規保存・削除などの具体的な処理を取りまとめます。
 * 【追記】
 * キーワードと一致条件を指定して投稿を検索するメソッドを追加
 * @param keyword 検索キーワード
 * @param matchType 一致条件 (contains, starts, ends)
 * @return 検索結果のリスト
 */
@Service
// @Service: このクラスを Spring の DI コンテナにビジネスロジック担当のコンポーネントとして登録するアノテーション

public class PostService {
// PostService クラスの定義開始

    private final PostRepository postRepository;
    // フィールド定義: データベース操作を行うリポジトリの参照を保持。final を指定して変更不可（不変）に設定

    // コンストラクタで Repository クラスをインジェクション
    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }


    // 投稿一覧を取得するメソッド
    public List<Post> findAll() {
     return postRepository.findAll();
   }

    // 投稿をIDで取得するメソッド
    public Optional<Post> findById(Long id) {
     return postRepository.findById(id);
    }

    // 新規投稿を保存するメソッド
    public Post save(Post post) {

        return postRepository.save(post);
    }

    // IDで投稿を削除するメソッド
    public void deleteById(Long id) {
     postRepository.deleteById(id);
    }

    // キーワードと一致条件を指定して投稿を検索するメソッド
    public List<Post> searchPosts(String keyword, String matchType){
      //matchType に応じて検索条件を切り替える
      return switch (matchType){
       case "starts" -> postRepository.findByTitleStartingWithOrContentStartingWith(keyword,keyword);
       case "ends" -> postRepository.findByTitleEndingWithOrContentEndingWith(keyword,keyword);
       default -> postRepository.findByTitleContainingOrContentContaining(keyword,keyword); //デフォルトは部分一致
    };
}

}
