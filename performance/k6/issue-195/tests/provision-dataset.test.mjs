import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const issueRoot = path.resolve(here, '..');
const provisionerPath = path.join(issueRoot, 'provision-dataset.mjs');
const contractPath = path.join(issueRoot, 'scenario-contract.json');
const sourceCommit = JSON.parse(fs.readFileSync(contractPath, 'utf8')).sourceIdentity.originDevelopCommit;

test('provisioner creates exactly 1,000 fresh ACTIVE USER rows and a secret-free manifest', async () => {
	const { provisionDataset } = await loadProvisioner();
	await withHarness(async (harness) => {
		const result = await provisionDataset(harness.options());
		assert.equal(result.createdUserCount, 1000);
		assert.equal(harness.api.signupCalls, 1000);
		assert.equal(harness.api.loginCalls, 2);
		assert.equal(harness.lock.acquireCalls, 1);
		assert.equal(harness.lock.releaseCalls, 1);
		const manifest = JSON.parse(fs.readFileSync(result.manifestPath, 'utf8'));
		assert.equal(manifest.status, 'provisioned/not-measured');
		assert.equal(manifest.datasetId, harness.env.PERF_DATASET_ID);
		assert.equal(manifest.createdUserCount, 1000);
		assert.equal(manifest.expectedActiveMembers, 1000);
		assert.equal(manifest.verificationTokenRefreshCount, 1);
		assert.equal(manifest.userIds.length, 1000);
		assert.equal(new Set(manifest.userIds).size, 1000);
		assert.equal(manifest.mutationPolicy, 'additive signup only; no existing row update/delete');
		const persisted = fs.readFileSync(result.manifestPath, 'utf8');
		for (const secret of [
			harness.env.PERF_ADMIN_EMAIL,
			harness.env.PERF_ADMIN_PASSWORD,
			harness.env.PERF_DATASET_MEMBER_PASSWORD,
			...harness.api.issuedTokens,
		]) assert.doesNotMatch(persisted, new RegExp(escapeRegExp(secret)));
		assert.deepEqual(harness.logs, [{
			status: 'provisioned/not-measured',
			datasetId: harness.env.PERF_DATASET_ID,
			createdUserCount: 1000,
			manifestPath: result.manifestPath,
		}]);
	});
});

test('namespace and report collisions fail before signup and never reuse an ID', async () => {
	const { provisionDataset } = await loadProvisioner();
	await withHarness(async (harness) => {
		harness.api.seedCollision();
		await assert.rejects(provisionDataset(harness.options()), /namespace.*not fresh/i);
		assert.equal(harness.api.signupCalls, 0);
		const rejection = readRejection(harness.reportDirectory);
		assert.equal(rejection.stage, 'namespace-preflight');
		assert.equal(rejection.createdUserCount, 0);
		assert.equal(rejection.reusable, false);
		const callsBeforeRetry = harness.api.calls.length;
		await assert.rejects(provisionDataset(harness.options()), /report directory already exists/i);
		assert.equal(harness.api.calls.length, callsBeforeRetry);
	});
});

test('partial signup failure is preserved and the dataset cannot be retried or cleaned up', async () => {
	const { provisionDataset } = await loadProvisioner();
	await withHarness(async (harness) => {
		harness.api.failSignupAt = 7;
		await assert.rejects(provisionDataset(harness.options()), /signup request failed at index 7/i);
		assert.equal(harness.api.signupCalls, 7);
		assert.equal(harness.api.users.length, 6);
		const rejection = readRejection(harness.reportDirectory);
		assert.equal(rejection.stage, 'signup');
		assert.equal(rejection.createdUserCount, 6);
		assert.equal(rejection.reusable, false);
		assert.equal(rejection.automaticCleanup, false);
		assert.equal(harness.lock.releaseCalls, 1);
	});
});

test('ADMIN, inactive, and duplicate user results fail closed', async (t) => {
	const { provisionDataset } = await loadProvisioner();
	for (const [label, mutate, pattern] of [
		['admin', (api) => { api.signupRoleAt = 3; }, /created user role must be USER/i],
		['inactive', (api) => { api.signupInactiveAt = 4; }, /created user must be ACTIVE/i],
		['duplicate', (api) => { api.duplicateIdAt = 5; }, /created user identity must be unique/i],
	]) {
		await t.test(label, async () => {
			await withHarness(async (harness) => {
				mutate(harness.api);
				await assert.rejects(provisionDataset(harness.options()), pattern);
				assert.equal(readRejection(harness.reportDirectory).reusable, false);
			});
		});
	}
});

