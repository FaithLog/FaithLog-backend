import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateDevotionManifest } from './fixture-contract.mjs';
import { validateRuntimeContract } from './runtime-contract.mjs';
import { validatePreflight } from './validate-devotion-preflight.mjs';

const REQUIRED_RULE_TYPES = ['BIBLE_READING', 'PRAYER', 'QUIET_TIME', 'SATURDAY_LATE'];
const INPUT_KEYS = ['adminEmail', 'adminPassword', 'fixtureUserPassword', 'penaltyAccount', 'penaltyRules'];
const ACCOUNT_KEYS = ['accountType', 'nickname', 'bankName', 'accountNumber', 'accountHolder', 'ownerUserId'];
const RULE_KEYS = ['ruleType', 'calculationType', 'requiredCount', 'baseAmount', 'amountPerUnit'];

export function buildFixtureBlueprint({ datasetId, fixtureRunId, referenceDate, penaltyRules }) {
	if (typeof datasetId !== 'string' || !/^PERFORMANCE_[A-Z0-9_-]+$/.test(datasetId)) {
		throw new Error('DATASET_ID must use the PERFORMANCE_ namespace.');
	}
	if (typeof fixtureRunId !== 'string' || !/^ISSUE197_[A-Z0-9_-]+$/.test(fixtureRunId)) {
		throw new Error('FIXTURE_RUN_ID must use the ISSUE197_ namespace.');
	}
	const reference = parseDate(referenceDate, 'referenceDate');
	const rules = validatePenaltyRules(penaltyRules);
	const namespaceDigest = createHash('sha256').update(`${datasetId}\0${fixtureRunId}`).digest('hex').slice(0, 24);
	const emailPrefix = `p197.${namespaceDigest}.`;
	const users = [
		fixtureUser('warmup', 1, emailPrefix),
		...Array.from({ length: 1000 }, (_, index) => fixtureUser('measured', index + 1, emailPrefix)),
		fixtureUser('rollback', 1, emailPrefix),
	];
	const successCampusName = boundedName(`${datasetId} ${fixtureRunId} SUCCESS`);
	const rollbackCampusName = boundedName(`${datasetId} ${fixtureRunId} ROLLBACK`);
	return {
		datasetId,
		fixtureRunId,
		referenceDate,
		namespaceDigest,
		emailPrefix,
		successCampusName,
		rollbackCampusName,
		rollbackWeekStartDate: mondayBefore(reference),
		warmupWeekStartDate: mondayAfter(reference, 1),
		measuredWeekStartDate: mondayAfter(reference, 8),
		cohortSizes: { warmup: 1, measured: 1000, rollback: 1 },
		users,
		penaltyRules: rules,
		expectedPenaltyAmount: calculateExpectedPenaltyAmount(rules),
	};
}

export function validatePenaltyRules(penaltyRules) {
	if (!Array.isArray(penaltyRules) || penaltyRules.length !== 4) {
		throw new Error('penaltyRules must contain exactly four approved rules.');
	}
	const seen = new Set();
	for (const rule of penaltyRules) {
		exactKeys(rule, RULE_KEYS, 'penalty rule');
		if (!REQUIRED_RULE_TYPES.includes(rule.ruleType) || seen.has(rule.ruleType)) {
			throw new Error('penaltyRules must contain each approved rule type exactly once.');
		}
		seen.add(rule.ruleType);
		const expectedCalculation = rule.ruleType === 'SATURDAY_LATE' ? 'LATE_MINUTE' : 'MISSING_COUNT';
		if (rule.calculationType !== expectedCalculation) {
			throw new Error(`${rule.ruleType} must use ${expectedCalculation}.`);
		}
		for (const field of ['requiredCount', 'baseAmount', 'amountPerUnit']) {
			if (!Number.isSafeInteger(rule[field]) || rule[field] < 0) throw new Error(`penalty rule ${field} must be a non-negative safe integer.`);
		}
		if (rule.ruleType !== 'SATURDAY_LATE' && rule.requiredCount !== 7) {
			throw new Error(`${rule.ruleType} requiredCount must be exactly 7 for the scenario request.`);
		}
	}
	return structuredClone(penaltyRules);
}

