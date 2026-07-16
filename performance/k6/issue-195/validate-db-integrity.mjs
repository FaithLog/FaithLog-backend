import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [beforePath, afterPath, measuredSummaryPath, outputPath, expectedEvidenceCase] = process.argv.slice(2);
if (!beforePath || !afterPath || !measuredSummaryPath || !expectedEvidenceCase) {
	throw new Error('beforePath, afterPath, measuredSummaryPath, and expectedEvidenceCase are required.');
}

const before = readJson(beforePath);
const after = readJson(afterPath);
const measured = readJson(measuredSummaryPath);
const failures = [];
const requiredPlannerKeys = [
	'effective_cache_size',
	'enable_hashjoin',
	'enable_indexscan',
	'enable_mergejoin',
	'enable_nestloop',
	'plan_cache_mode',
	'random_page_cost',
	'seq_page_cost',
	'work_mem',
];
const requiredTables = ['campus_duty_assignments', 'campus_members', 'campuses', 'users'];
const maintenanceCountKeys = ['analyzeCount', 'autoanalyzeCount', 'vacuumCount', 'autovacuumCount'];
const maintenanceTimestampKeys = ['lastAnalyze', 'lastAutoanalyze', 'lastVacuum', 'lastAutovacuum'];

validateSnapshot(before, 'before', 'before');
validateSnapshot(after, 'after', 'after');
validateMeasuredAdoption(measured);
if (!/^[a-z0-9_]+-[a-z0-9_]+$/.test(expectedEvidenceCase)) {
	failures.push('expected evidence case has an invalid format');
}

if (isObject(before) && isObject(after)) {
	if (before.database !== after.database) failures.push('database changed');
	const observerEvidenceCase = validateObserverPair(before.observerApplicationName, after.observerApplicationName);
	if (observerEvidenceCase && observerEvidenceCase !== expectedEvidenceCase) {
		failures.push('observer case identity does not match runner evidence case');
	}
	if (before.externalActiveSessions !== 0 || after.externalActiveSessions !== 0) {
		failures.push('external active session observed');
	}
	if (!isDeepStrictEqual(before.plannerSettings, after.plannerSettings)) failures.push('planner settings changed');
	if (!isDeepStrictEqual(before.tableMaintenance, after.tableMaintenance)) {
		failures.push('analyze/vacuum maintenance evidence changed');
	}
}
if (isObject(measured)) {
	const measuredEvidenceCase = `${measured.scenario}-${measured.case}`;
	if (measuredEvidenceCase !== expectedEvidenceCase) {
		failures.push('measured scenario/case does not match runner evidence case');
	}
	const expectedMetricName = `issue195_${sanitize(measured.scenario)}_${sanitize(measured.case)}`;
	if (measured.metricName !== expectedMetricName) failures.push('measured metricName does not match scenario/case');
}

const commitDeltaValue = safeBigIntDelta(after?.databaseStats?.xactCommit, before?.databaseStats?.xactCommit, 'database commit');
const rollbackDeltaValue = safeBigIntDelta(after?.databaseStats?.xactRollback, before?.databaseStats?.xactRollback, 'database rollback');
const expectedApplicationTransactionsPerRequest = 2;
const requestCount = isPositiveSafeInteger(measured?.requestCount) ? measured.requestCount : null;
const expectedCommitDeltaValue = requestCount === null
	? null
	: BigInt(requestCount) * BigInt(expectedApplicationTransactionsPerRequest) + 1n;
if (expectedCommitDeltaValue !== null && commitDeltaValue !== null && commitDeltaValue !== expectedCommitDeltaValue) {
	failures.push(`database commit delta ${commitDeltaValue} != (measured requests * ${expectedApplicationTransactionsPerRequest}) + observer ${expectedCommitDeltaValue}`);
}
if (rollbackDeltaValue !== null && rollbackDeltaValue !== 0n) failures.push(`database rollback delta ${rollbackDeltaValue} != 0`);

const commitDelta = serializeBigInt(commitDeltaValue);
const rollbackDelta = serializeBigInt(rollbackDeltaValue);
const expectedCommitDelta = serializeBigInt(expectedCommitDeltaValue);

