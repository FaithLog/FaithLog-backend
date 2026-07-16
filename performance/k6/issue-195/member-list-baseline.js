import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import contract from './scenario-contract.json';

const BASE_URL = __ENV.BASE_URL?.replace(/\/$/, '');
const SCENARIO = __ENV.SCENARIO;
const CASE = __ENV.CASE;
const DATASET_ID = __ENV.PERF_DATASET_ID;
const FIXTURE_RUN_ID = __ENV.PERF_FIXTURE_RUN_ID;
const EXECUTION_RUN_ID = __ENV.PERF_EXECUTION_RUN_ID;
const CAMPUS_ID = __ENV.CAMPUS_ID;
const ISOLATION_CAMPUS_ID = __ENV.ISOLATION_CAMPUS_ID;
const ISOLATION_USER_ID = __ENV.ISOLATION_USER_ID;
const EXPECTED_ACTIVE_MEMBERS = Number(__ENV.EXPECTED_ACTIVE_MEMBERS || 1000);
const EXPECTED_DUTY_ASSIGNMENTS = Number(__ENV.EXPECTED_DUTY_ASSIGNMENTS || 101);
const VUS = Number(__ENV.VUS);
const DURATION = __ENV.DURATION;
const MAX_FAILURE_RATE_INPUT = __ENV.MAX_FAILURE_RATE;
const MAX_FAILURE_RATE = Number(MAX_FAILURE_RATE_INPUT);
const PERF_ACCESS_TOKEN = __ENV.PERF_ACCESS_TOKEN;

const endpoint = contract.endpoints.find(({ key }) => key === SCENARIO);
const scenarioCase = endpoint?.cases.find(({ key }) => key === CASE);
const metricName = `issue195_${sanitize(SCENARIO)}_${sanitize(CASE)}`;
const endpointDuration = new Trend(`${metricName}_duration`, true);
const endpointRequests = new Counter(`${metricName}_requests`);
const endpointFailures = new Rate(`${metricName}_failures`);

export const options = {
	scenarios: {
		issue_195_single_endpoint: {
			executor: 'constant-vus',
			vus: VUS,
			duration: DURATION,
		},
	},
	summaryTrendStats: ['p(50)', 'p(95)', 'p(99)', 'max'],
	thresholds: {
		[`${metricName}_failures`]: [`rate<=${MAX_FAILURE_RATE}`],
	},
};

export function setup() {
	guardLocalTarget();
	for (const [name, value] of Object.entries({
		SCENARIO,
		CASE,
		PERF_DATASET_ID: DATASET_ID,
		PERF_FIXTURE_RUN_ID: FIXTURE_RUN_ID,
		PERF_EXECUTION_RUN_ID: EXECUTION_RUN_ID,
		CAMPUS_ID,
		ISOLATION_CAMPUS_ID,
		ISOLATION_USER_ID,
		PERF_ACCESS_TOKEN,
		DURATION,
		MAX_FAILURE_RATE_INPUT,
	})) {
		if (!value) {
			fail(`${name} is required.`);
		}
	}
	if (!endpoint || !scenarioCase) {
		fail(`Unknown SCENARIO/CASE: ${SCENARIO}/${CASE}`);
	}
	if (Number(CAMPUS_ID) === Number(ISOLATION_CAMPUS_ID)) {
		fail('CAMPUS_ID and ISOLATION_CAMPUS_ID must be different.');
	}
	if (!Number.isInteger(VUS) || VUS <= 0) {
		fail('VUS must be a positive runtime integer.');
	}
	if (MAX_FAILURE_RATE !== 0) {
		fail('MAX_FAILURE_RATE must be explicitly approved as 0 for exact correctness.');
	}
	return { token: PERF_ACCESS_TOKEN };
}

export default function ({ token }) {
	const path = buildPath();
	const response = http.get(`${BASE_URL}${path}`, {
		headers: {
			Authorization: `Bearer ${token}`,
			'X-FaithLog-Performance-Run': EXECUTION_RUN_ID,
		},
		tags: { name: metricName, issue: '195', endpoint: SCENARIO, case: CASE },
	});
	endpointDuration.add(response.timings.duration);
	endpointRequests.add(1);

	const body = parseJson(response);
	const valid = check(response, {
		[`${metricName} status is 200`]: (res) => res.status === 200,
		[`${metricName} success envelope`]: () => body.success === true,
		[`${metricName} result shape`]: () => validateResultShape(body),
		[`${metricName} stable order`]: () => validateStableOrder(body),
		[`${metricName} page metadata`]: () => validatePageMetadata(body),
		[`${metricName} filter contract`]: () => validateFilters(body),
		[`${metricName} exact cardinality`]: () => validateExactCardinality(body),
		[`${metricName} campus isolation`]: () => validateCampusIsolation(body),
	});
	endpointFailures.add(!valid);
	if (!valid) {
		fail(`${SCENARIO}/${CASE} correctness failed: status=${response.status} body=${response.body}`);
	}
}

