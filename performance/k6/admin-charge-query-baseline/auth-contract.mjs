import {pathToFileURL} from 'node:url';

export function parseK6DurationSeconds(value) {
	if (typeof value !== 'string' || value.length === 0) {
		throw new Error('k6 duration is required.');
	}
	const unitSeconds = {ms: 0.001, s: 1, m: 60, h: 3600, d: 86400};
	const pattern = /(\d+(?:\.\d+)?)(ms|s|m|h|d)/g;
	let cursor = 0;
	let seconds = 0;
	for (const match of value.matchAll(pattern)) {
		if (match.index !== cursor) {
			throw new Error(`Unsupported k6 duration: ${value}`);
		}
		seconds += Number(match[1]) * unitSeconds[match[2]];
		cursor += match[0].length;
	}
	if (cursor !== value.length || !Number.isFinite(seconds) || seconds <= 0) {
		throw new Error(`Unsupported k6 duration: ${value}`);
	}
	return Math.ceil(seconds);
}

export function validateWorkloadInputs(input) {
	const warmupIterations = positiveIntegerString(input?.warmupIterations, 'WARMUP_ITERATIONS');
	const warmupVus = positiveIntegerString(input?.warmupVus, 'WARMUP_VUS');
	const measuredVus = positiveIntegerString(input?.measuredVus, 'MEASURED_VUS');
	const warmupMaxDuration = input?.warmupMaxDuration;
	const measuredDuration = input?.measuredDuration;
	return {
		warmupIterations,
		warmupVus,
		warmupMaxDuration,
		warmupMaxSeconds: parseK6DurationSeconds(warmupMaxDuration),
		measuredVus,
		measuredDuration,
		measuredSeconds: parseK6DurationSeconds(measuredDuration),
	};
}

export function validateLoginContract(login, requesterUserId, minimumTtlSeconds, nowEpochSeconds = nowSeconds()) {
	const expectedUserId = Number(requesterUserId);
	const actualUserId = Number(login?.user?.id);
	if (!Number.isSafeInteger(expectedUserId) || actualUserId !== expectedUserId) {
		throw new Error(`Runtime login user does not match REQUESTER_USER_ID ${requesterUserId}.`);
	}
	const accessToken = login?.accessToken;
	const responseTtl = Number(login?.accessTokenExpiresIn);
	if (typeof accessToken !== 'string' || accessToken.length === 0 || !Number.isFinite(responseTtl)) {
		throw new Error('Runtime login response is missing access token TTL evidence.');
	}
	const jwtExpiration = parseJwtExpiration(accessToken);
	const jwtRemaining = jwtExpiration - nowEpochSeconds;
	const remainingSeconds = Math.min(responseTtl, jwtRemaining);
	if (!Number.isFinite(remainingSeconds) || remainingSeconds < minimumTtlSeconds) {
		throw new Error(`Access token TTL ${remainingSeconds}s cannot cover required ${minimumTtlSeconds}s workload.`);
	}
	return {accessToken, actualUserId, jwtExpiration, remainingSeconds};
}

export function validateTokenRemaining(accessToken, minimumTtlSeconds, nowEpochSeconds = nowSeconds()) {
	const remainingSeconds = parseJwtExpiration(accessToken) - nowEpochSeconds;
	if (!Number.isFinite(remainingSeconds) || remainingSeconds < minimumTtlSeconds) {
		throw new Error(`Access token remaining TTL ${remainingSeconds}s cannot cover ${minimumTtlSeconds}s workload.`);
	}
	return remainingSeconds;
}

export function requiredTokenCoverageSeconds(phaseDurationSeconds, safetySeconds) {
	if (!Number.isSafeInteger(phaseDurationSeconds) || phaseDurationSeconds <= 0) {
		throw new Error('Phase duration seconds must be a positive integer.');
	}
	if (!Number.isSafeInteger(safetySeconds) || safetySeconds <= 0) {
		throw new Error('TOKEN_EXPIRY_SAFETY_SECONDS must be a positive runtime integer.');
	}
	const required = phaseDurationSeconds + safetySeconds;
	if (!Number.isSafeInteger(required)) {
		throw new Error('Required token coverage exceeds the safe integer range.');
	}
	return required;
}

function parseJwtExpiration(accessToken) {
	const parts = accessToken.split('.');
	if (parts.length < 2) {
		throw new Error('Access token does not contain a JWT expiration claim.');
	}
	let payload;
	try {
		payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
	} catch (_error) {
		throw new Error('Access token JWT payload cannot be decoded.');
	}
	const expiration = Number(payload.exp);
	if (!Number.isFinite(expiration)) {
		throw new Error('Access token JWT expiration claim is missing.');
	}
	return expiration;
}

function nowSeconds() {
	return Math.floor(Date.now() / 1000);
}

function positiveIntegerString(value, label) {
	if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
		throw new Error(`${label} must be a positive integer workload input.`);
	}
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed)) {
		throw new Error(`${label} exceeds the safe integer workload range.`);
	}
	return parsed;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	if (process.argv[2] === 'workload') {
		process.stdout.write(JSON.stringify(validateWorkloadInputs({
			warmupIterations: process.argv[3],
			warmupVus: process.argv[4],
			warmupMaxDuration: process.argv[5],
			measuredVus: process.argv[6],
			measuredDuration: process.argv[7],
		})));
	} else if (process.argv[2] === 'coverage') {
		process.stdout.write(String(requiredTokenCoverageSeconds(
			Number(process.argv[3]),
			Number(process.argv[4])
		)));
	} else {
		process.stdout.write(String(parseK6DurationSeconds(process.argv[2])));
	}
}
