import http from 'k6/http';
import {check, fail, group} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';
import {
	buildRequestCases,
	encodeQuery,
	validateCaseResponseSemantics,
	validateExpectationsManifest,
} from './scenario-definition.mjs';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const CAMPUS_ID = Number(__ENV.CAMPUS_ID);
const PERF_ACCESS_TOKEN = __ENV.PERF_ACCESS_TOKEN;
const PHASE = __ENV.PHASE;
const EXPECTATIONS_PATH = __ENV.EXPECTATIONS_PATH;
const WARMUP_ITERATIONS = Number(__ENV.WARMUP_ITERATIONS);
const WARMUP_VUS = Number(__ENV.WARMUP_VUS);
const WARMUP_MAX_DURATION = __ENV.WARMUP_MAX_DURATION;
const MEASURED_VUS = Number(__ENV.MEASURED_VUS);
const MEASURED_DURATION = __ENV.MEASURED_DURATION;

if (!EXPECTATIONS_PATH) {
	throw new Error('EXPECTATIONS_PATH is required. Run through run-baseline.sh.');
}

const EXPECTATIONS = JSON.parse(open(EXPECTATIONS_PATH));
const requestCases = buildRequestCases(EXPECTATIONS, CAMPUS_ID);
const endpointMetrics = Object.fromEntries(
	requestCases.map(({name}) => [
		name,
		{
			duration: new Trend(`admin_charge_${name}_duration`, true),
			throughput: new Counter(`admin_charge_${name}_requests`),
			failure: new Rate(`admin_charge_${name}_failure`),
		},
	])
);
const measuredFailureThresholds = Object.fromEntries(
	requestCases.map(({name}) => [`admin_charge_${name}_failure`, ['rate==0']])
);

export const options = {
	scenarios: PHASE === 'warmup'
		? {
			warmup: {
				executor: 'shared-iterations',
				vus: WARMUP_VUS,
				iterations: WARMUP_ITERATIONS,
				maxDuration: WARMUP_MAX_DURATION,
			},
		}
		: {
			measured: {
				executor: 'constant-vus',
				vus: MEASURED_VUS,
				duration: MEASURED_DURATION,
			},
		},
	thresholds: PHASE === 'measured' ? measuredFailureThresholds : {},
	summaryTrendStats: ['avg', 'med', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'],
};

export function setup() {
	validateRuntimeContract();
	return {token: PERF_ACCESS_TOKEN};
}

export default function ({token}) {
	for (const requestCase of requestCases) {
		group(requestCase.name, () => executeCase(token, requestCase));
	}
}

function validateRuntimeContract() {
	if (!['warmup', 'measured'].includes(PHASE)) {
		fail('PHASE must be warmup or measured.');
	}
	if (!Number.isInteger(CAMPUS_ID) || CAMPUS_ID <= 0) {
		fail('CAMPUS_ID must be a positive integer.');
	}
	if (!PERF_ACCESS_TOKEN) {
		fail('PERF_ACCESS_TOKEN is required from the runner preflight.');
	}
	if (!BASE_URL) {
		fail('BASE_URL is required from the inspected target binding.');
	}
	if (EXPECTATIONS.campusId !== CAMPUS_ID) {
		fail('The fixture expectations campus does not match CAMPUS_ID.');
	}
	try {
		validateExpectationsManifest(EXPECTATIONS, CAMPUS_ID);
	} catch (error) {
		fail(`The measured case manifest is invalid: ${error.message}`);
	}
	if (PHASE === 'warmup' && (
		!Number.isInteger(WARMUP_ITERATIONS)
		|| WARMUP_ITERATIONS <= 0
		|| !Number.isInteger(WARMUP_VUS)
		|| WARMUP_VUS <= 0
		|| !WARMUP_MAX_DURATION
	)) {
		fail('WARMUP_ITERATIONS, WARMUP_VUS, and WARMUP_MAX_DURATION require approved positive runtime values.');
	}
	if (PHASE === 'measured' && (!Number.isInteger(MEASURED_VUS) || MEASURED_VUS <= 0 || !MEASURED_DURATION)) {
		fail('MEASURED_VUS and MEASURED_DURATION are required for the measured phase.');
	}
}

function executeCase(token, requestCase) {
	const response = http.get(`${BASE_URL}${requestCase.path}?${encodeQuery(requestCase.query)}`, {
		headers: {Authorization: `Bearer ${token}`},
		tags: {name: requestCase.name, phase: PHASE},
	});
	const body = parseJson(response);
	const ok = check(response, {
		[`${requestCase.name} status is 200`]: (res) => res.status === 200,
		[`${requestCase.name} success envelope`]: () => body.success === true,
		[`${requestCase.name} semantic body matches case manifest`]: () =>
			validateCaseResponseSemantics(body, requestCase, EXPECTATIONS),
	});
	endpointMetrics[requestCase.name].duration.add(response.timings.duration);
	endpointMetrics[requestCase.name].throughput.add(1);
	endpointMetrics[requestCase.name].failure.add(!ok);
}

function parseJson(response) {
	try {
		return response.json();
	} catch (_error) {
		return {};
	}
}
