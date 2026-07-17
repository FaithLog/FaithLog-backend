export function createFixtureAuthSession({
	login,
	nowEpochSeconds,
	safetyMarginSeconds,
	onFailure,
}) {
	if (typeof login !== 'function' || typeof nowEpochSeconds !== 'function' || typeof onFailure !== 'function') {
		throw new Error('Fixture auth session requires login, clock, and failure callbacks.');
	}
	if (!Number.isSafeInteger(safetyMarginSeconds) || safetyMarginSeconds <= 0) {
		throw new Error('Fixture token safety margin must be a positive safe integer.');
	}
	let accessToken;
	let refreshCount = 0;

	async function refresh(stage) {
		let candidate;
		try {
			candidate = await login();
			assertLifetime(candidate, nowEpochSeconds(), safetyMarginSeconds);
		} catch (error) {
			onFailure({ stage });
			const label = stage === 'fixture-initial-token' ? 'initialization' : 'refresh';
			throw new Error(`Fixture admin token ${label} failed.`, { cause: error });
		}
		accessToken = candidate;
		refreshCount += 1;
		return accessToken;
	}

	return {
		async initialize() {
			return refresh('fixture-initial-token');
		},
		async authorizedRequest({ stage, execute }) {
			if (typeof stage !== 'string' || stage.length === 0 || typeof execute !== 'function') {
				throw new Error('Fixture authorized request requires a stage and execute callback.');
			}
			if (!hasLifetime(accessToken, nowEpochSeconds(), safetyMarginSeconds)) {
				await refresh(accessToken ? `${stage}-token-refresh` : 'fixture-initial-token');
			}
			return execute(accessToken);
		},
		get refreshCount() {
			return refreshCount;
		},
	};
}

function assertLifetime(token, now, margin) {
	if (decodeExp(token) - now < margin) {
		throw new Error('Fixture admin token remaining lifetime is below the runtime margin.');
	}
}

function hasLifetime(token, now, margin) {
	try {
		return decodeExp(token) - now >= margin;
	} catch {
		return false;
	}
}

function decodeExp(token) {
	if (typeof token !== 'string') throw new Error('Fixture admin token must be a JWT.');
	const parts = token.split('.');
	if (parts.length !== 3 || parts.some((part) => part.length === 0)) {
		throw new Error('Fixture admin token must be a JWT.');
	}
	let payload;
	try {
		payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
	} catch (error) {
		throw new Error('Fixture admin token payload is invalid.', { cause: error });
	}
	if (!Number.isSafeInteger(payload?.exp) || payload.exp <= 0) {
		throw new Error('Fixture admin token exp is invalid.');
	}
	return payload.exp;
}
