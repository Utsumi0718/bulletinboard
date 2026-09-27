package com.example.bulletinboard.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.ContactNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.repository.UserRepository;

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
 * - お問い合わせの状態変更
 * - 状態変更と同じトランザクションでの操作履歴保存
 * - 同じ状態を指定した場合の更新・履歴追加の省略
 * - お問い合わせの物理削除
 * - 削除と同じトランザクションでの操作履歴保存
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
 * 状態変更・削除APIのService呼び出しから伝わる
 * DataAccessException・TransactionExceptionは、
 * Controllerで管理操作専用例外へ変換し、
 * GlobalExceptionHandlerで安全なログと共通エラー応答を生成します。
 */

@Service
public class AdminContactService {


    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final AdminOperationLogRepository adminOperationLogRepository;

    /**
     * 一覧で指定できる最大ページサイズ。
     */
    public static final int MAX_PAGE_SIZE = 100;


    public AdminContactService(
        ContactRepository contactRepository,
        UserRepository userRepository,
        AdminOperationLogRepository adminOperationLogRepository) {

    this.contactRepository = contactRepository;
    this.userRepository = userRepository;
    this.adminOperationLogRepository = adminOperationLogRepository;
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

/**
 * お問い合わせの対応状態を変更し、操作履歴を保存します。
 *
 * 状態変更と履歴保存は、同じトランザクションで行います。
 * 処理中の実行時例外では、両方がロールバックの対象となります。
 * ただし、コミット時の通信障害などではDBの最終状態を断定できません。
 * 例外が返ったことだけで、ロールバック完了とは判断しません。
 *
 * 同じ状態を指定した場合は、
 * お問い合わせの更新と履歴追加を行いません。
 *
 * @param id お問い合わせID
 * @param newStatus 変更先の状態
 * @param loginEmail Controllerが認証情報から取得したメールアドレス
 * @return 変更後、または変更不要だったお問い合わせ
 * @throws IllegalArgumentException 変更先の状態がnullの場合
 * @throws UserNotFoundException 操作するユーザーを取得できない場合
 * @throws ForbiddenOperationException 操作するユーザーが管理者でない場合
 * @throws ContactNotFoundException お問い合わせが存在しない場合
 */
@Transactional
public Contact updateStatus(
        Long id,
        ContactStatus newStatus,
        String loginEmail) {

    // APIの入力検証に加え、Serviceでもnullを拒否する。
    if (newStatus == null) {
        throw new IllegalArgumentException(
                "ステータスを指定してください。"
        );
    }

    // loginEmailには、リクエスト本文の値ではなく
    // ControllerがAuthenticationから取得した値を渡す。
    if (loginEmail == null || loginEmail.isBlank()) {
        throw new UserNotFoundException(
                "ログインユーザー情報を取得できませんでした。"
        );
    }

    User adminUser = userRepository.findByEmail(loginEmail)
            .orElseThrow(() -> new UserNotFoundException(
                    "ログインユーザー情報を取得できませんでした。"
            ));

    // 履歴へ記録するユーザーが管理者であることも確認する。
    if (!"ROLE_ADMIN".equals(adminUser.getRole())) {
        throw new ForbiddenOperationException(
                "この操作は管理者のみ実行できます。"
        );
    }

    Contact contact = contactRepository.findById(id)
            .orElseThrow(ContactNotFoundException::new);

    ContactStatus beforeStatus = contact.getStatus();

    // 同じ状態なら、更新日時も操作履歴も変更しない。
    if (beforeStatus == newStatus) {
        return contact;
    }

    contact.setStatus(newStatus);

    // 更新SQLを実行し、@PreUpdateによる更新日時を
    // 戻り値へ反映する。この時点ではコミットしない。
    Contact updatedContact =
            contactRepository.saveAndFlush(contact);

    AdminOperationLog operationLog = new AdminOperationLog(
            adminUser,
            "CONTACT",
            contact.getId(),
            "STATUS_CHANGE",
            beforeStatus.name(),
            newStatus.name()
    );

    // 履歴保存中の実行時例外では、
    // 先にflushしたお問い合わせの変更もロールバックの対象となる。
    adminOperationLogRepository.save(operationLog);

    return updatedContact;
}

/**
 * お問い合わせを物理削除し、操作履歴を保存します。
 *
 * すべてのステータスを削除対象とします。
 * 削除と履歴保存は同じトランザクションで行います。
 * 履歴には削除前の状態とafterStatus=nullを記録します。
 * 履歴の対象IDはContactへの外部キーではないため、削除後も履歴が残ります。
 * コミット時の通信障害などでは、DBの最終状態を断定できません。
 *
 * @param id お問い合わせID
 * @param loginEmail Controllerが認証情報から取得したメールアドレス
 * @throws UserNotFoundException 操作者を取得できない場合
 * @throws ForbiddenOperationException 操作者が管理者でない場合
 * @throws ContactNotFoundException お問い合わせが存在しない場合
 */
@Transactional
public void deleteContact(Long id, String loginEmail) {

    if (loginEmail == null || loginEmail.isBlank()) {
        throw new UserNotFoundException(
                "ログインユーザー情報を取得できませんでした。"
        );
    }

    User adminUser = userRepository.findByEmail(loginEmail)
            .orElseThrow(() -> new UserNotFoundException(
                    "ログインユーザー情報を取得できませんでした。"
            ));

    if (!"ROLE_ADMIN".equals(adminUser.getRole())) {
        throw new ForbiddenOperationException(
                "この操作は管理者のみ実行できます。"
        );
    }

    Contact contact = contactRepository.findById(id)
            .orElseThrow(ContactNotFoundException::new);

    // 削除前の情報で履歴を作成する。
    // この時点では、まだ履歴を保存しない。
    AdminOperationLog operationLog = new AdminOperationLog(
            adminUser,
            "CONTACT",
            contact.getId(),
            "DELETE",
            contact.getStatus().name(),
            null
    );

    contactRepository.delete(contact);

    // 削除SQLを実行する。コミットはまだ行われない。
    // 削除に失敗した場合は、履歴保存へ進まない。
    contactRepository.flush();

    // 履歴保存中の実行時例外では、削除もロールバックの対象となる。
    // 履歴の保存だけでは成功は確定せず、同じトランザクションのコミットで確定する。
    adminOperationLogRepository.save(operationLog);
}
}
