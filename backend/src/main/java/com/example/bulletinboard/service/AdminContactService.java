package com.example.bulletinboard.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.ContactNotFoundException;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.repository.ContactRepository;

/**
 * 【クラスの役割】
 * 管理者向けのお問い合わせ管理処理を担当するServiceです。
 *
 * 【現在の処理】
 * - IDによるお問い合わせの詳細取得
 * - 対象不存在時のContactNotFoundExceptionの送出
 * - ページングによるお問い合わせ一覧取得
 * - ステータスによる一覧の絞り込み
 * - ページ番号・取得件数の検証
 *
 * 【一覧取得のルール】
 * - ページ番号は0以上、取得件数は1～100とします。
 * - statusがnullの場合は全状態を取得します。
 * - statusが指定された場合は、その状態に絞り込みます。
 * - 作成日時の降順、同じ日時の場合はIDの降順で取得します。
 * - 入力条件が不正な場合はRepositoryを呼ばず、例外を送出します。
 *
 * 【役割分担】
 * HTTPリクエストの受付、省略時の値の設定、
 * DTOへの変換、HTTP応答はControllerで行います。
 * 管理APIへのアクセスはSecurityConfigでROLE_ADMINに制限します。
 *
 * 状態変更・削除・操作履歴の保存は、後続の作業で追加します。
 */

@Service
public class AdminContactService {

    private final ContactRepository contactRepository;

    /**
     * 一覧で指定できる最大ページサイズ。
     */
    public static final int MAX_PAGE_SIZE = 100;

    public AdminContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    /**
     * 指定されたお問い合わせを取得します。
     *
     * @param id お問い合わせID
     * @return 取得したお問い合わせ
     * @throws ContactNotFoundException 対象が存在しない場合
     */
    @Transactional(readOnly = true)
    public Contact getById(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(ContactNotFoundException::new);
    }


  /**
    * 管理者向けのお問い合わせ一覧をページ単位で取得します。
    *
    * statusがnullの場合は全状態を対象とし、
    * 指定されている場合はその状態に絞り込みます。
    *
    * 作成日時の降順、同じ日時の場合はIDの降順で取得します。
    *
    * @param page   ページ番号（0始まり）
    * @param size   1ページあたりの件数（1～100）
    * @param status 絞り込む状態。nullの場合は全状態
    * @return お問い合わせ一覧とページ情報
    * @throws IllegalArgumentException ページ番号・件数が不正な場合
 */

    @Transactional(readOnly = true)
    public Page<Contact> findContacts(
        int page,
        int size,
        ContactStatus status) {

    // 不正な条件ではRepositoryを呼ばない
    if (page < 0) {
        throw new IllegalArgumentException(
                "ページ番号は0以上で指定してください。"
        );
    }

    if (size < 1 || size > MAX_PAGE_SIZE) {
        throw new IllegalArgumentException(
                "1ページあたりの件数は1～100で指定してください。"
        );
    }

    // 同じ作成日時でも取得順が安定するようIDを補助条件にする
    Sort sort = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    Pageable pageable = PageRequest.of(page, size, sort);

    if (status == null) {
        return contactRepository.findAll(pageable);
    }

    return contactRepository.findByStatus(status, pageable);
}
}
