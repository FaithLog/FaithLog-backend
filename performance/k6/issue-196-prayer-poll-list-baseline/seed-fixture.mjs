import { mkdirSync, rmdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { FIXTURE_CONTRACT, validateFixtureRunId } from './fixture-contract.mjs';
import { validatePublishedTarget } from './validate-published-target.mjs';
import { validateRuntimeIdentity } from './validate-runtime-identity.mjs';
import { parseRedisRuntimeIdentity } from './redis-runtime-identity.mjs';
import { assertRuntimePreparationMatches, validateRuntimePrepManifest } from './runtime-prep-contract.mjs';
import { assertCurrentTooling } from './tooling-provenance.mjs';

const BASE_URL = required('BASE_URL').replace(/\/$/, '');
const DATASET_ID = process.env.DATASET_ID || FIXTURE_CONTRACT.datasetId;
const FIXTURE_RUN_ID = validateFixtureRunId(process.env.FIXTURE_RUN_ID);
const ADMIN_EMAIL = required('PERF_ADMIN_EMAIL');
const ADMIN_PASSWORD = required('PERF_ADMIN_PASSWORD');
const MEMBER_PASSWORD = required('PERF_MEMBER_PASSWORD');
const WEEK_START_DATE = required('PERF_WEEK_START_DATE');
const REPORT_ROOT = resolve(process.env.REPORT_ROOT || 'build/reports/k6/issue-196');
const MANIFEST_PATH = resolve(process.env.FIXTURE_MANIFEST || `${REPORT_ROOT}/${FIXTURE_RUN_ID}/fixture-manifest.json`);
const APP_CONTAINER = required('APP_CONTAINER');
const DB_CONTAINER = required('DB_CONTAINER');
const REDIS_CONTAINER = required('REDIS_CONTAINER');
const EXPECTED_APP_SERVICE = required('EXPECTED_APP_SERVICE');
const EXPECTED_DB_SERVICE = required('EXPECTED_DB_SERVICE');
const EXPECTED_REDIS_SERVICE = required('EXPECTED_REDIS_SERVICE');
const EXPECTED_APP_IMAGE = required('EXPECTED_APP_IMAGE');
const EXPECTED_APP_IMAGE_ID = required('EXPECTED_APP_IMAGE_ID');
const EXPECTED_DB_IMAGE = required('EXPECTED_DB_IMAGE');
const EXPECTED_DB_IMAGE_ID = required('EXPECTED_DB_IMAGE_ID');
const EXPECTED_REDIS_IMAGE = required('EXPECTED_REDIS_IMAGE');
const EXPECTED_REDIS_IMAGE_ID = required('EXPECTED_REDIS_IMAGE_ID');
const EXPECTED_REDIS_PORT = required('EXPECTED_REDIS_PORT');
const EXPECTED_FLYWAY_VERSION = required('EXPECTED_FLYWAY_VERSION');
const EXPECTED_SOURCE_REVISION = required('EXPECTED_SOURCE_REVISION');
const RUNTIME_PREP_MANIFEST = resolve(required('RUNTIME_PREP_MANIFEST'));
const SCENARIO_WORKTREE = resolve(required('PERF_SCENARIO_WORKTREE'));
const EXPECTED_SCENARIO_HEAD = required('EXPECTED_SCENARIO_HEAD');
const DB_USER = required('PERF_DB_USER');
const DB_NAME = required('PERF_DB_NAME');
const DB_PASSWORD = required('PERF_DB_PASSWORD');

for (const name of [
	'PERF_ACCESS_TOKEN',
	'PERF_ADMIN_ACCESS_TOKEN',
	'PERF_MEMBER_ACCESS_TOKEN',
	'PERF_COFFEE_CREATOR_ACCESS_TOKEN',
	'PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN',
	'PERF_MEAL_DUTY_ACCESS_TOKEN',
]) {
	delete process.env[name];
}

guardLocalTarget();
guardDatasetIdentity();
validateMonday(WEEK_START_DATE);
if (EXPECTED_SOURCE_REVISION !== FIXTURE_CONTRACT.currentDevelop.sourceRevision
	|| EXPECTED_FLYWAY_VERSION !== FIXTURE_CONTRACT.currentDevelop.flywayVersion) {
	throw new Error('Approved source/Flyway identity does not match the current-develop scenario contract.');
}
const runtimePreparation = validateRuntimePrepManifest(JSON.parse(readFileSync(RUNTIME_PREP_MANIFEST, 'utf8')), {
	sourceRevision: EXPECTED_SOURCE_REVISION,
});
assertCurrentTooling(runtimePreparation.tooling, SCENARIO_WORKTREE, EXPECTED_SCENARIO_HEAD);
const composeRuntime = verifyComposeRuntime();
assertRuntimePreparationMatches(runtimePreparation, composeRuntime);
const projectLock = `/tmp/faithlog-performance-${composeRuntime.composeProject}.lock`;
acquireProjectLock(projectLock);

try {
	const postLockRuntime = verifyComposeRuntime();
	if (!semanticEqual(composeRuntime, postLockRuntime)) {
		throw new Error('Runtime identity changed after project lock.');
	}
	assertRuntimePreparationMatches(runtimePreparation, postLockRuntime);
	assertCurrentTooling(runtimePreparation.tooling, SCENARIO_WORKTREE, EXPECTED_SCENARIO_HEAD);
	if (existsSync(MANIFEST_PATH)) {
		throw new Error(`Manifest already exists for FIXTURE_RUN_ID=${FIXTURE_RUN_ID}. Use a new fixtureRunId; existing rows are never reused or cleaned up.`);
	}

	const initialAdminSession = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
	let adminToken = initialAdminSession.accessToken;
	const primaryCampus = await createCampus(adminToken, 'PRIMARY');
	const isolationCampus = await createCampus(adminToken, 'ISOLATION');

	const generatedPrimaryMembers = await createMembers(
		primaryCampus,
		FIXTURE_CONTRACT.primaryCampus.activeMemberCount - 1,
		'p'
	);
	const generatedIsolationMembers = await createMembers(
		isolationCampus,
		FIXTURE_CONTRACT.isolationCampus.activeMemberCount - 1,
		'i'
	);
	const refreshedAdminSession = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
	adminToken = refreshedAdminSession.accessToken;
	const adminUserId = refreshedAdminSession.user.id;
	const primaryMembers = [
		creatorMember(refreshedAdminSession, primaryCampus.campusId, adminUserId),
		...generatedPrimaryMembers,
	].sort((left, right) => left.membershipId - right.membershipId);
	const isolationMembers = [
		creatorMember(refreshedAdminSession, isolationCampus.campusId, adminUserId),
		...generatedIsolationMembers,
	].sort((left, right) => left.membershipId - right.membershipId);
	const dutyActors = {
		coffeeCreator: generatedPrimaryMembers[1],
		otherCoffeeDuty: generatedPrimaryMembers[2],
		mealDuty: generatedPrimaryMembers[3],
	};
	await assignDuties(adminToken, primaryCampus.campusId, dutyActors);

	const prayer = await createPrayerFixture(
		adminToken,
		primaryCampus.campusId,
		primaryMembers,
		FIXTURE_CONTRACT.prayer.groupCount,
		FIXTURE_CONTRACT.prayer.membersPerGroup,
		FIXTURE_CONTRACT.prayer.submissionCount,
		'PRIMARY'
	);
	const isolationPrayer = await createPrayerFixture(
		adminToken,
		isolationCampus.campusId,
		isolationMembers,
		2,
		25,
		0,
		'ISOLATION'
	);

	adminToken = (await login(ADMIN_EMAIL, ADMIN_PASSWORD)).accessToken;
	const polls = await createPollFixture(
		adminToken,
		primaryCampus.campusId,
		primaryMembers,
		generatedPrimaryMembers
	);
	const dutyPolls = await createDutyPollFixture(primaryCampus.campusId, dutyActors);
	adminToken = (await login(ADMIN_EMAIL, ADMIN_PASSWORD)).accessToken;
	const isolationPoll = await createPoll(
		adminToken,
		isolationCampus.campusId,
		'isolation',
		false,
		currentWindow()
	);

	const manifest = {
		datasetId: DATASET_ID,
		fixtureRunId: FIXTURE_RUN_ID,
		fixtureRunIdImmutable: true,
		createdAt: new Date().toISOString(),
		runtimePreparation,
		composeRuntime,
		shapedAt: null,
		weekStartDate: WEEK_START_DATE,
		primaryCampus: {
			campusId: primaryCampus.campusId,
			activeMemberCount: primaryMembers.length,
			memberActor: generatedPrimaryMembers[0],
			...dutyActors,
			members: primaryMembers,
		},
		isolationCampus: {
			campusId: isolationCampus.campusId,
			activeMemberCount: isolationMembers.length,
			members: isolationMembers,
		},
		prayer: {
			...prayer,
			isolationSeasonId: isolationPrayer.seasonId,
			isolationGroupIds: isolationPrayer.groups.map((group) => group.groupId),
		},
		polls: {
			...polls,
			duty: dutyPolls,
			manageableByMe: FIXTURE_CONTRACT.polls.manageableByMe,
			isolationPollId: isolationPoll.id,
		},
	};

	mkdirSync(dirname(MANIFEST_PATH), { recursive: true });
	writeFileSync(MANIFEST_PATH, `${JSON.stringify(manifest, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
	console.log(`Scenario fixture created. manifest=${MANIFEST_PATH}`);
	console.log('No password or access token was written to the manifest. Run shape-fixture.sh before baseline measurement.');
} finally {
	releaseProjectLock(projectLock);
}

async function createCampus(adminToken, scope) {
	return request('POST', '/api/v1/campuses', {
		name: `PERF_196_${FIXTURE_RUN_ID}_${scope}`,
		region: 'PERF_LOCAL_ONLY',
		description: `${DATASET_ID}:${FIXTURE_RUN_ID}:${scope}`,
	}, adminToken, [201]);
}

async function createMembers(campus, count, scope) {
	const members = [];
	for (let index = 0; index < count; index += 1) {
		const ordinal = String(index + 1).padStart(4, '0');
		const email = `i196-${FIXTURE_RUN_ID.toLowerCase()}-${scope}${ordinal}@perf.faithlog.local`;
		const signup = await request('POST', '/api/v1/auth/signup', {
			name: `PERF196 ${scope.toUpperCase()} ${ordinal}`,
			email,
			password: MEMBER_PASSWORD,
		}, undefined, [201]);
		const memberToken = (await login(email, MEMBER_PASSWORD)).accessToken;
		const membership = await request('POST', '/api/v1/campuses/join', {
			inviteCode: campus.inviteCode,
		}, memberToken, [201]);
		members.push({
			userId: signup.id,
			email,
			membershipId: membership.membershipId,
			ordinal: index + 1,
		});
		if ((index + 1) % 100 === 0 || index + 1 === count) {
			console.log(`Created ${scope} members: ${index + 1}/${count}`);
		}
	}
	return members;
}

async function createPrayerFixture(adminToken, campusId, members, groupCount, membersPerGroup, submissionCount, scope) {
	const season = await request('POST', `/api/v1/admin/campuses/${campusId}/prayer-seasons`, {
		name: `PERF_196_${FIXTURE_RUN_ID}_${scope}_SEASON`,
		startDate: WEEK_START_DATE,
	}, adminToken, [201]);
	const groups = [];
	for (let groupIndex = 0; groupIndex < groupCount; groupIndex += 1) {
		const created = await request('POST', `/api/v1/admin/prayer-seasons/${season.seasonId}/groups`, {
			name: `PERF_196_${FIXTURE_RUN_ID}_${scope}_GROUP_${String(groupIndex + 1).padStart(2, '0')}`,
			sortOrder: groupIndex + 1,
		}, adminToken, [201]);
		const groupMembers = members.slice(groupIndex * membersPerGroup, (groupIndex + 1) * membersPerGroup);
		const replaced = await request('PUT', `/api/v1/admin/prayer-groups/${created.groupId}/members`, {
			userIds: groupMembers.map((member) => member.userId),
		}, adminToken, [200]);
		groups.push({
			groupId: replaced.groupId,
			sortOrder: replaced.sortOrder,
			memberUserIds: groupMembers.map((member) => member.userId),
		});
	}

	const submissionMembers = members.slice(0, submissionCount);
	if (submissionMembers.length > 0) {
		await request('PUT', `/api/v1/campuses/${campusId}/prayers/weeks/${WEEK_START_DATE}/submissions`, {
			submissions: submissionMembers.map((member) => ({
				userId: member.userId,
				content: `PERF_196_${FIXTURE_RUN_ID}_PRAYER_${member.ordinal}`,
				version: 0,
			})),
		}, adminToken, [200]);
	}

	return {
		seasonId: season.seasonId,
		groups,
		submissionUserIds: submissionMembers.map((member) => member.userId),
		unsubmittedUserIds: members.slice(submissionCount).map((member) => member.userId),
		memberActorGroupId: groups[0]?.groupId ?? null,
	};
}

async function createPollFixture(adminToken, campusId, activeMembers, responseCandidates) {
	let currentAdminToken = adminToken;
	const byKey = {};
	for (const visibilityCase of FIXTURE_CONTRACT.polls.visibilityCases) {
		const window = visibilityCase.key === 'scheduled_future' ? futureWindow() : currentWindow();
		byKey[visibilityCase.key] = await createPoll(
			currentAdminToken,
			campusId,
			visibilityCase.key,
			visibilityCase.key === 'closed_admin_only',
			window
		);
	}

	const responderMembers = responseCandidates.slice(0, FIXTURE_CONTRACT.polls.responseCount);
	const responderIds = new Set(responderMembers.map((member) => member.userId));
	const missingMembers = activeMembers.filter((member) => !responderIds.has(member.userId));
	const commentIds = [];
	for (let index = 0; index < responderMembers.length; index += 1) {
		const member = responderMembers[index];
		const memberToken = (await login(member.email, MEMBER_PASSWORD)).accessToken;
		for (const pollKey of ['open', 'closed_admin_only']) {
			const poll = byKey[pollKey];
			await request('PUT', `/api/v1/campuses/${campusId}/polls/${poll.id}/responses/me`, {
				optionIds: [poll.optionIds[index % poll.optionIds.length]],
				memo: `PERF_196_${FIXTURE_RUN_ID}_${pollKey}_${member.ordinal}`,
			}, memberToken, [200]);
		}
		if (index < FIXTURE_CONTRACT.polls.commentCount) {
			const comment = await request('POST', `/api/v1/campuses/${campusId}/polls/${byKey.open.id}/comments`, {
				content: `PERF_196_${FIXTURE_RUN_ID}_COMMENT_${member.ordinal}`,
			}, memberToken, [201]);
			commentIds.push(comment.commentId);
		}
		if ((index + 1) % 100 === 0 || index + 1 === responderMembers.length) {
			console.log(`Created poll responses: ${index + 1}/${responderMembers.length}`);
		}
	}

	currentAdminToken = (await login(ADMIN_EMAIL, ADMIN_PASSWORD)).accessToken;
	for (const key of ['closed_member_visible', 'closed_admin_only', 'closed_expired']) {
		const closed = await request('PATCH', `/api/v1/admin/campuses/${campusId}/polls/${byKey[key].id}/close`, undefined, currentAdminToken, [200]);
		byKey[key] = {
			...byKey[key],
			status: closed.status,
			startsAt: closed.startsAt,
			endsAt: closed.endsAt,
		};
	}

	const templates = [];
	for (let index = 0; index < FIXTURE_CONTRACT.polls.templateCount; index += 1) {
		const template = await request('POST', `/api/v1/admin/campuses/${campusId}/poll-templates`, {
			title: `PERF_196_${FIXTURE_RUN_ID}_TEMPLATE_${String(index + 1).padStart(2, '0')}`,
			pollType: 'CUSTOM',
			selectionType: 'SINGLE',
			chargeGenerationType: 'NONE',
			allowUserOptionAdd: false,
			autoCreateEnabled: false,
			startDayOfWeek: 1,
			startTime: '09:00:00',
			endDayOfWeek: 7,
			endTime: '18:00:00',
			options: Array.from({ length: FIXTURE_CONTRACT.polls.optionsPerTemplate }, (_, optionIndex) => ({
				content: `PERF_196_${FIXTURE_RUN_ID}_T${index + 1}_O${optionIndex + 1}`,
				priceAmount: 0,
				sortOrder: optionIndex + 1,
			})),
		}, currentAdminToken, [201]);
		templates.push({
			id: template.id,
			optionIds: template.options.map((option) => option.id),
		});
	}

	return {
		byKey,
		responderUserIds: responderMembers.map((member) => member.userId),
		missingUserIds: missingMembers.map((member) => member.userId),
		commentIds,
		templates,
		templateIds: templates.map((template) => template.id),
		templateDetailId: templates[0].id,
		expectedMemberVisibleKeys: ['open', 'closed_member_visible'],
		expectedAdminVisibleKeys: ['open', 'closed_member_visible', 'closed_admin_only'],
	};
}

async function assignDuties(adminToken, campusId, actors) {
	for (const actor of [actors.coffeeCreator, actors.otherCoffeeDuty]) {
		await request('PUT', `/api/v1/admin/campuses/${campusId}/duty-assignments/coffee`, {
			userId: actor.userId,
		}, adminToken, [200]);
	}
	await request('POST', `/api/v1/admin/campuses/${campusId}/duty-assignments/meal`, {
		userId: actors.mealDuty.userId,
	}, adminToken, [200]);
}

async function createDutyPollFixture(campusId, actors) {
	const coffeeToken = (await login(actors.coffeeCreator.email, MEMBER_PASSWORD)).accessToken;
	const brands = await request('GET', '/api/v1/coffee-brands', undefined, coffeeToken, [200]);
	if (!Array.isArray(brands) || brands.length === 0) {
		throw new Error('Current-develop COFFEE fixture requires at least one active catalog brand.');
	}
	const menus = await request('GET', `/api/v1/coffee-brands/${brands[0].id}/menus`, undefined, coffeeToken, [200]);
	if (!Array.isArray(menus) || menus.length < FIXTURE_CONTRACT.polls.optionCount) {
		throw new Error(`Current-develop COFFEE fixture requires ${FIXTURE_CONTRACT.polls.optionCount} active menu rows.`);
	}
	const account = await request('POST', `/api/v1/admin/campuses/${campusId}/payment-accounts`, {
		accountType: 'COFFEE',
		nickname: `PERF_196_${FIXTURE_RUN_ID}_COFFEE`,
		bankName: 'PERF_LOCAL_ONLY',
		accountNumber: `196-${FIXTURE_RUN_ID}`,
		accountHolder: `PERF_196_${FIXTURE_RUN_ID}`,
		ownerUserId: actors.coffeeCreator.userId,
	}, coffeeToken, [201]);
	const coffeeWindow = currentWindow();
	const coffeeTitle = `PERF_196_${FIXTURE_RUN_ID}_POLL_COFFEE`;
	const coffee = await request('POST', `/api/v1/admin/campuses/${campusId}/polls`, {
		title: coffeeTitle,
		pollType: 'COFFEE',
		selectionType: 'SINGLE',
		isAnonymous: false,
		allowUserOptionAdd: false,
		chargeGenerationType: 'OPTION_PRICE',
		paymentCategory: 'COFFEE',
		paymentAccountId: account.id,
		startsAt: coffeeWindow.startsAt,
		endsAt: coffeeWindow.endsAt,
		options: menus.slice(0, FIXTURE_CONTRACT.polls.optionCount).map((menu, index) => ({
			content: menu.name,
			menuId: menu.id,
			priceAmount: menu.priceAmount,
			sortOrder: index + 1,
		})),
	}, coffeeToken, [201]);

	const mealToken = (await login(actors.mealDuty.email, MEMBER_PASSWORD)).accessToken;
	const openMeal = await createMealPoll(mealToken, campusId, 'OPEN');
	const mealToArchive = await createMealPoll(mealToken, campusId, 'ARCHIVED');
	const archivedMeal = await request('PATCH', `/api/v1/campuses/${campusId}/meal/polls/${mealToArchive.id}/close`, undefined, mealToken, [200]);
	return {
		coffee: pollManifest(coffee),
		mealOpen: pollManifest(openMeal),
		mealArchived: pollManifest(archivedMeal),
		mealManagementDefaultIds: [openMeal.id],
		mealManagementArchiveIds: [openMeal.id, archivedMeal.id].sort((left, right) => right - left),
	};
}

async function createMealPoll(token, campusId, key) {
	const title = `PERF_196_${FIXTURE_RUN_ID}_POLL_MEAL_${key}`;
	return request('POST', `/api/v1/campuses/${campusId}/meal/polls`, {
		title,
		isAnonymous: false,
		allowUserOptionAdd: false,
		endsAt: currentWindow().endsAt,
		options: Array.from({ length: FIXTURE_CONTRACT.polls.optionCount }, (_, index) => ({
			content: `${title}_OPTION_${index + 1}`,
			sortOrder: index + 1,
		})),
	}, token, [201]);
}

function pollManifest(poll) {
	return {
		id: poll.id,
		title: poll.title,
		status: poll.status,
		startsAt: poll.startsAt,
		endsAt: poll.endsAt,
		optionIds: poll.options.map((option) => option.id),
	};
}

async function createPoll(adminToken, campusId, key, anonymous, window) {
	const title = `PERF_196_${FIXTURE_RUN_ID}_POLL_${key.toUpperCase()}`;
	const poll = await request('POST', `/api/v1/admin/campuses/${campusId}/polls`, {
		title,
		pollType: 'CUSTOM',
		selectionType: 'SINGLE',
		isAnonymous: anonymous,
		allowUserOptionAdd: false,
		chargeGenerationType: 'NONE',
		startsAt: window.startsAt,
		endsAt: window.endsAt,
		options: Array.from({ length: FIXTURE_CONTRACT.polls.optionCount }, (_, index) => ({
			content: `${title}_OPTION_${index + 1}`,
			priceAmount: 0,
			sortOrder: index + 1,
		})),
	}, adminToken, [201]);
	return {
		id: poll.id,
		title: poll.title,
		status: poll.status,
		anonymous: poll.isAnonymous,
		startsAt: poll.startsAt,
		endsAt: poll.endsAt,
		optionIds: poll.options.map((option) => option.id),
	};
}

async function login(email, password) {
	return request('POST', '/api/v1/auth/login', { email, password }, undefined, [200]);
}

async function request(method, path, body, token, expectedStatuses) {
	const headers = { 'Content-Type': 'application/json' };
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	const response = await fetch(`${BASE_URL}${path}`, {
		method,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	let payload;
	try {
		payload = text ? JSON.parse(text) : {};
	} catch {
		payload = {};
	}
	if (!expectedStatuses.includes(response.status) || payload.success === false) {
		const safeBody = path === '/api/v1/auth/login' ? '<redacted>' : text.slice(0, 1000);
		throw new Error(`${method} ${path} failed: status=${response.status} body=${safeBody}`);
	}
	return payload.data;
}

function currentWindow() {
	const now = Date.now();
	return {
		startsAt: new Date(now - 60_000).toISOString(),
		endsAt: new Date(now + 6 * 60 * 60 * 1000).toISOString(),
	};
}

function futureWindow() {
	const now = Date.now();
	return {
		startsAt: new Date(now + 2 * 24 * 60 * 60 * 1000).toISOString(),
		endsAt: new Date(now + 3 * 24 * 60 * 60 * 1000).toISOString(),
	};
}

function required(name) {
	const value = process.env[name];
	if (!value) {
		throw new Error(`${name} is required at runtime.`);
	}
	return value;
}

function creatorMember(session, campusId, userId) {
	const membership = session.user.campusMemberships.find((candidate) => candidate.campusId === campusId);
	if (!membership) {
		throw new Error(`Campus creator membership not found for campusId=${campusId}.`);
	}
	return {
		userId,
		email: null,
		membershipId: membership.campusMemberId,
		ordinal: 0,
		creator: true,
	};
}

function guardLocalTarget() {
	if (!/^http:\/\/(127\.0\.0\.1|\[::1\])(?::\d+)?$/.test(BASE_URL)) {
		throw new Error('Issue #196 fixture creation is local-Docker-only; remote targets are blocked.');
	}
}

function guardDatasetIdentity() {
	if (DATASET_ID !== FIXTURE_CONTRACT.datasetId) {
		throw new Error(`DATASET_ID must remain ${FIXTURE_CONTRACT.datasetId}; use FIXTURE_RUN_ID for each run.`);
	}
}

function verifyComposeRuntime() {
	const appContainerId = dockerInspect(APP_CONTAINER, '{{.Id}}');
	const appImageId = dockerInspect(APP_CONTAINER, '{{.Image}}');
	const appContainerStartedAt = dockerInspect(APP_CONTAINER, '{{.State.StartedAt}}');
	const dbContainerId = dockerInspect(DB_CONTAINER, '{{.Id}}');
	const dbImageId = dockerInspect(DB_CONTAINER, '{{.Image}}');
	const dbContainerStartedAt = dockerInspect(DB_CONTAINER, '{{.State.StartedAt}}');
	const redisContainerId = dockerInspect(REDIS_CONTAINER, '{{.Id}}');
	const redisImageId = dockerInspect(REDIS_CONTAINER, '{{.Image}}');
	const redisContainerStartedAt = dockerInspect(REDIS_CONTAINER, '{{.State.StartedAt}}');
	const appProject = dockerLabel(APP_CONTAINER, 'com.docker.compose.project');
	const dbProject = dockerLabel(DB_CONTAINER, 'com.docker.compose.project');
	const redisProject = dockerLabel(REDIS_CONTAINER, 'com.docker.compose.project');
	const appService = dockerLabel(APP_CONTAINER, 'com.docker.compose.service');
	const dbService = dockerLabel(DB_CONTAINER, 'com.docker.compose.service');
	const redisService = dockerLabel(REDIS_CONTAINER, 'com.docker.compose.service');
	const appConfigHash = dockerLabel(APP_CONTAINER, 'com.docker.compose.config-hash');
	const dbConfigHash = dockerLabel(DB_CONTAINER, 'com.docker.compose.config-hash');
	const redisConfigHash = dockerLabel(REDIS_CONTAINER, 'com.docker.compose.config-hash');
	const appImage = dockerInspect(APP_CONTAINER, '{{.Config.Image}}');
	const dbImage = dockerInspect(DB_CONTAINER, '{{.Config.Image}}');
	const redisImage = dockerInspect(REDIS_CONTAINER, '{{.Config.Image}}');
	const databaseIdentity = captureDatabaseIdentity();
	const redisIdentity = captureRedisIdentity();
	const publishedPorts = execFileSync('docker', ['port', APP_CONTAINER, '8080/tcp'], { encoding: 'utf8', env: sanitizedChildEnv() }).trim();
	const publishedTarget = validatePublishedTarget(BASE_URL, publishedPorts.split(/\r?\n/).filter(Boolean));
	const targetPort = String(publishedTarget.hostPort);
	if (!appProject || appProject !== dbProject || appProject !== redisProject
		|| appService !== EXPECTED_APP_SERVICE || dbService !== EXPECTED_DB_SERVICE || redisService !== EXPECTED_REDIS_SERVICE
		|| !appConfigHash || !dbConfigHash || !redisConfigHash
		|| appImage !== EXPECTED_APP_IMAGE || appImageId !== EXPECTED_APP_IMAGE_ID
		|| dbImage !== EXPECTED_DB_IMAGE || dbImageId !== EXPECTED_DB_IMAGE_ID
		|| redisImage !== EXPECTED_REDIS_IMAGE || redisImageId !== EXPECTED_REDIS_IMAGE_ID) {
		throw new Error('immutable-app-image-mismatch: approved app/PostgreSQL/Redis Compose identity did not match.');
	}
	if (!/^[a-z0-9][a-z0-9_-]*$/.test(appProject)) {
		throw new Error('Compose project label cannot be represented by the canonical project lock path.');
	}
	return {
		composeProject: appProject,
		sourceRevision: EXPECTED_SOURCE_REVISION,
		appContainer: APP_CONTAINER,
		dbContainer: DB_CONTAINER,
		redisContainer: REDIS_CONTAINER,
		appService,
		dbService,
		redisService,
		appConfigHash,
		dbConfigHash,
		redisConfigHash,
		appImage,
		dbImage,
		redisImage,
		appContainerId,
		appImageId,
		appContainerStartedAt,
		dbContainerId,
		dbImageId,
		dbContainerStartedAt,
		redisContainerId,
		redisImageId,
		redisContainerStartedAt,
		...redisIdentity,
		databaseIdentity,
		publishedTarget,
		targetPort,
	};
}

function captureDatabaseIdentity() {
	const sql = readFileSync(new URL('./db-runtime-identity.sql', import.meta.url), 'utf8');
	const result = execFileSync('docker', [
		'exec', '-e', 'PGPASSWORD', '-e', 'PGAPPNAME=faithlog_issue196_observer', DB_CONTAINER,
		'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-U', DB_USER, '-d', DB_NAME, '-At',
		'-f', '-',
	], { encoding: 'utf8', input: sql, env: { ...sanitizedChildEnv(), PGPASSWORD: DB_PASSWORD } }).trim();
	return validateRuntimeIdentity(JSON.parse(result), {
		expectedFlywayVersion: EXPECTED_FLYWAY_VERSION,
		expectedTableCount: FIXTURE_CONTRACT.currentDevelop.publicApplicationTableCount,
	});
}

function captureRedisIdentity() {
	const info = execFileSync('docker', ['exec', REDIS_CONTAINER, 'redis-cli', '--raw', 'INFO', 'server'], {
		encoding: 'utf8', env: sanitizedChildEnv(),
	});
	return parseRedisRuntimeIdentity(info, EXPECTED_REDIS_PORT);
}

function semanticEqual(left, right) {
	if (Object.is(left, right)) return true;
	if (Array.isArray(left) && Array.isArray(right)) {
		return left.length === right.length && left.every((value, index) => semanticEqual(value, right[index]));
	}
	if (left && right && typeof left === 'object' && typeof right === 'object') {
		const leftKeys = Object.keys(left).sort();
		const rightKeys = Object.keys(right).sort();
		return semanticEqual(leftKeys, rightKeys) && leftKeys.every((key) => semanticEqual(left[key], right[key]));
	}
	return false;
}

function dockerLabel(container, label) {
	return dockerInspect(container, `{{ index .Config.Labels "${label}" }}`);
}

function dockerInspect(container, format) {
	return execFileSync('docker', ['inspect', '--format', format, container], { encoding: 'utf8', env: sanitizedChildEnv() }).trim();
}

function sanitizedChildEnv() {
	const blocked = /^(PERF_ADMIN_EMAIL|PERF_ADMIN_PASSWORD|PERF_MEMBER_PASSWORD|PERF_DB_USER|PERF_DB_NAME|PERF_DB_PASSWORD|PERF_ACCESS_TOKEN|PERF_ADMIN_ACCESS_TOKEN|PERF_MEMBER_ACCESS_TOKEN|PERF_COFFEE_CREATOR_ACCESS_TOKEN|PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN|PERF_MEAL_DUTY_ACCESS_TOKEN)$/;
	return Object.fromEntries(Object.entries(process.env).filter(([name]) => !blocked.test(name)));
}

function validateMonday(value) {
	const date = new Date(`${value}T00:00:00Z`);
	if (Number.isNaN(date.getTime()) || date.getUTCDay() !== 1) {
		throw new Error('PERF_WEEK_START_DATE must be a valid Monday in YYYY-MM-DD format.');
	}
}

function acquireProjectLock(lockPath) {
	try {
		mkdirSync(lockPath);
	} catch {
		throw new Error(`Another performance seed or load run owns ${lockPath}. Parallel execution is forbidden.`);
	}
}

function releaseProjectLock(lockPath) {
	try {
		rmdirSync(lockPath);
	} catch {
		// The lock is an empty directory owned by this process. A missing lock needs no cleanup.
	}
}
