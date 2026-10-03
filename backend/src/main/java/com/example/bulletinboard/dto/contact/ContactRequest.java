package com.example.bulletinboard.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 【クラスの役割】
 * お問い合わせ受付APIの入力値を保持するDTO。
 * バックエンドで検証する入力条件を定義します。
 */
@Getter
@Setter
public class ContactRequest {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 50, message = "名前は50文字以内で入力してください")
    private String name;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "正しいメールアドレスの形式で入力してください")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    @NotBlank(message = "件名を入力してください")
    @Size(max = 100, message = "件名は100文字以内で入力してください")
    private String subject;

    @NotBlank(message = "お問い合わせ本文を入力してください")
    @Size(max = 1000, message = "お問い合わせ本文は1000文字以内で入力してください")
    private String message;
}