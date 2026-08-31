package com.example.bulletinboard.model;

/**
 * ユーザーアカウントのサービス利用状態を表す列挙型です。
 *
 * ACTIVE    : 通常利用可能
 * FROZEN    : 管理者による凍結状態
 * WITHDRAWN : 退会済み
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    WITHDRAWN
}