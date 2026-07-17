import { parseByteDisplay, parsePercentDisplay, validateMemoryDisplayConsistency } from './resource-contract.mjs';

const DECIMAL = /^(0|[1-9]\d*)$/;
const SIGNED_DECIMAL = /^-?(0|[1-9]\d*)$/;
export const RESOURCE_ROLES = Object.freeze(['app', 'postgres', 'redis']);
export const DATABASE_COUNTER_KEYS = Object.freeze(['xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched', 'tup_inserted', 'tup_updated', 'tup_deleted', 'temp_files', 'temp_bytes']);
export const TABLE_NAMES = Object.freeze(['users', 'campus_members', 'campus_duty_assignments', 'polls', 'poll_options', 'poll_responses', 'poll_response_options', 'payment_accounts', 'charge_items', 'meal_poll_settlements', 'meal_poll_charge_groups', 'notification_logs']);
export const TABLE_COUNTER_KEYS = Object.freeze(['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del', 'analyze_count', 'autoanalyze_count', 'vacuum_count', 'autovacuum_count']);
export const MAINTENANCE_KEYS = Object.freeze(['lastVacuum', 'lastAutovacuum', 'lastAnalyze', 'lastAutoanalyze']);
export const PLANNER_KEYS = Object.freeze(['shared_buffers', 'work_mem', 'effective_cache_size', 'random_page_cost', 'seq_page_cost', 'max_connections', 'autovacuum', 'autovacuum_naptime', 'autovacuum_vacuum_scale_factor', 'autovacuum_analyze_scale_factor']);
export const ACTIVITY_KEYS = Object.freeze(['pid', 'database', 'user', 'applicationName', 'clientAddress', 'backendStartedAt', 'state']);
const ACTIVITY_SEMANTIC_KEYS = Object.freeze(['database', 'user', 'applicationName', 'clientAddress', 'state']);

export function validateCase(actual, expected) {
	assertObject(actual, 'case');
	assertSameKeys(actual, expected, 'case');
	for (const [key, value] of Object.entries(expected)) if (actual[key] !== value) throw new Error(`case mismatch: ${key}`);
	return actual;
}

export function validateRuntimeSnapshot(snapshot, target, expectedCase) {
	validateRuntime(snapshot, target, expectedCase);
	return snapshot;
}

export function validateRuntimeTarget(target) {
	assertObject(target, 'runtime target');
	assertExactKeys(target.containers, RESOURCE_ROLES, 'runtime role');
	for (const role of RESOURCE_ROLES) validateContainerTarget(target.containers[role], role);
	assertExactKeys(target.database, ['name', 'user', 'serverAddress', 'serverPort', 'postmasterStartedAt', 'statsReset'], 'runtime database target');
	if (typeof target.database.name !== 'string' || !target.database.name || typeof target.database.user !== 'string' || !target.database.user || target.database.serverAddress !== null || target.database.serverPort !== null || typeof target.database.postmasterStartedAt !== 'string' || !Number.isFinite(Date.parse(target.database.postmasterStartedAt)) || !nullableIso(target.database.statsReset)) throw new Error('runtime database target schema');
	assertExactKeys(target.redis, ['runId', 'serverPort'], 'runtime Redis target');
	if (typeof target.redis.runId !== 'string' || !target.redis.runId || !Number.isSafeInteger(target.redis.serverPort) || target.redis.serverPort <= 0) throw new Error('runtime Redis target schema');
	assertExactKeys(target.resourceSampling, ['minSamples', 'samplingIntervalMs', 'maxGapMs'], 'resource sampling');
	if (!Number.isSafeInteger(target.resourceSampling.minSamples) || target.resourceSampling.minSamples < 2 || !Number.isSafeInteger(target.resourceSampling.samplingIntervalMs) || target.resourceSampling.samplingIntervalMs <= 0 || !Number.isSafeInteger(target.resourceSampling.maxGapMs) || target.resourceSampling.maxGapMs < target.resourceSampling.samplingIntervalMs) throw new Error('resource sampling metadata');
	return target;
}

