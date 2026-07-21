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
 */
@Service
// @Service: このクラスを Spring の DI コンテナにビジネスロジック担当のコンポーネントとして登録するアノテーション

public class PostService {
// PostService クラスの定義開始

    private final PostRepository postRepository;
    // フィールド定義: データベース操作を行うリポジトリの参照を保持。final を指定して変更不可（不変）に設定

    // コンストラクタで Repository クラスをインジェクション
    public PostService(PostRepository postRepository) {
    // コンストラクタ定義: 外部（Spring）から PostRepository のインスタンスを受け取る

        this.postRepository = postRepository;
        // 渡された PostRepository のインスタンスを自身（this）のフィールドに設定
    }
    // コンストラクタの終了

    // 投稿一覧を取得するメソッド
    public List<Post> getAllPosts() {
    // getAllPostsメソッド: 戻り値として Post のリスト（List<Post>）を返す

        return postRepository.findAll();
        // リポジトリの findAll() を呼び出し、データベース内のすべての投稿データを取得してそのまま返す
    }
    // getAllPostsメソッドの終了

    // 投稿をIDで取得するメソッド
    public Optional<Post> findById(Long id) {
    // findByIdメソッド: 指定された id（Long型）を受け取り、検索結果を Optional<Post> 型で返す

        return postRepository.findById(id);
        // リポジトリの findById(id) を呼び出し、該当する投稿データを取得して返す（データがない場合は空の Optional）
    }
    // findByIdメソッドの終了

    // 新規投稿を保存するメソッド
    public Post savePost(Post post) {
    // savePostメソッド: 保存対象の Post オブジェクトを受け取り、保存後の Post オブジェクトを返す

        return postRepository.save(post);
        // リポジトリの save(post) を呼び出し、データベースへ保存（新規追加または更新）した結果を返す
    }
    // savePostメソッドの終了

    // IDで投稿を削除するメソッド
    public void deleteById(Long id) {
    // deleteByIdメソッド: 削除対象の id（Long型）を受け取り、戻り値なし（void）で処理を実行

        postRepository.deleteById(id);
        // リポジトリの deleteById(id) を呼び出し、該当する ID の投稿データをデータベースから削除する
    }
    // deleteByIdメソッドの終了

}
// PostService クラスの定義終了