import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const SCENARIO = join(ROOT, 'scenario.js');
const RUNNER = join(ROOT, 'run-baseline.sh');
const SYNTHETIC = join(ROOT, 'tests/fixtures/k6-v2-explicit-token-setup.js');
const EXPLICIT_NAMES = [
	'BASE_URL', 'FIXTURE_MANIFEST', 'PHASE', 'MODE', 'ENDPOINT', 'VUS', 'DURATION',
	'PERF_ADMIN_ACCESS_TOKEN', 'PERF_MEMBER_ACCESS_TOKEN', 'PERF_COFFEE_CREATOR_ACCESS_TOKEN',
	'PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN', 'PERF_MEAL_DUTY_ACCESS_TOKEN',
];
const IDENTITY_NAMES = [
	'EXPECTED_SOURCE_REVISION', 'EXPECTED_APP_SERVICE', 'EXPECTED_DB_SERVICE', 'EXPECTED_REDIS_SERVICE',
	'EXPECTED_APP_IMAGE', 'EXPECTED_APP_IMAGE_ID', 'EXPECTED_DB_IMAGE', 'EXPECTED_DB_IMAGE_ID',
	'EXPECTED_REDIS_IMAGE', 'EXPECTED_REDIS_IMAGE_ID',
];

test('installed k6 v2 receives explicit phase and five tokens without setup login HTTP', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-k6-env-'));
	const manifest = join(temporary, 'fixture-manifest.json');
	const summary = join(temporary, 'summary.json');
	const values = Object.fromEntries(EXPLICIT_NAMES.map((name) => [name, `sentinel-${name}`]));
	values.BASE_URL = 'http://127.0.0.1:1';
	values.FIXTURE_MANIFEST = manifest;
	values.PHASE = 'warmup';
	values.MODE = 'prayer';
	values.ENDPOINT = 'prayer_current_season';
	values.VUS = '1';
	values.DURATION = '1s';
	for (const name of IDENTITY_NAMES) values[name] = `sentinel-${name}`;
	writeFileSync(manifest, `${JSON.stringify({ shapedAt: '2026-07-17T00:00:00.000Z' })}\n`, { mode: 0o600 });
	const allNames = [...EXPLICIT_NAMES, ...IDENTITY_NAMES];
	const explicitArgs = allNames.flatMap((name) => ['-e', `${name}=${values[name]}`]);
	const secretPattern = /sentinel-PERF_(?:ADMIN|MEMBER|COFFEE|OTHER|MEAL)[A-Z_]*ACCESS_TOKEN/;
	try {
		const assignmentOnly = spawnSync('k6', ['inspect', SCENARIO], {
			encoding: 'utf8', env: { ...process.env, ...values },
		});
		assert.notEqual(assignmentOnly.status, 0, 'OS assignments alone must not satisfy installed k6 v2 __ENV');

		const explicitInspect = spawnSync('k6', ['inspect', ...explicitArgs, SCENARIO], { encoding: 'utf8' });
		assert.equal(explicitInspect.status, 0, explicitInspect.stderr);
		assert.doesNotMatch(`${explicitInspect.stdout}\n${explicitInspect.stderr}`, secretPattern);

		const syntheticArgs = [
			'run', '--quiet', '--summary-export', summary,
			...EXPLICIT_NAMES.filter((name) => name === 'BASE_URL' || name.includes('ACCESS_TOKEN'))
				.flatMap((name) => ['-e', `${name}=${values[name]}`]),
			SYNTHETIC,
		];
		const explicitRun = spawnSync('k6', syntheticArgs, { encoding: 'utf8' });
		assert.equal(explicitRun.status, 0, explicitRun.stderr);
		assert.doesNotMatch(`${explicitRun.stdout}\n${explicitRun.stderr}`, secretPattern);
		const exported = JSON.parse(readFileSync(summary, 'utf8'));
		assert.equal(Object.hasOwn(exported.metrics, 'http_reqs'), false,
			'explicit tokens must prevent all five setup login requests');

		const runner = readFileSync(RUNNER, 'utf8');
		for (const name of EXPLICIT_NAMES) {
			assert.ok((runner.match(new RegExp(`-e "${name}=`, 'g')) || []).length >= 2,
				`${name} must be passed explicitly to warmup and measured k6 processes`);
		}
		const scenario = readFileSync(SCENARIO, 'utf8');
		assert.match(scenario, /PHASE/);
		for (const name of EXPLICIT_NAMES.filter((value) => value.includes('ACCESS_TOKEN'))) {
			assert.match(scenario, new RegExp(`${name} \\|\\| login\\(`));
		}
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});
