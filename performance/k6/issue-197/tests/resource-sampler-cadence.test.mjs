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
		stream.stdin.write(`${dockerRows.join('\n')}\n`);
		await waitFor(() => fs.existsSync(streamPath) && readJsonLines(streamPath).length === 3);
		await new Promise((resolve) => setTimeout(resolve, 10));
		fs.writeFileSync(stopPath, 'stop\n');
		stream.stdin.write(`${dockerRows.join('\n')}\n`);
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
		], { encoding: 'utf8', input: `${dockerRows[0]}\n` });
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

function sample(observedAt, role, containerId) {
	return { observedAt, role, containerId, cpuPercent: 1, memoryBytes: 1024 };
}

function readJsonLines(filePath) {
	return fs.readFileSync(filePath, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
}

async function waitFor(predicate) {
	for (let attempt = 0; attempt < 100; attempt += 1) {
		if (predicate()) return;
		await new Promise((resolve) => setTimeout(resolve, 5));
	}
	assert.fail('timed out waiting for resource sampler output');
}

function childExit(child) {
	return new Promise((resolve, reject) => {
		child.once('error', reject);
		child.once('exit', (code) => resolve(code));
	});
}