export function calculateExpectedPenaltyAmount(penaltyRules) {
	let total = 0;
	for (const rule of validatePenaltyRules(penaltyRules)) {
		const amount = rule.ruleType === 'SATURDAY_LATE'
			? rule.baseAmount + (5 * rule.amountPerUnit)
			: Math.max(rule.requiredCount - 4, 0) * rule.amountPerUnit;
		total += amount;
		if (!Number.isSafeInteger(total) || total > 2_147_483_647) throw new Error('calculated expectedPenaltyAmount exceeds the supported integer range.');
	}
	if (total < 1) throw new Error('calculated expectedPenaltyAmount must be positive.');
	return total;
}

export function readPreparationInput(filePath) {
	if (typeof filePath !== 'string' || !path.isAbsolute(filePath)) throw new Error('PREPARE_INPUT_FILE must be an absolute path.');
	const resolved = fs.realpathSync(filePath);
	if (!isRuntimeSecretPath(resolved)) throw new Error('PREPARE_INPUT_FILE must be runtime-only under the OS temp directory.');
	const stat = fs.statSync(resolved);
	if (!stat.isFile()) throw new Error('PREPARE_INPUT_FILE must be a regular file.');
	if ((stat.mode & 0o077) !== 0) throw new Error('PREPARE_INPUT_FILE must be owner-only (mode 600).');
	const input = JSON.parse(fs.readFileSync(resolved, 'utf8'));
	return validateInputObject(input);
}

export function reservePreparation({ reportRoot, secretRoot, fixtureRunId, namespaceEvidence }) {
	validateNamespaceEvidence(namespaceEvidence);
	for (const [label, value] of [['reportRoot', reportRoot], ['secretRoot', secretRoot]]) {
		if (typeof value !== 'string' || !path.isAbsolute(value)) throw new Error(`${label} must be an absolute path.`);
	}
	if (!isIgnoredReportPath(path.resolve(reportRoot))) throw new Error('reportRoot must be under build/reports/k6/issue-197 or the OS temp directory.');
	if (!isRuntimeSecretPath(path.resolve(secretRoot))) throw new Error('secretRoot must be runtime-only under the OS temp directory.');
	if (typeof fixtureRunId !== 'string' || !/^ISSUE197_[A-Z0-9_-]+$/.test(fixtureRunId)) throw new Error('fixtureRunId is invalid.');
	const reportDirectory = path.join(reportRoot, fixtureRunId);
	const secretDirectory = path.join(secretRoot, fixtureRunId);
	if (fs.existsSync(reportDirectory) || fs.existsSync(secretDirectory)) throw new Error('fixture/report namespace is already reserved or exists.');
	fs.mkdirSync(reportRoot, { recursive: true, mode: 0o700 });
	fs.chmodSync(reportRoot, 0o700);
	fs.mkdirSync(secretRoot, { recursive: true, mode: 0o700 });
	fs.chmodSync(secretRoot, 0o700);
	fs.mkdirSync(reportDirectory, { mode: 0o700 });
	try {
		fs.mkdirSync(secretDirectory, { mode: 0o700 });
	} catch (error) {
		writeJson(path.join(reportDirectory, 'preparation-receipt.json'), {
			schemaVersion: 1, fixtureRunId, status: 'partial-failure', automaticAdoption: false,
			cleanupAllowed: false, reuseAllowed: false, firstFailure: { stage: 'reserve-secret-namespace', code: 'SECRET_NAMESPACE_RESERVATION_FAILED' },
		});
		throw error;
	}
	writeJsonExclusive(path.join(reportDirectory, 'namespace-reservation.json'), {
		schemaVersion: 1, fixtureRunId, status: 'reserved', namespaceEvidence,
		automaticAdoption: false, cleanupAllowed: false, reuseAllowed: false,
	});
	return { reportDirectory, secretDirectory };
}

