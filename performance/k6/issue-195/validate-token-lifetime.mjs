const warmupSeconds = parseDuration(process.env.WARMUP_DURATION, 'WARMUP_DURATION');
const measuredSeconds = parseDuration(process.env.MEASURED_DURATION, 'MEASURED_DURATION');
const safetySeconds = parseNonNegativeInteger(
	process.env.TOKEN_SAFETY_MARGIN_SECONDS,
	'TOKEN_SAFETY_MARGIN_SECONDS',
);
const phase = process.env.TOKEN_LIFETIME_PHASE || 'case';
if (!['case', 'measured'].includes(phase)) {
	throw new Error('TOKEN_LIFETIME_PHASE must be case or measured.');
}
const token = await readStdin();
const payload = decodePayload(token);
const now = resolveNow();
const remaining = payload.exp - now;
const idleControlSeconds = measuredSeconds;
const required = (phase === 'case'
	? warmupSeconds + idleControlSeconds + measuredSeconds
	: measuredSeconds) + safetySeconds;
const sufficient = phase === 'case' ? remaining >= required : remaining > required;
if (!Number.isInteger(payload.exp) || !sufficient) {
	throw new Error(`Access token remaining lifetime is insufficient: remaining=${remaining}s required=${required}s.`);
}

function parseDuration(value, name) {
	if (!value) {
		throw new Error(`${name} is required.`);
	}
	const units = { h: 3600, m: 60, s: 1 };
	let seconds = 0;
	let consumed = '';
	for (const match of value.matchAll(/(\d+)(h|m|s)/g)) {
		seconds += Number(match[1]) * units[match[2]];
		consumed += match[0];
	}
	if (consumed !== value || seconds <= 0) {
		throw new Error(`${name} must use positive k6 h/m/s duration syntax.`);
	}
	return seconds;
}

function parseNonNegativeInteger(value, name) {
	if (!/^\d+$/.test(value || '')) {
		throw new Error(`${name} must be a runtime non-negative integer.`);
	}
	return Number(value);
}

function decodePayload(token) {
	const parts = token.split('.');
	if (parts.length !== 3) {
		throw new Error('Access token must be a JWT with an exp claim.');
	}
	try {
		return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
	} catch (error) {
		throw new Error('Access token JWT payload is invalid.', { cause: error });
	}
}

function resolveNow() {
	if (process.env.TOKEN_CLOCK_EPOCH_SECONDS !== undefined) {
		if (process.env.PERF_CONTRACT_TEST !== '1') {
			throw new Error('TOKEN_CLOCK_EPOCH_SECONDS is test-only.');
		}
		return parseNonNegativeInteger(process.env.TOKEN_CLOCK_EPOCH_SECONDS, 'TOKEN_CLOCK_EPOCH_SECONDS');
	}
	return Math.floor(Date.now() / 1000);
}

async function readStdin() {
	let input = '';
	for await (const chunk of process.stdin) {
		input += chunk;
	}
	if (!input) {
		throw new Error('Access token is required on stdin.');
	}
	return input.trim();
}
