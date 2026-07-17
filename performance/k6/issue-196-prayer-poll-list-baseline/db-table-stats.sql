select json_build_object(
  'capturedAt', clock_timestamp(),
  'plannerSettings', json_build_object(
    'plan_cache_mode', current_setting('plan_cache_mode'),
    'random_page_cost', current_setting('random_page_cost'),
    'cpu_tuple_cost', current_setting('cpu_tuple_cost'),
    'cpu_index_tuple_cost', current_setting('cpu_index_tuple_cost'),
    'effective_cache_size', current_setting('effective_cache_size'),
    'work_mem', current_setting('work_mem'),
    'jit', current_setting('jit'),
    'max_parallel_workers_per_gather', current_setting('max_parallel_workers_per_gather')
  ),
  'databaseIdentity', json_build_object(
    'currentDatabase', current_database(),
    'currentUser', current_user,
    'sessionUser', session_user,
    'sessionUserIsDatabaseOwner', pg_has_role(
      session_user, (select datdba from pg_database where datname = current_database()), 'MEMBER'
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
    'pgStatStatementsExtensionInstalled', exists (select 1 from pg_extension where extname = 'pg_stat_statements'),
    'pgStatStatementsPreloaded', 'pg_stat_statements' = any (
      string_to_array(replace(current_setting('shared_preload_libraries'), ' ', ''), ',')
    ),
    'pgStatStatementsViewAvailable', to_regclass('public.pg_stat_statements') is not null
  ),
  'tables', coalesce(json_agg(row_to_json(stats) order by stats.relname), '[]'::json)
)
from (
  select
    schemaname,
    relname,
    seq_scan::text as seq_scan,
    seq_tup_read::text as seq_tup_read,
    idx_scan::text as idx_scan,
    idx_tup_fetch::text as idx_tup_fetch,
    n_tup_ins::text as n_tup_ins,
    n_tup_upd::text as n_tup_upd,
    n_tup_del::text as n_tup_del,
    n_live_tup::text as n_live_tup,
    n_dead_tup::text as n_dead_tup,
    last_analyze,
    last_autoanalyze,
    analyze_count::text as analyze_count,
    autoanalyze_count::text as autoanalyze_count,
    last_vacuum,
    last_autovacuum,
    vacuum_count::text as vacuum_count,
    autovacuum_count::text as autovacuum_count
  from pg_stat_user_tables
  where schemaname = 'public' and relname <> 'flyway_schema_history'
) stats;
