import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { isDeepStrictEqual } from 'node:util';
import { validateTargetIdentity } from './validate-target-identity.mjs';

const contract = JSON.parse(fs.readFileSync(new URL('./scenario-contract.json', import.meta.url), 'utf8'));

const BASE_URL = process.env.BASE_URL?.replace(/\/$/, '');
const ADMIN_EMAIL = process.env.PERF_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.PERF_ADMIN_PASSWORD;
const DATASET_ID = process.env.PERF_DATASET_ID;
const FIXTURE_RUN_ID = process.env.PERF_FIXTURE_RUN_ID;
const SOURCE_COMMIT = process.env.PERF_SOURCE_COMMIT;
const APP_CONTAINER_ID = process.env.APP_CONTAINER_ID;
const EXPECTED_APP_COMPOSE_SERVICE = process.env.EXPECTED_APP_COMPOSE_SERVICE;
const EXPECTED_APP_IMAGE_ID = process.env.EXPECTED_APP_IMAGE_ID;
const POSTGRES_CONTAINER_ID = process.env.POSTGRES_CONTAINER_ID;
const EXPECTED_POSTGRES_COMPOSE_SERVICE = process.env.EXPECTED_POSTGRES_COMPOSE_SERVICE;
const EXPECTED_POSTGRES_IMAGE_ID = process.env.EXPECTED_POSTGRES_IMAGE_ID;
const REDIS_CONTAINER_ID = process.env.REDIS_CONTAINER_ID;
const EXPECTED_REDIS_COMPOSE_SERVICE = process.env.EXPECTED_REDIS_COMPOSE_SERVICE;
const EXPECTED_REDIS_IMAGE_ID = process.env.EXPECTED_REDIS_IMAGE_ID;
const REPORT_ROOT = process.env.PERF_REPORT_ROOT
	|| path.join('performance', 'k6', 'issue-195', 'reports');
const requiredActiveMembers = Number(process.env.EXPECTED_ACTIVE_MEMBERS);
const expectedDutyAssignments = Number(process.env.EXPECTED_DUTY_ASSIGNMENTS);
const pageCampusCount = contract.dataset.pageableCampusCount;
const mealDutyCount = expectedDutyAssignments - 1;

for (const [name, value] of Object.entries({
	BASE_URL,
	PERF_ADMIN_EMAIL: ADMIN_EMAIL,
	PERF_ADMIN_PASSWORD: ADMIN_PASSWORD,
	PERF_DATASET_ID: DATASET_ID,
	PERF_FIXTURE_RUN_ID: FIXTURE_RUN_ID,
	PERF_SOURCE_COMMIT: SOURCE_COMMIT,
	APP_CONTAINER_ID,
	EXPECTED_APP_COMPOSE_SERVICE,
	EXPECTED_APP_IMAGE_ID,
	POSTGRES_CONTAINER_ID,
	EXPECTED_POSTGRES_COMPOSE_SERVICE,
	EXPECTED_POSTGRES_IMAGE_ID,
	REDIS_CONTAINER_ID,
	EXPECTED_REDIS_COMPOSE_SERVICE,
	EXPECTED_REDIS_IMAGE_ID,
	EXPECTED_ACTIVE_MEMBERS: process.env.EXPECTED_ACTIVE_MEMBERS,
	EXPECTED_DUTY_ASSIGNMENTS: process.env.EXPECTED_DUTY_ASSIGNMENTS,
})) {
	if (!value) {
		throw new Error(`${name} is required.`);
	}
}
if (SOURCE_COMMIT !== contract.sourceIdentity.originDevelopCommit) {
	throw new Error('PERF_SOURCE_COMMIT must match the approved source identity.');
}
if (!Number.isSafeInteger(requiredActiveMembers)
	|| requiredActiveMembers !== contract.dataset.requiredActiveMembers
	|| !Number.isSafeInteger(expectedDutyAssignments)
	|| expectedDutyAssignments !== contract.dataset.activeDutyAssignments) {
	throw new Error('Runtime fixture cardinalities must match the approved scenario contract.');
}
if (!/^PERF_[A-Za-z0-9_]+$/.test(DATASET_ID)) {
	throw new Error('PERF_DATASET_ID must be an existing PERF_ identifier.');
}
if (!/^ISSUE195_[A-Za-z0-9_]+$/.test(FIXTURE_RUN_ID)) {
	throw new Error('PERF_FIXTURE_RUN_ID must be a new ISSUE195_ identifier.');
}
guardLocalTarget();

