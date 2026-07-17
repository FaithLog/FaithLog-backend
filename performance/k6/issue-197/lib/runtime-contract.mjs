import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateRuntimeContract(manifest, credentials, workload, nowEpochSeconds = Math.floor(Date.now() / 1000)) {
	const phaseContracts = [
		['WARMUP_VUS', workload.warmupVus, manifest.warmupUserIds?.length, workload.warmupMaxDuration],
		['MEASURED_VUS', workload.measuredVus, manifest.measuredUserIds?.length, workload.measuredMaxDuration],
		['ROLLBACK_VUS', workload.rollbackVus, manifest.rollbackUserIds?.length, workload.rollbackMaxDuration],
	];
	let phaseDurationSeconds = 0;
	for (const [label, vus, cohortSize, duration] of phaseContracts) {
		if (!Number.isInteger(Number(vus)) || Number(vus) < 1 || Number(vus) > cohortSize) {
			throw new Error(`${label} must be a positive integer no greater than its fixture cohort.`);
		}
		phaseDurationSeconds += durationSeconds(duration, label.replace('_VUS', '_MAX_DURATION'));
	}
	const safety = Number(workload.tokenTtlSafetySeconds);
	if (!Number.isInteger(safety) || safety < 1) {
		throw new Error('TOKEN_TTL_SAFETY_SECONDS must be a positive integer.');
	}
	const requiredTokenTtlSeconds = phaseDurationSeconds + safety;
	if (credentials.fixtureRunId !== manifest.fixtureRunId || !Array.isArray(credentials.tokens)) {
		throw new Error('runtime credential fixtureRunId/tokens do not match the manifest.');
	}
	const requiredUserIds = new Set([
		...(manifest.warmupUserIds || []),
		...(manifest.measuredUserIds || []),
		...(manifest.rollbackUserIds || []),
	].map(String));
	const credentialUserIds = new Set();
	for (const entry of credentials.tokens) {
		const credentialUserId = String(entry.userId);
		if (!requiredUserIds.has(credentialUserId) || credentialUserIds.has(credentialUserId)) {
			throw new Error(`JWT credential coverage is not exact for userId=${entry.userId}.`);
		}
		credentialUserIds.add(credentialUserId);
		const claims = decodeJwtPayload(entry.accessToken);
		if (String(claims.sub) !== String(entry.userId) || Number(claims.userId) !== Number(entry.userId)) {
			throw new Error(`JWT subject/userId mismatch for credential userId=${entry.userId}.`);
		}
		if (claims.tokenType !== 'ACCESS') {
			throw new Error(`JWT tokenType must be ACCESS for credential userId=${entry.userId}.`);
		}
		if (!Number.isInteger(claims.exp) || claims.exp - nowEpochSeconds < requiredTokenTtlSeconds) {
			throw new Error(`JWT remaining TTL is insufficient for credential userId=${entry.userId}.`);
		}
	}
	if (credentialUserIds.size !== requiredUserIds.size) {
		throw new Error('JWT credential coverage does not include every fixture user exactly once.');
	}
	return { requiredTokenTtlSeconds, credentialCount: credentials.tokens.length };
}

export function validatePublishedTarget(baseUrl, publishedPort) {
	let url;
	try {
		url = new URL(baseUrl);
	} catch (error) {
		throw new Error('BASE_URL must be an absolute local HTTP URL.');
	}
	const port = Number(publishedPort);
	if (url.protocol !== 'http:' || !['127.0.0.1', '[::1]'].includes(url.hostname) || url.pathname !== '/' || url.search || url.hash) {
		throw new Error('BASE_URL must target numeric loopback over plain HTTP with no path/query/fragment.');
	}
	if (!Number.isInteger(port) || port < 1 || Number(url.port) !== port) {
		throw new Error('BASE_URL port must equal the inspected APP_CONTAINER published port.');
	}
	return { host: url.hostname, port };
}

export function validateNumericLoopbackHost(host, label = 'runtime host') {
	if (!['127.0.0.1', '::1'].includes(host)) {
		throw new Error(`${label} must be an explicit numeric loopback host.`);
	}
	return host;
}