function buildPath() {
	const basePath = endpoint.path.replace('{campusId}', encodeURIComponent(CAMPUS_ID));
	const entries = Object.entries(scenarioCase.query || {}).map(([key, rawValue]) => {
		const value = String(rawValue)
			.replaceAll('${datasetId}', DATASET_ID)
			.replaceAll('${fixtureRunId}', FIXTURE_RUN_ID);
		return `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
	});
	return entries.length === 0 ? basePath : `${basePath}?${entries.join('&')}`;
}

function validateResultShape(body) {
	if (SCENARIO === 'admin_users' || SCENARIO === 'admin_campuses') {
		return Array.isArray(body.data?.content);
	}
	if (!Array.isArray(body.data)) {
		return false;
	}
	if (SCENARIO === 'campus_members') {
		return body.data.length === EXPECTED_ACTIVE_MEMBERS
			&& body.data.every((member) => member.status === 'ACTIVE');
	}
	return body.data.length === EXPECTED_DUTY_ASSIGNMENTS
		&& body.data.every((assignment) => assignment.isActive === true);
}

function validateStableOrder(body) {
	const content = pageOrList(body);
	if (!Array.isArray(content)) {
		return false;
	}
	const field = SCENARIO === 'admin_users'
		? 'userId'
		: SCENARIO === 'admin_campuses'
			? 'campusId'
			: SCENARIO === 'campus_members'
				? 'membershipId'
				: 'assignmentId';
	return content.every((item, index) => index === 0 || Number(content[index - 1][field]) < Number(item[field]));
}

function validatePageMetadata(body) {
	if (SCENARIO !== 'admin_users' && SCENARIO !== 'admin_campuses') {
		return true;
	}
	const expectedPage = Number(scenarioCase.query.page);
	const expectedSize = Number(scenarioCase.query.size);
	return body.data !== null
		&& typeof body.data === 'object'
		&& Array.isArray(body.data.content)
		&& body.data.page === expectedPage
		&& body.data.size === expectedSize
		&& Number.isInteger(body.data.totalElements)
		&& Number.isInteger(body.data.totalPages)
		&& body.data.content.length > 0
		&& body.data.content.length <= expectedSize;
}

function validateCampusIsolation(body) {
	if (SCENARIO === 'campus_members' || SCENARIO === 'duty_assignments') {
		return Array.isArray(body.data)
			&& body.data.every((item) => Number(item.campusId) === Number(CAMPUS_ID))
			&& body.data.every((item) => Number(item.userId) !== Number(ISOLATION_USER_ID));
	}
	if (SCENARIO === 'admin_campuses') {
		return Array.isArray(body.data?.content)
			&& body.data.content.every((campus) => campus.region === DATASET_ID);
	}
	return true;
}

function validateFilters(body) {
	const content = pageOrList(body);
	if (!Array.isArray(content)) {
		return false;
	}
	if (SCENARIO === 'admin_users') {
		const datasetNeedle = DATASET_ID.toLowerCase();
		if (!content.every((user) => user.name.toLowerCase().includes(datasetNeedle)
			&& user.email.toLowerCase().includes(datasetNeedle))) {
			return false;
		}
		return CASE !== 'role_filter' || content.every((user) => user.role === 'USER');
	}
	if (SCENARIO === 'admin_campuses') {
		return content.every((campus) => campus.status === 'ACTIVE' && campus.name.includes(FIXTURE_RUN_ID));
	}
	return true;
}

function validateExactCardinality(body) {
	if (SCENARIO === 'admin_users') {
		return body.data?.totalElements === 1000;
	}
	if (SCENARIO === 'admin_campuses') {
		const expected = CASE === 'active_search' ? 1 : contract.dataset.pageableCampusCount;
		return body.data?.totalElements === expected;
	}
	if (SCENARIO === 'campus_members') {
		return Array.isArray(body.data) && body.data.length === EXPECTED_ACTIVE_MEMBERS;
	}
	return Array.isArray(body.data) && body.data.length === EXPECTED_DUTY_ASSIGNMENTS;
}

function pageOrList(body) {
	return SCENARIO === 'admin_users' || SCENARIO === 'admin_campuses'
		? body.data?.content
		: body.data;
}

function parseJson(response) {
	try {
		return response.json();
	} catch (error) {
		return {};
	}
}

function sanitize(value) {
	return String(value || 'unset').replace(/[^a-zA-Z0-9_]/g, '_');
}

function guardLocalTarget() {
	const localTarget = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal|faithlog-backend|app)(?::\d+)?($|\/)/.test(BASE_URL);
	if (!localTarget) {
		fail('Issue #195 baseline is local Docker only. Remote targets are blocked.');
	}
}