export function validateRuntimeContinuity(initial, snapshots, target, expectedCase) {
	validateRuntimeSnapshot(initial, target, expectedCase);
	if (initial.phase !== 'initial') throw new Error('runtime initial phase mismatch');
	const identity = runtimeIdentity(initial);
	let uptime = decimal(initial.redis.uptimeSeconds, 'Redis uptime');
	for (const snapshot of snapshots) {
		validateRuntimeSnapshot(snapshot, target, expectedCase);
		if (stable(identity) !== stable(runtimeIdentity(snapshot))) throw new Error(`runtime continuity drift: ${snapshot.phase}`);
		const nextUptime = decimal(snapshot.redis.uptimeSeconds, 'Redis uptime');
		if (nextUptime < uptime) throw new Error('Redis uptime regression');
		uptime = nextUptime;
	}
	return identity;
}

export function validateMetricEvidence(evidence, mode, expectedCase) {
	assertExactKeys(evidence, ['case', 'phase', 'metrics'], 'metric evidence');
	validateCase(evidence.case, { ...expectedCase, mode });
	if (evidence.phase !== 'measured') throw new Error('metric phase mismatch');
	assertObject(evidence.metrics, 'metrics');
	const kind = mode.startsWith('coffee-') ? 'coffee' : mode.startsWith('meal-') ? 'meal' : fail('metric mode');
	const expectedCount = mode.endsWith('-sequential') ? 10 : mode.endsWith('-concurrent') ? 5 : fail('metric mode');
	const requests = metric(evidence.metrics, `${kind}_settlement_requests`, 'counter');
	const count = counterCount(requests, 'metric count');
	if (!Number.isSafeInteger(count) || count !== expectedCount) throw new Error(`metric count expected ${expectedCount}`);
	if (rate(metric(evidence.metrics, `${kind}_settlement_failure_rate`, 'rate'), 'failure', expectedCount) !== 0) throw new Error('failure must be exact zero');
	if (rate(metric(evidence.metrics, 'http_req_failed', 'rate'), 'HTTP failure', expectedCount) !== 0) throw new Error('HTTP failure must be exact zero');
	if (rate(metric(evidence.metrics, 'checks', 'rate'), 'checks', expectedCount * 2) !== 1) throw new Error('checks must be exact one');
	const iterations = counterCount(metric(evidence.metrics, 'iterations', 'counter'), 'iteration count');
	if (!Number.isSafeInteger(iterations) || iterations !== expectedCount) throw new Error(`iteration count expected ${expectedCount}`);
	const duration = metric(evidence.metrics, `${kind}_settlement_duration`, 'trend', 'time').values;
	const latency = ['p(50)', 'p(95)', 'p(99)', 'max'].map((key) => finite(duration[key], `latency ${key}`));
	if (latency.some((value) => value < 0) || !(latency[0] <= latency[1] && latency[1] <= latency[2] && latency[2] <= latency[3])) throw new Error('latency ordering is invalid');
	const startMs = finite(metric(evidence.metrics, `${kind}_settlement_started_at`, 'trend', 'default').values.min, 'metric start');
	const endMs = finite(metric(evidence.metrics, `${kind}_settlement_finished_at`, 'trend', 'default').values.max, 'metric end');
	if (!Number.isSafeInteger(startMs) || !Number.isSafeInteger(endMs) || endMs <= startMs) throw new Error('metric window is invalid');
	const throughput = count / ((endMs - startMs) / 1000);
	if (!Number.isFinite(throughput) || throughput <= 0) throw new Error('throughput must be positive');
	return { count, throughput, failureRate: 0, latencyMs: { p50: latency[0], p95: latency[1], p99: latency[2], max: latency[3] }, window: { startMs, endMs } };
}

