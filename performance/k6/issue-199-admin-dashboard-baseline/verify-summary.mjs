import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:28080').replace(/\/$/, '');
const INPUT_MANIFEST = process.env.INPUT_MANIFEST;
const DATASET_MODE = process.env.DATASET_MODE || 'thousand';
const PERF_ACCESS_TOKEN = process.env.PERF_ACCESS_TOKEN;

if (!INPUT_MANIFEST || !PERF_ACCESS_TOKEN) {
	throw new Error('INPUT_MANIFEST and the runtime-only PERF_ACCESS_TOKEN are required.');
}

if (!/^https?:\/\/(127\.0\.0\.1|localhost|\[::1\]|host\.docker\.internal)(?::\d+)?$/.test(BASE_URL)) {
	throw new Error('Issue #199 verification is restricted to the local faithlog-latest target.');
}

const manifestPath = path.resolve(INPUT_MANIFEST);
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const dataset = manifest.modes?.[DATASET_MODE];
assert.equal(manifest.issue, 199);
assert.ok(manifest.datasetId);
assert.ok(dataset, `Missing dataset mode: ${DATASET_MODE}`);
assert.equal(dataset.mode, DATASET_MODE);
assert.ok(dataset.fixtureRunId);
assert.ok(dataset.campusId);
assert.ok(dataset.isolationCampusId);
assert.ok(dataset.weekStartDate);

validateFixtureReferences();
validateExpectedArithmetic();

const summary = await request(
	`/api/v1/admin/campuses/${dataset.campusId}/dashboard/summary?weekStartDate=${dataset.weekStartDate}`,
	{token: PERF_ACCESS_TOKEN},
);
assert.equal(summary.status, 200, 'dashboard summary must succeed');
assert.equal(summary.body.success, true, 'dashboard summary must use the success envelope');
assert.deepEqual(summary.body.data, toExpectedResponse(dataset.expected));

const isolation = await request(
	`/api/v1/admin/campuses/${dataset.isolationCampusId}/dashboard/summary?weekStartDate=${dataset.weekStartDate}`,
	{token: PERF_ACCESS_TOKEN},
);
assert.equal(isolation.status, 403, 'other-campus dashboard must be forbidden for the campus-scoped manager actor');
assert.equal(isolation.body.code, 'ADMIN_DASHBOARD_ACCESS_FORBIDDEN');

process.stdout.write(`${JSON.stringify({
	status: 'correctness-verified',
	datasetId: manifest.datasetId,
	fixtureRunId: dataset.fixtureRunId,
	datasetMode: DATASET_MODE,
	campusId: dataset.campusId,
	weekStartDate: dataset.weekStartDate,
	statusBasis: dataset.expected.charges.statusBasis,
	pollResponseCounts: dataset.expected.pollResponseCounts,
	missingResponseCount: dataset.expected.polls.missingResponseCount,
	submittedCount: dataset.expected.devotion.submittedCount,
	missingCount: dataset.expected.devotion.missingCount,
	campusIsolation: '403 ADMIN_DASHBOARD_ACCESS_FORBIDDEN',
}, null, 2)}\n`);

function validateFixtureReferences() {
	if (DATASET_MODE !== 'thousand') {
		return;
	}
	assert.equal(dataset.expected.members.activeCount, 1000);
	for (const domain of ['devotion', 'penalty', 'coffee', 'meal', 'poll', 'prayer']) {
		const reference = dataset.fixtureReferences?.[domain];
		assert.ok(reference?.fixtureRunId, `${domain} fixtureRunId is required`);
		assert.ok(reference?.manifestPath, `${domain} manifestPath is required`);
		const referencedPath = path.resolve(path.dirname(manifestPath), reference.manifestPath);
		assert.ok(fs.existsSync(referencedPath), `${domain} fixture manifest does not exist: ${referencedPath}`);
		const referencedManifest = JSON.parse(fs.readFileSync(referencedPath, 'utf8'));
		assert.equal(referencedManifest.datasetId, manifest.datasetId, `${domain} datasetId mismatch`);
		assert.equal(referencedManifest.fixtureRunId, reference.fixtureRunId, `${domain} fixtureRunId mismatch`);
	}
}

function validateExpectedArithmetic() {
	const expected = dataset.expected;
	assert.deepEqual(expected.charges.statusBasis, ['UNPAID']);
	assert.deepEqual(
		expected.charges.byCategory.map((item) => item.paymentCategory),
		['PENALTY', 'COFFEE'],
	);
	assert.equal(
		expected.charges.byCategory.reduce((sum, item) => sum + item.unpaidAmount, 0),
		expected.charges.unpaidAmount,
		'PENALTY + COFFEE must equal the dashboard unpaid total',
	);
	assert.equal(
		expected.devotion.submittedCount + expected.devotion.missingCount,
		expected.members.activeCount,
		'submitted + missing must equal ACTIVE members',
	);
	assert.equal(expected.pollResponseCounts.length, expected.polls.openCount);
	const derivedMissing = expected.pollResponseCounts.reduce(
		(sum, poll) => sum + Math.max(0, expected.members.activeCount - poll.responseCount),
		0,
	);
	assert.equal(derivedMissing, expected.polls.missingResponseCount);
}

function toExpectedResponse(expected) {
	return {
		campus: expected.campus,
		members: expected.members,
		devotion: expected.devotion,
		charges: {
			unpaidAmount: expected.charges.unpaidAmount,
			unpaidMemberCount: expected.charges.unpaidMemberCount,
			byCategory: expected.charges.byCategory,
		},
		polls: expected.polls,
	};
}

async function request(requestPath, {method = 'GET', token, body} = {}) {
	const response = await fetch(`${BASE_URL}${requestPath}`, {
		method,
		headers: {
			...(token ? {Authorization: `Bearer ${token}`} : {}),
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
