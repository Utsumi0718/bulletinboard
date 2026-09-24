package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataAccessResourceFailureException;

import com.example.bulletinboard.exception.ContactNotFoundException;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;

/**
 * 【クラスの役割】
 * 管理者向けお問い合わせServiceの処理を検証する単体テストです。
 *
 * 【現在の検証内容】
 * - 対象が存在する場合、Repositoryの取得結果を返すこと
 * - 対象が存在しない場合、ContactNotFoundExceptionを発生させること
 * - 同じ状態への指定では保存・日時変更・操作履歴追加を行わないこと   
 * - 状態変更の対象が存在しない場合は例外を返し、保存・履歴追加を行わないこと
 * - 操作するユーザーが存在しない場合は、お問い合わせの処理と履歴追加を行わないこと  
 * - 一般ユーザーによる状態変更を拒否し、お問い合わせの処理と履歴追加を行わないこと
 * - 状態変更の保存失敗時は例外を伝え、操作履歴を追加しないこと   
 *
 * 【テストの範囲】
 * ContactRepositoryはモックに置き換えます。
 * 実DBへの接続、HTTP応答、管理者認可、
 * Springのトランザクション制御は検証しません。
 */
@ExtendWith(MockitoExtension.class)
class AdminContactServiceTest {

    @Mock
    private ContactRepository contactRepository;
    
    @Mock
    private AdminOperationLogRepository adminOperationLogRepository;

    @Mock
    private UserRepository userRepository;


    @InjectMocks
    private AdminContactService adminContactService;

    /**
     * 指定したIDをRepositoryへ渡し、
     * 取得したContactをそのまま返すことを確認します。
     */
    @Test
    @DisplayName("対象のお問い合わせが存在する場合は取得結果を返す")
    void getById_whenFound_shouldReturnContact() {

        // Given：Repositoryが返すお問い合わせを用意する
        Contact contact = new Contact();
        contact.setId(1L);

        when(contactRepository.findById(1L))
                .thenReturn(Optional.of(contact));

        // When：詳細取得処理を実行する
        Contact result = adminContactService.getById(1L);

        // Then：取得したインスタンスをそのまま返す
        assertThat(result).isSameAs(contact);

        // 指定したIDで検索したことを確認する
        verify(contactRepository).findById(1L);
    }

    /**
     * Repositoryの検索結果が空の場合、
     * 対象不存在の専用例外へ変換することを確認します。
     */
    @Test
    @DisplayName("対象のお問い合わせが存在しない場合は専用例外を発生させる")
    void getById_whenNotFound_shouldThrowContactNotFoundException() {

        // Given：対象が存在しない状態を再現する
        when(contactRepository.findById(999L))
                .thenReturn(Optional.empty());

        // When・Then：例外の種類と公開用メッセージを確認する
        assertThatThrownBy(
                () -> adminContactService.getById(999L)
        )
                .isInstanceOf(ContactNotFoundException.class)
                .hasMessage("指定されたお問い合わせが見つかりません。");

        verify(contactRepository).findById(999L);
    }

