import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
	validateCase,
	validateDbBoundaryContinuity,
	validateDbEvidence,
	validateMetricEvidence,
	validatePgStatStatements,
	validateResourceEvidence,
	validateRuntimeContinuity,
	validateRuntimeSnapshot,
	validateWarmupEvidence,
} from './evidence-contract.mjs';
import { requireExactBaseUrl, requireTokenCoverage } from './scenario-contract.js';
import { parseByteDisplay } from './resource-contract.mjs';

const CASE = Object.freeze({
	datasetId: 'PERFORMANCE_192_BEFORE_20260715_B',
	fixtureRunId: 'BEFORE_20260715_B',
	executionRunId: 'EXEC192_BEFORE_20260715_B',
});
const TARGET = Object.freeze({
	containers: {
		app: container('a', '1', 'app', '2026-07-15T14:04:40.111829595Z', '4', '28080:8080/tcp'),
		postgres: container('b', '2', 'postgres', '2026-07-15T08:32:51.579933554Z', '5', '25432:5432/tcp'),
		redis: container('c', '3', 'redis', '2026-07-15T08:32:51.569151095Z', '6', '26379:6379/tcp'),
	},
	database: { name: 'faithlog', user: 'faithlog', serverAddress: null, serverPort: null, postmasterStartedAt: '2026-07-15T08:32:52.000Z', statsReset: null },
	redis: { serverPort: 6379, runId: 'redis-run-1' },
	resourceSampling: { minSamples: 2, samplingIntervalMs: 1000, maxGapMs: 3000 },
});

function container(id, imageId, service, startedAt, configHash, publishedPort) {
	return { name: `faithlog-latest-${service}`, id: id.repeat(64), imageId: `sha256:${imageId.repeat(64)}`, startedAt, composeProject: 'faithlog-frontend-latest', composeService: service, configHash: configHash.repeat(64), health: service === 'app' ? 'none' : 'healthy', publishedPorts: [publishedPort] };
}

function runtime(phase = 'initial') {
	return {
		case: CASE, phase, capturedAt: '2026-07-15T14:00:00.000Z', containers: TARGET.containers,
		database: TARGET.database,
		redis: { ...TARGET.redis, uptimeSeconds: '100' },
	};
}

function metric(mode = 'coffee-sequential') {
	const kind = mode.startsWith('coffee') ? 'coffee' : 'meal';
	const count = mode.endsWith('sequential') ? 10 : 5;
	return {
		case: { ...CASE, mode },
		phase: 'measured',
		metrics: {
			[`${kind}_settlement_requests`]: { values: { count, rate: 2 } },
			[`${kind}_settlement_failure_rate`]: { rate: 0 },
			[`${kind}_settlement_duration`]: { values: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40 } },
			[`${kind}_settlement_started_at`]: { values: { min: 1784110000000, max: 1784110001000 } },
			[`${kind}_settlement_finished_at`]: { values: { min: 1784110000100, max: 1784110002000 } },
			iterations: { values: { count, rate: 2 } }, checks: { values: { rate: 1 } }, http_req_failed: { values: { rate: 0 } },
		},
	};
}

function k6V2Metric() {
	const value = metric();
	const definitions = {
		coffee_settlement_requests: ['counter', 'default'], iterations: ['counter', 'default'],
		coffee_settlement_failure_rate: ['rate', 'default'], checks: ['rate', 'default'], http_req_failed: ['rate', 'default'],
		coffee_settlement_duration: ['trend', 'time'], coffee_settlement_started_at: ['trend', 'default'], coffee_settlement_finished_at: ['trend', 'default'],
	};
	for (const [name, [type, contains]] of Object.entries(definitions)) Object.assign(value.metrics[name], { type, contains });
	Object.assign(value.metrics.coffee_settlement_failure_rate, { values: { rate: 0, passes: 0, fails: 10 } });
	Object.assign(value.metrics.http_req_failed.values, { passes: 0, fails: 10 });
	Object.assign(value.metrics.checks.values, { passes: 20, fails: 0 });
	return value;
}