export async function prepareDevotionFixture({
	blueprint, input, request, reportDirectory, secretDirectory,
	nowEpochSeconds, prepareMaxDurationSeconds, tokenTtlSafetySeconds,
}) {
	if (typeof request !== 'function') throw new Error('request adapter is required.');
	validatePreparationDirectories(reportDirectory, secretDirectory, blueprint.fixtureRunId);
	validateInputObject(input);
	validatePreparationWindow(prepareMaxDurationSeconds, nowEpochSeconds * 1000);
	if (!Number.isSafeInteger(tokenTtlSafetySeconds) || tokenTtlSafetySeconds < 1) {
		throw new Error('TOKEN_TTL_SAFETY_SECONDS must be a positive safe integer.');
	}
	if (calculateExpectedPenaltyAmount(input.penaltyRules) !== blueprint.expectedPenaltyAmount) {
		throw new Error('runtime penalty rules do not match the fixture blueprint.');
	}
	const deadlineEpochMilliseconds = (nowEpochSeconds + prepareMaxDurationSeconds) * 1000;
	const boundedRequest = deadlineBoundRequest(request, deadlineEpochMilliseconds);
	const receiptPath = path.join(reportDirectory, 'preparation-receipt.json');
	const counts = { httpRequests: 0, campusesCreated: 0, accountsCreated: 0, rulesCreated: 0, usersCreated: 0, membershipsCreated: 0, tokensCollected: 0 };
	let stage = 'admin-login';
	writeJson(receiptPath, receipt(blueprint, 'preparing', counts));
	try {
		const adminLogin = await checkedRequest(boundedRequest, counts, stage, {
			method: 'POST', path: '/api/v1/auth/login', body: { email: input.adminEmail, password: input.adminPassword },
		}, 200);
		const admin = adminLogin.data;
		if (admin?.user?.role !== 'ADMIN' || admin.user.isActive !== true || typeof admin.accessToken !== 'string' || admin.accessToken.length < 8) {
			throw safeError(stage, 200, 'ADMIN_IDENTITY_INVALID');
		}
		positiveId(admin.user.id, stage);
		validateAdminAccessToken(admin.accessToken, admin.user.id, nowEpochSeconds, prepareMaxDurationSeconds + tokenTtlSafetySeconds);
		const adminToken = admin.accessToken;

		stage = 'create-success-campus';
		const successCampus = await checkedRequest(boundedRequest, counts, stage, {
			method: 'POST', path: '/api/v1/campuses', accessToken: adminToken,
			body: { name: blueprint.successCampusName, region: 'Issue 197', description: blueprint.fixtureRunId },
		}, 201);
		const campusId = positiveId(successCampus.data?.campusId, stage);
		counts.campusesCreated += 1;

		stage = 'create-rollback-campus';
		const rollbackCampus = await checkedRequest(boundedRequest, counts, stage, {
			method: 'POST', path: '/api/v1/campuses', accessToken: adminToken,
			body: { name: blueprint.rollbackCampusName, region: 'Issue 197', description: blueprint.fixtureRunId },
		}, 201);
		const rollbackCampusId = positiveId(rollbackCampus.data?.campusId, stage);
		if (rollbackCampusId === campusId) throw safeError(stage, 201, 'CAMPUS_ISOLATION_INVALID');
		counts.campusesCreated += 1;

		stage = 'create-penalty-account';
		await checkedRequest(boundedRequest, counts, stage, {
			method: 'POST', path: `/api/v1/admin/campuses/${campusId}/payment-accounts`, accessToken: adminToken,
			body: input.penaltyAccount,
		}, 201);
		counts.accountsCreated += 1;

		for (const rule of blueprint.penaltyRules) {
			stage = `create-penalty-rule-${rule.ruleType.toLowerCase()}`;
			await checkedRequest(boundedRequest, counts, stage, {
				method: 'POST', path: `/api/v1/admin/campuses/${campusId}/penalty-rules`, accessToken: adminToken, body: rule,
			}, 201);
			counts.rulesCreated += 1;
		}

		const preparedUsers = [];
		for (const user of blueprint.users) {
			stage = `signup-${user.cohort}-${user.ordinal}`;
			const signup = await checkedRequest(boundedRequest, counts, stage, {
				method: 'POST', path: '/api/v1/auth/signup',
				body: { name: user.name, email: user.email, password: input.fixtureUserPassword },
			}, 201);
			const userId = positiveId(signup.data?.id, stage);
			if (signup.data?.role !== 'USER' || signup.data?.isActive !== true || signup.data?.email !== user.email || signup.data?.name !== user.name) {
				throw safeError(stage, 201, 'NEW_USER_IDENTITY_INVALID');
			}
			counts.usersCreated += 1;
			const targetCampusId = user.cohort === 'rollback' ? rollbackCampusId : campusId;
			stage = `membership-${user.cohort}-${user.ordinal}`;
			await checkedRequest(boundedRequest, counts, stage, {
				method: 'POST', path: `/api/v1/admin/campuses/${targetCampusId}/members`, accessToken: adminToken, body: { userId },
			}, 201);
			counts.membershipsCreated += 1;
			preparedUsers.push({ ...user, userId });
		}

		const tokens = [];
		for (const user of preparedUsers) {
			stage = `token-${user.cohort}-${user.ordinal}`;
			const login = await checkedRequest(boundedRequest, counts, stage, {
				method: 'POST', path: '/api/v1/auth/login', body: { email: user.email, password: input.fixtureUserPassword },
			}, 200);
			if (login.data?.user?.id !== user.userId || login.data?.user?.role !== 'USER' || login.data?.user?.isActive !== true) {
				throw safeError(stage, 200, 'FIXTURE_LOGIN_IDENTITY_INVALID');
			}
			if (typeof login.data?.accessToken !== 'string' || login.data.accessToken.length < 20) throw safeError(stage, 200, 'ACCESS_TOKEN_MISSING');
			tokens.push({ userId: user.userId, accessToken: login.data.accessToken });
			counts.tokensCollected += 1;
		}

		const byCohort = (cohort) => preparedUsers.filter((user) => user.cohort === cohort).map((user) => user.userId);
		const manifest = {
			scenarioType: 'devotion-write', datasetId: blueprint.datasetId, fixtureRunId: blueprint.fixtureRunId,
			referenceDate: blueprint.referenceDate, campusId, rollbackCampusId,
			warmupWeekStartDate: blueprint.warmupWeekStartDate, measuredWeekStartDate: blueprint.measuredWeekStartDate,
			rollbackWeekStartDate: blueprint.rollbackWeekStartDate, expectedMeasuredUserCount: 1000,
			expectedPenaltyAmount: blueprint.expectedPenaltyAmount,
			warmupUserIds: byCohort('warmup'), measuredUserIds: byCohort('measured'), rollbackUserIds: byCohort('rollback'),
		};
		validateDevotionManifest(manifest);
		const credentials = { fixtureRunId: blueprint.fixtureRunId, tokens };
		const manifestPath = path.join(reportDirectory, 'devotion-fixture.json');
		const credentialsPath = path.join(secretDirectory, 'devotion-credentials.json');
		writeJsonExclusive(manifestPath, manifest);
		writeJsonExclusive(credentialsPath, credentials);
		const finalReceipt = receipt(blueprint, 'prepared', counts, {
			manifestFile: path.basename(manifestPath), credentialCount: tokens.length,
			expectedPenaltyAmount: blueprint.expectedPenaltyAmount,
		});
		writeJson(receiptPath, finalReceipt);
		return { manifest, credentials, receipt: finalReceipt, manifestPath, credentialsPath };
	} catch (error) {
		const firstFailure = {
			stage: error.stage || stage,
			...(Number.isSafeInteger(error.httpStatus) ? { httpStatus: error.httpStatus } : {}),
			code: typeof error.safeCode === 'string' ? error.safeCode : 'PREPARATION_FAILED',
		};
		writeJson(receiptPath, receipt(blueprint, 'partial-failure', counts, { firstFailure }));
		throw new Error(`${humanStage(firstFailure.stage)} failed (${firstFailure.code}).`);
	}
}

