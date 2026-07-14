import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const [endpoint, summaryPath, beforePath, afterPath, sqlLogPath, resourcePath, integrityPath, metadataPath, outputPath] = process.argv.slice(2);
if (![endpoint, summaryPath, beforePath, afterPath, sqlLogPath, resourcePath, integrityPath, metadataPath, outputPath].every(Boolean)) {
	throw new Error('Usage: node summarize-run.mjs <endpoint> <k6-summary> <db-before> <db-after> <sql-log> <resource-tsv> <integrity-jsonl> <metadata> <output>');
}

const TOOL_STATUS = 'scenario-ready';
const EXPECTED_TABLES = [
	'campus_duty_assignments', 'campus_members', 'campuses', 'charge_items', 'coffee_brands',
	'coffee_menu_catalog', 'devotion_daily_checks', 'meal_poll_charge_groups', 'meal_poll_settlements',
	'notification_logs', 'payment_accounts', 'penalty_rules', 'poll_comments', 'poll_options',
	'poll_response_options', 'poll_responses', 'poll_template_options', 'poll_templates', 'polls',
	'prayer_group_members', 'prayer_groups', 'prayer_seasons', 'prayer_submissions', 'prayer_weeks',
	'user_fcm_tokens', 'users', 'weekly_devotion_records',
].sort();
const EXPECTED_PLANNER_KEYS = [
	'cpu_index_tuple_cost', 'cpu_tuple_cost', 'effective_cache_size', 'jit',
	'max_parallel_workers_per_gather', 'plan_cache_mode', 'random_page_cost', 'work_mem',
].sort();
const TABLE_COUNTER_FIELDS = [
	'seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd',
	'n_tup_del', 'n_live_tup', 'n_dead_tup', 'analyze_count', 'autoanalyze_count',
	'vacuum_count', 'autovacuum_count',
];
const MAINTENANCE_TIMESTAMP_FIELDS = [
	'last_analyze', 'last_autoanalyze', 'last_vacuum', 'last_autovacuum',
];
const EXPECTED_TABLE_FIELDS = [
	'schemaname', 'relname', ...TABLE_COUNTER_FIELDS, ...MAINTENANCE_TIMESTAMP_FIELDS,
].sort();
const EXPECTED_DATABASE_IDENTITY_KEYS = [
	'currentDatabase', 'postmasterStartedAt', 'serverAddress', 'serverPort',
].sort();
const metadata = readJson(metadataPath);
const k6ExitStatus = Number(metadata.runtime?.k6ExitStatus);
const resourceSamplerExitStatus = Number(metadata.runtime?.resourceSamplerExitStatus);
const fixtureWindowExitStatus = Number(metadata.runtime?.fixtureWindowExitStatus);
const logCaptureExitStatus = Number(metadata.runtime?.logCaptureExitStatus);
const afterDbSnapshotExitStatus = Number(metadata.runtime?.afterDbSnapshotExitStatus);
const warmupExitStatus = Number(metadata.runtime?.warmupExitStatus);
const integritySamplerExitStatus = Number(metadata.runtime?.integritySamplerExitStatus);
const runtimeContinuityExitStatus = Number(metadata.runtime?.runtimeContinuityExitStatus);
if (![k6ExitStatus, resourceSamplerExitStatus, fixtureWindowExitStatus, logCaptureExitStatus,
	afterDbSnapshotExitStatus, warmupExitStatus, integritySamplerExitStatus,
	runtimeContinuityExitStatus].every(Number.isInteger)) {
	throw new Error('Runtime metadata must contain integer warmup, k6, sampler, integrity, window, log, and DB snapshot exit statuses.');
}
const summary = readJsonOptional(summaryPath);
const before = readJsonOptional(beforePath);
const after = readJsonOptional(afterPath);
const sqlLog = readTextOptional(sqlLogPath);
const resourceText = readTextOptional(resourcePath);
const integritySamples = readJsonLinesOptional(integrityPath);
const metricName = endpoint.replace(/-/g, '_');
const durationValues = metricValues(summary || {}, `endpoint_${metricName}_duration`);
const requestValues = metricValues(summary || {}, `endpoint_${metricName}_requests`);
const failureValues = metricValues(summary || {}, `endpoint_${metricName}_failures`);
const queryLines = extractSql(sqlLog || '');

