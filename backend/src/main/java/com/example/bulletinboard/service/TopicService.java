package com.example.bulletinboard.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Topic;
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
 * - 投稿者本人のみ編集可能、回答が付いた後は編集不可などの
 *   業務ルールについては、Topic / Answer主要機能の実装時に追加します。
 */
@Service
public class TopicService {

    private final TopicRepository topicRepository;

    /*
     * 1ページあたりに表示するお題の件数。
     */
    private static final int PAGE_SIZE = 5;

    /*
     * TopicRepositoryをコンストラクタインジェクションします。
     */
    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
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
     * DBからレコードそのものを削除するのではなく、
     * deletedAtに現在日時を設定します。
     */
    @Transactional
    public void deleteById(Long id) {

        Topic topic = topicRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "指定されたお題が存在しません。id=" + id
                )
            );

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
}