const preLockRuntimeIdentity = captureFixtureRuntimeIdentity();
const { app, postgres, redis } = preLockRuntimeIdentity;
const composeProject = app.composeProject;
const composeService = app.composeService;
if (!/^[A-Za-z0-9_.-]+$/.test(composeProject)) {
	throw new Error('APP_CONTAINER_ID must expose a safe, non-empty actual Docker Compose project label.');
}
if (postgres.composeProject !== composeProject || redis.composeProject !== composeProject) {
	throw new Error('App, PostgreSQL, and Redis Compose projects must match.');
}
const targetIdentity = validateTargetIdentity({
	baseUrl: BASE_URL,
	appContainerId: app.containerId,
	appImageId: app.imageId,
	expectedAppImageId: EXPECTED_APP_IMAGE_ID,
	appComposeService: composeService,
	expectedAppComposeService: EXPECTED_APP_COMPOSE_SERVICE,
	appPublishedPortsJson: JSON.stringify(app.publishedPorts),
	postgresContainerId: postgres.containerId,
	postgresComposeService: postgres.composeService,
	expectedPostgresComposeService: EXPECTED_POSTGRES_COMPOSE_SERVICE,
	postgresImageId: postgres.imageId,
	expectedPostgresImageId: EXPECTED_POSTGRES_IMAGE_ID,
	redisContainerId: redis.containerId,
	redisComposeService: redis.composeService,
	expectedRedisComposeService: EXPECTED_REDIS_COMPOSE_SERVICE,
	redisImageId: redis.imageId,
	expectedRedisImageId: EXPECTED_REDIS_IMAGE_ID,
});
const lockDirectory = `/tmp/faithlog-performance-${composeProject}.lock`;
try {
	fs.mkdirSync(lockDirectory);
} catch (error) {
	throw new Error(`Shared performance runner lock is held: ${lockDirectory}`, { cause: error });
}

try {
	// Re-capture and exact-bind the post-lock immutable identity before login or any API mutation.
	const postLockRuntimeIdentity = captureFixtureRuntimeIdentity();
	if (!isDeepStrictEqual(preLockRuntimeIdentity, postLockRuntimeIdentity)) {
		throw new Error('Fixture post-lock runtime identity changed before login.');
	}
	await prepareFixture();
} finally {
	fs.rmdirSync(lockDirectory);
}

