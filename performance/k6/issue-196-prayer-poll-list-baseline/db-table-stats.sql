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
    'serverAddress', inet_server_addr(),
    'serverPort', inet_server_port(),
    'postmasterStartedAt', pg_postmaster_start_time()
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
  where schemaname = 'public'
) stats;
