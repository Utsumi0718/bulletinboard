package com.example.bulletinboard.service;

/** DBコミット後に対象Userの既存Sessionを期限切れにするためのイベント。 */
public record PasswordResetCompletedEvent(String email) {
}
