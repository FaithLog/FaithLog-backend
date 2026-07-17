import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

export function parseDurationSeconds(value) {
	if (typeof value !== 'string' || value.length === 0) throw new Error('Phase duration is required.');
	let totalMilliseconds = 0;
	let consumed = '';
	const pattern = /(\d+(?:\.\d+)?)(ms|s|m|h)/g;
	for (const match of value.matchAll(pattern)) {
		if (match.index !== consumed.length) throw new Error(`Unsupported k6 duration: ${value}`);
		consumed += match[0];
		const multiplier = { ms: 1, s: 1000, m: 60000, h: 3600000 }[match[2]];
		totalMilliseconds += Number(match[1]) * multiplier;
	}
	if (consumed !== value || !Number.isFinite(totalMilliseconds) || totalMilliseconds <= 0) {
		throw new Error(`Unsupported k6 duration: ${value}`);
	}
	return totalMilliseconds / 1000;
}

export function assertTokenLifetime(token, duration, nowEpochSeconds = Date.now() / 1000, safetyMarginSeconds = 60) {
	if (typeof token !== 'string') throw new Error('JWT is required for lifetime validation.');
	const parts = token.split('.');
	if (parts.length !== 3) throw new Error('Access token is not a JWT.');
	let payload;
	try {
		payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
	} catch {
		throw new Error('Access token payload is not valid JSON.');
	}
	if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) throw new Error('Access token exp is missing.');
	const required = parseDurationSeconds(duration) + safetyMarginSeconds;
	const remaining = payload.exp - nowEpochSeconds;
	if (remaining < required) {
		throw new Error(`Access token remaining lifetime is insufficient: remaining=${remaining}s required=${required}s.`);
	}
	return { exp: payload.exp, remainingSeconds: remaining, requiredSeconds: required };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	assertTokenLifetime(process.env.PERF_ACCESS_TOKEN, process.env.PHASE_DURATION);
}
