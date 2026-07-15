import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const ROLES = ['app', 'postgres', 'redis'];
const FULL_CONTAINER_ID = /^[a-f0-9]{64}$/;
const CONTAINER_KEYS = [
	'role', 'containerId', 'cpuPercent', 'memoryUsedBytes', 'memoryLimitBytes', 'memoryPercent',
];

export function validateDockerResourceEvidence({
	samples,
	expectedContainerIds,
	measuredStart,
	measuredEnd,
	samplingIntervalSeconds,
}) {
	const startMs = timestampMs(measuredStart, 'measuredStart');
	const endMs = timestampMs(measuredEnd, 'measuredEnd');
	const interval = positiveFinite(samplingIntervalSeconds, 'Docker stats sampling interval');
	if (startMs > endMs) {
		throw new Error('Docker resource measured window is reversed.');
	}
	assertExactKeys(expectedContainerIds, ROLES, 'expected Docker container IDs');
	for (const role of ROLES) {
		assertFullContainerId(expectedContainerIds[role], `Expected ${role} container identity`);
	}
	if (!Array.isArray(samples) || samples.length < 2) {
		throw new Error('Docker resource evidence requires at least two sample points.');
	}

	let previousMs;
	for (const [index, sample] of samples.entries()) {
		assertExactKeys(sample, ['capturedAt', 'containers'], `Docker sample ${index}`);
		const currentMs = timestampMs(sample.capturedAt, `Docker sample ${index}.capturedAt`);
		if (previousMs !== undefined) {
			if (currentMs <= previousMs) {
				throw new Error('Docker resource sample timestamps must be strictly monotonic without duplicates.');
			}
			if ((currentMs - previousMs) / 1000 > interval) {
				throw new Error('Docker resource sample gap exceeds the approved sampling interval.');
			}
		}
		previousMs = currentMs;
		validateContainers(sample.containers, expectedContainerIds, index);
	}

	const firstMs = timestampMs(samples[0].capturedAt, 'first Docker sample');
	const lastMs = timestampMs(samples.at(-1).capturedAt, 'last Docker sample');
	if (firstMs > startMs) {
		throw new Error('Docker resource evidence does not cover measuredStart.');
	}
	if (lastMs < endMs) {
		throw new Error('Docker resource evidence does not cover measuredEnd.');
	}
	return {
		status: 'valid-supporting-resource-evidence',
		sampleCount: samples.length,
		measuredStart,
		measuredEnd,
		samplingIntervalSeconds: interval,
	};
}

export function normalizeDockerStats({capturedAt, expectedContainerIds, rawStats}) {
	timestampMs(capturedAt, 'Docker sample capturedAt');
	assertExactKeys(expectedContainerIds, ROLES, 'expected Docker container IDs');
	for (const role of ROLES) {
		assertFullContainerId(expectedContainerIds[role], `Expected ${role} container identity`);
	}
	if (!Array.isArray(rawStats) || rawStats.length !== ROLES.length) {
		throw new Error('Docker stats must contain exactly app, PostgreSQL, and Redis rows.');
	}
	const containers = ROLES.map((role) => {
		const expectedId = expectedContainerIds[role];
		const matches = rawStats.filter((raw) => rawIdentifierMatches(raw, expectedId));
		if (matches.length !== 1) {
			throw new Error(`Docker stats cannot be bound exactly to the ${role} container identity.`);
		}
		const raw = matches[0];
		const [memoryUsedBytes, memoryLimitBytes] = parseMemoryUsage(raw.MemUsage);
		if (memoryLimitBytes <= 0 || memoryUsedBytes > memoryLimitBytes) {
			throw new Error(`${role} Docker memory usage and limit are invalid.`);
		}
		const memoryPercent = (memoryUsedBytes / memoryLimitBytes) * 100;
		const renderedMemoryPercent = parseRenderedPercent(raw.MemPerc, `${role} memory`);
		if (Number(memoryPercent.toFixed(renderedMemoryPercent.decimalPlaces))
			!== renderedMemoryPercent.value) {
			throw new Error(`${role} Docker memory percentage contradicts MemUsage used/limit evidence.`);
		}
		return {
			role,
			containerId: expectedId,
			cpuPercent: parsePercent(raw.CPUPerc, `${role} CPU`),
			memoryUsedBytes,
			memoryLimitBytes,
			memoryPercent,
		};
	});
	return {capturedAt, containers};
}

