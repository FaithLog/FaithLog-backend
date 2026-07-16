import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const ROLES = ['app', 'postgres', 'redis'];
const FULL_CONTAINER_ID = /^[a-f0-9]{64}$/;
const CONTAINER_KEYS = [
	'role', 'containerId', 'cpuPercent', 'memoryUsed', 'memoryLimit', 'memoryPercent',
];
const MEMORY_RANGE_KEYS = ['displayed', 'minimumBytesInclusive', 'maximumBytesInclusive'];
const PERCENT_RANGE_KEYS = [
	'displayed', 'minimumNumeratorInclusive', 'maximumNumeratorExclusive', 'denominator',
];
const MAX_SAFE_BYTES = BigInt(Number.MAX_SAFE_INTEGER);

export function validateDockerResourceEvidence({
	samples,
	expectedContainerIds,
	measuredStart,
	measuredEnd,
	samplingIntervalSeconds,
	maximumGapSeconds,
}) {
	const startMs = timestampMs(measuredStart, 'measuredStart');
	const endMs = timestampMs(measuredEnd, 'measuredEnd');
	const {samplingIntervalSeconds: interval, maximumGapSeconds: maximumGap} = validateSamplingCadence({
		samplingIntervalSeconds,
		maximumGapSeconds,
	});
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
			if ((currentMs - previousMs) / 1000 > maximumGap) {
				throw new Error('Docker resource sample gap exceeds the approved maximum gap.');
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
		maximumGapSeconds: maximumGap,
	};
}

