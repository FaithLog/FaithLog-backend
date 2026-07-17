import assert from 'node:assert/strict';
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { DATABASE_COUNTER_KEYS, MAINTENANCE_KEYS, PLANNER_KEYS, TABLE_COUNTER_KEYS, TABLE_NAMES } from './evidence-contract.mjs';

const COLLECTOR = resolve(new URL('capture-db-evidence.mjs', import.meta.url).pathname);
const CASE = { datasetId: 'PERFORMANCE_192_STDIN', fixtureRunId: 'STDIN_192', executionRunId: 'EXEC192_STDIN' };

test('DB collector uses docker exec -i so fake Docker receives SQL stdin and returns a strict snapshot', () => {
	const fixture = harness();
	try {
		const result = collect(fixture);
		assert.equal(result.status, 0, result.stderr);
		const evidence = JSON.parse(result.stdout);
		assert.deepEqual(evidence.db.case, CASE);
		assert.equal(evidence.pgStatStatements.available, false);
		const calls = readFileSync(fixture.logPath, 'utf8').trim().split('\n').map(JSON.parse);
		assert.equal(calls.length, 2);
		for (const call of calls) {
			assert.equal(call.interactive, true);
			assert.ok(call.inputBytes > 0);
			assert.equal(call.argvHasInteractive, true);
		}
		assert.equal(calls.some((call) => call.databaseSnapshot), true);
		assert.equal(calls.some((call) => call.extensionCheck), true);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('DB collector rejects successful psql exit with empty stdout using a sanitized explicit error', () => {
	const fixture = harness();
	try {
		const result = collect(fixture, { FAKE_DOCKER_EMPTY: '1' });
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /empty psql output/i);
		assert.doesNotMatch(result.stderr, /WITH table_stats|SELECT json_build_object/);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

function harness() {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-db-collector-')); const bin = resolve(root, 'bin'); mkdirSync(bin);
	const targetPath = resolve(root, 'target.json'); const responsePath = resolve(root, 'snapshot.json'); const logPath = resolve(root, 'docker-calls.ndjson');
	writeFileSync(targetPath, `${JSON.stringify({ containers: { postgres: { id: 'a'.repeat(64) } }, database: { user: 'faithlog', name: 'faithlog' } })}\n`);
	writeFileSync(responsePath, `${JSON.stringify(snapshot())}\n`);
	const dockerPath = resolve(bin, 'docker');
	writeFileSync(dockerPath, `#!/usr/bin/env node
const fs = require('fs');
const args = process.argv.slice(2);
const interactive = args[0] === 'exec' && args[1] === '-i';
const input = interactive ? fs.readFileSync(0, 'utf8') : '';
fs.appendFileSync(process.env.FAKE_DOCKER_LOG, JSON.stringify({ interactive, argvHasInteractive: args.includes('-i'), inputBytes: Buffer.byteLength(input), databaseSnapshot: input.includes('WITH table_stats'), extensionCheck: input.includes('SELECT EXISTS') }) + '\\n');
if (!interactive || process.env.FAKE_DOCKER_EMPTY === '1') process.exit(0);
if (input.includes('WITH table_stats')) process.stdout.write(fs.readFileSync(process.env.FAKE_DB_RESPONSE, 'utf8'));
else if (input.includes('SELECT EXISTS')) process.stdout.write('f\\n');
else process.exit(2);
`);
	chmodSync(dockerPath, 0o755);
	return { root, bin, targetPath, responsePath, logPath };
}
function collect(fixture, extraEnv = {}) {
	const childEnv = { ...process.env };
	delete childEnv.MODE;
	return spawnSync(process.execPath, [COLLECTOR], { encoding: 'utf8', env: { ...childEnv, ...extraEnv, PATH: `${fixture.bin}:${process.env.PATH}`, FAKE_DOCKER_LOG: fixture.logPath, FAKE_DB_RESPONSE: fixture.responsePath, TARGET_CONTRACT: fixture.targetPath, EVIDENCE_SCOPE: 'global', PERF_DATASET_ID: CASE.datasetId, PERF_FIXTURE_RUN_ID: CASE.fixtureRunId, PERF_EXECUTION_RUN_ID: CASE.executionRunId } });
}
function snapshot() { return {
	case: CASE, statsReset: null,
	database: Object.fromEntries(DATABASE_COUNTER_KEYS.map((key) => [key, '1'])),
	tables: Object.fromEntries(TABLE_NAMES.map((name) => [name, { ...Object.fromEntries(TABLE_COUNTER_KEYS.map((key) => [key, '1'])), maintenance: Object.fromEntries(MAINTENANCE_KEYS.map((key) => [key, null])) }])),
	planner: Object.fromEntries(PLANNER_KEYS.map((key) => [key, '1'])), activity: [],
}; }