function validateContainers(containers, expectedContainerIds, sampleIndex) {
	if (!Array.isArray(containers) || containers.length !== ROLES.length) {
		throw new Error(`Docker sample ${sampleIndex} requires exactly app and PostgreSQL containers.`);
	}
	const byRole = Object.fromEntries(containers.map((container) => [container?.role, container]));
	if (Object.keys(byRole).length !== ROLES.length) {
		throw new Error(`Docker sample ${sampleIndex} contains duplicate or unknown container roles.`);
	}
	for (const role of ROLES) {
		const container = byRole[role];
		assertExactKeys(container, CONTAINER_KEYS, `Docker sample ${sampleIndex}.${role}`);
		if (container.role !== role || container.containerId !== expectedContainerIds[role]) {
			throw new Error(`Docker sample ${sampleIndex}.${role} container identity does not match.`);
		}
		for (const name of ['cpuPercent', 'memoryUsedBytes', 'memoryLimitBytes', 'memoryPercent']) {
			if (typeof container[name] !== 'number' || !Number.isFinite(container[name]) || container[name] < 0) {
				throw new Error(`Docker sample ${sampleIndex}.${role}.${name} must be a non-negative finite number.`);
			}
		}
		for (const name of ['memoryUsedBytes', 'memoryLimitBytes']) {
			if (!Number.isSafeInteger(container[name])) {
				throw new Error(`Docker sample ${sampleIndex}.${role}.${name} must be a safe integer byte value.`);
			}
		}
		if (container.memoryLimitBytes <= 0
			|| container.memoryUsedBytes > container.memoryLimitBytes
			|| container.memoryPercent > 100
			|| container.memoryPercent !== (container.memoryUsedBytes / container.memoryLimitBytes) * 100) {
			throw new Error(`Docker sample ${sampleIndex}.${role} RAM values are invalid.`);
		}
	}
}

function rawIdentifierMatches(raw, expectedId) {
	if (!raw || !FULL_CONTAINER_ID.test(expectedId)) {
		return false;
	}
	return typeof raw.ID === 'string'
		&& FULL_CONTAINER_ID.test(raw.ID)
		&& raw.ID === expectedId;
}

function parseMemoryUsage(value) {
	if (typeof value !== 'string') {
		throw new Error('Docker MemUsage must be a string.');
	}
	const parts = value.split('/').map((part) => part.trim());
	if (parts.length !== 2) {
		throw new Error('Docker MemUsage must contain used and limit values.');
	}
	return parts.map(parseByteSize);
}

function parseByteSize(value) {
	const match = /^(\d+(?:\.\d+)?)\s*(B|kB|MB|GB|TB|KiB|MiB|GiB|TiB)$/i.exec(value);
	if (!match) {
		throw new Error(`Unsupported Docker memory size: ${value}`);
	}
	const unit = match[2].toLowerCase();
	const factors = {
		b: 1n, kb: 1000n, mb: 1000n ** 2n, gb: 1000n ** 3n, tb: 1000n ** 4n,
		kib: 1024n, mib: 1024n ** 2n, gib: 1024n ** 3n, tib: 1024n ** 4n,
	};
	const [whole, fraction = ''] = match[1].split('.');
	const scale = 10n ** BigInt(fraction.length);
	const scaledBytes = BigInt(`${whole}${fraction}`) * factors[unit];
	if (scaledBytes % scale !== 0n) {
		throw new Error(`Docker memory size must resolve to an integer byte value: ${value}`);
	}
	const bytes = scaledBytes / scale;
	if (bytes > BigInt(Number.MAX_SAFE_INTEGER)) {
		throw new Error(`Docker memory size exceeds the safe integer byte range: ${value}`);
	}
	return Number(bytes);
}

