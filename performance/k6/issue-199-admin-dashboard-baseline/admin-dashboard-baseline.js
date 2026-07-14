import http from 'k6/http';
import {check, fail} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:28080').replace(/\/$/, '');
const INPUT_MANIFEST = __ENV.INPUT_MANIFEST;
const DATASET_MODE = __ENV.DATASET_MODE || 'thousand';
const PHASE = __ENV.PHASE || 'measured';
const VUS = Number(__ENV.VUS || 0);
const DURATION = __ENV.DURATION || '0s';
const PERF_ACCESS_TOKEN = __ENV.PERF_ACCESS_TOKEN;

if (!INPUT_MANIFEST) {
	throw new Error('INPUT_MANIFEST is required.');
}

const inputManifest = JSON.parse(open(INPUT_MANIFEST));
const dataset = inputManifest.modes?.[DATASET_MODE];

export const adminDashboardDuration = new Trend('admin_dashboard_duration', true);
export const adminDashboardRequests = new Counter('admin_dashboard_requests');
export const adminDashboardFailureRate = new Rate('admin_dashboard_failure_rate');

export const options = {
	scenarios: {
		admin_dashboard_summary: {
			executor: 'constant-vus',
			vus: VUS,
			duration: DURATION,
		},
	},
	summaryTrendStats: ['p(50)', 'p(95)', 'p(99)', 'max'],
	tags: {
		issue: '199',
		phase: PHASE,
		dataset_mode: DATASET_MODE,
	},
	thresholds: {
		admin_dashboard_failure_rate: ['rate==0'],
	},
};

export function setup() {
	guardLocalTarget();
	validateInput();
	return {token: PERF_ACCESS_TOKEN};
}

export default function ({token}) {
	const response = http.get(
		`${BASE_URL}/api/v1/admin/campuses/${dataset.campusId}/dashboard/summary?weekStartDate=${dataset.weekStartDate}`,
		authParams(token, {name: 'admin_dashboard_summary', measured: 'true'}),
	);
	adminDashboardDuration.add(response.timings.duration);
	adminDashboardRequests.add(1);

	const body = parseJson(response);
	const data = body.data || {};
	const expected = dataset.expected;
	const ok = check(response, {
		'admin dashboard status is 200': (result) => result.status === 200,
		'admin dashboard success envelope': () => body.success === true,
		'campus identity is exact': () => deepEqual(data.campus, expected.campus),
		'members.activeCount is exact': () => data.members?.activeCount === expected.members.activeCount,
		'members.inactiveCount is exact': () => data.members?.inactiveCount === expected.members.inactiveCount,
		'members.adminCount is exact': () => data.members?.adminCount === expected.members.adminCount,
		'devotion.weekStartDate is exact': () => data.devotion?.weekStartDate === expected.devotion.weekStartDate,
		'devotion.submittedCount is exact': () => data.devotion?.submittedCount === expected.devotion.submittedCount,
		'devotion.missingCount is exact': () => data.devotion?.missingCount === expected.devotion.missingCount,
		'devotion.submitRate is exact': () => data.devotion?.submitRate === expected.devotion.submitRate,
		'charges.unpaidAmount is exact': () => data.charges?.unpaidAmount === expected.charges.unpaidAmount,
		'charges.unpaidMemberCount is exact': () => data.charges?.unpaidMemberCount === expected.charges.unpaidMemberCount,
		'charges.byCategory has exact PENALTY and COFFEE totals': () => deepEqual(
			data.charges?.byCategory,
			expected.charges.byCategory,
		),
		'polls.openCount is exact': () => data.polls?.openCount === expected.polls.openCount,
		'polls.recentlyClosedCount is exact': () => data.polls?.recentlyClosedCount === expected.polls.recentlyClosedCount,
		'polls.missingResponseCount is exact': () => data.polls?.missingResponseCount === expected.polls.missingResponseCount,
		'polls.recentlyClosedDays is exact': () => data.polls?.recentlyClosedDays === expected.polls.recentlyClosedDays,
	});
	adminDashboardFailureRate.add(!ok);
	if (!ok) {
		fail(`Dashboard correctness failed for ${inputManifest.datasetId}/${dataset.fixtureRunId}/${DATASET_MODE}.`);
	}
}

function validateInput() {
	if (!PERF_ACCESS_TOKEN) {
		fail('PERF_ACCESS_TOKEN must be prepared in runtime memory before k6 starts.');
	}
	if (!Number.isInteger(VUS) || VUS < 1 || !/^\d+(?:\.\d+)?(?:ms|s|m|h)$/.test(DURATION) || DURATION === '0s') {
		fail('VUS and DURATION must be explicitly set to user-approved positive values.');
	}
	if (inputManifest.issue !== 199 || !inputManifest.datasetId || !dataset) {
		fail('Input manifest must identify Issue #199, datasetId, and the requested dataset mode.');
	}
	if (dataset.mode !== DATASET_MODE || !dataset.fixtureRunId || !dataset.campusId
		|| !dataset.isolationCampusId || !dataset.weekStartDate || !dataset.expected) {
		fail('Dataset mode is missing its fixtureRunId, campus, week, isolation, or expected contract.');
	}
	if (DATASET_MODE === 'thousand') {
		if (dataset.expected.members.activeCount !== 1000) {
			fail('The thousand mode must reference exactly 1,000 ACTIVE members.');
		}
		for (const domain of ['devotion', 'penalty', 'coffee', 'meal', 'poll', 'prayer']) {
			const reference = dataset.fixtureReferences?.[domain];
			if (!reference?.fixtureRunId || !reference?.manifestPath) {
				fail(`The thousand mode must reference the shared ${domain} fixture manifest.`);
			}
		}
	}
	if (!deepEqual(dataset.expected.charges.statusBasis, ['UNPAID'])) {
		fail('Dashboard charge correctness must use the UNPAID status basis.');
	}
	if (!deepEqual(
		dataset.expected.charges.byCategory.map((item) => item.paymentCategory),
		['PENALTY', 'COFFEE'],
	)) {
		fail('Dashboard categories must be exactly PENALTY and COFFEE in response order.');
	}
}

function authParams(token, tags) {
	return {
		headers: {Authorization: `Bearer ${token}`},
		tags,
	};
}

function parseJson(response) {
	try {
		return response.json();
	} catch (error) {
		return {};
	}
}

function deepEqual(actual, expected) {
	return JSON.stringify(actual) === JSON.stringify(expected);
}

function guardLocalTarget() {
	if (!/^https?:\/\/(127\.0\.0\.1|localhost|\[::1\]|host\.docker\.internal)(?::\d+)?$/.test(BASE_URL)) {
		fail('Issue #199 is local faithlog-latest only; remote targets are prohibited.');
	}
}
