package com.example.bulletinboard.cleanup;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC participates in the same DataSource transaction as JPA. No external DB is selected here. */
@Component
public class CleanupDatabase {
    final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final CleanupProperties properties;
    private final boolean mysql;

    public CleanupDatabase(JdbcTemplate jdbc, PlatformTransactionManager manager,
            CleanupProperties properties, DataSource source) throws java.sql.SQLException {
        this.jdbc = new JdbcTemplate(source) {
            @Override protected org.springframework.jdbc.core.RowMapper<java.util.Map<String,Object>> getColumnMapRowMapper() {
                return new org.springframework.jdbc.core.ColumnMapRowMapper() {
                    @Override protected Object getColumnValue(java.sql.ResultSet rs,int index) throws java.sql.SQLException {
                        int type=rs.getMetaData().getColumnType(index);
                        if(type==java.sql.Types.TIMESTAMP) return rs.getObject(index,LocalDateTime.class);
                        if(type==java.sql.Types.DATE) return rs.getObject(index,java.time.LocalDate.class);
                        return super.getColumnValue(rs,index);
                    }
                };
            }
        };
        this.manager = manager; this.properties = properties;
        try (var connection = source.getConnection()) {
            mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        }
    }

    public LocalDateTime now() {
        if (mysql) return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", (rs,n) -> rs.getObject(1,LocalDateTime.class));
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6) AT TIME ZONE 'UTC'", (rs,n) ->
                rs.getObject(1,java.time.OffsetDateTime.class).withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime());
    }

    <T> T transaction(Supplier<T> work) {
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(properties.getTransactionTimeoutSeconds());
        return tx.execute(status -> {
            Integer previous = mysql ? jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class) : null;
            if (mysql) jdbc.execute("SET SESSION innodb_lock_wait_timeout = " + properties.getLockWaitSeconds());
            try { return work.get(); }
            finally {
                if (mysql) jdbc.execute("SET SESSION innodb_lock_wait_timeout = " + previous);
            }
        });
    }

    // Bind local UTC fields directly using JDBC 4.2; do not round-trip through the JVM default time zone.
    static LocalDateTime stamp(LocalDateTime time) { return time; }
}