async function prepareFixture() {

const token = await login();
const requester = (await get('/api/v1/users/me', token)).data;
const existingFixtureCampuses = await get(
	`/api/v1/admin/campuses?name=${encodeURIComponent(FIXTURE_RUN_ID)}&page=0&size=100&sort=id,asc`,
	token,
);
if (existingFixtureCampuses.data.totalElements !== 0) {
	throw new Error('fixtureRunId already exists. Use a new ID; existing fixture rows are never changed or deleted.');
}

const activeDatasetUsers = await loadActiveDatasetUsers(token, requester.id);
if (activeDatasetUsers.length !== requiredActiveMembers) {
	throw new Error(`Expected exactly ${requiredActiveMembers} ACTIVE dataset users, found ${activeDatasetUsers.length}.`);
}

const primaryUsers = activeDatasetUsers.slice(0, requiredActiveMembers - 1);
const isolationUser = activeDatasetUsers[requiredActiveMembers - 1];
const campuses = [];
for (let index = 0; index < pageCampusCount; index += 1) {
	const suffix = index === 0
		? 'PRIMARY'
		: index === 1
			? 'ISOLATION'
			: `PAGE_${String(index + 1).padStart(2, '0')}`;
	const response = await post('/api/v1/campuses', token, {
		name: `${FIXTURE_RUN_ID} ${suffix}`,
		region: DATASET_ID,
		description: `Issue #195 additive fixture ${FIXTURE_RUN_ID}`,
	});
	campuses.push(response.data);
}

const primaryCampus = campuses[0];
const isolationCampus = campuses[1];
const primaryMemberships = [];
for (const user of primaryUsers) {
	const response = await addCampusMember(primaryCampus.campusId, user.userId, token);
	primaryMemberships.push(response.data);
}
await addCampusMember(isolationCampus.campusId, isolationUser.userId, token);

for (const user of primaryUsers.slice(0, mealDutyCount)) {
	await assignMealDuty(primaryCampus.campusId, user.userId, token);
}
await assignCoffeeDuty(primaryCampus.campusId, primaryUsers[0].userId, token);

const primaryMembers = (await get(
	`/api/v1/admin/campuses/${primaryCampus.campusId}/members`,
	token,
)).data;
const primaryDuties = (await get(
	`/api/v1/admin/campuses/${primaryCampus.campusId}/duty-assignments?staleOnly=false`,
	token,
)).data;
if (primaryMembers.length !== requiredActiveMembers) {
	throw new Error(`Primary campus must have ${requiredActiveMembers} ACTIVE members; found ${primaryMembers.length}.`);
}
if (primaryDuties.length !== mealDutyCount + 1) {
	throw new Error(`Primary campus must have ${mealDutyCount + 1} ACTIVE duties; found ${primaryDuties.length}.`);
}
if (primaryMembers.some((member) => member.userId === isolationUser.userId)) {
	throw new Error('Isolation sentinel leaked into the primary campus fixture.');
}

const finalRuntimeIdentity = captureFixtureRuntimeIdentity();
if (!isDeepStrictEqual(preLockRuntimeIdentity, finalRuntimeIdentity)) {
	throw new Error('Fixture runtime identity changed before final manifest creation.');
}

const manifest = {
	issue: 195,
	status: 'scenario-ready/not-measured',
	sourceCommit: SOURCE_COMMIT,
	datasetId: DATASET_ID,
	fixtureRunId: FIXTURE_RUN_ID,
		composeProject,
		composeService,
		postgresComposeService: postgres.composeService,
		redisComposeService: redis.composeService,
	targetIdentity,
	runtimeIdentity: preLockRuntimeIdentity,
	createdAt: new Date().toISOString(),
	requesterUserId: requester.id,
	requiredActiveMembers,
	primaryCampusId: primaryCampus.campusId,
	isolationCampusId: isolationCampus.campusId,
	isolationUserId: isolationUser.userId,
	fixtureCampusIds: campuses.map(({ campusId }) => campusId),
	primaryMembershipIds: primaryMemberships.map(({ membershipId }) => membershipId),
	activeDutyAssignments: primaryDuties.length,
	mutationPolicy: 'additive-only; no existing user, membership, duty, or QA row modified/deleted',
};
const outputDirectory = path.join(REPORT_ROOT, DATASET_ID, FIXTURE_RUN_ID);
fs.mkdirSync(outputDirectory, { recursive: true });
const manifestPath = path.join(outputDirectory, 'fixture-manifest.json');
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(JSON.stringify({ manifestPath, ...manifest }, null, 2));
}

function captureFixtureRuntimeIdentity() {
	return {
		app: captureContainer(APP_CONTAINER_ID),
		postgres: captureContainer(POSTGRES_CONTAINER_ID),
		redis: captureContainer(REDIS_CONTAINER_ID),
	};
}

