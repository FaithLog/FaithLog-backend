import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {pathToFileURL} from 'node:url';

const scenarioDir = path.dirname(new URL(import.meta.url).pathname);

async function read(name) {
	return readFile(path.join(scenarioDir, name), 'utf8');
}

async function module(name) {
	return import(pathToFileURL(path.join(scenarioDir, name)).href);
}

function container(role, overrides = {}) {
	return {
		id: role.repeat(64).slice(0, 64).replace(/[^a-f0-9]/g, 'a'),
		image: `sha256:${role.repeat(64).slice(0, 64).replace(/[^a-f0-9]/g, 'b')}`,
		project: 'faithlog-frontend-latest',
		service: role,
		startedAt: '2026-07-15T00:00:00.000Z',
		running: true,
		...overrides,
	};
}

test('runner requires every approved workload and immutable target value without defaults', async () => {
	const runner = await read('run-baseline.sh');
	for (const name of [
		'DATASET_ID', 'FIXTURE_RUN_ID', 'PERF_EXECUTION_RUN_ID', 'BASE_URL',
		'APP_CONTAINER', 'POSTGRES_CONTAINER', 'REDIS_CONTAINER',
		'EXPECTED_COMPOSE_PROJECT', 'EXPECTED_APP_COMPOSE_SERVICE',
		'EXPECTED_POSTGRES_COMPOSE_SERVICE', 'EXPECTED_REDIS_COMPOSE_SERVICE',
		'EXPECTED_APP_CONTAINER_ID', 'EXPECTED_APP_IMAGE_ID',
		'EXPECTED_POSTGRES_CONTAINER_ID', 'EXPECTED_POSTGRES_IMAGE_ID',
		'EXPECTED_REDIS_CONTAINER_ID', 'EXPECTED_REDIS_IMAGE_ID', 'EXPECTED_SOURCE_COMMIT',
		'POSTGRES_DB', 'POSTGRES_USER', 'REQUESTER_USER_ID', 'DUTY_REQUESTER_USER_ID',
		'PERF_ADMIN_EMAIL', 'PERF_ADMIN_PASSWORD', 'PERF_DUTY_EMAIL', 'PERF_DUTY_PASSWORD',
		'WARMUP_ITERATIONS', 'WARMUP_VUS', 'WARMUP_MAX_DURATION',
		'MEASURED_VUS', 'MEASURED_DURATION', 'TOKEN_EXPIRY_SAFETY_SECONDS',
		'DOCKER_STATS_SAMPLING_INTERVAL_SECONDS', 'EXTERNAL_ACTIVITY',
	]) {
		assert.match(runner, new RegExp(`:\\s+"\\$\\{${name}:\\?`), `${name} must be runtime-required`);
		assert.doesNotMatch(runner, new RegExp(`\\$\\{${name}:-`), `${name} must not have a default`);
	}
	assert.match(runner, /355f79df5b2e47636b7d1a17dea029da6c93c62d/);
});

test('all target, workload, credential, and identity gates precede fresh fixture mutation', async () => {
	const runner = await read('run-baseline.sh');
	const mutation = runner.indexOf('prepare-fixture.sql');
	for (const gate of [
		'auth-contract.mjs" workload',
		'target-binding.mjs',
		'runtime-identity.mjs" post-lock',
		'ADMIN_ACCESS_TOKEN=',
		'DUTY_ACCESS_TOKEN=',
		'shared-stack-check',
	]) {
		const position = runner.indexOf(gate);
		assert.ok(position >= 0 && position < mutation, `${gate} must run before fixture mutation`);
	}
	assert.match(runner, /dataset_id="\$DATASET_ID"[\s\S]*fixture_run_id="\$FIXTURE_RUN_ID"[\s\S]*duty_requester_user_id="\$DUTY_REQUESTER_USER_ID"/);
	assert.doesNotMatch(runner, /\b(?:docker compose (?:up|down|build|restart)|docker (?:restart|rm|system prune|volume prune)|flyway|CREATE EXTENSION|ALTER SYSTEM|pg_stat_reset)\b/i);
});

