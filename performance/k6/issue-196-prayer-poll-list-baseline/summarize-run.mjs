import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const [endpoint, summaryPath, beforePath, afterPath, sqlLogPath, resourcePath, metadataPath, outputPath] = process.argv.slice(2);
if (![endpoint, summaryPath, beforePath, afterPath, sqlLogPath, resourcePath, metadataPath, outputPath].every(Boolean)) {
	throw new Error('Usage: node summarize-run.mjs <endpoint> <k6-summary> <db-before> <db-after> <sql-log> <resource-tsv> <metadata> <output>');
}

const TOOL_STATUS = 'scenario-ready';
const summary = readJson(summaryPath);
const before = readJson(beforePath);
const after = readJson(afterPath);
const metadata = readJson(metadataPath);
const sqlLog = readFileSync(sqlLogPath, 'utf8');
const metricName = endpoint.replace(/-/g, '_');
const durationValues = metricValues(summary, `endpoint_${metricName}_duration`);
const requestValues = metricValues(summary, `endpoint_${metricName}_requests`);
const failureValues = metricValues(summary, `endpoint_${metricName}_failures`);
const queryLines = extractSql(sqlLog);
if (queryLines.length === 0) {
	throw new Error('No org.hibernate.SQL statements were captured. Start the app externally with SQL DEBUG logging before measuring.');
}

const repeatedSqlMap = new Map();
for (const query of queryLines) {
	const normalized = normalizeSql(query);
	repeatedSqlMap.set(normalized, (repeatedSqlMap.get(normalized) || 0) + 1);
}
const repeatedSql = [...repeatedSqlMap.entries()]
	.map(([sql, count]) => ({ sql, count }))
	.filter((entry) => entry.count > 1)
	.sort((left, right) => right.count - left.count || left.sql.localeCompare(right.sql));

const requestCount = Number(requestValues.count || 0);
const queryCount = queryLines.length;
const tableCounterDelta = diffTableCounters(before.tables || [], after.tables || []);
const resources = summarizeResources(readFileSync(resourcePath, 'utf8'));
const capturedContainers = new Set(resources.map((resource) => resource.container));
for (const container of [metadata.runtime?.appContainer, metadata.runtime?.dbContainer]) {
	if (!container || !capturedContainers.has(container)) {
		throw new Error(`Missing CPU/RAM samples for expected container: ${container || 'undefined'}`);
	}
}
const writeCounters = tableCounterDelta.reduce((sum, table) => (
	sum + table.n_tup_ins + table.n_tup_upd + table.n_tup_del
), 0);

const report = {
	measurementStatus: 'measured',
	toolPreparationStatus: TOOL_STATUS,
	endpoint,
	mode: metadata.mode,
	datasetId: metadata.datasetId,
	fixtureRunId: metadata.fixtureRunId,
	runtime: metadata.runtime,
	resources,
	http: {
		p50Ms: durationValues['p(50)'] ?? durationValues.med ?? null,
		p95Ms: durationValues['p(95)'] ?? null,
		p99Ms: durationValues['p(99)'] ?? null,
		maxMs: durationValues.max ?? null,
		throughputPerSecond: requestValues.rate ?? null,
		requestCount,
		failureRate: failureValues.rate ?? null,
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
if (writeCounters !== 0) {
	throw new Error(`Read baseline changed table write counters (${writeCounters}). Check fixture status synchronization before accepting the run.`);
}

function readJson(path) {
	return JSON.parse(readFileSync(path, 'utf8'));
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
