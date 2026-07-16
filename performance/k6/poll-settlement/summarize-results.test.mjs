import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { DATABASE_COUNTER_KEYS, MAINTENANCE_KEYS, PLANNER_KEYS, TABLE_COUNTER_KEYS, TABLE_NAMES } from './evidence-contract.mjs';

const SCRIPT = resolve(new URL('summarize-results.mjs', import.meta.url).pathname);
const VALIDATOR = resolve(new URL('validate-evidence.mjs', import.meta.url).pathname);
const RUNNER = resolve(new URL('run-baseline.sh', import.meta.url).pathname);
const CASE = { datasetId: 'PERFORMANCE_192_FAKE', fixtureRunId: 'FAKE_192', executionRunId: 'EXEC192_FAKE' };
const MODES = ['coffee-sequential', 'meal-sequential', 'coffee-concurrent', 'meal-concurrent'];
const MODE = 'coffee-sequential';
const TARGET = {
	contractType: 'before', sourceCommit: '3'.repeat(40), baseUrl: 'http://127.0.0.1:28080', flywayVersion: '11',
	containers: {
		app: container('a', '1', 'app', '4', '28080:8080/tcp'),
		postgres: container('b', '2', 'postgres', '5', '25432:5432/tcp'),
		redis: container('c', '3', 'redis', '6', '26379:6379/tcp'),
	},
	database: { name: 'faithlog', user: 'faithlog', serverAddress: null, serverPort: null, postmasterStartedAt: '2026-07-15T08:32:52.000Z', statsReset: null },
	redis: { runId: 'fake-run-id', serverPort: 6379 }, resourceSampling: { minSamples: 2, samplingIntervalMs: 1000, maxGapMs: 3000 },
	maintenanceReadiness: { pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180, expectedChargeWrites: { 'coffee-sequential': 11000, 'meal-sequential': 11000, 'coffee-concurrent': 6000, 'meal-concurrent': 6000 } },
};