test('post-lock and final runtime replacement stop provisioning at the first boundary', async (t) => {
	const { provisionDataset } = await loadProvisioner();
	await t.test('post-lock', async () => {
		await withHarness(async (harness) => {
			harness.identity.replaceAtCapture = 2;
			await assert.rejects(provisionDataset(harness.options()), /post-lock runtime identity changed/i);
			assert.equal(harness.api.calls.length, 0);
			assert.equal(readRejection(harness.reportDirectory).stage, 'post-lock-identity');
		});
	});
	await t.test('final', async () => {
		await withHarness(async (harness) => {
			harness.identity.replaceAtCapture = 3;
			await assert.rejects(provisionDataset(harness.options()), /final runtime identity changed/i);
			assert.equal(harness.api.signupCalls, 1000);
			assert.equal(readRejection(harness.reportDirectory).stage, 'final-identity');
		});
	});
});

test('post-signup and each 100-detail boundary use a fresh-enough runtime-only admin JWT', async (t) => {
	const { provisionDataset } = await loadProvisioner();
	await t.test('an initial token may expire during signup because verification logs in again', async () => {
		await withHarness(async (harness) => {
			harness.api.tokenExpByLogin.set(1, 1100);
			harness.api.tokenExpByLogin.set(2, 10_000);
			harness.api.advanceAfterSignupTo = 2000;
			const result = await provisionDataset(harness.options());
			assert.equal(result.createdUserCount, 1000);
			assert.equal(harness.api.loginCalls, 2);
			assert.equal(JSON.parse(fs.readFileSync(result.manifestPath, 'utf8')).verificationTokenRefreshCount, 1);
		});
	});
	await t.test('a token approaching the margin is refreshed before the next 100-user chunk', async () => {
		await withHarness(async (harness) => {
			harness.api.tokenExpByLogin.set(2, 1210);
			harness.api.tokenExpByLogin.set(3, 10_000);
			harness.api.advanceAfterDetailAt = 100;
			harness.api.advanceAfterDetailTo = 1100;
			const result = await provisionDataset(harness.options());
			assert.equal(harness.api.loginCalls, 3);
			assert.equal(JSON.parse(fs.readFileSync(result.manifestPath, 'utf8')).verificationTokenRefreshCount, 2);
		});
	});
	await t.test('post-signup login failure preserves the additive rows and first rejection', async () => {
		await withHarness(async (harness) => {
			harness.api.failLoginAt = 2;
			await assert.rejects(provisionDataset(harness.options()), /verification admin login failed/i);
			assert.equal(harness.api.users.length, 1000);
			assert.equal(harness.api.deleteCalls, 0);
			assert.equal(readRejection(harness.reportDirectory).stage, 'verification-login');
		});
	});
	await t.test('a newly issued token below the runtime margin fails before verification reads', async () => {
		await withHarness(async (harness) => {
			harness.api.tokenExpByLogin.set(2, 1119);
			await assert.rejects(provisionDataset(harness.options()), /verification token lifetime is below.*120/i);
			assert.equal(harness.api.users.length, 1000);
			assert.equal(harness.api.adminReadCalls, 2);
			assert.equal(readRejection(harness.reportDirectory).stage, 'verification-token');
		});
	});
});

