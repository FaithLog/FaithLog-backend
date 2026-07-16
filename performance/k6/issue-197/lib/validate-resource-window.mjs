import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
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
		assert.equal(samples.length % ROLES.length, 0, 'resource samples must contain complete three-role snapshots');
		let previousSnapshotTime = Number.NEGATIVE_INFINITY;
		const byRole = Object.fromEntries(ROLES.map((role) => [role, []]));
		for (let offset = 0; offset < samples.length; offset += ROLES.length) {
			const snapshot = samples.slice(offset, offset + ROLES.length);
			assert.deepEqual(snapshot.map(({ role }) => role), ROLES, `snapshot ${offset / ROLES.length} must contain app, database, and redis exactly once`);
			const observedAt = snapshot[0].observedAt;
			const observedTime = strictTimestamp(observedAt, `samples[${offset}].observedAt`);
			assert.ok(observedTime > previousSnapshotTime, 'resource snapshot timestamps must be strictly monotonic and unique');
			previousSnapshotTime = observedTime;
			for (const [roleIndex, sample] of snapshot.entries()) {
				const index = offset + roleIndex;
				validateSample(sample, `samples[${index}]`);
				assert.equal(sample.observedAt, observedAt, `snapshot ${offset / ROLES.length} roles must share one capture timestamp`);
				const expectedContainerId = config[`${sample.role}ContainerId`];
				assert.equal(sample.containerId, expectedContainerId, `${sample.role} sample must match the approved immutable container ID`);
				byRole[sample.role].push({ ...sample, observedTime });
			}
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

function parseDockerSnapshot(lines, observedAt, appContainerId, databaseContainerId, redisContainerId) {
	strictTimestamp(observedAt, 'observedAt');
	const bindings = [
		{ role: 'app', containerId: appContainerId },
		{ role: 'database', containerId: databaseContainerId },
		{ role: 'redis', containerId: redisContainerId },
	];
	for (const { role, containerId } of bindings) fullContainerId(containerId, `${role}ContainerId`);
	assert.equal(new Set(bindings.map(({ containerId }) => containerId)).size, ROLES.length, 'Docker snapshot container IDs must be distinct');
	assert.equal(lines.length, ROLES.length, 'Docker stats snapshot must contain exactly three rows');
	const normalizedLines = normalizeDockerStatsFraming(lines);
	const parsedById = new Map();
	for (const [index, line] of normalizedLines.entries()) {
		assert.equal(typeof line, 'string', `Docker stats row ${index} must be a string`);
		const fields = line.split('|');
		assert.equal(fields.length, 3, `Docker stats row ${index} must have container, CPU, and memory fields`);
		const [containerId, cpu, memory] = fields;
		fullContainerId(containerId, `Docker stats row ${index} containerId`);
		assert.equal(parsedById.has(containerId), false, `Docker stats container ${containerId} must appear exactly once`);
		parsedById.set(containerId, { cpuPercent: parseCpuPercent(cpu), memoryBytes: parseMemoryBytes(memory) });
	}
	assert.deepEqual([...parsedById.keys()].sort(), bindings.map(({ containerId }) => containerId).sort(),
		'Docker stats snapshot must match the exact approved three-container set');
	return bindings.map(({ role, containerId }) => ({ observedAt, role, containerId, ...parsedById.get(containerId) }));
}

function normalizeDockerStatsFraming(lines) {
	const cursorHome = '\u001b[H';
	const eraseToEndOfLine = '\u001b[K';
	const framed = lines[0].startsWith(cursorHome);
	if (!framed) {
		for (const [index, line] of lines.entries()) assertNoControlBytes(line, `Docker stats row ${index}`);
		return lines;
	}
	assert.ok(lines.slice(1).every((line) => !line.startsWith(cursorHome)),
		'Docker stats ANSI framing may place CSI cursor-home only at the first snapshot row');
	assert.ok(lines.every((line) => line.endsWith(eraseToEndOfLine)),
		'Docker stats ANSI framing must erase to end-of-line on every snapshot row');
	const normalized = lines.map((line, index) => {
		const withoutHome = index === 0 ? line.slice(cursorHome.length) : line;
		return withoutHome.slice(0, -eraseToEndOfLine.length);
	});
	for (const [index, line] of normalized.entries()) assertNoControlBytes(line, `Docker stats row ${index}`);
	return normalized;
}

function assertNoControlBytes(value, label) {
	assert.doesNotMatch(value, /[\u0000-\u001f\u007f-\u009f]/,
		`${label} contains unsupported ANSI framing or control bytes`);
}

function appendDockerSnapshot(samplesPath, observedAt, lines, ...containerIds) {
	const samples = parseDockerSnapshot(lines, observedAt, ...containerIds);
	fs.appendFileSync(samplesPath, `${samples.map((sample) => JSON.stringify(sample)).join('\n')}\n`, { mode: 0o600 });
}

async function readStandardInputLines() {
	const lines = [];
	const reader = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
	for await (const line of reader) {
		assert.ok(line.length > 0, 'Docker stats stream must not contain empty rows');
		lines.push(line);
	}
	return lines;
}

async function streamDockerSnapshots(samplesPath, stopFile, maxGapSeconds, ...containerIds) {
	nonEmptyString(stopFile, 'stopFile');
	positiveFinite(maxGapSeconds, 'maxGapSeconds');
	assert.equal(fs.existsSync(stopFile), false, 'resource sampler stop marker must not exist before streaming starts');
	const reader = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
	const iterator = reader[Symbol.asyncIterator]();
	let pending = [];
	let stoppedAfterBoundarySnapshot = false;
	let snapshotDeadline = Date.now() + (maxGapSeconds * 1000);
	try {
		while (!stoppedAfterBoundarySnapshot) {
			const remainingMilliseconds = snapshotDeadline - Date.now();
			assert.ok(remainingMilliseconds > 0, 'Docker stats stream exceeded maxGapSeconds before a complete snapshot');
			const { value: line, done } = await nextWithDeadline(iterator, remainingMilliseconds);
			if (done) break;
			assert.ok(line.length > 0, 'Docker stats stream must not contain empty rows');
			pending.push(line);
			if (pending.length === ROLES.length) {
				appendDockerSnapshot(samplesPath, new Date().toISOString(), pending, ...containerIds);
				pending = [];
				snapshotDeadline = Date.now() + (maxGapSeconds * 1000);
				if (fs.existsSync(stopFile)) stoppedAfterBoundarySnapshot = true;
			}
		}
	} finally {
		reader.close();
		if (!stoppedAfterBoundarySnapshot) process.stdin.destroy();
	}
	assert.equal(pending.length, 0, 'Docker stats stream ended with an incomplete three-role snapshot');
	assert.equal(stoppedAfterBoundarySnapshot, true, 'Docker stats stream ended before the stop marker and final boundary snapshot');
}

async function nextWithDeadline(iterator, timeoutMilliseconds) {
	let timeoutId;
	try {
		return await Promise.race([
			iterator.next(),
			new Promise((_, reject) => {
				timeoutId = setTimeout(() => reject(new Error('Docker stats stream exceeded maxGapSeconds before a complete snapshot')), timeoutMilliseconds);
			}),
		]);
	} finally {
		clearTimeout(timeoutId);
	}
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
	if (command === 'append-snapshot') {
		const [samplesPath, observedAt, appContainerId, databaseContainerId, redisContainerId] = args;
		appendDockerSnapshot(samplesPath, observedAt, await readStandardInputLines(), appContainerId, databaseContainerId, redisContainerId);
		return;
	}
	if (command === 'stream-samples') {
		const [samplesPath, stopFile, maxGap, appContainerId, databaseContainerId, redisContainerId] = args;
		await streamDockerSnapshots(samplesPath, stopFile, Number(maxGap), appContainerId, databaseContainerId, redisContainerId);
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
