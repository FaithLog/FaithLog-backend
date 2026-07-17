import assert from 'node:assert/strict';
import {
	chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
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
		'PERF_EXPECTED_POSTGRES_SERVER_ADDRESS',
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
	const freshAvailable = validatePgStatStatements(
		{ available: true, databaseId: '20', statsReset: null, rows: [row] },
		{ available: true, databaseId: '20', statsReset: null, rows: [{
			...row, calls: '9007199254740995', totalExecTimeMicros: '9007199254741000',
		}] },
	);
	assert.deepEqual(freshAvailable.deltas, available.deltas);
	assert.throws(() => validatePgStatStatements(
		{ available: true, databaseId: '20', statsReset: null, rows: [row] },
		{ available: true, databaseId: '20', statsReset: '2026-07-16T00:00:00.000Z', rows: [row] },
	), /stats reset|statsReset/i);
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
		postgres: { name: 'pg-198', id: 'a'.repeat(64) },
		redis: { name: 'redis-198', id: 'b'.repeat(64) },
	};
	const sample = (capturedAt, component, name, id) => ({
		capturedAt, component, containerName: name, containerId: id,
		cpuPercent: 150.25, memoryUsedBytes: '10485760', memoryLimitBytes: '1073741824', memoryPercent: 0.98,
	});
	const samples = [
		sample('2026-07-16T00:00:00.000Z', 'postgres', 'pg-198', 'a'.repeat(64)),
		sample('2026-07-16T00:00:00.000Z', 'redis', 'redis-198', 'b'.repeat(64)),
		sample('2026-07-16T00:01:00.000Z', 'postgres', 'pg-198', 'a'.repeat(64)),
		sample('2026-07-16T00:01:00.000Z', 'redis', 'redis-198', 'b'.repeat(64)),
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
	assert.throws(() => validateResourceSamples(samples.map((value, index) => index === 0
		? { ...value, memoryPercent: 2.5 }
		: value), expected, {
		workloadStartedAt: '2026-07-16T00:00:10.000Z',
		workloadFinishedAt: '2026-07-16T00:00:50.000Z',
		maxGapMilliseconds: 60000,
	}), /bytes-percent/);
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

test('preflight continuity reports cannot survive as a misleading final verified report', () => {
	const continuity = readScenario('assert-runtime-continuity.mjs');
	const runner = readScenario('run-before.sh');
	const prepare = readScenario('prepare-fixtures.sh');
	assert.match(continuity, /RUNTIME_CONTINUITY_REPORT_PATH/);
	assert.match(runner, /runtime-continuity-pre-workload\.json/);
	assert.match(prepare, /runtime-continuity-pre-fixture\.json/);
	assert.match(runner, /RUNTIME_CONTINUITY_REPORT_PATH="\$\{RUN_DIR\}\/runtime-continuity-report\.json"/);
	assert.match(prepare, /RUNTIME_CONTINUITY_REPORT_PATH="\$\{REPORT_DIR\}\/runtime-continuity-report\.json"/);
});

test('entrypoint preflight rejects missing runtime inputs before Docker and preserves the first rejection', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-preflight-rejection-'));
	try {
		const repository = join(root, 'repository');
		const scenario = join(repository, 'performance/k6/notification-batch');
		const bin = join(root, 'bin');
		mkdirSync(scenario, { recursive: true });
		mkdirSync(bin);
		for (const name of [
			'run-before.sh', 'prepare-fixtures.sh', 'guard-runtime.sh',
			'runner-lifecycle.sh', 'rejection-contract.mjs',
		]) copyFileSync(fileURLToPath(new URL(`./notification-batch/${name}`, import.meta.url)), join(scenario, name));
		const dockerTrace = join(root, 'docker.trace');
		const docker = join(bin, 'docker');
		writeFileSync(docker, `#!/usr/bin/env bash\nprintf 'docker-called\\n' >> "${dockerTrace}"\nexit 99\n`);
		chmodSync(docker, 0o755);
		const baseEnv = { PATH: `${bin}:${process.env.PATH}`, HOME: process.env.HOME };
		const runId = 'missing-runtime-run';
		const runner = spawnSync('bash', [join(scenario, 'run-before.sh')], {
			env: { ...baseEnv, RUN_ID: runId }, encoding: 'utf8',
		});
		assert.notEqual(runner.status, 0);
		const runnerRejection = JSON.parse(readFileSync(join(
			repository, `build/reports/k6/notification-batch/rejections/${runId}.json`,
		), 'utf8'));
		assert.equal(runnerRejection.stage, 'preflight');
		assert.equal(runnerRejection.automaticAdoption, false);
		const harnessEnv = {
			...baseEnv, POSTGRES_USER: 'owner', POSTGRES_PASSWORD: 'runtime-secret',
			POSTGRES_DB: 'faithlog_perf', PERF_EXPECTED_POSTGRES_ROLE: 'owner',
			PERF_EXPECTED_POSTGRES_SERVER_ADDRESS: '127.0.0.1',
			PERF_EXPECTED_HARNESS_CONTRACT_DIGEST: 'a'.repeat(64),
		};
		const missingHead = spawnSync('bash', [join(scenario, 'run-before.sh')], {
			env: { ...harnessEnv, RUN_ID: 'missing-harness-head' }, encoding: 'utf8',
		});
		assert.notEqual(missingHead.status, 0);
		assert.match(missingHead.stderr, /PERF_EXPECTED_HARNESS_HEAD/);
		const wrongHead = spawnSync('bash', [join(scenario, 'run-before.sh')], {
			env: { ...harnessEnv, RUN_ID: 'wrong-harness-head', PERF_EXPECTED_HARNESS_HEAD: 'wrong' },
			encoding: 'utf8',
		});
		assert.notEqual(wrongHead.status, 0);
		assert.match(wrongHead.stderr, /HEAD.*digest/i);
		const wrongDigest = spawnSync('bash', [join(scenario, 'run-before.sh')], {
			env: {
				...harnessEnv, RUN_ID: 'wrong-harness-digest',
				PERF_EXPECTED_HARNESS_HEAD: 'a'.repeat(40),
				PERF_EXPECTED_HARNESS_CONTRACT_DIGEST: 'wrong',
			},
			encoding: 'utf8',
		});
		assert.notEqual(wrongDigest.status, 0);
		assert.match(wrongDigest.stderr, /HEAD.*digest/i);

		const fixtureRunId = 'missing-runtime-fixture';
		const fixture = spawnSync('bash', [join(scenario, 'prepare-fixtures.sh')], {
			env: { ...baseEnv, PERF_FIXTURE_RUN_ID: fixtureRunId }, encoding: 'utf8',
		});
		assert.notEqual(fixture.status, 0);
		const fixtureRejection = JSON.parse(readFileSync(join(
			repository, `build/reports/k6/notification-batch/fixtures/rejections/${fixtureRunId}.json`,
		), 'utf8'));
		assert.equal(fixtureRejection.stage, 'fixture-preflight');
		assert.equal(fixtureRejection.automaticAdoption, false);
		assert.equal(existsSync(dockerTrace), false, 'Docker invocation count must be zero');
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('credential argv, pgss schema, and PostgreSQL table evidence stay exact', () => {
	const scripts = [
		'capture-runtime-identity.sh', 'capture-pgss.sh', 'prepare-fixtures.sh', 'run-before.sh',
	].map(readScenario).join('\n');
	assert.doesNotMatch(scripts, /docker exec -e (?:PGPASSWORD|REDISCLI_AUTH)="\$\{/,
		'runtime credentials must not appear in Docker CLI argv');
	assert.match(scripts, /docker exec -e PGPASSWORD/);
	assert.match(readScenario('capture-pgss.sh'), /pg_extension[\s\S]*pg_namespace/);
	assert.match(readScenario('capture-pgss.sh'), /pgss_schema/);
	assert.match(readScenario('run-before.sh'), /to_jsonb\(table_stats\) - 'relname'/);
});
