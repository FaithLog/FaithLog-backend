import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const CONFIG_KEYS = [
	'appContainerId', 'databaseContainerId', 'redisContainerId', 'maxGapSeconds',
	'measuredEnd', 'measuredStart', 'samplingIntervalSeconds',
].sort();
const SAMPLE_KEYS = ['containerId', 'cpuPercent', 'memoryBytes', 'observedAt', 'role'].sort();
const ROLES = ['app', 'database', 'redis'];

export function validateResourceSettings(samplingIntervalSeconds, maxGapSeconds) {
	positiveFinite(samplingIntervalSeconds, 'samplingIntervalSeconds');
	positiveFinite(maxGapSeconds, 'maxGapSeconds');
	assert.ok(maxGapSeconds >= samplingIntervalSeconds, 'maxGapSeconds must be at least samplingIntervalSeconds');
	return { samplingIntervalSeconds, maxGapSeconds };
}

export function validateResourceWindow(samples, config) {
	const failures = [];
	try {
		validateConfig(config);
		assert.ok(Array.isArray(samples), 'resource samples must be an array');
		assert.ok(samples.length > 0, 'resource samples must not be empty');
		let previousGlobalTime = Number.NEGATIVE_INFINITY;
		const byRole = Object.fromEntries(ROLES.map((role) => [role, []]));
		for (const [index, sample] of samples.entries()) {
			validateSample(sample, `samples[${index}]`);
			const observedTime = strictTimestamp(sample.observedAt, `samples[${index}].observedAt`);
			assert.ok(observedTime > previousGlobalTime, 'resource sample timestamps must be globally monotonic and unique');
			previousGlobalTime = observedTime;
			const expectedContainerId = config[`${sample.role}ContainerId`];
			assert.equal(sample.containerId, expectedContainerId, `${sample.role} sample must match the approved immutable container ID`);
			byRole[sample.role].push({ ...sample, observedTime });
		}

		const start = strictTimestamp(config.measuredStart, 'measuredStart');
		const end = strictTimestamp(config.measuredEnd, 'measuredEnd');
		assert.ok(end > start, 'measuredEnd must be after measuredStart');
		const maxGapMilliseconds = config.maxGapSeconds * 1000;
		for (const role of ROLES) {
			const roleSamples = byRole[role];
			assert.ok(roleSamples.length >= 2, `${role} requires at least two resource samples`);
			assert.ok(roleSamples[0].observedTime <= start, `${role} first sample must be at or before measuredStart`);
			assert.ok(roleSamples.at(-1).observedTime >= end, `${role} last sample must be at or after measuredEnd`);
			for (let index = 1; index < roleSamples.length; index += 1) {
				const gap = roleSamples[index].observedTime - roleSamples[index - 1].observedTime;
				assert.ok(gap <= maxGapMilliseconds, `${role} resource sample gap exceeds maxGapSeconds`);
			}
		}

		return {
			status: 'adoptable', adoptable: true,
			config,
			rawSampleCount: samples.length,
			byRole: Object.fromEntries(ROLES.map((role) => [role, summarizeRole(byRole[role])])),
			failures,
		};
	} catch (error) {
		failures.push({ name: 'resourceWindow', expected: 'strict measured-window coverage', actual: error.message });
		return { status: 'contaminated', adoptable: false, config, rawSampleCount: Array.isArray(samples) ? samples.length : 0, byRole: {}, failures };
	}
}

function validateConfig(config) {
	assertExactKeys(config, CONFIG_KEYS, 'resource config');
	validateResourceSettings(config.samplingIntervalSeconds, config.maxGapSeconds);
	for (const field of ['appContainerId', 'databaseContainerId', 'redisContainerId']) fullContainerId(config[field], field);
	assert.equal(new Set([config.appContainerId, config.databaseContainerId, config.redisContainerId]).size, 3,
		'app, database, and Redis full Docker container IDs must be distinct');
	strictTimestamp(config.measuredStart, 'measuredStart');
	strictTimestamp(config.measuredEnd, 'measuredEnd');
}

function validateSample(sample, label) {
	assertExactKeys(sample, SAMPLE_KEYS, label);
	assert.ok(ROLES.includes(sample.role), `${label}.role must be app, database, or redis`);
	fullContainerId(sample.containerId, `${label}.containerId`);
	strictTimestamp(sample.observedAt, `${label}.observedAt`);
	assert.equal(typeof sample.cpuPercent, 'number', `${label}.cpuPercent must be a number`);
	assert.ok(Number.isFinite(sample.cpuPercent) && sample.cpuPercent >= 0, `${label}.cpuPercent must be finite and non-negative`);
	assert.ok(Number.isSafeInteger(sample.memoryBytes) && sample.memoryBytes >= 0, `${label}.memoryBytes must be a non-negative safe integer`);
}

