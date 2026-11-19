import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { Script } from 'node:vm';
import test from 'node:test';

const raw = readFileSync(new URL('./baseline.js', import.meta.url), 'utf8');
const scenarioSource = raw
  .replace(/^import [^\n]+\n/gm, '')
  .replace('export const options =', 'const options =')
  .replace('export function setup()', 'function setup()')
  .replace('export default function (context)', 'function run(context)') +
  '\nglobalThis.scenario = { options, setup, run };';

const syntheticAccount = { email: 'perf-farm-001@example.invalid', password: 'fake', campusId: 1 };
function scenario(overrides = {}) {
  const calls = [];
  const measures = [];
  const envelope = (url) => ({
    success: true,
    data: url.includes('/instances/garden') ? [] :
      url.includes('/companions/me/garden') ? { placements: [] } : {},
  });
  const context = {
    __ENV: {
      BASE_URL: 'http://127.0.0.1:8080',
      PERF_USERS_FILE: 'build/perf-farm-users.json',
      ISOLATED_FIXTURE_ACK: 'yes',
      VUS: '1',
      DURATION: '30s',
      ...overrides,
    },
    __VU: 1,
    open: () => JSON.stringify([syntheticAccount]),
    http: {
      post: (url) => {
        calls.push(['POST', url]);
        return { status: 200, json: () => ({ data: { accessToken: 'local-fake-token' } }) };
      },
      get: (url, params) => {
        calls.push(['GET', url, params.tags.name]);
        return { status: 200, timings: { duration: 2 }, json: () => envelope(url) };
      },
    },
    Rate: class { add(value) { measures.push(value); } },
    Trend: class { add(value) { assert.equal(value, 2); } },
    check: (response, assertions) => {
      for (const check of Object.values(assertions)) assert.equal(check(response), true);
    },
    fail: (message) => { throw new Error(message); },
    sleep: () => {},
  };
  new Script(scenarioSource, { filename: 'baseline.js' })
    .runInNewContext(context, { timeout: 2000 });
  return { ...context.scenario, calls, measures };
}

test('isolated farm read scenario logs in only in setup, then uses four read endpoints', () => {
  const script = scenario();
  const state = script.setup();
  script.run(state);
  assert.deepEqual(script.calls.map(([method]) => method), ['POST', 'GET', 'GET', 'GET', 'GET']);
  assert.deepEqual(script.calls.slice(1).map(([, url]) => url.split('/api/v1/')[1]), [
    'companions/me/garden',
    'companions/me/instances/garden',
    'points/me/wallet',
    'points/me/attendance',
  ]);
  assert.equal(script.measures.length, 4);
  assert.equal(script.measures.every(Boolean), true);
});

test('mission profile uses authenticated campus-scoped GET without claiming points', () => {
  const script = scenario({ INCLUDE_MISSIONS: 'true' });
  script.run(script.setup());
  assert.equal(script.calls.at(-1)[0], 'GET');
  assert.match(script.calls.at(-1)[1], /\/api\/v1\/campuses\/1\/missions\/me$/);
  assert.equal(script.measures.length, 5);
});

test('remote URLs, missing acknowledgment, excessive VUs and duration are fail-closed', () => {
  for (const env of [
    { BASE_URL: 'https://production.example.com' },
    { BASE_URL: 'http://127.0.0.1:8080/evil-path' },
    { ISOLATED_FIXTURE_ACK: 'no' },
    { VUS: '301' },
    { DURATION: '11m' },
    { PERF_USERS_FILE: '../secrets.json' },
  ]) {
    assert.throws(() => scenario(env));
  }
});

test('no care, evolve, draw, sale or mission-claim endpoint is sent', () => {
  const script = scenario({ INCLUDE_MISSIONS: 'true' });
  script.run(script.setup());
  assert.equal(script.calls.some(([, url]) =>
    /\/(care|evolve|draws|sales|claims)(\/|$)/.test(url)), false);
});
