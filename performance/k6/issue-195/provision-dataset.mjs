import fs from 'node:fs';
import path from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import { fileURLToPath } from 'node:url';
import { validateTargetIdentity } from './validate-target-identity.mjs';
import { captureContainerIdentity } from './runtime-container-identity.mjs';
export { sanitizedChildEnvironment } from './runtime-container-identity.mjs';

const contract = JSON.parse(fs.readFileSync(new URL('./scenario-contract.json', import.meta.url), 'utf8'));

export async function provisionDataset(options = {}) {
	const env = options.env ?? cliEnvironment();
	const fetchImpl = options.fetchImpl ?? fetch;
	const captureRuntimeIdentity = options.captureRuntimeIdentity
		?? (() => captureDockerRuntimeIdentity(env));
	const acquireLock = options.acquireLock ?? ((lockDirectory) => fs.mkdirSync(lockDirectory));
	const releaseLock = options.releaseLock ?? ((lockDirectory) => fs.rmdirSync(lockDirectory));
	const log = options.log ?? ((value) => process.stdout.write(`${JSON.stringify(value)}\n`));
	const nowEpochSeconds = options.nowEpochSeconds ?? (() => Math.floor(Date.now() / 1000));
	const inputs = validateInputs(env);
	const reportDirectory = createReportDirectory(inputs.reportRoot, inputs.datasetId);
	let stage = 'pre-lock-identity';
	let createdUserCount = 0;
	let lockDirectory;
	let lockAcquired = false;
	let verificationTokenRefreshCount = 0;

	try {
		const initialRuntimeIdentity = captureRuntimeIdentity();
		const targetIdentity = validateRuntimeTarget(initialRuntimeIdentity, inputs);
		lockDirectory = `/tmp/faithlog-performance-${targetIdentity.composeProject}.lock`;
		stage = 'runner-lock';
		acquireLock(lockDirectory);
		lockAcquired = true;

		stage = 'post-lock-identity';
		const postLockRuntimeIdentity = captureRuntimeIdentity();
		if (!isDeepStrictEqual(initialRuntimeIdentity, postLockRuntimeIdentity)) {
			throw new Error('Post-lock runtime identity changed before login or signup.');
		}

		stage = 'runtime-login';
		const accessToken = await loginAdmin(fetchImpl, inputs);
		const me = await apiRequest(fetchImpl, inputs.baseUrl, 'GET', '/api/v1/users/me', accessToken);
		const requesterUserId = requirePositiveSafeInteger(me.data?.id ?? me.data?.userId, 'runtime admin user id');

		stage = 'namespace-preflight';
		await assertFreshNamespace(fetchImpl, inputs, accessToken);

		stage = 'signup';
		const createdUsers = [];
		const userIds = new Set();
		const emails = new Set();
		for (let index = 1; index <= inputs.expectedActiveMembers; index += 1) {
			const suffix = String(index).padStart(4, '0');
			const name = `${inputs.datasetId} USER ${suffix}`;
			const email = `${inputs.datasetId.toLowerCase()}_${suffix}@example.test`;
			let response;
			try {
				response = await apiRequest(fetchImpl, inputs.baseUrl, 'POST', '/api/v1/auth/signup', null, {
					name,
					email,
					password: inputs.memberPassword,
				});
			} catch (error) {
				throw new Error(`Signup request failed at index ${index}.`, { cause: error });
			}
			createdUserCount += 1;
			const created = normalizeCreatedUser(response.data);
			if (created.name !== name || created.email.toLowerCase() !== email) {
				throw new Error(`Created user identity does not match requested namespace at index ${index}.`);
			}
			if (created.role !== 'USER') throw new Error('Created user role must be USER.');
			if (created.isActive !== true) throw new Error('Created user must be ACTIVE.');
			if (userIds.has(created.userId) || emails.has(created.email.toLowerCase())) {
				throw new Error('Created user identity must be unique.');
			}
			userIds.add(created.userId);
			emails.add(created.email.toLowerCase());
			createdUsers.push(created);
		}

		const verificationToken = {
			value: null,
			async refresh() {
				stage = 'verification-login';
				try {
					this.value = await loginAdmin(fetchImpl, inputs);
				} catch (error) {
					throw new Error('Verification admin login failed.', { cause: error });
				}
				verificationTokenRefreshCount += 1;
				stage = 'verification-token';
				assertVerificationTokenLifetime(
					this.value,
					nowEpochSeconds(),
					inputs.tokenSafetyMarginSeconds,
				);
				return this.value;
			},
			async current() {
				stage = 'verification-token';
				if (!hasVerificationTokenLifetime(
					this.value,
					nowEpochSeconds(),
					inputs.tokenSafetyMarginSeconds,
				)) return this.refresh();
				return this.value;
			},
		};
		await verificationToken.refresh();
		stage = 'dataset-verification';
		await verifyDataset(fetchImpl, inputs, verificationToken, requesterUserId, createdUsers, () => {
			stage = 'dataset-verification';
		});

		stage = 'final-identity';
		const finalRuntimeIdentity = captureRuntimeIdentity();
		if (!isDeepStrictEqual(initialRuntimeIdentity, finalRuntimeIdentity)) {
			throw new Error('Final runtime identity changed before manifest creation.');
		}

		stage = 'manifest';
		const manifest = {
			schemaVersion: 1,
			issue: 195,
			status: 'provisioned/not-measured',
			automaticAdoption: false,
			sourceCommit: inputs.sourceCommit,
			datasetId: inputs.datasetId,
			createdUserCount,
			expectedActiveMembers: inputs.expectedActiveMembers,
			verificationTokenRefreshCount,
			userIds: createdUsers.map(({ userId }) => userId),
			composeProject: targetIdentity.composeProject,
			targetIdentity,
			runtimeIdentity: initialRuntimeIdentity,
			mutationPolicy: 'additive signup only; no existing row update/delete',
			createdAt: new Date().toISOString(),
		};
		const manifestPath = path.join(reportDirectory, 'dataset-manifest.json');
		writeJsonExclusive(manifestPath, manifest);
		const result = { createdUserCount, manifestPath };
		log({
			status: manifest.status,
			datasetId: inputs.datasetId,
			createdUserCount,
			manifestPath,
		});
		return result;
	} catch (error) {
		writeFirstRejection(reportDirectory, {
			schemaVersion: 1,
			status: 'partial-non-reusable',
			automaticAdoption: false,
			reusable: false,
			automaticCleanup: false,
			datasetId: inputs.datasetId,
			stage,
			createdUserCount,
			expectedActiveMembers: inputs.expectedActiveMembers,
		});
		throw error;
	} finally {
		if (lockAcquired) releaseLock(lockDirectory);
	}
}

