import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const SCENARIO = join(ROOT, 'scenario.js');
const RUNNER = join(ROOT, 'run-baseline.sh');
const SYNTHETIC = join(ROOT, 'tests/fixtures/k6-v2-explicit-token-setup.js');
const EXPLICIT_NAMES = ['BASE_URL', 'FIXTURE_MANIFEST', 'CREDENTIALS_FILE', 'PHASE', 'MODE', 'ENDPOINT', 'VUS', 'DURATION'];
const IDENTITY_NAMES = [
	'EXPECTED_SOURCE_REVISION', 'EXPECTED_APP_SERVICE', 'EXPECTED_DB_SERVICE', 'EXPECTED_REDIS_SERVICE',
	'EXPECTED_APP_IMAGE', 'EXPECTED_APP_IMAGE_ID', 'EXPECTED_DB_IMAGE', 'EXPECTED_DB_IMAGE_ID',
	'EXPECTED_REDIS_IMAGE', 'EXPECTED_REDIS_IMAGE_ID',
];
const TOKEN_KEYS = ['admin', 'member', 'coffeeCreator', 'otherCoffeeDuty', 'mealDuty'];

test('installed k6 v2 reads a 0600 five-token file without argv, login HTTP, or setup-data serialization', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-k6-env-'));
	chmodSync(temporary, 0o700);
	const manifest = join(temporary, 'fixture-manifest.json');
	const credentials = join(temporary, 'warmup-credentials.json');
	const summary = join(temporary, 'summary.json');
	const secretToken = 'sentinel-token-must-not-appear-outside-credentials-file';
	const values = {
		BASE_URL: 'http://127.0.0.1:1', FIXTURE_MANIFEST: manifest, CREDENTIALS_FILE: credentials,
		PHASE: 'warmup', MODE: 'prayer', ENDPOINT: 'prayer_current_season', VUS: '1', DURATION: '1s',
	};
	for (const name of IDENTITY_NAMES) values[name] = `sentinel-${name}`;
	writeFileSync(manifest, `${JSON.stringify({ fixtureRunId: 'i196-k6-env', shapedAt: '2026-07-17T00:00:00.000Z' })}\n`, { mode: 0o600 });
	writeFileSync(credentials, `${JSON.stringify({
		schemaVersion: 1, fixtureRunId: 'i196-k6-env', phase: 'warmup',
		tokens: Object.fromEntries(TOKEN_KEYS.map((key) => [key, `${secretToken}-${key}`])),
	})}\n`, { mode: 0o600 });
	const allNames = [...EXPLICIT_NAMES, ...IDENTITY_NAMES];
	const explicitArgs = allNames.flatMap((name) => ['-e', `${name}=${values[name]}`]);
	try {
		assert.equal(statSync(temporary).mode & 0o777, 0o700);
		assert.equal(statSync(credentials).mode & 0o777, 0o600);
		const assignmentOnly = spawnSync('k6', ['inspect', SCENARIO], {
			encoding: 'utf8', env: { ...process.env, ...values },
		});
		assert.notEqual(assignmentOnly.status, 0, 'OS assignments alone must not satisfy installed k6 v2 __ENV');

		const explicitInspect = spawnSync('k6', ['inspect', ...explicitArgs, SCENARIO], { encoding: 'utf8' });
		assert.equal(explicitInspect.status, 0, explicitInspect.stderr);
		assert.doesNotMatch(`${explicitInspect.stdout}\n${explicitInspect.stderr}`, new RegExp(secretToken));

		const syntheticArgs = [
			'run', '--quiet', '--summary-export', summary,
			'-e', `BASE_URL=${values.BASE_URL}`, '-e', `CREDENTIALS_FILE=${credentials}`, '-e', 'PHASE=warmup', SYNTHETIC,
		];
		assert.doesNotMatch(syntheticArgs.join(' '), new RegExp(secretToken));
		const explicitRun = spawnSync('k6', syntheticArgs, { encoding: 'utf8' });
		assert.equal(explicitRun.status, 0, explicitRun.stderr);
		assert.doesNotMatch(`${explicitRun.stdout}\n${explicitRun.stderr}`, new RegExp(secretToken));
		const exportedText = readFileSync(summary, 'utf8');
		const exported = JSON.parse(exportedText);
		assert.equal(Object.hasOwn(exported.metrics, 'http_reqs'), false, 'setup must perform no login HTTP');
		assert.doesNotMatch(exportedText, new RegExp(secretToken), 'summary must not serialize init-scope credentials');

		const runner = readFileSync(RUNNER, 'utf8');
		for (const name of EXPLICIT_NAMES) {
			assert.ok((runner.match(new RegExp(`-e "${name}=`, 'g')) || []).length >= 2,
				`${name} must be passed explicitly to warmup and measured k6 processes`);
		}
		assert.doesNotMatch(runner, /-e "PERF_[A-Z_]*ACCESS_TOKEN=/);
		assert.match(runner, /mktemp -d[\s\S]*chmod 700/);
		assert.match(runner, /write_credentials_file[\s\S]*chmod 600/);
		assert.match(runner, /trap[\s\S]*cleanup_runtime_credentials/);

		const scenario = readFileSync(SCENARIO, 'utf8');
		assert.match(scenario, /JSON\.parse\(open\(__ENV\.CREDENTIALS_FILE\)\)/);
		assert.doesNotMatch(scenario, /__ENV\.PERF_[A-Z_]*ACCESS_TOKEN|function login\(/);
		const setupSource = scenario.slice(scenario.indexOf('export function setup()'), scenario.indexOf('export default function'));
		assert.doesNotMatch(setupSource, /Token|tokens|accessToken|login\(/,
			'setup data must contain no token material or login fallback');
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});
