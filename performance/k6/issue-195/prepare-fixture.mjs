import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { validateTargetIdentity } from './validate-target-identity.mjs';

const BASE_URL = process.env.BASE_URL?.replace(/\/$/, '');
const ADMIN_EMAIL = process.env.PERF_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.PERF_ADMIN_PASSWORD;
const DATASET_ID = process.env.PERF_DATASET_ID;
const FIXTURE_RUN_ID = process.env.PERF_FIXTURE_RUN_ID;
const APP_CONTAINER_ID = process.env.APP_CONTAINER_ID;
const EXPECTED_APP_COMPOSE_SERVICE = process.env.EXPECTED_APP_COMPOSE_SERVICE;
const REPORT_ROOT = process.env.PERF_REPORT_ROOT
	|| path.join('performance', 'k6', 'issue-195', 'reports');
const requiredActiveMembers = 1000;
const pageCampusCount = 25;
const mealDutyCount = 100;

for (const [name, value] of Object.entries({
	BASE_URL,
	PERF_ADMIN_EMAIL: ADMIN_EMAIL,
	PERF_ADMIN_PASSWORD: ADMIN_PASSWORD,
	PERF_DATASET_ID: DATASET_ID,
	PERF_FIXTURE_RUN_ID: FIXTURE_RUN_ID,
	APP_CONTAINER_ID,
	EXPECTED_APP_COMPOSE_SERVICE,
})) {
	if (!value) {
		throw new Error(`${name} is required.`);
	}
}
if (!/^PERF_[A-Za-z0-9_]+$/.test(DATASET_ID)) {
	throw new Error('PERF_DATASET_ID must be an existing PERF_ identifier.');
}
if (!/^ISSUE195_[A-Za-z0-9_]+$/.test(FIXTURE_RUN_ID)) {
	throw new Error('PERF_FIXTURE_RUN_ID must be a new ISSUE195_ identifier.');
}
guardLocalTarget();

const composeProject = execFileSync(
	'docker',
	['inspect', '--format', '{{ index .Config.Labels "com.docker.compose.project" }}', APP_CONTAINER_ID],
	{ encoding: 'utf8' },
).trim();
const composeService = execFileSync(
	'docker',
	['inspect', '--format', '{{ index .Config.Labels "com.docker.compose.service" }}', APP_CONTAINER_ID],
	{ encoding: 'utf8' },
).trim();
const publishedPortsJson = execFileSync(
	'docker',
	['inspect', '--format', '{{json .NetworkSettings.Ports}}', APP_CONTAINER_ID],
	{ encoding: 'utf8' },
).trim();
if (!/^[A-Za-z0-9_.-]+$/.test(composeProject)) {
	throw new Error('APP_CONTAINER_ID must expose a safe, non-empty actual Docker Compose project label.');
}
const targetIdentity = validateTargetIdentity({
	baseUrl: BASE_URL,
	appContainerId: APP_CONTAINER_ID,
	appComposeService: composeService,
	expectedAppComposeService: EXPECTED_APP_COMPOSE_SERVICE,
	appPublishedPortsJson: publishedPortsJson,
});
const lockDirectory = `/tmp/faithlog-performance-${composeProject}.lock`;
try {
	fs.mkdirSync(lockDirectory);
} catch (error) {
	throw new Error(`Shared performance runner lock is held: ${lockDirectory}`, { cause: error });
}

try {
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

const manifest = {
	issue: 195,
	status: 'scenario-ready/not-measured',
	datasetId: DATASET_ID,
	fixtureRunId: FIXTURE_RUN_ID,
	composeProject,
	composeService,
	targetIdentity,
	createdAt: new Date().toISOString(),
	requesterUserId: requester.id,
	requiredActiveMembers: 1000,
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
