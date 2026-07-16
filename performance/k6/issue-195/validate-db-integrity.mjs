import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [
	beforePath,
	afterPath,
	measuredSummaryPath,
	controlAdoptionPath,
	outputPath,
	expectedEvidenceCase,
	expectedControlDuration,
	maximumControlGapSecondsInput,
] = process.argv.slice(2);
if (!beforePath || !afterPath || !measuredSummaryPath || !controlAdoptionPath
	|| !expectedEvidenceCase || !expectedControlDuration || !maximumControlGapSecondsInput) {
	throw new Error('beforePath, afterPath, measuredSummaryPath, controlAdoptionPath, expectedEvidenceCase, expectedControlDuration, and maximumControlGapSeconds are required.');
}

const before = readJson(beforePath);
const after = readJson(afterPath);
const measured = readJson(measuredSummaryPath);
const control = readJson(controlAdoptionPath);
const failures = [];
const maximumControlGapSeconds = parsePositiveInteger(maximumControlGapSecondsInput, 'maximumControlGapSeconds');
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
validateControlAdoption(control);
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
if (isObject(control) && isObject(before)) {
	if (control.evidenceCase !== expectedEvidenceCase) failures.push('control evidence case does not match runner evidence case');
	if (control.configuredDuration !== expectedControlDuration) failures.push('control duration does not match runner measured duration');
	const controlCompletedAt = parseTimestamp(control.afterCaptureCompletedAt, 'control.afterCaptureCompletedAt');
	const measuredBeforeCapturedAt = parseTimestamp(before.snapshotCapturedAt, 'before.snapshotCapturedAt');
	if (controlCompletedAt !== null && measuredBeforeCapturedAt !== null) {
		if (controlCompletedAt > measuredBeforeCapturedAt) {
			failures.push('control completed after the measured before snapshot');
		} else if (measuredBeforeCapturedAt - controlCompletedAt > maximumControlGapSeconds * 1_000) {
			failures.push('control evidence is not immediately bound to the measured before snapshot');
		}
	}
}

const commitDeltaValue = safeBigIntDelta(after?.databaseStats?.xactCommit, before?.databaseStats?.xactCommit, 'database commit');
const rollbackDeltaValue = safeBigIntDelta(after?.databaseStats?.xactRollback, before?.databaseStats?.xactRollback, 'database rollback');
// Current source has one authentication repository transaction and one endpoint
// service transaction per request. pg_stat_database is nevertheless a cumulative,
// database-wide snapshot whose cross-backend publication and source attribution
// cannot prove that both transactions are visible for every completed request.
// Keep the source boundary explicit, but gate only the conservative transaction
// lower bound that this evidence can prove without a tolerance or subtraction.
const minimumApplicationTransactionsPerRequest = 1;
const sourceTransactionBoundaryPerRequest = 2;
const requestCount = isPositiveSafeInteger(measured?.requestCount) ? measured.requestCount : null;
const minimumCommitDeltaValue = requestCount === null
	? null
	: BigInt(requestCount) * BigInt(minimumApplicationTransactionsPerRequest) + 1n;
const sourceRequiredCommitDeltaValue = requestCount === null
	? null
	: BigInt(requestCount) * BigInt(sourceTransactionBoundaryPerRequest) + 1n;
if (minimumCommitDeltaValue !== null && commitDeltaValue !== null && commitDeltaValue < minimumCommitDeltaValue) {
	failures.push(`database commit delta ${commitDeltaValue} is below measured requests + observer ${minimumCommitDeltaValue}`);
}
if (rollbackDeltaValue !== null && rollbackDeltaValue !== 0n) failures.push(`database rollback delta ${rollbackDeltaValue} != 0`);

const commitDelta = serializeBigInt(commitDeltaValue);
const rollbackDelta = serializeBigInt(rollbackDeltaValue);
const minimumCommitDelta = serializeBigInt(minimumCommitDeltaValue);
const sourceRequiredCommitDelta = serializeBigInt(sourceRequiredCommitDeltaValue);
// Preserve the previous output fields as source-boundary diagnostics. They are
// not the acceptance gate and do not make the DB-wide excess attributable.
const expectedCommitDelta = sourceRequiredCommitDelta;
const unattributedCommitDeltaValue = commitDeltaValue !== null && sourceRequiredCommitDeltaValue !== null
	&& commitDeltaValue >= sourceRequiredCommitDeltaValue
	? commitDeltaValue - sourceRequiredCommitDeltaValue
	: null;
const unattributedCommitDelta = serializeBigInt(unattributedCommitDeltaValue);
const sourceUnattributedCommitDeltaValue = commitDeltaValue !== null && minimumCommitDeltaValue !== null
	&& commitDeltaValue >= minimumCommitDeltaValue
	? commitDeltaValue - minimumCommitDeltaValue
	: null;
const sourceUnattributedCommitDelta = serializeBigInt(sourceUnattributedCommitDeltaValue);

if (failures.length > 0) {
	writeResult({
		status: 'non-adoptable',
		automaticAdoption: false,
		failures,
		commitDelta,
		rollbackDelta,
		minimumCommitDelta,
		sourceRequiredCommitDelta,
		expectedCommitDelta,
		unattributedCommitDelta,
		sourceUnattributedCommitDelta,
	});
	throw new Error(`DB integrity is non-adoptable: ${failures.join('; ')}`);
}
writeResult({
	status: sourceUnattributedCommitDeltaValue > 0n ? 'conditional-not-adoptable' : 'supporting-only',
	automaticAdoption: false,
	evidenceUse: 'supporting-only',
	transactionAttribution: 'database-wide-unattributed',
	minimumApplicationTransactionsPerRequest,
	sourceTransactionBoundaryPerRequest,
	sourceCommitCoverage: 'not-proven-by-database-wide-snapshot',
	observerCommitOverhead: 1,
	commitDelta,
	rollbackDelta,
	minimumCommitDelta,
	sourceRequiredCommitDelta,
	expectedCommitDelta,
	unattributedCommitDelta,
	sourceUnattributedCommitDelta,
	controlBackgroundCommitDelta: control.backgroundCommitDelta,
	backgroundSubtractionApplied: false,
});