function db() {
	return {
		case: { ...CASE, mode: 'coffee-sequential' }, statsReset: null,
		database: Object.fromEntries(['xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched', 'tup_inserted', 'tup_updated', 'tup_deleted', 'temp_files', 'temp_bytes'].map((key) => [key, key === 'xact_commit' ? '9007199254740992' : '1'])),
		tables: Object.fromEntries(['users', 'campus_members', 'campus_duty_assignments', 'polls', 'poll_options', 'poll_responses', 'poll_response_options', 'payment_accounts', 'charge_items', 'meal_poll_settlements', 'meal_poll_charge_groups', 'notification_logs'].map((name) => [name, {
			seq_scan: '10', seq_tup_read: '11', idx_scan: '20', idx_tup_fetch: '21', n_tup_ins: '1', n_tup_upd: '2', n_tup_del: '3',
			analyze_count: '1', autoanalyze_count: '0', vacuum_count: '0', autovacuum_count: '0',
			maintenance: { lastVacuum: null, lastAutovacuum: null, lastAnalyze: '2026-07-15T13:00:00.000Z', lastAutoanalyze: null },
		}])),
		planner: Object.fromEntries(['shared_buffers', 'work_mem', 'effective_cache_size', 'random_page_cost', 'seq_page_cost', 'max_connections', 'autovacuum', 'autovacuum_naptime', 'autovacuum_vacuum_scale_factor', 'autovacuum_analyze_scale_factor'].map((key) => [key, key === 'work_mem' ? '4096' : '1'])),
		activity: [{ pid: '101', database: 'faithlog', user: 'faithlog', applicationName: 'PostgreSQL JDBC Driver', clientAddress: '172.1.0.2', backendStartedAt: '2026-07-15T08:33:00.000Z', state: 'idle' }],
	};
}

function resources(mode = 'coffee-sequential') {
	return [
		resource(mode, '2026-07-15T14:06:39.000Z'),
		resource(mode, '2026-07-15T14:06:41.000Z'),
	];
}

function resource(mode, capturedAt) {
	return { case: { ...CASE, mode }, capturedAt, samplingIntervalMs: 1000, maxGapMs: 3000, roles: Object.fromEntries(Object.entries(TARGET.containers).map(([role, value]) => [role, { containerId: value.id, containerName: value.name, cpuPercent: 1.5, cpuPercentDisplay: '1.50%', memoryUsedBytes: '100', memoryLimitBytes: '1000', memoryUsageDisplay: '100B / 1kB', memoryPercent: 10, memoryPercentDisplay: '10.00%' }])) };
}

function expectReject(fn, pattern) { assert.throws(fn, pattern); }

test('runtime continuity rejects every app, PostgreSQL, Redis, database, and service restart drift', () => {
	const initial = runtime();
	validateRuntimeContinuity(initial, [runtime('post-lock'), runtime('final')], TARGET, CASE);
	for (const mutate of [
		(r) => { r.containers.app.startedAt = 'changed'; },
		(r) => { r.containers.postgres.id = 'replacement'; },
		(r) => { r.containers.postgres.imageId = 'drift'; },
		(r) => { r.containers.redis.configHash = 'drift'; },
		(r) => { r.database.postmasterStartedAt = 'changed'; },
		(r) => { r.redis.runId = 'changed'; },
	]) {
		const changed = structuredClone(runtime('mode-after')); mutate(changed);
		expectReject(() => validateRuntimeContinuity(initial, [changed], TARGET, CASE), /runtime|target/i);
	}
	const uptimeRegression = structuredClone(runtime('mode-after')); uptimeRegression.redis.uptimeSeconds = '99';
	expectReject(() => validateRuntimeContinuity(initial, [uptimeRegression], TARGET, CASE), /uptime/i);
});

