package com.example.bulletinboard.cleanup;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/** All destructive work and its success marker commit together; no report/evidence table is deleted. */
@Service
public class TopicCleanupService {
    public static class Integrity extends RuntimeException { }
    public record Candidate(long topicId, LocalDateTime deletedAt, LocalDateTime verifiedUtc,
            LocalDateTime lastCheckedAt, boolean imageOnly) { }
    public record Preview(long topicId, String reason, LocalDateTime deletedAt,
            LocalDateTime eligibleAtUtc, Long elapsedHours, boolean imageOnly) { }
    private final CleanupDatabase db;
    private final CleanupLease lease;
    private final CleanupProperties properties;

    public TopicCleanupService(CleanupDatabase db, CleanupLease lease, CleanupProperties properties) {
        this.db = db; this.lease = lease; this.properties = properties;
    }

    public List<Candidate> candidates(int limit, LocalDate day) {
        var cutoff = db.now().minusHours(720);
        return db.jdbc.query("""
            SELECT t.id,t.deleted_at,t.cleanup_deleted_at_utc,c.last_checked_at,0 AS image_only
            FROM topics t LEFT JOIN topic_cleanup_targets c ON c.original_topic_id=t.id
            WHERE t.deleted_at IS NOT NULL
              AND (t.cleanup_deleted_at_utc IS NULL OR t.cleanup_deleted_at_utc<=?)
              AND (c.state IS NULL OR c.state NOT IN ('STOPPED','COMPLETED'))
              AND (c.next_eligible_date IS NULL OR c.next_eligible_date<=?)
            UNION ALL
            SELECT c.original_topic_id,NULL,NULL,c.last_checked_at,1
            FROM topic_cleanup_targets c WHERE c.deleted_success_at IS NOT NULL AND c.pending_image_id IS NOT NULL
              AND c.state<>'STOPPED' AND (c.next_eligible_date IS NULL OR c.next_eligible_date<=?)
              AND NOT EXISTS(SELECT 1 FROM topics t WHERE t.id=c.original_topic_id)
            ORDER BY last_checked_at ASC, id ASC LIMIT ?
            """, (rs, n) -> new Candidate(rs.getLong(1), rs.getObject(2,LocalDateTime.class),
                    rs.getObject(3,LocalDateTime.class), rs.getObject(4,LocalDateTime.class), rs.getBoolean(5)),
                CleanupDatabase.stamp(cutoff), Date.valueOf(day), Date.valueOf(day), limit);
    }

    /** Read-only preview: no lease, target, attempt or archive is written. */
    public List<Preview> dryRun(int limit) {
        LocalDateTime now = db.now();
        return candidates(limit, now.plusHours(9).toLocalDate()).stream().map(c -> new Preview(c.topicId,
                c.imageOnly ? "IMAGE_RECHECK" : reason(c.topicId, c.verifiedUtc, now), c.deletedAt,
                c.verifiedUtc == null ? null : c.verifiedUtc.plusHours(720),
                c.verifiedUtc == null ? null : java.time.Duration.between(c.verifiedUtc, now).toHours(), c.imageOnly)).toList();
    }