const repeatedSqlMap = new Map();
for (const query of queryLines) {
	const normalized = normalizeSql(query);
	repeatedSqlMap.set(normalized, (repeatedSqlMap.get(normalized) || 0) + 1);
}
const repeatedSql = [...repeatedSqlMap.entries()]
	.map(([sql, count]) => ({ sql, count }))
	.filter((entry) => entry.count > 1)
	.sort((left, right) => right.count - left.count || left.sql.localeCompare(right.sql));

const requestCount = strictNumber(requestValues.count) ?? 0;
const queryCount = queryLines.length;
const failureRate = strictNumber(failureValues.rate);
const latency = {
	p50Ms: strictNumber(durationValues['p(50)']),
	p95Ms: strictNumber(durationValues['p(95)']),
	p99Ms: strictNumber(durationValues['p(99)']),
	maxMs: strictNumber(durationValues.max),
};
const throughputPerSecond = strictNumber(requestValues.rate);
const rejectionReasons = [];
const addReason = (reason) => { if (!rejectionReasons.includes(reason)) rejectionReasons.push(reason); };
if (warmupExitStatus !== 0) addReason(`warmup-exit-${warmupExitStatus}`);
if (k6ExitStatus !== 0) rejectionReasons.push(`k6-exit-${k6ExitStatus}`);
if (resourceSamplerExitStatus !== 0) rejectionReasons.push(`resource-sampler-exit-${resourceSamplerExitStatus}`);
if (integritySamplerExitStatus !== 0) rejectionReasons.push(`integrity-sampler-exit-${integritySamplerExitStatus}`);
if (runtimeContinuityExitStatus !== 0) rejectionReasons.push(`runtime-continuity-exit-${runtimeContinuityExitStatus}`);
if (metadata.runtime?.exclusiveWindowConfirmed !== true) rejectionReasons.push('exclusive-window-not-confirmed');
if (fixtureWindowExitStatus !== 0) rejectionReasons.push('fixture-window-crossed');
if (logCaptureExitStatus !== 0) rejectionReasons.push(`log-capture-exit-${logCaptureExitStatus}`);
if (afterDbSnapshotExitStatus !== 0) rejectionReasons.push(`after-db-snapshot-exit-${afterDbSnapshotExitStatus}`);
if (!summary) rejectionReasons.push('missing-k6-summary');
if (!before || !after) rejectionReasons.push('missing-db-snapshot');
if (queryLines.length === 0) rejectionReasons.push('missing-sql-evidence');
if (resourceText === null) rejectionReasons.push('missing-resource-evidence');
for (const name of [`endpoint_${metricName}_duration`, `endpoint_${metricName}_requests`, `endpoint_${metricName}_failures`]) {
	if (!Object.hasOwn(summary?.metrics || {}, name)) addReason(`missing-required-metric:${name}`);
}
if (!Number.isInteger(requestCount) || requestCount <= 0) addReason('zero-http-requests');
if (!Number.isFinite(failureRate)) rejectionReasons.push('missing-correctness-rate');
else if (failureRate !== 0) rejectionReasons.push('correctness-failure');
if (!Object.values(latency).every((value) => Number.isFinite(value) && value >= 0)) rejectionReasons.push('missing-latency-metrics');
else if (!(latency.p50Ms <= latency.p95Ms && latency.p95Ms <= latency.p99Ms && latency.p99Ms <= latency.maxMs)) {
	rejectionReasons.push('invalid-latency-order');
}
if (!Number.isFinite(throughputPerSecond)) rejectionReasons.push('missing-throughput');
else if (throughputPerSecond <= 0) rejectionReasons.push('non-positive-throughput');
const tableValidation = validateTableEvidence(before, after);
for (const reason of tableValidation.rejectionReasons) addReason(reason);
const integrityValidation = validateIntegrityEvidence(integritySamples, metadata.runtime);
for (const reason of integrityValidation.rejectionReasons) addReason(reason);
const tableCounterDelta = diffTableCounters(before?.tables || [], after?.tables || []);
const resourceValidation = validateResourceEvidence(resourceText || '', metadata.runtime);
for (const reason of resourceValidation.rejectionReasons) addReason(reason);
const resources = resourceValidation.resources;
const capturedContainers = new Set(resources.map((resource) => resource.container));
for (const container of [metadata.runtime?.appContainer, metadata.runtime?.dbContainer]) {
	if (!container || !capturedContainers.has(container)) {
		rejectionReasons.push(`missing-resource-samples:${container || 'undefined'}`);
	}
}
const writeCounters = tableCounterDelta.reduce((sum, table) => (
	sum + table.n_tup_ins + table.n_tup_upd + table.n_tup_del
), 0);
const accepted = k6ExitStatus === 0
	&& warmupExitStatus === 0
	&& resourceSamplerExitStatus === 0
	&& integritySamplerExitStatus === 0
	&& runtimeContinuityExitStatus === 0
	&& fixtureWindowExitStatus === 0
	&& logCaptureExitStatus === 0
	&& afterDbSnapshotExitStatus === 0
	&& requestCount > 0
	&& Number.isFinite(failureRate)
	&& failureRate === 0
	&& rejectionReasons.length === 0;

