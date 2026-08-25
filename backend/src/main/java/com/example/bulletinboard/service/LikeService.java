package com.example.bulletinboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.LikeRepository;

/**
 * 【クラスの役割】
 * 「いいね」機能に関するビジネスロジック
 * 「いいね」の切り替え（トグル：登録・削除）、「いいね」の数の判定、「いいね」の総数を担当する
 */


@Service
@Transactional(readOnly = true) //データベースのデータを読み込み（検索） 専用
public class LikeService {

    private final LikeRepository likeRepository;

    public LikeService(LikeRepository likeRepository){
      this.likeRepository = likeRepository;
    }


    //「いいね」のトグル処理（既にいいねをしていれば削除、していなければ保存）
    @Transactional
    public boolean toggleLike(User user, Post post){
        if(likeRepository.existsByUserAndPost(user,post)){
           likeRepository.deleteByUserAndPost(user,post);
           return false; //「いいね」が解除
        }else{
           likeRepository.save(new Like(user, post));
           return true;//「いいね」が登録
        }
    }

    //ログイン中のユーザーがこの投稿をすでにいいね済みかどうか判定
    public boolean isLikedByUser(User user, Post post){
      if(user == null || post == null){
         return false;
      }

      return likeRepository.existsByUserAndPost(user,post);
    }


    //対象の投稿に対する「いいね」の総数をカウント
    public long getLikeCount(Post post){
       return likeRepository.countByPost(post);
    }


}