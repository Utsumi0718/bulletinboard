package com.example.bulletinboard.dto.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/*
 * 【クラスの役割】
 * お問い合わせフォーム画面（/contact）から送信された入力値（名前、メールアドレス、件名、お問い合わせ内容）を
 * 一括で受け取り、バリデーション（入力チェック）ルールを定義・保持するDTO（Data Transfer Object）クラスです。
 * Thymeleaf のフォームバインディング機能（th:object）と連携し、エラーメッセージの管理を行います。
 */
@Data
public class ContactForm {

    /**
     * お問い合わせ者の名前
     */
    @NotBlank(message = "{NotBlank.form.name}")
    @Size(max = 50, message = "{Size.form.name}")
    private String name;

    /**
     * お問い合わせ者のメールアドレス
     */
    @NotBlank(message = "{NotBlank.form.email}")
    @Email(message = "{Email.form.email}")
    private String email;

    /**
     * お問い合わせ件名
     */
    @NotBlank(message = "{NotBlank.form.subject}")
    @Size(max = 100, message = "{Size.form.subject}")
    private String subject;

    /**
     * お問い合わせ内容本文
     */
    @NotBlank(message = "{NotBlank.form.message}")
    @Size(max = 1000, message = "{Size.form.message}")
    private String message;
}