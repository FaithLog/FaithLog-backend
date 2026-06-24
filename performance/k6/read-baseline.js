import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const VUS = Number(__ENV.VUS || 30);
const DURATION = __ENV.DURATION || '5m';
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 1);
const MAX_FAILURE_RATE = Number(__ENV.MAX_FAILURE_RATE || 0.01);

const PERF_EMAIL = __ENV.PERF_EMAIL;
const PERF_PASSWORD = __ENV.PERF_PASSWORD;
const WEEK_START_DATE = __ENV.WEEK_START_DATE || '2026-06-22';
const YEAR = __ENV.YEAR || '2026';
const MONTH = __ENV.MONTH || '6';
const CAMPUS_ID = __ENV.CAMPUS_ID;
const POLL_ID = __ENV.POLL_ID;

const INCLUDE = new Set(
	(__ENV.INCLUDE || 'auth,campuses')
		.split(',')
		.map((name) => name.trim())
		.filter(Boolean)
);

const endpointDurations = {
	health: new Trend('endpoint_health', true),
	auth_login: new Trend('endpoint_auth_login', true),
	setup_campuses_me: new Trend('endpoint_setup_campuses_me', true),
	campuses_me: new Trend('endpoint_campuses_me', true),
	campus_detail: new Trend('endpoint_campus_detail', true),
	admin_campuses: new Trend('endpoint_admin_campuses', true),
	admin_dashboard_summary: new Trend('endpoint_admin_dashboard_summary', true),
	devotion_weekly_read: new Trend('endpoint_devotion_weekly_read', true),
	devotion_monthly_summary: new Trend('endpoint_devotion_monthly_summary', true),
	billing_my_charges: new Trend('endpoint_billing_my_charges', true),
	billing_my_summary: new Trend('endpoint_billing_my_summary', true),
	poll_list: new Trend('endpoint_poll_list', true),
	poll_detail: new Trend('endpoint_poll_detail', true),
	poll_results: new Trend('endpoint_poll_results', true),
	prayer_weekly_board: new Trend('endpoint_prayer_weekly_board', true),
};

