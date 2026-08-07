package com.example.bulletinboard.service;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
 * 【追記】
 * 1ページあたりの件数（例: 5件）とページ番号（page）を受け取れるようにし、PageRequest.of(...) を使って Pageable オブジェクトを生成・使用するように修正
 */
@Service
// @Service: このクラスを Spring の DI コンテナにビジネスロジック担当のコンポーネントとして登録するアノテーション

public class PostService {
// PostService クラスの定義開始

    private final PostRepository postRepository;

    //1ページあたりを表示する件数（変更可能）
    private static final int PAGE_SIZE = 5;

    // コンストラクタで Repository クラスをインジェクション
    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }


    // 投稿一覧を取得するメソッド(ページネーション・並び替え対応)
    public Page<Post> findAll(int page, String sortBy, String sortOrder) {
     Pageable pageable = createPageable(page, sortBy,sortOrder);
     return postRepository.findAll(pageable);
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

    // キーワードと一致条件を指定して投稿を検索するメソッド(並び替え対応・ページネーション対応)
    public Page<Post> searchPosts(int page, String keyword, String matchType, String sortBy, String sortOrder) {
        Pageable pageable  = createPageable(page, sortBy, sortOrder);

        // matchTypeがnullの場合の対策
        String type = (matchType == null) ? "contains" : matchType;

        return switch (type) {
            case "starts" -> postRepository.findByTitleStartingWithOrContentStartingWith(keyword, keyword, pageable); // 🔍 keywordを2つ渡す
            case "ends"   -> postRepository.findByTitleEndingWithOrContentEndingWith(keyword, keyword, pageable);     // 🔍 keywordを2つ渡す
            default       -> postRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable);   // 🔍 keywordを2つ渡す
        };
    }

    //追加：（共通の処理）ページネーションとソート用のPageableオブジェクトを生成するヘルパーメソッド
    private Pageable createPageable(int page, String sortBy, String sortOrder){
       //0未満の数値が渡された時には強制的に0ページ目にする安全策
       int safePage = Math.max(0, page);
       //デフォルトの値
       if(sortBy == null || sortBy.isEmpty()){
         sortBy = "createdAt";
       }

       //昇順・降順の判定
      Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

     return PageRequest.of(safePage, PAGE_SIZE, Sort.by(direction,sortBy));
 }

}
