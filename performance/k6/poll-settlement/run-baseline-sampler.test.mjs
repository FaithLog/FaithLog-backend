import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';

const RUNNER = resolve(new URL('run-baseline.sh', import.meta.url).pathname);
const SOURCE = readFileSync(RUNNER, 'utf8');
const RUN_MODE = section('resource_loop() {', '\nRUN_STATUS=0');

test('sampler marker removal exits zero and run_mode completes final resource, DB, runtime, status, and validation', () => {
	const result = fakeRun('stop-during-sample');
	try {
		assert.equal(result.status, 0, JSON.stringify(result));
		assert.equal(result.statusEvidence?.exitStatus, 0);
		for (const expected of ['resource-final', 'db-after', 'runtime:coffee-sequential-after', 'validate-runtime:coffee-sequential-after', 'validate-mode']) {
			assert.ok(result.trace.includes(expected), `missing trace: ${expected}`);
		}
		assert.ok(result.trace.indexOf('resource-final') < result.trace.indexOf('db-after'));
		assert.ok(result.trace.indexOf('db-after') < result.trace.indexOf('runtime:coffee-sequential-after'));
	} finally { rmSync(result.root, { recursive: true, force: true }); }
});

test('sampler capture failure remains non-zero and blocks measured adoption evidence', () => {
	const result = fakeRun('capture-failure');
	try {
		assert.notEqual(result.status, 0);
		assert.equal(result.trace.includes('capture-failure'), true);
		assert.equal(result.trace.includes('resource-final'), false);
		assert.equal(result.trace.includes('db-after'), false);
		assert.equal(result.trace.includes('validate-mode'), false);
		assert.equal(result.statusEvidence, null);
		assert.equal(result.adoption?.stage, 'coffee-sequential-resource-sampler');
		assert.deepEqual(result.adoption?.reasons, ['resource-capture-failed']);
		assert.equal(result.adoption?.accepted, false);
		assert.equal(result.adoption?.automaticAdoption, false);
		assert.equal(result.adoption?.evidenceIntegrity, 'rejected');
		assert.equal(result.adoption?.measurementStatus, 'rejected');
	} finally { rmSync(result.root, { recursive: true, force: true }); }
});

test('2.1 second capture overhead does not add a one-second sleep or exceed the approved 3-second max gap', () => {
	const result = fakeRun('cadence');
	try {
		assert.equal(result.status, 0, JSON.stringify(result));
		assert.ok(result.samples.length >= 3);
		const gaps = result.samples.slice(1).map((value, index) => value - result.samples[index]);
		assert.ok(Math.max(...gaps) <= 3000, `observed sampler gap exceeded 3000ms: ${gaps.join(',')}`);
		assert.equal(result.sleepCalls, 0, 'blocking docker stats capture must determine cadence without additive sleep');
	} finally { rmSync(result.root, { recursive: true, force: true }); }
});

