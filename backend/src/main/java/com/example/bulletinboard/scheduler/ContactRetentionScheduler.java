package com.example.bulletinboard.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.bulletinboard.service.ContactRetentionService;

/** 外部メール・バックアップの運用確認後にのみ明示的に有効化する。 */
@Component
@ConditionalOnProperty(name = "contact.retention.enabled", havingValue = "true")
public class ContactRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(ContactRetentionScheduler.class);
    private final ContactRetentionService service;
    public ContactRetentionScheduler(ContactRetentionService service) { this.service = service; }
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Tokyo")
    public void deleteExpiredContacts() {
        try { log.info("contact_retention_deleted count={}", service.deleteExpired()); }
        catch (RuntimeException ex) { log.error("contact_retention_error errorType={}", ex.getClass().getSimpleName()); }
    }
}