export function validatePreparationWindow(prepareMaxDurationSeconds, nowEpochMilliseconds = Date.now()) {
	if (!Number.isSafeInteger(prepareMaxDurationSeconds) || prepareMaxDurationSeconds < 1) {
		throw new Error('PREPARE_MAX_DURATION_SECONDS must be a positive safe integer.');
	}
	if (!Number.isFinite(nowEpochMilliseconds)) throw new Error('preparation clock is invalid.');
	const startDate = seoulDate(new Date(nowEpochMilliseconds));
	const deadlineDate = seoulDate(new Date(nowEpochMilliseconds + (prepareMaxDurationSeconds * 1000)));
	if (startDate !== deadlineDate) throw new Error('PREPARE_MAX_DURATION_SECONDS crosses the Asia/Seoul execution date boundary.');
	return { startDate, deadlineDate, prepareMaxDurationSeconds };
}

export function inspectPreparation({ manifest, credentials, preflight, receipt: preparationReceipt, workload, nowEpochSeconds }) {
	validateDevotionManifest(manifest);
	if (
		preparationReceipt?.status !== 'prepared'
		|| preparationReceipt.fixtureRunId !== manifest.fixtureRunId
		|| preparationReceipt.automaticAdoption !== false
		|| preparationReceipt.cleanupAllowed !== false
		|| preparationReceipt.reuseAllowed !== false
	) throw new Error('preparation receipt is not a complete non-reusable conditional fixture.');
	validatePreflight(manifest, preflight);
	const runtime = validateRuntimeContract(manifest, credentials, workload, nowEpochSeconds);
	return {
		schemaVersion: 1,
		status: 'ready-for-conditional-before',
		automaticAdoption: false,
		fixtureRunId: manifest.fixtureRunId,
		datasetId: manifest.datasetId,
		credentialCount: runtime.credentialCount,
		requiredTokenTtlSeconds: runtime.requiredTokenTtlSeconds,
		expectedBusinessRows: {
			measured: { weekly: 1000, daily: 7000, charge: 1000 },
			rollback: { persisted: 0 },
		},
		dbEvidence: 'runtime-observed-supporting-only',
		classification: 'conditional-not-adoptable',
	};
}

