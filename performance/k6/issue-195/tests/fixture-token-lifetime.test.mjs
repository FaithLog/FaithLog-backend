import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const issueRoot = path.resolve(here, '..');
const sessionPath = path.join(issueRoot, 'fixture-auth-session.mjs');
const reportPath = path.join(issueRoot, 'fixture-report.mjs');
const runtimeIdentityPath = path.join(issueRoot, 'runtime-container-identity.mjs');
const fixturePath = path.join(issueRoot, 'prepare-fixture.mjs');

test('fixture rejects an initial malformed or short-lived JWT before every mutation', async (t) => {
	const { createFixtureAuthSession } = await loadSession();
	for (const [label, token] of [
		['malformed', 'not-a-jwt'],
		['short-lived', jwt(1119)],
	]) {
		await t.test(label, async () => {
			const failures = [];
			let executed = 0;
			const session = createFixtureAuthSession({
				login: async () => token,
				nowEpochSeconds: () => 1000,
				safetyMarginSeconds: 120,
				onFailure: (failure) => failures.push(failure),
			});
			await assert.rejects(session.initialize(), /fixture admin token/i);
			await assert.rejects(session.authorizedRequest({
				stage: 'campus-mutation',
				execute: async () => { executed += 1; },
			}), /fixture admin token/i);
			assert.equal(executed, 0);
			assert.equal(failures[0].stage, 'fixture-initial-token');
			assert.deepEqual(Object.keys(failures[0]).sort(), ['stage']);
		});
	}
});

test('fixture refreshes before requests while crossing 1,000 details and 1,126 mutations', async () => {
	const { createFixtureAuthSession } = await loadSession();
	const clock = { now: 1000 };
	const tokens = [jwt(1300), jwt(2000), jwt(3000)];
	let loginCalls = 0;
	let detailReads = 0;
	let mutationWrites = 0;
	const session = createFixtureAuthSession({
		login: async () => tokens[loginCalls++],
		nowEpochSeconds: () => clock.now,
		safetyMarginSeconds: 120,
		onFailure: assert.fail,
	});
	await session.initialize();
	for (let index = 0; index < 1000; index += 1) {
		if (index === 500) clock.now = 1190;
		await session.authorizedRequest({
			stage: 'dataset-detail',
			execute: async (token) => {
				assert.match(token, /^[^.]+\.[^.]+\.[^.]+$/);
				detailReads += 1;
			},
		});
	}
	for (let index = 0; index < 1126; index += 1) {
		if (index === 600) clock.now = 1890;
		await session.authorizedRequest({
			stage: 'membership-mutation',
			execute: async () => { mutationWrites += 1; },
		});
	}
	assert.equal(detailReads, 1000);
	assert.equal(mutationWrites, 1126);
	assert.equal(loginCalls, 3);
	assert.equal(session.refreshCount, 3);
});

