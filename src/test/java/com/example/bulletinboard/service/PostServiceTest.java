package com.example.bulletinboard.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.mockito.ArgumentCaptor;


import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;




@ExtendWith(MockitoExtension.class)

class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("検索キーワードが部分的に一致している場合の検索結果の検証")
    void test_PartialMatch(){

        //部分一致メソッドが呼ばれたら空のリストを返す設定
        when(postRepository.findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Sort.class)))
        .thenReturn(List.of(new Post()));

       //実行
       List<Post> results = postService.searchPosts("Java","contains","createdAt","desc");

       //検証:正しいリポジトリメソッドが１回呼ばれたか
       assertThat(results).hasSize(1);
       verify(postRepository, times(1)).findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Sort.class));

    }

    @Test
    @DisplayName("検索キーワードが前方のキーワードに一致している場合の検索結果の検証")
     void test_PrefixMatch() {
        // 1. 準備 [7: 1274]
        when(postRepository.findByTitleStartingWithOrContentStartingWith(eq("Spring"), eq("Spring"), any(Sort.class)))
            .thenReturn(List.of(new Post()));

        // 2. 実行
        List<Post> results = postService.searchPosts("Spring", "starts","createAt","desc");

        // 3. 検証
        assertThat(results).hasSize(1);
        verify(postRepository, times(1)).findByTitleStartingWithOrContentStartingWith(eq("Spring"), eq("Spring"), any(Sort.class));
    }

    @Test
    @DisplayName("検索キーワードが後方のキーワードに一致している場合の検索結果の検証")
    void test_SuffixMatch(){
        // 1. 準備：後方一致メソッドが呼ばれたら空リストを返すよう設定 [7: 1274]
        when(postRepository.findByTitleEndingWithOrContentEndingWith(eq("です"), eq("です"), any(Sort.class)))
            .thenReturn(List.of(new Post()));

        // 2. 実行：matchTypeに "ends" を指定
        List<Post> results = postService.searchPosts("です", "ends", "createdAt", "desc");

        // 3. 検証：正しいリポジトリメソッド（EndingWith）が1回呼ばれたか [7: 1267, 1274]
        assertThat(results).hasSize(1);
        verify(postRepository, times(1)).findByTitleEndingWithOrContentEndingWith(eq("です"), eq("です"), any(Sort.class));
    }

    @Test
    @DisplayName("ソート順に asc を指定した場合、昇順（ASC）の Sort オブジェクトがリポジトリに渡されること")
    void test_SortAscending(){
        // 1. 準備：Sort オブジェクトを捕捉するための ArgumentCaptor を準備
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);

        when(postRepository.findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), any(Sort.class)))
            .thenReturn(List.of(new Post()));

        // 2. 実行：sortOrder に "asc" を渡して実行
        postService.searchPosts("Java", "contains", "createdAt", "asc");

        // 3. 検証：実際にリポジトリへ渡された Sort オブジェクトをキャプチャ
        verify(postRepository).findByTitleContainingOrContentContaining(eq("Java"), eq("Java"), sortCaptor.capture());

        Sort capturedSort = sortCaptor.getValue();

        // 渡された Sort が "createdAt" の "ASC" (昇順) になっていることを検証
        assertThat(capturedSort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

}