function cliEnvironment() {
	return {
		BASE_URL: process.env.BASE_URL,
		PERF_ADMIN_EMAIL: process.env.PERF_ADMIN_EMAIL,
		PERF_ADMIN_PASSWORD: process.env.PERF_ADMIN_PASSWORD,
		PERF_DATASET_MEMBER_PASSWORD: process.env.PERF_DATASET_MEMBER_PASSWORD,
		PERF_DATASET_ID: process.env.PERF_DATASET_ID,
		PERF_SOURCE_COMMIT: process.env.PERF_SOURCE_COMMIT,
		PERF_REPORT_ROOT: process.env.PERF_REPORT_ROOT,
		APP_CONTAINER_ID: process.env.APP_CONTAINER_ID,
		EXPECTED_APP_COMPOSE_SERVICE: process.env.EXPECTED_APP_COMPOSE_SERVICE,
		EXPECTED_APP_IMAGE_ID: process.env.EXPECTED_APP_IMAGE_ID,
		POSTGRES_CONTAINER_ID: process.env.POSTGRES_CONTAINER_ID,
		EXPECTED_POSTGRES_COMPOSE_SERVICE: process.env.EXPECTED_POSTGRES_COMPOSE_SERVICE,
		EXPECTED_POSTGRES_IMAGE_ID: process.env.EXPECTED_POSTGRES_IMAGE_ID,
		REDIS_CONTAINER_ID: process.env.REDIS_CONTAINER_ID,
		EXPECTED_REDIS_COMPOSE_SERVICE: process.env.EXPECTED_REDIS_COMPOSE_SERVICE,
		EXPECTED_REDIS_IMAGE_ID: process.env.EXPECTED_REDIS_IMAGE_ID,
		EXPECTED_ACTIVE_MEMBERS: process.env.EXPECTED_ACTIVE_MEMBERS,
		TOKEN_SAFETY_MARGIN_SECONDS: process.env.TOKEN_SAFETY_MARGIN_SECONDS,
	};
}

