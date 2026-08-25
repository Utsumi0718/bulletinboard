package com.example.bulletinboard.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Comment;
import com.example.bulletinboard.repository.CommentRepository;

/*
 * 【クラスの役割】
 * コメント機能に関するビジネスロジック（業務処理）を制御するサービスクラスです。
 * コメントリポジトリ（CommentRepository）を介して、特定の投稿に紐づくコメント一覧の取得、
 * 個別コメントの検索、新規投稿、および削除などの処理を安全に実行・管理します。
 */


@Service
public class CommentService {
  private final CommentRepository commentRepository;

  //インジェクション
  public CommentService(CommentRepository commentRepository) {
    this.commentRepository = commentRepository;
  }

 //コメントを保存するメソッド
 @Transactional
 public Comment saveComment(Comment comment) {
    return commentRepository.save(comment);
  }

  //特定の投稿に紐づくコメント一覧を取得するメソッド
  @Transactional(readOnly = true)
  public List<Comment> getCommentsByPostId(Long postId) {
    return commentRepository.findByPostId(postId);
  }

  //コメントをIDで取得するメソッド
  @Transactional(readOnly = true)
  public Optional<Comment> getCommentById(Long id) {
    return commentRepository.findById(id);
  }

  //コメントを削除するメソッド
  @Transactional
  public void deleteComment(Long id) {
    commentRepository.deleteById(id);
  }

}
