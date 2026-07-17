import exec from 'k6/execution';
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = String(__ENV.BASE_URL ?? '').replace(/\/$/, '');
const FIXTURE_MANIFEST = JSON.parse(open(__ENV.FIXTURE_MANIFEST));
const RUNTIME_CREDENTIALS = JSON.parse(open(__ENV.CREDENTIALS_FILE));
const PHASE = String(__ENV.PHASE ?? '');
const VUS = Number(__ENV.VUS);
const MAX_DURATION = String(__ENV.MAX_DURATION ?? '');

const warmupUserIds = FIXTURE_MANIFEST.warmupUserIds || [];
const measuredUserIds = FIXTURE_MANIFEST.measuredUserIds || [];
const rollbackUserIds = FIXTURE_MANIFEST.rollbackUserIds || [];
const expectedMeasuredUserCount = FIXTURE_MANIFEST.expectedMeasuredUserCount;
const fixtureRunId = FIXTURE_MANIFEST.fixtureRunId;
const datasetId = FIXTURE_MANIFEST.datasetId;

const phaseDurations = {
	warmup: new Trend('devotion_weekly_warmup', true),
	measured: new Trend('devotion_weekly_measured', true),
	rollback: new Trend('devotion_weekly_rollback', true),
};
const phaseFailures = {
	warmup: new Rate('devotion_weekly_warmup_failure'),
	measured: new Rate('devotion_weekly_measured_failure'),
	rollback: new Rate('devotion_weekly_rollback_failure'),
};
const phaseTransactions = {
	warmup: new Counter('devotion_weekly_warmup_transactions'),
	measured: new Counter('devotion_weekly_measured_transactions'),
	rollback: new Counter('devotion_weekly_rollback_transactions'),
};

const credentialsByUserId = Object.create(null);
for (const credential of RUNTIME_CREDENTIALS.tokens || []) {
	credentialsByUserId[String(credential.userId)] = credential.accessToken;
}

const selectedPhase = phaseConfig(PHASE);
validateWorkloadInputs();