    public Long createRun(CleanupLease.Token token, LocalDate day) {
        return db.transaction(() -> {
            lease.verify(token, true);
            if (db.jdbc.queryForObject("SELECT COUNT(*) FROM topic_cleanup_runs WHERE scheduled_date=?", Long.class,
                    Date.valueOf(day)) > 0) return null;
            var key = new GeneratedKeyHolder();
            db.jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO topic_cleanup_runs "
                    + "(scheduled_date,owner_id,generation,started_at,status,daily_limit) VALUES (?,?,?,?, 'RUNNING',?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setDate(1, Date.valueOf(day)); statement.setString(2, token.owner());
                statement.setLong(3, token.generation()); statement.setObject(4, CleanupDatabase.stamp(db.now()));
                statement.setInt(5, properties.getDailyLimit()); return statement;
            }, key);
            return Objects.requireNonNull(key.getKey()).longValue();
        });
    }

    /** Committed reservation consumes one daily slot even if the process stops before deletion. */
    public Long reserve(CleanupLease.Token token, long run, Candidate candidate) {
        return db.transaction(() -> {
            lease.verify(token, true);
            var job = db.jdbc.queryForMap("SELECT daily_limit,scheduled_date FROM topic_cleanup_runs WHERE id=? FOR UPDATE", run);
            if (db.jdbc.queryForObject("SELECT COUNT(*) FROM topic_cleanup_attempts WHERE run_id=?", Long.class, run)
                    >= ((Number) job.get("daily_limit")).intValue()) return null;
            if (db.jdbc.queryForObject("SELECT COUNT(*) FROM topic_cleanup_attempts WHERE run_id=? AND original_topic_id=?",
                    Long.class, run, candidate.topicId) > 0) return null;
            if (db.jdbc.queryForObject("SELECT COUNT(*) FROM topic_cleanup_targets WHERE original_topic_id=?",
                    Long.class, candidate.topicId) == 0) {
                db.jdbc.update("INSERT INTO topic_cleanup_targets(original_topic_id,state) VALUES (?, 'ELIGIBLE')", candidate.topicId);
            }
            var state = db.jdbc.queryForMap("SELECT state,cycle_no FROM topic_cleanup_targets WHERE original_topic_id=? FOR UPDATE", candidate.topicId);
            if ("STOPPED".equals(state.get("state"))) return null;
            var key = new GeneratedKeyHolder();
            db.jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO topic_cleanup_attempts "
                        + "(run_id,original_topic_id,cycle_no,outcome) VALUES (?,?,?,'RESERVED')",
                        java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, run); statement.setLong(2, candidate.topicId);
                statement.setInt(3, ((Number) state.get("cycle_no")).intValue()); return statement;
            }, key);
            return Objects.requireNonNull(key.getKey()).longValue();
        });
    }

    public void process(CleanupLease.Token token, long attempt) {
        db.transaction(() -> {
            lease.verify(token, true);
            int changed = db.jdbc.update("UPDATE topic_cleanup_attempts SET outcome='STARTED',started_at=? WHERE id=? AND outcome='RESERVED'",
                    CleanupDatabase.stamp(db.now()), attempt);
            if (changed != 1) throw new Integrity();
            return null;
        });
        try {
            db.transaction(() -> {
                lease.verify(token, true);
                var a = db.jdbc.queryForMap("SELECT * FROM topic_cleanup_attempts WHERE id=?", attempt);
                long topicId = number(a, "original_topic_id");
                var topics = db.jdbc.queryForList("SELECT id,user_id,image,deleted_at,cleanup_deleted_at_utc FROM topics WHERE id=? FOR UPDATE", topicId);
                var target = db.jdbc.queryForMap("SELECT * FROM topic_cleanup_targets WHERE original_topic_id=? FOR UPDATE", topicId);
                if (!"STARTED".equals(a.get("outcome")) || number(a, "cycle_no") != number(target, "cycle_no")) throw new Integrity();
                if (topics.isEmpty()) {
                    // Only a previously committed Topic deletion may proceed to image rechecks.
                    if (target.get("deleted_success_at") == null) throw new Integrity();
                    String pending = (String) target.get("pending_image_id");
                    Long expectedOwner = target.get("pending_image_owner_id") == null ? null : number(target,"pending_image_owner_id");
                    boolean removed = pending == null || removeImage(pending, expectedOwner);
                    db.jdbc.update("UPDATE topic_cleanup_targets SET state='COMPLETED',pending_image_id=?,reason_code=?,last_checked_at=? WHERE original_topic_id=?",
                            removed ? null : pending, removed ? null : "HELD_IMAGE", CleanupDatabase.stamp(db.now()), topicId);
                    finishAttempt(attempt, removed ? "IMAGE_COMPLETED" : "HELD_IMAGE", removed ? null : "HELD_IMAGE", null);
                } else {
                    var topic = topics.getFirst();
                    String held = topic.get("deleted_at") == null ? "NOT_DELETED" :
                            reason(topicId, valueTime(topic.get("cleanup_deleted_at_utc")), db.now());
                    if (!"ELIGIBLE".equals(held)) {
                        db.jdbc.update("UPDATE topic_cleanup_targets SET state=?,reason_code=?,last_checked_at=? WHERE original_topic_id=?",
                                held, held, CleanupDatabase.stamp(db.now()), topicId);
                        finishAttempt(attempt, held, held, null);
                    } else {
                        archive(topicId);
                        String image = managedImage((String) topic.get("image"));
                        if (image != null) db.jdbc.queryForList("SELECT id FROM topic_images WHERE id=? FOR UPDATE", image);
                        db.jdbc.update("DELETE FROM likes WHERE answer_id IN (SELECT id FROM answers WHERE topic_id=?)", topicId);
                        db.jdbc.update("DELETE FROM ranking_results WHERE ranking_judgment_id IN (SELECT id FROM ranking_judgments WHERE topic_id=?)", topicId);
                        db.jdbc.update("DELETE FROM winner_achievements WHERE topic_id=?", topicId);
                        db.jdbc.update("DELETE FROM answers WHERE topic_id=?", topicId);
                        db.jdbc.update("DELETE FROM ranking_judgments WHERE topic_id=?", topicId);
                        if (db.jdbc.update("DELETE FROM topics WHERE id=?", topicId) != 1) throw new Integrity();
                        boolean removed = image == null || removeImage(image, number(topic,"user_id"));
                        db.jdbc.update("UPDATE topic_cleanup_targets SET state='COMPLETED',reason_code=?,pending_image_id=?,pending_image_owner_id=?,last_checked_at=?,deleted_success_at=? WHERE original_topic_id=?",
                                removed ? null : "HELD_IMAGE", removed ? null : image, removed ? null : number(topic,"user_id"), CleanupDatabase.stamp(db.now()), CleanupDatabase.stamp(db.now()), topicId);
                        finishAttempt(attempt, "COMPLETED", removed ? null : "HELD_IMAGE", null);
                    }
                }
                lease.verify(token, false);
                return null;
            });
        } catch (CleanupLease.Lost ex) { throw ex;
        } catch (Integrity | DataIntegrityViolationException ex) {
            recordFailure(token, attempt, true, ex.getClass().getSimpleName());
        } catch (TransientDataAccessException ex) {
            recordFailure(token, attempt, false, ex.getClass().getSimpleName());
        } catch (org.springframework.transaction.TransactionTimedOutException
                | org.springframework.transaction.UnexpectedRollbackException ex) {
            recordFailure(token, attempt, false, ex.getClass().getSimpleName());
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            recordFailure(token, attempt, true, ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            // A lost COMMIT response is not proof of rollback. Leave STARTED for next-day reconciliation.
            throw ex;
        }
    }

    private String reason(long id, LocalDateTime verified, LocalDateTime now) {
        if (verified == null) return "HELD_TIME_UNVERIFIED";
        if (verified.plusHours(720).isAfter(now)) return "NOT_DUE";
        long reports = db.jdbc.queryForObject("""
            SELECT COUNT(*) FROM reports r WHERE r.status IN ('UNHANDLED','IN_PROGRESS') AND
            ((r.target_type='TOPIC' AND r.target_id=?) OR (r.target_type='ANSWER' AND
              r.target_id IN (SELECT id FROM answers WHERE topic_id=?)))
            """, Long.class, id, id);
        if (reports > 0) return "HELD_REPORT";
        long x = db.jdbc.queryForObject("SELECT COUNT(*) FROM x_post_history WHERE topic_id=? OR answer_id IN (SELECT id FROM answers WHERE topic_id=?)",
                Long.class, id, id);
        return x > 0 ? "HELD_X_REFERENCE" : "ELIGIBLE";
    }

    private boolean removeImage(String id, Long expectedOwner) {
        var rows = db.jdbc.queryForList("SELECT owner_user_id FROM topic_images WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) return true;
        if (expectedOwner == null || number(rows.getFirst(),"owner_user_id") != expectedOwner) return false;
        if (db.jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE image=?", Long.class, "/api/topic-images/" + id) > 0) return false;
        db.jdbc.update("DELETE FROM topic_images WHERE id=?", id);
        return true;
    }

    private void archive(long topic) {
        var rankings = db.jdbc.queryForList("""
            SELECT r.answer_id AS original_answer_id,a.user_id,j.checkpoint,r.like_count,j.judged_at,a.topic_id
            FROM ranking_results r JOIN ranking_judgments j ON j.id=r.ranking_judgment_id
              JOIN answers a ON a.id=r.answer_id WHERE j.topic_id=? ORDER BY r.id FOR UPDATE
            """, topic);
        for (var row : rankings) {
            if (number(row, "topic_id") != topic) throw new Integrity();
            copyArchive("ranking_result_archives", row, topic, "judged_at", true);
        }
        var winners = db.jdbc.queryForList("""
            SELECT w.user_id,w.answer_id AS original_answer_id,w.like_count,w.achieved_at,
                   a.topic_id,a.user_id AS answer_owner
            FROM winner_achievements w JOIN answers a ON a.id=w.answer_id WHERE w.topic_id=? ORDER BY w.id FOR UPDATE
            """, topic);
        for (var row : winners) {
            if (number(row, "topic_id") != topic || number(row, "user_id") != number(row, "answer_owner")) throw new Integrity();
            copyArchive("winner_achievement_archives", row, topic, "achieved_at", false);
        }
        if (db.jdbc.queryForObject("SELECT COUNT(*) FROM ranking_result_archives WHERE original_topic_id=?", Long.class, topic) != rankings.size()
                || db.jdbc.queryForObject("SELECT COUNT(*) FROM winner_achievement_archives WHERE original_topic_id=?", Long.class, topic) != winners.size()) throw new Integrity();
    }

    private void copyArchive(String table, Map<String,Object> source, long topic, String dateColumn, boolean ranking) {
        String condition = ranking ? "original_topic_id=? AND checkpoint=? AND original_answer_id=?" : "original_topic_id=? AND user_id=?";
        Object[] keys = ranking ? new Object[]{topic, source.get("checkpoint"), source.get("original_answer_id")}
                : new Object[]{topic, source.get("user_id")};
        var existing = db.jdbc.queryForList("SELECT * FROM " + table + " WHERE " + condition + " FOR UPDATE", keys);
        if (existing.isEmpty()) {
            if (ranking) db.jdbc.update("INSERT INTO ranking_result_archives(user_id,original_topic_id,original_answer_id,checkpoint,like_count,judged_at,archived_at) VALUES (?,?,?,?,?,?,?)",
                    source.get("user_id"), topic, source.get("original_answer_id"), source.get("checkpoint"), source.get("like_count"), source.get(dateColumn), CleanupDatabase.stamp(db.now()));
            else db.jdbc.update("INSERT INTO winner_achievement_archives(user_id,original_topic_id,original_answer_id,like_count,achieved_at,archived_at) VALUES (?,?,?,?,?,?)",
                    source.get("user_id"), topic, source.get("original_answer_id"), source.get("like_count"), source.get(dateColumn), CleanupDatabase.stamp(db.now()));
            existing = db.jdbc.queryForList("SELECT * FROM " + table + " WHERE " + condition, keys);
        }
        var saved = existing.getFirst();
        if (existing.size() != 1 || number(saved,"user_id") != number(source,"user_id")
                || number(saved,"original_answer_id") != number(source,"original_answer_id")
                || number(saved,"like_count") != number(source,"like_count")
                || !Objects.equals(valueTime(saved.get(dateColumn)), valueTime(source.get(dateColumn)))) throw new Integrity();
    }

    private void finishAttempt(long attempt, String outcome, String reason, String error) {
        db.jdbc.update("UPDATE topic_cleanup_attempts SET outcome=?,reason_code=?,error_type=?,finished_at=? WHERE id=?",
                outcome, reason, error, CleanupDatabase.stamp(db.now()), attempt);
    }

    private void recordFailure(CleanupLease.Token token, long attempt, boolean integrity, String error) {
        db.transaction(() -> {
            lease.verify(token, false);
            recordFailureInTransaction(attempt, integrity, error);
            return null;
        });
    }

    private void recordFailureInTransaction(long attempt, boolean integrity, String error) {
            var a = db.jdbc.queryForMap("SELECT * FROM topic_cleanup_attempts WHERE id=? FOR UPDATE", attempt);
            if (!"STARTED".equals(a.get("outcome"))) return;
            long topic = number(a,"original_topic_id");
            var t = db.jdbc.queryForMap("SELECT * FROM topic_cleanup_targets WHERE original_topic_id=? FOR UPDATE", topic);
            if (number(a,"cycle_no") != number(t,"cycle_no")) throw new Integrity();
            int failures = ((Number)t.get("failure_count")).intValue() + 1;
            String state = integrity || failures >= 4 ? "STOPPED" : "RETRY_WAIT";
            db.jdbc.update("UPDATE topic_cleanup_targets SET state=?,reason_code=?,failure_count=?,last_checked_at=?,next_eligible_date=? WHERE original_topic_id=?",
                    state, integrity ? "INTEGRITY" : "TRANSIENT", failures, CleanupDatabase.stamp(db.now()),
                    Date.valueOf(db.now().plusHours(9).toLocalDate().plusDays(1)), topic);
            finishAttempt(attempt,"FAILED", integrity ? "INTEGRITY" : "TRANSIENT", error);
    }

    /** Only called by a newly scheduled next-day job, with an exclusively owned lease. */
    public void reconcile(CleanupLease.Token token, LocalDate day) {
        db.transaction(() -> {
            lease.verify(token, true);
            var pending = db.jdbc.queryForList("SELECT a.* FROM topic_cleanup_attempts a JOIN topic_cleanup_runs r ON r.id=a.run_id "
                    + "WHERE r.scheduled_date<? AND a.outcome IN ('RESERVED','STARTED') ORDER BY a.id FOR UPDATE", Date.valueOf(day));
            for (var a : pending) {
                long topic = number(a,"original_topic_id"), attempt = number(a,"id");
                boolean exists = db.jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id=?", Long.class, topic) > 0;
                var target = db.jdbc.queryForMap("SELECT * FROM topic_cleanup_targets WHERE original_topic_id=? FOR UPDATE", topic);
                if (!exists && target.get("deleted_success_at") == null) {
                    db.jdbc.update("UPDATE topic_cleanup_targets SET state='STOPPED',reason_code='MISSING_SUCCESS_RECORD' WHERE original_topic_id=?", topic);
                    finishAttempt(attempt,"FAILED","MISSING_SUCCESS_RECORD",null);
                } else if ("RESERVED".equals(a.get("outcome"))) {
                    finishAttempt(attempt,"NOT_STARTED","INTERRUPTED",null);
                } else {
                    recordFailureInTransaction(attempt, false, "Interrupted");
                    db.jdbc.update("UPDATE topic_cleanup_targets SET next_eligible_date=? WHERE original_topic_id=?",
                            Date.valueOf(day),topic);
                }
            }
            for (Long old : db.jdbc.queryForList("SELECT id FROM topic_cleanup_runs WHERE scheduled_date<? AND status='RUNNING'", Long.class, Date.valueOf(day))) finishRunInTransaction(old,"INTERRUPTED");
            return null;
        });
    }

    public void finishRun(CleanupLease.Token token, long run, String status) {
        db.transaction(() -> { lease.verify(token,false); finishRunInTransaction(run,status); return null; });
    }
    private void finishRunInTransaction(long run, String status) {
        db.jdbc.update("""
            UPDATE topic_cleanup_runs SET status=?,finished_at=?,
              processed_count=(SELECT COUNT(*) FROM topic_cleanup_attempts WHERE run_id=?),
              success_count=(SELECT COUNT(*) FROM topic_cleanup_attempts WHERE run_id=? AND outcome IN ('COMPLETED','IMAGE_COMPLETED')),
              failure_count=(SELECT COUNT(*) FROM topic_cleanup_attempts WHERE run_id=? AND outcome='FAILED'),
              held_count=(SELECT COUNT(*) FROM topic_cleanup_attempts WHERE run_id=? AND outcome LIKE 'HELD_%') WHERE id=?
            """, status,CleanupDatabase.stamp(db.now()),run,run,run,run,run);
    }

    static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private static LocalDateTime valueTime(Object value) { return value == null ? null : value instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime)value; }
    private static String managedImage(String url) {
        if (url == null || !url.startsWith("/api/topic-images/")) return null;
        String id = url.substring("/api/topic-images/".length());
        try { return UUID.fromString(id).toString().equals(id) ? id : null; }
        catch (IllegalArgumentException ex) { return null; }
    }
}