test('runtime snapshot self-validation accepts every explicit runner phase without weakening initial-only continuity', () => {
	for (const phase of ['initial', 'post-lock', 'coffee-sequential-before', 'coffee-sequential-after', 'final']) {
		validateRuntimeSnapshot(runtime(phase), TARGET, CASE);
	}
	expectReject(() => validateRuntimeContinuity(runtime('post-lock'), [], TARGET, CASE), /initial phase/i);
	const drift = structuredClone(runtime('post-lock')); drift.containers.postgres.id = 'replacement';
	expectReject(() => validateRuntimeSnapshot(drift, TARGET, CASE), /runtime|target/i);
});

test('metric validator rejects sparse, fractional, failed, nonfinite, zero-throughput, and unordered evidence', () => {
	assert.equal(validateMetricEvidence(metric(), 'coffee-sequential', CASE).throughput, 5);
	for (const count of [9, 10.5, 11]) {
		const value = metric(); value.metrics.coffee_settlement_requests.values.count = count;
		expectReject(() => validateMetricEvidence(value, 'coffee-sequential', CASE), /count/i);
	}
	for (const count of [4, 5.5, 6]) {
		const value = metric('coffee-concurrent'); value.metrics.coffee_settlement_requests.values.count = count;
		expectReject(() => validateMetricEvidence(value, 'coffee-concurrent', CASE), /count/i);
	}
	for (const mutate of [
		(v) => { v.metrics.coffee_settlement_failure_rate.rate = 0.1; },
		(v) => { v.metrics.coffee_settlement_duration.values['p(95)'] = Number.NaN; },
		(v) => { v.metrics.coffee_settlement_duration.values['p(50)'] = -1; },
		(v) => { v.metrics.coffee_settlement_duration.values['p(99)'] = 19; },
	]) {
		const value = metric(); mutate(value);
		expectReject(() => validateMetricEvidence(value, 'coffee-sequential', CASE), /metric|latency|failure|throughput/i);
	}
	const direct = metric(); for (const [key, value] of Object.entries(direct.metrics)) if (value.values) direct.metrics[key] = value.values;
	validateMetricEvidence(direct, 'coffee-sequential', CASE);
	const valueRate = metric(); valueRate.metrics.coffee_settlement_failure_rate = { value: 0 }; valueRate.metrics.http_req_failed = { values: { value: 0 } }; valueRate.metrics.checks = { value: 1 };
	validateMetricEvidence(valueRate, 'coffee-sequential', CASE);
	const ignoredCounterRate = metric(); ignoredCounterRate.metrics.coffee_settlement_requests.values.rate = 0;
	assert.equal(validateMetricEvidence(ignoredCounterRate, 'coffee-sequential', CASE).throughput, 5);
});

test('k6 v2 wrapped Counter, Rate, and Trend metadata cannot be confused with another metric type or mixed direct shape', () => {
	const valid = k6V2Metric();
	validateMetricEvidence(valid, 'coffee-sequential', CASE);
	for (const mutate of [
		(value) => { value.metrics.coffee_settlement_requests.type = 'rate'; },
		(value) => { value.metrics.coffee_settlement_duration.contains = 'default'; },
		(value) => { value.metrics.coffee_settlement_requests.count = 10; },
	]) {
		const changed = structuredClone(valid); mutate(changed);
		expectReject(() => validateMetricEvidence(changed, 'coffee-sequential', CASE), /metric|counter|rate|trend|shape|type/i);
	}
});

test('k6 v2 wrapped Counter and Rate values enforce finite counters and exact passes-fails math', () => {
	const valid = k6V2Metric();
	validateMetricEvidence(valid, 'coffee-sequential', CASE);
	for (const mutate of [
		(value) => { value.metrics.coffee_settlement_requests.values.rate = Number.NaN; },
		(value) => { value.metrics.coffee_settlement_failure_rate.values.passes = 1; },
		(value) => { value.metrics.checks.values.fails = 1; },
		(value) => { value.metrics.http_req_failed.values.passes = 1; },
	]) {
		const changed = structuredClone(valid); mutate(changed);
		expectReject(() => validateMetricEvidence(changed, 'coffee-sequential', CASE), /metric|counter|rate|passes|fails|failure|checks/i);
	}
});

