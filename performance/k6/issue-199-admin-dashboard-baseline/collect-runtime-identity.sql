-- issue199:evidence=runtime-identity
\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
\pset pager off

BEGIN TRANSACTION READ ONLY;

SELECT jsonb_build_object(
    'database', current_database(),
    'serverAddress', inet_server_addr(),
    'serverPort', current_setting('port')::integer,
    'serverVersion', current_setting('server_version'),
    'systemIdentifier', (SELECT system_identifier::text FROM pg_control_system()),
    'postmasterStartedAt', pg_postmaster_start_time(),
    'expectedRoleMatched', current_user = :'expected_db_user',
    'flyway', (
        SELECT jsonb_build_object('latestVersion', version, 'latestScript', script, 'latestSuccess', success)
        FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1
    ),
    'rls', (
        WITH required(relname) AS (VALUES
            ('users'), ('campuses'), ('campus_members'), ('weekly_devotion_records'),
            ('charge_items'), ('polls'), ('poll_responses')
        ), states AS (
            SELECT r.relname, c.relrowsecurity, c.relforcerowsecurity,
                   pg_get_userbyid(c.relowner) = current_user AS owned_by_current_user
            FROM required r LEFT JOIN pg_class c ON c.oid = to_regclass('public.' || r.relname)
        )
        SELECT jsonb_build_object(
            'requiredTables', (SELECT jsonb_agg(relname ORDER BY relname) FROM states),
            'allEnabled', bool_and(relrowsecurity IS TRUE),
            'anyForced', bool_or(relforcerowsecurity),
            'policyCount', (SELECT COUNT(*)::integer FROM pg_policies WHERE schemaname = 'public' AND tablename IN (SELECT relname FROM required)),
            'allOwnedByCurrentUser', bool_and(owned_by_current_user IS TRUE)
        ) FROM states
    )
);

COMMIT;
