import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { parseDockerStatsRows } from './resource-contract.mjs';

try {
	const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
	const mode = required('MODE');
	const output = resolve(required('RESOURCE_OUTPUT'));
	const evidenceCase = { datasetId: required('PERF_DATASET_ID'), fixtureRunId: required('PERF_FIXTURE_RUN_ID'), executionRunId: required('PERF_EXECUTION_RUN_ID'), mode };
	const ids = ['app', 'postgres', 'redis'].map((role) => target.containers[role].id);
	const result = spawnSync('docker', ['stats', '--no-stream', '--no-trunc', '--format', '{{json .}}', ...ids], { encoding: 'utf8' });
	if (result.status !== 0) throw new Error('docker stats process failed');
	const rows = result.stdout.trim().split('\n').filter(Boolean).map(JSON.parse);
	const roles = parseDockerStatsRows(rows, target);
	appendFileSync(output, `${JSON.stringify({
		case: evidenceCase,
		capturedAt: new Date().toISOString(),
		samplingIntervalMs: target.resourceSampling.samplingIntervalMs,
		maxGapMs: target.resourceSampling.maxGapMs,
		roles,
	})}\n`);
} catch {
	writeRejection();
	throw new Error('resource capture failed');
}

function writeRejection() {
	const path = process.env.RESOURCE_REJECTION_PATH; if (!path || existsSync(resolve(path))) return;
	const stage = required('RESOURCE_STAGE');
	if (!/^[a-z0-9-]{1,80}$/.test(stage)) throw new Error('resource rejection stage invalid');
	const evidenceCase = { datasetId: required('PERF_DATASET_ID'), fixtureRunId: required('PERF_FIXTURE_RUN_ID'), executionRunId: required('PERF_EXECUTION_RUN_ID') };
	const rejection = { case: evidenceCase, accepted: false, automaticAdoption: false, evidenceIntegrity: 'rejected', measurementStatus: 'rejected', rejectedAt: new Date().toISOString(), stage, reasons: ['resource-capture-failed'], secretsIncluded: false };
	try { writeFileSync(resolve(path), `${JSON.stringify(rejection, null, 2)}\n`, { flag: 'wx', mode: 0o600 }); }
	catch (error) { if (error?.code !== 'EEXIST') throw new Error('resource rejection write failed'); }
}

function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
