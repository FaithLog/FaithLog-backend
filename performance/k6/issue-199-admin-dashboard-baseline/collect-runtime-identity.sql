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
    'postmasterStartedAt', pg_postmaster_start_time()
);

COMMIT;
