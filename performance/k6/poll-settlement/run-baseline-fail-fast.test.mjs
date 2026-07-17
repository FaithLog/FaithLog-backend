import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';

const RUNNER = resolve(new URL('run-baseline.sh', import.meta.url).pathname);
const SOURCE = readFileSync(RUNNER, 'utf8');
const CLEANUP = section('cleanup() {', '\ntrap cleanup EXIT');
const RUN_MODE = section('resource_loop() {', '\nRUN_STATUS=0');
const CAPTURE_DB = section('capture_db() {', 'capture_resource() {');

test('measured token failure stops before DB/resource/measured work and preserves rejected cleanup', () => {
	const result = fakeRun('token');
	try {
		assert.equal(result.status, 1, JSON.stringify({ trace: result.trace, stderr: result.stderr, adoption: result.adoption }));
		assert.doesNotMatch(result.stderr, /mode: unbound variable/);
		assert.deepEqual(result.trace, [
			'runtime:coffee-sequential-before',
			'validate-runtime:coffee-sequential-before',
			'maintenance-readiness',
			'validate-readiness',
			'warmup-k6',
			'validate-warmup',
			'prepare-token',
		]);
		assert.equal(result.trace.some((line) => /db-|resource|measured-k6/.test(line)), false);
		assert.equal(result.tokenExists, false);
		assert.equal(result.lockExists, false);
		assert.deepEqual(result.adoption, {
			case: { datasetId: 'PERFORMANCE_192_FAIL_FAST', fixtureRunId: 'FAIL_FAST_192', executionRunId: 'EXEC192_FAIL_FAST', mode: 'coffee-sequential' },
			accepted: false, automaticAdoption: false, evidenceIntegrity: 'rejected', measurementStatus: 'rejected', reasons: ['runner-exit-1'],
		});
	} finally { rmSync(result.root, { recursive: true, force: true }); }
});

test('another pre-measured critical failure cannot continue because run_mode is called from a conditional', () => {
	for (const failure of ['runtime-before', 'warmup-validator']) {
		const result = fakeRun(failure);
		try {
			assert.notEqual(result.status, 0, `${failure}: ${JSON.stringify({ trace: result.trace, stderr: result.stderr, adoption: result.adoption })}`);
			const failureIndex = result.trace.findIndex((line) => line === (failure === 'runtime-before' ? 'runtime:coffee-sequential-before' : 'validate-warmup'));
			assert.ok(failureIndex >= 0, `${failure} was not reached`);
			assert.deepEqual(result.trace.slice(failureIndex + 1), [], `${failure} continued into later work`);
			assert.equal(result.trace.some((line) => /db-|resource|measured-k6/.test(line)), false);
			assert.equal(result.adoption?.evidenceIntegrity, 'rejected');
		} finally { rmSync(result.root, { recursive: true, force: true }); }
	}
});

test('mode readiness timeout stops before that mode warmup, DB, resource, or k6 work', () => {
	const result = fakeRun('readiness');
	try {
		assert.notEqual(result.status, 0, JSON.stringify({ trace: result.trace, stderr: result.stderr }));
		assert.deepEqual(result.trace, ['runtime:coffee-sequential-before', 'validate-runtime:coffee-sequential-before', 'maintenance-readiness']);
		assert.equal(result.trace.some((line) => /warmup|db-|resource|measured-k6/.test(line)), false);
		assert.equal(result.adoption?.evidenceIntegrity, 'rejected');
	} finally { rmSync(result.root, { recursive: true, force: true }); }
});

test('global DB capture and validation omit parent MODE while mode scope preserves it exactly', () => {
	const result = captureDbEnvironmentRun();
	try {
		assert.equal(result.status, 0, result.stderr);
		assert.deepEqual(result.trace, [
			'global|unset|capture-db-evidence.mjs',
			'unset|unset|validate-evidence.mjs db-snapshot global.json global',
			'mode|coffee-sequential|capture-db-evidence.mjs',
			'unset|coffee-sequential|validate-evidence.mjs db-snapshot mode.json coffee-sequential',
		]);
	} finally { rmSync(result.root, { recursive: true, force: true }); }
	assert.match(RUN_MODE, /VALIDATION_REJECTION_PATH="\$\{RUN_DIR\}\/baseline-adoption\.json" VALIDATION_STAGE="\$\{mode\}-db-pair-validator"[\s\\]*case_env node "\$\{SCRIPT_DIR\}\/validate-evidence\.mjs" db-pair/);
});

function captureDbEnvironmentRun() {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-db-scope-')); const bin = resolve(root, 'bin'); mkdirSync(bin);
	const tracePath = resolve(root, 'trace'); const nodePath = resolve(bin, 'node');
	writeFileSync(nodePath, `#!/usr/bin/env bash
scope="\${EVIDENCE_SCOPE-unset}"
mode="\${MODE-unset}"
args="$*"
args="\${args##*/}"
printf '%s|%s|%s\\n' "$scope" "$mode" "$args" >> "$TRACE"
[[ "$args" == capture-db-evidence.mjs ]] && printf '{}\\n'
exit 0
`);
	spawnSync('chmod', ['+x', nodePath]);
	const scriptPath = resolve(root, 'harness.sh');
	writeFileSync(scriptPath, `#!/usr/bin/env bash
set -euo pipefail
export MODE=coffee-sequential
export TRACE=${quote(tracePath)}
PATH=${quote(bin)}:"$PATH"
TARGET_CONTRACT=target.json
SCRIPT_DIR=.
PERF_DATASET_ID=PERFORMANCE_192_SCOPE
PERF_FIXTURE_RUN_ID=SCOPE_192
PERF_EXECUTION_RUN_ID=EXEC192_SCOPE
case_env() { PERF_DATASET_ID="$PERF_DATASET_ID" PERF_FIXTURE_RUN_ID="$PERF_FIXTURE_RUN_ID" PERF_EXECUTION_RUN_ID="$PERF_EXECUTION_RUN_ID" "$@"; }
${CAPTURE_DB}
capture_db global.json global
capture_db mode.json mode coffee-sequential
`);
	const executed = spawnSync('bash', [scriptPath], { cwd: root, encoding: 'utf8' });
	return { root, status: executed.status, stderr: executed.stderr, trace: safeRead(tracePath).trim().split('\n').filter(Boolean) };
}

