package com.example.bulletinboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.model.ContactStatus;

/**
 * 【クラスの役割】
 * お問い合わせの保存処理を担当するService。
 * メール通知は、この保存処理の外側で扱います。
 */
@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    /**
     * 入力内容からお問い合わせを作成して保存します。
     *
     * @param request 検証済みのお問い合わせ入力
     * @return 保存したお問い合わせ
     */
    @Transactional
    public Contact createContact(ContactRequest request) {

        Contact contact = new Contact();

        contact.setName(request.getName());
        contact.setEmail(request.getEmail());
        contact.setSubject(request.getSubject());
        contact.setMessage(request.getMessage());

        // 対応状況は、利用者の入力によらず未対応にする
       contact.setStatus(ContactStatus.UNANSWERED);

        return contactRepository.save(contact);
    }
}