package com.example.bulletinboard.cleanup;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CleanupLease {
    public record Token(String owner, long generation) { }
    public static class Lost extends RuntimeException { }
    private final CleanupDatabase db;
    private final CleanupProperties properties;

    public CleanupLease(CleanupDatabase db, CleanupProperties properties) {
        this.db = db; this.properties = properties;
    }

    public Optional<Token> acquire() {
        return db.transaction(() -> {
            var row = rowForUpdate();
            var now = db.now(); // Separate statement, after waiting for the row lock.
            if (row.owner != null && row.until.isAfter(now)) return Optional.empty();
            var token = new Token(UUID.randomUUID().toString(), row.generation + 1);
            db.jdbc.update("UPDATE cleanup_job_leases SET owner_id=?, generation=?, expires_at=? WHERE job_key='TOPIC_CLEANUP'",
                    token.owner, token.generation, CleanupDatabase.stamp(now.plusSeconds(properties.getLeaseSeconds())));
            return Optional.of(token);
        });
    }

    public boolean renew(Token token) {
        return db.transaction(() -> {
            var row = rowForUpdate();
            var now = db.now();
            if (!matches(row, token) || !row.until.isAfter(now)) return false;
            db.jdbc.update("UPDATE cleanup_job_leases SET expires_at=? WHERE job_key='TOPIC_CLEANUP'",
                    CleanupDatabase.stamp(now.plusSeconds(properties.getLeaseSeconds())));
            return true;
        });
    }

    /** Caller holds this lock until its deletion transaction commits or rolls back. */
    void verify(Token token, boolean starting) {
        var row = rowForUpdate();
        var now = db.now();
        long reserve = starting ? properties.getTransactionTimeoutSeconds() + properties.getLockWaitSeconds() : 0;
        if (!matches(row, token) || !row.until.isAfter(now.plusSeconds(reserve))) throw new Lost();
    }

    public void release(Token token) {
        db.transaction(() -> {
            var row = rowForUpdate();
            if (matches(row, token)) db.jdbc.update(
                    "UPDATE cleanup_job_leases SET owner_id=NULL, expires_at=? WHERE job_key='TOPIC_CLEANUP'",
                    CleanupDatabase.stamp(db.now()));
            return null;
        });
    }

    private boolean matches(Row row, Token token) {
        return token.owner.equals(row.owner) && token.generation == row.generation;
    }
    private Row rowForUpdate() {
        return db.jdbc.queryForObject("SELECT owner_id,generation,expires_at FROM cleanup_job_leases WHERE job_key='TOPIC_CLEANUP' FOR UPDATE",
                (rs, n) -> new Row(rs.getString(1), rs.getLong(2), rs.getObject(3,LocalDateTime.class)));
    }
    private record Row(String owner, long generation, LocalDateTime until) { }
}
