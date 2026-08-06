package com.example.bulletinboard.service;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(postRepository.findByTitleContainingOrContentContaining("Java", "Java"))
        .thenReturn(List.of(new Post()));

       //実行
       List<Post> results = postService.searchPosts("Java","contains");

       //検証:正しいリポジトリメソッドが１回呼ばれたか
       assertThat(results).hasSize(1);
       verify(postRepository, times(1)).findByTitleContainingOrContentContaining("Java", "Java");

    }

    @Test
    @DisplayName("検索キーワードが前方のキーワードに一致している場合の検索結果の検証")
     void test_PrefixMatch() {
        // 1. 準備 [7: 1274]
        when(postRepository.findByTitleStartingWithOrContentStartingWith("Spring", "Spring"))
            .thenReturn(List.of(new Post()));

        // 2. 実行
        List<Post> results = postService.searchPosts("Spring", "starts");

        // 3. 検証
        assertThat(results).hasSize(1);
        verify(postRepository, times(1)).findByTitleStartingWithOrContentStartingWith("Spring", "Spring");
    }

    @Test
    @DisplayName("検索キーワードが後方のキーワードに一致している場合の検索結果の検証")
    void test_SuffixMatch(){
        // 1. 準備：後方一致メソッドが呼ばれたら空リストを返すよう設定 [7: 1274]
        when(postRepository.findByTitleEndingWithOrContentEndingWith("です", "です"))
            .thenReturn(List.of(new Post()));

        // 2. 実行：matchTypeに "ends" を指定
        List<Post> results = postService.searchPosts("です", "ends");

        // 3. 検証：正しいリポジトリメソッド（EndingWith）が1回呼ばれたか [7: 1267, 1274]
        assertThat(results).hasSize(1);
        verify(postRepository, times(1)).findByTitleEndingWithOrContentEndingWith("です", "です");
    }

}