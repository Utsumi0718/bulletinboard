package com.example.bulletinboard.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.LikeRepository;

import static org.junit.jupiter.api.Assertions.*;


/**
 * 【クラスの役割】
 * LikeService（いいね機能のビジネスロジック）の単体テストクラス。
 * データベース等の外部依存をモック（模擬オブジェクト）化し、
 * 「未登録時のいいね保存」および「登録済み時のいいね解除」のトグル処理が正しく動作するかを検証する。
 */
@ExtendWith(MockitoExtension.class)
public class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testPost = new Post();
        testPost.setId(10L);
        testPost.setTitle("テストタイトル");
    }

    @Test
    @DisplayName("未いいねの状態でtoggleLikeを実行すると、新規登録（save）されること")
    void toggleLike_WhenNotLiked_ShouldSaveLike() {
        // Given: まだいいねしていない状態（existsがfalseを返す）
        when(likeRepository.existsByUserAndPost(testUser, testPost)).thenReturn(false);

        // When: トグル処理を実行
       boolean result = likeService.toggleLike(testUser, testPost);

        // Then: saveが1回呼ばれ、deleteは呼ばれないこと
        assertTrue(result);
         verify(likeRepository, times(1)).save(any(Like.class));
         verify(likeRepository, never()).deleteByUserAndPost(any(), any());
    }

    @Test
    @DisplayName("既にいいね済みの状態でtoggleLikeを実行すると、解除（delete）されること")
    void toggleLike_WhenAlreadyLiked_ShouldDeleteLike() {
        // Given: 既にいいねが存在する状態
        Like existingLike = new Like();
        existingLike.setUser(testUser);
        existingLike.setPost(testPost);
         when(likeRepository.existsByUserAndPost(testUser, testPost)).thenReturn(true);

         // When: トグル処理を実行
       boolean result = likeService.toggleLike(testUser, testPost);
        // Then: deleteが1回呼ばれ、saveは呼ばれないこと
        assertFalse(result); // 💡 いいね解除なので結果は false になるはず
        verify(likeRepository, times(1)).deleteByUserAndPost(testUser, testPost);
        verify(likeRepository, never()).save(any()); // ⭕ saveは呼ばれない
    }
}