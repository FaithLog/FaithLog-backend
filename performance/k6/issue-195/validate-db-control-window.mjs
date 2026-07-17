import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [beforePath, afterPath, expectedEvidenceCase, configuredDuration, outputPath] = process.argv.slice(2);
if (!beforePath || !afterPath || !expectedEvidenceCase || !configuredDuration || !outputPath) {
	throw new Error('beforePath, afterPath, expectedEvidenceCase, configuredDuration, and outputPath are required.');
}

const failures = [];
const before = readJson(beforePath, 'before');
const after = readJson(afterPath, 'after');
const configuredDurationMilliseconds = parseDuration(configuredDuration) * 1_000;
const requiredPlannerKeys = [
	'effective_cache_size', 'enable_hashjoin', 'enable_indexscan', 'enable_mergejoin',
	'enable_nestloop', 'plan_cache_mode', 'random_page_cost', 'seq_page_cost', 'work_mem',
];
const requiredTables = ['campus_duty_assignments', 'campus_members', 'campuses', 'users'];
const maintenanceCountKeys = ['analyzeCount', 'autoanalyzeCount', 'vacuumCount', 'autovacuumCount'];
const maintenanceTimestampKeys = ['lastAnalyze', 'lastAutoanalyze', 'lastVacuum', 'lastAutovacuum'];
const snapshotKeys = [
	'schemaVersion', 'phase', 'captureStartedAt', 'snapshotCapturedAt', 'captureCompletedAt',
	'database', 'observerApplicationName', 'externalActiveSessions', 'databaseStats',
	'plannerSettings', 'tableMaintenance', 'observerOverhead',
];

validateSnapshot(before, 'before');
validateSnapshot(after, 'after');
if (!/^[a-z0-9_]+-[a-z0-9_]+$/.test(expectedEvidenceCase)) failures.push('expected evidence case is invalid');
if (before && after) {
	if (before.database !== after.database) failures.push('control database changed');
	if (!isDeepStrictEqual(before.plannerSettings, after.plannerSettings)) failures.push('control planner settings changed');
	if (!isDeepStrictEqual(before.tableMaintenance, after.tableMaintenance)) failures.push('control maintenance evidence changed');
	if (before.externalActiveSessions !== 0 || after.externalActiveSessions !== 0) {
		failures.push('control external active session observed at a boundary');
	}
}

const beforeCaptureStartedAt = timestamp(before?.captureStartedAt, 'before.captureStartedAt');
const beforeSnapshotCapturedAt = timestamp(before?.snapshotCapturedAt, 'before.snapshotCapturedAt');
const beforeCaptureCompletedAt = timestamp(before?.captureCompletedAt, 'before.captureCompletedAt');
const afterCaptureStartedAt = timestamp(after?.captureStartedAt, 'after.captureStartedAt');
const afterSnapshotCapturedAt = timestamp(after?.snapshotCapturedAt, 'after.snapshotCapturedAt');
const afterCaptureCompletedAt = timestamp(after?.captureCompletedAt, 'after.captureCompletedAt');
requireOrdered(
	[beforeCaptureStartedAt, beforeSnapshotCapturedAt, beforeCaptureCompletedAt],
	'before capture timestamps are not ordered',
);
requireOrdered(
	[afterCaptureStartedAt, afterSnapshotCapturedAt, afterCaptureCompletedAt],
	'after capture timestamps are not ordered',
);
if (beforeCaptureCompletedAt !== null && afterCaptureStartedAt !== null
	&& afterCaptureStartedAt - beforeCaptureCompletedAt < configuredDurationMilliseconds) {
	failures.push('observed idle control window is shorter than configured duration');
}

const controlCommitDeltaValue = delta(
	after?.databaseStats?.xactCommit,
	before?.databaseStats?.xactCommit,
	'control commit',
);
const rollbackDeltaValue = delta(
	after?.databaseStats?.xactRollback,
	before?.databaseStats?.xactRollback,
	'control rollback',
);
const observerCommitOverhead = 1n;
if (controlCommitDeltaValue !== null && controlCommitDeltaValue < observerCommitOverhead) {
	failures.push('control commit delta does not include the exact before observer commit');
}
if (rollbackDeltaValue !== null && rollbackDeltaValue !== 0n) failures.push('control rollback delta must be zero');

if (failures.length > 0) {
	write({
		schemaVersion: 1,
		status: 'non-adoptable',
		automaticAdoption: false,
		failures,
	});
	throw new Error(`DB idle control is non-adoptable: ${failures.join('; ')}`);
}

write({
	schemaVersion: 1,
	status: 'supporting-only',
	automaticAdoption: false,
	evidenceUse: 'supporting-only',
	evidenceCase: expectedEvidenceCase,
	configuredDuration,
	beforeCaptureStartedAt: before.captureStartedAt,
	beforeCapturedAt: before.snapshotCapturedAt,
	beforeCaptureCompletedAt: before.captureCompletedAt,
	afterCaptureStartedAt: after.captureStartedAt,
	afterCapturedAt: after.snapshotCapturedAt,
	afterCaptureCompletedAt: after.captureCompletedAt,
	observedElapsedMilliseconds: afterCaptureStartedAt - beforeCaptureCompletedAt,
	beforeCaptureOverheadMilliseconds: beforeCaptureCompletedAt - beforeCaptureStartedAt,
	afterCaptureOverheadMilliseconds: afterCaptureCompletedAt - afterCaptureStartedAt,
	controlCommitDelta: controlCommitDeltaValue.toString(),
	observerCommitOverhead: Number(observerCommitOverhead),
	backgroundCommitDelta: (controlCommitDeltaValue - observerCommitOverhead).toString(),
	rollbackDelta: rollbackDeltaValue.toString(),
	backgroundSubtractionApplied: false,
});

