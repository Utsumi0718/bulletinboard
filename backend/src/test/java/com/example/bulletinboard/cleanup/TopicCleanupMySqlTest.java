package com.example.bulletinboard.cleanup;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Opt in only against a pre-created EMPTY local database cleanup_verification_<suffix>.
 * Never falls back to the normal datasource. Does not start Docker, create/drop a database, or contact AWS.
 * The inherited scenarios use real MySQL only when all guards pass.
 */
@EnabledIfEnvironmentVariable(named="CLEANUP_MYSQL_TESTS",matches="true")
class TopicCleanupMySqlTest extends TopicCleanupIntegrationTest {
    @Autowired DataSource source;

    @DynamicPropertySource
    static void isolatedMySql(DynamicPropertyRegistry properties) throws Exception {
        String url=System.getenv("CLEANUP_MYSQL_TEST_URL");
        String user=System.getenv("CLEANUP_MYSQL_TEST_USER");
        String password=System.getenv("CLEANUP_MYSQL_TEST_PASSWORD");
        if(url==null || !url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/cleanup_verification_[a-z0-9_]+")
                || user==null || password==null) throw new IllegalStateException("Use an explicitly configured isolated local MySQL database.");
        try(var connection=DriverManager.getConnection(url,user,password);
                var tables=connection.getMetaData().getTables(connection.getCatalog(),null,"%",new String[]{"TABLE"})) {
            if(tables.next()) throw new IllegalStateException("MySQL verification requires an EMPTY isolated database.");
        }
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.username",()->user);
        properties.add("spring.datasource.password",()->password);
        properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.database-platform",()->"org.hibernate.dialect.MySQLDialect");
    }

    @Test void mysqlTablesUseTransactionalInnoDb() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND engine<>'InnoDB'",Integer.class)).isZero();
    }
    @Test void mysqlCleanupExplicitlyUsesReadCommitted() {
        assertThat(db.transaction(()->jdbc.queryForObject("SELECT @@SESSION.transaction_isolation",String.class))).isEqualTo("READ-COMMITTED");
    }
    @Test void mysqlLockWaitRollsBackAndBecomesRetryWait()throws Exception {
        long topic=topic(721,true);var token=lease.acquire().orElseThrow();long run=cleanup.createRun(token,day());
        long attempt=cleanup.reserve(token,run,cleanup.candidates(100,day()).getFirst());
        try(var blocker=source.getConnection();var executor=Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try(var statement=blocker.prepareStatement("SELECT id FROM topics WHERE id=? FOR UPDATE")) {statement.setLong(1,topic);statement.executeQuery().close();}
            try {executor.submit(()->cleanup.process(token,attempt)).get(15,TimeUnit.SECONDS);}
            finally {blocker.rollback();}
        }
        assertThat(state(topic)).isEqualTo("RETRY_WAIT");assertThat(count("topics")).isEqualTo(1);assertThat(count("ranking_result_archives")).isZero();
    }
}