function validateInputs(env) {
	for (const name of [
		'BASE_URL',
		'PERF_ADMIN_EMAIL',
		'PERF_ADMIN_PASSWORD',
		'PERF_DATASET_MEMBER_PASSWORD',
		'PERF_DATASET_ID',
		'PERF_SOURCE_COMMIT',
		'PERF_REPORT_ROOT',
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
	]) requireNonEmptyString(env[name], name);
	const baseUrl = env.BASE_URL.replace(/\/$/, '');
	const sourceCommit = env.PERF_SOURCE_COMMIT;
	const datasetId = env.PERF_DATASET_ID;
	const expectedActiveMembers = Number(env.EXPECTED_ACTIVE_MEMBERS);
	const tokenSafetyMarginSeconds = Number(env.TOKEN_SAFETY_MARGIN_SECONDS);
	if (sourceCommit !== contract.sourceIdentity.originDevelopCommit) {
		throw new Error('PERF_SOURCE_COMMIT must match the approved source identity.');
	}
	if (!Number.isSafeInteger(expectedActiveMembers)
		|| expectedActiveMembers !== contract.dataset.requiredActiveMembers) {
		throw new Error('EXPECTED_ACTIVE_MEMBERS must exactly match the approved scenario contract.');
	}
	if (!Number.isSafeInteger(tokenSafetyMarginSeconds) || tokenSafetyMarginSeconds <= 0) {
		throw new Error('TOKEN_SAFETY_MARGIN_SECONDS must be a positive safe integer runtime input.');
	}
	if (!/^PERF_[A-Za-z0-9_]+$/.test(datasetId)) {
		throw new Error('PERF_DATASET_ID must be a fresh PERF_ identifier.');
	}
	return {
		baseUrl,
		adminEmail: env.PERF_ADMIN_EMAIL,
		adminPassword: env.PERF_ADMIN_PASSWORD,
		memberPassword: env.PERF_DATASET_MEMBER_PASSWORD,
		datasetId,
		sourceCommit,
		reportRoot: path.resolve(env.PERF_REPORT_ROOT),
		expectedActiveMembers,
		tokenSafetyMarginSeconds,
		appContainerId: env.APP_CONTAINER_ID,
		expectedAppService: env.EXPECTED_APP_COMPOSE_SERVICE,
		expectedAppImageId: env.EXPECTED_APP_IMAGE_ID,
		postgresContainerId: env.POSTGRES_CONTAINER_ID,
		expectedPostgresService: env.EXPECTED_POSTGRES_COMPOSE_SERVICE,
		expectedPostgresImageId: env.EXPECTED_POSTGRES_IMAGE_ID,
		redisContainerId: env.REDIS_CONTAINER_ID,
		expectedRedisService: env.EXPECTED_REDIS_COMPOSE_SERVICE,
		expectedRedisImageId: env.EXPECTED_REDIS_IMAGE_ID,
	};
}

function createReportDirectory(reportRoot, datasetId) {
	const directory = path.join(reportRoot, datasetId, '_provisioning');
	fs.mkdirSync(path.dirname(directory), { recursive: true });
	try {
		fs.mkdirSync(directory);
	} catch (error) {
		if (error?.code === 'EEXIST') {
			throw new Error(`Provisioning report directory already exists; dataset ID is not reusable: ${directory}`);
		}
		throw error;
	}
	return directory;
}

function captureDockerRuntimeIdentity(env) {
	return {
		app: captureContainerIdentity(env.APP_CONTAINER_ID),
		postgres: captureContainerIdentity(env.POSTGRES_CONTAINER_ID),
		redis: captureContainerIdentity(env.REDIS_CONTAINER_ID),
	};
}

