import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEVOTION_SCRIPT = path.join(ISSUE_DIR, 'devotion-write.js');
const SYNTHETIC_SCRIPT = path.join(ISSUE_DIR, 'tests/fixtures/k6-v2-env-open-shape.js');
const ENV_NAMES = ['BASE_URL', 'FIXTURE_MANIFEST', 'CREDENTIALS_FILE', 'PHASE', 'VUS', 'MAX_DURATION'];

test('installed k6 v2 requires explicit -e for devotion inspect and baseline no-HTTP run', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-k6-env-'));
	const manifestPath = path.join(temporaryDirectory, 'fixture.json');
	const credentialsPath = path.join(temporaryDirectory, 'credentials.json');
	const summaryPath = path.join(temporaryDirectory, 'summary.json');
	const fixtureRunId = 'ISSUE197_K6_ENV_SYNTHETIC';
	const secretToken = 'sentinel-token-must-never-appear-in-output-or-argv';
	const values = {
		BASE_URL: 'http://127.0.0.1:1', FIXTURE_MANIFEST: manifestPath, CREDENTIALS_FILE: credentialsPath,
		PHASE: 'warmup', VUS: '1', MAX_DURATION: '1s',
	};
	fs.writeFileSync(manifestPath, `${JSON.stringify({
		fixtureRunId, datasetId: 'PERFORMANCE_K6_ENV_SYNTHETIC', scenarioType: 'devotion-write',
		campusId: 1, rollbackCampusId: 2, expectedMeasuredUserCount: 1000,
		referenceDate: '2099-01-01', warmupWeekStartDate: '2099-01-05', measuredWeekStartDate: '2099-01-12', rollbackWeekStartDate: '2098-12-29',
		warmupUserIds: [1], measuredUserIds: [2], rollbackUserIds: [3],
	})}\n`, { mode: 0o600 });
	fs.writeFileSync(credentialsPath, `${JSON.stringify({ fixtureRunId, tokens: [{ userId: 1, accessToken: secretToken }] })}\n`, { mode: 0o600 });
	const explicitArgs = ENV_NAMES.flatMap((name) => ['-e', `${name}=${values[name]}`]);
	try {
		const assignmentOnly = spawnSync('k6', ['inspect', DEVOTION_SCRIPT], {
			encoding: 'utf8', env: { ...process.env, ...values },
		});
		assert.equal(assignmentOnly.status, 107);
		assert.match(`${assignmentOnly.stdout}\n${assignmentOnly.stderr}`, /open\(\).*empty filename/i);
		assert.doesNotMatch(`${assignmentOnly.stdout}\n${assignmentOnly.stderr}`, new RegExp(secretToken));

		const explicitInspect = spawnSync('k6', ['inspect', ...explicitArgs, DEVOTION_SCRIPT], { encoding: 'utf8' });
		assert.equal(explicitInspect.status, 0, explicitInspect.stderr);
		assert.doesNotMatch(`${explicitInspect.stdout}\n${explicitInspect.stderr}`, new RegExp(secretToken));

		const syntheticArgs = [
			'run', '--quiet', '--summary-export', summaryPath,
			'-e', `FIXTURE_MANIFEST=${manifestPath}`, '-e', `CREDENTIALS_FILE=${credentialsPath}`,
			'-e', `EXPECTED_FIXTURE_RUN_ID=${fixtureRunId}`, SYNTHETIC_SCRIPT,
		];
		assert.equal(syntheticArgs.includes(secretToken), false);
		const explicitRun = spawnSync('k6', syntheticArgs, { encoding: 'utf8' });
		assert.equal(explicitRun.status, 0, explicitRun.stderr);
		assert.doesNotMatch(`${explicitRun.stdout}\n${explicitRun.stderr}`, new RegExp(secretToken));
		const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
		assert.equal(Object.hasOwn(summary.metrics, 'http_reqs'), false);

		const prepareRunner = fs.readFileSync(path.join(ISSUE_DIR, 'run-devotion-prepare.sh'), 'utf8');
		const baselineRunner = fs.readFileSync(path.join(ISSUE_DIR, 'run-devotion-baseline.sh'), 'utf8');
		for (const name of ENV_NAMES) {
			assert.match(prepareRunner, new RegExp(`-e "${name}=`));
			assert.match(baselineRunner, new RegExp(`-e "${name}=`));
		}
		for (const runner of [prepareRunner, baselineRunner]) assert.doesNotMatch(runner, new RegExp(secretToken));
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});
