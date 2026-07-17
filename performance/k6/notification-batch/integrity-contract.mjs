import assert from 'node:assert/strict';

const DECIMAL = /^(0|[1-9]\d*)$/;
const SIGNED_DECIMAL = /^-?(0|[1-9]\d*)$/;
const FULL_CONTAINER_ID = /^[a-f0-9]{64}$/;
const MEMORY_PERCENT_ROUNDING_TOLERANCE = 0.01;

export function canonicalDecimal(value, label) {
	assert.ok(typeof value === 'string' && DECIMAL.test(value), `${label} must be a canonical decimal string`);
	return BigInt(value);
}

export function decimalDelta(before, after, label) {
	const left = canonicalDecimal(before, `${label}.before`);
	const right = canonicalDecimal(after, `${label}.after`);
	assert.ok(right >= left, `${label} must be monotonic`);
	return String(right - left);
}

export function validatePgStatStatements(before, after) {
	for (const [label, value] of [['before', before], ['after', after]]) {
		assert.ok(value && typeof value === 'object' && !Array.isArray(value), `pgss ${label} must be an object`);
		assert.equal(typeof value.available, 'boolean', `pgss ${label}.available must be boolean`);
		assert.ok(Array.isArray(value.rows), `pgss ${label}.rows must be an array`);
	}
	assert.equal(after.available, before.available, 'pg_stat_statements availability drift');
	if (!before.available) {
		assert.deepEqual(Object.keys(before).sort(), ['available', 'reason', 'rows']);
		assert.deepEqual(Object.keys(after).sort(), ['available', 'reason', 'rows']);
		assert.equal(before.reason, 'extension-not-installed');
		assert.equal(after.reason, before.reason);
		assert.equal(before.rows.length, 0);
		assert.equal(after.rows.length, 0);
		return { available: false, deltas: [] };
	}
	for (const value of [before, after]) {
		assert.deepEqual(Object.keys(value).sort(), ['available', 'databaseId', 'rows', 'statsReset']);
		canonicalDecimal(value.databaseId, 'pgss databaseId');
		assert.ok(value.statsReset === null || Number.isFinite(Date.parse(value.statsReset)),
			'pgss statsReset must be null or an ISO timestamp');
	}
	assert.equal(after.databaseId, before.databaseId, 'pgss database drift');
	assert.equal(after.statsReset, before.statsReset, 'pgss stats reset drift');
	const rowMap = (rows, label) => {
		const result = new Map();
		for (const row of rows) {
			assert.deepEqual(Object.keys(row).sort(), [
				'calls', 'databaseId', 'queryId', 'topLevel', 'totalExecTimeMicros', 'userId',
			]);
			canonicalDecimal(row.userId, 'pgss userId');
			canonicalDecimal(row.databaseId, 'pgss row databaseId');
			assert.equal(row.databaseId, before.databaseId, 'pgss current database filter');
			assert.ok(typeof row.queryId === 'string' && SIGNED_DECIMAL.test(row.queryId), 'pgss queryId invalid');
			assert.equal(typeof row.topLevel, 'boolean', 'pgss topLevel invalid');
			canonicalDecimal(row.calls, 'pgss calls');
			canonicalDecimal(row.totalExecTimeMicros, 'pgss totalExecTimeMicros');
			const key = `${row.userId}:${row.databaseId}:${row.queryId}:${row.topLevel}`;
			assert.equal(result.has(key), false, `pgss duplicate ${label} row`);
			result.set(key, row);
		}
		return result;
	};
	const previous = rowMap(before.rows, 'before');
	const current = rowMap(after.rows, 'after');
	for (const key of previous.keys()) assert.ok(current.has(key), 'pgss before row disappeared');
	return {
		available: true,
		deltas: [...current.entries()].map(([key, row]) => {
			const left = previous.get(key);
			return {
				key,
				calls: decimalDelta(left?.calls ?? '0', row.calls, `pgss ${key} calls`),
				totalExecTimeMicros: decimalDelta(
					left?.totalExecTimeMicros ?? '0', row.totalExecTimeMicros, `pgss ${key} totalExecTimeMicros`,
				),
			};
		}),
	};
}

export function parseDockerStatsDisplay(raw) {
	const parts = String(raw).split(',');
	assert.equal(parts.length, 3, 'Docker stats display must contain CPU, memory usage, and memory percent');
	const cpuPercent = percent(parts[0], 'CPU', false);
	const memoryPercent = percent(parts[2], 'memory', true);
	const quantities = parts[1].split('/').map((value) => value.trim());
	assert.equal(quantities.length, 2, 'Docker memory usage must contain used and limit');
	const memoryUsedBytes = bytes(quantities[0], 'memory used');
	const memoryLimitBytes = bytes(quantities[1], 'memory limit');
	assert.ok(BigInt(memoryLimitBytes) > 0n, 'memory limit must be positive');
	assert.ok(BigInt(memoryUsedBytes) <= BigInt(memoryLimitBytes), 'memory used must not exceed limit');
	return { cpuPercent, memoryUsedBytes, memoryLimitBytes, memoryPercent };
}