function validatePreparationDirectories(reportDirectory, secretDirectory, fixtureRunId) {
	for (const [label, directory] of [['reportDirectory', reportDirectory], ['secretDirectory', secretDirectory]]) {
		if (typeof directory !== 'string' || path.basename(directory) !== fixtureRunId || !fs.statSync(directory).isDirectory()) {
			throw new Error(`${label} is not the reserved fixture namespace.`);
		}
		if ((fs.statSync(directory).mode & 0o077) !== 0) throw new Error(`${label} must be owner-only.`);
	}
}

function validateNamespaceEvidence(evidence) {
	if (!evidence || Object.keys(evidence).sort().join(',') !== 'existingCampusCount,existingUserCount') {
		throw new Error('namespace evidence has an invalid exact schema.');
	}
	for (const field of ['existingCampusCount', 'existingUserCount']) {
		if (!Number.isSafeInteger(evidence[field]) || evidence[field] !== 0) throw new Error('fresh namespace must be empty before reservation.');
	}
	return evidence;
}

function validateInputObject(input) {
	exactKeys(input, INPUT_KEYS, 'prepare input');
	for (const field of ['adminEmail', 'adminPassword', 'fixtureUserPassword']) {
		if (typeof input[field] !== 'string' || input[field].length < 8) throw new Error(`prepare input ${field} is invalid.`);
	}
	if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.adminEmail)) throw new Error('prepare input adminEmail is invalid.');
	exactKeys(input.penaltyAccount, ACCOUNT_KEYS, 'penalty account');
	if (input.penaltyAccount.accountType !== 'PENALTY' || input.penaltyAccount.ownerUserId !== null) {
		throw new Error('penalty account must be a campus PENALTY account with ownerUserId null.');
	}
	for (const field of ['nickname', 'bankName', 'accountNumber', 'accountHolder']) {
		if (typeof input.penaltyAccount[field] !== 'string' || input.penaltyAccount[field].length < 1 || input.penaltyAccount[field].length > 100) {
			throw new Error(`penalty account ${field} must contain 1..100 characters.`);
		}
	}
	return { ...input, penaltyRules: validatePenaltyRules(input.penaltyRules) };
}

async function checkedRequest(request, counts, stage, call, expectedStatus) {
	let response;
	try {
		response = await request(call);
	} catch (error) {
		throw safeError(stage, undefined, error?.preparationDeadline ? 'PREPARATION_DEADLINE_EXCEEDED' : 'HTTP_REQUEST_FAILED');
	} finally {
		counts.httpRequests += 1;
	}
	if (!response || response.status !== expectedStatus || response.body?.success !== true) {
		throw safeError(stage, response?.status, safeCode(response?.body?.code));
	}
	return response.body;
}

function deadlineBoundRequest(request, deadlineEpochMilliseconds) {
	return async (call) => {
		if (Date.now() > deadlineEpochMilliseconds) throw deadlineError();
		const response = await request(call);
		if (Date.now() > deadlineEpochMilliseconds) throw deadlineError();
		return response;
	};
}

function deadlineError() {
	const error = new Error('preparation deadline exceeded.');
	error.preparationDeadline = true;
	return error;
}