function validateRuntimeTarget(identity, inputs) {
	if (!isObject(identity) || !isObject(identity.app) || !isObject(identity.postgres) || !isObject(identity.redis)) {
		throw new Error('Runtime identity must contain app, postgres, and redis objects.');
	}
	for (const [label, container, expectedId] of [
		['app', identity.app, inputs.appContainerId],
		['postgres', identity.postgres, inputs.postgresContainerId],
		['redis', identity.redis, inputs.redisContainerId],
	]) {
		for (const key of ['containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService']) {
			requireNonEmptyString(container[key], `${label}.${key}`);
		}
		if (!isObject(container.publishedPorts)) throw new Error(`${label}.publishedPorts must be an object.`);
		if (container.containerId !== expectedId) throw new Error(`${label} full container ID does not match runtime input.`);
	}
	const composeProject = identity.app.composeProject;
	if (!/^[A-Za-z0-9_.-]+$/.test(composeProject)
		|| identity.postgres.composeProject !== composeProject
		|| identity.redis.composeProject !== composeProject) {
		throw new Error('App, PostgreSQL, and Redis must share one safe Compose project identity.');
	}
	validateTargetIdentity({
		baseUrl: inputs.baseUrl,
		appContainerId: identity.app.containerId,
		appImageId: identity.app.imageId,
		expectedAppImageId: inputs.expectedAppImageId,
		appComposeService: identity.app.composeService,
		expectedAppComposeService: inputs.expectedAppService,
		appPublishedPortsJson: JSON.stringify(identity.app.publishedPorts),
		postgresContainerId: identity.postgres.containerId,
		postgresComposeService: identity.postgres.composeService,
		expectedPostgresComposeService: inputs.expectedPostgresService,
		postgresImageId: identity.postgres.imageId,
		expectedPostgresImageId: inputs.expectedPostgresImageId,
		redisContainerId: identity.redis.containerId,
		redisComposeService: identity.redis.composeService,
		expectedRedisComposeService: inputs.expectedRedisService,
		redisImageId: identity.redis.imageId,
		expectedRedisImageId: inputs.expectedRedisImageId,
	});
	return {
		composeProject,
		baseUrl: inputs.baseUrl,
		app: identity.app,
		postgres: identity.postgres,
		redis: identity.redis,
	};
}

async function assertFreshNamespace(fetchImpl, inputs, accessToken) {
	for (const query of [
		`name=${encodeURIComponent(inputs.datasetId)}`,
		`email=${encodeURIComponent(inputs.datasetId)}`,
	]) {
		const response = await apiRequest(
			fetchImpl,
			inputs.baseUrl,
			'GET',
			`/api/v1/admin/users?${query}&page=0&size=1&sort=id,asc`,
			accessToken,
		);
		if (!Number.isSafeInteger(response.data?.totalElements) || response.data.totalElements !== 0) {
			throw new Error('Dataset namespace is not fresh; use a new PERF_DATASET_ID.');
		}
	}
}

async function verifyDataset(fetchImpl, inputs, verificationToken, requesterUserId, createdUsers, markVerificationStage) {
	const summaries = [];
	let page = 0;
	let totalPages = 1;
	do {
		const accessToken = await verificationToken.current();
		markVerificationStage();
		const response = await apiRequest(
			fetchImpl,
			inputs.baseUrl,
			'GET',
			`/api/v1/admin/users?name=${encodeURIComponent(inputs.datasetId)}&page=${page}&size=100&sort=id,asc`,
			accessToken,
		);
		if (!Array.isArray(response.data?.content)
			|| !Number.isSafeInteger(response.data.totalElements)
			|| !Number.isSafeInteger(response.data.totalPages)
			|| response.data.totalPages < 1) {
			throw new Error('Dataset verification page shape is invalid.');
		}
		summaries.push(...response.data.content);
		totalPages = response.data.totalPages;
		page += 1;
	} while (page < totalPages);
	if (summaries.length !== inputs.expectedActiveMembers
		|| summaries.length !== createdUsers.length) {
		throw new Error('Dataset verification must resolve exactly 1,000 created users.');
	}
	const expectedIds = new Set(createdUsers.map(({ userId }) => userId));
	const observedIds = new Set();
	const observedEmails = new Set();
	const needle = inputs.datasetId.toLowerCase();
	for (let index = 0; index < summaries.length; index += 1) {
		const summary = summaries[index];
		const userId = requirePositiveSafeInteger(summary?.userId ?? summary?.id, 'dataset summary user id');
		const email = requireNonEmptyString(summary?.email, 'dataset summary email').toLowerCase();
		if (!expectedIds.has(userId) || observedIds.has(userId) || observedEmails.has(email)) {
			throw new Error('Dataset verification found a missing, duplicate, or external user.');
		}
		if (userId === requesterUserId) throw new Error('Runtime ADMIN must be excluded from the dataset.');
		if (summary.role !== 'USER') throw new Error('Verified dataset user role must be USER.');
		if (!requireNonEmptyString(summary.name, 'dataset summary name').toLowerCase().includes(needle)
			|| !email.includes(needle)) {
			throw new Error('Verified dataset user is outside the fresh namespace.');
		}
		const accessToken = await verificationToken.current();
		markVerificationStage();
		const detail = await apiRequest(
			fetchImpl,
			inputs.baseUrl,
			'GET',
			`/api/v1/admin/users/${userId}`,
			accessToken,
		);
		if (detail.data?.role !== 'USER') throw new Error('Verified dataset detail role must be USER.');
		if (detail.data?.isActive !== true) throw new Error('Verified dataset detail must be ACTIVE.');
		observedIds.add(userId);
		observedEmails.add(email);
	}
	if (observedIds.size !== inputs.expectedActiveMembers) {
		throw new Error('Dataset verification cardinality is not exact.');
	}
}

