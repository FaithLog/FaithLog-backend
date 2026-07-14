select json_build_object(
  'capturedAt', clock_timestamp(),
  'tables', coalesce(json_agg(row_to_json(stats) order by stats.relname), '[]'::json)
)
from (
  select
    schemaname,
    relname,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch,
    n_tup_ins,
    n_tup_upd,
    n_tup_del,
    n_live_tup,
    n_dead_tup
  from pg_stat_user_tables
  where schemaname = 'public'
) stats;
