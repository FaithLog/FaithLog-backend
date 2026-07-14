import assert from 'node:assert/strict';

const [durationSource, safetySource, nowSource] = process.argv.slice(2);

try {
	const token = process.env.PERF_ACCESS_TOKEN;
	assert.ok(token, 'Runtime access token is required for lifetime validation.');
	const segments = token.split('.');
	assert.ok(segments.length >= 2, 'Runtime access token must be a JWT.');
	const payload = JSON.parse(Buffer.from(segments[1], 'base64url').toString('utf8'));
	assert.ok(Number.isInteger(payload.exp), 'Runtime access token exp must be an integer epoch second.');

	const durationSeconds = parseDuration(durationSource);
	const safetySeconds = Number(safetySource);
	const nowEpochSeconds = Number(nowSource);
	assert.ok(Number.isInteger(safetySeconds) && safetySeconds >= 0,
		'TOKEN_EXPIRY_SAFETY_SECONDS must be a non-negative integer.');
	assert.ok(Number.isInteger(nowEpochSeconds) && nowEpochSeconds > 0, 'Current epoch seconds must be valid.');
	const requiredSeconds = Math.ceil(durationSeconds) + safetySeconds;
	const remainingSeconds = payload.exp - nowEpochSeconds;
	assert.ok(
		remainingSeconds >= requiredSeconds,
		`Measured token lifetime is insufficient: ${remainingSeconds}s remaining, ${requiredSeconds}s required before expiry.`,
	);
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function parseDuration(source) {
	assert.ok(source, 'MEASURED_DURATION is required.');
	const units = {ms: 0.001, s: 1, m: 60, h: 3600, d: 86400};
	const pattern = /(\d+(?:\.\d+)?)(ms|s|m|h|d)/gy;
	let total = 0;
	let consumed = 0;
	for (let match = pattern.exec(source); match; match = pattern.exec(source)) {
		total += Number(match[1]) * units[match[2]];
		consumed = pattern.lastIndex;
	}
	assert.equal(consumed, source.length, `Unsupported k6 duration: ${source}`);
	assert.ok(total > 0, 'MEASURED_DURATION must be positive.');
	return total;
}
