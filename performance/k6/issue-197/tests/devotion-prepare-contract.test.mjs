import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function requiredPath(relativePath) {
	const target = path.join(ISSUE_DIR, relativePath);
	assert.equal(fs.existsSync(target), true, `required devotion preparation file is missing: ${relativePath}`);
	return target;
}

async function preparationModule() {
	return import(`${pathToFileURL(requiredPath('lib/devotion-prepare.mjs')).href}?test=${Date.now()}-${Math.random()}`);
}

function seoulToday() {
	return new Intl.DateTimeFormat('en-CA', {
		timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
	}).format(new Date());
}

function runtimeInput() {
	return {
		adminEmail: 'service-admin@example.test',
		adminPassword: 'admin-secret-value',
		fixtureUserPassword: 'fixture-secret-value',
		penaltyAccount: {
			accountType: 'PENALTY', nickname: 'Issue 197 penalty', bankName: 'Fixture Bank',
			accountNumber: '197-0000', accountHolder: 'Issue 197', ownerUserId: null,
		},
		penaltyRules: [
			{ ruleType: 'QUIET_TIME', calculationType: 'MISSING_COUNT', requiredCount: 7, baseAmount: 0, amountPerUnit: 100 },
			{ ruleType: 'PRAYER', calculationType: 'MISSING_COUNT', requiredCount: 7, baseAmount: 0, amountPerUnit: 200 },
			{ ruleType: 'BIBLE_READING', calculationType: 'MISSING_COUNT', requiredCount: 7, baseAmount: 0, amountPerUnit: 300 },
			{ ruleType: 'SATURDAY_LATE', calculationType: 'LATE_MINUTE', requiredCount: 0, baseAmount: 400, amountPerUnit: 10 },
		],
	};
}

function jwt(userId, exp) {
	const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
	return `${encode({ alg: 'none' })}.${encode({ sub: String(userId), userId, tokenType: 'ACCESS', exp })}.signature`;
}

test('fresh preparation replaces impossible pre-run DB-wide attribution with supporting evidence and conditional classification', () => {
	const runner = fs.readFileSync(requiredPath('run-devotion-baseline.sh'), 'utf8');
	const scenario = fs.readFileSync(requiredPath('lib/scenario-contract.mjs'), 'utf8');
	const dbWindow = fs.readFileSync(requiredPath('lib/validate-db-window.mjs'), 'utf8');

	assert.doesNotMatch(runner, /ATTRIBUTION_SIGNATURE_FILE|validate-activity-attribution|freeze-signature/);
	assert.match(runner, /db-window-evidence\.json/);
	assert.match(scenario, /conditional-not-adoptable/);
	assert.match(scenario, /automaticAdoption:\s*false/);
	assert.doesNotMatch(scenario, /activityAttributionEvidence|baseline-measured/);
	assert.match(dbWindow, /BigInt\(databaseDelta\.tup_inserted\)\s*<\s*9000n/);
	assert.match(dbWindow, /weekly_devotion_records.*1000/s);
	assert.match(dbWindow, /devotion_daily_checks.*7000/s);
	assert.match(dbWindow, /charge_items.*1000/s);
	assert.match(dbWindow, /xact_rollback/);
	assert.match(dbWindow, /externalActiveSessions/);
	assert.match(dbWindow, /autoanalyze_count|autovacuum_count/);
});

