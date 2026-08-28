package com.example.bulletinboard.repository;

import java.util.List;

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
 * - AnswerのIDによる取得
 * - Answer一覧の取得
 * - Answerの更新・削除
 * - 特定のお題に紐づく回答一覧の取得
 *
 * 【設計上のポイント】
 * - 旧CommentRepositoryではpostIdを基準にコメントを取得していましたが、
 *   新しい設計ではAnswerがTopicに紐付くため、
 *   topicIdを基準に回答を取得します。
 * - AnswerはdeletedAtを利用した論理削除方式を採用するため、
 *   実際の画面表示では削除済みAnswerを除外する処理を
 *   今後Service層またはRepository側で追加する予定です。
 * - 1つのお題には複数の回答が存在できるため、
 *   戻り値はList<Answer>としています。
 */
@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    /*
     * 指定したTopicに紐づく回答をすべて取得します。
     *
     * Answerエンティティのtopic.idを条件として検索します。
     */
    List<Answer> findByTopicId(Long topicId);
}