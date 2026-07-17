import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
	validateDbEvidence, validateDbSnapshot, validateMetricEvidence, validatePgStatStatements,
	validateResourceEvidence, validateRuntimeContinuity, validateWarmupEvidence,
} from './evidence-contract.mjs';
import { validateMaintenanceReadinessEvidence, validateMaintenanceStabilityEvidence } from './maintenance-quiet-contract.mjs';

const command = process.argv[2];
const expectedCase = { datasetId: required('PERF_DATASET_ID'), fixtureRunId: required('PERF_FIXTURE_RUN_ID'), executionRunId: required('PERF_EXECUTION_RUN_ID') };

try {
	if (command === 'runtime-initial') {
		const target = read(process.argv[3]); const snapshot = read(process.argv[4]);
		validateRuntimeContinuity(snapshot, [], target, expectedCase);
	} else if (command === 'runtime') {
		const target = read(process.argv[3]); const initial = read(process.argv[4]); const snapshot = read(process.argv[5]); const phase = process.argv[6];
		if (!phase || snapshot.phase !== phase) throw new Error(`runtime phase mismatch: ${phase}`);
		validateRuntimeContinuity(initial, [snapshot], target, expectedCase);
	} else if (command === 'db-snapshot') {
		const evidence = read(process.argv[3]); const mode = process.argv[4];
		const modeCase = mode === 'global' ? expectedCase : { ...expectedCase, mode };
		validateDbSnapshot(evidence.db, modeCase);
		validatePgStatStatements(evidence.pgStatStatements, evidence.pgStatStatements, modeCase);
	} else if (command === 'db-pair') {
		const mode = process.argv[6]; const modeCase = { ...expectedCase, mode };
		const before = read(process.argv[3]); const after = read(process.argv[4]); const initial = read(process.argv[5]);
		validateDbEvidence(before.db, after.db, modeCase, initial.db);
		validatePgStatStatements(before.pgStatStatements, after.pgStatStatements, modeCase);
	} else if (command === 'mode') {
		const target = read(process.argv[3]); const metricEvidence = read(process.argv[4]); const resources = ndjson(process.argv[5]); const mode = process.argv[6];
		const metric = validateMetricEvidence(metricEvidence, mode, expectedCase);
		validateResourceEvidence(resources, mode, expectedCase, target, metric.window);
	} else if (command === 'warmup') {
		validateWarmupEvidence(read(process.argv[3]), process.argv[4], expectedCase);
	} else if (command === 'maintenance') {
		const target = read(process.argv[3]);
		validateMaintenanceStabilityEvidence(read(process.argv[4]), expectedCase, target.maintenanceQuietGate);
	} else if (command === 'maintenance-readiness') {
		const target = read(process.argv[3]); const mode = process.argv[5];
		validateMaintenanceReadinessEvidence(read(process.argv[4]), { ...expectedCase, mode }, target.maintenanceReadiness);
	} else {
		throw new Error(`Unknown validation command: ${command}`);
	}
} catch (error) {
	writeRejection(error);
	throw error;
}

function read(path) { if (!path) throw new Error('Evidence path is required.'); return JSON.parse(readFileSync(resolve(path), 'utf8')); }
function ndjson(path) { if (!path) throw new Error('Resource path is required.'); const text = readFileSync(resolve(path), 'utf8').trim(); if (!text) throw new Error('Resource evidence is empty.'); return text.split('\n').map((line) => JSON.parse(line)); }
function writeRejection(error) {
	const path = process.env.VALIDATION_REJECTION_PATH;
	if (!path) return;
	const stage = required('VALIDATION_STAGE');
	if (!/^[a-z0-9-]{1,80}$/.test(stage)) throw new Error('VALIDATION_STAGE is invalid.');
	const reason = sanitizedReason(error);
	const rejection = { case: expectedCase, accepted: false, automaticAdoption: false, evidenceIntegrity: 'rejected', measurementStatus: 'rejected', rejectedAt: new Date().toISOString(), stage, reasons: [reason], secretsIncluded: false };
	writeFileSync(resolve(path), `${JSON.stringify(rejection, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}
function sanitizedReason(error) {
	const message = String(error?.message || '').toLowerCase();
	if (!/^(case|metric|metrics|failure|http failure|checks|iteration|latency|throughput|resource|activity)[a-z0-9 _():.-]{0,90}$/.test(message)) return 'validation-failed';
	return message.replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'validation-failed';
}
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