test('runtime identity binds app, PostgreSQL, and Redis to one approved Compose project', async () => {
	const {validateRuntimeBootstrap, validateRuntimeStability} = await module('runtime-identity.mjs');
	const identity = {
		app: container('app'),
		postgres: container('postgres'),
		redis: container('redis'),
	};
	const expected = {
		project: 'faithlog-frontend-latest',
		services: {app: 'app', postgres: 'postgres', redis: 'redis'},
		containerIds: Object.fromEntries(Object.entries(identity).map(([role, value]) => [role, value.id])),
		imageIds: Object.fromEntries(Object.entries(identity).map(([role, value]) => [role, value.image])),
	};
	assert.deepEqual(validateRuntimeBootstrap(identity, expected), identity);
	assert.equal(validateRuntimeStability(identity, structuredClone(identity)), true);
	for (const mutate of [
		(value) => { value.redis.project = 'foreign'; },
		(value) => { value.postgres.id = 'f'.repeat(64); },
		(value) => { value.app.image = `sha256:${'e'.repeat(64)}`; },
	]) {
		const malformed = structuredClone(identity);
		mutate(malformed);
		assert.throws(() => validateRuntimeBootstrap(malformed, expected));
	}
});

test('target binding accepts only the inspected numeric loopback app port', async () => {
	const {validateTargetBinding} = await module('target-binding.mjs');
	const inspected = {service: 'app', ports: {'8080/tcp': [{HostIp: '127.0.0.1', HostPort: '28080'}]}};
	assert.deepEqual(validateTargetBinding('http://127.0.0.1:28080', inspected, 'app'), {
		service: 'app', containerPort: '8080/tcp', hostPort: '28080',
	});
	for (const target of ['http://localhost:28080', 'http://0.0.0.0:28080', 'https://127.0.0.1:28080']) {
		assert.throws(() => validateTargetBinding(target, inspected, 'app'));
	}
});