export function validateWarmupEvidence(evidence, mode, expectedCase) {
	assertExactKeys(evidence, ['case', 'phase', 'metrics'], 'warmup evidence');
	validateCase(evidence.case, { ...expectedCase, mode });
	if (evidence.phase !== 'warmup') throw new Error('warmup phase mismatch');
	const kind = mode.startsWith('coffee-') ? 'coffee' : mode.startsWith('meal-') ? 'meal' : fail('warmup mode');
	const count = counterCount(metric(evidence.metrics, `${kind}_warmup_requests`, 'counter'), 'warmup count');
	if (!Number.isSafeInteger(count) || count !== 1) throw new Error('warmup count expected 1');
	if (rate(metric(evidence.metrics, `${kind}_warmup_failure_rate`, 'rate'), 'warmup failure', 1) !== 0) throw new Error('warmup failure must be exact zero');
	if (rate(metric(evidence.metrics, 'http_req_failed', 'rate'), 'warmup HTTP failure', 2) !== 0) throw new Error('warmup HTTP failure must be exact zero');
	if (rate(metric(evidence.metrics, 'checks', 'rate'), 'warmup checks', 2) !== 1) throw new Error('warmup checks must be exact one');
	const iterations = counterCount(metric(evidence.metrics, 'iterations', 'counter'), 'warmup iteration count');
	if (!Number.isSafeInteger(iterations) || iterations !== 1) throw new Error('warmup iteration count expected 1');
	return true;
}

export function validateDbSnapshot(value, expectedCase) {
	assertExactKeys(value, ['case', 'statsReset', 'database', 'tables', 'planner', 'activity'], 'DB evidence');
	validateCase(value.case, expectedCase);
	if (!nullableIso(value.statsReset)) throw new Error('stats reset schema');
	validateDecimalObject(value.database, DATABASE_COUNTER_KEYS, 'database counter');
	assertExactKeys(value.tables, TABLE_NAMES, 'required table');
	for (const name of TABLE_NAMES) validateTable(value.tables[name], name);
	assertExactKeys(value.planner, PLANNER_KEYS, 'planner');
	for (const key of PLANNER_KEYS) if (typeof value.planner[key] !== 'string' || !value.planner[key]) throw new Error(`planner value: ${key}`);
	activitySemanticMultiset(value.activity);
	return value;
}

function activitySemanticMultiset(activity) {
	if (!Array.isArray(activity)) throw new Error('activity schema');
	const pids = new Set();
	const semanticRows = [];
	for (const row of activity) {
		assertExactKeys(row, ACTIVITY_KEYS, 'activity row');
		decimal(row.pid, 'activity pid');
		if (pids.has(row.pid)) throw new Error('activity duplicate PID');
		pids.add(row.pid);
		for (const key of ['database', 'user', 'applicationName']) if (typeof row[key] !== 'string') throw new Error(`activity ${key}`);
		if (row.clientAddress !== null && (typeof row.clientAddress !== 'string' || !row.clientAddress)) throw new Error('activity clientAddress');
		if (!rfc3339Utc(row.backendStartedAt)) throw new Error('activity backendStartedAt');
		if (row.state !== 'idle') throw new Error('activity active boundary');
		semanticRows.push(Object.fromEntries(ACTIVITY_SEMANTIC_KEYS.map((key) => [key, row[key]])));
	}
	return semanticRows.sort((left, right) => stable(left) < stable(right) ? -1 : stable(left) > stable(right) ? 1 : 0);
}

