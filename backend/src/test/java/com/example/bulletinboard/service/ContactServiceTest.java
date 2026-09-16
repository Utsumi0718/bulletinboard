package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.repository.ContactRepository;

/**
 * 【クラスの役割】
 * お問い合わせの保存処理を検証する単体テスト。
 * Repositoryをモック化し、実DBには接続しません。
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private ContactService contactService;

    @Test
    @DisplayName("入力内容を未対応のContactとして保存し、保存結果を返すこと")
    void createContact_ShouldSaveInputAsUnansweredAndReturnSavedContact() {

        // Given：入力内容を用意
        ContactRequest request = new ContactRequest();
        request.setName("テスト太郎");
        request.setEmail("user@example.com");
        request.setSubject("サービスについて");
        request.setMessage("お問い合わせ本文です。");

        // Repositoryが返す保存結果を用意
        Contact savedContact = new Contact();
        savedContact.setId(1L);

        when(contactRepository.save(any(Contact.class)))
                .thenReturn(savedContact);

        // When：Serviceを実行
        Contact result = contactService.createContact(request);

        // Then：保存のために渡されたContactを取り出す
        ArgumentCaptor<Contact> contactCaptor =
                ArgumentCaptor.forClass(Contact.class);

        verify(contactRepository).save(contactCaptor.capture());

        Contact capturedContact = contactCaptor.getValue();

        // 入力内容と初期状態を確認
        assertThat(capturedContact.getName())
                .isEqualTo("テスト太郎");
        assertThat(capturedContact.getEmail())
                .isEqualTo("user@example.com");
        assertThat(capturedContact.getSubject())
                .isEqualTo("サービスについて");
        assertThat(capturedContact.getMessage())
                .isEqualTo("お問い合わせ本文です。");
        assertThat(capturedContact.getStatus())
                .isEqualTo("UNANSWERED");

        // Repositoryから返された保存結果を、そのまま返しているか確認
        assertThat(result).isSameAs(savedContact);
    }

@Test
@DisplayName("Repositoryで保存に失敗した場合、例外を呼び出し元へ伝えること")
void createContact_WhenSaveFails_ShouldPropagateException() {

    // Given：正常な入力を用意
    ContactRequest request = new ContactRequest();
    request.setName("テスト太郎");
    request.setEmail("user@example.com");
    request.setSubject("サービスについて");
    request.setMessage("お問い合わせ本文です。");

    DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException("テスト用のDB接続失敗");

    // 保存失敗をモックで再現
    when(contactRepository.save(any(Contact.class)))
            .thenThrow(failure);

    // When & Then：失敗を握りつぶさず、呼び出し元へ伝える
    assertThatThrownBy(() -> contactService.createContact(request))
            .isSameAs(failure);

    verify(contactRepository).save(any(Contact.class));
}
}