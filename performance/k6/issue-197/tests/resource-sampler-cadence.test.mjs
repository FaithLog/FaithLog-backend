import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
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
		assert.match(`${result.stdout}\n${result.stderr}`, /resource sample gap exceeds maxGapSeconds/);
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

function sample(observedAt, role, containerId) {
	return { observedAt, role, containerId, cpuPercent: 1, memoryBytes: 1024 };
}
