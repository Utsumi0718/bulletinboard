package com.example.bulletinboard.cleanup;

import java.util.List;
import com.example.bulletinboard.security.ActorIds;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.bulletinboard.exception.ForbiddenOperationException;

@Service
public class CleanupAdminService {
    private final CleanupDatabase db;
    private final TopicCleanupService cleanup;
    public CleanupAdminService(CleanupDatabase db, TopicCleanupService cleanup) { this.db=db; this.cleanup=cleanup; }

    private long admin(String actorId) {
        var found = db.jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ROLE_ADMIN' AND account_status='ACTIVE'", ActorIds.parse(actorId));
        if (found.size()!=1) throw new ForbiddenOperationException("この操作は管理者のみ実行できます。");
        return TopicCleanupService.number(found.getFirst(),"id");
    }
    private int offset(int page,int size) {
        if(page<0 || page>100000 || size<1 || size>100) throw new IllegalArgumentException("一覧条件を確認してください。");
        return Math.multiplyExact(page,size);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> runs(String actorId,int page,int size) {
        admin(actorId); int offset=offset(page,size);
        return db.jdbc.queryForList("""
            SELECT r.id,r.scheduled_date,r.started_at,r.finished_at,r.status,r.daily_limit,
              (SELECT COUNT(*) FROM topic_cleanup_attempts a WHERE a.run_id=r.id) AS processed_count,
              (SELECT COUNT(*) FROM topic_cleanup_attempts a WHERE a.run_id=r.id AND a.outcome IN ('COMPLETED','IMAGE_COMPLETED')) AS success_count,
              (SELECT COUNT(*) FROM topic_cleanup_attempts a WHERE a.run_id=r.id AND a.outcome='FAILED') AS failure_count,
              (SELECT COUNT(*) FROM topic_cleanup_attempts a WHERE a.run_id=r.id AND a.outcome LIKE 'HELD_%') AS held_count
            FROM topic_cleanup_runs r ORDER BY r.scheduled_date DESC LIMIT ? OFFSET ?
            """,size,offset);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> targets(String actorId,int page,int size,String state) {
        admin(actorId); int offset=offset(page,size);
        if(state!=null && !List.of("HELD_REPORT","HELD_X_REFERENCE","HELD_TIME_UNVERIFIED","RETRY_WAIT","STOPPED","COMPLETED","ELIGIBLE","HELD_IMAGE").contains(state))
            throw new IllegalArgumentException("状態を確認してください。");
        String where=state==null?"":state.equals("HELD_IMAGE")?" WHERE c.pending_image_id IS NOT NULL":" WHERE c.state=?";
        String sql="SELECT c.*,t.deleted_at,t.cleanup_deleted_at_utc FROM topic_cleanup_targets c LEFT JOIN topics t ON t.id=c.original_topic_id"+where
                +" ORDER BY c.original_topic_id LIMIT ? OFFSET ?";
        var rows = state==null || state.equals("HELD_IMAGE")?db.jdbc.queryForList(sql,size,offset):db.jdbc.queryForList(sql,state,size,offset);
        var now = db.now();
        for (var row : rows) {
            var time = (java.time.LocalDateTime) row.get("cleanup_deleted_at_utc");
            Long hours = time == null ? null : Math.max(0,java.time.Duration.between(time,now).toHours());
            row.put("elapsed_hours",hours);
            row.put("held_hours",hours == null ? null : Math.max(0,hours-720));
        }
        return rows;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> attempts(String actorId,long topic,int page,int size) {
        admin(actorId); int offset=offset(page,size);
        return db.jdbc.queryForList("SELECT id,run_id,original_topic_id,cycle_no,started_at,finished_at,outcome,reason_code,error_type "
                +"FROM topic_cleanup_attempts WHERE original_topic_id=? ORDER BY id DESC LIMIT ? OFFSET ?",topic,size,offset);
    }

    @Transactional(readOnly=true)
    public List<TopicCleanupService.Preview> dryRun(String actorId,int size) {
        admin(actorId); offset(0,size); return cleanup.dryRun(size);
    }

    public void resume(String actorId,long topic,String reason) {
        if(reason==null || reason.isBlank() || reason.length()>500) throw new IllegalArgumentException("解除理由を1〜500文字で入力してください。");
        db.transaction(() -> {
            long actor=admin(actorId);
            var rows=db.jdbc.queryForList("SELECT * FROM topic_cleanup_targets WHERE original_topic_id=? FOR UPDATE",topic);
            if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if(!"STOPPED".equals(rows.getFirst().get("state"))) throw new ResponseStatusException(HttpStatus.CONFLICT);
            db.jdbc.update("UPDATE topic_cleanup_targets SET state='RETRY_WAIT',reason_code=NULL,failure_count=0,cycle_no=cycle_no+1,next_eligible_date=? WHERE original_topic_id=?",
                    java.sql.Date.valueOf(db.now().plusHours(9).toLocalDate()),topic);
            db.jdbc.update("INSERT INTO admin_operation_logs(admin_user_id,target_type,target_id,action,before_status,after_status,result,created_at,reason) "
                    +"VALUES (?,'TOPIC_CLEANUP',?,'RESUME','STOPPED','RETRY_WAIT','SUCCESS',?,?)",
                    actor,topic,CleanupDatabase.stamp(db.now()),reason);
            return null;
        });
    }
}