test('warmup evidence is a separate exact one-request phase', () => {
	const value = metric();
	value.phase = 'warmup';
	value.metrics.coffee_warmup_requests = { values: { count: 1 } };
	value.metrics.coffee_warmup_failure_rate = { values: { rate: 0 } };
	value.metrics.iterations.values.count = 1;
	validateWarmupEvidence(value, 'coffee-sequential', CASE);
	value.metrics.coffee_warmup_requests.values.count = 2;
	expectReject(() => validateWarmupEvidence(value, 'coffee-sequential', CASE), /warmup|count/i);
});

test('DB validator uses canonical decimal strings and BigInt deltas, rejecting unsafe, missing, reset, maintenance, planner, activity, and regression drift', () => {
	const before = db(); const after = structuredClone(before); after.database.xact_commit = '9007199254740993';
	const MODE_CASE = { ...CASE, mode: 'coffee-sequential' };
	assert.equal(validateDbEvidence(before, after, MODE_CASE).database.xact_commit, '1');
	for (const mutate of [
		(v) => { v.database.xact_commit = 9007199254740992; },
		(v) => { v.database.xact_commit = null; },
		(v) => { delete v.database.xact_commit; },
		(v) => { v.database.xact_commit = '9007199254740991'; },
		(v) => { v.statsReset = '2026-07-15T14:00:00.000Z'; },
		(v) => { v.planner.work_mem = '8192'; },
		(v) => { delete v.planner.work_mem; },
		(v) => { v.planner.extra = '1'; },
		(v) => { v.planner.work_mem = null; },
		(v) => { delete v.database.blks_hit; },
		(v) => { v.database.extra = '1'; },
		(v) => { delete v.tables.users; },
		(v) => { v.tables.extra = structuredClone(v.tables.users); },
		(v) => { delete v.tables.charge_items.maintenance.lastVacuum; },
		(v) => { v.tables.charge_items.maintenance.lastVacuum = 'not-a-timestamp'; },
		(v) => { v.tables.charge_items.analyze_count = null; },
		(v) => { v.tables.charge_items.maintenance.lastAutovacuum = '2026-07-15T14:00:00.000Z'; },
		(v) => { v.activity.push({ ...v.activity[0], pid: '102' }); },
		(v) => { v.activity.push({ ...v.activity[0] }); },
		(v) => { delete v.activity[0].applicationName; },
		(v) => { v.activity[0].clientAddress = 1; },
		(v) => { v.activity[0].applicationName = 'other'; },
		(v) => { v.activity[0].state = 'active'; },
	]) {
		const value = structuredClone(after); mutate(value);
		expectReject(() => validateDbEvidence(before, value, MODE_CASE), /counter|schema|reset|planner|maintenance|activity|required table/i);
	}
	for (const mutateBoth of [
		(v) => { delete v.database.blks_hit; },
		(v) => { delete v.planner.work_mem; },
		(v) => { delete v.tables.users; },
		(v) => { v.tables.charge_items.maintenance.lastVacuum = 'not-a-timestamp'; },
		(v) => { v.activity.push({ ...v.activity[0] }); },
	]) {
		const left = db(); const right = db(); mutateBoth(left); mutateBoth(right);
		expectReject(() => validateDbEvidence(left, right, MODE_CASE), /counter|schema|planner|maintenance|activity|required table/i);
	}
});

test('DB evidence permits idle pool PID and backend-start rotation with the same semantic multiset', () => {
	const before = db();
	before.activity = ['33948', '34291', '34496', '34906', '35061'].map((pid, index) => ({ ...before.activity[0], pid, backendStartedAt: `2026-07-17T00:59:0${index}.000000Z` }));
	const after = structuredClone(before);
	after.activity[0] = { ...after.activity[0], pid: '35476', backendStartedAt: '2026-07-17T01:00:03.539781Z' };
	assert.doesNotThrow(() => validateDbEvidence(before, after, { ...CASE, mode: 'coffee-sequential' }));
});

