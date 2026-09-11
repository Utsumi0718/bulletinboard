package com.example.bulletinboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.Answer;

/*
 * 【インターフェースの役割】
 * データベースの「answers」テーブルに対するデータ操作を担当する
 * Spring Data JPAのRepositoryインターフェースです。
 *
 * Answerエンティティを対象として、
 * 回答の保存・取得・更新・削除などの基本的なCRUD操作を行います。
 *
 * 【主な役割】
 * - Answerの保存
 * - 削除されていないAnswerのIDによる取得
 * - 特定のお題に紐づく削除されていない回答一覧の取得
 *
 * 【設計上のポイント】
 * - AnswerはTopicに紐付きます。
 * - deletedAtを利用した論理削除方式を採用しているため、
 *   通常の一覧取得や個別取得では削除済みAnswerを除外します。
 * - 1つのお題には複数の回答が存在できるため、
 *   Topicに紐づく回答一覧の戻り値はList<Answer>としています。
 */
@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    /*
     * 指定したTopicに紐づく、
     * 削除されていない回答を取得します。
     *
     * Answer一覧表示ではusernameを使用するため、
     * EntityGraphでUserも同時取得し、
     * AnswerごとのUser取得によるN+1を防ぎます。
     */
     @EntityGraph(attributePaths = "user")
     List<Answer> findByTopicIdAndDeletedAtIsNull(Long topicId);

     /*
     * 指定したIDかつ削除されていない回答を取得します。
     */
    Optional<Answer> findByIdAndDeletedAtIsNull(Long id);

    /*
     * 指定したTopicにAnswerが一度でも投稿されたことがあるか確認します。
     *
     * Topicは一度でもAnswerが投稿された後は編集不可とするため、
     * 論理削除済みAnswerも含めて存在判定します。
     */
    boolean existsByTopicId(Long topicId);
}