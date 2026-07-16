import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);

function readScenario(name) {
	return readFileSync(new URL(name, SCENARIO_ROOT), 'utf8');
}

test('all target, service, image, credential, and workload inputs are runtime-required without fallback', () => {
	const guard = readScenario('guard-runtime.sh');
	const runner = readScenario('run-before.sh');
	const prepare = readScenario('prepare-fixtures.sh');
	const harness = readFileSync(new URL(
		'../../src/test/java/com/faithlog/performance/notification/NotificationBatchBeforeScenarioTest.java',
		import.meta.url,
	), 'utf8');
	const combined = `${guard}\n${runner}\n${prepare}`;
	const required = [
		'PERF_EXPECTED_COMPOSE_PROJECT',
		'POSTGRES_CONTAINER',
		'REDIS_CONTAINER',
		'PERF_EXPECTED_POSTGRES_CONTAINER_ID',
		'PERF_EXPECTED_REDIS_CONTAINER_ID',
		'PERF_EXPECTED_POSTGRES_SERVICE',
		'PERF_EXPECTED_REDIS_SERVICE',
		'PERF_EXPECTED_POSTGRES_IMAGE_ID',
		'PERF_EXPECTED_REDIS_IMAGE_ID',
		'POSTGRES_USER',
		'POSTGRES_PASSWORD',
		'POSTGRES_DB',
		'PERF_EXPECTED_POSTGRES_ROLE',
		'PERF_REDIS_AUTH_MODE',
		'PERF_MEMBER_COUNT',
		'PERF_SUCCESS_COUNT',
		'PERF_TRANSIENT_COUNT',
		'PERF_PERMANENT_COUNT',
		'PERF_INACTIVE_COUNT',
		'PERF_NO_TOKEN_COUNT',
		'PERF_BUSINESS_DATE',
		'PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS',
		'PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS',
	];
	for (const name of required) {
		assert.match(combined, new RegExp(`\\$\\{${name}:\\?`), `${name} must be runtime-required`);
		assert.doesNotMatch(combined, new RegExp(`\\$\\{${name}:-`), `${name} must not have a fallback`);
	}
	assert.match(harness, /spring\.datasource\.username/);
	assert.match(harness, /spring\.datasource\.password/);
	assert.match(harness, /PERF_REDIS_AUTH_MODE/);
	assert.doesNotMatch(combined, /faithlog-latest.*:-|POSTGRES_PASSWORD.*printf|REDIS_PASSWORD.*printf/);
});

test('cumulative PostgreSQL and Redis counters use canonical decimal strings and BigInt deltas', async () => {
	const integrity = await import(new URL('./notification-batch/integrity-contract.mjs', import.meta.url));
	assert.equal(integrity.canonicalDecimal('9007199254740993', 'counter'), 9007199254740993n);
	assert.equal(integrity.decimalDelta('9007199254740993', '9007199254740999', 'counter'), '6');
	assert.throws(() => integrity.canonicalDecimal(9007199254740992, 'counter'), /decimal string/);
	assert.throws(() => integrity.canonicalDecimal('01', 'counter'), /decimal string/);
	assert.throws(() => integrity.decimalDelta('9', '8', 'counter'), /monotonic/);

	const runner = readScenario('run-before.sh');
	const redisParser = readScenario('parse-redis-evidence.mjs');
	const verifier = readScenario('verify-before.mjs');
	assert.match(runner, /::text/);
	assert.match(redisParser, /canonicalDecimal/);
	assert.match(verifier, /BigInt|decimalDelta/);
});

test('pg_stat_statements available and unavailable evidence is continuous and fail-closed', async () => {
	const { validatePgStatStatements } = await import(
		new URL('./notification-batch/integrity-contract.mjs', import.meta.url)
	);
	assert.deepEqual(
		validatePgStatStatements(
			{ available: false, reason: 'extension-not-installed', rows: [] },
			{ available: false, reason: 'extension-not-installed', rows: [] },
		),
		{ available: false, deltas: [] },
	);
	const row = {
		userId: '10', databaseId: '20', queryId: '30', topLevel: true,
		calls: '9007199254740993', totalExecTimeMicros: '9007199254740995',
	};
	const available = validatePgStatStatements(
		{ available: true, databaseId: '20', statsReset: '2026-07-16T00:00:00.000Z', rows: [row] },
		{ available: true, databaseId: '20', statsReset: '2026-07-16T00:00:00.000Z', rows: [{
			...row, calls: '9007199254740995', totalExecTimeMicros: '9007199254741000',
		}] },
	);
	assert.deepEqual(available.deltas, [{
		key: '10:20:30:true', calls: '2', totalExecTimeMicros: '5',
	}]);
	assert.throws(() => validatePgStatStatements(
		{ available: false, reason: 'extension-not-installed', rows: [] },
		{ available: true, databaseId: '20', statsReset: '2026-07-16T00:00:00.000Z', rows: [row] },
	), /availability drift/);
	assert.match(readScenario('run-before.sh'), /pgss-before\.json/);
	assert.match(readScenario('verify-before.mjs'), /validatePgStatStatements/);
});