export function validateDbEvidence(before, after, expectedCase, approvedInitial = null) {
	validateDbSnapshot(before, expectedCase);
	validateDbSnapshot(after, expectedCase);
	if (before.statsReset !== after.statsReset) throw new Error('stats reset drift');
	if (stable(before.planner) !== stable(after.planner)) throw new Error('planner drift');
	if (stable(activitySemanticMultiset(before.activity)) !== stable(activitySemanticMultiset(after.activity))) throw new Error('activity drift');
	if (approvedInitial) {
		const globalCase = Object.fromEntries(Object.entries(expectedCase).filter(([key]) => key !== 'mode'));
		validateDbSnapshot(approvedInitial, globalCase);
		if (before.statsReset !== approvedInitial.statsReset) throw new Error('stats reset initial drift');
		if (stable(before.planner) !== stable(approvedInitial.planner)) throw new Error('planner initial drift');
		if (stable(activitySemanticMultiset(before.activity)) !== stable(activitySemanticMultiset(approvedInitial.activity))) throw new Error('activity initial drift');
	}
	const database = decimalMapDelta(before.database, after.database, 'database counter');
	const tables = {};
	for (const name of TABLE_NAMES) {
		const left = before.tables[name]; const right = after.tables[name];
		if (stable(left.maintenance) !== stable(right.maintenance)) throw new Error(`maintenance drift: ${name}`);
		const leftCounters = Object.fromEntries(TABLE_COUNTER_KEYS.map((key) => [key, left[key]]));
		const rightCounters = Object.fromEntries(TABLE_COUNTER_KEYS.map((key) => [key, right[key]]));
		tables[name] = decimalMapDelta(leftCounters, rightCounters, `table counter ${name}`);
	}
	return { database, tables };
}

export function validateDbBoundaryContinuity(before, after) {
	if (before.statsReset !== after.statsReset) throw new Error('stats reset boundary drift');
	if (stable(before.planner) !== stable(after.planner)) throw new Error('planner boundary drift');
	if (stable(activitySemanticMultiset(before.activity)) !== stable(activitySemanticMultiset(after.activity))) throw new Error('activity boundary drift');
	decimalMapDelta(before.database, after.database, 'database boundary counter');
	for (const name of TABLE_NAMES) {
		if (stable(before.tables[name].maintenance) !== stable(after.tables[name].maintenance)) throw new Error(`maintenance boundary drift: ${name}`);
		const left = Object.fromEntries(TABLE_COUNTER_KEYS.map((key) => [key, before.tables[name][key]]));
		const right = Object.fromEntries(TABLE_COUNTER_KEYS.map((key) => [key, after.tables[name][key]]));
		decimalMapDelta(left, right, `table boundary counter ${name}`);
	}
	return true;
}

export function validateResourceEvidence(samples, mode, expectedCase, target, window) {
	const sampling = target.resourceSampling;
	assertExactKeys(sampling, ['minSamples', 'samplingIntervalMs', 'maxGapMs'], 'resource sampling');
	if (!Number.isSafeInteger(sampling.minSamples) || sampling.minSamples < 2 || !Number.isSafeInteger(sampling.samplingIntervalMs) || sampling.samplingIntervalMs <= 0 || !Number.isSafeInteger(sampling.maxGapMs) || sampling.maxGapMs < sampling.samplingIntervalMs) throw new Error('resource sampling metadata');
	const durationMinimum = Math.ceil((window.endMs - window.startMs) / sampling.maxGapMs) + 1;
	const requiredSamples = Math.max(sampling.minSamples, durationMinimum);
	if (!Array.isArray(samples) || samples.length < requiredSamples) throw new Error('resource sample count');
	let previous = null; let latestPreStart = null; let earliestPostEnd = null;
	for (const sample of samples) {
		assertExactKeys(sample, ['case', 'capturedAt', 'samplingIntervalMs', 'maxGapMs', 'roles'], 'resource sample');
		validateCase(sample.case, { ...expectedCase, mode });
		if (sample.samplingIntervalMs !== sampling.samplingIntervalMs || sample.maxGapMs !== sampling.maxGapMs) throw new Error('resource sampling binding');
		if (!canonicalIso(sample.capturedAt)) throw new Error('resource timestamp');
		const at = Date.parse(sample.capturedAt);
		if (previous !== null && (at <= previous || at - previous > sampling.maxGapMs)) throw new Error('resource max gap');
		previous = at;
		if (at <= window.startMs) latestPreStart = at;
		if (earliestPostEnd === null && at >= window.endMs) earliestPostEnd = at;
		assertExactKeys(sample.roles, RESOURCE_ROLES, 'resource role');
		for (const role of RESOURCE_ROLES) {
			const value = sample.roles[role];
			assertExactKeys(value, ['containerId', 'containerName', 'cpuPercent', 'cpuPercentDisplay', 'memoryUsedBytes', 'memoryLimitBytes', 'memoryUsageDisplay', 'memoryPercent', 'memoryPercentDisplay'], `resource ${role}`);
			if (value.containerId !== target.containers[role].id || value.containerName !== target.containers[role].name) throw new Error(`resource container: ${role}`);
			if (finite(value.cpuPercent, 'resource CPU') < 0) throw new Error('resource CPU');
			if (parsePercentDisplay(value.cpuPercentDisplay, 'CPU') !== value.cpuPercent) throw new Error('resource CPU display consistency');
			const used = decimal(value.memoryUsedBytes, 'resource memory used'); const limit = decimal(value.memoryLimitBytes, 'resource memory limit');
			if (used > BigInt(Number.MAX_SAFE_INTEGER) || limit > BigInt(Number.MAX_SAFE_INTEGER) || limit === 0n || used > limit) throw new Error('resource memory bounds');
			const memoryPercent = finite(value.memoryPercent, 'resource memory percent');
			if (parsePercentDisplay(value.memoryPercentDisplay, 'memory') !== memoryPercent) throw new Error('resource memory percent display consistency');
			const [usedDisplay, limitDisplay, extraDisplay] = String(value.memoryUsageDisplay).split('/').map((item) => item.trim());
			if (extraDisplay !== undefined || parseByteDisplay(usedDisplay) !== value.memoryUsedBytes || parseByteDisplay(limitDisplay) !== value.memoryLimitBytes) throw new Error('resource memory display consistency');
			validateMemoryDisplayConsistency(usedDisplay, limitDisplay, value.memoryPercentDisplay);
		}
	}
	if (latestPreStart === null || earliestPostEnd === null || window.startMs - latestPreStart > sampling.maxGapMs || earliestPostEnd - window.endMs > sampling.maxGapMs) throw new Error('resource boundary coverage');
	return true;
}