test('k6 keeps the 16 frontend cases in one ordered loop and separates warmup from measured load', async () => {
	const script = await read('admin-charge-query-baseline.js');
	assert.match(script, /buildRequestCases\(EXPECTATIONS, CAMPUS_ID\)/);
	assert.match(script, /for \(const requestCase of requestCases\)/);
	assert.match(script, /executor: 'shared-iterations'/);
	assert.match(script, /executor: 'constant-vus'/);
	assert.match(script, /PHASE === 'measured'/);
	assert.match(script, /summaryTrendStats: \['avg', 'med', 'p\(50\)', 'p\(95\)', 'p\(99\)', 'max', 'count'\]/);
	const runner = await read('run-baseline.sh');
	assert.ok(runner.indexOf('PHASE=warmup') < runner.indexOf('PHASE=measured'));
	assert.match(runner, /ADMIN_ACCESS_TOKEN="\$\([\s\S]*MEASURED_REQUIRED_TTL_SECONDS[\s\S]*authenticate\.mjs/);
});

test('summary validation enforces exact count math, failure math, latency order, and throughput', async () => {
	const {validateMeasuredSummary} = await module('validate-measured-summary.mjs');
	const {REQUEST_CASE_NAMES} = await module('scenario-definition.mjs');
	const metrics = {};
	for (const name of REQUEST_CASE_NAMES) {
		metrics[`admin_charge_${name}_failure`] = {values: {rate: 0, passes: 20, fails: 0}};
		metrics[`admin_charge_${name}_requests`] = {values: {count: 20, rate: 10}};
		metrics[`admin_charge_${name}_duration`] = {values: {
			avg: 5, med: 4, 'p(50)': 4, 'p(95)': 8, 'p(99)': 9, max: 10, count: 20,
		}};
	}
	const summary = {metrics};
	assert.equal(validateMeasuredSummary(summary, {expectedRequestCount: 20}), true);
	for (const mutate of [
		(value, name) => { value.metrics[`admin_charge_${name}_failure`].values.fails = 1; },
		(value, name) => { value.metrics[`admin_charge_${name}_duration`].values.count = 19; },
		(value, name) => { value.metrics[`admin_charge_${name}_duration`].values['p(95)'] = 3; },
		(value, name) => { value.metrics[`admin_charge_${name}_requests`].values.count = 19; },
	]) {
		const malformed = structuredClone(summary);
		mutate(malformed, REQUEST_CASE_NAMES[0]);
		assert.throws(() => validateMeasuredSummary(malformed, {expectedRequestCount: 20}));
	}
});

test('PostgreSQL counter integrity preserves decimal strings across the JS safe boundary', async () => {
	const {counterDelta} = await module('measurement-integrity.mjs');
	assert.equal(counterDelta('9007199254740993', '9007199254741003'), '10');
	for (const invalid of [9007199254740993, '01', '-1', '1.5', null]) {
		assert.throws(() => counterDelta('0', invalid));
	}
});

test('resource evidence covers app, PostgreSQL, and Redis with exact immutable IDs', async () => {
	const {validateDockerResourceEvidence} = await module('docker-resource-evidence.mjs');
	const ids = {app: 'a'.repeat(64), postgres: 'b'.repeat(64), redis: 'c'.repeat(64)};
	const containers = Object.entries(ids).map(([role, containerId]) => ({
		role, containerId, cpuPercent: 1, memoryUsedBytes: 100, memoryLimitBytes: 1000, memoryPercent: 10,
	}));
	const result = validateDockerResourceEvidence({
		samples: [
			{capturedAt: '2026-07-15T00:00:00.000Z', containers},
			{capturedAt: '2026-07-15T00:00:01.000Z', containers},
		],
		expectedContainerIds: ids,
		measuredStart: '2026-07-15T00:00:00.000Z',
		measuredEnd: '2026-07-15T00:00:01.000Z',
		samplingIntervalSeconds: 1,
	});
	assert.equal(result.sampleCount, 2);
	const missingRedis = structuredClone(containers).slice(0, 2);
	assert.throws(() => validateDockerResourceEvidence({
		samples: [
			{capturedAt: '2026-07-15T00:00:00.000Z', containers: missingRedis},
			{capturedAt: '2026-07-15T00:00:01.000Z', containers: missingRedis},
		],
		expectedContainerIds: ids,
		measuredStart: '2026-07-15T00:00:00.000Z',
		measuredEnd: '2026-07-15T00:00:01.000Z',
		samplingIntervalSeconds: 1,
	}));
});

test('database evidence is exact, non-mutating, and keeps optional pgss continuity', async () => {
	const state = await read('collect-measurement-state.sql');
	const counters = await read('collect-counter-boundary.sql');
	const evidence = await read('collect-postgres-evidence.sql');
	assert.match(state, /pg_stat_activity/);
	assert.match(state, /pg_settings/);
	assert.match(state, /pg_stat_user_tables/);
	assert.match(state, /pg_stat_statements_info/);
	assert.match(counters, /::text/);
	assert.match(evidence, /EXPLAIN \(ANALYZE, BUFFERS, FORMAT JSON\)/);
	for (const sql of [state, counters, evidence]) {
		assert.doesNotMatch(sql, /\b(?:INSERT|UPDATE|DELETE|TRUNCATE|CREATE|ALTER|DROP|VACUUM|ANALYZE)\b/i);
	}
});

test('measurement status stays separate from evidence integrity and cannot auto-adopt boundary provenance', async () => {
	const {classifySharedStackEvidence} = await module('measurement-classification.mjs');
	assert.deepEqual(classifySharedStackEvidence({externalActivityDeclaration: 'none'}), {
		measurementStatus: 'conditional-shared-stack',
		evidenceIntegrity: 'validated-separately',
		automaticAdoption: false,
		requiresPmReview: true,
		externalActivityDeclaration: 'none',
		provenance: 'boundary-observation-only',
		note: 'PM must verify the exclusive-use window and supporting evidence before any baseline adoption.',
	});
	assert.throws(() => classifySharedStackEvidence({externalActivityDeclaration: 'unknown'}));
	const runner = await read('run-baseline.sh');
	assert.match(runner, /faithlog-performance-\$\{LOCK_KEY\}\.lock/);
	assert.match(runner, /EXPECTED_COMPOSE_PROJECT/);
	assert.match(runner, /measurement-classification\.json/);
	assert.match(runner, /evidence-integrity\.json/);
	assert.doesNotMatch(runner, /(?:EMAIL|PASSWORD|ACCESS_TOKEN).*run-conditions|echo.*(?:PASSWORD|ACCESS_TOKEN)/i);
});