export const options = {
	scenarios: {
		[PHASE]: {
			executor: 'shared-iterations',
			exec: 'runSelectedPhase',
			vus: VUS,
			iterations: selectedPhase.userIds.length,
			maxDuration: MAX_DURATION,
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
	thresholds: {
		[`devotion_weekly_${PHASE}_failure`]: ['rate==0'],
	},
};

export function setup() {
	guardTarget();
	validateFixtureContract();
	return {
		datasetId,
		fixtureRunId,
		phase: PHASE,
		userCount: selectedPhase.userIds.length,
	};
}

export function runSelectedPhase() {
	const iterationIndex = Number(exec.scenario.iterationInTest);
	const userId = selectedPhase.userIds[iterationIndex];
	if (!userId) {
		fail(`No ${PHASE} user exists for iterationIndex=${iterationIndex}.`);
	}
	const accessToken = credentialsByUserId[String(userId)];
	if (!accessToken) {
		fail(`CREDENTIALS_FILE has no runtime accessToken for userId=${userId}.`);
	}

	const response = http.put(
		`${BASE_URL}/api/v1/campuses/${selectedPhase.campusId}/devotions/me/weeks/${selectedPhase.weekStartDate}`,
		JSON.stringify(weeklySubmitBody(selectedPhase.weekStartDate)),
		{
			headers: {
				Authorization: `Bearer ${accessToken}`,
				'Content-Type': 'application/json',
			},
			tags: {
				name: `devotion_weekly_${PHASE}`,
				phase: PHASE,
				dataset_id: datasetId,
				fixture_run_id: fixtureRunId,
			},
		}
	);
	phaseDurations[PHASE].add(response.timings.duration);
	phaseTransactions[PHASE].add(1);

	const success = PHASE === 'rollback'
		? checkRollbackResponse(response)
		: checkSuccessfulResponse(response, userId, selectedPhase.weekStartDate);
	phaseFailures[PHASE].add(!success);
}

function checkSuccessfulResponse(response, userId, weekStartDate) {
	const body = parseJson(response);
	const dailyChecks = body.data?.dailyChecks;
	const expectedDates = Array.from({ length: 7 }, (_, index) => plusDays(weekStartDate, index));
	return check(response, {
		[`${PHASE} status is 200`]: (res) => res.status === 200,
		[`${PHASE} success envelope`]: () => body.success === true,
		[`${PHASE} submitted user and week match`]: () => body.data?.userId === userId && body.data?.weekStartDate === weekStartDate,
		[`${PHASE} submittedAt exists`]: () => Boolean(body.data?.submittedAt),
		[`${PHASE} returns exactly seven daily rows`]: () => Array.isArray(dailyChecks) && dailyChecks.length === 7,
		[`${PHASE} daily dates are unique`]: () => Array.isArray(dailyChecks)
			&& new Set(dailyChecks.map((daily) => daily.recordDate)).size === 7,
		[`${PHASE} daily dates cover Monday through Sunday`]: () => Array.isArray(dailyChecks)
			&& expectedDates.every((date) => dailyChecks.some((daily) => daily.recordDate === date)),
	});
}

function checkRollbackResponse(response) {
	const body = parseJson(response);
	return check(response, {
		'rollback status is 400': (res) => res.status === 400,
		'rollback uses missing penalty account error': () => body.code === 'BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING',
		'rollback response is not successful': () => body.success === false,
	});
}

function weeklySubmitBody(weekStartDate) {
	return {
		dailyChecks: Array.from({ length: 7 }, (_, index) => ({
			recordDate: plusDays(weekStartDate, index),
			quietTimeChecked: index < 4,
			prayerChecked: index < 4,
			bibleReadingChecked: index < 4,
		})),
		saturdayLateMinutes: 5,
		submit: true,
	};
}

function phaseConfig(phase) {
	if (phase === 'warmup') {
		return {
			userIds: warmupUserIds,
			campusId: FIXTURE_MANIFEST.campusId,
			weekStartDate: FIXTURE_MANIFEST.warmupWeekStartDate,
		};
	}
	if (phase === 'measured') {
		return {
			userIds: measuredUserIds,
			campusId: FIXTURE_MANIFEST.campusId,
			weekStartDate: FIXTURE_MANIFEST.measuredWeekStartDate,
		};
	}
	if (phase === 'rollback') {
		return {
			userIds: rollbackUserIds,
			campusId: FIXTURE_MANIFEST.rollbackCampusId,
			weekStartDate: FIXTURE_MANIFEST.rollbackWeekStartDate,
		};
	}
	throw new Error('PHASE must be warmup, measured, or rollback.');
}

function validateWorkloadInputs() {
	if (!BASE_URL) {
		throw new Error('BASE_URL is required.');
	}
	if (!Number.isInteger(VUS) || VUS < 1 || VUS > selectedPhase.userIds.length) {
		throw new Error('VUS must be a positive integer no greater than the selected fixture cohort.');
	}
	if (!/^[1-9]\d*(?:ms|s|m|h)$/.test(MAX_DURATION)) {
		throw new Error('MAX_DURATION must be an explicit positive k6 duration.');
	}
}

function validateFixtureContract() {
	if (FIXTURE_MANIFEST.scenarioType !== 'devotion-write') {
		fail('fixture scenarioType must be devotion-write.');
	}
	if (!String(datasetId).startsWith('PERFORMANCE_')) {
		fail('datasetId must start with PERFORMANCE_.');
	}
	if (!String(fixtureRunId).startsWith('ISSUE197_')) {
		fail('fixtureRunId must start with ISSUE197_.');
	}
	if (expectedMeasuredUserCount !== 1000 || measuredUserIds.length !== 1000) {
		fail('Issue #197 measured cohort must contain exactly 1,000 users.');
	}
	if (FIXTURE_MANIFEST.referenceDate !== seoulToday()) {
		fail('referenceDate must equal the current Asia/Seoul date.');
	}
	if (
		FIXTURE_MANIFEST.warmupWeekStartDate <= FIXTURE_MANIFEST.referenceDate ||
		FIXTURE_MANIFEST.measuredWeekStartDate <= FIXTURE_MANIFEST.referenceDate ||
		FIXTURE_MANIFEST.rollbackWeekStartDate >= FIXTURE_MANIFEST.referenceDate
	) {
		fail('warmup/measured weeks must be future and rollback week must be past.');
	}
	const allUserIds = [...warmupUserIds, ...measuredUserIds, ...rollbackUserIds];
	if (new Set(allUserIds).size !== allUserIds.length) {
		fail('warmup, measured, and rollback users must be completely disjoint.');
	}
	if (RUNTIME_CREDENTIALS.fixtureRunId !== fixtureRunId) {
		fail('CREDENTIALS_FILE fixtureRunId does not match FIXTURE_MANIFEST.');
	}
}

function seoulToday() {
	return new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

function plusDays(dateText, days) {
	const date = new Date(`${dateText}T00:00:00Z`);
	date.setUTCDate(date.getUTCDate() + days);
	return date.toISOString().slice(0, 10);
}

function parseJson(response) {
	try {
		return response.json();
	} catch (error) {
		return {};
	}
}

function guardTarget() {
	const localTarget = /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(BASE_URL);
	if (!localTarget) {
		fail('Issue #197 devotion write is local-Docker-only; remote targets are blocked.');
	}
}