function fakeRun(behavior) {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-sampler-'));
	const runDir = resolve(root, 'run'); const tokenDir = resolve(root, 'token'); mkdirSync(runDir); mkdirSync(tokenDir);
	const tracePath = resolve(root, 'trace'); const markerActivity = resolve(root, 'sampler-active');
	const adoptionPath = resolve(runDir, 'baseline-adoption.json');
	const clockPath = resolve(root, 'clock'); const countPath = resolve(root, 'count'); const sleepPath = resolve(root, 'sleep-count');
	writeFileSync(clockPath, '0'); writeFileSync(countPath, '0'); writeFileSync(sleepPath, '0');
	const scriptPath = resolve(root, 'harness.sh');
	writeFileSync(scriptPath, `#!/usr/bin/env bash
set -euo pipefail
RUN_DIR=${quote(runDir)}
TOKEN_DIR=${quote(tokenDir)}
TRACE=${quote(tracePath)}
ACTIVITY=${quote(markerActivity)}
CLOCK=${quote(clockPath)}
COUNT=${quote(countPath)}
SLEEP_COUNT=${quote(sleepPath)}
BEHAVIOR=${quote(behavior)}
SAMPLER_PID=''
SAMPLE_MARKER=''
TOKEN_PATH=''
SAMPLING_SECONDS=1
BASE_URL=http://127.0.0.1:28080
MANIFEST_PATH=${quote(resolve(root, 'manifest.json'))}
PERF_PASSWORD=not-a-secret
PERF_DATASET_ID=PERFORMANCE_192_SAMPLER
PERF_FIXTURE_RUN_ID=SAMPLER_192
PERF_EXECUTION_RUN_ID=EXEC192_SAMPLER
TOKEN_EXPIRY_SAFETY_SECONDS=120
TARGET_CONTRACT=${quote(resolve(root, 'target.json'))}
SCRIPT_DIR=${quote(root)}
trace() { printf '%s\\n' "$1" >> "$TRACE"; }
increment() { local path="$1" amount="$2" value; value="$(<"$path")"; printf '%s' "$((value + amount))" > "$path"; }
case_env() {
	if [[ "$*" == *' warmup '* ]]; then trace validate-warmup
	elif [[ "$*" == *' db-pair '* ]]; then trace validate-db-pair
	elif [[ "$*" == *' mode '* ]]; then trace validate-mode
	fi
}
capture_runtime() { trace "runtime:$2"; }
validate_runtime_boundary() { trace "validate-runtime:$2"; }
capture_db() { [[ "$1" == *db-before.json ]] && trace db-before || trace db-after; }
capture_resource() {
	local stage="\${3:-missing-resource-stage}"
	increment "$COUNT" 1
	local count sampling; count="$(<"$COUNT")"; sampling=false; [[ -f "$SAMPLE_MARKER" ]] && sampling=true
	if [[ "$BEHAVIOR" == stop-during-sample && "$count" -eq 2 ]]; then
		touch "$ACTIVITY"
		while [[ -f "$SAMPLE_MARKER" ]]; do /bin/sleep 0.001; done
	fi
	increment "$CLOCK" 2100
	local now; now="$(<"$CLOCK")"; printf 'sample:%s\\n' "$now" >> "$TRACE"
	if [[ "$count" -eq 1 ]]; then trace resource-initial
	elif [[ "$sampling" == true ]]; then trace resource-background
	else trace resource-final; fi
	if [[ "$BEHAVIOR" == capture-failure && "$count" -eq 2 ]]; then
		trace capture-failure
		printf '{"case":{"datasetId":"%s","fixtureRunId":"%s","executionRunId":"%s"},"accepted":false,"automaticAdoption":false,"evidenceIntegrity":"rejected","measurementStatus":"rejected","stage":"%s","reasons":["resource-capture-failed"],"secretsIncluded":false}\n' "$PERF_DATASET_ID" "$PERF_FIXTURE_RUN_ID" "$PERF_EXECUTION_RUN_ID" "$stage" > ${quote(adoptionPath)}
		rm -f "$SAMPLE_MARKER"
		return 42
	fi
	if [[ "$BEHAVIOR" == cadence && "$count" -eq 3 ]]; then rm -f "$SAMPLE_MARKER"; fi
}
sleep() {
	increment "$SLEEP_COUNT" 1
	increment "$CLOCK" 1000
	if [[ "$BEHAVIOR" == stop-during-sample ]]; then
		touch "$ACTIVITY"
		while [[ -f "$SAMPLE_MARKER" ]]; do /bin/sleep 0.001; done
	fi
}
env() {
	if [[ "$*" == *'PHASE=warmup'* ]]; then trace warmup-k6; return 0; fi
	if [[ "$*" == *'PHASE=measured'* ]]; then
		trace measured-k6
		if [[ "$BEHAVIOR" == stop-during-sample ]]; then while [[ ! -f "$ACTIVITY" ]]; do /bin/sleep 0.001; done; rm -f "$SAMPLE_MARKER"
		else while [[ -f "$SAMPLE_MARKER" ]]; do /bin/sleep 0.001; done; fi
		return 0
	fi
}
node() { if [[ "$*" == *'prepare-measured-token.mjs'* ]]; then trace prepare-token; printf token > "$TOKEN_PATH"; fi; }
${RUN_MODE}
mode=coffee-sequential
run_mode "$mode"
`);
	const executed = spawnSync('bash', [scriptPath], { encoding: 'utf8', timeout: 5000 });
	const trace = safeRead(tracePath).trim().split('\n').filter(Boolean);
	const statusPath = resolve(runDir, 'coffee-sequential-status.json');
	return {
		root, status: executed.status, signal: executed.signal, stderr: executed.stderr, trace,
		samples: trace.filter((line) => line.startsWith('sample:')).map((line) => Number(line.slice(7))),
		sleepCalls: Number(safeRead(sleepPath) || 0),
		statusEvidence: existsSync(statusPath) ? JSON.parse(readFileSync(statusPath, 'utf8')) : null,
		adoption: existsSync(adoptionPath) ? JSON.parse(readFileSync(adoptionPath, 'utf8')) : null,
	};
}

function section(start, end) {
	const from = SOURCE.indexOf(start); const to = SOURCE.indexOf(end, from);
	assert.ok(from >= 0 && to > from, `runner section missing: ${start}`);
	return SOURCE.slice(from, to);
}
function quote(value) { return `'${value.replaceAll("'", "'\\''")}'`; }
function safeRead(path) { try { return readFileSync(path, 'utf8'); } catch { return ''; } }
