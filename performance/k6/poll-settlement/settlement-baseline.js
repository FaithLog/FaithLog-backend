import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { requireExactBaseUrl } from './scenario-contract.js';

const MODE = __ENV.MODE;
const PHASE = __ENV.PHASE;
const CONFIG = MODE?.endsWith('-sequential') ? { concurrent: false, vus: 1, iterations: 10, maxDuration: '27m' }
	: MODE?.endsWith('-concurrent') ? { concurrent: true, vus: 5, iterations: 5, maxDuration: '14m' } : null;
const fixtureType = MODE?.startsWith('coffee-') ? 'coffee' : MODE?.startsWith('meal-') ? 'meal' : null;
const manifest = __ENV.MANIFEST_PATH ? JSON.parse(open(__ENV.MANIFEST_PATH)) : {};
const measuredToken = __ENV.TOKEN_PATH ? open(__ENV.TOKEN_PATH).trim() : null;
let BASE_URL;
try { BASE_URL = requireExactBaseUrl(__ENV.BASE_URL); } catch (error) { fail(error.message); }
const evidenceCase = { datasetId: __ENV.PERF_DATASET_ID, fixtureRunId: __ENV.PERF_FIXTURE_RUN_ID, executionRunId: __ENV.PERF_EXECUTION_RUN_ID, mode: MODE };
const coffeeSettlementDuration = new Trend('coffee_settlement_duration', true);
const mealSettlementDuration = new Trend('meal_settlement_duration', true);
const coffeeSettlementFailureRate = new Rate('coffee_settlement_failure_rate');
const mealSettlementFailureRate = new Rate('meal_settlement_failure_rate');
const coffeeSettlementRequests = new Counter('coffee_settlement_requests');
const mealSettlementRequests = new Counter('meal_settlement_requests');
const coffeeStartedAt = new Trend('coffee_settlement_started_at');
const mealStartedAt = new Trend('meal_settlement_started_at');
const coffeeFinishedAt = new Trend('coffee_settlement_finished_at');
const mealFinishedAt = new Trend('meal_settlement_finished_at');
const coffeeWarmupFailureRate = new Rate('coffee_warmup_failure_rate');
const mealWarmupFailureRate = new Rate('meal_warmup_failure_rate');
const coffeeWarmupRequests = new Counter('coffee_warmup_requests');
const mealWarmupRequests = new Counter('meal_warmup_requests');

const phaseConfig = PHASE === 'warmup' ? { vus: 1, iterations: 1, maxDuration: '14m' } : CONFIG;
export const options = {
	scenarios: { settlement: { executor: 'shared-iterations', vus: phaseConfig?.vus || 1, iterations: phaseConfig?.iterations || 1, maxDuration: phaseConfig?.maxDuration || '1s' } },
	summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
	thresholds: fixtureType && PHASE ? { [`${fixtureType}_${PHASE === 'warmup' ? 'warmup' : 'settlement'}_failure_rate`]: ['rate==0'] } : {},
};

export function setup() {
	guardCase();
	if (!CONFIG || !fixtureType || !['warmup', 'measured'].includes(PHASE)) fail('Valid MODE and PHASE are required.');
	if (PHASE === 'warmup') return { token: login() };
	if (!measuredToken) fail('TOKEN_PATH must contain a prepared measured token.');
	return { token: measuredToken };
}

export default function (data) {
	const group = PHASE === 'warmup' ? (CONFIG.concurrent ? 'concurrentWarmup' : 'sequentialWarmup') : (CONFIG.concurrent ? 'concurrentMeasured' : 'sequentialMeasured');
	const fixture = manifest[fixtureType][group][exec.scenario.iterationInTest];
	if (!fixture) fail(`No fixture for ${PHASE} iteration ${exec.scenario.iterationInTest}.`);
	const startedAt = Date.now(); const response = settle(data.token, fixture); const finishedAt = Date.now(); const ok = successful(response);
	if (PHASE === 'warmup') {
		if (fixtureType === 'coffee') { coffeeWarmupFailureRate.add(!ok); coffeeWarmupRequests.add(1); }
		else { mealWarmupFailureRate.add(!ok); mealWarmupRequests.add(1); }
	} else if (fixtureType === 'coffee') {
		coffeeStartedAt.add(startedAt); coffeeFinishedAt.add(finishedAt); coffeeSettlementDuration.add(response.timings.duration); coffeeSettlementFailureRate.add(!ok); coffeeSettlementRequests.add(1);
	} else {
		mealStartedAt.add(startedAt); mealFinishedAt.add(finishedAt); mealSettlementDuration.add(response.timings.duration); mealSettlementFailureRate.add(!ok); mealSettlementRequests.add(1);
	}
	check(response, { [`${MODE} ${PHASE} status is 200`]: (res) => res.status === 200, [`${MODE} ${PHASE} success envelope`]: (res) => parseJson(res).success === true });
}

export function handleSummary(data) { const path = __ENV.EVIDENCE_PATH; if (!path) throw new Error('EVIDENCE_PATH is required.'); return { [path]: `${JSON.stringify({ case: evidenceCase, phase: PHASE, metrics: data.metrics })}\n` }; }
function settle(token, fixture) { const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }; if (fixtureType === 'coffee') return http.patch(`${BASE_URL}/api/v1/admin/campuses/${manifest.campusId}/polls/${fixture.pollId}/close`, null, { headers, tags: { name: `${PHASE}_coffee_settlement_close` }, timeout: '13m' }); const groups = fixture.groups.map(({ optionId, calculationType, enteredAmount }) => ({ optionId, calculationType, enteredAmount })); return http.post(`${BASE_URL}/api/v1/campuses/${manifest.campusId}/meal/polls/${fixture.pollId}/charges`, JSON.stringify({ paymentAccountId: manifest.meal.paymentAccountId, groups }), { headers, tags: { name: `${PHASE}_meal_settlement_charges` }, timeout: '13m' }); }
function login() { const password = __ENV.PERF_PASSWORD; if (!password) fail('PERF_PASSWORD is required only for warmup.'); const response = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email: manifest.actorEmail, password }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'warmup_auth_login' } }); const token = parseJson(response).data?.accessToken; if (response.status !== 200 || !token) fail(`Warmup login failed with HTTP ${response.status}.`); return token; }
function guardCase() { if (!__ENV.MANIFEST_PATH || !__ENV.EVIDENCE_PATH) fail('MANIFEST_PATH and EVIDENCE_PATH are required.'); for (const key of ['datasetId', 'fixtureRunId', 'executionRunId', 'mode']) if (!evidenceCase[key]) fail(`Missing case: ${key}`); if (manifest.datasetId !== evidenceCase.datasetId || manifest.fixtureRunId !== evidenceCase.fixtureRunId) fail('Manifest case mismatch.'); if (manifest.baseUrl !== BASE_URL) fail('Manifest target mismatch.'); }
function successful(response) { return response.status === 200 && parseJson(response).success === true; }
function parseJson(response) { try { return response.json(); } catch { return {}; } }
