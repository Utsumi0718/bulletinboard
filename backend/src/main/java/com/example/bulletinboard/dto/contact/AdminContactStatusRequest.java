package com.example.bulletinboard.dto.contact;

import com.example.bulletinboard.model.ContactStatus;

import jakarta.validation.constraints.NotNull;

/**
 * 【クラスの役割】
 * 管理者によるお問い合わせの状態変更を受け取るDTOです。
 *
 * 変更先の状態だけを受け取り、
 * 操作する管理者の情報は認証情報から別途取得します。
 *
 * APIでは@Validと組み合わせて必須条件を検証します。
 * JSONからの変換と不正入力の拒否は、API接続後にテストします。
 *
 * @param status 変更先の対応状態
 */
public record AdminContactStatusRequest(

        @NotNull(message = "ステータスを指定してください。")
        ContactStatus status

) {
}