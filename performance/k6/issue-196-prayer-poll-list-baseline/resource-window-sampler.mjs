import assert from 'node:assert/strict';
import fs from 'node:fs';
import readline from 'node:readline';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROLES = ['app', 'database', 'redis'];

function fullContainerId(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.match(value, /^[a-f0-9]{64}$/, `${label} must be a full Docker container ID`);
}

function strictTimestamp(value, label) {
	assert.equal(typeof value, 'string', `${label} must be an ISO timestamp string`);
	const parsed = Date.parse(value);
	assert.ok(Number.isFinite(parsed) && new Date(parsed).toISOString() === value, `${label} must be a canonical ISO timestamp`);
}

function positiveFinite(value, label) {
	assert.ok(Number.isFinite(value) && value > 0, `${label} must be positive and finite`);
}

function parsePercent(value, label, maximum = Number.POSITIVE_INFINITY) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.match(value, /^\d+(?:\.\d+)?%$/, `${label} must use a non-negative percent format`);
	const parsed = Number(value.slice(0, -1));
	assert.ok(Number.isFinite(parsed) && parsed <= maximum, `${label} is outside the supported range`);
}

function parseMemoryBytes(value, label) {
	const match = value.match(/^(\d+(?:\.\d+)?)\s*([KMGTP]?i?B)$/i);
	assert.ok(match, `${label} must contain a supported byte unit`);
	const unit = match[2].toUpperCase();
	const powers = { B: 0, KB: 1, KIB: 1, MB: 2, MIB: 2, GB: 3, GIB: 3, TB: 4, TIB: 4, PB: 5, PIB: 5 };
	const parsed = Math.round(Number(match[1]) * (1024 ** powers[unit]));
	assert.ok(Number.isSafeInteger(parsed) && parsed >= 0, `${label} must resolve to non-negative safe bytes`);
	return parsed;
}

function validateMemoryUsage(value) {
	assert.equal(typeof value, 'string', 'Docker memory value must be a string');
	const match = value.match(/^([^\s]+) \/ ([^\s]+)$/);
	assert.ok(match, 'Docker memory value must contain used and limit values');
	const used = parseMemoryBytes(match[1], 'Docker used memory');
	const limit = parseMemoryBytes(match[2], 'Docker memory limit');
	assert.ok(limit > 0 && used <= limit, 'Docker memory usage must not exceed a positive limit');
}

