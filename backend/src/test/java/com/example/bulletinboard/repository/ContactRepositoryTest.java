package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;

/**
 * 【クラスの役割】
 * ContactRepositoryの検索処理を、
 * テスト用H2データベースで検証します。
 *
 * 実際に保存したデータを検索し、
 * ページングと取得順序を確認します。
 *
 * MySQL固有の動作やFlywayのMigrationは対象外です。
 * テストで保存したデータは終了時にロールバックされます。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
})
class ContactRepositoryTest {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("全状態を作成日時降順・ID降順でページ取得できる")
    void findAll_shouldReturnPagesOrderedByCreatedAtAndIdDescending() {

        LocalDateTime baseTime =
                LocalDateTime.of(2026, 9, 24, 10, 0);

        // 保存順と作成日時順を意図的に変える。
        // IDだけで並べてもテストが通らないデータにする。
        Contact newestLowerId = persistContact(
                "最新・先に保存",
                ContactStatus.UNANSWERED,
                baseTime.plusDays(2)
        );

        Contact oldest = persistContact(
                "最も古い",
                ContactStatus.RESOLVED,
                baseTime
        );

        Contact newestHigherId = persistContact(
                "最新・後に保存",
                ContactStatus.IN_PROGRESS,
                baseTime.plusDays(2)
        );

        Contact middle = persistContact(
                "中間の日時",
                ContactStatus.UNANSWERED,
                baseTime.plusDays(1)
        );

        Long newestLowerIdValue = newestLowerId.getId();
        Long oldestId = oldest.getId();
        Long newestHigherIdValue = newestHigherId.getId();
        Long middleId = middle.getId();

        assertThat(newestHigherIdValue)
                .isGreaterThan(newestLowerIdValue);

        // DBへ反映し、管理中のEntityをクリアしてから検索する。
        entityManager.flush();
        entityManager.clear();

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        Page<Contact> firstPage = contactRepository.findAll(
                PageRequest.of(0, 2, sort)
        );

        Page<Contact> secondPage = contactRepository.findAll(
                PageRequest.of(1, 2, sort)
        );

        // 同じ最新日時の2件は、IDが大きい順になる。
        assertThat(firstPage.getContent())
                .extracting(Contact::getId)
                .containsExactly(
                        newestHigherIdValue,
                        newestLowerIdValue
                );

        assertThat(firstPage.getNumber()).isZero();
        assertThat(firstPage.getSize()).isEqualTo(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(4L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        // 次のページには、残りの2件が日時の降順で入る。
        assertThat(secondPage.getContent())
                .extracting(Contact::getId)
                .containsExactly(middleId, oldestId);

        assertThat(secondPage.getNumber()).isEqualTo(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(4L);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.isLast()).isTrue();
    }

    /**
     * 状態と作成日時を指定してテストデータを保存します。
     * IDはDBに採番させます。
     */
    private Contact persistContact(
            String subject,
            ContactStatus status,
            LocalDateTime createdAt) {

        Contact contact = new Contact();
        contact.setName("テスト太郎");
        contact.setEmail("user@example.com");
        contact.setSubject(subject);
        contact.setMessage("Repositoryの検索確認用です。");
        contact.setStatus(status);
        contact.setCreatedAt(createdAt);
        contact.setUpdatedAt(createdAt);

        return entityManager.persist(contact);
    }

    /**
 * 指定した状態だけを対象として、
 * 並び順とページ情報が正しくなることを確認します。
 */
@Test
@DisplayName("指定した状態だけを作成日時降順・ID降順でページ取得できる")
void findByStatus_shouldFilterAndReturnOrderedPages() {

    LocalDateTime baseTime =
            LocalDateTime.of(2026, 9, 24, 10, 0);

    Contact older = persistContact(
            "対応中・古い",
            ContactStatus.IN_PROGRESS,
            baseTime
    );

    Contact newerLowerId = persistContact(
            "対応中・新しい・先に保存",
            ContactStatus.IN_PROGRESS,
            baseTime.plusDays(1)
    );

    Contact newerHigherId = persistContact(
            "対応中・新しい・後に保存",
            ContactStatus.IN_PROGRESS,
            baseTime.plusDays(1)
    );

    // 対象外の状態は、日時が新しくても検索結果に含めない。
    persistContact(
            "未対応",
            ContactStatus.UNANSWERED,
            baseTime.plusDays(2)
    );

    persistContact(
            "対応済み",
            ContactStatus.RESOLVED,
            baseTime.plusDays(3)
    );

    Long olderId = older.getId();
    Long newerLowerIdValue = newerLowerId.getId();
    Long newerHigherIdValue = newerHigherId.getId();

    entityManager.flush();
    entityManager.clear();

    Sort sort = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    Page<Contact> firstPage = contactRepository.findByStatus(
            ContactStatus.IN_PROGRESS,
            PageRequest.of(0, 2, sort)
    );

    Page<Contact> secondPage = contactRepository.findByStatus(
            ContactStatus.IN_PROGRESS,
            PageRequest.of(1, 2, sort)
    );

    assertThat(firstPage.getContent())
            .extracting(Contact::getId)
            .containsExactly(
                    newerHigherIdValue,
                    newerLowerIdValue
            );

    assertThat(secondPage.getContent())
            .extracting(Contact::getId)
            .containsExactly(olderId);

    assertThat(firstPage.getContent())
            .extracting(Contact::getStatus)
            .containsOnly(ContactStatus.IN_PROGRESS);

    assertThat(secondPage.getContent())
            .extracting(Contact::getStatus)
            .containsOnly(ContactStatus.IN_PROGRESS);

    // 全5件ではなく、絞り込み対象の3件を基準に計算する。
    assertThat(firstPage.getNumber()).isZero();
    assertThat(firstPage.getSize()).isEqualTo(2);
    assertThat(firstPage.getTotalElements()).isEqualTo(3L);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);

    assertThat(secondPage.getNumber()).isEqualTo(1);
    assertThat(secondPage.getTotalElements()).isEqualTo(3L);
    assertThat(secondPage.getTotalPages()).isEqualTo(2);
    assertThat(secondPage.isLast()).isTrue();
}
}
