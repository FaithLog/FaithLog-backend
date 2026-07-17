import assert from 'node:assert/strict';
import test from 'node:test';
import {
	cohortIds,
	validateDevotionManifest,
	validateRetentionManifest,
} from '../lib/fixture-contract.mjs';

function devotionManifest(overrides = {}) {
	const referenceDate = seoulToday();
	return {
		scenarioType: 'devotion-write',
		datasetId: 'PERFORMANCE_1000_20300101_A',
		fixtureRunId: 'ISSUE197_20300101_A',
		referenceDate,
		campusId: 10,
		rollbackCampusId: 11,
		warmupWeekStartDate: mondayRelativeTo(referenceDate, 1),
		measuredWeekStartDate: mondayRelativeTo(referenceDate, 2),
		rollbackWeekStartDate: mondayRelativeTo(referenceDate, -1),
		expectedMeasuredUserCount: 1000,
		expectedPenaltyAmount: 4300,
		warmupUserIds: [1],
		measuredUserIds: Array.from({ length: 1000 }, (_, index) => index + 1001),
		rollbackUserIds: [3001],
		...overrides,
	};
}

function seoulToday() {
	return new Intl.DateTimeFormat('en-CA', {
		timeZone: 'Asia/Seoul',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
	}).format(new Date());
}

function mondayRelativeTo(referenceDate, weekOffset) {
	const date = new Date(`${referenceDate}T00:00:00Z`);
	const daysSinceMonday = (date.getUTCDay() + 6) % 7;
	date.setUTCDate(date.getUTCDate() - daysSinceMonday + weekOffset * 7);
	return date.toISOString().slice(0, 10);
}

function plusDays(dateText, days) {
	const date = new Date(`${dateText}T00:00:00Z`);
	date.setUTCDate(date.getUTCDate() + days);
	return date.toISOString().slice(0, 10);
}

function retentionManifest(overrides = {}) {
	return {
		scenarioType: 'retention-dry-verify-only',
		datasetId: 'PERFORMANCE_1000_20300101_R',
		fixtureRunId: 'ISSUE197_20300101_R',
		datasetPrefix: 'PERFORMANCE_1000_20300101_R_ISSUE197_20300101_R',
		referenceInstant: '2027-01-31T15:00:00Z',
		expectedDeleteCounts: {
			notificationLogs: 10,
			pollResponseOptions: 20,
			pollResponses: 10,
			pollComments: 10,
			pollOptions: 5,
			polls: 2,
			softDeletedPollComments: 3,
			prayerSubmissions: 10,
			devotionDailyChecks: 70,
			weeklyDevotionRecords: 10,
			chargeItems: 10,
		},
		...overrides,
	};
}

test('valid devotion fixture locks exact 1,000 users and future/past Monday separation', () => {
	const manifest = devotionManifest();
	assert.equal(validateDevotionManifest(manifest), manifest);
	assert.equal(cohortIds(manifest, 'measured').split(',').length, 1000);
});

test('devotion fixture rejects cohort overlap and a shared rollback campus', () => {
	assert.throws(
		() => validateDevotionManifest(devotionManifest({ rollbackUserIds: [1001] })),
		/cohorts must be disjoint/
	);
	assert.throws(
		() => validateDevotionManifest(devotionManifest({ rollbackCampusId: 10 })),
		/rollbackCampusId must be isolated/
	);
	assert.throws(
		() => validateDevotionManifest(devotionManifest({ referenceDate: plusDays(seoulToday(), -1) })),
		/referenceDate must equal the current Asia\/Seoul date/
	);
	assert.throws(
		() => validateDevotionManifest(devotionManifest({ accessToken: 'must-not-live-in-manifest' })),
		/devotion manifest has unexpected or missing fields/
	);
});

test('retention fixture requires combined dataset prefix and February 1 Seoul evidence date', () => {
	const manifest = retentionManifest();
	assert.equal(validateRetentionManifest(manifest), manifest);
	assert.throws(
		() => validateRetentionManifest(retentionManifest({ datasetPrefix: 'PERFORMANCE_WITHOUT_RUN' })),
		/datasetPrefix/
	);
	assert.throws(
		() => validateRetentionManifest(retentionManifest({ referenceInstant: '2027-02-01T15:00:00Z' })),
		/February 1/
	);
	assert.throws(
		() => validateRetentionManifest(retentionManifest({ referenceInstant: '2027-02-01' })),
		/RFC 3339 instant with an explicit offset/
	);
	assert.throws(
		() => validateRetentionManifest(retentionManifest({ unexpected: true })),
		/retention manifest has unexpected or missing fields/
	);
	assert.throws(
		() => validateRetentionManifest(retentionManifest({
			expectedDeleteCounts: { ...retentionManifest().expectedDeleteCounts, unexpected: 1 },
		})),
		/expectedDeleteCounts has unexpected or missing fields/
	);
});