function summarizeRole(samples) {
	return {
		containerId: samples[0].containerId,
		sampleCount: samples.length,
		firstObservedAt: samples[0].observedAt,
		lastObservedAt: samples.at(-1).observedAt,
		maxCpuPercent: Math.max(...samples.map(({ cpuPercent }) => cpuPercent)),
		maxMemoryBytes: Math.max(...samples.map(({ memoryBytes }) => memoryBytes)),
	};
}

function parseCpuPercent(value) {
	assert.equal(typeof value, 'string', 'Docker CPU value must be a string');
	assert.match(value, /^\d+(?:\.\d+)?%$/, 'Docker CPU value must use a non-negative percent format');
	const parsed = Number(value.slice(0, -1));
	assert.ok(Number.isFinite(parsed), 'Docker CPU value must be finite');
	return parsed;
}

function parseMemoryBytes(value) {
	assert.equal(typeof value, 'string', 'Docker memory value must be a string');
	const used = value.split('/')[0].trim();
	const match = used.match(/^(\d+(?:\.\d+)?)\s*([KMGTP]?i?B)$/i);
	assert.ok(match, 'Docker memory value must contain a supported byte unit');
	const unit = match[2].toUpperCase();
	const powers = { B: 0, KB: 1, KIB: 1, MB: 2, MIB: 2, GB: 3, GIB: 3, TB: 4, TIB: 4, PB: 5, PIB: 5 };
	const parsed = Math.round(Number(match[1]) * (1024 ** powers[unit]));
	assert.ok(Number.isSafeInteger(parsed) && parsed >= 0, 'Docker memory value must resolve to non-negative whole bytes');
	return parsed;
}

function strictTimestamp(value, label) {
	assert.equal(typeof value, 'string', `${label} must be an ISO timestamp string`);
	const parsed = Date.parse(value);
	assert.ok(Number.isFinite(parsed) && new Date(parsed).toISOString() === value, `${label} must be a canonical ISO timestamp`);
	return parsed;
}

function positiveFinite(value, label) {
	assert.equal(typeof value, 'number', `${label} must be a number`);
	assert.ok(Number.isFinite(value) && value > 0, `${label} must be positive and finite`);
}

function nonEmptyString(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.ok(value.length > 0, `${label} must not be empty`);
}

function fullContainerId(value, label) {
	nonEmptyString(value, label);
	assert.match(value, /^[a-f0-9]{64}$/, `${label} must be a full Docker container ID`);
}

function assertExactKeys(value, expectedKeys, label) {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${label} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), expectedKeys, `${label} must have exact keys`);
}

function readJsonLines(filePath) {
	const source = fs.readFileSync(filePath, 'utf8').trim();
	return source ? source.split('\n').map((line) => JSON.parse(line)) : [];
}

function writeSecureJson(filePath, value) {
	fs.mkdirSync(path.dirname(filePath), { recursive: true });
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}

async function main() {
	const [command, ...args] = process.argv.slice(2);
	if (command === 'validate-settings') {
		validateResourceSettings(Number(args[0]), Number(args[1]));
		return;
	}
	if (command === 'append-sample') {
		const [samplesPath, observedAt, role, containerId, cpu, memory] = args;
		const sample = { observedAt, role, containerId, cpuPercent: parseCpuPercent(cpu), memoryBytes: parseMemoryBytes(memory) };
		validateSample(sample, 'sample');
		fs.appendFileSync(samplesPath, `${JSON.stringify(sample)}\n`, { mode: 0o600 });
		return;
	}
	if (command === 'write-config') {
		const [outputPath, interval, maxGap, measuredStart, measuredEnd, appContainerId, databaseContainerId, redisContainerId] = args;
		const config = {
			samplingIntervalSeconds: Number(interval), maxGapSeconds: Number(maxGap), measuredStart, measuredEnd,
			appContainerId, databaseContainerId, redisContainerId,
		};
		validateConfig(config);
		writeSecureJson(outputPath, config);
		return;
	}
	const [configPath, outputPath] = args;
	const samples = readJsonLines(command);
	const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
	const evidence = validateResourceWindow(samples, config);
	writeSecureJson(outputPath, evidence);
	if (!evidence.adoptable) throw new Error(`resource window is contaminated: ${JSON.stringify(evidence.failures)}`);
	process.stdout.write(`${JSON.stringify(evidence)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
