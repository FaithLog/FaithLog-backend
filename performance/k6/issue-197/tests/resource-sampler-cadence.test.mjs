import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const VALIDATOR = path.join(ISSUE_DIR, 'lib/validate-resource-window.mjs');
const RUNNER = path.join(ISSUE_DIR, 'run-devotion-baseline.sh');
const APP_ID = 'a'.repeat(64);
const DATABASE_ID = 'b'.repeat(64);
const REDIS_ID = 'c'.repeat(64);

test('E-shaped serial blocking Docker stats cannot satisfy the approved 1s/2s three-role cadence', () => {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-e-resource-'));
	const samplesPath = path.join(directory, 'samples.jsonl');
	const configPath = path.join(directory, 'config.json');
	const evidencePath = path.join(directory, 'evidence.json');
	const samples = [
		sample('2026-07-17T18:23:27.815Z', 'app', APP_ID),
		sample('2026-07-17T18:23:29.850Z', 'database', DATABASE_ID),
		sample('2026-07-17T18:23:31.900Z', 'redis', REDIS_ID),
		sample('2026-07-17T18:23:36.020Z', 'app', APP_ID),
		sample('2026-07-17T18:23:38.061Z', 'database', DATABASE_ID),
		sample('2026-07-17T18:23:40.127Z', 'redis', REDIS_ID),
	];
	const config = {
		samplingIntervalSeconds: 1,
		maxGapSeconds: 2,
		measuredStart: '2026-07-17T18:23:31.940Z',
		measuredEnd: '2026-07-17T18:23:34.040Z',
		appContainerId: APP_ID,
		databaseContainerId: DATABASE_ID,
		redisContainerId: REDIS_ID,
	};
	try {
		fs.writeFileSync(samplesPath, `${samples.map(JSON.stringify).join('\n')}\n`);
		fs.writeFileSync(configPath, `${JSON.stringify(config)}\n`);
		const result = spawnSync(process.execPath, [VALIDATOR, samplesPath, configPath, evidencePath], { encoding: 'utf8' });
		assert.notEqual(result.status, 0);
		assert.match(`${result.stdout}\n${result.stderr}`, /share one capture timestamp|resource sample gap exceeds maxGapSeconds/);
		assert.equal(JSON.parse(fs.readFileSync(evidencePath, 'utf8')).adoptable, false);

		const runner = fs.readFileSync(RUNNER, 'utf8');
		assert.doesNotMatch(runner, /sample_stats_once app[\s\S]+sample_stats_once database[\s\S]+sample_stats_once redis/);
		assert.match(runner, /docker stats --no-stream --no-trunc[\s\S]+"\$app_container_id" "\$db_container_id" "\$redis_container_id"/);
		assert.match(runner, /docker stats --no-trunc[\s\S]+"\$app_container_id" "\$db_container_id" "\$redis_container_id"[\s\S]+stream-samples/);
		assert.match(runner, /STATS_STOP_FILE[\s\S]+wait "\$STATS_PID"/);
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('three-role snapshot and stream keep exact identity, shared timestamps, marker stop, and capture failure propagation', async () => {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-resource-stream-'));
	const snapshotPath = path.join(directory, 'snapshot.jsonl');
	const streamPath = path.join(directory, 'stream.jsonl');
	const stopPath = path.join(directory, 'stop');
	const dockerRows = [
		`${DATABASE_ID}|2.50%|2MiB / 1GiB`,
		`${REDIS_ID}|0.50%|512KiB / 1GiB`,
		`${APP_ID}|1.50%|1MiB / 1GiB`,
	];
	try {
		const snapshotTime = '2026-07-17T18:23:31.900Z';
		const snapshot = spawnSync(process.execPath, [
			VALIDATOR, 'append-snapshot', snapshotPath, snapshotTime, APP_ID, DATABASE_ID, REDIS_ID,
		], { encoding: 'utf8', input: `${dockerRows.join('\n')}\n` });
		assert.equal(snapshot.status, 0, snapshot.stderr);
		const snapshotSamples = readJsonLines(snapshotPath);
		assert.deepEqual(snapshotSamples.map(({ role }) => role), ['app', 'database', 'redis']);
		assert.deepEqual(new Set(snapshotSamples.map(({ observedAt }) => observedAt)), new Set([snapshotTime]));
		const wrongIdentity = spawnSync(process.execPath, [
			VALIDATOR, 'append-snapshot', path.join(directory, 'wrong.jsonl'), snapshotTime, APP_ID, DATABASE_ID, REDIS_ID,
		], { encoding: 'utf8', input: `${dockerRows.slice(0, 2).concat(`${'d'.repeat(64)}|1.00%|1MiB / 1GiB`).join('\n')}\n` });
		assert.notEqual(wrongIdentity.status, 0);
		assert.match(wrongIdentity.stderr, /exact approved three-container set/);

		const stream = spawn(process.execPath, [
			VALIDATOR, 'stream-samples', streamPath, stopPath, '2', APP_ID, DATABASE_ID, REDIS_ID,
		], { stdio: ['pipe', 'pipe', 'pipe'] });
		let stderr = '';
		stream.stderr.setEncoding('utf8');
		stream.stderr.on('data', (chunk) => { stderr += chunk; });
		stream.stdin.write(`${initialDisplaySnapshot().join('\n')}\n`);
		await waitFor(() => fs.existsSync(streamPath) && readJsonLines(streamPath).length === 3);
		await new Promise((resolve) => setTimeout(resolve, 10));
		fs.writeFileSync(stopPath, 'stop\n');
		stream.stdin.write(`${recurringDisplaySnapshot().join('\n')}\n`);
		stream.stdin.end();
		assert.equal(await childExit(stream), 0, stderr);
		const streamSamples = readJsonLines(streamPath);
		assert.equal(streamSamples.length, 6);
		assert.deepEqual(streamSamples.slice(0, 3).map(({ role }) => role), ['app', 'database', 'redis']);
		assert.deepEqual(streamSamples.slice(3).map(({ role }) => role), ['app', 'database', 'redis']);
		assert.equal(new Set(streamSamples.slice(0, 3).map(({ observedAt }) => observedAt)).size, 1);
		assert.equal(new Set(streamSamples.slice(3).map(({ observedAt }) => observedAt)).size, 1);
		assert.ok(Date.parse(streamSamples[3].observedAt) > Date.parse(streamSamples[0].observedAt));

		const incomplete = spawnSync(process.execPath, [
			VALIDATOR, 'stream-samples', path.join(directory, 'incomplete.jsonl'), path.join(directory, 'missing-stop'),
			'2', APP_ID, DATABASE_ID, REDIS_ID,
		], { encoding: 'utf8', input: `${initialDisplaySnapshot()[0]}\n` });
		assert.notEqual(incomplete.status, 0);
		assert.match(incomplete.stderr, /incomplete three-role snapshot|before the stop marker/);

		const stalled = spawn(process.execPath, [
			VALIDATOR, 'stream-samples', path.join(directory, 'stalled.jsonl'), path.join(directory, 'stalled-stop'),
			'0.05', APP_ID, DATABASE_ID, REDIS_ID,
		], { stdio: ['pipe', 'pipe', 'pipe'] });
		let stalledError = '';
		stalled.stderr.setEncoding('utf8');
		stalled.stderr.on('data', (chunk) => { stalledError += chunk; });
		assert.notEqual(await childExit(stalled), 0);
		assert.match(stalledError, /exceeded maxGapSeconds/);
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('F-shaped Docker stats stream accepts only exact CSI screen framing around an ID-based snapshot', () => {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-resource-ansi-'));
	const snapshotTime = '2026-07-17T19:00:00.000Z';
	const ansiRows = [
		`\u001b[H${APP_ID}|1.50%|1MiB / 1GiB\u001b[K`,
		`${DATABASE_ID}|2.50%|2MiB / 1GiB\u001b[K`,
		`${REDIS_ID}|0.50%|512KiB / 1GiB\u001b[K`,
	];
	try {
		const validPath = path.join(directory, 'valid.jsonl');
		const valid = appendSnapshot(validPath, ansiRows, snapshotTime);
		assert.equal(valid.status, 0, valid.stderr);
		assert.deepEqual(readJsonLines(validPath).map(({ containerId }) => containerId), [APP_ID, DATABASE_ID, REDIS_ID]);

		for (const [name, rows] of [
			['unknown-csi', [`\u001b[2J${ansiRows[0].slice(3)}`, ...ansiRows.slice(1)]],
			['mid-field-control', [ansiRows[0].replace('1.50%', `1.50%\u001b[H`), ...ansiRows.slice(1)]],
			['missing-clear', [ansiRows[0], ansiRows[1], ansiRows[2].slice(0, -3)]],
			['clear-without-home', [ansiRows[0].slice(3), ...ansiRows.slice(1)]],
			['text-before-home', [`noise${ansiRows[0]}`, ...ansiRows.slice(1)]],
		]) {
			const rejected = appendSnapshot(path.join(directory, `${name}.jsonl`), rows, snapshotTime);
			assert.notEqual(rejected.status, 0, `${name} must be rejected`);
			assert.match(rejected.stderr, /ANSI|control|framing/i);
		}

		const runner = fs.readFileSync(RUNNER, 'utf8');
		assert.match(runner, /\{\{\.ID\}\}\|\{\{\.CPUPerc\}\}\|\{\{\.MemUsage\}\}/);
		assert.doesNotMatch(runner, /\{\{\.Container\}\}/);
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('G-shaped recurring Docker display protocol yields three complete snapshots and a marker-bound final boundary', async () => {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-resource-recurring-'));
	const streamPath = path.join(directory, 'stream.jsonl');
	const stopPath = path.join(directory, 'stop');
	const child = spawn(process.execPath, [
		VALIDATOR, 'stream-samples', streamPath, stopPath, '2', APP_ID, DATABASE_ID, REDIS_ID,
	], { stdio: ['pipe', 'pipe', 'pipe'] });
	let stderr = '';
	child.stderr.setEncoding('utf8');
	child.stderr.on('data', (chunk) => { stderr += chunk; });
	try {
		child.stdin.write(`${initialDisplaySnapshot().join('\n')}\n`);
		await waitForLiveRows(child, streamPath, 3, () => stderr);
		child.stdin.write(`${recurringDisplaySnapshot().join('\n')}\n`);
		await waitForLiveRows(child, streamPath, 6, () => stderr);
		fs.writeFileSync(stopPath, 'stop\n');
		child.stdin.write(`${recurringDisplaySnapshot().join('\n')}\n`);
		child.stdin.end();
		assert.equal(await childExit(child), 0, stderr);
		const samples = readJsonLines(streamPath);
		assert.equal(samples.length, 9);
		for (let offset = 0; offset < samples.length; offset += 3) {
			assert.deepEqual(samples.slice(offset, offset + 3).map(({ role }) => role), ['app', 'database', 'redis']);
			assert.equal(new Set(samples.slice(offset, offset + 3).map(({ observedAt }) => observedAt)).size, 1);
		}
		assert.ok(Date.parse(samples[3].observedAt) > Date.parse(samples[0].observedAt));
		assert.ok(Date.parse(samples[6].observedAt) > Date.parse(samples[3].observedAt));

		for (const [name, lines] of [
			['missing-separator', [...initialDisplaySnapshot(), ...recurringDisplaySnapshot().slice(1)]],
			['extra-separator', [...initialDisplaySnapshot(), '\u001b[K', '\u001b[K']],
			['initial-prefix-reused', [...initialDisplaySnapshot(), '\u001b[K', ...initialDisplaySnapshot()]],
			['erase-entire-display', [...initialDisplaySnapshot(), '\u001b[K', ...recurringDisplaySnapshot().slice(1).map((line, index) => index === 0 ? line.replace('\u001b[J', '\u001b[2J') : line)]],
			['mid-field-control', [...initialDisplaySnapshot(), '\u001b[K', ...recurringDisplaySnapshot().slice(1).map((line, index) => index === 0 ? line.replace('1.75%', '1.75%\u001b[1A') : line)]],
			['unknown-separator-csi', [...initialDisplaySnapshot(), '\u001b[1A']],
		]) {
			const rejected = spawnSync(process.execPath, [
				VALIDATOR, 'stream-samples', path.join(directory, `${name}.jsonl`), path.join(directory, `${name}.stop`),
				'2', APP_ID, DATABASE_ID, REDIS_ID,
			], { encoding: 'utf8', input: `${lines.join('\n')}\n` });
			assert.notEqual(rejected.status, 0, `${name} must be rejected`);
			assert.match(rejected.stderr, /display protocol|separator|prefix|ANSI|control/i, `${name} must fail closed at framing validation`);
		}
	} finally {
		if (child.exitCode === null) child.kill();
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

function sample(observedAt, role, containerId) {
	return { observedAt, role, containerId, cpuPercent: 1, memoryBytes: 1024 };
}

function readJsonLines(filePath) {
	return fs.readFileSync(filePath, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
}

function appendSnapshot(outputPath, rows, observedAt) {
	return spawnSync(process.execPath, [
		VALIDATOR, 'append-snapshot', outputPath, observedAt, APP_ID, DATABASE_ID, REDIS_ID,
	], { encoding: 'utf8', input: `${rows.join('\n')}\n` });
}

function initialDisplaySnapshot() {
	return [
		`\u001b[H${APP_ID}|1.50%|1MiB / 1GiB\u001b[K`,
		`${DATABASE_ID}|2.50%|2MiB / 1GiB\u001b[K`,
		`${REDIS_ID}|0.50%|512KiB / 1GiB\u001b[K`,
	];
}

function recurringDisplaySnapshot() {
	return [
		'\u001b[K',
		`\u001b[J\u001b[H${APP_ID}|1.75%|1.5MiB / 1GiB\u001b[K`,
		`${DATABASE_ID}|2.75%|2.5MiB / 1GiB\u001b[K`,
		`${REDIS_ID}|0.75%|768KiB / 1GiB\u001b[K`,
	];
}

async function waitFor(predicate) {
	for (let attempt = 0; attempt < 100; attempt += 1) {
		if (predicate()) return;
		await new Promise((resolve) => setTimeout(resolve, 5));
	}
	assert.fail('timed out waiting for resource sampler output');
}

async function waitForLiveRows(child, filePath, expectedRows, errorText) {
	await waitFor(() => {
		assert.equal(child.exitCode, null, `resource stream exited early: ${errorText()}`);
		return fs.existsSync(filePath) && readJsonLines(filePath).length === expectedRows;
	});
}

function childExit(child) {
	return new Promise((resolve, reject) => {
		child.once('error', reject);
		child.once('exit', (code) => resolve(code));
	});
}