test('resource evidence binds exact full IDs and validates CPU, RAM bytes-percent, cadence, and lifecycle window', async () => {
	const { validateResourceSamples } = await import(
		new URL('./notification-batch/integrity-contract.mjs', import.meta.url)
	);
	const expected = {
		postgres: { name: 'pg-198', id: 'pg-full-id-198' },
		redis: { name: 'redis-198', id: 'redis-full-id-198' },
	};
	const sample = (capturedAt, component, name, id) => ({
		capturedAt, component, containerName: name, containerId: id,
		cpuPercent: 150.25, memoryUsedBytes: '10485760', memoryLimitBytes: '1073741824', memoryPercent: 0.98,
	});
	const samples = [
		sample('2026-07-16T00:00:00.000Z', 'postgres', 'pg-198', 'pg-full-id-198'),
		sample('2026-07-16T00:00:00.000Z', 'redis', 'redis-198', 'redis-full-id-198'),
		sample('2026-07-16T00:01:00.000Z', 'postgres', 'pg-198', 'pg-full-id-198'),
		sample('2026-07-16T00:01:00.000Z', 'redis', 'redis-198', 'redis-full-id-198'),
	];
	assert.equal(validateResourceSamples(samples, expected, {
		workloadStartedAt: '2026-07-16T00:00:10.000Z',
		workloadFinishedAt: '2026-07-16T00:00:50.000Z',
		maxGapMilliseconds: 60000,
	}).sampleInstants, 2);
	assert.throws(() => validateResourceSamples(samples.slice(0, 2), expected, {
		workloadStartedAt: '2026-07-16T00:00:10.000Z',
		workloadFinishedAt: '2026-07-16T00:00:50.000Z',
		maxGapMilliseconds: 60000,
	}), /at least two|coverage/);
	assert.throws(() => validateResourceSamples(samples.map((value, index) => index === 1
		? { ...value, containerId: 'mixed-container' }
		: value), expected, {
		workloadStartedAt: '2026-07-16T00:00:10.000Z',
		workloadFinishedAt: '2026-07-16T00:00:50.000Z',
		maxGapMilliseconds: 60000,
	}), /identity/);
	assert.match(readScenario('capture-docker-stats.sh'), /memoryUsedBytes/);
});

test('Java harness records exact zero scenario failures and ordered phase latency without pretending to be k6', () => {
	const harness = readFileSync(new URL(
		'../../src/test/java/com/faithlog/performance/notification/NotificationBatchBeforeScenarioTest.java',
		import.meta.url,
	), 'utf8');
	const verifier = readScenario('verify-before.mjs');
	const readme = readScenario('README.md');
	const runner = readScenario('run-before.sh');
	assert.match(harness, /scenarioFailureCount/);
	assert.match(harness, /scenarioFailureRate/);
	assert.match(harness, /phaseOrder/);
	assert.match(verifier, /scenarioFailureCount[\s\S]*0/);
	assert.match(verifier, /p50[\s\S]*p95[\s\S]*p99[\s\S]*max|latency ordering/i);
	assert.match(verifier, /endToEnd[\s\S]*creation[\s\S]*delivery/);
	assert.match(readme, /k6 v2.*not applicable/i);
	assert.match(readme, /Counter.*Rate.*Trend/i);
	assert.doesNotMatch(runner, /\bk6\s+run\b/);
});

test('fixture namespace includes fresh token and log markers and related correctness exclusions are explicit', () => {
	const sql = readScenario('prepare-fixtures.sql');
	const readme = readScenario('README.md');
	assert.match(sql, /fresh_fixture_run_guard/);
	assert.match(sql, /fresh_notification_log_namespace_guard/);
	assert.match(sql, /PERFORMANCE #198/);
	assert.match(readme, /pagination.*not applicable|pagination.*범위 밖/is);
	assert.match(readme, /archive.*not applicable|archive.*범위 밖/is);
	assert.match(readme, /#200/);
	assert.match(readme, /#206/);
	assert.match(readme, /stable.*id.*asc/i);
});

test('the first machine-readable rejection is immutable and every outcome forbids automatic adoption', async () => {
	const { writeFirstRejection } = await import(
		new URL('./notification-batch/rejection-contract.mjs', import.meta.url)
	);
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-rejection-'));
	try {
		const output = join(root, 'rejection.json');
		writeFirstRejection(output, { stage: 'preflight', reason: 'runtime-target-mismatch', exitCode: 2 });
		const firstText = readFileSync(output, 'utf8');
		writeFirstRejection(output, { stage: 'verification', reason: 'later-failure', exitCode: 3 });
		assert.equal(readFileSync(output, 'utf8'), firstText);
		const rejection = JSON.parse(firstText);
		assert.equal(rejection.accepted, false);
		assert.equal(rejection.automaticAdoption, false);
		assert.equal(rejection.stage, 'preflight');
		assert.deepEqual(rejection.reasons, ['runtime-target-mismatch']);
		assert.equal(rejection.secretsIncluded, false);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
	for (const file of ['run-before.sh', 'prepare-fixtures.sh', 'verify-before.mjs', 'summarize-before.mjs']) {
		assert.match(readScenario(file), /automaticAdoption/);
	}
});
