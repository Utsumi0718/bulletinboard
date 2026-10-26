package com.example.bulletinboard.service;
import com.example.bulletinboard.security.ActorIds;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * - 回答の論理削除
 *
 * 【設計上のポイント】
 * - 旧CommentServiceではPostに紐づくCommentを扱っていましたが、
 *   新しい設計ではTopicに紐づくAnswerを扱います。
 *
 * - 通常の一覧取得・ID取得では、
 *   deletedAtがNULLのAnswerのみを対象とします。
 *
 * - Answerの削除は投稿者本人またはROLE_ADMINのみ可能です。
 *
 * - Answerの削除は物理削除ではなく、
 *   deletedAtに削除日時を設定する論理削除方式を採用します。
 *
 * - Answerが存在しない、または論理削除済みの場合は、
 *   AnswerNotFoundExceptionを使用します。
 *
 * - 削除権限がない場合は、
 *   ForbiddenOperationExceptionを使用します。
 *
 */

@Service
public class AnswerService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final AnswerRepository answerRepository;

    /*
     * AnswerRepositoryをコンストラクタインジェクションします。
    */
    public AnswerService(
              AnswerRepository answerRepository
             ) {
        this.answerRepository = answerRepository;
    }

    /*
     * 回答を保存します。
     *
     * 新規投稿時にRepositoryのsave()を使用します。
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Answer saveAnswer(Answer answer) {
        var topic = TopicWriteLocks.lock(entityManager, answer.getTopic().getId(), true);
        answer.setTopic(topic);
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
     public void deleteAnswer(
        Long id,
        String actorId,
        boolean isAdmin) {

       lockParent(id, false);

      Answer answer = answerRepository
        .findReportableByIdForUpdate(id)
        .orElseThrow(
       () -> new AnswerNotFoundException(
        "この回答は存在しないか、削除されています。"
     )
      );
    boolean isOwner =
        answer.getUser().getId().equals(ActorIds.parse(actorId));

    if (!isOwner && !isAdmin) {
    throw new ForbiddenOperationException(
        "この回答を削除する権限がありません。"
    );
}

    answer.setDeletedAt(LocalDateTime.now());

    answerRepository.save(answer);
}

    private void lockParent(Long answerId, boolean requirePublic) {
        Long topic = answerRepository.findReportableTopicIdByAnswerId(answerId)
                .orElseThrow(() -> new AnswerNotFoundException("この回答は存在しないか、削除されています。"));
        TopicWriteLocks.lock(entityManager, topic, requirePublic);
    }
}
