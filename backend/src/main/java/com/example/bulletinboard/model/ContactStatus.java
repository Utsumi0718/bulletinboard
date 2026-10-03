package com.example.bulletinboard.model;

/**
 * 【クラスの役割】
 * お問い合わせの対応状況を定義するEnumです。
 *
 * Contactへの組み込み時は、Enum名をDBへ文字列として保存します。
 * 既存DBで使用している値をそのまま維持します。
 *
 * 状態遷移と同じ状態への更新を行わない判定は、
 * 管理用Serviceで実装します。
 */
public enum ContactStatus {

    /** 未対応：お問い合わせを受け付けた初期状態。 */
    UNANSWERED,

    /** 対応中：管理者が確認・対応している状態。 */
    IN_PROGRESS,

    /** 対応済み：管理者による対応が完了した状態。 */
    RESOLVED
}