test('prepare wrapper is fail-closed, read-only before reservation, and never performs load or cleanup', () => {
	const runner = fs.readFileSync(requiredPath('run-devotion-prepare.sh'), 'utf8');
	const namespaceSql = fs.readFileSync(requiredPath('preflight-devotion-namespace.sql'), 'utf8');

	for (const name of [
		'DATASET_ID', 'FIXTURE_RUN_ID', 'PREPARE_REPORT_ROOT', 'RUNTIME_SECRET_ROOT', 'PREPARE_INPUT_FILE',
		'APP_CONTAINER', 'DB_CONTAINER', 'REDIS_CONTAINER', 'APP_SOURCE_WORKTREE', 'EXPECTED_COMPOSE_PROJECT',
		'EXPECTED_APP_COMPOSE_SERVICE', 'EXPECTED_DB_COMPOSE_SERVICE', 'EXPECTED_REDIS_COMPOSE_SERVICE',
		'EXPECTED_APP_REVISION', 'EXPECTED_APP_IMAGE_ID', 'EXPECTED_APP_JAR_SHA256', 'EXPECTED_API_CONTRACT_SHA256',
		'EXPECTED_DB_IMAGE_ID', 'EXPECTED_REDIS_IMAGE_ID', 'EXPECTED_FLYWAY_VERSION', 'EXPECTED_FLYWAY_SCRIPT',
		'EXPECTED_FLYWAY_CHECKSUM', 'DB_HOST', 'REDIS_HOST', 'EXPECTED_DB_PORT', 'EXPECTED_REDIS_PORT',
		'DB_NAME', 'DB_USER', 'BASE_URL', 'REJECTION_EVIDENCE_FILE',
	]) assert.match(runner, new RegExp(name));
	assert.doesNotMatch(runner, /\$\{(?:BASE_URL|DATASET_ID|FIXTURE_RUN_ID|APP_CONTAINER|DB_CONTAINER|REDIS_CONTAINER):-/);
	assert.match(runner, /faithlog-performance-\$\{[^}]*compose[^}]*\}\.lock/i);
	assert.match(runner, /SPRING_TASK_SCHEDULING_ENABLED=false/);
	assert.match(runner, /runtime-identity\.sql/);
	assert.match(runner, /preflight-devotion-namespace\.sql/);
	assert.match(runner, /-X\s+-qAt\s+-v\s+ON_ERROR_STOP=1/);
	assert.ok(runner.indexOf('preflight-devotion-namespace.sql') < runner.indexOf('reserve'));
	assert.doesNotMatch(runner, /docker\s+compose\s+(up|down|build)|docker\s+(run|rm)|prune|k6\s+run/);
	assert.doesNotMatch(runner, /\b(rm|rmdir)\b/);
	assert.doesNotMatch(namespaceSql, /\b(INSERT|UPDATE|DELETE|TRUNCATE|ALTER|DROP|CREATE)\b/i);
});