export function validatePgStatStatements(before, after, expectedCase) {
	validateCase(before.case, expectedCase); validateCase(after.case, expectedCase);
	if (typeof before.available !== 'boolean' || typeof after.available !== 'boolean' || before.available !== after.available) throw new Error('pg_stat_statements availability drift');
	if (!Array.isArray(before.rows) || !Array.isArray(after.rows)) throw new Error('pg_stat_statements rows');
	if (!before.available) {
		assertExactKeys(before, ['case', 'available', 'reason', 'rows'], 'pg_stat_statements unavailable');
		assertExactKeys(after, ['case', 'available', 'reason', 'rows'], 'pg_stat_statements unavailable');
		if (typeof before.reason !== 'string' || !before.reason || before.reason !== after.reason || before.rows.length || after.rows.length) throw new Error('pg_stat_statements unavailable inventory');
		return { available: false, deltas: [] };
	}
	if (!before.rows.length || !after.rows.length) throw new Error('pg_stat_statements empty inventory');
	assertExactKeys(before, ['case', 'available', 'databaseId', 'rows'], 'pg_stat_statements available');
	assertExactKeys(after, ['case', 'available', 'databaseId', 'rows'], 'pg_stat_statements available');
	if (before.databaseId !== after.databaseId) throw new Error('pg_stat_statements database drift');
	decimal(before.databaseId, 'pg_stat_statements databaseId');
	const previous = statementMap(before.rows, before.databaseId, 'before'); const current = statementMap(after.rows, after.databaseId, 'after');
	for (const key of previous.keys()) if (!current.has(key)) throw new Error('pg_stat_statements before row disappeared');
	const deltas = [];
	for (const [key, row] of current) {
		const left = previous.get(key); const calls = decimal(row.calls, 'pg_stat_statements calls'); const total = decimal(row.totalExecTimeMicros, 'pg_stat_statements total');
		const beforeCalls = left ? decimal(left.calls, 'pg_stat_statements calls') : 0n; const beforeTotal = left ? decimal(left.totalExecTimeMicros, 'pg_stat_statements total') : 0n;
		if (calls < beforeCalls || total < beforeTotal) throw new Error('pg_stat_statements counter regression');
		deltas.push({ key, calls: String(calls - beforeCalls), totalExecTimeMicros: String(total - beforeTotal) });
	}
	return { available: true, deltas };
}

