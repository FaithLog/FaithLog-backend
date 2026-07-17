with table_stats as (
  select relname, json_build_object(
    'lastAnalyze', case when last_analyze is null then null else to_char(last_analyze at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') end,
    'lastAutoanalyze', case when last_autoanalyze is null then null else to_char(last_autoanalyze at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') end,
    'lastVacuum', case when last_vacuum is null then null else to_char(last_vacuum at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') end,
    'lastAutovacuum', case when last_autovacuum is null then null else to_char(last_autovacuum at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') end,
    'analyzeCount', analyze_count::text,
    'autoanalyzeCount', autoanalyze_count::text,
    'vacuumCount', vacuum_count::text,
    'autovacuumCount', autovacuum_count::text,
    'nTupIns', n_tup_ins::text,
    'nTupUpd', n_tup_upd::text,
    'nTupDel', n_tup_del::text
  ) value
  from pg_stat_user_tables
  where schemaname = 'public' and relname <> 'flyway_schema_history'
), workers as (
  select count(*)::int count
  from pg_stat_activity
  where datname = current_database() and backend_type = 'autovacuum worker'
)
select json_build_object(
  'capturedAt', to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
  'activeAutovacuumWorkers', (select count from workers),
  'tables', (select json_object_agg(relname, value order by relname) from table_stats)
);
