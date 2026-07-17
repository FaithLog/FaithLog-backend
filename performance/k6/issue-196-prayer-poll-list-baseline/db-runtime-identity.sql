select json_build_object(
  'currentDatabase', current_database(),
  'currentUser', current_user,
  'sessionUser', session_user,
  'sessionUserIsDatabaseOwner', pg_has_role(
    session_user,
    (select datdba from pg_database where datname = current_database()),
    'MEMBER'
  ),
  'serverAddress', inet_server_addr(),
  'serverPort', inet_server_port(),
  'postmasterStartedAt', pg_postmaster_start_time(),
  'latestFlywayVersion', (
    select version from flyway_schema_history where success order by installed_rank desc limit 1
  ),
  'publicApplicationTableCount', (
    select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r' and c.relname <> 'flyway_schema_history'
  ),
  'rlsEnabledTableCount', (
    select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r' and c.relname <> 'flyway_schema_history' and c.relrowsecurity
  ),
  'forceRlsTableCount', (
    select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r' and c.relname <> 'flyway_schema_history' and c.relforcerowsecurity
  ),
  'policyCount', (select count(*) from pg_policies where schemaname = 'public'),
  'jdbcOwnedTableCount', (
    select count(*) from pg_tables
    where schemaname = 'public' and tablename <> 'flyway_schema_history' and tableowner = current_user
  ),
  'pgStatStatementsExtensionInstalled', exists (
    select 1 from pg_extension where extname = 'pg_stat_statements'
  ),
  'pgStatStatementsPreloaded', 'pg_stat_statements' = any (
    string_to_array(replace(current_setting('shared_preload_libraries'), ' ', ''), ',')
  ),
  'pgStatStatementsViewAvailable', to_regclass('public.pg_stat_statements') is not null
);