  /**
 * 状態未指定の場合、全状態を対象にページ取得することを確認します。
 *
 * Repositoryへ渡すページ番号・件数・並び順を検証します。
 * DBで実際に並び替えられることは、Repositoryテストで確認します。
 */
@Test
@DisplayName("状態未指定の場合は全状態を指定ページ・件数・並び順で取得する")
void findContacts_withoutStatus_shouldFindAllWithPageable() {

    // Given：2ページ目（0始まりで1）の取得結果を用意する
    Contact contact = new Contact();
    contact.setId(12L);

    Page<Contact> expectedPage = new PageImpl<>(
            List.of(contact),
            PageRequest.of(1, 20),
            21
    );

    when(contactRepository.findAll(any(Pageable.class)))
            .thenReturn(expectedPage);

    // When：状態を指定せず一覧を取得する
    Page<Contact> result =
            adminContactService.findContacts(1, 20, null);

    // Then：Repositoryの取得結果をそのまま返す
    assertThat(result).isSameAs(expectedPage);

    // Repositoryに渡されたページ条件を確認する
    ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

    verify(contactRepository).findAll(pageableCaptor.capture());

    Pageable pageable = pageableCaptor.getValue();

    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(20);

    // 作成日時が優先され、その次にIDで降順となることを確認する
    assertThat(pageable.getSort().toList())
            .containsExactly(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );

    // 状態別検索など、ほかのRepository操作をしていないことを確認する
    verifyNoMoreInteractions(contactRepository);
 }

/**
 * 状態指定時に、指定した状態とページ条件を
 * Repositoryへ渡すことを確認します。
 *
 * 実際にDBの検索結果が絞り込まれることは、
 * Repositoryテストで確認します。
 */
@Test
@DisplayName("状態指定時は指定ステータスでページ取得する")
void findContacts_withStatus_shouldFindByStatus() {

    // Given：対応中のお問い合わせの取得結果を用意する
    Contact contact = new Contact();
    contact.setId(12L);
    contact.setStatus(ContactStatus.IN_PROGRESS);

    Page<Contact> expectedPage = new PageImpl<>(
            List.of(contact),
            PageRequest.of(0, 20),
            1
    );

    when(contactRepository.findByStatus(
            eq(ContactStatus.IN_PROGRESS),
            any(Pageable.class)
    )).thenReturn(expectedPage);

    // When：対応中に絞り込んで取得する
    Page<Contact> result = adminContactService.findContacts(
            0,
            20,
            ContactStatus.IN_PROGRESS
    );

    // Then：Repositoryの取得結果をそのまま返す
    assertThat(result).isSameAs(expectedPage);

    ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

    verify(contactRepository).findByStatus(
            eq(ContactStatus.IN_PROGRESS),
            pageableCaptor.capture()
    );

    Pageable pageable = pageableCaptor.getValue();

    assertThat(pageable.getPageNumber()).isEqualTo(0);
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort().toList())
            .containsExactly(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );

    // 全状態の取得など、別のRepository操作は行わない
    verifyNoMoreInteractions(contactRepository);
}

/**
 * ページ番号・件数が不正な場合に例外を発生させ、
 * DB検索へ進まないことを確認します。
 */
@ParameterizedTest(name = "page={0}, size={1}の場合は拒否する")
@CsvSource({
        "-1, 20, ページ番号は0以上で指定してください。",
        "0, 0, 1ページあたりの件数は1～100で指定してください。",
        "0, -1, 1ページあたりの件数は1～100で指定してください。",
        "0, 101, 1ページあたりの件数は1～100で指定してください。"
})
void findContacts_withInvalidPageOrSize_shouldReject(
        int page,
        int size,
        String expectedMessage) {

    // Repositoryの戻り値は設定しない。
    // 入力検証で拒否され、Repositoryには到達しないため。
    assertThatThrownBy(
            () -> adminContactService.findContacts(page, size, null)
    )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(expectedMessage);

    verifyNoInteractions(contactRepository);
}

/**
 * 未対応から対応中へ変更し、
 * 操作した管理者と変更前後の状態を履歴へ渡すことを確認します。
 *
 * Repositoryはモックのため、
 * 実DBのコミットや更新日時の自動設定は検証しません。
 */