function captureContainer(containerId) {
	const inspect = (format) => execFileSync(
		'docker',
		['inspect', '--format', format, containerId],
		{ encoding: 'utf8' },
	).trim();
	return {
		containerId: inspect('{{.Id}}'),
		name: inspect('{{.Name}}'),
		imageId: inspect('{{.Image}}'),
		startedAt: inspect('{{.State.StartedAt}}'),
		composeProject: inspect('{{ index .Config.Labels "com.docker.compose.project" }}'),
		composeService: inspect('{{ index .Config.Labels "com.docker.compose.service" }}'),
		publishedPorts: JSON.parse(inspect('{{json .NetworkSettings.Ports}}')),
	};
}

async function loadActiveDatasetUsers(adminToken, requesterUserId) {
	const summaries = [];
	let page = 0;
	let totalPages = 1;
	do {
		const response = await get(
			`/api/v1/admin/users?name=${encodeURIComponent(DATASET_ID)}&page=${page}&size=100&sort=id,asc`,
			adminToken,
		);
		summaries.push(...response.data.content);
		totalPages = response.data.totalPages;
		page += 1;
	} while (page < totalPages);
	if (summaries.length !== requiredActiveMembers) {
		throw new Error(`Dataset search must resolve to exactly ${requiredActiveMembers} users; found ${summaries.length}.`);
	}
	const datasetNeedle = DATASET_ID.toLowerCase();
	if (summaries.some((summary) => !summary.name.toLowerCase().includes(datasetNeedle)
		|| !summary.email.toLowerCase().includes(datasetNeedle))) {
		throw new Error('Every dataset user must contain datasetId in both name and email for isolated search cases.');
	}
	if (summaries.some((summary) => summary.role !== 'USER')) {
		throw new Error('Every dataset user must have service role USER for the isolated role-filter case.');
	}

	const active = [];
	for (const summary of summaries) {
		if (summary.userId === requesterUserId) {
			throw new Error('The runtime service admin must not be part of the 1,000-user performance dataset.');
		}
		const detail = (await get(`/api/v1/admin/users/${summary.userId}`, adminToken)).data;
		if (!detail.isActive) {
			throw new Error(`Dataset user ${summary.userId} is inactive; all 1,000 users must be ACTIVE.`);
		}
		active.push(summary);
	}
	return active;
}

async function addCampusMember(campusId, userId, token) {
	return post(`/api/v1/admin/campuses/${campusId}/members`, token, { userId });
}

async function assignMealDuty(campusId, userId, token) {
	return post(`/api/v1/admin/campuses/${campusId}/duty-assignments/meal`, token, { userId });
}

async function assignCoffeeDuty(campusId, userId, token) {
	return put(`/api/v1/admin/campuses/${campusId}/duty-assignments/coffee`, token, { userId });
}

async function login() {
	const response = await request('POST', '/api/v1/auth/login', null, {
		email: ADMIN_EMAIL,
		password: ADMIN_PASSWORD,
	});
	return response.data.accessToken;
}

async function get(pathName, token) {
	return request('GET', pathName, token);
}

async function post(pathName, token, body) {
	return request('POST', pathName, token, body);
}

async function put(pathName, token, body) {
	return request('PUT', pathName, token, body);
}

async function request(method, pathName, token, body) {
	const headers = { 'Content-Type': 'application/json' };
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	const response = await fetch(`${BASE_URL}${pathName}`, {
		method,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	const parsed = text ? JSON.parse(text) : null;
	if (!response.ok || parsed?.success === false) {
		throw new Error(`${method} ${pathName} failed: status=${response.status} body=${text}`);
	}
	return parsed;
}

function guardLocalTarget() {
	const localTarget = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal|faithlog-backend|app)(?::\d+)?($|\/)/.test(BASE_URL);
	if (!localTarget) {
		throw new Error('Issue #195 fixture preparation is local Docker only.');
	}
}
