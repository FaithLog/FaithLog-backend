import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, renameSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
	validateCase, validateDbEvidence, validateDbSnapshot, validateMetricEvidence,
	validatePgStatStatements, validateResourceEvidence, validateRuntimeContinuity,
} from './evidence-contract.mjs';
import { validateMaintenanceReadinessEvidence } from './maintenance-quiet-contract.mjs';
import { requireExactMode, SETTLEMENT_MODES, targetRoleIdentity, workloadContract } from './single-mode-contract.mjs';

const RUN_DIR = resolve(required('RUN_DIR'));
const MANIFEST_PATH = resolve(required('MANIFEST_PATH'));
const TARGET_CONTRACT_PATH = resolve(required('TARGET_CONTRACT'));
const manifest = JSON.parse(readFileSync(MANIFEST_PATH, 'utf8'));
const targetBytes = readFileSync(TARGET_CONTRACT_PATH);
const target = JSON.parse(targetBytes.toString('utf8'));
const MODE = requireExactMode(required('MODE'));
const expectedCase = { datasetId: manifest.datasetId, fixtureRunId: manifest.fixtureRunId, executionRunId: required('PERF_EXECUTION_RUN_ID') };
const modeCase = { ...expectedCase, mode: MODE };
const adoptionPath = resolve(RUN_DIR, 'baseline-adoption.json');

