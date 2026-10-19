package com.example.bulletinboard.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.bulletinboard.exception.ContactRateLimitException;

/** 共有DBの時刻と行ロックで10分5回を判定する。受付失敗でも許可済みの枠は戻さない。 */
@Service
public class ContactRateLimitService {
    private final JdbcTemplate jdbc;
    public ContactRateLimitService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ContactRateLimitException.class)
    public void check(String remoteAddress) {
        String key = sourceKey(remoteAddress);
        // 初回の同時作成も主キーの競合で直列化する。IPや転送ヘッダーを保存しない。
        jdbc.update("INSERT INTO contact_rate_limit_buckets (source_key, expires_at) VALUES (?, CURRENT_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE source_key = ?", key, key);
        List<LocalDateTime> times = jdbc.queryForObject(
                "SELECT slot1, slot2, slot3, slot4, slot5 FROM contact_rate_limit_buckets WHERE source_key = ? FOR UPDATE",
                (rs, n) -> {
                    List<LocalDateTime> values = new ArrayList<>();
                    for (int i = 1; i <= 5; i++) {
                        Timestamp value = rs.getTimestamp(i);
                        if (value != null) values.add(value.toLocalDateTime());
                    }
                    return values;
                }, key);
        LocalDateTime now = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)", Timestamp.class).toLocalDateTime();
        times.removeIf(time -> !time.isAfter(now.minusMinutes(10)));
        if (times.size() >= 5) throw new ContactRateLimitException();
        times.add(now);
        Object[] args = new Object[7];
        for (int i = 0; i < 5; i++) args[i] = i < times.size() ? Timestamp.valueOf(times.get(i)) : null;
        args[5] = Timestamp.valueOf(now.plusMinutes(10));
        args[6] = key;
        jdbc.update("UPDATE contact_rate_limit_buckets SET slot1=?, slot2=?, slot3=?, slot4=?, slot5=?, expires_at=? WHERE source_key=?", args);
    }

    /** 失効情報はバッチで削除する。JVM内にIP数に応じて増えるMapは持たない。 */
    @Scheduled(fixedDelayString = "${contact.rate-limit.cleanup-delay-ms:60000}")
    @Transactional
    public void cleanExpired() {
        jdbc.update("DELETE FROM contact_rate_limit_buckets WHERE expires_at <= CURRENT_TIMESTAMP(6) LIMIT 1000");
    }

    private static String sourceKey(String address) {
        if (address == null || address.isBlank()) throw new IllegalStateException("送信元を確認できません。");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("送信元を確認できません。", ex); }
    }
}
