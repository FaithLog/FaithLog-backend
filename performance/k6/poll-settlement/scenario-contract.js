export function requireExactBaseUrl(value) {
	if (value !== 'http://127.0.0.1:28080') throw new Error('BASE_URL must exactly match http://127.0.0.1:28080.');
	return value;
}

export function requireTokenCoverage(expSeconds, nowMs, maxDurationSeconds, safetySeconds) {
	for (const [value, label] of [[expSeconds, 'exp'], [nowMs, 'now'], [maxDurationSeconds, 'maxDuration'], [safetySeconds, 'safety']]) {
		if (!Number.isFinite(value) || value < 0) throw new Error(`token expiry ${label} is invalid`);
	}
	if (!Number.isSafeInteger(expSeconds) || !Number.isSafeInteger(maxDurationSeconds) || !Number.isSafeInteger(safetySeconds)) throw new Error('token expiry inputs must be safe integers');
	const requiredUntilMs = nowMs + (maxDurationSeconds + safetySeconds) * 1000;
	if (expSeconds * 1000 < requiredUntilMs) throw new Error('token expiry does not cover the measured window');
	return expSeconds;
}
