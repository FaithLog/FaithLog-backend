import fs from 'node:fs';

const [scenario, caseName, phase, outputPath] = process.argv.slice(2);
if (!scenario || !caseName || !['before', 'after'].includes(phase) || !outputPath) {
	throw new Error('scenario, caseName, before|after phase, and outputPath are required.');
}

const snapshot = {
	scenario,
	case: caseName,
	phase,
	capturedAt: new Date().toISOString(),
	coverage: 'boundary-only',
	containers: [
		container('app', 'APP'),
		container('postgres', 'POSTGRES'),
		container('redis', 'REDIS'),
	],
};
fs.appendFileSync(outputPath, `${JSON.stringify(snapshot)}\n`, { encoding: 'utf8', mode: 0o600 });

function container(role, prefix) {
	const containerId = required(`${prefix}_ACTUAL_ID`);
	const name = required(`${prefix}_ACTUAL_NAME`);
	const imageId = required(`${prefix}_IMAGE_ID`);
	const startedAt = required(`${prefix}_STARTED_AT`);
	const raw = JSON.parse(required(`${prefix}_STATS_JSON`));
	return {
		role,
		containerId,
		name,
		imageId,
		startedAt,
		cpuPercent: parsePercent(raw.CPUPerc, `${role}.CPUPerc`),
		memoryBytes: parseMemoryBytes(raw.MemUsage, `${role}.MemUsage`),
	};
}

function required(name) {
	const value = process.env[name];
	if (!value) throw new Error(`${name} is required.`);
	return value;
}

function parsePercent(value, label) {
	if (typeof value !== 'string' || !/^\d+(?:\.\d+)?%$/.test(value)) {
		throw new Error(`${label} must be a non-negative Docker percentage.`);
	}
	const parsed = Number(value.slice(0, -1));
	if (!Number.isFinite(parsed) || parsed < 0) throw new Error(`${label} is invalid.`);
	return parsed;
}

function parseMemoryBytes(value, label) {
	if (typeof value !== 'string') throw new Error(`${label} must be a Docker memory usage string.`);
	const used = value.split('/')[0]?.trim();
	const match = /^(\d+(?:\.\d+)?)\s*(B|kB|KB|KiB|MB|MiB|GB|GiB|TB|TiB)$/.exec(used || '');
	if (!match) throw new Error(`${label} has an unsupported format.`);
	const factors = {
		B: 1,
		kB: 1_000,
		KB: 1_000,
		KiB: 1_024,
		MB: 1_000_000,
		MiB: 1_048_576,
		GB: 1_000_000_000,
		GiB: 1_073_741_824,
		TB: 1_000_000_000_000,
		TiB: 1_099_511_627_776,
	};
	const scaled = Number(match[1]) * factors[match[2]];
	const parsed = Math.round(scaled);
	if (!Number.isFinite(scaled) || scaled < 0 || !Number.isSafeInteger(parsed)) {
		throw new Error(`${label} cannot be represented as non-negative safe bytes.`);
	}
	return parsed;
}
