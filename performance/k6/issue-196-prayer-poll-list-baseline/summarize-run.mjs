import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const [endpoint, summaryPath, beforePath, afterPath, sqlLogPath, resourcePath, metadataPath, outputPath] = process.argv.slice(2);
if (![endpoint, summaryPath, beforePath, afterPath, sqlLogPath, resourcePath, metadataPath, outputPath].every(Boolean)) {
	throw new Error('Usage: node summarize-run.mjs <endpoint> <k6-summary> <db-before> <db-after> <sql-log> <resource-tsv> <metadata> <output>');
}

const TOOL_STATUS = 'scenario-ready';
const metadata = readJson(metadataPath);
const k6ExitStatus = Number(metadata.runtime?.k6ExitStatus);
const resourceSamplerExitStatus = Number(metadata.runtime?.resourceSamplerExitStatus);
const fixtureWindowExitStatus = Number(metadata.runtime?.fixtureWindowExitStatus);
const logCaptureExitStatus = Number(metadata.runtime?.logCaptureExitStatus);
const afterDbSnapshotExitStatus = Number(metadata.runtime?.afterDbSnapshotExitStatus);
if (![k6ExitStatus, resourceSamplerExitStatus, fixtureWindowExitStatus, logCaptureExitStatus, afterDbSnapshotExitStatus].every(Number.isInteger)) {
	throw new Error('Runtime metadata must contain integer k6, sampler, window, log, and DB snapshot exit statuses.');
}
const summary = readJsonOptional(summaryPath);
const before = readJsonOptional(beforePath);
const after = readJsonOptional(afterPath);
const sqlLog = readTextOptional(sqlLogPath);
const resourceText = readTextOptional(resourcePath);
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
if (k6ExitStatus !== 0) rejectionReasons.push(`k6-exit-${k6ExitStatus}`);
if (resourceSamplerExitStatus !== 0) rejectionReasons.push(`resource-sampler-exit-${resourceSamplerExitStatus}`);
if (fixtureWindowExitStatus !== 0) rejectionReasons.push('fixture-window-crossed');
if (logCaptureExitStatus !== 0) rejectionReasons.push(`log-capture-exit-${logCaptureExitStatus}`);
if (afterDbSnapshotExitStatus !== 0) rejectionReasons.push(`after-db-snapshot-exit-${afterDbSnapshotExitStatus}`);
if (!summary) rejectionReasons.push('missing-k6-summary');
if (!before || !after) rejectionReasons.push('missing-db-snapshot');
if (queryLines.length === 0) rejectionReasons.push('missing-sql-evidence');
if (resourceText === null) rejectionReasons.push('missing-resource-evidence');
if (requestCount === 0) rejectionReasons.push('zero-http-requests');
if (!Number.isFinite(failureRate)) rejectionReasons.push('missing-correctness-rate');
else if (failureRate !== 0) rejectionReasons.push('correctness-failure');
if (!Object.values(latency).every(Number.isFinite)) rejectionReasons.push('missing-latency-metrics');
if (!Number.isFinite(throughputPerSecond)) rejectionReasons.push('missing-throughput');
if (!validTableEvidence(before?.tables, after?.tables)) rejectionReasons.push('invalid-db-table-snapshot');
const tableCounterDelta = diffTableCounters(before?.tables || [], after?.tables || []);
const resources = summarizeResources(resourceText || '');
const capturedContainers = new Set(resources.map((resource) => resource.container));
for (const container of [metadata.runtime?.appContainer, metadata.runtime?.dbContainer]) {
	if (!container || !capturedContainers.has(container)) {
		rejectionReasons.push(`missing-resource-samples:${container || 'undefined'}`);
	}
}
const writeCounters = tableCounterDelta.reduce((sum, table) => (
	sum + table.n_tup_ins + table.n_tup_upd + table.n_tup_del
), 0);
if (writeCounters !== 0) rejectionReasons.push(`write-counter-delta:${writeCounters}`);
const accepted = k6ExitStatus === 0
	&& resourceSamplerExitStatus === 0
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
	},
	nPlusOneEvidence: {
		loopSignal: repeatedSql.filter((entry) => entry.count >= Math.max(2, requestCount * 2)),
		note: 'Repeated normalized SQL and queriesPerRequest are evidence only; compare the same fixture and endpoint before/after implementation.',
	},
};

mkdirSync(dirname(resolve(outputPath)), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
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

function metricValues(k6Summary, name) {
	return k6Summary.metrics?.[name]?.values || {};
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

function validTableEvidence(beforeTables, afterTables) {
	const counters = ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del', 'n_live_tup'];
	if (!Array.isArray(beforeTables) || !Array.isArray(afterTables) || beforeTables.length === 0 || beforeTables.length !== afterTables.length) {
		return false;
	}
	const validRows = (tables) => tables.every((table) => typeof table.relname === 'string' && table.relname.length > 0
		&& counters.every((counter) => strictNumber(table[counter]) !== null));
	if (!validRows(beforeTables) || !validRows(afterTables)) return false;
	const beforeNames = beforeTables.map((table) => table.relname).sort();
	const afterNames = afterTables.map((table) => table.relname).sort();
	return beforeNames.every((name, index) => name === afterNames[index]);
}

function strictNumber(value) {
	return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function summarizeResources(tsv) {
	const byContainer = new Map();
	for (const line of tsv.trim().split(/\r?\n/)) {
		if (!line) continue;
		const [capturedAt, container, cpuText, memoryText, memoryPercentText] = line.split('\t');
		const cpuPercent = Number(cpuText?.replace('%', ''));
		const memoryPercent = Number(memoryPercentText?.replace('%', ''));
		const memoryUsed = memoryText?.split('/')[0]?.trim() || null;
		if (!container || !Number.isFinite(cpuPercent) || !Number.isFinite(memoryPercent)) continue;
		if (!byContainer.has(container)) byContainer.set(container, []);
		byContainer.get(container).push({ capturedAt, cpuPercent, memoryPercent, memoryUsed });
	}
	return [...byContainer.entries()].map(([container, samples]) => ({
		container,
		sampleCount: samples.length,
		averageCpuPercent: average(samples.map((sample) => sample.cpuPercent)),
		maxCpuPercent: Math.max(...samples.map((sample) => sample.cpuPercent)),
		averageMemoryPercent: average(samples.map((sample) => sample.memoryPercent)),
		maxMemoryPercent: Math.max(...samples.map((sample) => sample.memoryPercent)),
		maxObservedMemory: samples.reduce((current, sample) => sample.memoryPercent >= current.memoryPercent ? sample : current).memoryUsed,
	}));
}

function average(values) {
	return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
}
