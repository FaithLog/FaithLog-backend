import {readFile} from 'node:fs/promises';
import {
	buildArchiveCorrectnessProbes,
	buildDutyScopeProbes,
	buildRequestCases,
	encodeQuery,
	PAGE_DEFAULTS,
	validateArchiveProbeResponse,
	validateCaseResponseSemantics,
	validateDutyAggregateResponse,
	validateDutyMemberDetailResponse,
	validateExpectationsManifest,
} from './scenario-definition.mjs';

const baseUrl = required('BASE_URL').replace(/\/$/, '');
const campusId = Number(required('CAMPUS_ID'));
const adminToken = required('PERF_ADMIN_ACCESS_TOKEN');
const dutyToken = required('PERF_DUTY_ACCESS_TOKEN');
const expectations = JSON.parse(await readFile(required('EXPECTATIONS_PATH'), 'utf8'));

validateExpectationsManifest(expectations, campusId);
await verifyIdentity(adminToken, expectations.requesterUserId, 'admin');
await verifyIdentity(dutyToken, expectations.dutyUserId, 'duty');
await verifyMeasuredCases();
await verifyArchiveCorrectness();
await verifyDutyScope();
await verifyCrossCampusIsolation();

if (expectations.sourceDuplicateCount !== 0) {
	throw new Error('source duplicate invariant failed before k6 execution.');
}

async function verifyMeasuredCases() {
	for (const requestCase of buildRequestCases(expectations, campusId)) {
		const {response, body} = await get(requestCase.path, requestCase.query, adminToken);
		if (response.status !== 200 || !validateCaseResponseSemantics(body, requestCase, expectations)) {
			throw new Error(`Measured case correctness failed: ${requestCase.name}, status=${response.status}.`);
		}
	}
}

async function verifyArchiveCorrectness() {
	for (const probe of buildArchiveCorrectnessProbes(expectations, campusId)) {
		const {response, body} = await get(probe.path, probe.query, adminToken);
		if (response.status !== 200
			|| !validateArchiveProbeResponse(body, probe, expectations.archiveCases)) {
			throw new Error(`Archive cutoff correctness failed: ${probe.name}, status=${response.status}.`);
		}
	}
}

async function verifyDutyScope() {
	for (const probe of buildDutyScopeProbes(expectations, campusId)) {
		const {response, body} = await get(probe.path, probe.query, dutyToken);
		const expected = expectations.dutyScope[probe.name];
		if (response.status !== expected.status && expected.status !== undefined) {
			throw new Error(`Duty scope status failed: ${probe.name}, status=${response.status}.`);
		}
		if (probe.name === 'duty_owned_account_visible'
			&& (response.status !== 200 || !validateDutyAggregateResponse(body, probe, expectations.dutyScope))) {
			throw new Error('Duty owned-account aggregate correctness failed.');
		}
		if (probe.name === 'duty_member_detail_owned_only'
			&& (response.status !== 200 || !validateDutyMemberDetailResponse(body, expected))) {
			throw new Error('Duty member-detail owned-account correctness failed.');
		}
	}
}

async function verifyCrossCampusIsolation() {
	const path = `/api/v1/admin/campuses/${campusId}/charges`;
	const {response} = await get(path, {
		...PAGE_DEFAULTS,
		paymentCategory: 'COFFEE',
		paymentAccountId: expectations.crossCampusAccountId,
	}, adminToken);
	if (response.status !== 404) {
		throw new Error(`cross-campus correctness failed: expected 404, received ${response.status}.`);
	}
}

async function verifyIdentity(token, expectedUserId, label) {
	const response = await fetch(`${baseUrl}/api/v1/users/me`, {headers: authHeaders(token)});
	const body = await parseJson(response);
	if (response.status !== 200 || body?.success !== true || body?.data?.id !== expectedUserId) {
		throw new Error(`${label} token identity does not match the approved requester.`);
	}
}

async function get(path, query, token) {
	const response = await fetch(`${baseUrl}${path}?${encodeQuery(query)}`, {headers: authHeaders(token)});
	return {response, body: await parseJson(response)};
}

function authHeaders(token) {
	return {Authorization: `Bearer ${token}`};
}

async function parseJson(response) {
	try {
		return await response.json();
	} catch (_error) {
		return {};
	}
}

function required(name) {
	const value = process.env[name];
	if (!value) {
		throw new Error(`${name} is required for preflight.`);
	}
	return value;
}
