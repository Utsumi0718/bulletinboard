package com.example.bulletinboard.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.AnswerEditConflictException;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.repository.AnswerRepository;

/*
 * 【クラスの役割】
 * 大喜利サービスの「回答（Answer）」に関する
 * ビジネスロジックを担当するServiceクラスです。
 *
 * Controllerなどの上位層から要求を受け取り、
 * AnswerRepositoryを通してanswersテーブルへアクセスします。
 *
 * 【主な役割】
 * - 回答の保存
 * - 特定のお題に紐づく回答一覧の取得
 * - 回答のIDによる取得
 * - 回答の編集
 * - 回答の論理削除
 *
 * 【設計上のポイント】
 * - 旧CommentServiceではPostに紐づくCommentを扱っていましたが、
 *   新しい設計ではTopicに紐づくAnswerを扱います。
 *
 * - 通常の一覧取得・ID取得では、
 *   deletedAtがNULLのAnswerのみを対象とします。
 *
 * - Answerの編集は投稿者本人のみ可能です。
 *
 * - Likeが1件でも付いているAnswerは編集できません。
 *
 * - Answerの削除は投稿者本人またはROLE_ADMINのみ可能です。
 *
 * - Answerの削除は物理削除ではなく、
 *   deletedAtに削除日時を設定する論理削除方式を採用します。
 *
 * - Answerが存在しない、または論理削除済みの場合は、
 *   AnswerNotFoundExceptionを使用します。
 *
 * - 編集・削除権限がない場合は、
 *   ForbiddenOperationExceptionを使用します。
 *
 * - Likeが付いていて編集できない場合は、
 *   AnswerEditConflictExceptionを使用します。
 */

@Service
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final LikeService likeService;

    /*
     * AnswerRepositoryとLikeServiceをコンストラクタインジェクションします。
    */
    public AnswerService(
              AnswerRepository answerRepository,
              LikeService likeService
             ) {
        this.answerRepository = answerRepository;
        this.likeService = likeService;
    }

    /*
     * 回答を保存します。
     *
     * 新規登録・更新のどちらでもRepositoryのsave()を使用します。
     */
    @Transactional
    public Answer saveAnswer(Answer answer) {
        return answerRepository.save(answer);
    }

    /*
     * 指定したTopicに紐づく、
     * 削除されていない回答一覧を取得します。
     */
    @Transactional(readOnly = true)
    public List<Answer> getAnswersByTopicId(Long topicId) {
        return answerRepository.findByTopicIdAndDeletedAtIsNull(topicId);
    }

    /*
     * 指定したIDの回答を取得します。
     *
     * 論理削除済みの回答は通常の取得対象に含めません。
     */
    @Transactional(readOnly = true)
    public Optional<Answer> getAnswerById(Long id) {
        return answerRepository.findByIdAndDeletedAtIsNull(id);
    }


    /*
     * 指定されたAnswerを論理削除します。
     *
     * 削除できるのは、
     * Answerの投稿者本人またはROLE_ADMINのユーザーです。
     *
     * Answerが存在しない、または論理削除済みの場合は
     * AnswerNotFoundExceptionを発生させます。
     *
     * 投稿者本人でもROLE_ADMINでもない場合は
     * ForbiddenOperationExceptionを発生させます。
     *
     * 削除時はDBからレコードそのものを削除せず、
    * deletedAtに現在日時を設定します。
    */

    @Transactional
     public void deleteAnswer(
        Long id,
        String loginEmail,
        boolean isAdmin) {

      Answer answer = answerRepository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(
       () -> new AnswerNotFoundException(
        "この回答は存在しないか、削除されています。"
     )
      );
    boolean isOwner =
        answer.getUser().getEmail().equals(loginEmail);

    if (!isOwner && !isAdmin) {
    throw new ForbiddenOperationException(
        "この回答を削除する権限がありません。"
    );
}

    answer.setDeletedAt(LocalDateTime.now());

    answerRepository.save(answer);
}

    /*
     * 指定されたAnswerを編集します。
     *
     * 編集できるのはAnswerの投稿者本人のみです。
     * また、Likeが1件でも付いているAnswerは編集できません。
     *
     * 条件を満たした場合のみcontentを更新します。
     */
@Transactional
    public Answer updateAnswer(
        Long answerId,
        String loginEmail,
        String content) {

    Answer answer = answerRepository
        .findByIdAndDeletedAtIsNull(answerId)
       .orElseThrow(
        () -> new AnswerNotFoundException(
        "この回答は存在しないか、削除されています。"
        )
      );

    if (!answer.getUser().getEmail().equals(loginEmail)) {
    throw new ForbiddenOperationException(
        "この回答を編集する権限がありません。"
    );
    }

    if (likeService.getLikeCount(answer) > 0) {
    throw new AnswerEditConflictException(
        "いいねが付いている回答は編集できません。"
    );
  }

    answer.setContent(content);

    return answerRepository.save(answer);
  }
}