try {
	if (manifest.selectedMode !== MODE) throw new Error('selected mode manifest mismatch');
	assertNoForeignModeArtifacts();
	const initial = phase('runtime-initial.json', 'initial');
	const runtimeSnapshots = [phase('runtime-post-lock.json', 'post-lock'), phase(`${MODE}-runtime-before.json`, `${MODE}-before`), phase(`${MODE}-runtime-after.json`, `${MODE}-after`), phase('runtime-final.json', 'final')];
	validateRuntimeContinuity(initial, runtimeSnapshots, target, expectedCase);

	const verification = read('verification-report.json');
	validateCase(verification.case, modeCase);
	if (verification.passed !== true || !Array.isArray(verification.failures) || verification.failures.length) throw new Error('correctness verification rejected');
	const initialDb = read('db-initial.json');
	validateDbSnapshot(initialDb.db, expectedCase);
	validatePgStatStatements(initialDb.pgStatStatements, initialDb.pgStatStatements, expectedCase);
	const readiness = read(`${MODE}-maintenance-readiness.json`);
	validateMaintenanceReadinessEvidence(readiness, modeCase, target.maintenanceReadiness);
	const status = read(`${MODE}-status.json`);
	assertExactStatus(status, modeCase);
	const metricResult = validateMetricEvidence(read(`${MODE}-k6-evidence.json`), MODE, expectedCase);
	const before = read(`${MODE}-db-before.json`); const after = read(`${MODE}-db-after.json`);
	const dbDelta = validateDbEvidence(before.db, after.db, modeCase, initialDb.db);
	const statements = validatePgStatStatements(before.pgStatStatements, after.pgStatStatements, modeCase);
	if (initialDb.pgStatStatements.available !== statements.available) throw new Error('pg_stat_statements initial availability drift');
	const resources = ndjson(`${MODE}-resources.ndjson`);
	validateResourceEvidence(resources, MODE, expectedCase, target, metricResult.window);

	const provenance = {
		measurementSlot: 'PM-approved-one-server-one-mode-sequential-lock', evidenceScope: 'strict-boundary-snapshots',
		continuousExternalActivityObserver: false, continuousExclusivityProven: false,
		adoptionMeaning: 'conditional-on-PM-slot-and-boundary-continuity',
		limitation: 'The lock coordinates approved runners and DB activity is exact only at captured boundaries; transient external read-only activity between boundaries is not continuously proven absent.',
	};
	const metrics = {
		measuredRequestCount: metricResult.count, latencyMs: metricResult.latencyMs, throughputRps: metricResult.throughput, failureRate: metricResult.failureRate,
		measuredWindow: { startedAt: new Date(metricResult.window.startMs).toISOString(), finishedAt: new Date(metricResult.window.endMs).toISOString() },
		postgres: { counterDelta: dbDelta, pgStatStatements: statements }, resourcePeaks: peaks(resources),
		maintenanceReadiness: { startedAt: readiness.startedAt, quietStartedAt: readiness.quietStartedAt, finishedAt: readiness.finishedAt, pollCount: readiness.pollCount, resetCount: readiness.resetCount, headroom: readiness.headroom },
	};
	const summary = {
		case: modeCase, mode: MODE, sourceCommit: target.sourceCommit,
		targetIdentitySha256: createHash('sha256').update(targetBytes).digest('hex'), targetRoleIdentity: targetRoleIdentity(target),
		workloadContract: workloadContract(target), seed: manifest.seed, memberCount: manifest.memberCount,
		generatedAt: new Date().toISOString(), provenance, pgStatStatementsAvailable: statements.available, metrics,
	};
	writeAtomic(resolve(RUN_DIR, 'baseline-summary.json'), `${JSON.stringify(summary, null, 2)}\n`);
	writeAtomic(resolve(RUN_DIR, 'baseline-summary.md'), markdown(summary));
	writeFileSync(adoptionPath, `${JSON.stringify({ case: modeCase, accepted: false, automaticAdoption: false, evidenceIntegrity: 'validated', measurementStatus: 'conditional-boundary-only', validatedAt: new Date().toISOString(), acceptanceScope: provenance.evidenceScope, continuousExclusivityProven: false, conditionalSummaryAvailable: true, summaryFiles: ['baseline-summary.json', 'baseline-summary.md'] }, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
} catch (error) {
	writeFileSync(adoptionPath, `${JSON.stringify({ case: modeCase, accepted: false, automaticAdoption: false, evidenceIntegrity: 'rejected', measurementStatus: 'rejected', rejectedAt: new Date().toISOString(), reasons: [sanitizedReason(error)], secretsIncluded: false }, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
	throw error;
}

function assertNoForeignModeArtifacts() {
	for (const name of readdirSync(RUN_DIR)) for (const mode of SETTLEMENT_MODES) if (mode !== MODE && name.startsWith(`${mode}-`)) throw new Error('foreign-mode-artifact');
}
function read(name) { try { return JSON.parse(readFileSync(resolve(RUN_DIR, name), 'utf8')); } catch { throw new Error('selected-evidence-missing-or-invalid'); } }
function phase(name, expected) { const value = read(name); if (value.phase !== expected) throw new Error('runtime-phase-mismatch'); return value; }
function ndjson(name) { let text; try { text = readFileSync(resolve(RUN_DIR, name), 'utf8').trim(); } catch { throw new Error('selected-evidence-missing-or-invalid'); } if (!text) throw new Error('selected-evidence-missing-or-invalid'); try { return text.split('\n').map((line) => JSON.parse(line)); } catch { throw new Error('selected-evidence-missing-or-invalid'); } }
function writeAtomic(path, content) { const temporary = `${path}.tmp`; writeFileSync(temporary, content, { flag: 'wx' }); renameSync(temporary, path); }
function assertExactStatus(value, expected) { if (JSON.stringify(Object.keys(value).sort()) !== JSON.stringify(['case', 'exitStatus'].sort())) throw new Error('mode-status-schema'); validateCase(value.case, expected); if (value.exitStatus !== 0) throw new Error('mode-execution-failed'); }
function peaks(samples) { const result = {}; for (const role of ['app', 'postgres', 'redis']) { let maxCpuPercent = -1; let memoryUsedBytesAtMaxCpu = '0'; let maxMemoryUsedBytes = 0n; for (const sample of samples) { const value = sample.roles[role]; if (value.cpuPercent > maxCpuPercent) { maxCpuPercent = value.cpuPercent; memoryUsedBytesAtMaxCpu = value.memoryUsedBytes; } const memory = BigInt(value.memoryUsedBytes); if (memory > maxMemoryUsedBytes) maxMemoryUsedBytes = memory; } result[role] = { containerId: samples[0].roles[role].containerId, maxCpuPercent, memoryUsedBytesAtMaxCpu, maxMemoryUsedBytes: String(maxMemoryUsedBytes) }; } return result; }
function markdown(data) { const value = data.metrics; return [`# ${data.case.datasetId} Issue #192 ${data.mode} before baseline`, '', `- Fixture: ${data.case.fixtureRunId}`, `- Execution: ${data.case.executionRunId}`, `- Mode: ${data.mode}`, `- ACTIVE members: ${data.memberCount}`, `- Evidence: ${data.provenance.evidenceScope}`, '', '| count | p50 ms | p95 ms | p99 ms | max ms | throughput req/s | failure |', '| ---: | ---: | ---: | ---: | ---: | ---: | ---: |', `| ${value.measuredRequestCount} | ${n(value.latencyMs.p50)} | ${n(value.latencyMs.p95)} | ${n(value.latencyMs.p99)} | ${n(value.latencyMs.max)} | ${n(value.throughputRps)} | ${n(value.failureRate)} |`, ''].join('\n'); }
function n(value) { return Number(value).toFixed(3); }
function sanitizedReason(error) { const value = String(error?.message || '').toLowerCase(); return /^[a-z0-9-]{1,80}$/.test(value) ? value : 'summary-validation-failed'; }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