@Test
@DisplayName("状態変更時に管理者と変更前後の状態を操作履歴へ保存する")
void updateStatus_shouldUpdateContactAndSaveOperationLog() {

    User adminUser = new User();
    adminUser.setId(10L);
    adminUser.setEmail("admin@example.com");
    adminUser.setRole("ROLE_ADMIN");

    Contact contact = new Contact();
    contact.setId(1L);
    contact.setStatus(ContactStatus.UNANSWERED);

    when(userRepository.findByEmail("admin@example.com"))
            .thenReturn(Optional.of(adminUser));

    when(contactRepository.findById(1L))
            .thenReturn(Optional.of(contact));

    when(contactRepository.saveAndFlush(contact))
            .thenReturn(contact);

    Contact result = adminContactService.updateStatus(
            1L,
            ContactStatus.IN_PROGRESS,
            "admin@example.com"
    );

    // お問い合わせの状態が変更され、保存結果が返ることを確認。
    assertThat(result).isSameAs(contact);
    assertThat(result.getStatus())
            .isEqualTo(ContactStatus.IN_PROGRESS);

    verify(userRepository).findByEmail("admin@example.com");
    verify(contactRepository).findById(1L);
    verify(contactRepository).saveAndFlush(contact);

    // 履歴Repositoryへ渡した内容を取り出して確認。
    ArgumentCaptor<AdminOperationLog> logCaptor =
            ArgumentCaptor.forClass(AdminOperationLog.class);

    verify(adminOperationLogRepository).save(logCaptor.capture());

    AdminOperationLog operationLog = logCaptor.getValue();

    assertThat(operationLog.getAdminUser()).isSameAs(adminUser);
    assertThat(operationLog.getTargetType()).isEqualTo("CONTACT");
    assertThat(operationLog.getTargetId()).isEqualTo(1L);
    assertThat(operationLog.getAction()).isEqualTo("STATUS_CHANGE");
    assertThat(operationLog.getBeforeStatus()).isEqualTo("UNANSWERED");
    assertThat(operationLog.getAfterStatus()).isEqualTo("IN_PROGRESS");
    assertThat(operationLog.getResult()).isEqualTo("SUCCESS");
}

/**
 * 同じ状態を指定した場合は現在の情報を返し、
 * 保存処理と操作履歴の追加を行わないことを確認します。
 */
@Test
@DisplayName("同じ状態なら保存と履歴追加を行わず更新日時を維持する")
void updateStatus_whenSameStatus_shouldNotSaveOrAddLog() {

    User adminUser = new User();
    adminUser.setId(10L);
    adminUser.setEmail("admin@example.com");
    adminUser.setRole("ROLE_ADMIN");

    LocalDateTime createdAt =
            LocalDateTime.of(2026, 9, 20, 10, 0);
    LocalDateTime updatedAt =
            LocalDateTime.of(2026, 9, 21, 11, 0);

    Contact contact = new Contact();
    contact.setId(1L);
    contact.setStatus(ContactStatus.IN_PROGRESS);
    contact.setCreatedAt(createdAt);
    contact.setUpdatedAt(updatedAt);

    when(userRepository.findByEmail("admin@example.com"))
            .thenReturn(Optional.of(adminUser));

    when(contactRepository.findById(1L))
            .thenReturn(Optional.of(contact));

    Contact result = adminContactService.updateStatus(
            1L,
            ContactStatus.IN_PROGRESS,
            "admin@example.com"
    );

    // 現在のEntityを返し、状態・日時を変更しない。
    assertThat(result).isSameAs(contact);
    assertThat(result.getStatus())
            .isEqualTo(ContactStatus.IN_PROGRESS);
    assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    assertThat(result.getUpdatedAt()).isEqualTo(updatedAt);

    // お問い合わせの取得以外に、保存などを呼んでいない。
    verify(contactRepository).findById(1L);
    verifyNoMoreInteractions(contactRepository);

    // 同じ状態への指定では操作履歴を追加しない。
    verifyNoInteractions(adminOperationLogRepository);
}

/**
 * お問い合わせが存在しない場合は専用例外を返し、
 * 保存処理と操作履歴の追加を行わないことを確認します。
 */