export function validateResourceSamples(samples, expected, window) {
	assert.ok(Array.isArray(samples), 'resource samples must be an array');
	assert.deepEqual(Object.keys(expected).sort(), ['postgres', 'redis']);
	assert.notEqual(expected.postgres.name, expected.redis.name, 'resource container names must be distinct');
	assert.notEqual(expected.postgres.id, expected.redis.id, 'resource container IDs must be distinct');
	const expectedByComponent = new Map(Object.entries(expected));
	assert.ok(Number.isSafeInteger(window.maxGapMilliseconds) && window.maxGapMilliseconds > 0,
		'resource max gap must be positive');
	const start = Date.parse(window.workloadStartedAt);
	const finish = Date.parse(window.workloadFinishedAt);
	assert.ok(Number.isFinite(start) && Number.isFinite(finish) && start < finish, 'resource window invalid');
	const instants = new Map();
	const peaks = {};
	let previousCaptured = null;
	for (const row of samples) {
		assert.deepEqual(Object.keys(row).sort(), [
			'capturedAt', 'component', 'containerId', 'containerName', 'cpuPercent',
			'memoryLimitBytes', 'memoryPercent', 'memoryUsedBytes',
		]);
		const captured = Date.parse(row.capturedAt);
		assert.ok(Number.isFinite(captured), 'resource timestamp invalid');
		assert.ok(previousCaptured === null || captured >= previousCaptured,
			'resource rows must be timestamp-monotonic');
		previousCaptured = captured;
		const target = expectedByComponent.get(row.component);
		assert.ok(target, 'resource component set is not exact');
		assert.equal(row.containerName, target.name, 'resource container name identity mismatch');
		assert.equal(row.containerId, target.id, 'resource container full ID identity mismatch');
		assert.match(row.containerId, FULL_CONTAINER_ID, 'resource container ID must be full 64-hex identity');
		assert.ok(typeof row.cpuPercent === 'number' && Number.isFinite(row.cpuPercent) && row.cpuPercent >= 0,
			'resource CPU invalid');
		const used = canonicalDecimal(row.memoryUsedBytes, 'resource memory used');
		const limit = canonicalDecimal(row.memoryLimitBytes, 'resource memory limit');
		assert.ok(limit > 0n && used <= limit, 'resource memory bounds invalid');
		assert.ok(typeof row.memoryPercent === 'number' && Number.isFinite(row.memoryPercent)
			&& row.memoryPercent >= 0 && row.memoryPercent <= 100, 'resource memory percent invalid');
		const expectedMemoryPercent = (Number(used) / Number(limit)) * 100;
		assert.ok(Number.isFinite(expectedMemoryPercent)
			&& Math.abs(row.memoryPercent - expectedMemoryPercent) <= MEMORY_PERCENT_ROUNDING_TOLERANCE,
			'resource memory bytes-percent mismatch');
		const components = instants.get(row.capturedAt) ?? new Set();
		assert.equal(components.has(row.component), false, 'duplicate resource component at sample instant');
		components.add(row.component);
		instants.set(row.capturedAt, components);
		const peak = peaks[row.component] ?? { cpuPercent: 0, memoryUsedBytes: '0', memoryPercent: 0, sampleCount: 0 };
		peak.cpuPercent = Math.max(peak.cpuPercent, row.cpuPercent);
		if (used > BigInt(peak.memoryUsedBytes)) peak.memoryUsedBytes = String(used);
		peak.memoryPercent = Math.max(peak.memoryPercent, row.memoryPercent);
		peak.sampleCount += 1;
		peaks[row.component] = peak;
	}
	assert.ok(instants.size >= 2, 'at least two resource sample instants are required');
	for (const components of instants.values()) {
		assert.deepEqual([...components].sort(), ['postgres', 'redis'], 'resource exact container set mismatch');
	}
	const timestamps = [...instants.keys()].sort((left, right) => Date.parse(left) - Date.parse(right));
	for (let index = 1; index < timestamps.length; index += 1) {
		const gap = Date.parse(timestamps[index]) - Date.parse(timestamps[index - 1]);
		assert.ok(gap > 0 && gap <= window.maxGapMilliseconds, 'resource cadence gap invalid');
	}
	assert.ok(Date.parse(timestamps[0]) <= start && Date.parse(timestamps.at(-1)) >= finish,
		'resource lifecycle window coverage invalid');
	return { sampleInstants: instants.size, peaks };
}

function percent(raw, label, maximum100) {
	const match = /^(0|[1-9]\d*)(?:\.(\d+))?%$/.exec(String(raw).trim());
	assert.ok(match, `${label} percent invalid`);
	const result = Number(String(raw).trim().slice(0, -1));
	assert.ok(Number.isFinite(result) && result >= 0 && (!maximum100 || result <= 100), `${label} percent invalid`);
	return result;
}

function bytes(raw, label) {
	const match = /^(0|[1-9]\d*)(?:\.(\d+))?\s*(B|kB|MB|GB|TB|KiB|MiB|GiB|TiB)$/.exec(raw);
	assert.ok(match, `${label} display invalid`);
	const fraction = match[2] ?? '';
	const numerator = BigInt(match[1] + fraction);
	const denominator = 10n ** BigInt(fraction.length);
	const multiplier = {
		B: 1n, kB: 1000n, MB: 1000000n, GB: 1000000000n, TB: 1000000000000n,
		KiB: 1024n, MiB: 1048576n, GiB: 1073741824n, TiB: 1099511627776n,
	}[match[3]];
	return String((numerator * multiplier + (denominator / 2n)) / denominator);
}