function validateAdminAccessToken(token, userId, nowEpochSeconds, requiredTtlSeconds) {
	let claims;
	try {
		const parts = token.split('.');
		if (parts.length !== 3) throw new Error('compact JWT required');
		claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
	} catch (error) {
		throw safeError('admin-login', 200, 'ADMIN_ACCESS_TOKEN_CLAIMS_INVALID');
	}
	if (
		String(claims.sub) !== String(userId)
		|| claims.userId !== userId
		|| claims.tokenType !== 'ACCESS'
		|| !Number.isSafeInteger(claims.exp)
		|| claims.exp - nowEpochSeconds < requiredTtlSeconds
	) throw safeError('admin-login', 200, 'ADMIN_ACCESS_TOKEN_TTL_INSUFFICIENT');
}

function safeError(stage, httpStatus, code) {
	const error = new Error(`${stage} failed.`);
	error.stage = stage;
	error.httpStatus = httpStatus;
	error.safeCode = code;
	return error;
}

function safeCode(value) {
	return typeof value === 'string' && /^[A-Z0-9_]{1,80}$/.test(value) ? value : 'UNEXPECTED_API_RESPONSE';
}

function humanStage(stage) {
	return String(stage).replaceAll('-', ' ');
}

function positiveId(value, stage) {
	if (!Number.isSafeInteger(value) || value < 1) throw safeError(stage, undefined, 'POSITIVE_ID_REQUIRED');
	return value;
}

function receipt(blueprint, status, counts, additional = {}) {
	return {
		schemaVersion: 1,
		fixtureRunId: blueprint.fixtureRunId,
		datasetId: blueprint.datasetId,
		status,
		automaticAdoption: false,
		cleanupAllowed: false,
		reuseAllowed: false,
		counts: structuredClone(counts),
		...additional,
	};
}

function fixtureUser(cohort, ordinal, emailPrefix) {
	const padded = String(ordinal).padStart(4, '0');
	return { cohort, ordinal, name: `Issue197 ${cohort} ${padded}`, email: `${emailPrefix}${cohort}.${padded}@example.test` };
}

function boundedName(value) {
	if (value.length <= 100) return value;
	const digest = createHash('sha256').update(value).digest('hex').slice(0, 16);
	return `${value.slice(0, 82)}-${digest}`;
}

function parseDate(value, label) {
	if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error(`${label} must be YYYY-MM-DD.`);
	const date = new Date(`${value}T00:00:00Z`);
	if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) throw new Error(`${label} is invalid.`);
	return date;
}

function mondayBefore(reference) {
	const date = new Date(reference);
	date.setUTCDate(date.getUTCDate() - 1);
	while (date.getUTCDay() !== 1) date.setUTCDate(date.getUTCDate() - 1);
	return date.toISOString().slice(0, 10);
}

function mondayAfter(reference, offset) {
	const date = new Date(reference);
	date.setUTCDate(date.getUTCDate() + offset);
	while (date.getUTCDay() !== 1) date.setUTCDate(date.getUTCDate() + 1);
	return date.toISOString().slice(0, 10);
}

function exactKeys(value, keys, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).sort().join(',') !== [...keys].sort().join(',')) {
		throw new Error(`${label} must have the exact approved keys.`);
	}
}

function isRuntimeSecretPath(value) {
	const temporaryRoots = new Set([path.resolve(os.tmpdir()), fs.realpathSync(os.tmpdir())]);
	return [...temporaryRoots].some((root) => value.startsWith(`${root}${path.sep}`));
}

function isIgnoredReportPath(value) {
	const issueBuildSegment = `${path.sep}build${path.sep}reports${path.sep}k6${path.sep}issue-197${path.sep}`;
	return value.includes(issueBuildSegment) || isRuntimeSecretPath(value);
}

function seoulDate(date) {
	return new Intl.DateTimeFormat('en-CA', {
		timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
	}).format(date);
}

function writeJson(filePath, value) {
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
	fs.chmodSync(filePath, 0o600);
}

function writeJsonExclusive(filePath, value) {
	const descriptor = fs.openSync(filePath, 'wx', 0o600);
	try {
		fs.writeFileSync(descriptor, `${JSON.stringify(value, null, 2)}\n`);
	} finally {
		fs.closeSync(descriptor);
	}
}