function validateRuntime(snapshot, target, expectedCase) {
	validateRuntimeTarget(target);
	assertExactKeys(snapshot, ['case', 'phase', 'capturedAt', 'containers', 'database', 'redis'], 'runtime evidence');
	validateCase(snapshot.case, expectedCase);
	if (typeof snapshot.phase !== 'string' || !snapshot.phase || !canonicalIso(snapshot.capturedAt)) throw new Error('runtime evidence schema');
	assertExactKeys(snapshot.containers, RESOURCE_ROLES, 'runtime role');
	for (const role of RESOURCE_ROLES) if (stable(snapshot.containers[role]) !== stable(target.containers[role])) throw new Error(`runtime target drift: ${role}`);
	if (stable(snapshot.database) !== stable(target.database)) throw new Error('runtime database target drift');
	assertExactKeys(snapshot.redis, ['runId', 'serverPort', 'uptimeSeconds'], 'runtime Redis');
	if (snapshot.redis.serverPort !== target.redis.serverPort || snapshot.redis.runId !== target.redis.runId || !DECIMAL.test(snapshot.redis.uptimeSeconds)) throw new Error('runtime Redis target drift');
}

function validateTable(value, name) {
	assertExactKeys(value, [...TABLE_COUNTER_KEYS, 'maintenance'], `table row ${name}`);
	for (const key of TABLE_COUNTER_KEYS) decimal(value[key], `table counter ${name}.${key}`);
	assertExactKeys(value.maintenance, MAINTENANCE_KEYS, `maintenance ${name}`);
	for (const key of MAINTENANCE_KEYS) if (!nullableIso(value.maintenance[key])) throw new Error(`maintenance timestamp ${name}.${key}`);
}
function validateDecimalObject(value, keys, label) { assertExactKeys(value, keys, label); for (const key of keys) decimal(value[key], `${label} ${key}`); }
function runtimeIdentity(value) { return { containers: value.containers, database: value.database, redis: { runId: value.redis.runId, serverPort: value.redis.serverPort } }; }
function metric(metrics, name, expectedType, expectedContains = 'default') {
	const raw = metrics[name];
	if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error(`metric missing: ${name}`);
	if (!Object.hasOwn(raw, 'values')) {
		if (['type', 'contains', 'thresholds'].some((key) => Object.hasOwn(raw, key))) throw new Error(`${name} metric shape`);
		return { values: raw, wrapped: false };
	}
	if (!raw.values || typeof raw.values !== 'object' || Array.isArray(raw.values)) throw new Error(`${name} metric shape`);
	const allowed = new Set(['type', 'contains', 'values', 'thresholds']);
	if (Object.keys(raw).some((key) => !allowed.has(key))) throw new Error(`${name} metric shape`);
	const hasType = Object.hasOwn(raw, 'type'); const hasContains = Object.hasOwn(raw, 'contains');
	if (hasType !== hasContains || (hasType && (raw.type !== expectedType || raw.contains !== expectedContains))) throw new Error(`${name} metric type`);
	return { values: raw.values, wrapped: hasType };
}
function counterCount(metricValue, label) {
	const value = metricValue.values;
	if (metricValue.wrapped && !Object.hasOwn(value, 'rate')) throw new Error(`${label} counter rate`);
	if (Object.hasOwn(value, 'rate') && finite(value.rate, `${label} rate`) < 0) throw new Error(`${label} counter rate`);
	return finite(value.count, label);
}
function rate(metricValue, label, expectedTotal) {
	const value = metricValue.values;
	const keys = ['rate', 'value'].filter((key) => Object.hasOwn(value, key));
	if (keys.length !== 1) throw new Error(`${label} metric shape`);
	const result = finite(value[keys[0]], label);
	const hasPasses = Object.hasOwn(value, 'passes'); const hasFails = Object.hasOwn(value, 'fails');
	if (hasPasses !== hasFails || (metricValue.wrapped && !hasPasses)) throw new Error(`${label} passes fails shape`);
	if (hasPasses) {
		if (!Number.isSafeInteger(value.passes) || value.passes < 0 || !Number.isSafeInteger(value.fails) || value.fails < 0 || value.passes + value.fails !== expectedTotal || result !== value.passes / expectedTotal) throw new Error(`${label} passes fails math`);
	}
	return result;
}
function finite(value, label) { if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${label} must be finite`); return value; }
function decimalMapDelta(before, after, label) { assertSameKeys(before, after, `${label} schema`); return Object.fromEntries(Object.keys(before).sort().map((key) => { const left = decimal(before[key], `${label} ${key}`); const right = decimal(after[key], `${label} ${key}`); if (right < left) throw new Error(`${label} regression: ${key}`); return [key, String(right - left)]; })); }
function decimal(value, label) { if (typeof value !== 'string' || !DECIMAL.test(value)) throw new Error(`${label} must be canonical decimal string`); return BigInt(value); }
function canonicalIso(value) { return typeof value === 'string' && Number.isFinite(Date.parse(value)) && new Date(value).toISOString() === value; }
function nullableIso(value) { return value === null || rfc3339Utc(value); }
function assertObject(value, label) { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${label} schema`); }
function assertSameKeys(a, b, label) { assertObject(a, label); assertObject(b, label); if (stable(Object.keys(a).sort()) !== stable(Object.keys(b).sort())) throw new Error(`${label} mismatch`); }
function assertExactKeys(value, keys, label) { assertObject(value, label); if (stable(Object.keys(value).sort()) !== stable([...keys].sort())) throw new Error(`${label} set mismatch`); }
function statementKey(row) { return [row.userId, row.databaseId, row.queryId, row.topLevel].join('|'); }
function statementRow(row) { assertExactKeys(row, ['userId', 'databaseId', 'queryId', 'topLevel', 'calls', 'totalExecTimeMicros', 'query'], 'pg_stat_statements row'); for (const key of ['userId', 'databaseId', 'calls', 'totalExecTimeMicros']) decimal(row[key], `pg_stat_statements ${key}`); if (typeof row.queryId !== 'string' || !SIGNED_DECIMAL.test(row.queryId)) throw new Error('pg_stat_statements queryId'); if (typeof row.topLevel !== 'boolean' || typeof row.query !== 'string' || !row.query) throw new Error('pg_stat_statements row'); return row; }
function statementMap(rows, databaseId, label) { const result = new Map(); for (const raw of rows) { const row = statementRow(raw); if (row.databaseId !== databaseId) throw new Error('pg_stat_statements current database filter'); const key = statementKey(row); if (result.has(key)) throw new Error(`pg_stat_statements duplicate ${label}`); result.set(key, row); } return result; }
function validateContainerTarget(value, role) { assertExactKeys(value, ['name', 'id', 'imageId', 'startedAt', 'composeProject', 'composeService', 'configHash', 'health', 'publishedPorts'], `runtime ${role}`); if (typeof value.name !== 'string' || !value.name || !/^[a-f0-9]{64}$/.test(value.id) || !/^sha256:[a-f0-9]{64}$/.test(value.imageId) || !/^[a-f0-9]{64}$/.test(value.configHash) || !rfc3339Utc(value.startedAt) || typeof value.composeProject !== 'string' || !value.composeProject || typeof value.composeService !== 'string' || !value.composeService || typeof value.health !== 'string' || !value.health || !Array.isArray(value.publishedPorts) || !value.publishedPorts.length || value.publishedPorts.some((port) => typeof port !== 'string' || !port) || new Set(value.publishedPorts).size !== value.publishedPorts.length) throw new Error(`runtime target schema: ${role}`); }
function stable(value) { return JSON.stringify(canonical(value)); }
function canonical(value) { if (Array.isArray(value)) return value.map(canonical); if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])])); return value; }
function rfc3339Utc(value) { return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) && Number.isFinite(Date.parse(value)); }
function fail(label) { throw new Error(label); }