const report = {
	accepted,
	measurementStatus: accepted ? 'measured' : 'rejected',
	rejectionReasons,
	toolPreparationStatus: TOOL_STATUS,
	endpoint,
	mode: metadata.mode,
	datasetId: metadata.datasetId,
	fixtureRunId: metadata.fixtureRunId,
	executionRunId: metadata.executionRunId,
	runtime: metadata.runtime,
	resources,
	http: {
		p50Ms: Number.isFinite(latency.p50Ms) ? latency.p50Ms : null,
		p95Ms: Number.isFinite(latency.p95Ms) ? latency.p95Ms : null,
		p99Ms: Number.isFinite(latency.p99Ms) ? latency.p99Ms : null,
		maxMs: Number.isFinite(latency.maxMs) ? latency.maxMs : null,
		throughputPerSecond: Number.isFinite(throughputPerSecond) ? throughputPerSecond : null,
		requestCount,
		failureRate: Number.isFinite(failureRate) ? failureRate : null,
	},
	db: {
		queryCount,
		queriesPerRequest: requestCount === 0 ? null : queryCount / requestCount,
		repeatedSql,
		tableCounterDelta,
		writeCounters,
		integrity: {
			snapshotOrderValid: tableValidation.snapshotOrderValid,
			plannerSettingsStable: tableValidation.plannerSettingsStable,
			analyzeStateStable: tableValidation.analyzeStateStable,
			vacuumStateStable: tableValidation.vacuumStateStable,
			databaseIdentityStable: tableValidation.databaseIdentityStable,
			runtimeSampleCount: integrityValidation.sampleCount,
		},
	},
	nPlusOneEvidence: {
		loopSignal: repeatedSql.filter((entry) => entry.count >= Math.max(2, requestCount * 2)),
		note: 'Repeated normalized SQL and queriesPerRequest are evidence only; compare the same fixture and endpoint before/after implementation.',
	},
};

