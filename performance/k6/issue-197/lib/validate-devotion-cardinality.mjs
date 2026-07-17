import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJson, validateDevotionManifest } from './fixture-contract.mjs';

const ROOT_KEYS = ['datasetId', 'fixtureRunId', 'measured', 'rollback', 'successCampusDevotionChargeCount', 'warmup'].sort();
const WARMUP_KEYS = ['chargeCount', 'submittedCount', 'weeklyCount'].sort();
const MEASURED_KEYS = [
	'chargeAmountSum', 'chargeCount', 'correctChargeAmountCount', 'correctChargeBindingCount',
	'correctDailyDateCount', 'dailyCount', 'distinctChargeSourceCount', 'distinctChargeUsers',
	'distinctDailyUsers', 'distinctWeeklyUsers', 'duplicateChargeSourceGroups', 'expectedUserCount',
	'submittedCount', 'usersWithSevenDaily', 'weeklyCount',
].sort();
const ROLLBACK_KEYS = ['chargeCount', 'dailyCount', 'weeklyCount'].sort();

export function validateDevotionCardinality(manifestInput, counts, phase) {
	const manifest = validateDevotionManifest(manifestInput);
	assert.ok(['measured', 'final'].includes(phase), 'devotion cardinality phase must be measured or final');
	assertExactKeys(counts, ROOT_KEYS, 'devotion cardinality');
	assertExactKeys(counts.warmup, WARMUP_KEYS, 'devotion cardinality warmup');
	assertExactKeys(counts.measured, MEASURED_KEYS, 'devotion cardinality measured');
	assertExactKeys(counts.rollback, ROLLBACK_KEYS, 'devotion cardinality rollback');
	for (const [section, fields] of [[counts.warmup, WARMUP_KEYS], [counts.measured, MEASURED_KEYS], [counts.rollback, ROLLBACK_KEYS]]) {
		for (const field of fields) nonNegativeSafeInteger(section[field], `devotion cardinality ${field}`);
	}
	nonNegativeSafeInteger(counts.successCampusDevotionChargeCount, 'devotion cardinality successCampusDevotionChargeCount');
	assert.equal(counts.datasetId, manifest.datasetId, 'devotion cardinality datasetId must match the fixture manifest');
	assert.equal(counts.fixtureRunId, manifest.fixtureRunId, 'devotion cardinality fixtureRunId must match the fixture manifest');
	const warmupUsers = manifest.warmupUserIds.length;
	const measuredUsers = manifest.measuredUserIds.length;
	const expectedDailyRows = measuredUsers * 7;
	const expectedChargeAmountSum = measuredUsers * manifest.expectedPenaltyAmount;
	const expected = {
		warmup: { weeklyCount: warmupUsers, submittedCount: warmupUsers, chargeCount: warmupUsers },
		measured: {
			expectedUserCount: measuredUsers,
			weeklyCount: measuredUsers,
			distinctWeeklyUsers: measuredUsers,
			submittedCount: measuredUsers,
			dailyCount: expectedDailyRows,
			distinctDailyUsers: measuredUsers,
			usersWithSevenDaily: measuredUsers,
			correctDailyDateCount: expectedDailyRows,
			chargeCount: measuredUsers,
			distinctChargeUsers: measuredUsers,
			correctChargeAmountCount: measuredUsers,
			distinctChargeSourceCount: measuredUsers,
			correctChargeBindingCount: measuredUsers,
			chargeAmountSum: expectedChargeAmountSum,
			duplicateChargeSourceGroups: 0,
		},
		rollback: { weeklyCount: 0, dailyCount: 0, chargeCount: 0 },
		successCampusDevotionChargeCount: warmupUsers + measuredUsers,
	};
	for (const [section, fields] of [['warmup', WARMUP_KEYS], ['measured', MEASURED_KEYS], ['rollback', ROLLBACK_KEYS]]) {
		for (const field of fields) {
			assert.equal(counts[section][field], expected[section][field],
				`${phase} devotion cardinality ${section}.${field} must match the fixture-owned exact count`);
		}
	}
	assert.equal(counts.successCampusDevotionChargeCount, expected.successCampusDevotionChargeCount,
		`${phase} devotion cardinality success campus charge count must equal warmup plus measured charges`);
	return {
		status: 'exact', exact: true, automaticAdoption: false, phase,
		datasetId: manifest.datasetId, fixtureRunId: manifest.fixtureRunId,
		counts,
	};
}

function assertExactKeys(value, keys, label) {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${label} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), keys, `${label} must have exact keys`);
}

function nonNegativeSafeInteger(value, label) {
	assert.ok(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative safe integer`);
}

function writeSecureJson(filePath, value) {
	fs.mkdirSync(path.dirname(filePath), { recursive: true });
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}

function main() {
	const [manifestPath, countsPath, phase, outputPath] = process.argv.slice(2);
	const evidence = validateDevotionCardinality(
		validateDevotionManifest(readJson(manifestPath, 'devotion manifest')),
		readJson(countsPath, 'devotion direct cardinality'),
		phase,
	);
	writeSecureJson(outputPath, evidence);
	process.stdout.write(`${JSON.stringify(evidence)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	try {
		main();
	} catch (error) {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	}
}
