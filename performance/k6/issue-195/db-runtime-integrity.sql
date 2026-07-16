\pset tuples_only on
\pset format unaligned

/* faithlog_issue195_runtime_integrity_observer */
with database_stats as (
    select xact_commit, xact_rollback
    from pg_stat_database
    where datname = current_database()
), external_activity as (
    select count(*)::bigint as active_sessions
    from pg_stat_activity
    where datname = current_database()
      and pid <> pg_backend_pid()
      and state <> 'idle'
), planner as (
    select jsonb_object_agg(name, setting order by name) as settings
    from pg_settings
    where name in (
        'effective_cache_size',
        'enable_hashjoin',
        'enable_indexscan',
        'enable_mergejoin',
        'enable_nestloop',
        'plan_cache_mode',
        'random_page_cost',
        'seq_page_cost',
        'work_mem'
    )
), maintenance as (
    select jsonb_object_agg(
        relname,
        jsonb_build_object(
            'analyzeCount', analyze_count,
            'autoanalyzeCount', autoanalyze_count,
            'lastAnalyze', last_analyze,
            'lastAutoanalyze', last_autoanalyze,
            'vacuumCount', vacuum_count,
            'autovacuumCount', autovacuum_count,
            'lastVacuum', last_vacuum,
            'lastAutovacuum', last_autovacuum
        ) order by relname
    ) as tables
    from pg_stat_user_tables
    where relname in ('users', 'campuses', 'campus_members', 'campus_duty_assignments')
)
select jsonb_build_object(
    'database', current_database(),
    'observerApplicationName', current_setting('application_name'),
    'externalActiveSessions', external_activity.active_sessions,
    'databaseStats', jsonb_build_object(
        'xactCommit', database_stats.xact_commit::text,
        'xactRollback', database_stats.xact_rollback::text
    ),
    'plannerSettings', planner.settings,
    'tableMaintenance', maintenance.tables,
    'observerOverhead', jsonb_build_object(
        'beforeSnapshotCommitIncludedInDelta', true,
        'expectedCommitCount', 1
    )
)::text
from database_stats, external_activity, planner, maintenance;