@Test
@DisplayName("状態変更の対象が存在しない場合は例外を返し保存と履歴追加を行わない")
void updateStatus_whenContactNotFound_shouldThrowAndNotSave() {

    User adminUser = new User();
    adminUser.setId(10L);
    adminUser.setEmail("admin@example.com");
    adminUser.setRole("ROLE_ADMIN");

    when(userRepository.findByEmail("admin@example.com"))
            .thenReturn(Optional.of(adminUser));

    when(contactRepository.findById(999L))
            .thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminContactService.updateStatus(
            999L,
            ContactStatus.IN_PROGRESS,
            "admin@example.com"
    ))
            .isInstanceOf(ContactNotFoundException.class)
            .hasMessage("指定されたお問い合わせが見つかりません。");

    // 対象の検索だけを行い、保存処理へ進まない。
    verify(contactRepository).findById(999L);
    verifyNoMoreInteractions(contactRepository);

    // 存在しないお問い合わせの操作履歴を追加しない。
    verifyNoInteractions(adminOperationLogRepository);
}

/**
 * 操作するユーザーが取得できない場合は、
 * お問い合わせの検索・更新と履歴追加を行わないことを確認します。
 */
@Test
@DisplayName("操作するユーザーが存在しない場合は状態変更と履歴追加を行わない")
void updateStatus_whenUserNotFound_shouldThrowAndNotSave() {

    when(userRepository.findByEmail("admin@example.com"))
            .thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminContactService.updateStatus(
            1L,
            ContactStatus.IN_PROGRESS,
            "admin@example.com"
    ))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("ログインユーザー情報を取得できませんでした。");

    verify(userRepository).findByEmail("admin@example.com");

    // 操作者を特定できないため、お問い合わせの処理には進まない。
    verifyNoInteractions(
            contactRepository,
            adminOperationLogRepository
    );
}

/**
 * 操作するユーザーが管理者でない場合は、
 * お問い合わせの検索・更新と履歴追加を行わないことを確認します。
 */
@Test
@DisplayName("一般ユーザーによる状態変更を拒否し履歴も追加しない")
void updateStatus_whenUserIsNotAdmin_shouldThrowAndNotSave() {

    User user = new User();
    user.setId(20L);
    user.setEmail("user@example.com");
    user.setRole("ROLE_USER");

    when(userRepository.findByEmail("user@example.com"))
            .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> adminContactService.updateStatus(
            1L,
            ContactStatus.IN_PROGRESS,
            "user@example.com"
    ))
            .isInstanceOf(ForbiddenOperationException.class)
            .hasMessage("この操作は管理者のみ実行できます。");

    verify(userRepository).findByEmail("user@example.com");

    // 管理者ではないため、お問い合わせの処理へ進まない。
    verifyNoInteractions(
            contactRepository,
            adminOperationLogRepository
    );
}

/**
 * お問い合わせの保存に失敗した場合は、
 * 例外を呼び出し元へ伝え、操作履歴を追加しないことを確認します。
 *
 * 実DBのロールバックは統合テストで別途確認します。
 */
@Test
@DisplayName("状態変更の保存に失敗した場合は例外を伝え履歴を追加しない")
void updateStatus_whenSaveFails_shouldThrowAndNotAddLog() {

    User adminUser = new User();
    adminUser.setId(10L);
    adminUser.setEmail("admin@example.com");
    adminUser.setRole("ROLE_ADMIN");

    Contact contact = new Contact();
    contact.setId(1L);
    contact.setStatus(ContactStatus.UNANSWERED);

    DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException(
                    "テスト用のDB接続失敗"
            );

    when(userRepository.findByEmail("admin@example.com"))
            .thenReturn(Optional.of(adminUser));

    when(contactRepository.findById(1L))
            .thenReturn(Optional.of(contact));

    when(contactRepository.saveAndFlush(contact))
            .thenThrow(failure);

    assertThatThrownBy(() -> adminContactService.updateStatus(
            1L,
            ContactStatus.IN_PROGRESS,
            "admin@example.com"
    ))
            .isSameAs(failure);

    verify(contactRepository).saveAndFlush(contact);

    // 保存に失敗したため、成功の操作履歴を追加しない。
    verifyNoInteractions(adminOperationLogRepository);
}
}
