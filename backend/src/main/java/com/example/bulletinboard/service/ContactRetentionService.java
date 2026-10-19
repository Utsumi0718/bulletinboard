package com.example.bulletinboard.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import com.example.bulletinboard.model.ContactRetentionLog;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.repository.ContactRetentionLogRepository;

/** RESOLVEDのみ暦で1年保持。各対象をロック後に再判定し、削除と記録を原子的に確定する。 */
@Service
public class ContactRetentionService {
    private final ContactRepository contacts;
    private final ContactRetentionLogRepository logs;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final Clock clock;
    public ContactRetentionService(ContactRepository contacts, ContactRetentionLogRepository logs,
            PlatformTransactionManager manager, @Value("${contact.retention.batch-size:100}") int batchSize,
            ObjectProvider<Clock> clocks) {
        this.contacts = contacts; this.logs = logs;
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("Invalid contact retention batch size");
        this.batchSize = batchSize;
        this.clock = clocks.getIfAvailable(() -> Clock.system(ZoneId.of("Asia/Tokyo")));
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** batchSize件ずつ読み込み、各対象を短い独立トランザクションで削除する。 */
    public int deleteExpired() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Tokyo"));
        int deleted = 0;
        while (true) {
            // 閏年の2/29受付も、翌年2/28の同時刻に1年経過として扱う。
            var ids = transactions.execute(tx -> contacts.findExpiredIds(
                    now.minusYears(1).plusDays(1), now, PageRequest.of(0, batchSize)));
            if (ids.isEmpty()) return deleted;
            for (Long id : ids) {
                boolean result = Boolean.TRUE.equals(transactions.execute(tx -> {
                    var contact = contacts.findByIdForUpdate(id).orElse(null);
                    if (contact == null || contact.getStatus() != ContactStatus.RESOLVED
                            || contact.getCreatedAt().plusYears(1).isAfter(now)
                            || (contact.getLegalHoldUntil() != null && contact.getLegalHoldUntil().isAfter(now))) {
                        return false;
                    }
                    logs.saveAndFlush(new ContactRetentionLog(contact, now));
                    contacts.delete(contact);
                    contacts.flush();
                    return true;
                }));
                if (result) deleted++;
            }
        }
    }
}