async function fetchJson(call, baseUrl) {
	const headers = { 'Content-Type': 'application/json' };
	if (call.accessToken) headers.Authorization = `Bearer ${call.accessToken}`;
	const response = await fetch(new URL(call.path, baseUrl), {
		method: call.method,
		headers,
		body: JSON.stringify(call.body),
		redirect: 'error',
	});
	let body;
	try {
		body = await response.json();
	} catch (error) {
		body = null;
	}
	return { status: response.status, body };
}

async function main() {
	const command = process.argv[2];
	if (command === 'blueprint') {
		const input = readPreparationInput(requiredEnv('PREPARE_INPUT_FILE'));
		process.stdout.write(`${JSON.stringify(buildFixtureBlueprint({
			datasetId: requiredEnv('DATASET_ID'), fixtureRunId: requiredEnv('FIXTURE_RUN_ID'),
			referenceDate: requiredEnv('REFERENCE_DATE'), penaltyRules: input.penaltyRules,
		}), null, 2)}\n`);
		return;
	}
	if (command === 'validate-window') {
		process.stdout.write(`${JSON.stringify(validatePreparationWindow(Number(requiredEnv('PREPARE_MAX_DURATION_SECONDS'))))}\n`);
		return;
	}
	if (command === 'reserve') {
		const evidence = JSON.parse(fs.readFileSync(requiredEnv('NAMESPACE_EVIDENCE_FILE'), 'utf8'));
		const result = reservePreparation({
			reportRoot: requiredEnv('PREPARE_REPORT_ROOT'), secretRoot: requiredEnv('RUNTIME_SECRET_ROOT'),
			fixtureRunId: requiredEnv('FIXTURE_RUN_ID'), namespaceEvidence: evidence,
		});
		process.stdout.write(`${JSON.stringify(result)}\n`);
		return;
	}
	if (command === 'prepare') {
		const input = readPreparationInput(requiredEnv('PREPARE_INPUT_FILE'));
		const blueprint = buildFixtureBlueprint({
			datasetId: requiredEnv('DATASET_ID'), fixtureRunId: requiredEnv('FIXTURE_RUN_ID'),
			referenceDate: requiredEnv('REFERENCE_DATE'), penaltyRules: input.penaltyRules,
		});
		const result = await prepareDevotionFixture({
			blueprint, input, request: (call) => fetchJson(call, requiredEnv('BASE_URL')),
			reportDirectory: requiredEnv('PREPARE_REPORT_DIRECTORY'), secretDirectory: requiredEnv('RUNTIME_SECRET_DIRECTORY'),
			nowEpochSeconds: Math.floor(Date.now() / 1000),
			prepareMaxDurationSeconds: Number(requiredEnv('PREPARE_MAX_DURATION_SECONDS')),
			tokenTtlSafetySeconds: Number(requiredEnv('TOKEN_TTL_SAFETY_SECONDS')),
		});
		process.stdout.write(`${JSON.stringify({ status: result.receipt.status, manifestPath: result.manifestPath, credentialsPath: result.credentialsPath })}\n`);
		return;
	}
	if (command === 'inspect') {
		const result = inspectPreparation({
			manifest: JSON.parse(fs.readFileSync(requiredEnv('FIXTURE_MANIFEST'), 'utf8')),
			credentials: JSON.parse(fs.readFileSync(requiredEnv('CREDENTIALS_FILE'), 'utf8')),
			preflight: JSON.parse(fs.readFileSync(requiredEnv('PREFLIGHT_EVIDENCE_FILE'), 'utf8')),
			receipt: JSON.parse(fs.readFileSync(requiredEnv('PREPARATION_RECEIPT_FILE'), 'utf8')),
			workload: {
				warmupVus: Number(requiredEnv('WARMUP_VUS')), measuredVus: Number(requiredEnv('MEASURED_VUS')), rollbackVus: Number(requiredEnv('ROLLBACK_VUS')),
				warmupMaxDuration: requiredEnv('WARMUP_MAX_DURATION'), measuredMaxDuration: requiredEnv('MEASURED_MAX_DURATION'),
				rollbackMaxDuration: requiredEnv('ROLLBACK_MAX_DURATION'), tokenTtlSafetySeconds: Number(requiredEnv('TOKEN_TTL_SAFETY_SECONDS')),
			},
		});
		process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
		return;
	}
	throw new Error('unsupported devotion preparation command.');
}

function requiredEnv(name) {
	const value = process.env[name];
	if (!value) throw new Error(`${name} is required.`);
	return value;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
