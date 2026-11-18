package com.faithlog.companion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded, idempotent operation-payload archiving. Request key and fingerprint
 * stay in the PK forever to prevent replayed care/evolution from paying XP twice.
 * Never delete these rows as part of ordinary retention.
 */
@Service
public class CompanionInstanceOperationArchiveService {
    public static final int MAX_BATCH = 500;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CompanionInstanceOperationArchiveService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public int archiveExpired(int batchSize) {
        return archiveBefore(clock.instant().minus(90, ChronoUnit.DAYS), batchSize);
    }

    @Transactional
    public int archiveBefore(Instant cutoff, int batchSize) {
        if (cutoff == null || batchSize < 1 || batchSize > MAX_BATCH) {
            throw new IllegalArgumentException("Invalid companion archive request");
        }
        return jdbc.update("""
            WITH eligible AS (
              SELECT user_id,instance_id,request_key
              FROM companion_instance_operations
              WHERE response_json IS NOT NULL AND created_at < :cutoff
              ORDER BY created_at,user_id,instance_id,request_key
              LIMIT :limit FOR UPDATE SKIP LOCKED
            )
            UPDATE companion_instance_operations op
              SET response_json=NULL,archived_at=CURRENT_TIMESTAMP
            FROM eligible e
            WHERE op.user_id=e.user_id AND op.instance_id=e.instance_id
              AND op.request_key=e.request_key
              AND op.response_json IS NOT NULL
            """,new MapSqlParameterSource("cutoff",java.sql.Timestamp.from(cutoff))
                .addValue("limit",batchSize));
    }
}