test('approved initial activity continuity ignores only PID and backend-start rotation', () => {
	const initial = db(); initial.case = { ...CASE };
	const before = db(); const after = structuredClone(before);
	before.activity[0].pid = '35476'; after.activity[0].pid = '35476';
	before.activity[0].backendStartedAt = '2026-07-17T01:00:03.539781Z';
	after.activity[0].backendStartedAt = '2026-07-17T01:00:03.539781Z';
	assert.doesNotThrow(() => validateDbEvidence(before, after, { ...CASE, mode: 'coffee-sequential' }, initial));
});

test('DB boundary continuity permits idle pool PID and backend-start rotation only', () => {
	const before = db(); const after = structuredClone(before);
	after.activity[0].pid = '35476';
	after.activity[0].backendStartedAt = '2026-07-17T01:00:03.539781Z';
	assert.doesNotThrow(() => validateDbBoundaryContinuity(before, after));
	const reorderedBefore = db();
	reorderedBefore.activity.push({ ...reorderedBefore.activity[0], pid: '102', applicationName: 'approved-observer' });
	const reorderedAfter = structuredClone(reorderedBefore); reorderedAfter.activity.reverse();
	assert.doesNotThrow(() => validateDbBoundaryContinuity(reorderedBefore, reorderedAfter));
	for (const mutate of [
		(value) => { value.activity.push({ ...value.activity[0], pid: '35477' }); },
		(value) => { value.activity[0].database = 'foreign'; },
		(value) => { value.activity[0].user = 'foreign'; },
		(value) => { value.activity[0].applicationName = 'foreign'; },
		(value) => { value.activity[0].clientAddress = '172.18.0.9'; },
		(value) => { value.activity[0].state = 'active'; },
		(value) => { value.activity[0].backendStartedAt = 'not-rfc3339'; },
		(value) => { value.activity.push({ ...value.activity[0] }); },
	]) {
		const changed = structuredClone(after); mutate(changed);
		expectReject(() => validateDbEvidence(before, changed, { ...CASE, mode: 'coffee-sequential' }), /activity/i);
	}
});

test('resource validator requires exact role/ID/case, strict CPU and memory, boundary coverage, samples, and max gap', () => {
	const window = { startMs: Date.parse('2026-07-15T14:06:40.000Z'), endMs: Date.parse('2026-07-15T14:06:40.500Z') };
	validateResourceEvidence(resources(), 'coffee-sequential', CASE, TARGET, window);
	for (const mutate of [
		(v) => { v.splice(1); },
		(v) => { v[0].roles.app.containerId = 'wrong'; },
		(v) => { v[0].roles.app.containerName = 'foreign'; },
		(v) => { delete v[0].roles.redis; },
		(v) => { v[0].roles.app.cpuPercent = -1; },
		(v) => { v[0].roles.app.memoryLimitBytes = '0'; },
		(v) => { v[0].roles.app.memoryUsedBytes = '1001'; },
		(v) => { v[0].roles.app.memoryLimitBytes = '9007199254740992'; },
		(v) => { v[0].roles.app.memoryPercent = 9.9; },
		(v) => { v[0].roles.app.memoryPercentDisplay = '9.90%'; },
		(v) => { v[0].roles.app.memoryUsageDisplay = '101B / 1kB'; },
		(v) => { v[0].samplingIntervalMs = 500; },
		(v) => { v[0].maxGapMs = 4000; },
		(v) => { v[0].case.mode = 'meal-sequential'; },
		(v) => { v[0].capturedAt = 'not-iso'; },
		(v) => { v[1].capturedAt = '2026-07-15T14:06:45.000Z'; },
	]) {
		const value = structuredClone(resources()); mutate(value);
		expectReject(() => validateResourceEvidence(value, 'coffee-sequential', CASE, TARGET, window), /resource|sample|role|container|cpu|memory|case|timestamp|gap|boundary/i);
	}
});

