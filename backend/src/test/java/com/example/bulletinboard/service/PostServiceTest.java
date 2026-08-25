package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;

/**
 * PostService（掲示板投稿サービス）の単体テストクラス。
 * 【主な役割】
 * 投稿の検索（部分一致・前方一致・後方一致）、ソート処理、およびページネーションの補正ロジックを検証します。
 * Mockito を用いて PostRepository をモック化し、データベースへアクセスせずに
 * Service 層の制御ロジックやリポジトリへ引き渡される Pageable オブジェクトが正しいかを判定します。
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("検索キーワードが部分的に一致している場合の検索結果の検証")
    void test_PartialMatch() {
        // ① 変数や初期値の設定：テスト用パラメータとモックの返却用データ（1件のダミーページ）を用意
        Page<Post> dummyPage = new PageImpl<>(List.of(new Post()));
        when(postRepository.findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Pageable.class)))
            .thenReturn(dummyPage);

        // ② 実行順に沿った処理：第1引数に page (0) を渡し、keyword="Java", matchType="contains" で呼び出し
        Page<Post> results = postService.searchPosts(0, "Java", "contains", "createdAt", "desc");

        // ③ 特殊な動作：モック化した postRepository の「部分一致メソッド」が意図通り1回呼ばれたか検証
        verify(postRepository, times(1)).findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Pageable.class));

        // ④ 最終的な出力の決定：返却された検索結果リストの件数が 1 件であることを確認
        assertThat(results.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("検索キーワードが前方のキーワードに一致している場合の検索結果の検証")
    void test_PrefixMatch() {
        // ① 変数や初期値の設定
        Page<Post> dummyPage = new PageImpl<>(List.of(new Post()));
        when(postRepository.findByTitleStartingWithOrContentStartingWith(eq("Spring"), eq("Spring"), any(Pageable.class)))
            .thenReturn(dummyPage);

        // ② 実行順に沿った処理：第1引数に page (0) を指定して呼び出し
        Page<Post> results = postService.searchPosts(0, "Spring", "starts", "createdAt", "desc");

        // ③ 特殊な動作：前方一致メソッド（StartingWith）が正しく選択されて呼び出されたか検証
        verify(postRepository, times(1)).findByTitleStartingWithOrContentStartingWith(eq("Spring"), eq("Spring"), any(Pageable.class));

        // ④ 最終的な出力の決定
        assertThat(results.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("検索キーワードが後方のキーワードに一致している場合の検索結果の検証")
    void test_SuffixMatch() {
        // ① 変数や初期値の設定
        Page<Post> dummyPage = new PageImpl<>(List.of(new Post()));
        when(postRepository.findByTitleEndingWithOrContentEndingWith(eq("です"), eq("です"), any(Pageable.class)))
            .thenReturn(dummyPage);

        // ② 実行順に沿った処理：第1引数に page (0) を指定して呼び出し
        Page<Post> results = postService.searchPosts(0, "です", "ends", "createdAt", "desc");

        // ③ 特殊な動作：後方一致メソッド（EndingWith）が選択されたか検証
        verify(postRepository, times(1)).findByTitleEndingWithOrContentEndingWith(eq("です"), eq("です"), any(Pageable.class));

        // ④ 最終的な出力の決定
        assertThat(results.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("ソート順に asc を指定した場合、昇順（ASC）の Pageable オブジェクトがリポジトリに渡されること")
    void test_SortAscending() {
        // ① 変数や初期値の設定：リポジトリへ渡された Pageable を横取り（キャプチャ）するためのキャプチャ器を用意
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Post> dummyPage = new PageImpl<>(List.of(new Post()));

        when(postRepository.findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Pageable.class)))
            .thenReturn(dummyPage);

        // ② 実行順に沿った処理：第1引数に page (0) を渡し、sortOrder="asc" を指定して実行
        postService.searchPosts(0, "Java", "contains", "createdAt", "asc");

        // ③ 特殊な動作（キャプチャ）：引数として渡された Pageable オブジェクトを ArgumentCaptor で横取りする
        verify(postRepository).findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), pageableCaptor.capture());

        // ④ 最終的な出力の決定：キャプチャした Pageable 内のソート順が Direction.ASC になっていることを確かめる
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("負のページ番号（-1など）が渡された場合、自動的に0ページ目に補正されること")
    void test_NegativePageNumber() {
        // ① 変数や初期値の設定：不正なページ数 -1 や検証用 ArgumentCaptor を準備
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Post> dummyPage = new PageImpl<>(List.of(new Post()));

        when(postRepository.findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Pageable.class)))
            .thenReturn(dummyPage);

        // ② 実行順に沿った処理：第1引数に不正なページ番号（page = -1）を渡して検索処理を呼び出す
        postService.searchPosts(-1, "Java", "contains", "createdAt", "desc");

        // ③ 特殊な動作（ガード処理の検証）：内部で Math.max(0, page) による自動安全補正が働く
        verify(postRepository).findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), pageableCaptor.capture());

        // ④ 最終的な出力の決定：実際にリポジトリに渡された Pageable のページ数が -1 ではなく 0 に補正されていることを判定
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
    }
}