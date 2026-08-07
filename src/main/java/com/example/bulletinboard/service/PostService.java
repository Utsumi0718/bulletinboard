package com.example.bulletinboard.service;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;

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
 * [追記]
 * コントローラーから受け取った sortBy（ソート項目）と sortOrder（ソート順）をもとに、Spring Data JPA の Sort オブジェクトを生成し、リポジトリ層へ引き渡す処理を追加
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


    // 投稿一覧を取得するメソッド(並び替え対応)
    public List<Post> findAll(String sortBy, String sortOrder) {
     Sort sort = createSort(sortBy,sortOrder);
     return postRepository.findAll(sort);
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

    // キーワードと一致条件を指定して投稿を検索するメソッド(並び替え対応)
    public List<Post> searchPosts(String keyword, String matchType, String sortBy, String sortOrder) {
        Sort sort = createSort(sortBy, sortOrder);

        // matchTypeがnullの場合の対策
        String type = (matchType == null) ? "contains" : matchType;

        return switch (type) {
            case "starts" -> postRepository.findByTitleStartingWithOrContentStartingWith(keyword, keyword, sort); // 🔍 keywordを2つ渡す
            case "ends"   -> postRepository.findByTitleEndingWithOrContentEndingWith(keyword, keyword, sort);     // 🔍 keywordを2つ渡す
            default       -> postRepository.findByTitleContainingOrContentContaining(keyword, keyword, sort);   // 🔍 keywordを2つ渡す
        };
    }

    //追加：（共通の処理）並び変えの用のSortオブジェクトを生成するヘルパーメソッド
    private Sort createSort(String sortBy, String sortOrder){
       //デフォルトの値
       if(sortBy == null || sortBy.isEmpty()){
         sortBy = "createdAt";
       }

       //昇順・降順の判定
       if("asc".equalsIgnoreCase(sortOrder)){
         return Sort.by(Sort.Direction.ASC, sortBy);
       }else{
        return Sort.by(Sort.Direction.DESC, sortBy);
       }

 }

}