test('resource boundary coverage selects the nearest pre-start and post-end samples while allowing later final samples', () => {
	const window = { startMs: Date.parse('2026-07-16T22:58:20.396Z'), endMs: Date.parse('2026-07-16T22:58:28.976Z') };
	const exact = ['20.332', '22.334', '24.351', '26.356', '28.368', '30.395', '32.428']
		.map((time) => resource('coffee-sequential', `2026-07-16T22:58:${time}Z`));
	validateResourceEvidence(exact, 'coffee-sequential', CASE, TARGET, window);

	const noPreStart = exact.slice(1);
	expectReject(() => validateResourceEvidence(noPreStart, 'coffee-sequential', CASE, TARGET, window), /boundary/i);
	const noPostEnd = exact.filter((sample) => Date.parse(sample.capturedAt) < window.endMs);
	expectReject(() => validateResourceEvidence(noPostEnd, 'coffee-sequential', CASE, TARGET, window), /boundary/i);
	const preStartTooFar = structuredClone(exact); preStartTooFar[0].capturedAt = '2026-07-16T22:58:17.395Z';
	expectReject(() => validateResourceEvidence(preStartTooFar, 'coffee-sequential', CASE, TARGET, window), /gap|boundary/i);
	const postEndTooFar = structuredClone(exact); postEndTooFar.splice(5, 1); postEndTooFar[5].capturedAt = '2026-07-16T22:58:32.000Z';
	expectReject(() => validateResourceEvidence(postEndTooFar, 'coffee-sequential', CASE, TARGET, window), /gap|boundary/i);
	const duplicate = structuredClone(exact); duplicate[3].capturedAt = duplicate[2].capturedAt;
	expectReject(() => validateResourceEvidence(duplicate, 'coffee-sequential', CASE, TARGET, window), /gap/i);
	const reversed = structuredClone(exact); [reversed[2], reversed[3]] = [reversed[3], reversed[2]];
	expectReject(() => validateResourceEvidence(reversed, 'coffee-sequential', CASE, TARGET, window), /gap/i);
});

test('resource evidence revalidates Docker display intervals without rejecting a valid rounded NDJSON capture', () => {
	const window = { startMs: Date.parse('2026-07-15T14:06:40.000Z'), endMs: Date.parse('2026-07-15T14:06:40.500Z') };
	const rounded = resources().map((sample) => {
		const value = sample.roles.app;
		value.memoryUsageDisplay = '206MiB / 7.653GiB';
		value.memoryUsedBytes = parseByteDisplay('206MiB');
		value.memoryLimitBytes = parseByteDisplay('7.653GiB');
		value.memoryPercent = 2.64;
		value.memoryPercentDisplay = '2.64%';
		return sample;
	});
	const ndjsonRoundTrip = rounded.map((sample) => JSON.stringify(sample)).join('\n').split('\n').map(JSON.parse);
	validateResourceEvidence(ndjsonRoundTrip, 'coffee-sequential', CASE, TARGET, window);

	const tamperedBytes = structuredClone(ndjsonRoundTrip);
	tamperedBytes[0].roles.app.memoryUsedBytes = String(BigInt(tamperedBytes[0].roles.app.memoryUsedBytes) + 1n);
	expectReject(() => validateResourceEvidence(tamperedBytes, 'coffee-sequential', CASE, TARGET, window), /memory display consistency/i);
	const tamperedDisplay = structuredClone(ndjsonRoundTrip);
	tamperedDisplay[0].roles.app.memoryUsageDisplay = '207MiB / 7.653GiB';
	expectReject(() => validateResourceEvidence(tamperedDisplay, 'coffee-sequential', CASE, TARGET, window), /memory display consistency/i);
	const nonOverlapping = structuredClone(ndjsonRoundTrip);
	nonOverlapping[0].roles.app.memoryPercent = 2.65; nonOverlapping[0].roles.app.memoryPercentDisplay = '2.65%';
	expectReject(() => validateResourceEvidence(nonOverlapping, 'coffee-sequential', CASE, TARGET, window), /memory percent consistency/i);
});