test('fake orchestration creates a conditional non-adoptable summary only after every strict gate passes', () => {
	const fixture = fakeRun();
	try {
		const result = summarize(fixture);
		assert.equal(result.status, 0, result.stderr);
		const adoption = read(fixture.runDir, 'baseline-adoption.json');
		assert.equal(adoption.accepted, false);
		assert.equal(adoption.automaticAdoption, false);
		assert.equal(adoption.evidenceIntegrity, 'validated');
		assert.equal(adoption.measurementStatus, 'conditional-boundary-only');
		assert.doesNotMatch(JSON.stringify(adoption), /"accepted":true/);
		assert.equal(read(fixture.runDir, 'baseline-summary.json').provenance.continuousExclusivityProven, false);
		assert.equal(read(fixture.runDir, 'baseline-summary.json').mode, MODE);
		assert.equal(read(fixture.runDir, 'baseline-summary.json').metrics.measuredRequestCount, 10);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('fake orchestration rejects runtime, metric, DB, resource, and correctness drift without summary output', () => {
	for (const mutate of [
		(f) => { rmSync(resolve(f.runDir, 'coffee-sequential-maintenance-readiness.json')); },
		(f) => { rmSync(resolve(f.runDir, 'coffee-sequential-runtime-after.json')); },
		(f) => { const value = read(f.runDir, 'runtime-final.json'); value.redis.runId = 'replacement'; write(f.runDir, 'runtime-final.json', value); },
		(f) => { const value = read(f.runDir, 'coffee-sequential-k6-evidence.json'); value.metrics.coffee_settlement_requests.values.count = 9; write(f.runDir, 'coffee-sequential-k6-evidence.json', value); },
		(f) => { const value = read(f.runDir, 'coffee-sequential-db-after.json'); delete value.db.planner.work_mem; write(f.runDir, 'coffee-sequential-db-after.json', value); },
		(f) => { const path = resolve(f.runDir, 'coffee-sequential-resources.ndjson'); const rows = readFileSync(path, 'utf8').trim().split('\n').map(JSON.parse); rows[0].roles.app.containerId = 'f'.repeat(64); writeFileSync(path, `${rows.map(JSON.stringify).join('\n')}\n`); },
		(f) => { const value = read(f.runDir, 'verification-report.json'); value.passed = false; value.failures = ['foreign API item']; write(f.runDir, 'verification-report.json', value); },
	]) {
		const fixture = fakeRun();
		try {
			mutate(fixture);
			const result = summarize(fixture);
			assert.notEqual(result.status, 0);
			const adoption = read(fixture.runDir, 'baseline-adoption.json');
			assert.equal(adoption.accepted, false);
			assert.equal(adoption.automaticAdoption, false);
			assert.equal(adoption.evidenceIntegrity, 'rejected');
			assert.notEqual(adoption.measurementStatus, 'conditional-boundary-only');
			assert.equal(exists(fixture.runDir, 'baseline-summary.json'), false);
		} finally { rmSync(fixture.root, { recursive: true, force: true }); }
	}
});

test('summarizer rejects a forged mode readiness gate whose last reset leaves no proven 30-second quiet interval', () => {
	const fixture = fakeRun();
	try {
		const evidence = read(fixture.runDir, 'coffee-sequential-maintenance-readiness.json');
		evidence.resetCount = 6;
		write(fixture.runDir, 'coffee-sequential-maintenance-readiness.json', evidence);
		const result = summarize(fixture);
		assert.notEqual(result.status, 0);
		assert.equal(exists(fixture.runDir, 'baseline-summary.json'), false);
		const adoption = read(fixture.runDir, 'baseline-adoption.json');
		assert.equal(adoption.accepted, false);
		assert.notEqual(adoption.evidenceIntegrity, 'validated');
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('summarizer requires selected readiness and rejects forged or foreign mode evidence', () => {
	for (const mutate of [
		(f) => { rmSync(resolve(f.runDir, `${MODE}-maintenance-readiness.json`)); },
		(f) => { const value = read(f.runDir, `${MODE}-maintenance-readiness.json`); value.headroom.expectedWrites = '6000'; write(f.runDir, `${MODE}-maintenance-readiness.json`, value); },
		(f) => { write(f.runDir, 'meal-concurrent-status.json', { case: { ...CASE, mode: 'meal-concurrent' }, exitStatus: 0 }); },
	]) {
		const fixture = fakeRun();
		try {
			mutate(fixture);
			const result = summarize(fixture);
			assert.notEqual(result.status, 0);
			assert.equal(exists(fixture.runDir, 'baseline-summary.json'), false);
			assert.notEqual(read(fixture.runDir, 'baseline-adoption.json').evidenceIntegrity, 'validated');
		} finally { rmSync(fixture.root, { recursive: true, force: true }); }
	}
});

test('runner order rejects a runtime replacement that occurs during correctness before any validated classification', () => {
	const fixture = fakeRun();
	try {
		const runner = readFileSync(RUNNER, 'utf8');
		const operations = [
			{ name: 'correctness', at: runner.lastIndexOf('verify-baseline.mjs') },
			{ name: 'final-runtime', at: runner.lastIndexOf('runtime-final.json') },
			{ name: 'classification', at: runner.lastIndexOf('summarize-results.mjs') },
		].sort((left, right) => left.at - right.at);
		let observed = structuredClone(runtime('final'));
		let status = 0;
		for (const operation of operations) {
			if (operation.name === 'correctness') {
				write(fixture.runDir, 'verification-report.json', { case: CASE, passed: true, failures: [] });
				observed.containers.app.id = 'f'.repeat(64);
			} else if (operation.name === 'final-runtime') {
				write(fixture.runDir, 'runtime-final.json', observed);
				status = validateFinalRuntime(fixture).status;
				if (status !== 0) break;
			} else {
				status = summarize(fixture).status;
				if (status !== 0) break;
			}
		}
		assert.notEqual(status, 0, 'replacement after correctness must fail the final runtime gate');
		assert.equal(exists(fixture.runDir, 'baseline-summary.json'), false);
		if (exists(fixture.runDir, 'baseline-adoption.json')) {
			const adoption = read(fixture.runDir, 'baseline-adoption.json');
			assert.notEqual(adoption.evidenceIntegrity, 'validated');
			assert.equal(adoption.accepted, false);
		}
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('runner preserves the first sanitized mode-validator rejection instead of summarizing missing future modes', () => {
	const fixture = fakeRun();
	try {
		writeFileSync(resolve(fixture.runDir, 'coffee-sequential-resources.ndjson'), `${resources('coffee-sequential').map((sample, index) => ({ ...sample, capturedAt: index === 0 ? '2026-07-15T14:06:39.000Z' : '2026-07-15T14:06:40.400Z' })).map(JSON.stringify).join('\n')}\n`);
		const adoptionPath = resolve(fixture.runDir, 'baseline-adoption.json');
		const validation = spawnSync(process.execPath, [VALIDATOR, 'mode', fixture.targetPath, resolve(fixture.runDir, 'coffee-sequential-k6-evidence.json'), resolve(fixture.runDir, 'coffee-sequential-resources.ndjson'), 'coffee-sequential'], {
			encoding: 'utf8',
			env: { ...process.env, PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId, VALIDATION_REJECTION_PATH: adoptionPath, VALIDATION_STAGE: 'coffee-sequential-mode-validator' },
		});
		assert.notEqual(validation.status, 0);
		const adoption = read(fixture.runDir, 'baseline-adoption.json');
		assert.equal(adoption.accepted, false);
		assert.equal(adoption.evidenceIntegrity, 'rejected');
		assert.equal(adoption.stage, 'coffee-sequential-mode-validator');
		assert.deepEqual(adoption.reasons, ['resource-boundary-coverage']);
		assert.equal(adoption.secretsIncluded, false);
		assert.equal(exists(fixture.runDir, 'baseline-summary.json'), false);
		assert.equal(exists(fixture.runDir, 'meal-sequential-k6-evidence.json'), false);

		const runner = readFileSync(RUNNER, 'utf8');
		const failedRunExit = runner.indexOf('[[ ${RUN_STATUS} -eq 0 ]] || exit 1');
		const summarizer = runner.lastIndexOf('summarize-results.mjs');
		assert.ok(failedRunExit > 0 && failedRunExit < summarizer, 'failed mode must exit with its existing rejection before the full summarizer reads future modes');
		assert.deepEqual(read(fixture.runDir, 'baseline-adoption.json').reasons, ['resource-boundary-coverage']);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('machine-readable validator rejection never includes a raw evidence path or error text', () => {
	const fixture = fakeRun();
	try {
		const adoptionPath = resolve(fixture.runDir, 'baseline-adoption.json');
		const missingPath = resolve(fixture.root, 'raw-secret-password-resource.ndjson');
		const validation = spawnSync(process.execPath, [VALIDATOR, 'mode', fixture.targetPath, resolve(fixture.runDir, 'coffee-sequential-k6-evidence.json'), missingPath, 'coffee-sequential'], {
			encoding: 'utf8',
			env: { ...process.env, PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId, VALIDATION_REJECTION_PATH: adoptionPath, VALIDATION_STAGE: 'coffee-sequential-mode-validator' },
		});
		assert.notEqual(validation.status, 0);
		const adoptionText = readFileSync(adoptionPath, 'utf8');
		const adoption = JSON.parse(adoptionText);
		assert.deepEqual(adoption.reasons, ['validation-failed']);
		assert.doesNotMatch(adoptionText, /raw-secret-password|faithlog-192-summary|ENOENT|no such file/i);
		assert.equal(adoption.secretsIncluded, false);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('DB pair activity rejection preserves its sanitized first stage and reason', () => {
	const fixture = fakeRun();
	try {
		const afterPath = resolve(fixture.runDir, 'coffee-sequential-db-after.json');
		const after = read(fixture.runDir, 'coffee-sequential-db-after.json');
		after.db.activity[0].applicationName = 'foreign-pool';
		write(fixture.runDir, 'coffee-sequential-db-after.json', after);
		const adoptionPath = resolve(fixture.runDir, 'baseline-adoption.json');
		const validation = spawnSync(process.execPath, [VALIDATOR, 'db-pair', resolve(fixture.runDir, 'coffee-sequential-db-before.json'), afterPath, resolve(fixture.runDir, 'db-initial.json'), 'coffee-sequential'], {
			encoding: 'utf8',
			env: { ...process.env, PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId, VALIDATION_REJECTION_PATH: adoptionPath, VALIDATION_STAGE: 'coffee-sequential-db-pair-validator' },
		});
		assert.notEqual(validation.status, 0);
		const adoptionText = readFileSync(adoptionPath, 'utf8');
		const adoption = JSON.parse(adoptionText);
		assert.equal(adoption.stage, 'coffee-sequential-db-pair-validator');
		assert.deepEqual(adoption.reasons, ['activity-drift']);
		assert.equal(adoption.secretsIncluded, false);
		assert.doesNotMatch(adoptionText, /foreign-pool|coffee-sequential-db-after|password|stderr/i);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

function fakeRun() {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-summary-')); const runDir = resolve(root, 'run');
	mkdirSync(runDir);
	const manifestPath = resolve(root, 'manifest.json'); const targetPath = resolve(root, 'target.json');
	writeFileSync(manifestPath, `${JSON.stringify({ ...CASE, selectedMode: MODE, seed: 192000, memberCount: 1000 })}\n`);
	writeFileSync(targetPath, `${JSON.stringify(TARGET)}\n`);
	write(runDir, 'runtime-initial.json', runtime('initial'));
	write(runDir, 'runtime-post-lock.json', runtime('post-lock'));
	write(runDir, 'db-initial.json', dbWrapper(CASE));
	for (const mode of [MODE]) {
		write(runDir, `${mode}-maintenance-readiness.json`, readiness(mode));
		write(runDir, `${mode}-runtime-before.json`, runtime(`${mode}-before`));
		write(runDir, `${mode}-runtime-after.json`, runtime(`${mode}-after`));
		write(runDir, `${mode}-db-before.json`, dbWrapper({ ...CASE, mode }));
		write(runDir, `${mode}-db-after.json`, dbWrapper({ ...CASE, mode }));
		write(runDir, `${mode}-k6-evidence.json`, metrics(mode));
		write(runDir, `${mode}-status.json`, { case: { ...CASE, mode }, exitStatus: 0 });
		writeFileSync(resolve(runDir, `${mode}-resources.ndjson`), `${resources(mode).map(JSON.stringify).join('\n')}\n`);
	}
	write(runDir, 'runtime-final.json', runtime('final'));
	write(runDir, 'verification-report.json', { case: { ...CASE, mode: MODE }, passed: true, failures: [] });
	return { root, runDir, manifestPath, targetPath };
}
function readiness(mode) { const expectedWrites = String(TARGET.maintenanceReadiness.expectedChargeWrites[mode]); return { case: { ...CASE, mode }, contract: TARGET.maintenanceReadiness, startedAt: '2026-07-15T14:06:00.000Z', quietStartedAt: '2026-07-15T14:06:00.000Z', finishedAt: '2026-07-15T14:06:30.000Z', pollCount: 7, resetCount: 0, headroom: { nModSinceAnalyze: '0', reltuples: '200000', effectiveBaseThreshold: '50', effectiveScaleFactor: { numerator: '1', denominator: '10' }, triggerAt: '20051', expectedWrites, projectedModifications: expectedWrites, sufficient: true }, finalStatus: 'passed', reason: 'maintenance-readiness-achieved' }; }
function summarize(fixture) { return spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8', env: { ...process.env, RUN_DIR: fixture.runDir, MANIFEST_PATH: fixture.manifestPath, TARGET_CONTRACT: fixture.targetPath, PERF_EXECUTION_RUN_ID: CASE.executionRunId, MODE } }); }
function validateFinalRuntime(fixture) { return spawnSync(process.execPath, [VALIDATOR, 'runtime', fixture.targetPath, resolve(fixture.runDir, 'runtime-initial.json'), resolve(fixture.runDir, 'runtime-final.json'), 'final'], { encoding: 'utf8', env: { ...process.env, PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId } }); }
function runtime(phase) { return { case: CASE, phase, capturedAt: '2026-07-15T14:00:00.000Z', containers: TARGET.containers, database: TARGET.database, redis: { ...TARGET.redis, uptimeSeconds: '100' } }; }
function dbWrapper(evidenceCase) { return { db: db(evidenceCase), pgStatStatements: { case: evidenceCase, available: false, reason: 'extension-not-installed', rows: [] } }; }
function db(evidenceCase) { return {
	case: evidenceCase, statsReset: null,
	database: Object.fromEntries(DATABASE_COUNTER_KEYS.map((key) => [key, '1'])),
	tables: Object.fromEntries(TABLE_NAMES.map((name) => [name, { ...Object.fromEntries(TABLE_COUNTER_KEYS.map((key) => [key, '1'])), maintenance: Object.fromEntries(MAINTENANCE_KEYS.map((key) => [key, null])) }])),
	planner: Object.fromEntries(PLANNER_KEYS.map((key) => [key, '1'])),
	activity: [{ pid: '101', database: 'faithlog', user: 'faithlog', applicationName: 'PostgreSQL JDBC Driver', clientAddress: '172.1.0.2', backendStartedAt: '2026-07-15T08:33:00.000Z', state: 'idle' }],
}; }
function metrics(mode) { const kind = mode.startsWith('coffee') ? 'coffee' : 'meal'; const count = mode.endsWith('sequential') ? 10 : 5; return { case: { ...CASE, mode }, phase: 'measured', metrics: {
	[`${kind}_settlement_requests`]: { values: { count, rate: 2 } }, [`${kind}_settlement_failure_rate`]: { values: { rate: 0 } },
	[`${kind}_settlement_duration`]: { values: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40 } },
	[`${kind}_settlement_started_at`]: { values: { min: Date.parse('2026-07-15T14:06:40.000Z') } }, [`${kind}_settlement_finished_at`]: { values: { max: Date.parse('2026-07-15T14:06:40.500Z') } },
	iterations: { values: { count } }, checks: { values: { rate: 1 } }, http_req_failed: { values: { rate: 0 } },
} }; }
function resources(mode) { return ['2026-07-15T14:06:39.000Z', '2026-07-15T14:06:41.000Z'].map((capturedAt) => ({ case: { ...CASE, mode }, capturedAt, samplingIntervalMs: 1000, maxGapMs: 3000, roles: Object.fromEntries(Object.entries(TARGET.containers).map(([role, value]) => [role, { containerId: value.id, containerName: value.name, cpuPercent: 1, cpuPercentDisplay: '1.00%', memoryUsedBytes: '100', memoryLimitBytes: '1000', memoryUsageDisplay: '100B / 1kB', memoryPercent: 10, memoryPercentDisplay: '10.00%' }])) })); }
function container(id, image, service, config, port) { return { name: `faithlog-latest-${service}`, id: id.repeat(64), imageId: `sha256:${image.repeat(64)}`, startedAt: '2026-07-15T08:32:51.000Z', composeProject: 'faithlog-frontend-latest', composeService: service, configHash: config.repeat(64), health: service === 'app' ? 'none' : 'healthy', publishedPorts: [port] }; }
function write(directory, name, value) { writeFileSync(resolve(directory, name), `${JSON.stringify(value)}\n`); }
function read(directory, name) { return JSON.parse(readFileSync(resolve(directory, name), 'utf8')); }
function exists(directory, name) { try { readFileSync(resolve(directory, name)); return true; } catch { return false; } }