function fakeRun(failure) {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-fail-fast-'));
	const runDir = resolve(root, 'run'); const tokenDir = resolve(root, 'token'); const lockDir = resolve(root, 'lock');
	mkdirSync(runDir); mkdirSync(tokenDir); mkdirSync(lockDir);
	const tracePath = resolve(root, 'trace'); const initial = resolve(root, 'initial.json'); writeFileSync(initial, '{}\n');
	const scriptPath = resolve(root, 'harness.sh');
	writeFileSync(scriptPath, `#!/usr/bin/env bash
set -euo pipefail
RUN_DIR=${quote(runDir)}
TOKEN_DIR=${quote(tokenDir)}
LOCK_DIR=${quote(lockDir)}
TRACE=${quote(tracePath)}
FAIL_STEP=${quote(failure)}
INITIAL_TEMP=${quote(initial)}
LOCK_INODE="$(stat -f '%d:%i' "${lockDir}")"
SAMPLER_PID=''
SAMPLE_MARKER=''
TOKEN_PATH=''
SAMPLING_SECONDS=0.01
BASE_URL=http://127.0.0.1:28080
MODE=coffee-sequential
MANIFEST_PATH=${quote(resolve(root, 'manifest.json'))}
PERF_PASSWORD=not-a-secret
PERF_DATASET_ID=PERFORMANCE_192_FAIL_FAST
PERF_FIXTURE_RUN_ID=FAIL_FAST_192
PERF_EXECUTION_RUN_ID=EXEC192_FAIL_FAST
TOKEN_EXPIRY_SAFETY_SECONDS=120
TARGET_CONTRACT=${quote(resolve(root, 'target.json'))}
SCRIPT_DIR=${quote(root)}
trace() { printf '%s\\n' "$1" >> "${tracePath}"; }
case_env() {
	if [[ "$*" == *'wait-maintenance-readiness.mjs'* ]]; then "$@"; return; fi
	if [[ "$*" == *' maintenance-readiness '* ]]; then trace validate-readiness; return; fi
	if [[ "$*" == *' warmup '* ]]; then trace validate-warmup; [[ "$FAIL_STEP" != warmup-validator ]]; return; fi
	trace validate-mode
}
capture_runtime() { trace "runtime:$2"; [[ "$FAIL_STEP" != runtime-before ]]; }
validate_runtime_boundary() { trace "validate-runtime:$2"; }
capture_db() { [[ "$1" == *db-before.json ]] && trace db-before || trace db-after; }
capture_resource() { trace resource; }
env() {
	if [[ "$*" == *'PHASE=warmup'* ]]; then trace warmup-k6; return 0; fi
	if [[ "$*" == *'PHASE=measured'* ]]; then trace measured-k6; return 0; fi
	return 0
}
node() {
	if [[ "$*" == *'wait-maintenance-readiness.mjs'* ]]; then trace maintenance-readiness; [[ "$FAIL_STEP" != readiness ]]; return; fi
	if [[ "$*" == *'prepare-measured-token.mjs'* ]]; then
		trace prepare-token
		mkdir -p "$(dirname "$TOKEN_PATH")"
		printf token > "$TOKEN_PATH"
		[[ "$FAIL_STEP" != token ]]
		return
	fi
	return 0
}
${CLEANUP}
trap cleanup EXIT
${RUN_MODE}
resource_loop() { trace resource-loop; }
RUN_STATUS=0
if ! run_mode coffee-sequential; then RUN_STATUS=1; fi
[[ "$RUN_STATUS" -eq 0 ]] || exit 1
`);
	const executed = spawnSync('bash', [scriptPath], { encoding: 'utf8' });
	const trace = safeRead(tracePath).trim().split('\n').filter(Boolean);
	const adoptionPath = resolve(runDir, 'baseline-adoption.json');
	return {
		root, status: executed.status, stderr: executed.stderr, trace,
		tokenExists: exists(resolve(tokenDir, 'coffee-sequential.jwt')),
		lockExists: exists(lockDir),
		adoption: exists(adoptionPath) ? JSON.parse(readFileSync(adoptionPath, 'utf8')) : null,
	};
}

function section(start, end) {
	const from = SOURCE.indexOf(start); const to = SOURCE.indexOf(end, from);
	assert.ok(from >= 0 && to > from, `runner section missing: ${start}`);
	return SOURCE.slice(from, to);
}
function quote(value) { return `'${value.replaceAll("'", "'\\''")}'`; }
function exists(path) { return existsSync(path); }
function safeRead(path) { try { return readFileSync(path, 'utf8'); } catch { return ''; } }