function assertFullContainerId(value, label) {
	if (typeof value !== 'string' || !FULL_CONTAINER_ID.test(value)) {
		throw new Error(`${label} must be a full 64-character lowercase hexadecimal container ID.`);
	}
}

function parsePercent(value, label) {
	if (typeof value !== 'string' || !value.endsWith('%')) {
		throw new Error(`${label} percentage is invalid.`);
	}
	return positiveFinite(Number(value.slice(0, -1)), `${label} percentage`, true);
}

function parseRenderedPercent(value, label) {
	if (typeof value !== 'string') {
		throw new Error(`${label} percentage is invalid.`);
	}
	const match = /^(\d+)(?:\.(\d+))?%$/.exec(value);
	if (!match) {
		throw new Error(`${label} percentage is invalid.`);
	}
	return {
		value: positiveFinite(Number(value.slice(0, -1)), `${label} percentage`, true),
		decimalPlaces: match[2]?.length ?? 0,
	};
}

function positiveFinite(value, label, allowZero = false) {
	const parsed = typeof value === 'number' ? value : Number(value);
	if (!Number.isFinite(parsed) || (allowZero ? parsed < 0 : parsed <= 0)) {
		throw new Error(`${label} must be ${allowZero ? 'non-negative' : 'positive'} and finite.`);
	}
	return parsed;
}

function timestampMs(value, label) {
	if (typeof value !== 'string'
		|| !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(value)
		|| !Number.isFinite(Date.parse(value))) {
		throw new Error(`${label} must be a valid timestamp.`);
	}
	return Date.parse(value);
}

function assertExactKeys(value, expected, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) {
		throw new Error(`${label} strict schema is missing.`);
	}
	const actual = Object.keys(value).sort();
	const required = [...expected].sort();
	if (actual.length !== required.length || actual.some((key, index) => key !== required[index])) {
		throw new Error(`${label} strict schema keys are invalid.`);
	}
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const mode = process.argv[2];
	if (mode === 'interval') {
		process.stdout.write(String(positiveFinite(process.argv[3], 'Docker stats sampling interval')));
	} else if (mode === 'normalize') {
		const identity = JSON.parse(await readFile(process.argv[3], 'utf8'));
		const rawStats = (process.env.RAW_DOCKER_STATS_JSONL ?? '')
			.split(/\r?\n/)
			.filter(Boolean)
			.map((line) => JSON.parse(line));
		const sample = normalizeDockerStats({
			capturedAt: process.env.CAPTURED_AT,
			expectedContainerIds: {app: identity.app.id, postgres: identity.postgres.id, redis: identity.redis.id},
			rawStats,
		});
		process.stdout.write(`${JSON.stringify(sample)}\n`);
	} else if (mode === 'validate') {
		const [evidencePath, identityPath, windowPath, interval] = process.argv.slice(3);
		const [evidenceText, identity, window] = await Promise.all([
			readFile(evidencePath, 'utf8'),
			readFile(identityPath, 'utf8').then((text) => JSON.parse(text)),
			readFile(windowPath, 'utf8').then((text) => JSON.parse(text)),
		]);
		const samples = evidenceText.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
		const result = validateDockerResourceEvidence({
			samples,
			expectedContainerIds: {app: identity.app.id, postgres: identity.postgres.id, redis: identity.redis.id},
			measuredStart: window.measuredStart,
			measuredEnd: window.measuredEnd,
			samplingIntervalSeconds: interval,
		});
		process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
	} else {
		throw new Error('Usage: docker-resource-evidence.mjs interval|normalize|validate');
	}
}