test('namespace evidence must be empty before exclusive report and secret reservation', async () => {
	const { reservePreparation } = await preparationModule();
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-prepare-reserve-'));
	const reportRoot = path.join(temporaryDirectory, 'reports');
	const secretRoot = path.join(temporaryDirectory, 'secrets');
	try {
		assert.throws(() => reservePreparation({
			reportRoot, secretRoot, fixtureRunId: 'ISSUE197_20990101_DEVOTION_BEFORE_B',
			namespaceEvidence: { existingCampusCount: 1, existingUserCount: 0 },
		}), /fresh|empty|existing/i);
		assert.equal(fs.existsSync(reportRoot), false);
		assert.equal(fs.existsSync(secretRoot), false);

		const reservation = reservePreparation({
			reportRoot, secretRoot, fixtureRunId: 'ISSUE197_20990101_DEVOTION_BEFORE_B',
			namespaceEvidence: { existingCampusCount: 0, existingUserCount: 0 },
		});
		assert.equal(fs.statSync(reservation.reportDirectory).mode & 0o077, 0);
		assert.equal(fs.statSync(reservation.secretDirectory).mode & 0o077, 0);
		assert.throws(() => reservePreparation({
			reportRoot, secretRoot, fixtureRunId: 'ISSUE197_20990101_DEVOTION_BEFORE_B',
			namespaceEvidence: { existingCampusCount: 0, existingUserCount: 0 },
		}), /exists|reserved/i);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('fixture blueprint fixes one warmup, 1000 measured, one rollback and four approved-rule math', async () => {
	const { buildFixtureBlueprint } = await preparationModule();
	const blueprint = buildFixtureBlueprint({
		datasetId: 'PERFORMANCE_1000_20990101_DEVOTION_197_B',
		fixtureRunId: 'ISSUE197_20990101_DEVOTION_BEFORE_B',
		referenceDate: '2099-01-01',
		penaltyRules: runtimeInput().penaltyRules,
	});
	assert.deepEqual(blueprint.cohortSizes, { warmup: 1, measured: 1000, rollback: 1 });
	assert.equal(blueprint.users.length, 1002);
	assert.equal(new Set(blueprint.users.map(({ email }) => email)).size, 1002);
	assert.deepEqual(
		[blueprint.rollbackWeekStartDate, blueprint.warmupWeekStartDate, blueprint.measuredWeekStartDate],
		['2098-12-29', '2099-01-05', '2099-01-12'],
	);
	assert.equal(blueprint.expectedPenaltyAmount, 2250);
	assert.equal(blueprint.penaltyRules.length, 4);
	assert.notEqual(blueprint.successCampusName, blueprint.rollbackCampusName);
	assert.match(blueprint.emailPrefix, /^p197\.[a-f0-9]{24}\./);
	assert.doesNotMatch(JSON.stringify(blueprint), /admin-secret-value|fixture-secret-value/);
});

test('fake full prepare uses one create-only path and writes exact manifest/credentials without leaking secrets', async () => {
	const { buildFixtureBlueprint, reservePreparation, prepareDevotionFixture } = await preparationModule();
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-prepare-full-'));
	const input = runtimeInput();
	const fixtureRunId = 'ISSUE197_20990101_DEVOTION_BEFORE_B';
	const blueprint = buildFixtureBlueprint({
		datasetId: 'PERFORMANCE_1000_20990101_DEVOTION_197_B', fixtureRunId,
		referenceDate: seoulToday(), penaltyRules: input.penaltyRules,
	});
	const reservation = reservePreparation({
		reportRoot: path.join(temporaryDirectory, 'reports'), secretRoot: path.join(temporaryDirectory, 'secrets'), fixtureRunId,
		namespaceEvidence: { existingCampusCount: 0, existingUserCount: 0 },
	});
	const calls = [];
	let nextUserId = 100;
	const userIdByEmail = new Map();
	const tokenExpiry = Math.floor(Date.now() / 1000) + 7200;
	const request = async (call) => {
		calls.push(structuredClone(call));
		if (call.path === '/api/v1/auth/login') {
			const isAdmin = call.body.email === input.adminEmail;
			const userId = isAdmin ? 1 : userIdByEmail.get(call.body.email);
			return { status: 200, body: { success: true, data: {
				user: { id: userId, role: isAdmin ? 'ADMIN' : 'USER', isActive: true },
				accessToken: isAdmin ? 'admin-access-token-secret' : jwt(userId, tokenExpiry),
				refreshToken: 'refresh-token-secret', tokenType: 'Bearer',
			} } };
		}
		if (call.path === '/api/v1/auth/signup') {
			const id = nextUserId++;
			userIdByEmail.set(call.body.email, id);
			return { status: 201, body: { success: true, data: { id, role: 'USER', isActive: true } } };
		}
		if (call.path === '/api/v1/campuses') {
			const campusId = call.body.name.includes('ROLLBACK') ? 902 : 901;
			return { status: 201, body: { success: true, data: { campusId } } };
		}
		return { status: 201, body: { success: true, data: { id: calls.length } } };
	};
	try {
		const result = await prepareDevotionFixture({ blueprint, input, request, ...reservation });
		assert.equal(calls.length, 3014);
		assert.equal(calls.filter(({ path: requestPath }) => requestPath === '/api/v1/auth/signup').length, 1002);
		assert.equal(calls.filter(({ path: requestPath }) => /\/api\/v1\/admin\/campuses\/\d+\/members/.test(requestPath)).length, 1002);
		assert.equal(calls.filter(({ path: requestPath }) => requestPath === '/api/v1/auth/login').length, 1003);
		assert.equal(calls.filter(({ path: requestPath }) => requestPath.includes('/penalty-rules')).length, 4);
		assert.equal(result.manifest.measuredUserIds.length, 1000);
		assert.equal(result.credentials.tokens.length, 1002);
		assert.equal(result.manifest.expectedPenaltyAmount, 2250);
		assert.equal(result.receipt.status, 'prepared');
		assert.equal(result.receipt.automaticAdoption, false);
		assert.equal(result.receipt.reuseAllowed, false);
		assert.equal(result.receipt.cleanupAllowed, false);
		assert.equal(fs.statSync(result.credentialsPath).mode & 0o077, 0);
		const reportText = fs.readdirSync(reservation.reportDirectory)
			.map((name) => fs.readFileSync(path.join(reservation.reportDirectory, name), 'utf8')).join('\n');
		for (const secret of [input.adminPassword, input.fixtureUserPassword, 'admin-access-token-secret', 'refresh-token-secret']) {
			assert.doesNotMatch(reportText, new RegExp(secret));
		}
		assert.doesNotMatch(reportText, /accessToken|refreshToken|Authorization/i);
		assert.doesNotMatch(JSON.stringify(process.argv), /admin-secret-value|fixture-secret-value|access-token-secret/);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('partial preparation preserves first failure receipt and never cleans or permits reuse', async () => {
	const { buildFixtureBlueprint, reservePreparation, prepareDevotionFixture } = await preparationModule();
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-prepare-partial-'));
	const input = runtimeInput();
	const fixtureRunId = 'ISSUE197_20990101_DEVOTION_BEFORE_C';
	const blueprint = buildFixtureBlueprint({
		datasetId: 'PERFORMANCE_1000_20990101_DEVOTION_197_C', fixtureRunId,
		referenceDate: seoulToday(), penaltyRules: input.penaltyRules,
	});
	const reservation = reservePreparation({
		reportRoot: path.join(temporaryDirectory, 'reports'), secretRoot: path.join(temporaryDirectory, 'secrets'), fixtureRunId,
		namespaceEvidence: { existingCampusCount: 0, existingUserCount: 0 },
	});
	let calls = 0;
	try {
		await assert.rejects(() => prepareDevotionFixture({
			blueprint, input, ...reservation,
			request: async () => {
				calls += 1;
				if (calls === 1) return { status: 200, body: { success: true, data: { user: { id: 1, role: 'ADMIN', isActive: true }, accessToken: 'admin-token', refreshToken: 'refresh' } } };
				if (calls === 2) return { status: 201, body: { success: true, data: { campusId: 901 } } };
				return { status: 409, body: { success: false, code: 'CAMPUS_ALREADY_EXISTS' } };
			},
		}), /rollback campus|CAMPUS_ALREADY_EXISTS/i);
		const receiptPath = path.join(reservation.reportDirectory, 'preparation-receipt.json');
		const receipt = JSON.parse(fs.readFileSync(receiptPath, 'utf8'));
		assert.equal(receipt.status, 'partial-failure');
		assert.equal(receipt.firstFailure.stage, 'create-rollback-campus');
		assert.equal(receipt.automaticAdoption, false);
		assert.equal(receipt.cleanupAllowed, false);
		assert.equal(receipt.reuseAllowed, false);
		assert.equal(fs.existsSync(reservation.reportDirectory), true);
		assert.equal(fs.existsSync(reservation.secretDirectory), true);
		assert.equal(fs.existsSync(path.join(reservation.secretDirectory, 'devotion-credentials.json')), false);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('preparation inspector binds manifest, 1002 JWTs, preflight, workload, and conditional-only handoff', async () => {
	const { inspectPreparation } = await preparationModule();
	const now = Math.floor(Date.now() / 1000);
	const manifest = {
		scenarioType: 'devotion-write', datasetId: 'PERFORMANCE_1000_20990101_DEVOTION_197_D',
		fixtureRunId: 'ISSUE197_20990101_DEVOTION_BEFORE_D', referenceDate: seoulToday(), campusId: 901, rollbackCampusId: 902,
		warmupWeekStartDate: nextMonday(seoulToday(), 1), measuredWeekStartDate: nextMonday(seoulToday(), 8),
		rollbackWeekStartDate: previousMonday(seoulToday()), expectedMeasuredUserCount: 1000, expectedPenaltyAmount: 2250,
		warmupUserIds: [100], measuredUserIds: Array.from({ length: 1000 }, (_, index) => index + 101), rollbackUserIds: [1101],
	};
	const credentials = {
		fixtureRunId: manifest.fixtureRunId,
		tokens: [...manifest.warmupUserIds, ...manifest.measuredUserIds, ...manifest.rollbackUserIds]
			.map((userId) => ({ userId, accessToken: jwt(userId, now + 7200) })),
	};
	const preflight = {
		expectedFixtureUsers: 1002, activeFixtureUsers: 1002, activeSuccessCampus: 1, activeRollbackCampus: 1,
		successMemberships: 1001, rollbackMemberships: 1, successUsersInRollbackCampus: 0, rollbackUsersInSuccessCampus: 0,
		successActivePenaltyAccounts: 1, rollbackActivePenaltyAccounts: 0, activePenaltyRuleCount: 4,
		calculatedPenaltyAmount: 2250, existingWeeklyCount: 0, existingDailyCount: 0, existingDevotionCharges: 0,
	};
	const evidence = inspectPreparation({
		manifest, credentials, preflight,
		receipt: { status: 'prepared', fixtureRunId: manifest.fixtureRunId, automaticAdoption: false, reuseAllowed: false, cleanupAllowed: false },
		workload: {
			warmupVus: 1, measuredVus: 30, rollbackVus: 1, warmupMaxDuration: '1m', measuredMaxDuration: '10m',
			rollbackMaxDuration: '1m', tokenTtlSafetySeconds: 60,
		},
		nowEpochSeconds: now,
	});
	assert.equal(evidence.status, 'ready-for-conditional-before');
	assert.equal(evidence.automaticAdoption, false);
	assert.equal(evidence.credentialCount, 1002);
	assert.equal(evidence.expectedBusinessRows.measured.weekly, 1000);
	assert.equal(evidence.expectedBusinessRows.measured.daily, 7000);
	assert.equal(evidence.expectedBusinessRows.measured.charge, 1000);
	assert.equal(evidence.expectedBusinessRows.rollback.persisted, 0);
	assert.equal('activitySignature' in evidence, false);
});

function dateAtUtc(date) {
	return new Date(`${date}T00:00:00Z`);
}

function formatDate(date) {
	return date.toISOString().slice(0, 10);
}

function nextMonday(referenceDate, additionalDays) {
	const date = dateAtUtc(referenceDate);
	date.setUTCDate(date.getUTCDate() + additionalDays);
	while (date.getUTCDay() !== 1) date.setUTCDate(date.getUTCDate() + 1);
	return formatDate(date);
}

function previousMonday(referenceDate) {
	const date = dateAtUtc(referenceDate);
	date.setUTCDate(date.getUTCDate() - 1);
	while (date.getUTCDay() !== 1) date.setUTCDate(date.getUTCDate() - 1);
	return formatDate(date);
}
