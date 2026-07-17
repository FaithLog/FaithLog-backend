import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import {
	DATABASE_COUNTER_KEYS, MAINTENANCE_KEYS, PLANNER_KEYS, TABLE_COUNTER_KEYS, TABLE_NAMES,
	validateDbSnapshot, validatePgStatStatements,
} from './evidence-contract.mjs';

const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
const scope = required('EVIDENCE_SCOPE');
if (!['global', 'mode'].includes(scope)) throw new Error('EVIDENCE_SCOPE must be global or mode.');
const evidenceCase = { datasetId: required('PERF_DATASET_ID'), fixtureRunId: required('PERF_FIXTURE_RUN_ID'), executionRunId: required('PERF_EXECUTION_RUN_ID') };
if (scope === 'mode') evidenceCase.mode = required('MODE');
if (scope === 'global' && process.env.MODE) throw new Error('Global DB evidence must not include MODE.');

const db = JSON.parse(psql(`
	WITH table_stats AS (
		SELECT relname,
			json_build_object(
				'seq_scan',seq_scan::text,'seq_tup_read',seq_tup_read::text,'idx_scan',idx_scan::text,'idx_tup_fetch',idx_tup_fetch::text,
				'n_tup_ins',n_tup_ins::text,'n_tup_upd',n_tup_upd::text,'n_tup_del',n_tup_del::text,
				'analyze_count',analyze_count::text,'autoanalyze_count',autoanalyze_count::text,'vacuum_count',vacuum_count::text,'autovacuum_count',autovacuum_count::text,
				'maintenance',json_build_object('lastVacuum',${pgIso('last_vacuum')},'lastAutovacuum',${pgIso('last_autovacuum')},'lastAnalyze',${pgIso('last_analyze')},'lastAutoanalyze',${pgIso('last_autoanalyze')})
			) value
		FROM pg_stat_user_tables WHERE relname = ANY(ARRAY[${TABLE_NAMES.map((name) => `'${name}'`).join(',')}])
	), database_stats AS (
		SELECT stats_reset,xact_commit,xact_rollback,blks_read,blks_hit,tup_returned,tup_fetched,tup_inserted,tup_updated,tup_deleted,temp_files,temp_bytes
		FROM pg_stat_database WHERE datname=current_database()
	)
	SELECT json_build_object(
		'case',${caseSql(evidenceCase)},
		'statsReset',(SELECT ${pgIso('stats_reset')} FROM database_stats),
		'database',(SELECT json_build_object('xact_commit',xact_commit::text,'xact_rollback',xact_rollback::text,'blks_read',blks_read::text,'blks_hit',blks_hit::text,'tup_returned',tup_returned::text,'tup_fetched',tup_fetched::text,'tup_inserted',tup_inserted::text,'tup_updated',tup_updated::text,'tup_deleted',tup_deleted::text,'temp_files',temp_files::text,'temp_bytes',temp_bytes::text) FROM database_stats),
		'tables',(SELECT json_object_agg(relname,value ORDER BY relname) FROM table_stats),
		'planner',(SELECT json_object_agg(name,setting ORDER BY name) FROM pg_settings WHERE name = ANY(ARRAY[${PLANNER_KEYS.map((name) => `'${name}'`).join(',')}])),
		'activity',(SELECT coalesce(json_agg(json_build_object('pid',pid::text,'database',datname,'user',usename,'applicationName',application_name,'clientAddress',client_addr::text,'backendStartedAt',${pgIso('backend_start')},'state',state) ORDER BY pid),'[]'::json) FROM pg_stat_activity WHERE backend_type='client backend' AND pid<>pg_backend_pid())
	);
`));
assertCollectorSchema(db);
validateDbSnapshot(db, evidenceCase);

const installed = psql("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_stat_statements');") === 't';
let pgStatStatements;
if (!installed) {
	pgStatStatements = { case: evidenceCase, available: false, reason: 'extension-not-installed', rows: [] };
} else {
	const databaseId = psql('SELECT oid::text FROM pg_database WHERE datname=current_database();');
	const rows = JSON.parse(psql(`SELECT coalesce(json_agg(row_to_json(s) ORDER BY s."totalExecTimeMicros"::numeric DESC),'[]'::json) FROM (
		SELECT userid::text "userId",dbid::text "databaseId",queryid::text "queryId",toplevel "topLevel",calls::text calls,
			round(total_exec_time*1000)::bigint::text "totalExecTimeMicros",left(regexp_replace(query,'[[:space:]]+',' ','g'),500) query
		FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database())
			AND query NOT LIKE '%faithlog-perf-192-observer%'
	) s;`));
	pgStatStatements = { case: evidenceCase, available: true, databaseId, rows };
}
validatePgStatStatements(pgStatStatements, pgStatStatements, evidenceCase);
process.stdout.write(`${JSON.stringify({ db, pgStatStatements })}\n`);

function assertCollectorSchema(value) {
	if (Object.keys(value.database || {}).sort().join(',') !== [...DATABASE_COUNTER_KEYS].sort().join(',')) throw new Error('DB collector counter set mismatch.');
	if (Object.keys(value.tables || {}).sort().join(',') !== [...TABLE_NAMES].sort().join(',')) throw new Error('DB collector table set mismatch.');
	for (const table of Object.values(value.tables)) {
		const expected = [...TABLE_COUNTER_KEYS, 'maintenance'].sort().join(',');
		if (Object.keys(table).sort().join(',') !== expected || Object.keys(table.maintenance || {}).sort().join(',') !== [...MAINTENANCE_KEYS].sort().join(',')) throw new Error('DB collector table schema mismatch.');
	}
}
function pgIso(column) { return `CASE WHEN ${column} IS NULL THEN NULL ELSE to_char(${column} AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') END`; }
function caseSql(value) { return `json_build_object(${Object.entries(value).flatMap(([key, item]) => [`'${literal(key)}'`, `'${literal(item)}'`]).join(',')})`; }
function psql(sql) {
	const result = spawnSync('docker', ['exec', '-i', '-e', 'PGAPPNAME=faithlog-perf-192-observer', target.containers.postgres.id, 'psql', '-U', target.database.user, '-d', target.database.name, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-A', '-t'], { input: sql, encoding: 'utf8' });
	if (result.error || result.status !== 0) throw new Error(`psql process failed with exit status ${result.status ?? 'unknown'}.`);
	const output = result.stdout.trim();
	if (!output) throw new Error('Empty psql output.');
	return output;
}
function literal(value) { return value.replaceAll("'", "''"); }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
