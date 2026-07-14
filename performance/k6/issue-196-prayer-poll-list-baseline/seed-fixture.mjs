import { mkdirSync, rmdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { FIXTURE_CONTRACT, currentMonday, validateFixtureRunId } from './fixture-contract.mjs';

const BASE_URL = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const DATASET_ID = process.env.DATASET_ID || FIXTURE_CONTRACT.datasetId;
const FIXTURE_RUN_ID = validateFixtureRunId(process.env.FIXTURE_RUN_ID);
const ADMIN_EMAIL = required('PERF_ADMIN_EMAIL');
const ADMIN_PASSWORD = required('PERF_ADMIN_PASSWORD');
const MEMBER_PASSWORD = required('PERF_MEMBER_PASSWORD');
const WEEK_START_DATE = process.env.PERF_WEEK_START_DATE || currentMonday();
const REPORT_ROOT = resolve(process.env.REPORT_ROOT || 'build/reports/k6/issue-196');
const MANIFEST_PATH = resolve(process.env.FIXTURE_MANIFEST || `${REPORT_ROOT}/${FIXTURE_RUN_ID}/fixture-manifest.json`);
const APP_CONTAINER = process.env.APP_CONTAINER || 'faithlog-backend';
const DB_CONTAINER = process.env.DB_CONTAINER || 'faithlog-postgres';
const EXPECTED_APP_IMAGE = 'faithlog-latest';

for (const name of ['PERF_ACCESS_TOKEN', 'PERF_ADMIN_ACCESS_TOKEN', 'PERF_MEMBER_ACCESS_TOKEN']) {
	delete process.env[name];
}

guardLocalTarget();
guardDatasetIdentity();
validateMonday(WEEK_START_DATE);
const composeRuntime = verifyComposeRuntime();
const projectLock = `/tmp/faithlog-performance-${composeRuntime.composeProject}.lock`;
acquireProjectLock(projectLock);

try {
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
		composeRuntime,
		shapedAt: null,
		weekStartDate: WEEK_START_DATE,
		primaryCampus: {
			campusId: primaryCampus.campusId,
			activeMemberCount: primaryMembers.length,
			memberActor: generatedPrimaryMembers[0],
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
	if (!/^http:\/\/(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/.test(BASE_URL)) {
		throw new Error('Issue #196 fixture creation is local-Docker-only; remote targets are blocked.');
	}
}

function guardDatasetIdentity() {
	if (DATASET_ID !== FIXTURE_CONTRACT.datasetId) {
		throw new Error(`DATASET_ID must remain ${FIXTURE_CONTRACT.datasetId}; use FIXTURE_RUN_ID for each run.`);
	}
}

function verifyComposeRuntime() {
	const appProject = dockerLabel(APP_CONTAINER, 'com.docker.compose.project');
	const dbProject = dockerLabel(DB_CONTAINER, 'com.docker.compose.project');
	const appService = dockerLabel(APP_CONTAINER, 'com.docker.compose.service');
	const dbService = dockerLabel(DB_CONTAINER, 'com.docker.compose.service');
	const appConfigHash = dockerLabel(APP_CONTAINER, 'com.docker.compose.config-hash');
	const dbConfigHash = dockerLabel(DB_CONTAINER, 'com.docker.compose.config-hash');
	const appImage = dockerInspect(APP_CONTAINER, '{{.Config.Image}}');
	const appImageId = dockerInspect(APP_CONTAINER, '{{.Image}}');
	const publishedPorts = execFileSync('docker', ['port', APP_CONTAINER, '8080/tcp'], { encoding: 'utf8', env: sanitizedChildEnv() }).trim();
	const targetPort = new URL(BASE_URL).port || '80';
	if (!appProject || appProject !== dbProject || appService !== 'app' || dbService !== 'postgres'
		|| !appConfigHash || !dbConfigHash
		|| !(appImage === EXPECTED_APP_IMAGE || appImage.startsWith(`${EXPECTED_APP_IMAGE}:`))
		|| !publishedPorts.split(/\r?\n/).some((binding) => binding.endsWith(`:${targetPort}`))) {
		throw new Error('Seed requires the approved app/PostgreSQL Compose project, service/config-hash labels, and app image.');
	}
	if (!/^[a-z0-9][a-z0-9_-]*$/.test(appProject)) {
		throw new Error('Compose project label cannot be represented by the canonical project lock path.');
	}
	return { composeProject: appProject, appService, dbService, appConfigHash, dbConfigHash, appImage, appImageId, targetPort };
}

function dockerLabel(container, label) {
	return dockerInspect(container, `{{ index .Config.Labels "${label}" }}`);
}

function dockerInspect(container, format) {
	return execFileSync('docker', ['inspect', '--format', format, container], { encoding: 'utf8', env: sanitizedChildEnv() }).trim();
}

function sanitizedChildEnv() {
	const blocked = /^(PERF_ADMIN_EMAIL|PERF_ADMIN_PASSWORD|PERF_MEMBER_PASSWORD|PERF_DB_USER|PERF_DB_NAME|PERF_DB_PASSWORD|PERF_ACCESS_TOKEN|PERF_ADMIN_ACCESS_TOKEN|PERF_MEMBER_ACCESS_TOKEN)$/;
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
