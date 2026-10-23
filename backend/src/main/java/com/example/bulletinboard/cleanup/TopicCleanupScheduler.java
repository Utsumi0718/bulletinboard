package com.example.bulletinboard.cleanup;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "topic.cleanup.enabled", havingValue = "true")
public class TopicCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(TopicCleanupScheduler.class);
    private final CleanupLease lease;
    private final CleanupDatabase db;
    private final CleanupProperties properties;
    private final TopicCleanupService service;

    public TopicCleanupScheduler(CleanupLease lease, CleanupDatabase db, CleanupProperties properties, TopicCleanupService service) {
        this.lease = lease; this.db = db; this.properties = properties; this.service = service;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Tokyo")
    public void run() {
        if (!properties.isEnabled()) return;
        CleanupLease.Token token = null;
        Long runId = null;
        var lost = new AtomicBoolean();
        try (var heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r,"topic-cleanup-heartbeat"); thread.setDaemon(true); return thread;
        })) {
            token = lease.acquire().orElse(null);
            if (token == null) return;
            final var owner = token;
            var day = db.now().plusHours(9).toLocalDate();
            runId = service.createRun(token, day);
            if (runId == null) return; // Expiry of a lease never permits a second run on the same JST day.
            heartbeat.scheduleWithFixedDelay(() -> {
                try { if (!lease.renew(owner)) lost.set(true); }
                catch (RuntimeException ex) { lost.set(true); log.error("topic_cleanup_heartbeat_error errorType={}",ex.getClass().getSimpleName()); }
            },properties.getHeartbeatSeconds(),properties.getHeartbeatSeconds(),TimeUnit.SECONDS);
            service.reconcile(token, day);
            for (var candidate : service.candidates(properties.getDailyLimit(),day)) {
                if (lost.get()) break;
                Long attempt = service.reserve(token,runId,candidate);
                if (attempt != null && !lost.get()) service.process(token,attempt);
            }
            if (!lost.get()) service.finishRun(token,runId,"FINISHED");
        } catch (RuntimeException ex) {
            // Never log exception messages, SQL, entities, or a Throwable.
            log.error("topic_cleanup_error runId={} errorType={}",runId,ex.getClass().getSimpleName());
        } finally {
            if (token != null) try { lease.release(token); }
            catch (RuntimeException ex) { log.error("topic_cleanup_release_error errorType={}",ex.getClass().getSimpleName()); }
        }
    }
}
