package com.example.bulletinboard.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.TopicRepository;

/*
 * 【クラス全体の役割】
 * 大喜利サービスの「お題（Topic）」に関する
 * ビジネスロジックを担当するServiceクラスです。
 *
 * Controllerなどの上位層から要求を受け取り、
 * TopicRepositoryを通してtopicsテーブルへアクセスします。
 *
 * 【主な役割】
 * - お題一覧の取得
 * - お題のIDによる取得
 * - お題の保存
 * - お題の編集
 * - お題タイトルの部分一致検索
 * - ページネーション
 * - 並び替え
 * - お題の論理削除
 *
 * 【設計上のポイント】
 * - 旧PostServiceではタイトルと本文に対して、
 *   部分一致・前方一致・後方一致検索を行っていました。
 * - 新しいTopicでは、検索対象を「お題タイトル」のみに限定し、
 *   検索方式も部分一致検索のみとします。
 * - Topicの削除はRepositoryのdelete()を使用した物理削除ではなく、
 *   deletedAtに削除日時を設定する論理削除方式を採用します。
 * - 通常の一覧取得・ID取得・検索では、
 *   deletedAtがNULLのTopicのみを対象とします。
 * - Topicの編集は投稿者本人のみ可能です。
 * - Answerが一度でも投稿されたTopicは編集できません。
 *   論理削除済みのAnswerも存在判定に含めます。
 * - Topicの削除は投稿者本人またはROLE_ADMINのみ可能です。
 */
@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final AnswerRepository answerRepository;


    /*
     * 1ページあたりに表示するお題の件数。
     */
    private static final int PAGE_SIZE = 10;

    /*
     * TopicRepositoryとAnswerRepositoryをコンストラクタインジェクションします。
     */
    public TopicService(
        TopicRepository topicRepository,
        AnswerRepository answerRepository) {

    this.topicRepository = topicRepository;
    this.answerRepository = answerRepository;
   }

    /*
     * 削除されていないお題をページ単位で取得します。
     *
     * ページネーションと並び替えに対応します。
     */
    public Page<Topic> findAll(
            int page,
            String sortBy,
            String sortOrder) {

        Pageable pageable = createPageable(
            page,
            sortBy,
            sortOrder
        );

        return topicRepository.findByDeletedAtIsNull(pageable);
    }

    /*
     * 指定されたIDのお題を取得します。
     *
     * 論理削除済みのお題は通常の取得対象に含めません。
     */
    public Optional<Topic> findById(Long id) {
        return topicRepository.findByIdAndDeletedAtIsNull(id);
    }

    /**
      * REST APIの詳細取得用として、
      * 指定されたIDのTopicを取得します。
      *
      * Topicが存在しない、または論理削除済みの場合は
      * TopicNotFoundExceptionを投げます。
    */
    public Topic getById(Long id) {
       return topicRepository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(
            () -> new TopicNotFoundException(
                "このお題は存在しないか、削除されています。"
            )
        );
    }

    /*
     * Topicを保存します。
     *
     * 新規登録・更新のどちらでもRepositoryのsave()を使用します。
     */
    @Transactional
    public Topic save(Topic topic) {
        return topicRepository.save(topic);
    }

    /*
     * 指定されたキーワードを使って、
     * お題タイトルを部分一致検索します。
     *
     * 論理削除済みのお題は検索結果から除外します。
     */
    public Page<Topic> searchTopics(
            int page,
            String keyword,
            String sortBy,
            String sortOrder) {

        Pageable pageable = createPageable(
            page,
            sortBy,
            sortOrder
        );

        return topicRepository
            .findByTitleContainingAndDeletedAtIsNull(
                keyword,
                pageable
            );
    }

    /*
     * 指定されたTopicを論理削除します。
     *
     * 削除できるのは、Topicの投稿者本人またはROLE_ADMINのユーザーです。
     * その他のユーザーによる削除は許可しません。
     *
     * 削除時はDBからレコードそのものを削除せず、
     * deletedAtに現在日時を設定します。
     */
     @Transactional
     public void deleteById(
        Long id,
        String loginEmail,
        boolean isAdmin) {

    Topic topic = topicRepository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(
         () -> new TopicNotFoundException(
            "このお題は存在しないか、削除されています。"
        )
        );

    boolean isOwner =
        topic.getUser().getEmail().equals(loginEmail);

    if (!isOwner && !isAdmin) {
        throw new IllegalStateException(
            "このお題を削除する権限がありません。"
        );
    }

    topic.setDeletedAt(LocalDateTime.now());

    topicRepository.save(topic);
}

    /*
     * ページネーションと並び替えに使用する
     * Pageableを生成する共通メソッドです。
     */
    private Pageable createPageable(
            int page,
            String sortBy,
            String sortOrder) {

        /*
         * 負のページ番号が渡された場合は、
         * 0ページ目として扱います。
         */
        int safePage = Math.max(0, page);

        /*
         * sortByが未指定の場合は、
         * 作成日時を基準に並び替えます。
         */
        String safeSortBy = normalizeSortBy(sortBy);

        /*
         * ascの場合のみ昇順、
         * それ以外は降順として扱います。
         */
        Sort.Direction direction =
            "asc".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(
            safePage,
            PAGE_SIZE,
            Sort.by(direction, safeSortBy)
        );
    }

    /*
     * 並び替え可能なフィールドを制限します。
     *
     * Controllerなどから不正なプロパティ名が渡されても
     * JPAのエラーにならないようにします。
     */
    private String normalizeSortBy(String sortBy) {

        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }

        return switch (sortBy) {
            case "createdAt" -> "createdAt";
            case "title" -> "title";
            default -> "createdAt";
        };
    }

    /**
     * 指定したTopicにAnswerが一度でも投稿されたことがあるか確認します。
     * 論理削除済みのAnswerも存在判定に含めます。
     */
    public boolean hasAnyAnswer(Long topicId) {
    return answerRepository.existsByTopicId(topicId);
   }

   /*
    *  指定されたTopicを編集します。
    *
    * 編集できるのはTopicの投稿者本人のみです。
    * また、Answerが一度でも投稿されたTopicは編集できません。
    * 論理削除済みのAnswerも「過去に回答が存在した」として判定します。
    *
    * Topicが存在しない、または論理削除済みの場合は、
    * TopicNotFoundExceptionを投げます。
    *
    * 条件を満たした場合のみ、
    * title、image、questionを更新します。
   */

   @Transactional
public Topic updateTopic(
        Long topicId,
        String loginEmail,
        String title,
        String image,
        String question) {

    Topic topic = topicRepository
            .findByIdAndDeletedAtIsNull(topicId)
            .orElseThrow(() ->
                    new TopicNotFoundException(
                            "このお題は存在しないか、削除されています。"
                        )

            );




    if (!topic.getUser().getEmail().equals(loginEmail)) {
        throw new IllegalStateException(
            "このお題を編集する権限がありません。"
        );
    }

    if (hasAnyAnswer(topicId)) {
        throw new IllegalStateException(
            "回答が投稿されたお題は編集できません。"
        );
    }

    topic.setTitle(title);
    topic.setImage(image);
    topic.setQuestion(question);

    return topicRepository.save(topic);
}
}