test('runtime inputs have no credential/count fallback and provisioning never patches or deletes', async () => {
	const { sanitizedChildEnvironment } = await loadProvisioner();
	const source = fs.readFileSync(provisionerPath, 'utf8');
	for (const name of [
		'BASE_URL',
		'PERF_ADMIN_EMAIL',
		'PERF_ADMIN_PASSWORD',
		'PERF_DATASET_MEMBER_PASSWORD',
		'PERF_DATASET_ID',
		'PERF_SOURCE_COMMIT',
		'APP_CONTAINER_ID',
		'EXPECTED_APP_COMPOSE_SERVICE',
		'EXPECTED_APP_IMAGE_ID',
		'POSTGRES_CONTAINER_ID',
		'EXPECTED_POSTGRES_COMPOSE_SERVICE',
		'EXPECTED_POSTGRES_IMAGE_ID',
		'REDIS_CONTAINER_ID',
		'EXPECTED_REDIS_COMPOSE_SERVICE',
		'EXPECTED_REDIS_IMAGE_ID',
		'EXPECTED_ACTIVE_MEMBERS',
		'TOKEN_SAFETY_MARGIN_SECONDS',
	]) assert.match(source, new RegExp(`process\\.env\\.${name}`), `${name} must be runtime-required`);
	assert.doesNotMatch(source, /PERF_DATASET_MEMBER_PASSWORD\s*\|\||PERF_ADMIN_PASSWORD\s*\|\||TOKEN_SAFETY_MARGIN_SECONDS\s*\|\|/);
	assert.doesNotMatch(source, /method:\s*['"](?:PATCH|PUT|DELETE)['"]/);
	assert.match(source, /POST.*\/api\/v1\/auth\/signup/s);
	assert.match(source, /sanitizedChildEnvironment/);
	assert.doesNotMatch(source, /console\.(?:log|error)\([^)]*(?:PASSWORD|token)/i);
	assert.deepEqual(sanitizedChildEnvironment({
		PATH: '/runtime/bin',
		PERF_ADMIN_EMAIL: 'admin@example.test',
		PERF_ADMIN_PASSWORD: 'admin-secret',
		PERF_DATASET_MEMBER_PASSWORD: 'member-secret',
		PERF_ACCESS_TOKEN: 'access-secret',
		POSTGRES_PASSWORD: 'db-secret',
	}), { PATH: '/runtime/bin' });
});

async function loadProvisioner() {
	assert.ok(fs.existsSync(provisionerPath), 'issue-local provision-dataset.mjs is required');
	return import(`${pathToFileURL(provisionerPath).href}?test=${Date.now()}-${Math.random()}`);
}

async function withHarness(callback) {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-provision-'));
	try {
		const env = validEnvironment(directory);
		const clock = {
			now: 1000,
			nowEpochSeconds() { return this.now; },
		};
		clock.nowEpochSeconds = clock.nowEpochSeconds.bind(clock);
		const api = fakeApi(env.PERF_DATASET_ID, env.PERF_ADMIN_EMAIL, clock);
		const identity = fakeIdentity();
		const lock = {
			acquireCalls: 0,
			releaseCalls: 0,
			acquire() { this.acquireCalls += 1; },
			release() { this.releaseCalls += 1; },
		};
		const logs = [];
		const reportDirectory = path.join(directory, env.PERF_DATASET_ID, '_provisioning');
		await callback({
			env,
			api,
			clock,
			identity,
			lock,
			logs,
			reportDirectory,
			options: () => ({
				env,
				fetchImpl: api.fetch,
				captureRuntimeIdentity: identity.capture,
				acquireLock: () => lock.acquire(),
				releaseLock: () => lock.release(),
				log: (value) => logs.push(value),
				nowEpochSeconds: clock.nowEpochSeconds,
			}),
		});
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
}

function validEnvironment(reportRoot) {
	return {
		BASE_URL: 'http://127.0.0.1:28080',
		PERF_ADMIN_EMAIL: 'runtime-admin@example.test',
		PERF_ADMIN_PASSWORD: 'runtime-admin-secret',
		PERF_DATASET_MEMBER_PASSWORD: 'runtime-member-secret',
		PERF_DATASET_ID: 'PERF_1000_20260716_195_A',
		PERF_SOURCE_COMMIT: sourceCommit,
		PERF_REPORT_ROOT: reportRoot,
		APP_CONTAINER_ID: 'sha256:app-container',
		EXPECTED_APP_COMPOSE_SERVICE: 'app',
		EXPECTED_APP_IMAGE_ID: 'sha256:app-image',
		POSTGRES_CONTAINER_ID: 'sha256:postgres-container',
		EXPECTED_POSTGRES_COMPOSE_SERVICE: 'postgres',
		EXPECTED_POSTGRES_IMAGE_ID: 'sha256:postgres-image',
		REDIS_CONTAINER_ID: 'sha256:redis-container',
		EXPECTED_REDIS_COMPOSE_SERVICE: 'redis',
		EXPECTED_REDIS_IMAGE_ID: 'sha256:redis-image',
		EXPECTED_ACTIVE_MEMBERS: '1000',
		TOKEN_SAFETY_MARGIN_SECONDS: '120',
	};
}

function fakeIdentity() {
	const base = {
		app: container('app', 'sha256:app-image'),
		postgres: container('postgres', 'sha256:postgres-image'),
		redis: container('redis', 'sha256:redis-image'),
	};
	const state = {
		captureCalls: 0,
		replaceAtCapture: 0,
		capture() {
			this.captureCalls += 1;
			const value = structuredClone(base);
			if (this.captureCalls === this.replaceAtCapture) value.app.containerId = 'sha256:replacement-app';
			return value;
		},
	};
	state.capture = state.capture.bind(state);
	return state;
}

function container(service, imageId) {
	return {
		containerId: `sha256:${service}-container`,
		name: `/faithlog-${service}`,
		imageId,
		startedAt: '2026-07-16T00:00:00Z',
		composeProject: 'faithlog-frontend-latest',
		composeService: service,
		publishedPorts: service === 'app'
			? { '8080/tcp': [{ HostIp: '0.0.0.0', HostPort: '28080' }] }
			: {},
	};
}

function fakeApi(datasetId, adminEmail, clock) {
	const state = {
		users: [],
		calls: [],
		loginCalls: 0,
		signupCalls: 0,
		adminReadCalls: 0,
		deleteCalls: 0,
		issuedTokens: [],
		tokenExpByLogin: new Map(),
		failLoginAt: 0,
		failSignupAt: 0,
		signupRoleAt: 0,
		signupInactiveAt: 0,
		duplicateIdAt: 0,
		advanceAfterSignupTo: 0,
		advanceAfterDetailAt: 0,
		advanceAfterDetailTo: 0,
		seedCollision() {
			this.users.push(user(9000, `${datasetId} COLLISION`, `${datasetId.toLowerCase()}_collision@example.test`));
		},
	};
	state.fetch = async (url, options = {}) => {
		const parsed = new URL(url);
		const body = options.body ? JSON.parse(options.body) : null;
		state.calls.push({ path: parsed.pathname, method: options.method || 'GET' });
		if (parsed.pathname === '/api/v1/auth/login') {
			state.loginCalls += 1;
			assert.deepEqual(body, { email: adminEmail, password: 'runtime-admin-secret' });
			if (state.loginCalls === state.failLoginAt) return response(401, null, false, 'synthetic login failure');
			const token = jwt(state.tokenExpByLogin.get(state.loginCalls) ?? 100_000, state.loginCalls);
			state.issuedTokens.push(token);
			return response(200, { accessToken: token });
		}
		if (parsed.pathname === '/api/v1/users/me') return response(200, { userId: 7000, email: adminEmail });
		if (parsed.pathname === '/api/v1/auth/signup') {
			state.signupCalls += 1;
			if (state.signupCalls === state.failSignupAt) return response(500, null, false, 'synthetic signup failure');
			const id = state.signupCalls === state.duplicateIdAt ? state.users.at(-1).userId : 10_000 + state.signupCalls;
			const created = user(id, body.name, body.email, {
				role: state.signupCalls === state.signupRoleAt ? 'ADMIN' : 'USER',
				isActive: state.signupCalls !== state.signupInactiveAt,
			});
			state.users.push(created);
			if (state.signupCalls === 1000 && state.advanceAfterSignupTo) clock.now = state.advanceAfterSignupTo;
			return response(201, {
				id: created.userId,
				name: created.name,
				email: created.email,
				role: created.role,
				isActive: created.isActive,
			});
		}
		if (parsed.pathname === '/api/v1/admin/users') {
			state.adminReadCalls += 1;
			if (!authorized(options.headers?.Authorization, clock.now)) return response(401, null, false, 'expired token');
			const name = parsed.searchParams.get('name')?.toLowerCase();
			const email = parsed.searchParams.get('email')?.toLowerCase();
			const filtered = state.users.filter((entry) => (!name || entry.name.toLowerCase().includes(name))
				&& (!email || entry.email.toLowerCase().includes(email)));
			const page = Number(parsed.searchParams.get('page') || 0);
			const size = Number(parsed.searchParams.get('size') || 20);
			return response(200, {
				content: filtered.slice(page * size, (page + 1) * size),
				page,
				size,
				totalElements: filtered.length,
				totalPages: Math.ceil(filtered.length / size),
			});
		}
		const detail = parsed.pathname.match(/^\/api\/v1\/admin\/users\/(\d+)$/);
		if (detail) {
			state.adminReadCalls += 1;
			if (!authorized(options.headers?.Authorization, clock.now)) return response(401, null, false, 'expired token');
			const detailCall = state.adminReadCalls - 2;
			const result = response(200, state.users.find(({ userId }) => userId === Number(detail[1])));
			if (detailCall === state.advanceAfterDetailAt) clock.now = state.advanceAfterDetailTo;
			return result;
		}
		if (options.method === 'DELETE') state.deleteCalls += 1;
		return response(404, null, false, 'unexpected fake request');
	};
	return state;
}

function jwt(exp, sequence) {
	const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
	return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ exp, sequence })}.signature`;
}

function authorized(header, now) {
	if (typeof header !== 'string' || !header.startsWith('Bearer ')) return false;
	try {
		const payload = JSON.parse(Buffer.from(header.slice(7).split('.')[1], 'base64url').toString('utf8'));
		return Number.isSafeInteger(payload.exp) && payload.exp > now;
	} catch {
		return false;
	}
}

function user(userId, name, email, overrides = {}) {
	return { userId, name, email, role: 'USER', isActive: true, campuses: [], ...overrides };
}

function response(status, data, success = true, message = 'ok') {
	return new Response(JSON.stringify({ success, code: success ? 'SUCCESS' : 'ERROR', message, data }), {
		status,
		headers: { 'Content-Type': 'application/json' },
	});
}

function readRejection(reportDirectory) {
	return JSON.parse(fs.readFileSync(path.join(reportDirectory, 'first-rejection.json'), 'utf8'));
}

function escapeRegExp(value) {
	return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
