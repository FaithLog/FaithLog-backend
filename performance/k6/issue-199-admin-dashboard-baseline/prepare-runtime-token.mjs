import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const BASE_URL = process.env.BASE_URL?.replace(/\/$/, '');
const INPUT_MANIFEST = process.env.INPUT_MANIFEST;
const DATASET_MODES_SOURCE = process.env.DATASET_MODES;
const DATASET_MODES = DATASET_MODES_SOURCE
	? DATASET_MODES_SOURCE.split(',').map((mode) => mode.trim()).filter(Boolean)
	: [];
const PERF_ADMIN_EMAIL = process.env.PERF_ADMIN_EMAIL;
const PERF_ADMIN_PASSWORD = process.env.PERF_ADMIN_PASSWORD;

if (!BASE_URL || !INPUT_MANIFEST || DATASET_MODES.length === 0 || !PERF_ADMIN_EMAIL || !PERF_ADMIN_PASSWORD) {
	throw new Error('BASE_URL, INPUT_MANIFEST, DATASET_MODES, PERF_ADMIN_EMAIL, and PERF_ADMIN_PASSWORD are required at runtime.');
}
if (!/^http:\/\/(127\.0\.0\.1|\[::1\])(?::\d+)?$/.test(BASE_URL)) {
	throw new Error('Issue #199 runtime token preparation is restricted to the local faithlog-latest target.');
}

const manifest = JSON.parse(fs.readFileSync(path.resolve(INPUT_MANIFEST), 'utf8'));
assert.equal(manifest.issue, 199);
const datasets = DATASET_MODES.map((mode) => {
	assert.ok(['empty', 'small', 'thousand'].includes(mode), `Unsupported dataset mode: ${mode}`);
	const dataset = manifest.modes?.[mode];
	assert.ok(dataset, `Missing dataset mode: ${mode}`);
	return dataset;
});

const login = await request('/api/v1/auth/login', {
	method: 'POST',
	body: {email: PERF_ADMIN_EMAIL, password: PERF_ADMIN_PASSWORD},
});
assert.equal(login.status, 200, 'frontend login must succeed');
const token = login.body.data?.accessToken;
assert.ok(token, 'frontend login must return an access token');

const [currentUser, campuses] = await Promise.all([
	request('/api/v1/users/me', {token}),
	request('/api/v1/campuses/me', {token}),
]);
assert.equal(currentUser.status, 200, 'frontend users/me must succeed');
assert.equal(currentUser.body.success, true, 'frontend users/me must use the success envelope');
assert.equal(campuses.status, 200, 'frontend campuses/me must succeed');
assert.equal(campuses.body.success, true, 'frontend campuses/me must use the success envelope');
for (const dataset of datasets) {
	const managerRoles = new Set(['MINISTER', 'ELDER', 'CAMPUS_LEADER']);
	assert.ok(
		(campuses.body.data || []).some(
			(campus) => campus.campusId === dataset.campusId
				&& campus.status === 'ACTIVE' && managerRoles.has(campus.campusRole),
		),
		`campuses/me must contain ACTIVE campus manager role for measured campus ${dataset.campusId}`,
	);
}

// stdout is captured directly into a shell variable. Never redirect this value to a report or log.
process.stdout.write(token);

async function request(requestPath, {method = 'GET', token: accessToken, body} = {}) {
	const response = await fetch(`${BASE_URL}${requestPath}`, {
		method,
		headers: {
			...(accessToken ? {Authorization: `Bearer ${accessToken}`} : {}),
			...(body ? {'Content-Type': 'application/json'} : {}),
		},
		...(body ? {body: JSON.stringify(body)} : {}),
	});
	let responseBody = {};
	try {
		responseBody = await response.json();
	} catch (error) {
		responseBody = {};
	}
	return {status: response.status, body: responseBody};
}
