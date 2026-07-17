import assert from 'node:assert/strict';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';

const COLLECTOR = resolve(new URL('capture-resource-sample.mjs', import.meta.url).pathname);
const CASE = { datasetId: 'PERFORMANCE_192_RESOURCE', fixtureRunId: 'RESOURCE_192', executionRunId: 'EXEC192_RESOURCE' };

test('resource collector parser failure writes the first sanitized machine-readable rejection without partial evidence', () => {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-resource-capture-'));
	try {
		const bin = resolve(root, 'bin'); mkdirSync(bin);
		const targetPath = resolve(root, 'target.json'); const outputPath = resolve(root, 'resources.ndjson');
		const adoptionPath = resolve(root, 'baseline-adoption.json'); const dockerPath = resolve(bin, 'docker');
		const containers = {
			app: { id: 'a'.repeat(64), name: 'faithlog-latest-app' },
			postgres: { id: 'b'.repeat(64), name: 'faithlog-latest-postgres' },
			redis: { id: 'c'.repeat(64), name: 'faithlog-latest-redis' },
		};
		writeFileSync(targetPath, `${JSON.stringify({ containers, resourceSampling: { samplingIntervalMs: 1000, maxGapMs: 3000 } })}\n`);
		writeFileSync(dockerPath, `#!/usr/bin/env node
const rows = ${JSON.stringify(Object.values(containers).map((container, index) => ({
			ID: container.id, Name: container.name, CPUPerc: '1.00%', MemUsage: '64MiB / 1GiB', MemPerc: index === 0 ? '99.00%' : '6.25%',
		})))};
for (const row of rows) process.stdout.write(JSON.stringify(row) + '\\n');
`);
		chmodSync(dockerPath, 0o755);
		const executed = spawnSync(process.execPath, [COLLECTOR], {
			encoding: 'utf8',
			env: {
				...process.env, PATH: `${bin}${delimiter}${process.env.PATH}`,
				TARGET_CONTRACT: targetPath, MODE: 'coffee-sequential', RESOURCE_OUTPUT: outputPath,
				RESOURCE_REJECTION_PATH: adoptionPath, RESOURCE_STAGE: 'coffee-sequential-resource-sampler',
				PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId,
			},
		});
		assert.notEqual(executed.status, 0);
		assert.equal(existsSync(outputPath), false, 'failed capture must not append partial resource evidence');
		assert.equal(existsSync(adoptionPath), true, `missing rejection: ${executed.stderr}`);
		const adoptionText = readFileSync(adoptionPath, 'utf8'); const adoption = JSON.parse(adoptionText);
		assert.deepEqual(adoption.case, CASE);
		assert.equal(adoption.stage, 'coffee-sequential-resource-sampler');
		assert.deepEqual(adoption.reasons, ['resource-capture-failed']);
		assert.equal(adoption.accepted, false); assert.equal(adoption.automaticAdoption, false);
		assert.equal(adoption.evidenceIntegrity, 'rejected'); assert.equal(adoption.measurementStatus, 'rejected');
		assert.equal(adoption.secretsIncluded, false);
		assert.doesNotMatch(adoptionText, /64MiB|99\.00|docker|stderr|faithlog-192-resource-capture/i);
		assert.doesNotMatch(executed.stderr, /64MiB|99\.00|faithlog-192-resource-capture/i);
		const retry = spawnSync(process.execPath, [COLLECTOR], {
			encoding: 'utf8',
			env: {
				...process.env, PATH: `${bin}${delimiter}${process.env.PATH}`,
				TARGET_CONTRACT: targetPath, MODE: 'coffee-sequential', RESOURCE_OUTPUT: outputPath,
				RESOURCE_REJECTION_PATH: adoptionPath, RESOURCE_STAGE: 'coffee-sequential-resource-final',
				PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId,
			},
		});
		assert.notEqual(retry.status, 0);
		assert.equal(readFileSync(adoptionPath, 'utf8'), adoptionText, 'later capture failure must not replace the first sampler rejection');
	} finally { rmSync(root, { recursive: true, force: true }); }
});