function validateSnapshot(value, expectedPhase) {
	if (!isObject(value)) {
		failures.push(`${expectedPhase} control snapshot must be an object`);
		return;
	}
	requireExactKeys(value, snapshotKeys, `${expectedPhase} control snapshot`);
	if (value.schemaVersion !== 1) failures.push(`${expectedPhase}.schemaVersion must be 1`);
	if (value.phase !== expectedPhase) failures.push(`${expectedPhase}.phase must be ${expectedPhase}`);
	requireNonEmptyString(value.database, `${expectedPhase}.database`);
	const expectedObserver = `faithlog-issue195-control-${expectedEvidenceCase}-${expectedPhase}`;
	if (value.observerApplicationName !== expectedObserver) failures.push(`${expectedPhase} observer identity mismatch`);
	requireNonNegativeFiniteNumber(value.externalActiveSessions, `${expectedPhase}.externalActiveSessions`);
	if (!isObject(value.databaseStats)) {
		failures.push(`${expectedPhase}.databaseStats must be an object`);
	} else {
		requireExactKeys(value.databaseStats, ['xactCommit', 'xactRollback'], `${expectedPhase}.databaseStats`);
		for (const key of ['xactCommit', 'xactRollback']) canonicalInteger(value.databaseStats[key], `${expectedPhase}.${key}`);
	}
	if (!isObject(value.plannerSettings)) {
		failures.push(`${expectedPhase}.plannerSettings must be an object`);
	} else {
		requireExactKeys(value.plannerSettings, requiredPlannerKeys, `${expectedPhase}.plannerSettings`);
		for (const key of requiredPlannerKeys) requireNonEmptyString(value.plannerSettings[key], `${expectedPhase}.plannerSettings.${key}`);
	}
	if (!isObject(value.tableMaintenance)) {
		failures.push(`${expectedPhase}.tableMaintenance must be an object`);
	} else {
		requireExactKeys(value.tableMaintenance, requiredTables, `${expectedPhase}.tableMaintenance`);
		for (const table of requiredTables) validateMaintenance(value.tableMaintenance[table], `${expectedPhase}.${table}`);
	}
	if (!isObject(value.observerOverhead)) {
		failures.push(`${expectedPhase}.observerOverhead must be an object`);
	} else {
		requireExactKeys(value.observerOverhead, ['beforeSnapshotCommitIncludedInDelta', 'expectedCommitCount'], `${expectedPhase}.observerOverhead`);
		if (value.observerOverhead.beforeSnapshotCommitIncludedInDelta !== true
			|| value.observerOverhead.expectedCommitCount !== 1) {
			failures.push(`${expectedPhase}.observerOverhead must declare one included before commit`);
		}
	}
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
			failures.push(`${label}.${key} must be null or an ISO timestamp`);
		}
	}
}

function parseDuration(value) {
	const units = { h: 3600, m: 60, s: 1 };
	let seconds = 0;
	let consumed = '';
	for (const match of String(value || '').matchAll(/(\d+)(h|m|s)/g)) {
		seconds += Number(match[1]) * units[match[2]];
		consumed += match[0];
	}
	if (consumed !== value || seconds <= 0) throw new Error('configuredDuration must use positive k6 h/m/s syntax.');
	return seconds;
}

function delta(afterValue, beforeValue, label) {
	if (!canonicalInteger(afterValue, `${label}.after`) || !canonicalInteger(beforeValue, `${label}.before`)) return null;
	const afterBigInt = BigInt(afterValue);
	const beforeBigInt = BigInt(beforeValue);
	if (afterBigInt < beforeBigInt) {
		failures.push(`${label} counter regressed`);
		return null;
	}
	return afterBigInt - beforeBigInt;
}

function timestamp(value, label) {
	if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) {
		failures.push(`${label} must be an ISO timestamp`);
		return null;
	}
	return Date.parse(value);
}

function requireOrdered(values, message) {
	if (values.every((value) => value !== null) && !(values[0] <= values[1] && values[1] <= values[2])) failures.push(message);
}

function canonicalInteger(value, label) {
	const valid = typeof value === 'string' && /^(0|[1-9]\d*)$/.test(value);
	if (!valid) failures.push(`${label} must be a canonical non-negative decimal string`);
	return valid;
}

function requireExactKeys(value, expected, label) {
	if (!isDeepStrictEqual(Object.keys(value).sort(), [...expected].sort())) failures.push(`${label} keys are incomplete or unexpected`);
}

function requireNonEmptyString(value, label) {
	if (typeof value !== 'string' || value.length === 0) failures.push(`${label} must be a non-empty string`);
}

function requireNonNegativeFiniteNumber(value, label) {
	if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) failures.push(`${label} must be a finite non-negative number`);
}

function readJson(filePath, label) {
	try {
		return JSON.parse(fs.readFileSync(filePath, 'utf8'));
	} catch {
		failures.push(`${label} control snapshot is missing or malformed`);
		return null;
	}
}

function isObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function write(value) {
	fs.writeFileSync(outputPath, `${JSON.stringify(value, null, 2)}\n`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
}
