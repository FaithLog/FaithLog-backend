import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEVOTION_COUNT_KEYS = [
	'notificationLogs',
	'pollResponseOptions',
	'pollResponses',
	'pollComments',
	'pollOptions',
	'polls',
	'softDeletedPollComments',
	'prayerSubmissions',
	'devotionDailyChecks',
	'weeklyDevotionRecords',
	'chargeItems',
];
const DEVOTION_MANIFEST_KEYS = [
	'scenarioType',
	'datasetId',
	'fixtureRunId',
	'referenceDate',
	'campusId',
	'rollbackCampusId',
	'warmupWeekStartDate',
	'measuredWeekStartDate',
	'rollbackWeekStartDate',
	'expectedMeasuredUserCount',
	'expectedPenaltyAmount',
	'warmupUserIds',
	'measuredUserIds',
	'rollbackUserIds',
];
const RETENTION_MANIFEST_KEYS = [
	'scenarioType',
	'datasetId',
	'fixtureRunId',
	'datasetPrefix',
	'referenceInstant',
	'expectedDeleteCounts',
];

export function readJson(filePath, label) {
	if (!filePath) {
		throw new Error(`${label} path is required.`);
	}
	return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

export function validateDevotionManifest(manifest) {
	assertExactKeys(manifest, DEVOTION_MANIFEST_KEYS, 'devotion manifest');
	requireExact(manifest.scenarioType, 'devotion-write', 'scenarioType');
	requirePattern(manifest.datasetId, /^PERFORMANCE_/, 'datasetId');
	requirePattern(manifest.fixtureRunId, /^ISSUE197_[A-Z0-9_-]+$/, 'fixtureRunId');
	requirePositiveInteger(manifest.campusId, 'campusId');
	requirePositiveInteger(manifest.rollbackCampusId, 'rollbackCampusId');
	if (manifest.campusId === manifest.rollbackCampusId) {
		throw new Error('rollbackCampusId must be isolated from the successful devotion campus.');
	}
	requireExact(manifest.expectedMeasuredUserCount, 1000, 'expectedMeasuredUserCount');
	requirePositiveInteger(manifest.expectedPenaltyAmount, 'expectedPenaltyAmount');

	const warmupUserIds = validateUserIds(manifest.warmupUserIds, 'warmupUserIds', 1);
	const measuredUserIds = validateUserIds(manifest.measuredUserIds, 'measuredUserIds', 1000, 1000);
	const rollbackUserIds = validateUserIds(manifest.rollbackUserIds, 'rollbackUserIds', 1);
	assertDisjoint(warmupUserIds, measuredUserIds, rollbackUserIds);

	const referenceDate = parseDate(manifest.referenceDate, 'referenceDate');
	if (manifest.referenceDate !== seoulToday()) {
		throw new Error('referenceDate must equal the current Asia/Seoul date.');
	}
	const warmupWeek = parseMonday(manifest.warmupWeekStartDate, 'warmupWeekStartDate');
	const measuredWeek = parseMonday(manifest.measuredWeekStartDate, 'measuredWeekStartDate');
	const rollbackWeek = parseMonday(manifest.rollbackWeekStartDate, 'rollbackWeekStartDate');
	if (warmupWeek <= referenceDate || measuredWeek <= referenceDate) {
		throw new Error('warmup and measured weeks must be fixtureRunId-dedicated future weeks.');
	}
	if (rollbackWeek >= referenceDate) {
		throw new Error('rollback week must be a fixtureRunId-dedicated past week.');
	}
	if (warmupWeek.getTime() === measuredWeek.getTime()) {
		throw new Error('warmup and measured weeks must be different.');
	}

	return manifest;
}

export function validateRuntimeCredentials(credentialsPath, manifest) {
	const resolved = path.resolve(credentialsPath);
	const issueBuildSegment = `${path.sep}build${path.sep}reports${path.sep}k6${path.sep}issue-197${path.sep}`;
	const allowed = resolved.includes(issueBuildSegment) || resolved.startsWith(`${os.tmpdir()}${path.sep}`);
	if (!allowed) {
		throw new Error('CREDENTIALS_FILE must be runtime-only under build/reports/k6/issue-197 or the OS temp directory.');
	}
	if ((fs.statSync(resolved).mode & 0o077) !== 0) {
		throw new Error('CREDENTIALS_FILE permissions must be owner-only (mode 600).');
	}
	const credentials = readJson(resolved, 'CREDENTIALS_FILE');
	requireExact(credentials.fixtureRunId, manifest.fixtureRunId, 'credential fixtureRunId');
	if (!Array.isArray(credentials.tokens)) {
		throw new Error('CREDENTIALS_FILE tokens must be an array.');
	}
	const tokenByUserId = new Map();
	for (const entry of credentials.tokens) {
		requirePositiveInteger(entry.userId, 'credential userId');
		if (typeof entry.accessToken !== 'string' || entry.accessToken.length < 20) {
			throw new Error(`runtime accessToken is missing for userId=${entry.userId}.`);
		}
		if (tokenByUserId.has(entry.userId)) {
			throw new Error(`duplicate runtime credential userId=${entry.userId}.`);
		}
		tokenByUserId.set(entry.userId, entry.accessToken);
	}
	const requiredUserIds = [
		...manifest.warmupUserIds,
		...manifest.measuredUserIds,
		...manifest.rollbackUserIds,
	];
	for (const userId of requiredUserIds) {
		if (!tokenByUserId.has(userId)) {
			throw new Error(`CREDENTIALS_FILE has no accessToken for required userId=${userId}.`);
		}
	}
	return credentials;
}

export function validateRetentionManifest(manifest) {
	assertExactKeys(manifest, RETENTION_MANIFEST_KEYS, 'retention manifest');
	requireExact(manifest.scenarioType, 'retention-dry-verify-only', 'scenarioType');
	requirePattern(manifest.datasetId, /^PERFORMANCE_/, 'datasetId');
	requirePattern(manifest.fixtureRunId, /^ISSUE197_[A-Z0-9_-]+$/, 'fixtureRunId');
	requirePattern(manifest.datasetPrefix, /^PERFORMANCE_.*ISSUE197_/, 'datasetPrefix');
	if (!manifest.datasetPrefix.includes(manifest.datasetId) || !manifest.datasetPrefix.includes(manifest.fixtureRunId)) {
		throw new Error('datasetPrefix must contain the separate datasetId and fixtureRunId values.');
	}
	if (
		typeof manifest.referenceInstant !== 'string'
		|| !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(manifest.referenceInstant)
	) {
		throw new Error('referenceInstant must be an RFC 3339 instant with an explicit offset.');
	}
	const referenceInstant = new Date(manifest.referenceInstant);
	if (Number.isNaN(referenceInstant.getTime())) {
		throw new Error('referenceInstant must be an ISO-8601 instant.');
	}
	const seoulParts = new Intl.DateTimeFormat('en-CA', {
		timeZone: 'Asia/Seoul',
		month: '2-digit',
		day: '2-digit',
	}).formatToParts(referenceInstant);
	const seoulMonth = seoulParts.find((part) => part.type === 'month')?.value;
	const seoulDay = seoulParts.find((part) => part.type === 'day')?.value;
	if (seoulMonth !== '02' || seoulDay !== '01') {
		throw new Error('referenceInstant must fall on February 1 in Asia/Seoul for combined daily/annual evidence.');
	}
	if (!manifest.expectedDeleteCounts || typeof manifest.expectedDeleteCounts !== 'object') {
		throw new Error('expectedDeleteCounts is required.');
	}
	assertExactKeys(manifest.expectedDeleteCounts, DEVOTION_COUNT_KEYS, 'expectedDeleteCounts');
	for (const key of DEVOTION_COUNT_KEYS) {
		const value = manifest.expectedDeleteCounts[key];
		if (!Number.isInteger(value) || value < 0) {
			throw new Error(`expectedDeleteCounts.${key} must be a non-negative integer.`);
		}
	}
	if (DEVOTION_COUNT_KEYS.every((key) => manifest.expectedDeleteCounts[key] === 0)) {
		throw new Error('expectedDeleteCounts must include at least one retention candidate.');
	}
	return manifest;
}

export function cohortIds(manifest, cohort) {
	const field = `${cohort}UserIds`;
	if (!['warmup', 'measured', 'rollback'].includes(cohort)) {
		throw new Error(`unknown cohort: ${cohort}`);
	}
	return validateUserIds(manifest[field], field, 1).join(',');
}

function validateUserIds(value, label, minItems, maxItems = Number.POSITIVE_INFINITY) {
	if (!Array.isArray(value) || value.length < minItems || value.length > maxItems) {
		throw new Error(`${label} must contain between ${minItems} and ${maxItems} users.`);
	}
	value.forEach((userId) => requirePositiveInteger(userId, label));
	if (new Set(value).size !== value.length) {
		throw new Error(`${label} must not contain duplicates.`);
	}
	return value;
}

function assertDisjoint(...cohorts) {
	const seen = new Set();
	for (const cohort of cohorts) {
		for (const userId of cohort) {
			if (seen.has(userId)) {
				throw new Error(`fixture cohorts must be disjoint; duplicate userId=${userId}.`);
			}
			seen.add(userId);
		}
	}
}

function assertExactKeys(value, allowedKeys, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) {
		throw new Error(`${label} must be an object.`);
	}
	const actual = Object.keys(value).sort();
	const expected = [...allowedKeys].sort();
	if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
		throw new Error(`${label} has unexpected or missing fields.`);
	}
}

