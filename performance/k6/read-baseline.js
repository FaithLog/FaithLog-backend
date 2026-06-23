import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';

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
	if (!PERF_EMAIL || !PERF_PASSWORD) {
		fail('PERF_EMAIL and PERF_PASSWORD are required.');
	}

	const loginResponse = login();
	const token = loginResponse.data.accessToken;
	const campusId = CAMPUS_ID || firstCampusId(token);
	return { token, campusId };
}

export default function (data) {
	let token = data.token;

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

function login() {
	const response = http.post(
		`${BASE_URL}/api/v1/auth/login`,
		JSON.stringify({ email: PERF_EMAIL, password: PERF_PASSWORD }),
		jsonParams('auth_login')
	);
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
	check(response, {
		[`${name} status is 200`]: (res) => res.status === 200,
		[`${name} success envelope`]: (res) => parseJson(res).success === true,
	});
	return response;
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
	const localTarget = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?($|\/)/.test(BASE_URL);
	if (!localTarget && __ENV.ALLOW_REMOTE_LOAD !== 'true') {
		fail('Remote load test is blocked. Use local Docker by default, or set ALLOW_REMOTE_LOAD=true only after user approval.');
	}
}