export function validateExpectedApplicationIdentity(expected, actual) {
	const revisionPattern = /^[a-f0-9]{40}$/;
	const imagePattern = /^sha256:[a-f0-9]{64}$/;
	const digestPattern = /^[a-f0-9]{64}$/;
	for (const [label, value, pattern] of [
		['expected revision', expected.revision, revisionPattern], ['actual revision', actual.revision, revisionPattern],
		['expected image ID', expected.imageId, imagePattern], ['actual image ID', actual.imageId, imagePattern],
		['expected app JAR SHA-256', expected.jarSha256, digestPattern], ['actual app JAR SHA-256', actual.jarSha256, digestPattern],
		['expected API contract SHA-256', expected.apiContractSha256, digestPattern],
		['actual API contract SHA-256', actual.apiContractSha256, digestPattern],
	]) {
		if (typeof value !== 'string' || !pattern.test(value)) throw new Error(`${label} has an invalid immutable identity format.`);
	}
	for (const field of ['revision', 'imageId', 'jarSha256', 'apiContractSha256']) {
		if (expected[field] !== actual[field]) throw new Error(`Actual app ${field} does not match the runtime-approved identity.`);
	}
	return actual;
}

export function durationSeconds(value, label) {
	if (typeof value !== 'string' || !/^[1-9]\d*(?:ms|s|m|h)$/.test(value)) {
		throw new Error(`${label} must be an explicit positive k6 duration using one unit (ms, s, m, or h).`);
	}
	const amount = Number.parseInt(value, 10);
	const unit = value.slice(String(amount).length);
	const multipliers = { ms: 0.001, s: 1, m: 60, h: 3600 };
	return Math.ceil(amount * multipliers[unit]);
}

function decodeJwtPayload(token) {
	if (typeof token !== 'string' || token.split('.').length !== 3) {
		throw new Error('runtime access token must use JWT compact serialization.');
	}
	try {
		return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
	} catch (error) {
		throw new Error('runtime access token JWT payload is not valid JSON.');
	}
}

async function main() {
	const [command, first, second] = process.argv.slice(2);
	if (command === 'validate-run') {
		const manifest = JSON.parse(fs.readFileSync(first, 'utf8'));
		const credentials = JSON.parse(fs.readFileSync(second, 'utf8'));
		const result = validateRuntimeContract(manifest, credentials, {
			warmupVus: Number(process.env.WARMUP_VUS),
			measuredVus: Number(process.env.MEASURED_VUS),
			rollbackVus: Number(process.env.ROLLBACK_VUS),
			warmupMaxDuration: process.env.WARMUP_MAX_DURATION,
			measuredMaxDuration: process.env.MEASURED_MAX_DURATION,
			rollbackMaxDuration: process.env.ROLLBACK_MAX_DURATION,
			tokenTtlSafetySeconds: Number(process.env.TOKEN_TTL_SAFETY_SECONDS),
		});
		process.stdout.write(`${JSON.stringify(result)}\n`);
		return;
	}
	if (command === 'validate-target') {
		process.stdout.write(`${JSON.stringify(validatePublishedTarget(first, second))}\n`);
		return;
	}
	if (command === 'validate-host') {
		process.stdout.write(`${validateNumericLoopbackHost(first, second)}\n`);
		return;
	}
	if (command === 'validate-app-identity') {
		const values = process.argv.slice(3);
		if (values.length !== 8) throw new Error('validate-app-identity requires four expected and four actual values.');
		const [expectedRevision, expectedImageId, expectedJarSha256, expectedApiContractSha256,
			actualRevision, actualImageId, actualJarSha256, actualApiContractSha256] = values;
		process.stdout.write(`${JSON.stringify(validateExpectedApplicationIdentity({
			revision: expectedRevision, imageId: expectedImageId, jarSha256: expectedJarSha256, apiContractSha256: expectedApiContractSha256,
		}, {
			revision: actualRevision, imageId: actualImageId, jarSha256: actualJarSha256, apiContractSha256: actualApiContractSha256,
		}))}\n`);
		return;
	}
	throw new Error('unsupported runtime-contract command.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