function parseDate(value, label) {
	if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
		throw new Error(`${label} must use YYYY-MM-DD.`);
	}
	const date = new Date(`${value}T00:00:00Z`);
	if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
		throw new Error(`${label} must be a real date.`);
	}
	return date;
}

function seoulToday() {
	return new Intl.DateTimeFormat('en-CA', {
		timeZone: 'Asia/Seoul',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
	}).format(new Date());
}

function parseMonday(value, label) {
	const date = parseDate(value, label);
	if (date.getUTCDay() !== 1) {
		throw new Error(`${label} must be Monday.`);
	}
	return date;
}

function requirePattern(value, pattern, label) {
	if (typeof value !== 'string' || !pattern.test(value)) {
		throw new Error(`${label} does not match ${pattern}.`);
	}
}

function requirePositiveInteger(value, label) {
	if (!Number.isInteger(value) || value < 1) {
		throw new Error(`${label} must be a positive integer.`);
	}
}

function requireExact(actual, expected, label) {
	if (actual !== expected) {
		throw new Error(`${label} must equal ${expected}.`);
	}
}

async function main() {
	const [command, manifestPath, argument] = process.argv.slice(2);
	const manifest = readJson(manifestPath, 'manifest');
	if (command === 'validate-devotion') {
		validateDevotionManifest(manifest);
		validateRuntimeCredentials(argument || process.env.CREDENTIALS_FILE, manifest);
		process.stdout.write(`${JSON.stringify({ datasetId: manifest.datasetId, fixtureRunId: manifest.fixtureRunId })}\n`);
		return;
	}
	if (command === 'validate-retention') {
		validateRetentionManifest(manifest);
		process.stdout.write(`${JSON.stringify({ datasetId: manifest.datasetId, fixtureRunId: manifest.fixtureRunId })}\n`);
		return;
	}
	if (command === 'field') {
		if (!Object.hasOwn(manifest, argument)) {
			throw new Error(`manifest field is missing: ${argument}`);
		}
		process.stdout.write(`${manifest[argument]}\n`);
		return;
	}
	if (command === 'json-field') {
		if (!Object.hasOwn(manifest, argument)) {
			throw new Error(`JSON field is missing: ${argument}`);
		}
		process.stdout.write(`${manifest[argument]}\n`);
		return;
	}
	if (command === 'cohort-ids') {
		validateDevotionManifest(manifest);
		process.stdout.write(`${cohortIds(manifest, argument)}\n`);
		return;
	}
	throw new Error(`unsupported command: ${command}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
