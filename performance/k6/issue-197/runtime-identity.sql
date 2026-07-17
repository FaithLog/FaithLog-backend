\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
\pset pager off

BEGIN TRANSACTION READ ONLY;

WITH latest_flyway AS (
    SELECT version, script, checksum::TEXT AS checksum
    FROM flyway_schema_history
    WHERE success = TRUE
    ORDER BY installed_rank DESC
    LIMIT 1
)
SELECT json_build_object(
    'currentDatabase', current_database(),
    'serverAddress', inet_server_addr()::text,
    'serverPort', inet_server_port(),
    'postmasterStartTime', pg_postmaster_start_time(),
    'flywayVersion', (SELECT version FROM latest_flyway),
    'flywayScript', (SELECT script FROM latest_flyway),
    'flywayChecksum', (SELECT checksum FROM latest_flyway)
);

COMMIT;
