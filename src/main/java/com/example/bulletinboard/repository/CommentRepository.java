package com.example.bulletinboard.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.bulletinboard.model.Comment;

/*
 * 【インターフェースの役割】
 * コメントデータ（Comment）に対するデータベース操作を担うSpring Data JPAのリポジトリです。
 * JpaRepositoryを継承することで、基本的なCRUD（作成・読込・更新・削除）機能を提供します。
 */

public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 特定の投稿（postId）に紐づくコメントをすべて取得するメソッド [1]
    List<Comment> findByPostId(Long postId);
}