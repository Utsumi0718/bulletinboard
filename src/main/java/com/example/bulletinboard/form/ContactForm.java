package com.example.bulletinboard.form;

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
    @NotBlank(message = "お名前を入力してください。")
    @Size(max = 50, message = "お名前は50文字以内で入力してください。")
    private String name;

    /**
     * お問い合わせ者のメールアドレス
     */
    @NotBlank(message = "メールアドレスを入力してください。")
    @Email(message = "正しいメールアドレスの形式で入力してください。")
    private String email;

    /**
     * お問い合わせ件名
     */
    @NotBlank(message = "件名を入力してください。")
    @Size(max = 100, message = "件名は100文字以内で入力してください。")
    private String subject;

    /**
     * お問い合わせ内容本文
     */
    @NotBlank(message = "お問い合わせ内容を入力してください。")
    @Size(max = 1000, message = "お問い合わせ内容は1000文字以内で入力してください。")
    private String message;
}