mkdirSync(dirname(resolve(outputPath)), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx' });
if (!accepted) {
	console.error(`Endpoint evidence was rejected (${rejectionReasons.join(', ')}); rejected report was preserved.`);
	process.exitCode = 2;
}

function readJson(path) {
	return JSON.parse(readFileSync(path, 'utf8'));
}

function readJsonOptional(path) {
	try {
		return readJson(path);
	} catch {
		return null;
	}
}

function readTextOptional(path) {
	try {
		return readFileSync(path, 'utf8');
	} catch {
		return null;
	}
}

function readJsonLinesOptional(path) {
	try {
		return readFileSync(path, 'utf8').trim().split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
	} catch {
		return null;
	}
}

function metricValues(k6Summary, name) {
	const metric = k6Summary.metrics?.[name];
	if (!metric || typeof metric !== 'object') return {};
	return metric.values && typeof metric.values === 'object' ? metric.values : metric;
}

function extractSql(log) {
	return log.split(/\r?\n/)
		.filter((line) => line.includes('org.hibernate.SQL'))
		.map((line) => line.slice(line.indexOf('org.hibernate.SQL') + 'org.hibernate.SQL'.length)
			.replace(/^\s*[-:]?\s*/, ''))
		.filter(Boolean);
}

function normalizeSql(sql) {
	return sql
		.replace(/'[^']*'/g, '?')
		.replace(/\b\d+\b/g, '?')
		.replace(/\s+/g, ' ')
		.trim()
		.toLowerCase();
}

function diffTableCounters(beforeTables, afterTables) {
	const beforeByName = new Map(beforeTables.map((table) => [table.relname, table]));
	const counters = ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del'];
	return afterTables.map((table) => {
		const previous = beforeByName.get(table.relname) || {};
		const delta = {
			table: table.relname,
			estimatedRowsBefore: Number(previous.n_live_tup || 0),
			estimatedRowsAfter: Number(table.n_live_tup || 0),
		};
		for (const counter of counters) {
			delta[counter] = Number(table[counter] || 0) - Number(previous[counter] || 0);
		}
		return delta;
	}).filter((table) => counters.some((counter) => table[counter] !== 0));
}

function validateTableEvidence(before, after) {
	const rejectionReasons = [];
	const cumulativeCounters = ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del'];
	const beforeTables = before?.tables;
	const afterTables = after?.tables;
	const beforeTime = Date.parse(before?.capturedAt);
	const afterTime = Date.parse(after?.capturedAt);
	const snapshotOrderValid = Number.isFinite(beforeTime) && Number.isFinite(afterTime) && beforeTime < afterTime;
	if (!snapshotOrderValid) rejectionReasons.push('snapshot-time-order');
	const plannerSettingsValid = [before?.plannerSettings, after?.plannerSettings].every((settings) => (
		isExactObject(settings, EXPECTED_PLANNER_KEYS)
		&& Object.values(settings).every(isNonEmptyScalar)
	));
	if (!plannerSettingsValid) rejectionReasons.push('invalid-planner-settings');
	const plannerSettingsStable = plannerSettingsValid && semanticEqual(before.plannerSettings, after.plannerSettings);
	if (plannerSettingsValid && !plannerSettingsStable) rejectionReasons.push('planner-settings-changed');
	const databaseIdentityValid = [before?.databaseIdentity, after?.databaseIdentity].every((identity) => (
		isExactObject(identity, EXPECTED_DATABASE_IDENTITY_KEYS)
		&& typeof identity.currentDatabase === 'string' && identity.currentDatabase.length > 0
		&& typeof identity.serverAddress === 'string' && identity.serverAddress.length > 0
		&& Number.isInteger(identity.serverPort) && identity.serverPort > 0
		&& isValidTimestamp(identity.postmasterStartedAt)
	));
	if (!databaseIdentityValid) rejectionReasons.push('invalid-database-identity');
	const databaseIdentityStable = databaseIdentityValid
		&& semanticEqual(before.databaseIdentity, after.databaseIdentity);
	if (databaseIdentityValid && !databaseIdentityStable) rejectionReasons.push('database-identity-changed');
	if (!Array.isArray(beforeTables) || !Array.isArray(afterTables)) {
		return {
			rejectionReasons: [...rejectionReasons, 'invalid-db-table-snapshot'], snapshotOrderValid,
			plannerSettingsStable, analyzeStateStable: false, vacuumStateStable: false, databaseIdentityStable,
		};
	}
	const beforeNames = beforeTables.map((table) => table.relname).sort();
	const afterNames = afterTables.map((table) => table.relname).sort();
	if (new Set(beforeNames).size !== EXPECTED_TABLES.length || new Set(afterNames).size !== EXPECTED_TABLES.length
		|| !semanticEqual(beforeNames, EXPECTED_TABLES) || !semanticEqual(afterNames, EXPECTED_TABLES)) {
		rejectionReasons.push('required-table-set-mismatch');
	}
	const beforeByName = new Map(beforeTables.map((table) => [table.relname, table]));
	let analyzeStateStable = true;
	let vacuumStateStable = true;
	for (const current of afterTables) {
		const previous = beforeByName.get(current.relname);
		const rows = [previous, current];
		if (!previous || rows.some((row) => !isExactObject(row, EXPECTED_TABLE_FIELDS)
			|| row.schemaname !== 'public' || row.relname !== current.relname
			|| TABLE_COUNTER_FIELDS.some((field) => strictNumber(row[field]) === null || row[field] < 0)
			|| MAINTENANCE_TIMESTAMP_FIELDS.some((field) => row[field] !== null && !isValidTimestamp(row[field])))) {
			rejectionReasons.push('invalid-db-table-snapshot');
			continue;
		}
		for (const counter of [...cumulativeCounters, 'analyze_count', 'autoanalyze_count', 'vacuum_count', 'autovacuum_count']) {
			const delta = current[counter] - previous[counter];
			if (delta < 0) rejectionReasons.push('counter-regression');
			if (['n_tup_ins', 'n_tup_upd', 'n_tup_del'].includes(counter) && delta !== 0) {
				rejectionReasons.push(`write-counter-delta:${current.relname}:${counter}:${delta}`);
			}
		}
		for (const field of ['last_analyze', 'last_autoanalyze', 'analyze_count', 'autoanalyze_count']) {
			if (current[field] !== previous[field]) analyzeStateStable = false;
		}
		for (const field of ['last_vacuum', 'last_autovacuum', 'vacuum_count', 'autovacuum_count']) {
			if (current[field] !== previous[field]) vacuumStateStable = false;
		}
	}
	if (!analyzeStateStable) rejectionReasons.push('analyze-state-changed');
	if (!vacuumStateStable) rejectionReasons.push('vacuum-state-changed');
	return {
		rejectionReasons: [...new Set(rejectionReasons)], snapshotOrderValid, plannerSettingsStable,
		analyzeStateStable, vacuumStateStable, databaseIdentityStable,
	};
}

function validateIntegrityEvidence(samples, runtime) {
	const rejectionReasons = [];
	if (!Array.isArray(samples) || samples.length === 0) {
		return { rejectionReasons: ['missing-runtime-integrity-evidence'], sampleCount: 0 };
	}
	for (const reason of validateSampleTimeline(samples.map((sample) => sample.capturedAt), runtime, 'runtime-integrity')) {
		rejectionReasons.push(reason);
	}
	for (const sample of samples) {
		if (sample.observerApplicationName !== 'faithlog_issue196_observer'
			|| !Array.isArray(sample.unexpectedDbSessions) || !Array.isArray(sample.unexpectedHttpClients)) {
			rejectionReasons.push('invalid-runtime-integrity-evidence');
			continue;
		}
		if (sample.unexpectedDbSessions.length > 0) rejectionReasons.push('unexpected-db-session');
		if (sample.unexpectedHttpClients.length > 0) rejectionReasons.push('external-http-activity');
	}
	return { rejectionReasons: [...new Set(rejectionReasons)], sampleCount: samples.length };
}

function semanticEqual(left, right) {
	if (Object.is(left, right)) return true;
	if (Array.isArray(left) && Array.isArray(right)) {
		return left.length === right.length && left.every((value, index) => semanticEqual(value, right[index]));
	}
	if (left && right && typeof left === 'object' && typeof right === 'object') {
		const leftKeys = Object.keys(left).sort();
		const rightKeys = Object.keys(right).sort();
		return semanticEqual(leftKeys, rightKeys) && leftKeys.every((key) => semanticEqual(left[key], right[key]));
	}
	return false;
}

function strictNumber(value) {
	return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function validateResourceEvidence(tsv, runtime) {
	const byContainer = new Map();
	const rejectionReasons = [];
	for (const line of tsv.trim().split(/\r?\n/)) {
		if (!line) continue;
		const [capturedAt, container, cpuText, memoryText, memoryPercentText] = line.split('\t');
		const cpuPercent = Number(cpuText?.replace('%', ''));
		const memoryPercent = Number(memoryPercentText?.replace('%', ''));
		const memoryUsed = memoryText?.split('/')[0]?.trim() || null;
		if (!container || !Number.isFinite(cpuPercent) || !Number.isFinite(memoryPercent)
			|| !memoryUsed || !isValidTimestamp(capturedAt)) {
			rejectionReasons.push('invalid-resource-sample');
			continue;
		}
		if (!byContainer.has(container)) byContainer.set(container, []);
		byContainer.get(container).push({ capturedAt, cpuPercent, memoryPercent, memoryUsed });
	}
	for (const container of [runtime?.appContainer, runtime?.dbContainer]) {
		const samples = byContainer.get(container) || [];
		for (const reason of validateSampleTimeline(samples.map((sample) => sample.capturedAt), runtime, `resource:${container || 'undefined'}`)) {
			rejectionReasons.push(reason);
		}
	}
	const resources = [...byContainer.entries()].map(([container, samples]) => ({
		container,
		sampleCount: samples.length,
		averageCpuPercent: average(samples.map((sample) => sample.cpuPercent)),
		maxCpuPercent: Math.max(...samples.map((sample) => sample.cpuPercent)),
		averageMemoryPercent: average(samples.map((sample) => sample.memoryPercent)),
		maxMemoryPercent: Math.max(...samples.map((sample) => sample.memoryPercent)),
		maxObservedMemory: samples.reduce((current, sample) => sample.memoryPercent >= current.memoryPercent ? sample : current).memoryUsed,
	}));
	return { resources, rejectionReasons: [...new Set(rejectionReasons)] };
}

function validateSampleTimeline(timestamps, runtime, evidenceName) {
	const reasons = [];
	const started = Date.parse(runtime?.measurementStartedAt);
	const ended = Date.parse(runtime?.measurementEndedAt);
	const intervalSeconds = strictNumber(runtime?.samplingIntervalSeconds);
	const maxGapSeconds = strictNumber(runtime?.samplingMaxGapSeconds);
	if (!Number.isFinite(started) || !Number.isFinite(ended) || started >= ended
		|| intervalSeconds !== 1 || maxGapSeconds !== 2) {
		return ['invalid-sampling-contract'];
	}
	const times = timestamps.map((timestamp) => Date.parse(timestamp));
	if (times.length === 0 || times.some((time) => !Number.isFinite(time))) {
		return [`invalid-sample-timeline:${evidenceName}`];
	}
	const maxGapMs = maxGapSeconds * 1000;
	const intervalMs = intervalSeconds * 1000;
	const expectedMinimum = Math.max(2, Math.floor((ended - started) / intervalMs));
	if (times.length < expectedMinimum) reasons.push(`insufficient-samples:${evidenceName}`);
	if (times[0] < started || times[0] - started > maxGapMs
		|| times.at(-1) > ended || ended - times.at(-1) > maxGapMs) {
		reasons.push(`sample-window-coverage:${evidenceName}`);
	}
	for (let index = 1; index < times.length; index += 1) {
		if (times[index] <= times[index - 1]) reasons.push(`non-monotonic-samples:${evidenceName}`);
		if (times[index] - times[index - 1] > maxGapMs) reasons.push(`sample-gap:${evidenceName}`);
	}
	return [...new Set(reasons)];
}

function isExactObject(value, expectedKeys) {
	return value !== null && typeof value === 'object' && !Array.isArray(value)
		&& semanticEqual(Object.keys(value).sort(), expectedKeys);
}

function isNonEmptyScalar(value) {
	return (typeof value === 'string' && value.trim().length > 0)
		|| (typeof value === 'number' && Number.isFinite(value))
		|| typeof value === 'boolean';
}

function isValidTimestamp(value) {
	return typeof value === 'string' && value.length > 0 && Number.isFinite(Date.parse(value));
}

function average(values) {
	return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
}