test('fixture refresh failure preserves a secret-free first rejection and partial counts', async () => {
	const { createFixtureAuthSession } = await loadSession();
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-fixture-token-'));
	try {
		const clock = { now: 1000 };
		let loginCalls = 0;
		let mutationWrites = 0;
		const rejectionPath = path.join(directory, 'first-rejection.json');
		const session = createFixtureAuthSession({
			login: async () => {
				loginCalls += 1;
				if (loginCalls === 2) throw new Error('synthetic-runtime-password-must-not-persist');
				return jwt(1300);
			},
			nowEpochSeconds: () => clock.now,
			safetyMarginSeconds: 120,
			onFailure: ({ stage }) => fs.writeFileSync(rejectionPath, `${JSON.stringify({
				status: 'partial-non-reusable',
				reusable: false,
				automaticCleanup: false,
				stage,
				mutationCounts: { campuses: 25, memberships: mutationWrites, duties: 0 },
			})}\n`, { flag: 'wx' }),
		});
		await session.initialize();
		for (let index = 0; index < 50; index += 1) {
			await session.authorizedRequest({
				stage: 'membership-mutation',
				execute: async () => { mutationWrites += 1; },
			});
		}
		clock.now = 1190;
		await assert.rejects(session.authorizedRequest({
			stage: 'membership-mutation',
			execute: async () => { mutationWrites += 1; },
		}), /fixture admin token refresh failed/i);
		assert.equal(mutationWrites, 50);
		const persisted = fs.readFileSync(rejectionPath, 'utf8');
		const rejection = JSON.parse(persisted);
		assert.equal(rejection.stage, 'membership-mutation-token-refresh');
		assert.deepEqual(rejection.mutationCounts, { campuses: 25, memberships: 50, duties: 0 });
		assert.doesNotMatch(persisted, /synthetic-runtime-password|eyJ/);
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('prepare-fixture integrates the runtime-only token session and sanitized rejection contract', () => {
	assert.ok(fs.existsSync(sessionPath), 'fixture-auth-session.mjs is required');
	const source = fs.readFileSync(fixturePath, 'utf8');
	assert.match(source, /process\.env\.TOKEN_SAFETY_MARGIN_SECONDS/);
	assert.doesNotMatch(source, /TOKEN_SAFETY_MARGIN_SECONDS\s*\|\|/);
	assert.match(source, /createFixtureAuthSession/);
	assert.match(source, /authorizedRequest/);
	assert.match(source, /first-rejection\.json/);
	assert.match(source, /reusable:\s*false/);
	assert.match(source, /automaticCleanup:\s*false/);
	assert.match(source, /mutationCounts/);
	assert.match(source, /refreshCount/);
	assert.doesNotMatch(source, /body=\$\{text\}/);
	assert.doesNotMatch(source, /error\.message|error\.stack/);
	assert.match(source, /reserveFixtureReportDirectory/);
	assert.match(source, /writeFixtureManifest/);
	assert.match(source, /captureContainerIdentity/);
});

test('fixture report reservation is atomic and the first partial receipt is never overwritten', async () => {
	assert.ok(fs.existsSync(reportPath), 'fixture-report.mjs is required');
	const {
		createFixtureRejectionRecorder,
		reserveFixtureReportDirectory,
		writeFixtureManifest,
	} = await import(`${pathToFileURL(reportPath).href}?test=${Date.now()}-${Math.random()}`);
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-fixture-report-'));
	try {
		let mutationCalls = 0;
		const directory = reserveFixtureReportDirectory({
			reportRoot: root,
			datasetId: 'PERF_1000_20260716_195_A',
			fixtureRunId: 'ISSUE195_BEFORE_20260716_A',
		});
		assert.throws(() => {
			reserveFixtureReportDirectory({
				reportRoot: root,
				datasetId: 'PERF_1000_20260716_195_A',
				fixtureRunId: 'ISSUE195_BEFORE_20260716_A',
			});
			mutationCalls += 1;
		}, /report directory already exists/i);
		assert.equal(mutationCalls, 0);

		const counts = { campuses: 2, memberships: 37, duties: 0 };
		const record = createFixtureRejectionRecorder({
			directory,
			datasetId: 'PERF_1000_20260716_195_A',
			fixtureRunId: 'ISSUE195_BEFORE_20260716_A',
			getMutationCounts: () => counts,
			getRefreshCount: () => 2,
		});
		record('membership-mutation-token-refresh');
		counts.memberships = 99;
		record('later-final-identity');
		const rejectionPath = path.join(directory, 'first-rejection.json');
		const rejection = JSON.parse(fs.readFileSync(rejectionPath, 'utf8'));
		assert.equal(rejection.stage, 'membership-mutation-token-refresh');
		assert.deepEqual(rejection.mutationCounts, { campuses: 2, memberships: 37, duties: 0 });
		assert.equal(rejection.reusable, false);
		assert.equal(rejection.automaticCleanup, false);

		const successRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-fixture-success-'));
		try {
			const successDirectory = reserveFixtureReportDirectory({
				reportRoot: successRoot,
				datasetId: 'PERF_1000_20260716_195_B',
				fixtureRunId: 'ISSUE195_BEFORE_20260716_B',
			});
			writeFixtureManifest(successDirectory, { status: 'scenario-ready/not-measured' });
			assert.throws(
				() => writeFixtureManifest(successDirectory, { status: 'overwritten' }),
				/EEXIST|already exists/i,
			);
		} finally {
			fs.rmSync(successRoot, { recursive: true, force: true });
		}
	} finally {
		fs.rmSync(root, { recursive: true, force: true });
	}
});

test('fixture Docker identity child receives no credential, password, token, or secret environment', async () => {
	assert.ok(fs.existsSync(runtimeIdentityPath), 'runtime-container-identity.mjs is required');
	const { captureContainerIdentity } = await import(
		`${pathToFileURL(runtimeIdentityPath).href}?test=${Date.now()}-${Math.random()}`
	);
	let observed;
	const identity = captureContainerIdentity('sha256:container', {
		environment: {
			PATH: '/runtime/bin',
			HOME: '/runtime/home',
			PERF_ADMIN_EMAIL: 'admin@example.test',
			PERF_ADMIN_PASSWORD: 'admin-password',
			PERF_ACCESS_TOKEN: 'access-token',
			POSTGRES_PASSWORD: 'db-password',
			CUSTOM_SECRET: 'custom-secret',
			CREDENTIAL_FILE: '/secret/path',
		},
		execFileSyncImpl(command, args, options) {
			observed = { command, args, env: options.env };
			return JSON.stringify([{
				Id: 'sha256:container',
				Name: '/faithlog-app',
				Image: 'sha256:image',
				State: { StartedAt: '2026-07-16T00:00:00Z' },
				Config: { Labels: {
					'com.docker.compose.project': 'faithlog',
					'com.docker.compose.service': 'app',
				} },
				NetworkSettings: { Ports: {} },
			}]);
		},
	});
	assert.equal(identity.containerId, 'sha256:container');
	assert.equal(observed.command, 'docker');
	assert.deepEqual(observed.args, ['inspect', 'sha256:container']);
	assert.deepEqual(observed.env, { PATH: '/runtime/bin', HOME: '/runtime/home' });
});

async function loadSession() {
	assert.ok(fs.existsSync(sessionPath), 'fixture-auth-session.mjs is required');
	return import(`${pathToFileURL(sessionPath).href}?test=${Date.now()}-${Math.random()}`);
}

function jwt(exp) {
	const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
	return `${encode({ alg: 'none' })}.${encode({ exp })}.signature`;
}