if (failures.length > 0) {
	writeResult({ status: 'non-adoptable', failures, commitDelta, rollbackDelta, expectedCommitDelta });
	throw new Error(`DB integrity is non-adoptable: ${failures.join('; ')}`);
}
writeResult({
	status: 'adoptable',
	expectedApplicationTransactionsPerRequest,
	observerCommitOverhead: 1,
	commitDelta,
	rollbackDelta,
});

function validateSnapshot(snapshot, label, expectedPhase) {
	if (!isObject(snapshot)) {
		failures.push(`${label} snapshot must be an object`);
		return;
	}
	requireNonEmptyString(snapshot.database, `${label}.database`);
	validateObserverName(snapshot.observerApplicationName, label, expectedPhase);
	requireNonNegativeFiniteNumber(snapshot.externalActiveSessions, `${label}.externalActiveSessions`);
	if (!isObject(snapshot.databaseStats)) {
		failures.push(`${label}.databaseStats must be an object`);
	} else {
		requireExactKeys(snapshot.databaseStats, ['xactCommit', 'xactRollback'], `${label}.databaseStats`);
		for (const key of ['xactCommit', 'xactRollback']) {
			requireCanonicalNonNegativeIntegerString(snapshot.databaseStats[key], `${label}.databaseStats.${key}`);
		}
	}
	if (!isObject(snapshot.plannerSettings)) {
		failures.push(`${label}.plannerSettings must be an object`);
	} else {
		requireExactKeys(snapshot.plannerSettings, requiredPlannerKeys, `${label}.plannerSettings`);
		for (const key of requiredPlannerKeys) requireNonEmptyString(snapshot.plannerSettings[key], `${label}.plannerSettings.${key}`);
	}
	if (!isObject(snapshot.tableMaintenance)) {
		failures.push(`${label}.tableMaintenance must be an object`);
	} else {
		requireExactKeys(snapshot.tableMaintenance, requiredTables, `${label}.tableMaintenance`);
		for (const table of requiredTables) validateMaintenance(snapshot.tableMaintenance[table], `${label}.tableMaintenance.${table}`);
	}
	if (!isObject(snapshot.observerOverhead)) {
		failures.push(`${label}.observerOverhead must be an object`);
	} else {
		requireExactKeys(snapshot.observerOverhead, ['beforeSnapshotCommitIncludedInDelta', 'expectedCommitCount'], `${label}.observerOverhead`);
		if (snapshot.observerOverhead.beforeSnapshotCommitIncludedInDelta !== true) {
			failures.push(`${label}.observerOverhead.beforeSnapshotCommitIncludedInDelta must be true`);
		}
		if (snapshot.observerOverhead.expectedCommitCount !== 1) {
			failures.push(`${label}.observerOverhead.expectedCommitCount must be 1`);
		}
	}
}

function validateObserverName(value, label, expectedPhase) {
	if (!requireNonEmptyString(value, `${label}.observerApplicationName`)) return;
	const parsed = /^faithlog-issue195-observer-([a-z0-9_-]+)-(before|after)$/.exec(value);
	if (!parsed) {
		failures.push(`${label}.observerApplicationName has an invalid issue/case/phase identity`);
	} else if (parsed[2] !== expectedPhase) {
		failures.push(`${label}.observerApplicationName phase must be ${expectedPhase}`);
	}
}

function validateObserverPair(beforeName, afterName) {
	const beforeMatch = typeof beforeName === 'string'
		? /^faithlog-issue195-observer-([a-z0-9_-]+)-before$/.exec(beforeName)
		: null;
	const afterMatch = typeof afterName === 'string'
		? /^faithlog-issue195-observer-([a-z0-9_-]+)-after$/.exec(afterName)
		: null;
	if (!beforeMatch || !afterMatch) return null;
	if (beforeMatch[1] !== afterMatch[1]) {
		failures.push('observer case identity changed');
		return null;
	}
	return beforeMatch[1];
}