function assertNoControlBytes(value, label) {
	assert.doesNotMatch(value, /[\u0000-\u001f\u007f-\u009f]/, `${label} contains unsupported ANSI framing or control bytes`);
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

function parseBindings(args) {
	assert.equal(args.length, ROLES.length * 2, 'container name and full ID are required for all three roles');
	const bindings = ROLES.map((role, index) => ({ role, name: args[index * 2], containerId: args[index * 2 + 1] }));
	for (const { role, name, containerId } of bindings) {
		assert.ok(typeof name === 'string' && name.length > 0, `${role} container name is required`);
		assertNoControlBytes(name, `${role} container name`);
		fullContainerId(containerId, `${role}ContainerId`);
	}
	assert.equal(new Set(bindings.map(({ name }) => name)).size, ROLES.length, 'container names must be distinct');
	assert.equal(new Set(bindings.map(({ containerId }) => containerId)).size, ROLES.length, 'container IDs must be distinct');
	return bindings;
}

function parseDockerSnapshot(lines, bindings) {
	assert.equal(lines.length, ROLES.length, 'Docker stats snapshot must contain exactly three rows');
	const parsedById = new Map();
	for (const [index, line] of normalizeDockerStatsFraming(lines).entries()) {
		const fields = line.split('|');
		assert.equal(fields.length, 4, `Docker stats row ${index} must have container, CPU, memory, and memory-percent fields`);
		const [containerId, cpu, memory, memoryPercent] = fields;
		fullContainerId(containerId, `Docker stats row ${index} containerId`);
		assert.equal(parsedById.has(containerId), false, `Docker stats container ${containerId} must appear exactly once`);
		parsePercent(cpu, 'Docker CPU value');
		validateMemoryUsage(memory);
		parsePercent(memoryPercent, 'Docker memory percent', 100);
		parsedById.set(containerId, { cpu, memory, memoryPercent });
	}
	assert.deepEqual([...parsedById.keys()].sort(), bindings.map(({ containerId }) => containerId).sort(),
		'Docker stats snapshot must match the exact approved three-container set');
	return bindings.map((binding) => ({ ...binding, ...parsedById.get(binding.containerId) }));
}

function appendDockerSnapshot(samplesPath, observedAt, lines, bindings) {
	strictTimestamp(observedAt, 'observedAt');
	const samples = parseDockerSnapshot(lines, bindings);
	const output = samples.map(({ name, containerId, cpu, memory, memoryPercent }) =>
		`${observedAt}\t${name}\t${containerId}\t${cpu}\t${memory}\t${memoryPercent}`).join('\n');
	fs.appendFileSync(samplesPath, `${output}\n`, { mode: 0o600 });
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

function consumeDockerDisplayLine(state, line) {
	const cursorHome = '\u001b[H';
	const eraseDisplay = '\u001b[J';
	const eraseToEndOfLine = '\u001b[K';
	if (state.expectsSeparator) {
		assert.equal(line, eraseToEndOfLine,
			'Docker stats display protocol requires exactly one standalone CSI erase-to-EOL separator between snapshots');
		state.expectsSeparator = false;
		return null;
	}
	const rowIndex = state.pending.length;
	if (rowIndex === 0) {
		const expectedPrefix = state.snapshotIndex === 0 ? cursorHome : `${eraseDisplay}${cursorHome}`;
		assert.ok(line.startsWith(expectedPrefix),
			`Docker stats display protocol requires ${state.snapshotIndex === 0 ? 'initial CSI cursor-home' : 'recurring CSI erase-display plus cursor-home'} at snapshot row 0`);
	}
	assert.ok(line.endsWith(eraseToEndOfLine),
		'Docker stats display protocol requires CSI erase-to-EOL at every data row suffix');
	state.pending.push(line);
	if (state.pending.length !== ROLES.length) return null;
	const snapshot = state.pending;
	state.pending = [];
	state.expectsSeparator = true;
	if (state.snapshotIndex > 0) snapshot[0] = snapshot[0].slice(eraseDisplay.length);
	state.snapshotIndex += 1;
	return snapshot;
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

async function streamDockerSnapshots(samplesPath, stopFile, maxGapSeconds, bindings) {
	assert.ok(typeof stopFile === 'string' && stopFile.length > 0, 'stopFile is required');
	positiveFinite(maxGapSeconds, 'maxGapSeconds');
	assert.equal(fs.existsSync(stopFile), false, 'resource sampler stop marker must not exist before streaming starts');
	const reader = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
	const iterator = reader[Symbol.asyncIterator]();
	const displayState = { snapshotIndex: 0, pending: [], expectsSeparator: false };
	let stoppedAfterBoundarySnapshot = false;
	let snapshotDeadline = Date.now() + (maxGapSeconds * 1000);
	try {
		while (!stoppedAfterBoundarySnapshot) {
			const remainingMilliseconds = snapshotDeadline - Date.now();
			assert.ok(remainingMilliseconds > 0, 'Docker stats stream exceeded maxGapSeconds before a complete snapshot');
			const { value: line, done } = await nextWithDeadline(iterator, remainingMilliseconds);
			if (done) break;
			assert.ok(line.length > 0, 'Docker stats stream must not contain empty rows');
			const snapshot = consumeDockerDisplayLine(displayState, line);
			if (snapshot) {
				appendDockerSnapshot(samplesPath, new Date().toISOString(), snapshot, bindings);
				snapshotDeadline = Date.now() + (maxGapSeconds * 1000);
				if (fs.existsSync(stopFile)) stoppedAfterBoundarySnapshot = true;
			}
		}
	} finally {
		reader.close();
		if (!stoppedAfterBoundarySnapshot) process.stdin.destroy();
	}
	assert.equal(displayState.pending.length, 0, 'Docker stats stream ended with an incomplete three-role snapshot');
	assert.equal(stoppedAfterBoundarySnapshot, true, 'Docker stats stream ended before the stop marker and final boundary snapshot');
}

async function main() {
	const [command, ...args] = process.argv.slice(2);
	if (command === 'append-snapshot') {
		const [samplesPath, observedAt, ...bindingArgs] = args;
		appendDockerSnapshot(samplesPath, observedAt, await readStandardInputLines(), parseBindings(bindingArgs));
		return;
	}
	if (command === 'stream-samples') {
		const [samplesPath, stopFile, maxGap, ...bindingArgs] = args;
		await streamDockerSnapshots(samplesPath, stopFile, Number(maxGap), parseBindings(bindingArgs));
		return;
	}
	throw new Error('Mode must be append-snapshot or stream-samples.');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
