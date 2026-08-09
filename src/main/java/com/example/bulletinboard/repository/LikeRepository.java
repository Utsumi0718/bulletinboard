package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;

/**
 * [クラスの役割]
 * Like（いいね）エンティティのデータベース操作を担当するリポジトリインタフェース
 * Spring Data JPAを継承し、特定ユーザーおよび投稿に関する「いいね」の存在チェック、登録件数の取得、削除処理などのデータ操作機能を提供します
 */
public interface LikeRepository extends JpaRepository<Like, Long> {

//指定したユーザーと投稿の組み合わせで「いいね」が存在するか判定
boolean existsByUserAndPost(User user, Post post);

//指定したユーザーと投稿の「いいね」のデータを取得（削除用）
Optional<Like> findByUserAndPost(User user, Post post);

//指定した投稿に対する「いいね」の総数をカウント
long  countByPost(Post post);


//指定したユーザーと投稿の「いいね」を削除
void deleteByUserAndPost(User user, Post post);



}
