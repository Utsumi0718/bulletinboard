package com.example.bulletinboard.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.like.LikeResponse;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.LikeConflictException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.LikeRepository;

/**
 * 【クラスの役割】
 * Answerに対するLike機能の業務処理を管理するService。
 *
 * ログインユーザーと対象Answerを取得し、
 * Answer・Topicの公開状態や自己Like禁止などの
 * 業務ルールを確認した上で、Likeの追加・解除を行う。
 *
 * また、現在のLike状態の確認やLike件数の取得も担当する。
 *
 * Like登録時の重複はService側で制御するとともに、
 * DBのUNIQUE制約による競合も検知する。
 */
@Service
@Transactional(readOnly = true)
public class LikeService {

    private final LikeRepository likeRepository;
    private final AnswerRepository answerRepository;
    private final CustomUserDetailsService customUserDetailsService;

    public LikeService(
            LikeRepository likeRepository,
            AnswerRepository answerRepository,
            CustomUserDetailsService customUserDetailsService) {

        this.likeRepository = likeRepository;
        this.answerRepository = answerRepository;
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
      * 指定されたAnswerに対するLike状態を切り替える。
      *
      * loginEmailからログインユーザーを取得し、
      * answerIdから論理削除されていないAnswerを取得する。
      *
      * 親Topicが論理削除済みの場合や、
      * ログインユーザー自身が投稿したAnswerの場合は
      * Like操作を拒否する。
      *
      * 未Likeの場合はLikeを追加し、
      * Like済みの場合はLikeを解除する。
      * 処理完了後は最新のLike状態とLike件数を返す。
      *
      * @param answerId   対象AnswerのID
      * @param loginEmail ログインユーザーのメールアドレス
      * @return toggle後のLike状態と最新Like件数を持つLikeResponse
      * @throws UserNotFoundException       ログインユーザーを取得できない場合
      * @throws AnswerNotFoundException     Answerが存在しない、論理削除済み、
      *                                     または親Topicが論理削除済みの場合
      * @throws ForbiddenOperationException 自分自身のAnswerへLikeしようとした場合
      * @throws LikeConflictException       Like登録時に重複登録競合が発生した場合
      */
    @Transactional
    public LikeResponse toggleLike(Long answerId, String loginEmail) {

        User user = customUserDetailsService.findByEmail(loginEmail)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "ユーザーが見つかりません。"
                        )
                );

        Answer answer = answerRepository
                .findByIdAndDeletedAtIsNull(answerId)
                .orElseThrow(() ->
                        new AnswerNotFoundException(
                                "この回答は存在しないか、削除されています。"
                        )
                );

        // 親Topicが論理削除済みの場合はLike不可
        if (answer.getTopic().getDeletedAt() != null) {
            throw new AnswerNotFoundException(
                    "この回答は存在しないか、削除されています。"
            );
        }

        // 自分自身のAnswerへのLikeは禁止
        if (Objects.equals(
                answer.getUser().getId(),
                user.getId())) {

            throw new ForbiddenOperationException(
                    "自分の回答にはいいねできません。"
            );
        }

        Optional<Like> existingLike =
                likeRepository.findByUserAndAnswer(user, answer);

        boolean liked;

        if (existingLike.isPresent()) {

            // Like済みの場合は解除
            likeRepository.delete(existingLike.get());
            liked = false;

        } else {

            // 未Likeの場合は追加
            try {
                likeRepository.saveAndFlush(
                        new Like(user, answer)
                );
                liked = true;

            } catch (DataIntegrityViolationException ex) {
                throw new LikeConflictException(
                        "この回答にはすでにいいねしています。"
                );
            }
        }

        long likeCount = likeRepository.countByAnswer(answer);

        return new LikeResponse(liked, likeCount);
    }

    /**
      * 指定されたUserが対象AnswerをLikeしているか確認する。
      *
      * @param user   Like状態を確認するUser
      * @param answer 対象Answer
      * @return Like済みの場合true、未Likeの場合false
      */
   public boolean isLikedByUser(User user, Answer answer) {

    return likeRepository.existsByUserAndAnswer(
            user,
            answer
    );
   }

/**
 * 指定されたAnswerの現在のLike件数を取得する。
 *
 * @param answer 対象Answer
 * @return 現在のLike件数
 */
public long getLikeCount(Answer answer) {

    return likeRepository.countByAnswer(answer);
}
}