export function validateSamplingCadence({samplingIntervalSeconds, maximumGapSeconds}) {
	const interval = positiveFinite(samplingIntervalSeconds, 'Docker stats nominal sampling interval');
	const maximumGap = positiveFinite(maximumGapSeconds, 'Docker stats maximum sample gap');
	if (maximumGap < interval) {
		throw new Error('Docker stats maximum sample gap cannot be less than the nominal sampling interval.');
	}
	return {samplingIntervalSeconds: interval, maximumGapSeconds: maximumGap};
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
		const [memoryUsed, memoryLimit] = parseMemoryUsage(raw.MemUsage);
		const memoryPercent = parseRenderedPercent(raw.MemPerc, `${role} memory`);
		validateMemoryEvidence(memoryUsed, memoryLimit, memoryPercent, role);
		return {
			role,
			containerId: expectedId,
			cpuPercent: parsePercent(raw.CPUPerc, `${role} CPU`),
			memoryUsed,
			memoryLimit,
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
		if (typeof container.cpuPercent !== 'number'
			|| !Number.isFinite(container.cpuPercent)
			|| container.cpuPercent < 0) {
			throw new Error(`Docker sample ${sampleIndex}.${role}.cpuPercent must be a non-negative finite number.`);
		}
		validateNormalizedRange(container.memoryUsed, parseByteSize, MEMORY_RANGE_KEYS,
			`Docker sample ${sampleIndex}.${role}.memoryUsed`);
		validateNormalizedRange(container.memoryLimit, parseByteSize, MEMORY_RANGE_KEYS,
			`Docker sample ${sampleIndex}.${role}.memoryLimit`);
		validateNormalizedRange(container.memoryPercent, parseRenderedPercent, PERCENT_RANGE_KEYS,
			`Docker sample ${sampleIndex}.${role}.memoryPercent`);
		validateMemoryEvidence(container.memoryUsed, container.memoryLimit, container.memoryPercent,
			`Docker sample ${sampleIndex}.${role}`);
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
	if (whole.length + fraction.length > 32) {
		throw new Error(`Docker memory size has unsafe precision: ${value}`);
	}
	const scale = 10n ** BigInt(fraction.length);
	const displayed = BigInt(`${whole}${fraction}`);
	const denominator = 2n * scale;
	const factor = factors[unit];
	const minimumNumerator = (2n * displayed - 1n) * factor;
	const maximumNumeratorExclusive = (2n * displayed + 1n) * factor;
	const minimumBytesInclusive = minimumNumerator <= 0n ? 0n : ceilDivide(minimumNumerator, denominator);
	const maximumBytesInclusive = ceilDivide(maximumNumeratorExclusive, denominator) - 1n;
	if (maximumBytesInclusive > MAX_SAFE_BYTES) {
		throw new Error(`Docker memory size exceeds the safe integer byte range: ${value}`);
	}
	return {
		displayed: value,
		minimumBytesInclusive: minimumBytesInclusive.toString(),
		maximumBytesInclusive: maximumBytesInclusive.toString(),
	};
}

function assertFullContainerId(value, label) {
	if (typeof value !== 'string' || !FULL_CONTAINER_ID.test(value)) {
		throw new Error(`${label} must be a full 64-character lowercase hexadecimal container ID.`);
	}
}

function parsePercent(value, label) {
	if (typeof value !== 'string' || !/^(?:0|[1-9][0-9]*)(?:\.[0-9]+)?%$/.test(value)) {
		throw new Error(`${label} percentage is invalid.`);
	}
	return positiveFinite(Number(value.slice(0, -1)), `${label} percentage`, true);
}

function parseRenderedPercent(value, label) {
	if (typeof value !== 'string') {
		throw new Error(`${label} percentage is invalid.`);
	}
	const match = /^(0|[1-9][0-9]*)(?:\.(\d+))?%$/.exec(value);
	if (!match) {
		throw new Error(`${label} percentage is invalid.`);
	}
	const fraction = match[2] ?? '';
	if (match[1].length + fraction.length > 32) {
		throw new Error(`${label} percentage has unsafe precision.`);
	}
	const scale = 10n ** BigInt(fraction.length);
	const displayed = BigInt(`${match[1]}${fraction}`);
	if (displayed > 100n * scale) {
		throw new Error(`${label} percentage must be between 0 and 100.`);
	}
	const denominator = 2n * scale;
	return {
		displayed: value,
		minimumNumeratorInclusive: (displayed === 0n ? 0n : 2n * displayed - 1n).toString(),
		maximumNumeratorExclusive: (2n * displayed + 1n).toString(),
		denominator: denominator.toString(),
	};
}

function validateMemoryEvidence(memoryUsed, memoryLimit, memoryPercent, label) {
	const usedMinimum = strictDecimalString(memoryUsed?.minimumBytesInclusive, `${label} used minimum`);
	const usedMaximum = strictDecimalString(memoryUsed?.maximumBytesInclusive, `${label} used maximum`);
	const limitMinimum = strictDecimalString(memoryLimit?.minimumBytesInclusive, `${label} limit minimum`);
	const limitMaximum = strictDecimalString(memoryLimit?.maximumBytesInclusive, `${label} limit maximum`);
	if (usedMinimum > usedMaximum
		|| limitMinimum <= 0n
		|| limitMinimum > limitMaximum
		|| usedMinimum > limitMaximum
		|| limitMaximum > MAX_SAFE_BYTES) {
		throw new Error(`${label} Docker memory usage and limit ranges are invalid.`);
	}
	const percentMinimum = strictDecimalString(
		memoryPercent?.minimumNumeratorInclusive, `${label} percent minimum numerator`
	);
	const percentMaximum = strictDecimalString(
		memoryPercent?.maximumNumeratorExclusive, `${label} percent maximum numerator`
	);
	const percentDenominator = strictDecimalString(memoryPercent?.denominator, `${label} percent denominator`);
	if (percentDenominator <= 0n || percentMinimum >= percentMaximum) {
		throw new Error(`${label} Docker memory percentage rounding interval is invalid.`);
	}
	const possibleRatioMaximumReachesPercentMinimum =
		usedMaximum * 100n * percentDenominator >= percentMinimum * limitMinimum;
	const possibleRatioMinimumPrecedesPercentMaximum =
		usedMinimum * 100n * percentDenominator < percentMaximum * limitMaximum;
	if (!possibleRatioMaximumReachesPercentMinimum || !possibleRatioMinimumPrecedesPercentMaximum) {
		throw new Error(`${label} Docker memory percentage contradicts MemUsage rounding ranges.`);
	}
}

function validateNormalizedRange(value, parser, keys, label) {
	assertExactKeys(value, keys, label);
	const reparsed = parser(value.displayed, label);
	for (const key of keys) {
		if (value[key] !== reparsed[key]) {
			throw new Error(`${label} does not match its displayed rounding evidence.`);
		}
	}
}

function strictDecimalString(value, label) {
	if (typeof value !== 'string' || !/^(?:0|[1-9][0-9]*)$/.test(value) || value.length > 32) {
		throw new Error(`${label} must be a safe non-negative decimal string.`);
	}
	const parsed = BigInt(value);
	if (parsed > MAX_SAFE_BYTES) {
		throw new Error(`${label} exceeds the safe magnitude.`);
	}
	return parsed;
}

function ceilDivide(numerator, denominator) {
	return (numerator + denominator - 1n) / denominator;
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
	if (mode === 'cadence') {
		process.stdout.write(JSON.stringify(validateSamplingCadence({
			samplingIntervalSeconds: process.argv[3],
			maximumGapSeconds: process.argv[4],
		})));
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
		const [evidencePath, identityPath, windowPath, interval, maximumGap] = process.argv.slice(3);
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
			maximumGapSeconds: maximumGap,
		});
		process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
	} else {
		throw new Error('Usage: docker-resource-evidence.mjs cadence|normalize|validate');
	}
}