function validateMaintenance(value, label) {
	if (!isObject(value)) {
		failures.push(`${label} must be an object`);
		return;
	}
	requireExactKeys(value, [...maintenanceCountKeys, ...maintenanceTimestampKeys], label);
	for (const key of maintenanceCountKeys) requireNonNegativeFiniteNumber(value[key], `${label}.${key}`);
	for (const key of maintenanceTimestampKeys) {
		if (value[key] !== null && (typeof value[key] !== 'string' || !Number.isFinite(Date.parse(value[key])))) {
			failures.push(`${label}.${key} must be null or an ISO timestamp string`);
		}
	}
}

function validateMeasuredAdoption(value) {
	if (!isObject(value)) {
		failures.push('measured adoption must be an object');
		return;
	}
	requireExactKeys(value, [
		'status', 'phase', 'scenario', 'case', 'metricName', 'requestCount',
		'throughput', 'failureRate', 'latency',
	], 'measured adoption');
	if (value.status !== 'adoptable') failures.push('measured adoption status must be adoptable');
	if (value.phase !== 'measured') failures.push('measured adoption phase must be measured');
	for (const key of ['scenario', 'case', 'metricName']) requireNonEmptyString(value[key], `measured adoption.${key}`);
	if (!isPositiveSafeInteger(value.requestCount)) failures.push('measured request count must be a positive safe integer');
	if (!isPositiveFiniteNumber(value.throughput)) failures.push('measured throughput is invalid');
	if (typeof value.failureRate !== 'number' || !Number.isFinite(value.failureRate) || value.failureRate !== 0) {
		failures.push('measured failure rate must be numeric zero');
	}
	if (!isObject(value.latency)) {
		failures.push('measured adoption.latency must be an object');
	} else {
		requireExactKeys(value.latency, ['p50', 'p95', 'p99', 'max'], 'measured adoption.latency');
		for (const key of ['p50', 'p95', 'p99', 'max']) {
			if (!isNonNegativeFiniteNumber(value.latency[key])) failures.push(`measured adoption.latency.${key} is invalid`);
		}
		if (!(value.latency.p50 <= value.latency.p95
			&& value.latency.p95 <= value.latency.p99
			&& value.latency.p99 <= value.latency.max)) {
			failures.push('measured adoption latency percentiles are not ordered');
		}
	}
}

function requireExactKeys(value, expected, label) {
	const actual = Object.keys(value).sort();
	const required = [...expected].sort();
	if (!isDeepStrictEqual(actual, required)) failures.push(`${label} keys are incomplete or unexpected`);
}

function requireNonEmptyString(value, label) {
	if (typeof value !== 'string' || value.length === 0) {
		failures.push(`${label} must be a non-empty string`);
		return false;
	}
	return true;
}

function requireNonNegativeFiniteNumber(value, label) {
	if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) failures.push(`${label} must be a finite non-negative number`);
}

function requireCanonicalNonNegativeIntegerString(value, label) {
	if (!isCanonicalNonNegativeIntegerString(value)) failures.push(`${label} must be a canonical non-negative decimal string`);
}

function safeBigIntDelta(afterValue, beforeValue, label) {
	if (!isCanonicalNonNegativeIntegerString(afterValue) || !isCanonicalNonNegativeIntegerString(beforeValue)) return null;
	const afterBigInt = BigInt(afterValue);
	const beforeBigInt = BigInt(beforeValue);
	if (afterBigInt < beforeBigInt) {
		failures.push(`${label} counter regressed`);
		return null;
	}
	return afterBigInt - beforeBigInt;
}

function serializeBigInt(value) {
	return typeof value === 'bigint' ? value.toString() : null;
}

function isCanonicalNonNegativeIntegerString(value) {
	return typeof value === 'string' && /^(0|[1-9]\d*)$/.test(value);
}

function isNonNegativeFiniteNumber(value) {
	return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function isPositiveFiniteNumber(value) {
	return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

function isPositiveSafeInteger(value) {
	return Number.isSafeInteger(value) && value > 0;
}

function isObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function sanitize(value) {
	return String(value).replace(/[^a-zA-Z0-9_]/g, '_');
}

function readJson(filePath) {
	return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeResult(result) {
	if (outputPath) fs.writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`);
}