export const options = {
	scenarios: {
		read_baseline: {
			executor: 'constant-vus',
			vus: VUS,
			duration: DURATION,
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
	thresholds: {
		http_req_failed: [`rate<${MAX_FAILURE_RATE}`],
	},
};

export function setup() {
	guardTarget();
	if (requiresAuth() && (!PERF_EMAIL || !PERF_PASSWORD)) {
		fail('PERF_EMAIL and PERF_PASSWORD are required.');
	}

	if (!requiresAuth()) {
		return { token: null, campusId: null };
	}

	const loginResponse = login();
	const token = loginResponse.data.accessToken;
	const campusId = CAMPUS_ID || firstCampusId(token);
	if (!campusId && requiresCampus()) {
		fail('CAMPUS_ID is required for campus-dependent read scenarios. Provide CAMPUS_ID or use includes that do not require campus data.');
	}
	return { token, campusId };
}

export default function (data) {
	let token = data.token;

	if (INCLUDE.has('health')) {
		group('health: status', () => {
			getPublic('/api/v1/health', 'health');
		});
	}

	if (INCLUDE.has('auth')) {
		group('auth: login', () => {
			token = login().data.accessToken;
		});
	}

	if (INCLUDE.has('campuses')) {
		group('campuses: my list/detail', () => {
			get('/api/v1/campuses/me', token, 'campuses_me');
			if (data.campusId) {
				get(`/api/v1/campuses/${data.campusId}`, token, 'campus_detail');
			}
		});
	}

	if (INCLUDE.has('admin-campuses')) {
		group('admin: campus list', () => {
			get('/api/v1/admin/campuses?page=0&size=20&sort=createdAt,desc', token, 'admin_campuses');
		});
	}

	if (data.campusId && INCLUDE.has('admin-dashboard')) {
		group('admin: campus dashboard summary', () => {
			get(`/api/v1/admin/campuses/${data.campusId}/dashboard/summary?weekStartDate=${WEEK_START_DATE}`, token, 'admin_dashboard_summary');
		});
	}

	if (data.campusId && INCLUDE.has('devotions')) {
		group('devotions: weekly/monthly read', () => {
			get(`/api/v1/campuses/${data.campusId}/devotions/me/weeks/${WEEK_START_DATE}`, token, 'devotion_weekly_read');
			get(`/api/v1/campuses/${data.campusId}/devotions/me/monthly-summary?year=${YEAR}&month=${MONTH}`, token, 'devotion_monthly_summary');
		});
	}

	if (data.campusId && INCLUDE.has('billing')) {
		group('billing: my charges and summary', () => {
			get(`/api/v1/campuses/${data.campusId}/charges/me?page=0&size=20&sort=createdAt,desc`, token, 'billing_my_charges');
			get(`/api/v1/campuses/${data.campusId}/charges/me/summary?year=${YEAR}&month=${MONTH}`, token, 'billing_my_summary');
		});
	}

	if (data.campusId && INCLUDE.has('polls')) {
		group('polls: list/detail/results', () => {
			get(`/api/v1/campuses/${data.campusId}/polls`, token, 'poll_list');
			if (POLL_ID) {
				get(`/api/v1/campuses/${data.campusId}/polls/${POLL_ID}`, token, 'poll_detail');
				get(`/api/v1/campuses/${data.campusId}/polls/${POLL_ID}/results`, token, 'poll_results');
			}
		});
	}

	if (data.campusId && INCLUDE.has('prayers')) {
		group('prayers: weekly board', () => {
			get(`/api/v1/campuses/${data.campusId}/prayers/weeks/${WEEK_START_DATE}`, token, 'prayer_weekly_board');
		});
	}

	sleep(THINK_TIME_SECONDS);
}

function requiresAuth() {
	return [...INCLUDE].some((name) => name !== 'health');
}

function requiresCampus() {
	const campusDependentIncludes = ['admin-dashboard', 'devotions', 'billing', 'polls', 'prayers'];
	return campusDependentIncludes.some((name) => INCLUDE.has(name));
}

function login() {
	const response = http.post(
		`${BASE_URL}/api/v1/auth/login`,
		JSON.stringify({ email: PERF_EMAIL, password: PERF_PASSWORD }),
		jsonParams('auth_login')
	);
	recordEndpointDuration('auth_login', response);
	const ok = check(response, {
		'auth_login status is 200': (res) => res.status === 200,
		'auth_login returns access token': (res) => Boolean(parseJson(res).data?.accessToken),
	});
	if (!ok) {
		fail(`Login failed: status=${response.status} body=${response.body}`);
	}
	return parseJson(response);
}

function firstCampusId(token) {
	const response = get('/api/v1/campuses/me', token, 'setup_campuses_me');
	const campuses = parseJson(response).data || [];
	return campuses.length > 0 ? campuses[0].campusId : null;
}

function get(path, token, name) {
	const response = http.get(`${BASE_URL}${path}`, {
		headers: { Authorization: `Bearer ${token}` },
		tags: { name },
	});
	recordEndpointDuration(name, response);
	check(response, {
		[`${name} status is 200`]: (res) => res.status === 200,
		[`${name} success envelope`]: (res) => parseJson(res).success === true,
	});
	return response;
}

function getPublic(path, name) {
	const response = http.get(`${BASE_URL}${path}`, {
		tags: { name },
	});
	recordEndpointDuration(name, response);
	check(response, {
		[`${name} status is 200`]: (res) => res.status === 200,
		[`${name} success envelope`]: (res) => parseJson(res).success === true,
	});
	return response;
}

function recordEndpointDuration(name, response) {
	if (endpointDurations[name]) {
		endpointDurations[name].add(response.timings.duration);
	}
}

function jsonParams(name) {
	return {
		headers: { 'Content-Type': 'application/json' },
		tags: { name },
	};
}

function parseJson(response) {
	try {
		return response.json();
	} catch (error) {
		return {};
	}
}

function guardTarget() {
	const localTarget = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal|faithlog-backend|app)(?::\d+)?($|\/)/.test(BASE_URL);
	if (!localTarget && __ENV.ALLOW_REMOTE_LOAD !== 'true') {
		fail('Remote load test is blocked. Use local Docker by default, or set ALLOW_REMOTE_LOAD=true only after user approval.');
	}
}
