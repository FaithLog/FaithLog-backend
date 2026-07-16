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
		'DOCKER_STATS_SAMPLING_INTERVAL_SECONDS', 'DOCKER_STATS_MAX_GAP_SECONDS',
		'EXTERNAL_ACTIVITY',
	]) {
		assert.match(runner, new RegExp(`:\\s+"\\$\\{${name}:\\?`), `${name} must be runtime-required`);
		assert.doesNotMatch(runner, new RegExp(`\\$\\{${name}:-`), `${name} must not have a default`);
	}
	assert.match(runner, /6796ed146244d8f3f5b5dd7048ebe16865084a97/);
});

test('all target, workload, credential, and identity gates precede fresh fixture mutation', async () => {
	const runner = await read('run-baseline.sh');
	const mutation = runner.indexOf('prepare-fixture.sql');
	for (const gate of [
		'auth-contract.mjs" workload',
		'target-binding.mjs',
		'runtime-identity.mjs" post-lock',
		'ADMIN_ACCESS_TOKEN="$(',
		'DUTY_ACCESS_TOKEN="$(',
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
		metrics[`admin_charge_${name}_failure`] = {values: {value: 0, passes: 0, fails: 20}};
		metrics[`admin_charge_${name}_requests`] = {values: {count: 20, rate: 10}};
		metrics[`admin_charge_${name}_duration`] = {values: {
			avg: 5, med: 4, 'p(50)': 4, 'p(95)': 8, 'p(99)': 9, max: 10, count: 20,
		}};
	}
	const summary = {metrics};
	assert.equal(validateMeasuredSummary(summary, {expectedRequestCount: 20}), true);
	const directSummary = {metrics: Object.fromEntries(
		Object.entries(metrics).map(([name, metric]) => [name, metric.values]),
	)};
	assert.equal(validateMeasuredSummary(directSummary, {expectedRequestCount: 20}), true);
	for (const mutate of [
		(value, name) => {
			Object.assign(value.metrics[`admin_charge_${name}_failure`].values, {value: 0.05, passes: 1, fails: 19});
		},
		(value, name) => { value.metrics[`admin_charge_${name}_duration`].values.count = 19; },
		(value, name) => { value.metrics[`admin_charge_${name}_duration`].values['p(95)'] = 3; },
		(value, name) => { value.metrics[`admin_charge_${name}_duration`].values.avg = 11; },
		(value, name) => { value.metrics[`admin_charge_${name}_requests`].values.count = 19; },
	]) {
		const malformed = structuredClone(summary);
		mutate(malformed, REQUEST_CASE_NAMES[0]);
		assert.throws(() => validateMeasuredSummary(malformed, {expectedRequestCount: 20}));
	}
});

test('k6 v2 Rate summary treats passes as true count and fails as false count in both phases', async () => {
	const {validateMeasuredSummary} = await module('validate-measured-summary.mjs');
	const {REQUEST_CASE_NAMES} = await module('scenario-definition.mjs');
	const summary = (nested) => {
		const metrics = {};
		for (const name of REQUEST_CASE_NAMES) {
			const values = {
				failure: {value: 0, passes: 0, fails: 5},
				requests: {count: 5, rate: 1},
				duration: {avg: 5, med: 4, 'p(50)': 4, 'p(95)': 8, 'p(99)': 9, max: 10, count: 5},
			};
			metrics[`admin_charge_${name}_failure`] = nested ? {values: values.failure} : values.failure;
			metrics[`admin_charge_${name}_requests`] = nested ? {values: values.requests} : values.requests;
			metrics[`admin_charge_${name}_duration`] = nested ? {values: values.duration} : values.duration;
		}
		return {metrics};
	};
	for (const nested of [false, true]) {
		assert.equal(validateMeasuredSummary(summary(nested), {expectedRequestCount: 5}), true, 'warmup');
		assert.equal(validateMeasuredSummary(summary(nested)), true, 'measured');
	}

	const firstFailure = (value) => {
		const metric = value.metrics[`admin_charge_${REQUEST_CASE_NAMES[0]}_failure`];
		return metric.values ?? metric;
	};
	const firstRequests = (value) => {
		const metric = value.metrics[`admin_charge_${REQUEST_CASE_NAMES[0]}_requests`];
		return metric.values ?? metric;
	};
	for (const mutate of [
		(value) => { firstFailure(value).value = 0.2; },
		(value) => { firstFailure(value).passes = 1; },
		(value) => { firstFailure(value).fails = 4; },
		(value) => { firstRequests(value).count = 4; },
		(value) => { delete firstFailure(value).value; },
		(value) => { firstFailure(value).value = Number.POSITIVE_INFINITY; },
		(value) => { firstFailure(value).value = '0'; },
	]) {
		const malformed = summary(true);
		mutate(malformed);
		assert.throws(() => validateMeasuredSummary(malformed, {expectedRequestCount: 5}));
		assert.throws(() => validateMeasuredSummary(malformed));
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
	const {normalizeDockerStats, validateDockerResourceEvidence} = await module('docker-resource-evidence.mjs');
	const ids = {app: 'a'.repeat(64), postgres: 'b'.repeat(64), redis: 'c'.repeat(64)};
	const normalized = normalizeDockerStats({
		capturedAt: '2026-07-15T00:00:00.000Z',
		expectedContainerIds: ids,
		rawStats: Object.values(ids).map((id) => ({
			ID: id, CPUPerc: '1%', MemUsage: '100B / 1000B', MemPerc: '10%',
		})),
	});
	const containers = normalized.containers;
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
	assert.deepEqual(normalized.containers.map(({role}) => role), ['app', 'postgres', 'redis']);
	assert.throws(() => normalizeDockerStats({
		capturedAt: '2026-07-15T00:00:00.000Z',
		expectedContainerIds: ids,
		rawStats: Object.values(ids).map((id) => ({
			ID: id, CPUPerc: '1%', MemUsage: '100B / 1000B', MemPerc: '99%',
		})),
	}));
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

test('resource sampler separates nominal cadence from an approved maximum gap', async () => {
	const {normalizeDockerStats, validateDockerResourceEvidence} = await module('docker-resource-evidence.mjs');
	const ids = {app: 'a'.repeat(64), postgres: 'b'.repeat(64), redis: 'c'.repeat(64)};
	const sampleAt = (capturedAt) => normalizeDockerStats({
		capturedAt,
		expectedContainerIds: ids,
		rawStats: Object.values(ids).map((id) => ({
			ID: id, CPUPerc: '1%', MemUsage: '100B / 1000B', MemPerc: '10%',
		})),
	});
	const gObservedSamples = [
		sampleAt('2026-07-16T04:54:06.025Z'),
		sampleAt('2026-07-16T04:54:07.894Z'),
		sampleAt('2026-07-16T04:54:12.701Z'),
	];
	const valid = validateDockerResourceEvidence({
		samples: gObservedSamples,
		expectedContainerIds: ids,
		measuredStart: '2026-07-16T04:54:06.025Z',
		measuredEnd: '2026-07-16T04:54:12.701Z',
		samplingIntervalSeconds: 1,
		maximumGapSeconds: 5,
	});
	assert.equal(valid.samplingIntervalSeconds, 1);
	assert.equal(valid.maximumGapSeconds, 5);

	const base = {
		samples: gObservedSamples.slice(0, 2),
		expectedContainerIds: ids,
		measuredStart: '2026-07-16T04:54:06.025Z',
		measuredEnd: '2026-07-16T04:54:07.894Z',
		samplingIntervalSeconds: 1,
		maximumGapSeconds: 5,
	};
	for (const maximumGapSeconds of [undefined, 0, Number.NaN, 0.5]) {
		assert.throws(() => validateDockerResourceEvidence({...base, maximumGapSeconds}));
	}
	assert.throws(() => validateDockerResourceEvidence({
		...base,
		samples: [gObservedSamples[0], sampleAt('2026-07-16T04:54:11.026Z')],
		measuredEnd: '2026-07-16T04:54:11.026Z',
	}));

	const runner = await read('run-baseline.sh');
	assert.doesNotMatch(runner, /DOCKER_STATS_COLLECTION_SLEEP_SECONDS/);
	assert.doesNotMatch(runner, /sleep "\$DOCKER_STATS_[A-Z_]+"/);
	assert.match(runner, /dockerStatsSamplingIntervalSeconds=\$DOCKER_STATS_SAMPLING_INTERVAL_SECONDS/);
	assert.match(runner, /dockerStatsMaximumGapSeconds=\$DOCKER_STATS_MAX_GAP_SECONDS/);
	assert.match(runner, /docker-resource-evidence\.mjs" validate[\s\S]*"\$DOCKER_STATS_SAMPLING_INTERVAL_SECONDS"[\s\S]*"\$DOCKER_STATS_MAX_GAP_SECONDS"/);
});

test('Docker decimal binary-unit displays preserve rounding ranges without false byte precision', async () => {
	const {normalizeDockerStats, validateDockerResourceEvidence} = await module('docker-resource-evidence.mjs');
	const ids = {app: 'a'.repeat(64), postgres: 'b'.repeat(64), redis: 'c'.repeat(64)};
	const rawStats = [
		{ID: ids.app, CPUPerc: '1.25%', MemUsage: '499.7MiB / 7.653GiB', MemPerc: '6.38%'},
		{ID: ids.postgres, CPUPerc: '2.5%', MemUsage: '264.9MiB / 7.653GiB', MemPerc: '3.38%'},
		{ID: ids.redis, CPUPerc: '0%', MemUsage: '19.5MiB / 7.653GiB', MemPerc: '0.25%'},
	];
	const normalized = normalizeDockerStats({
		capturedAt: '2026-07-16T00:00:00.000Z',
		expectedContainerIds: ids,
		rawStats,
	});
	assert.deepEqual(normalized.containers.map((container) => ({
		role: container.role,
		memoryUsed: container.memoryUsed,
		memoryLimit: container.memoryLimit,
		memoryPercent: container.memoryPercent,
	})), [
		{
			role: 'app',
			memoryUsed: {
				displayed: '499.7MiB', minimumBytesInclusive: '523920999', maximumBytesInclusive: '524025855',
			},
			memoryLimit: {
				displayed: '7.653GiB', minimumBytesInclusive: '8216809309', maximumBytesInclusive: '8217883049',
			},
			memoryPercent: {
				displayed: '6.38%', minimumNumeratorInclusive: '1275', maximumNumeratorExclusive: '1277', denominator: '200',
			},
		},
		{
			role: 'postgres',
			memoryUsed: {
				displayed: '264.9MiB', minimumBytesInclusive: '277715354', maximumBytesInclusive: '277820211',
			},
			memoryLimit: {
				displayed: '7.653GiB', minimumBytesInclusive: '8216809309', maximumBytesInclusive: '8217883049',
			},
			memoryPercent: {
				displayed: '3.38%', minimumNumeratorInclusive: '675', maximumNumeratorExclusive: '677', denominator: '200',
			},
		},
		{
			role: 'redis',
			memoryUsed: {
				displayed: '19.5MiB', minimumBytesInclusive: '20394804', maximumBytesInclusive: '20499660',
			},
			memoryLimit: {
				displayed: '7.653GiB', minimumBytesInclusive: '8216809309', maximumBytesInclusive: '8217883049',
			},
			memoryPercent: {
				displayed: '0.25%', minimumNumeratorInclusive: '49', maximumNumeratorExclusive: '51', denominator: '200',
			},
		},
	]);
	assert.equal(validateDockerResourceEvidence({
		samples: [normalized, {...structuredClone(normalized), capturedAt: '2026-07-16T00:00:01.000Z'}],
		expectedContainerIds: ids,
		measuredStart: '2026-07-16T00:00:00.000Z',
		measuredEnd: '2026-07-16T00:00:01.000Z',
		samplingIntervalSeconds: 1,
	}).sampleCount, 2);
	assert.doesNotThrow(() => normalizeDockerStats({
		capturedAt: '2026-07-16T00:00:00.000Z',
		expectedContainerIds: ids,
		rawStats: Object.values(ids).map((id) => ({
			ID: id, CPUPerc: '0%', MemUsage: '1GiB / 1GiB', MemPerc: '100%',
		})),
	}), 'overlapping rounded ranges must allow a consistent used <= limit value');

	for (const mutate of [
		(rows) => { rows[0].MemPerc = '99%'; },
		(rows) => { rows[0].MemUsage = '499.7MiB / 0B'; },
		(rows) => { rows[0].MemUsage = '2GiB / 1GiB'; },
		(rows) => { rows[0].MemUsage = '9007199254740992B / 9007199254740992B'; },
		(rows) => { rows[0].MemUsage = '499.7ZiB / 7.653GiB'; },
		(rows) => { rows[0].CPUPerc = 'NaN%'; },
		(rows) => { rows[0].MemPerc = '101%'; },
		(rows) => { rows[0].ID = 'd'.repeat(64); },
	]) {
		const malformed = structuredClone(rawStats);
		mutate(malformed);
		assert.throws(() => normalizeDockerStats({
			capturedAt: '2026-07-16T00:00:00.000Z',
			expectedContainerIds: ids,
			rawStats: malformed,
		}));
	}
});

test('measurement integrity rejects activity, planner, maintenance, pgss, and counter drift', async () => {
	const {validateMeasurementIntegrity} = await module('measurement-integrity.mjs');
	const valid = integrityFixture();
	const result = validateMeasurementIntegrity(valid);
	assert.equal(result.evidenceIntegrity, 'valid-supporting-evidence');
	assert.equal(result.observerAdjustedCounters.xactCommit, '100');
	for (const mutate of [
		(value) => { value.afterState.externalActiveCount = 1; },
		(value) => { value.afterState.plannerSettings.work_mem = '8MB'; },
		(value) => { value.afterState.tables.charge_items.autovacuumCount = 1; },
		(value) => { value.afterState.pgStatStatements.dealloc = 1; },
		(value) => { value.afterCounter.xactCommit = 1; },
	]) {
		const malformed = structuredClone(valid);
		mutate(malformed);
		assert.throws(() => validateMeasurementIntegrity(malformed));
	}
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
		assert.doesNotMatch(sql, /^\s*(?:INSERT|UPDATE|DELETE|TRUNCATE|CREATE|ALTER|DROP|VACUUM|ANALYZE)\b/im);
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

test('final immutable checkpoint follows every evidence validator and precedes classification', async () => {
	const runner = await read('run-baseline.sh');
	assert.match(runner, /docker exec -i "\$EXPECTED_POSTGRES_CONTAINER_ID"/);
	assert.match(runner, /docker stats[\s\S]*"\$EXPECTED_APP_CONTAINER_ID"[\s\S]*"\$EXPECTED_POSTGRES_CONTAINER_ID"[\s\S]*"\$EXPECTED_REDIS_CONTAINER_ID"/);
	const resourceValidation = runner.indexOf('docker-resource-validation.json');
	const databaseValidation = runner.indexOf('evidence-integrity.json');
	const finalRuntime = runner.indexOf('runtime-identity-final.json');
	const finalDatabase = runner.indexOf('database-identity-final.json');
	const finalBinding = runner.indexOf('target-binding-final.json');
	const finalContinuity = runner.indexOf('final-continuity');
	const classification = runner.indexOf('measurement-classification.json');
	for (const position of [resourceValidation, databaseValidation, finalRuntime, finalDatabase, finalBinding, finalContinuity, classification]) {
		assert.ok(position >= 0);
	}
	assert.ok(resourceValidation < finalRuntime);
	assert.ok(databaseValidation < finalRuntime);
	assert.ok(finalRuntime < finalContinuity);
	assert.ok(finalDatabase < finalContinuity);
	assert.ok(finalBinding < finalContinuity);
	assert.ok(finalContinuity < classification);

	const {validateRuntimeStability} = await module('runtime-identity.mjs');
	const before = {app: container('app'), postgres: container('postgres'), redis: container('redis')};
	for (const role of ['app', 'postgres', 'redis']) {
		const replaced = structuredClone(before);
		replaced[role].id = 'f'.repeat(64);
		assert.throws(() => validateRuntimeStability(before, replaced));
	}
});

test('dataset binding uses psql stdin variable substitution instead of -c', async () => {
	const bindingQuery = `SELECT JSONB_BUILD_OBJECT(
		'campusId', (SELECT id FROM campuses WHERE name = 'PERF_ISSUE_193:' || :'dataset_id'),
		'crossCampusId', (SELECT id FROM campuses WHERE name = 'PERF_ISSUE_193:' || :'dataset_id' || ':CROSS')
	)`;
	assert.throws(() => fakePsql({
		args: ['-q', '-t', '-A', '-c', bindingQuery, '-v', 'dataset_id=I193_BEFORE_20260716_B'],
		stdin: '',
	}), /syntax error at or near ":"/);
	assert.equal(fakePsql({
		args: ['-q', '-t', '-A', '-v', 'dataset_id=I193_BEFORE_20260716_C'],
		stdin: bindingQuery,
	}), '{"campusId":18,"crossCampusId":19}');

	const runner = await read('run-baseline.sh');
	const bindingSql = await read('select-dataset-binding.sql');
	assert.doesNotMatch(runner, /DATASET_BINDING_JSON="\$\(psql_exec[^\n]*-c/);
	assert.match(runner, /DATASET_BINDING_JSON="\$\(\s*psql_exec -q -t -A[\s\S]*-v dataset_id="\$DATASET_ID"[\s\S]*< "\$SCENARIO_DIR\/select-dataset-binding\.sql"/);
	assert.match(bindingSql, /:'dataset_id'/);
	assert.doesNotMatch(bindingSql, /\$\{?DATASET_ID\}?|I193_BEFORE_/);
});

function integrityFixture() {
	const plannerSettings = Object.fromEntries([
		'enable_bitmapscan', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
		'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'effective_cache_size',
		'random_page_cost', 'seq_page_cost', 'work_mem',
	].map((name) => [name, 'on']));
	const table = {
		nModSinceAnalyze: 0,
		analyzeCount: 1,
		autoanalyzeCount: 0,
		vacuumCount: 0,
		autovacuumCount: 0,
		lastAnalyze: '2026-07-14T00:00:00.000Z',
		lastAutoanalyze: null,
		lastVacuum: null,
		lastAutovacuum: null,
	};
	const database = {
		name: 'faithlog',
		serverAddress: '172.20.0.2',
		serverPort: 5432,
		postmasterStartTime: '2026-07-14T00:00:00.000Z',
		statsReset: '2026-07-14T00:00:00.000Z',
	};
	const state = (capturedAt) => ({
		capturedAt,
		externalActiveCount: 0,
		database: structuredClone(database),
		plannerSettings: structuredClone(plannerSettings),
		pgStatStatements: {
			available: true,
			statsReset: '2026-07-14T00:00:00.000Z',
			dealloc: 0,
		},
		tables: Object.fromEntries(
			['campus_members', 'charge_items', 'payment_accounts', 'users']
				.map((name) => [name, structuredClone(table)]),
		),
	});
	const counters = (value) => Object.fromEntries(
		['xactCommit', 'xactRollback', 'blksRead', 'blksHit', 'tupReturned', 'tupFetched']
			.map((name) => [name, value]),
	);
	return {
		beforeState: state('2026-07-15T00:00:00.000Z'),
		afterState: state('2026-07-15T00:03:00.000Z'),
		calibrationCounter: counters('9007199254740992'),
		beforeCounter: counters('9007199254740994'),
		afterCounter: counters('9007199254741096'),
		expectedDatabaseName: 'faithlog',
	};
}

function fakePsql({args, stdin}) {
	const commandIndex = args.indexOf('-c');
	const command = commandIndex >= 0 ? args[commandIndex + 1] : null;
	if (command?.includes(":'dataset_id'")) {
		throw new Error('PostgreSQL syntax error at or near ":"');
	}
	const variableIndex = args.indexOf('-v');
	const assignment = variableIndex >= 0 ? args[variableIndex + 1] : '';
	if (!stdin.includes(":'dataset_id'") || !/^dataset_id=[A-Za-z0-9_-]+$/.test(assignment)) {
		throw new Error('stdin SQL and dataset_id psql variable are required');
	}
	return '{"campusId":18,"crossCampusId":19}';
}