async function loginAdmin(fetchImpl, inputs) {
	const response = await apiRequest(fetchImpl, inputs.baseUrl, 'POST', '/api/v1/auth/login', null, {
		email: inputs.adminEmail,
		password: inputs.adminPassword,
	});
	return requireNonEmptyString(response.data?.accessToken, 'runtime admin access token');
}

function assertVerificationTokenLifetime(token, nowEpochSeconds, safetyMarginSeconds) {
	const remaining = decodeJwtExp(token) - nowEpochSeconds;
	if (remaining < safetyMarginSeconds) {
		throw new Error(`Verification token lifetime is below the runtime safety margin of ${safetyMarginSeconds} seconds.`);
	}
}

function hasVerificationTokenLifetime(token, nowEpochSeconds, safetyMarginSeconds) {
	try {
		return decodeJwtExp(token) - nowEpochSeconds >= safetyMarginSeconds;
	} catch {
		return false;
	}
}

function decodeJwtExp(token) {
	const parts = requireNonEmptyString(token, 'verification access token').split('.');
	if (parts.length !== 3 || parts.some((part) => part.length === 0)) {
		throw new Error('Verification access token must be a three-part JWT.');
	}
	let payload;
	try {
		payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
	} catch (error) {
		throw new Error('Verification access token payload is invalid.', { cause: error });
	}
	if (!Number.isSafeInteger(payload?.exp) || payload.exp <= 0) {
		throw new Error('Verification access token exp must be a positive safe integer.');
	}
	return payload.exp;
}

function normalizeCreatedUser(value) {
	return {
		userId: requirePositiveSafeInteger(value?.id ?? value?.userId, 'created user id'),
		name: requireNonEmptyString(value?.name, 'created user name'),
		email: requireNonEmptyString(value?.email, 'created user email'),
		role: requireNonEmptyString(value?.role, 'created user role'),
		isActive: value?.isActive,
	};
}

async function apiRequest(fetchImpl, baseUrl, method, pathName, accessToken, body) {
	const headers = { 'Content-Type': 'application/json' };
	if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
	const response = await fetchImpl(`${baseUrl}${pathName}`, {
		method,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	let parsed;
	try {
		const text = await response.text();
		parsed = text ? JSON.parse(text) : null;
	} catch (error) {
		throw new Error(`${method} request returned an invalid response envelope.`, { cause: error });
	}
	if (!response.ok || parsed?.success !== true || !isObject(parsed.data)) {
		throw new Error(`${method} request failed with status ${response.status}.`);
	}
	return parsed;
}

function writeFirstRejection(reportDirectory, rejection) {
	try {
		writeJsonExclusive(path.join(reportDirectory, 'first-rejection.json'), rejection);
	} catch (error) {
		if (error?.code !== 'EEXIST') throw error;
	}
}

function writeJsonExclusive(filePath, value) {
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

function requireNonEmptyString(value, label) {
	if (typeof value !== 'string' || value.length === 0) throw new Error(`${label} must be a non-empty string.`);
	return value;
}

function requirePositiveSafeInteger(value, label) {
	if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`${label} must be a positive safe integer.`);
	return value;
}

function isObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) await provisionDataset();
