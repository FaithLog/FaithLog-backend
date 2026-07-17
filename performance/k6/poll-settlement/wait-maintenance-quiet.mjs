import { readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { TABLE_NAMES } from './evidence-contract.mjs';
import {
	evaluateMaintenanceQuietObservations, validateMaintenanceObservation,
	validateMaintenanceQuietContract, validateMaintenanceStabilityEvidence,
} from './maintenance-quiet-contract.mjs';

export function captureMaintenanceObservation(target, spawn = spawnSync) {
	const sql = maintenanceSql();
	const result = spawn('docker', [
		'exec', '-i', '-e', 'PGAPPNAME=faithlog-perf-192-maintenance-observer', target.containers.postgres.id,
		'psql', '-U', target.database.user, '-d', target.database.name, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-A', '-t',
	], { input: sql, encoding: 'utf8' });
	if (result.error || result.status !== 0) throw new Error('maintenance-collector-failed');
	const output = typeof result.stdout === 'string' ? result.stdout.trim() : '';
	if (!output) throw new Error('maintenance-collector-empty');
	try { return validateMaintenanceObservation(JSON.parse(output)); }
	catch { throw new Error('maintenance-collector-invalid'); }
}

export async function runMaintenanceQuietGate({ target, contract, capture = captureMaintenanceObservation, sleep = delay }) {
	validateMaintenanceQuietContract(contract);
	const observations = [];
	while (true) {
		observations.push(capture(target));
		const state = evaluateMaintenanceQuietObservations(observations, contract);
		if (state.finalStatus !== 'pending') return state;
		await sleep(contract.pollIntervalSeconds * 1000);
	}
}

async function main() {
	const outputPath = resolve(required('MAINTENANCE_OUTPUT'));
	const expectedCase = { datasetId: required('PERF_DATASET_ID'), fixtureRunId: required('PERF_FIXTURE_RUN_ID'), executionRunId: required('PERF_EXECUTION_RUN_ID') };
	const contract = {
		pollIntervalSeconds: exactInteger('MAINTENANCE_POLL_INTERVAL_SECONDS'),
		quietSeconds: exactInteger('MAINTENANCE_QUIET_SECONDS'),
		timeoutSeconds: exactInteger('MAINTENANCE_TIMEOUT_SECONDS'),
	};
	validateMaintenanceQuietContract(contract);
	const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
	let state;
	try {
		state = await runMaintenanceQuietGate({ target, contract });
	} catch (error) {
		const now = new Date().toISOString();
		state = { contract, startedAt: now, quietStartedAt: null, finishedAt: now, pollCount: 0, resetCount: 0, finalStatus: 'rejected', reason: sanitizedReason(error) };
	}
	const evidence = { case: expectedCase, ...state };
	writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
	if (state.finalStatus !== 'passed') throw new Error(state.reason);
	validateMaintenanceStabilityEvidence(evidence, expectedCase, contract);
}

function maintenanceSql() {
	const names = TABLE_NAMES.map((name) => `'${name}'`).join(',');
	return `
	WITH table_stats AS (
		SELECT relname, json_build_object(
			'lastAnalyze',${pgIso('last_analyze')},'lastAutoanalyze',${pgIso('last_autoanalyze')},
			'lastVacuum',${pgIso('last_vacuum')},'lastAutovacuum',${pgIso('last_autovacuum')},
			'analyzeCount',analyze_count::text,'autoanalyzeCount',autoanalyze_count::text,
			'vacuumCount',vacuum_count::text,'autovacuumCount',autovacuum_count::text
		) value FROM pg_stat_user_tables WHERE relname = ANY(ARRAY[${names}])
	), workers AS (
		SELECT count(*)::int count FROM pg_stat_activity WHERE backend_type='autovacuum worker'
	)
	SELECT json_build_object(
		'capturedAt',to_char(clock_timestamp() AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
		'activeAutovacuumWorkers',(SELECT count FROM workers),
		'tables',(SELECT json_object_agg(relname,value ORDER BY relname) FROM table_stats)
	);
	`;
}
function pgIso(column) { return `CASE WHEN ${column} IS NULL THEN NULL ELSE to_char(${column} AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') END`; }
function sanitizedReason(error) {
	const reason = String(error?.message || '');
	return ['maintenance-collector-failed', 'maintenance-collector-empty', 'maintenance-collector-invalid', 'maintenance-quiet-timeout'].includes(reason) ? reason : 'maintenance-gate-failed';
}
function exactInteger(name) { const value = required(name); if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value))) throw new Error(`${name} is invalid.`); return Number(value); }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
function delay(milliseconds) { return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds)); }

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
