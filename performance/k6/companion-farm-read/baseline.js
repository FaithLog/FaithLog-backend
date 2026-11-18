import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// #297: isolated local fixture only. Deliberately no remote override.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const USERS_FILE = __ENV.PERF_USERS_FILE;
const INCLUDE_MISSIONS = __ENV.INCLUDE_MISSIONS === 'true';
const VUS = Number(__ENV.VUS || '1');
const DURATION = __ENV.DURATION || '30s';
const THINK_TIME = Number(__ENV.THINK_TIME_SECONDS || '1');

function guard() {
  if (__ENV.ISOLATED_FIXTURE_ACK !== 'yes') {
    throw new Error('Set ISOLATED_FIXTURE_ACK=yes only for an isolated, non-production fixture.');
  }
  if (!/^http:\/\/(?:127\.0\.0\.1|localhost|host\.docker\.internal|app)(?::[0-9]{2,5})?$/.test(BASE_URL)) {
    throw new Error('Only local HTTP fixture URLs are permitted; remote load is prohibited.');
  }
  if (!USERS_FILE || !/^build\/[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*\.json$/.test(USERS_FILE)) {
    throw new Error('PERF_USERS_FILE must be an ignored build/ JSON file.');
  }
  if (!Number.isInteger(VUS) || VUS < 1 || VUS > 300) {
    throw new Error('VUS must be between 1 and 300.');
  }
  const durationMatch = /^([0-9]+)(s|m)$/.exec(DURATION);
  if (!durationMatch) throw new Error('DURATION must be 10s..10m.');
  const durationSeconds = Number(durationMatch[1]) * (durationMatch[2] === 'm' ? 60 : 1);
  if (durationSeconds < 10 || durationSeconds > 600) {
    throw new Error('DURATION must be 10s..10m.');
  }
  if (!Number.isFinite(THINK_TIME) || THINK_TIME < 0.25 || THINK_TIME > 10) {
    throw new Error('THINK_TIME_SECONDS must be 0.25..10.');
  }
}
guard();

// k6 resolves init-context open() relative to this JS module, not repository root.
const accounts = JSON.parse(open('../../../' + USERS_FILE));
if (!Array.isArray(accounts) || accounts.length < VUS || accounts.length > 300) {
  throw new Error('Provide a JSON array of at least VUS and at most 300 synthetic accounts.');
}
for (const account of accounts) {
  if (!account || typeof account.email !== 'string' || !account.email.startsWith('perf-') ||
      typeof account.password !== 'string' || !account.password ||
      (INCLUDE_MISSIONS && (!Number.isSafeInteger(account.campusId) || account.campusId < 1))) {
    throw new Error('Accounts must be synthetic perf-* users; campusId required for mission reads.');
  }
}

const responseOk = new Rate('farm_response_ok');
const gardenLatency = new Trend('farm_garden_ms', true);
const visibleLatency = new Trend('farm_visible_instances_ms', true);
const walletLatency = new Trend('farm_wallet_ms', true);
const attendanceLatency = new Trend('farm_attendance_ms', true);
const missionsLatency = new Trend('farm_missions_ms', true);

export const options = {
  scenarios: {
    isolated_farm_reads: { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  setupTimeout: '5m',
  summaryTrendStats: ['avg', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    farm_response_ok: ['rate>=1'],
    http_req_failed: ['rate<=0'],
  },
};

function parseJson(response) {
  try { return response.json(); } catch (_) { return null; }
}

export function setup() {
  const sessions = [];
  // Authentication happens before the measured VU iterations.
  // It may still appear in aggregate http_req metrics; analyze farm_* metrics separately.
  for (const account of accounts.slice(0, VUS)) {
    const result = http.post(BASE_URL + '/api/v1/auth/login',
      JSON.stringify({ email: account.email, password: account.password }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'fixture_login' } });
    const body = parseJson(result);
    if (result.status !== 200 || !body || !body.data ||
        typeof body.data.accessToken !== 'string' || !body.data.accessToken) {
      fail('Synthetic fixture login failed (HTTP ' + result.status + ').');
    }
    sessions.push({ token: body.data.accessToken, campusId: account.campusId || null });
  }
  return { sessions };
}

function read(path, token, name, metric, validData) {
  const response = http.get(BASE_URL + path,
    { headers: { Authorization: 'Bearer ' + token }, tags: { name: name } });
  const body = parseJson(response);
  metric.add(response.timings.duration);
  const ok = response.status === 200 && body && body.success === true &&
    validData(body.data);
  responseOk.add(Boolean(ok));
  check(response, { [name + '_success_and_shape']: () => Boolean(ok) });
  // Do not print response bodies: they might contain member data.
}

export default function (context) {
  const session = context.sessions[__VU - 1];
  if (!session) fail('No synthetic session for VU.');
  read('/api/v1/companions/me/garden', session.token,
    'farm_garden', gardenLatency,
    (data) => data && Array.isArray(data.placements) && data.placements.length <= 5);
  read('/api/v1/companions/me/instances/garden', session.token,
    'farm_visible_instances', visibleLatency,
    (data) => Array.isArray(data) && data.length <= 5);
  read('/api/v1/points/me/wallet', session.token,
    'farm_wallet', walletLatency, (data) => data && typeof data === 'object');
  read('/api/v1/points/me/attendance', session.token,
    'farm_attendance', attendanceLatency, (data) => data && typeof data === 'object');
  if (INCLUDE_MISSIONS) {
    read('/api/v1/campuses/' + session.campusId + '/missions/me', session.token,
      'farm_missions', missionsLatency, (data) => data && typeof data === 'object');
  }
  sleep(THINK_TIME);
}
