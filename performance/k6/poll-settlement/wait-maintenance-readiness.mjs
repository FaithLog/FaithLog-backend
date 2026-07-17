import { readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { TABLE_NAMES } from './evidence-contract.mjs';
import {
	evaluateMaintenanceReadinessObservations, validateMaintenanceReadinessContract,
	validateMaintenanceReadinessEvidence,
} from './maintenance-quiet-contract.mjs';

export function captureMaintenanceReadinessObservation(target, spawn = spawnSync) {
	const result = spawn('docker', [
		'exec', '-i', '-e', 'PGAPPNAME=faithlog-perf-192-maintenance-readiness-observer', target.containers.postgres.id,
		'psql', '-U', target.database.user, '-d', target.database.name, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-A', '-t',
	], { input: readinessSql(), encoding: 'utf8' });
	if (result.error || result.status !== 0) throw new Error('maintenance-readiness-collector-failed');
	const output = typeof result.stdout === 'string' ? result.stdout.trim() : '';
	if (!output) throw new Error('maintenance-readiness-collector-empty');
	try {
		const value = JSON.parse(output);
		if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
		return value;
	} catch { throw new Error('maintenance-readiness-collector-invalid'); }
}

export async function runMaintenanceReadinessGate({ target, contract, mode, capture = captureMaintenanceReadinessObservation, sleep = delay }) {
	validateMaintenanceReadinessContract(contract);
	const observations = [];
	while (true) {
		observations.push(capture(target));
		const state = evaluateMaintenanceReadinessObservations(observations, contract, mode);
		if (state.finalStatus !== 'pending') return state;
		await sleep(contract.pollIntervalSeconds * 1000);
	}
}

async function main() {
	const outputPath = resolve(required('MAINTENANCE_OUTPUT'));
	const mode = required('MODE');
	const expectedCase = { datasetId: required('PERF_DATASET_ID'), fixtureRunId: required('PERF_FIXTURE_RUN_ID'), executionRunId: required('PERF_EXECUTION_RUN_ID'), mode };
	const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
	const contract = target.maintenanceReadiness;
	validateMaintenanceReadinessContract(contract);
	for (const [name, key] of [
		['MAINTENANCE_POLL_INTERVAL_SECONDS', 'pollIntervalSeconds'], ['MAINTENANCE_QUIET_SECONDS', 'quietSeconds'], ['MAINTENANCE_TIMEOUT_SECONDS', 'timeoutSeconds'],
	]) if (exactInteger(name) !== contract[key]) throw new Error('maintenance readiness runtime contract mismatch');
	let state;
	try { state = await runMaintenanceReadinessGate({ target, contract, mode }); }
	catch (error) {
		const now = new Date().toISOString();
		state = { contract, startedAt: now, quietStartedAt: null, finishedAt: now, pollCount: 0, resetCount: 0, headroom: null, finalStatus: 'rejected', reason: sanitizedReason(error) };
	}
	const evidence = { case: expectedCase, ...state };
	writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
	if (state.finalStatus !== 'passed') throw new Error(state.reason);
	validateMaintenanceReadinessEvidence(evidence, expectedCase, contract);
}

function readinessSql() {
	const names = TABLE_NAMES.map((name) => `'${name}'`).join(',');
	return `
	WITH table_stats AS (
		SELECT relname, json_build_object(
			'lastAnalyze',${pgIso('last_analyze')},'lastAutoanalyze',${pgIso('last_autoanalyze')},
			'lastVacuum',${pgIso('last_vacuum')},'lastAutovacuum',${pgIso('last_autovacuum')},
			'analyzeCount',analyze_count::text,'autoanalyzeCount',autoanalyze_count::text,
			'vacuumCount',vacuum_count::text,'autovacuumCount',autovacuum_count::text
		) value FROM pg_stat_user_tables WHERE relname = ANY(ARRAY[${names}])
	), charge_relation AS (
		SELECT s.n_mod_since_analyze::text n_mod, c.reltuples::numeric::text reltuples,
			(SELECT option_value FROM pg_options_to_table(c.reloptions) WHERE option_name='autovacuum_enabled') relation_enabled,
			(SELECT option_value FROM pg_options_to_table(c.reloptions) WHERE option_name='autovacuum_analyze_threshold') relation_threshold,
			(SELECT option_value FROM pg_options_to_table(c.reloptions) WHERE option_name='autovacuum_analyze_scale_factor') relation_scale
		FROM pg_stat_user_tables s JOIN pg_class c ON c.oid=s.relid WHERE s.relname='charge_items'
	), workers AS (
		SELECT count(*)::int count FROM pg_stat_activity WHERE backend_type='autovacuum worker'
	)
	SELECT json_build_object(
		'capturedAt',to_char(clock_timestamp() AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
		'activeAutovacuumWorkers',(SELECT count FROM workers),
		'tables',(SELECT json_object_agg(relname,value ORDER BY relname) FROM table_stats),
		'chargeItems',(SELECT json_build_object(
			'nModSinceAnalyze',n_mod,'reltuples',reltuples,
			'globalAutovacuumEnabled',current_setting('autovacuum')::boolean,
			'relationAutovacuumEnabled',CASE WHEN relation_enabled IS NULL THEN NULL ELSE relation_enabled::boolean END,
			'globalBaseThreshold',current_setting('autovacuum_analyze_threshold'),
			'globalScaleFactor',current_setting('autovacuum_analyze_scale_factor'),
			'relationBaseThreshold',relation_threshold,'relationScaleFactor',relation_scale
		) FROM charge_relation)
	);
	`;
}
function pgIso(column) { return `CASE WHEN ${column} IS NULL THEN NULL ELSE to_char(${column} AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') END`; }
function sanitizedReason(error) {
	const reason = String(error?.message || '');
	return ['maintenance-readiness-collector-failed', 'maintenance-readiness-collector-empty', 'maintenance-readiness-collector-invalid', 'maintenance-readiness-timeout'].includes(reason) ? reason : 'maintenance-readiness-gate-failed';
}
function exactInteger(name) { const value = required(name); if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value))) throw new Error(`${name} is invalid.`); return Number(value); }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
function delay(milliseconds) { return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds)); }

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
