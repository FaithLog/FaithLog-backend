import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const SAMPLER = join(ROOT, 'resource-window-sampler.mjs');
const RUNNER = join(ROOT, 'run-baseline.sh');
const APP_ID = 'a'.repeat(64);
const DB_ID = 'b'.repeat(64);
const REDIS_ID = 'c'.repeat(64);

test('runner uses one exact-ID three-role snapshot, continuous stream, stop marker, and bounded child wait', () => {
	const runner = readFileSync(RUNNER, 'utf8');
	assert.match(runner, /docker stats --no-stream --no-trunc[\s\S]*"\$\{app_container_id\}" "\$\{db_container_id\}" "\$\{redis_container_id\}"/);
	assert.match(runner, /docker stats --no-trunc[\s\S]*"\$\{app_container_id\}" "\$\{db_container_id\}" "\$\{redis_container_id\}"[\s\S]*stream-samples/);
	assert.match(runner, /resource-sampler\.ready[\s\S]*wait_for_file_or_child "\$\{sampler_pid\}" "\$\{resource_ready_file\}"[\s\S]*k6 run/);
	assert.match(runner, /resource-sampler\.stop[\s\S]*kill -0 "\$\{sampler_pid\}"[\s\S]*wait "\$\{sampler_pid\}"/);
	assert.match(runner, /append-snapshot[\s\S]*log_since=.*rfc3339_now/);
	assert.match(runner, /log_until=.*rfc3339_now[\s\S]*: > "\$\{resource_stop_file\}"/);
});

test('streaming sampler preserves ANSI-framed exact three-role ticks and one complete final tick after stop', async () => {
	const directory = mkdtempSync(join(tmpdir(), 'faithlog-196-resource-stream-'));
	const output = join(directory, 'samples.tsv');
	const ready = join(directory, 'ready');
	const stop = join(directory, 'stop');
	const child = spawn(process.execPath, [SAMPLER, 'stream-samples', output, ready, stop, '2',
		'faithlog-app', APP_ID, 'faithlog-db', DB_ID, 'faithlog-redis', REDIS_ID], { stdio: ['pipe', 'pipe', 'pipe'] });
	let stderr = '';
	child.stderr.setEncoding('utf8');
	child.stderr.on('data', (chunk) => { stderr += chunk; });
	try {
		await waitFor(() => existsSync(ready));
		assert.equal(statSync(ready).mode & 0o777, 0o600);
		child.stdin.write(`${initialSnapshot().join('\n')}\n`);
		await waitFor(() => existsSync(output) && rows(output).length === 3);
		writeFileSync(stop, 'stop\n');
		child.stdin.write(`${recurringSnapshot().join('\n')}\n`);
		child.stdin.end();
		assert.equal(await childExit(child), 0, stderr);
		const samples = rows(output);
		assert.equal(samples.length, 6);
		for (let offset = 0; offset < samples.length; offset += 3) {
			assert.deepEqual(samples.slice(offset, offset + 3).map((row) => row[1]), ['faithlog-app', 'faithlog-db', 'faithlog-redis']);
			assert.equal(new Set(samples.slice(offset, offset + 3).map((row) => row[0])).size, 1);
		}
		assert.ok(Date.parse(samples[3][0]) > Date.parse(samples[0][0]));
	} finally {
		if (child.exitCode === null) child.kill();
		rmSync(directory, { recursive: true, force: true });
	}
});

test('sampler rejects incomplete identities, unsupported framing, and stream termination before a final stop tick', () => {
	const directory = mkdtempSync(join(tmpdir(), 'faithlog-196-resource-reject-'));
	try {
		const incomplete = spawnSync(process.execPath, [SAMPLER, 'stream-samples', join(directory, 'incomplete.tsv'),
			join(directory, 'incomplete.ready'), join(directory, 'missing.stop'), '0.05',
			'faithlog-app', APP_ID, 'faithlog-db', DB_ID, 'faithlog-redis', REDIS_ID],
		{ encoding: 'utf8', input: `${initialSnapshot()[0]}\n` });
		assert.notEqual(incomplete.status, 0);
		assert.match(incomplete.stderr, /incomplete three-role snapshot|before the stop marker|exceeded maxGapSeconds/);
		const wrong = spawnSync(process.execPath, [SAMPLER, 'append-snapshot', join(directory, 'wrong.tsv'),
			'2026-07-17T00:00:00.000Z', 'faithlog-app', APP_ID, 'faithlog-db', DB_ID, 'faithlog-redis', REDIS_ID],
		{ encoding: 'utf8', input: `${[...plainSnapshot().slice(0, 2), `${'d'.repeat(64)}|1.00%|1MiB / 1GiB|0.10%`].join('\n')}\n` });
		assert.notEqual(wrong.status, 0);
		assert.match(wrong.stderr, /exact approved three-container set/);
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
});

function plainSnapshot() {
	return [`${APP_ID}|1.50%|1MiB / 1GiB|0.10%`, `${DB_ID}|2.50%|2MiB / 1GiB|0.20%`, `${REDIS_ID}|0.50%|512KiB / 1GiB|0.05%`];
}
function initialSnapshot() { return plainSnapshot().map((line, index) => `${index === 0 ? '\u001b[H' : ''}${line}\u001b[K`); }
function recurringSnapshot() { return ['\u001b[K', ...plainSnapshot().map((line, index) => `${index === 0 ? '\u001b[J\u001b[H' : ''}${line}\u001b[K`)]; }
function rows(path) { return readFileSync(path, 'utf8').trim().split('\n').filter(Boolean).map((line) => line.split('\t')); }
async function waitFor(predicate) { for (let count = 0; count < 100; count += 1) { if (predicate()) return; await new Promise((resolve) => setTimeout(resolve, 5)); } assert.fail('timed out waiting for resource samples'); }
function childExit(child) { return new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); }); }