test('collector and evidence validator reject memory percentages above 100 while preserving multi-core CPU percentages', () => {
	const window = { startMs: Date.parse('2026-07-15T14:06:40.000Z'), endMs: Date.parse('2026-07-15T14:06:40.500Z') };
	const value = resources();
	for (const sample of value) {
		sample.roles.app.cpuPercent = 125; sample.roles.app.cpuPercentDisplay = '125.00%';
		sample.roles.app.memoryUsageDisplay = '100B / 100B';
		sample.roles.app.memoryLimitBytes = '100';
		sample.roles.app.memoryPercent = 100.01; sample.roles.app.memoryPercentDisplay = '100.01%';
	}
	expectReject(() => validateResourceEvidence(value, 'coffee-sequential', CASE, TARGET, window), /memory percent/i);
});

test('pg_stat_statements permits only strict both-unavailable or valid available continuity', () => {
	const MODE_CASE = { ...CASE, mode: 'coffee-sequential' };
	const unavailable = { case: MODE_CASE, available: false, reason: 'extension-not-installed', rows: [] };
	validatePgStatStatements(unavailable, unavailable, MODE_CASE);
	for (const pair of [
		[unavailable, { ...unavailable, available: true }],
		[{ ...unavailable, rows: [{}] }, unavailable],
		[{ case: MODE_CASE, available: true, databaseId: '1', rows: [] }, { case: MODE_CASE, available: true, databaseId: '1', rows: [] }],
	]) expectReject(() => validatePgStatStatements(pair[0], pair[1], MODE_CASE), /pg_stat_statements/i);
	const row = { userId: '1', databaseId: '2', queryId: '-3', topLevel: true, calls: '10', totalExecTimeMicros: '100', query: 'select 1' };
	const availableBefore = { case: MODE_CASE, available: true, databaseId: '2', rows: [row] };
	const availableAfter = { case: MODE_CASE, available: true, databaseId: '2', rows: [{ ...row, calls: '11', totalExecTimeMicros: '120' }] };
	validatePgStatStatements(availableBefore, availableAfter, MODE_CASE);
	for (const [left, right] of [
		[{ ...availableBefore, rows: [row, row] }, availableAfter],
		[availableBefore, { ...availableAfter, rows: [availableAfter.rows[0], availableAfter.rows[0]] }],
		[availableBefore, { ...availableAfter, rows: [] }],
		[availableBefore, { ...availableAfter, rows: [{ ...row, queryId: 'bad' }] }],
	]) expectReject(() => validatePgStatStatements(left, right, MODE_CASE), /pg_stat_statements/i);
});

test('case, exact target, and JWT coverage fail closed at exact boundaries', () => {
	validateCase(CASE, CASE);
	expectReject(() => validateCase({ ...CASE, fixtureRunId: 'OTHER' }, CASE), /case/i);
	expectReject(() => validateCase({ ...CASE, mode: 'coffee-sequential' }, CASE), /case/i);
	assert.equal(requireExactBaseUrl('http://127.0.0.1:28080'), 'http://127.0.0.1:28080');
	for (const value of [undefined, 'http://localhost:28080', 'http://127.0.0.1:29280']) expectReject(() => requireExactBaseUrl(value), /BASE_URL/);
	assert.equal(requireTokenCoverage(2000, 1000_000, 900, 100), 2000);
	expectReject(() => requireTokenCoverage(1999, 1000_000, 900, 100), /expiry/i);
	assert.equal(requireTokenCoverage(2001, 1000_999, 900, 100), 2001);
	assert.equal(requireTokenCoverage(1800, 0, 1620, 120), 1800);
	assert.equal(1800 - (1620 + 120), 60, 'approved sequential token headroom must be 60 seconds at issuance');
	assert.equal(requireTokenCoverage(1800, 59_000, 1620, 120), 1800, 'issued token must tolerate pre-measured preparation within the 60-second headroom');
	assert.equal(requireTokenCoverage(1740, 0, 1620, 120), 1740, '27m plus safety 120 must pass at the exact upper bound');
	expectReject(() => requireTokenCoverage(1739, 0, 1620, 120), /expiry/i);
	assert.equal(requireTokenCoverage(960, 0, 840, 120), 960, 'concurrent 14m plus safety 120 remains unchanged');
	expectReject(() => requireTokenCoverage(959, 0, 840, 120), /expiry/i);
});