function validateSnapshot(snapshot, label, expectedPhase) {
	if (!isObject(snapshot)) {
		failures.push(`${label} snapshot must be an object`);
		return;
	}
	requireNonEmptyString(snapshot.database, `${label}.database`);
	parseTimestamp(snapshot.snapshotCapturedAt, `${label}.snapshotCapturedAt`);
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

function validateControlAdoption(value) {
	if (!isObject(value)) {
		failures.push('control adoption must be an object');
		return;
	}
	requireExactKeys(value, [
		'schemaVersion', 'status', 'automaticAdoption', 'evidenceUse', 'evidenceCase',
		'configuredDuration', 'beforeCaptureStartedAt', 'beforeCapturedAt',
		'beforeCaptureCompletedAt', 'afterCaptureStartedAt', 'afterCapturedAt',
		'afterCaptureCompletedAt', 'observedElapsedMilliseconds',
		'beforeCaptureOverheadMilliseconds', 'afterCaptureOverheadMilliseconds',
		'controlCommitDelta', 'observerCommitOverhead', 'backgroundCommitDelta',
		'rollbackDelta', 'backgroundSubtractionApplied',
	], 'control adoption');
	if (value.schemaVersion !== 1) failures.push('control adoption.schemaVersion must be 1');
	if (value.status !== 'supporting-only') failures.push('control adoption status must be supporting-only');
	if (value.automaticAdoption !== false) failures.push('control adoption automaticAdoption must be false');
	if (value.evidenceUse !== 'supporting-only') failures.push('control adoption evidenceUse must be supporting-only');
	requireNonEmptyString(value.evidenceCase, 'control adoption.evidenceCase');
	requireNonEmptyString(value.configuredDuration, 'control adoption.configuredDuration');
	const timestamps = {};
	for (const key of [
		'beforeCaptureStartedAt', 'beforeCapturedAt', 'beforeCaptureCompletedAt',
		'afterCaptureStartedAt', 'afterCapturedAt', 'afterCaptureCompletedAt',
	]) timestamps[key] = parseTimestamp(value[key], `control adoption.${key}`);
	for (const key of [
		'observedElapsedMilliseconds', 'beforeCaptureOverheadMilliseconds', 'afterCaptureOverheadMilliseconds',
	]) requireNonNegativeFiniteNumber(value[key], `control adoption.${key}`);
	for (const key of ['controlCommitDelta', 'backgroundCommitDelta', 'rollbackDelta']) {
		requireCanonicalNonNegativeIntegerString(value[key], `control adoption.${key}`);
	}
	if (value.observerCommitOverhead !== 1) failures.push('control adoption observerCommitOverhead must be 1');
	if (value.rollbackDelta !== '0') failures.push('control adoption rollbackDelta must be 0');
	if (value.backgroundSubtractionApplied !== false) failures.push('control adoption must not subtract background commits');
	validateControlTimestampArithmetic(value, timestamps);
	if (isCanonicalNonNegativeIntegerString(value.controlCommitDelta)
		&& isCanonicalNonNegativeIntegerString(value.backgroundCommitDelta)
		&& value.observerCommitOverhead === 1
		&& BigInt(value.controlCommitDelta) !== BigInt(value.backgroundCommitDelta) + 1n) {
		failures.push('control adoption commit arithmetic is inconsistent');
	}
}

function validateControlTimestampArithmetic(value, timestamps) {
	const before = [
		timestamps.beforeCaptureStartedAt,
		timestamps.beforeCapturedAt,
		timestamps.beforeCaptureCompletedAt,
	];
	const after = [
		timestamps.afterCaptureStartedAt,
		timestamps.afterCapturedAt,
		timestamps.afterCaptureCompletedAt,
	];
	if (before.every((entry) => entry !== null)
		&& !(before[0] <= before[1] && before[1] <= before[2])) {
		failures.push('control adoption before timestamps are not ordered');
	}
	if (after.every((entry) => entry !== null)
		&& !(after[0] <= after[1] && after[1] <= after[2])) {
		failures.push('control adoption after timestamps are not ordered');
	}
	if (before[2] !== null && after[0] !== null) {
		const elapsed = after[0] - before[2];
		if (elapsed < 0 || value.observedElapsedMilliseconds !== elapsed) {
			failures.push('control adoption elapsed time is inconsistent');
		}
	}
	if (before[0] !== null && before[2] !== null
		&& value.beforeCaptureOverheadMilliseconds !== before[2] - before[0]) {
		failures.push('control adoption before capture overhead is inconsistent');
	}
	if (after[0] !== null && after[2] !== null
		&& value.afterCaptureOverheadMilliseconds !== after[2] - after[0]) {
		failures.push('control adoption after capture overhead is inconsistent');
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

function parsePositiveInteger(value, label) {
	if (!/^[1-9]\d*$/.test(value || '') || !Number.isSafeInteger(Number(value))) {
		throw new Error(`${label} must be a positive safe integer.`);
	}
	return Number(value);
}

function parseTimestamp(value, label) {
	if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) {
		failures.push(`${label} must be an ISO timestamp`);
		return null;
	}
	return Date.parse(value);
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
