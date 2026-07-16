import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const issueRoot = path.resolve(here, '..');
const repositoryRoot = path.resolve(issueRoot, '../../..');

const files = {
	contract: path.join(issueRoot, 'scenario-contract.json'),
	scenario: path.join(issueRoot, 'member-list-baseline.js'),
	fixture: path.join(issueRoot, 'prepare-fixture.mjs'),
	runtimeContainerIdentity: path.join(issueRoot, 'runtime-container-identity.mjs'),
	runtimeLogin: path.join(issueRoot, 'runtime-login.mjs'),
	targetIdentity: path.join(issueRoot, 'validate-target-identity.mjs'),
	approvedRuntimeTarget: path.join(issueRoot, 'validate-approved-runtime-target.mjs'),
	tokenLifetime: path.join(issueRoot, 'validate-token-lifetime.mjs'),
	summaryValidator: path.join(issueRoot, 'validate-k6-summary.mjs'),
	dbIntegrityValidator: path.join(issueRoot, 'validate-db-integrity.mjs'),
	dbControlCapture: path.join(issueRoot, 'capture-db-control-snapshot.sh'),
	dbControlValidator: path.join(issueRoot, 'validate-db-control-window.mjs'),
	runtimeContinuityValidator: path.join(issueRoot, 'validate-runtime-continuity.mjs'),
	resourceSnapshotValidator: path.join(issueRoot, 'validate-resource-snapshots.mjs'),
	resourceSnapshotCapture: path.join(issueRoot, 'capture-resource-snapshot.mjs'),
	measurementClassifier: path.join(issueRoot, 'classify-measurement.mjs'),
	firstRejectionRecorder: path.join(issueRoot, 'record-first-rejection.mjs'),
	tableCounterValidator: path.join(issueRoot, 'validate-table-counters.mjs'),
	runner: path.join(issueRoot, 'run-baseline.sh'),
	dbCollector: path.join(issueRoot, 'collect-db-evidence.sh'),
	queryDelta: path.join(issueRoot, 'derive-query-delta.mjs'),
	dbCounters: path.join(issueRoot, 'db-table-counters.sql'),
	dbRuntimeIntegrity: path.join(issueRoot, 'db-runtime-integrity.sql'),
	readme: path.join(issueRoot, 'README.md'),
};

const read = (file) => fs.readFileSync(file, 'utf8');

let cachedFakeRunnerResult;

function runFakeBaseline({ swapAfterPreLock = false, preflightSourceMismatch = false } = {}) {
	if (!swapAfterPreLock && !preflightSourceMismatch && cachedFakeRunnerResult) {
		return cachedFakeRunnerResult;
	}

	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-runner-'));
	const binaryDirectory = path.join(tempDirectory, 'bin');
	const tracePath = path.join(tempDirectory, 'trace.ndjson');
	const runnerPath = path.join(tempDirectory, 'run-baseline.sh');
	fs.mkdirSync(binaryDirectory);
	fs.copyFileSync(files.runner, runnerPath);
	fs.chmodSync(runnerPath, 0o755);
	fs.copyFileSync(files.contract, path.join(tempDirectory, 'scenario-contract.json'));
	for (const validator of [
		files.targetIdentity,
		files.tokenLifetime,
		files.summaryValidator,
		files.dbControlValidator,
		files.runtimeContinuityValidator,
		files.approvedRuntimeTarget,
		files.resourceSnapshotValidator,
		files.resourceSnapshotCapture,
		files.measurementClassifier,
		files.firstRejectionRecorder,
	]) {
		fs.copyFileSync(validator, path.join(tempDirectory, path.basename(validator)));
	}
	fs.copyFileSync(files.dbControlCapture, path.join(tempDirectory, path.basename(files.dbControlCapture)));
	fs.chmodSync(path.join(tempDirectory, path.basename(files.dbControlCapture)), 0o755);
	fs.copyFileSync(files.dbRuntimeIntegrity, path.join(tempDirectory, path.basename(files.dbRuntimeIntegrity)));

	const writeExecutable = (name, contents) => {
		const executable = path.join(binaryDirectory, name);
		fs.writeFileSync(executable, contents);
		fs.chmodSync(executable, 0o755);
		return executable;
	};

writeExecutable('docker', `#!/usr/bin/env bash
set -euo pipefail
case "\${1-}" in
	inspect)
		count_file="$TRACE.inspect-count"
		count=0
		if [[ -f "$count_file" ]]; then count=$(<"$count_file"); fi
		count=$((count + 1))
		printf '%s' "$count" > "$count_file"
		replaced=0
		if [[ "\${FAKE_SWAP_AFTER_PRELOCK-0}" == 1 && "$count" -gt 10 ]]; then replaced=1; fi
		if [[ "$*" == *'{{.Id}}'* ]]; then
			printf '%s\\n' "sha256:\${*: -1}-container-$replaced"
		elif [[ "$*" == *'{{.Name}}'* ]]; then
			printf '/%s\\n' "\${*: -1}-$replaced"
		elif [[ "$*" == *'{{.Image}}'* ]]; then
			printf '%s\\n' "sha256:\${*: -1}-image-$replaced"
		elif [[ "$*" == *State.StartedAt* ]]; then
			printf '2026-07-14T00:0%s:00Z\\n' "$replaced"
		elif [[ "$*" == *com.docker.compose.project* ]]; then
			if [[ "$replaced" == 1 ]]; then printf '%s\\n' replacement-project; else printf '%s\\n' "$FAKE_PROJECT"; fi
		elif [[ "$*" == *com.docker.compose.service* ]]; then
			if [[ "$replaced" == 1 ]]; then printf '%s\\n' replacement-service; else printf '%s\\n' fake-service; fi
		elif [[ "$*" == *NetworkSettings.Ports* ]]; then
			if [[ "$replaced" == 1 ]]; then
				printf '%s\\n' '{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"18081"}]}'
			else
				printf '%s\\n' '{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"18080"}]}'
			fi
		else
			printf '%s\\n' '{}'
		fi
		;;
	stats) printf '%s\\n' '{"CPUPerc":"1.00%","MemUsage":"1MiB / 1GiB"}' ;;
	exec)
		if [[ "$*" == *pg_postmaster_start_time* ]]; then
			printf '%s\\n' '{"name":"runtime-db","serverAddress":"127.0.0.1","serverPort":5432,"postmasterStartedAt":"2026-07-14T00:00:00Z"}'
		elif [[ "$*" == *faithlog-issue195-control* ]]; then
			while IFS= read -r _; do :; done
			printf '%s\\n' '{"snapshotCapturedAt":"2026-07-16T12:48:36.050Z"}'
		fi
		;;
	logs) ;;
esac
`);
	writeExecutable('node', `#!/usr/bin/env bash
set -euo pipefail
if [[ "\${1-}" == *runtime-login.mjs ]]; then
	printf 'login|%s|%s\\n' "\${SCENARIO-}" "\${CASE-}" >> "$TRACE"
	count=$(grep -c '^login|' "$TRACE" || true)
	printf 'e30.eyJleHAiOjQwMDAwMDAwMDB9.token-%s' "$count"
	exit 0
fi
if [[ "\${1-}" == *derive-query-delta.mjs ]]; then
	printf '%s\\n' '{"status":"available","integrity":"verified","observedCallDelta":"0","deltas":[]}' > "$6"
	exit 0
fi
if [[ "\${1-}" == *validate-token-lifetime.mjs ]]; then
	while IFS= read -r _; do :; done
	exit 0
fi
if [[ "\${1-}" == *validate-k6-summary.mjs ]]; then
	printf '%s\\n' '{"status":"adoptable","requestCount":10}' > "$6"
	exit 0
fi
if [[ "\${1-}" == *validate-db-control-window.mjs ]]; then
	printf '%s\\n' '{"schemaVersion":1,"status":"supporting-only","automaticAdoption":false,"evidenceUse":"supporting-only","evidenceCase":"'"$4"'","configuredDuration":"'"$5"'","beforeCaptureStartedAt":"2026-07-16T12:46:35.900Z","beforeCapturedAt":"2026-07-16T12:46:35.950Z","beforeCaptureCompletedAt":"2026-07-16T12:46:36.000Z","afterCaptureStartedAt":"2026-07-16T12:48:36.000Z","afterCapturedAt":"2026-07-16T12:48:36.050Z","afterCaptureCompletedAt":"2026-07-16T12:48:36.100Z","observedElapsedMilliseconds":120000,"beforeCaptureOverheadMilliseconds":100,"afterCaptureOverheadMilliseconds":100,"controlCommitDelta":"18","observerCommitOverhead":1,"backgroundCommitDelta":"17","rollbackDelta":"0","backgroundSubtractionApplied":false}' > "$6"
	exit 0
fi
if [[ "\${1-}" == *validate-db-integrity.mjs || "\${1-}" == *validate-table-counters.mjs ]]; then
	exit 0
fi
exec ${JSON.stringify(process.execPath)} "$@"
`);
	writeExecutable('sleep', `#!/usr/bin/env bash
set -euo pipefail
printf 'sleep|%s\\n' "\${1-}" >> "$TRACE"
`);
	const fakeK6 = writeExecutable('k6', `#!/usr/bin/env bash
set -euo pipefail
printf 'k6|%s|%s|%s|%s|%s|%s|%s|%s\\n' \\
	"\${SCENARIO-}" "\${CASE-}" "\${VUS-}" "\${DURATION-}" "\${PERF_ACCESS_TOKEN-}" \\
	"\${POSTGRES_PASSWORD-}" "\${POSTGRES_USER-}" "\${POSTGRES_DB-}" >> "$TRACE"
summary=''
while [[ "$#" -gt 0 ]]; do
	if [[ "$1" == '--summary-export' ]]; then summary=$2; break; fi
	shift
done
metric="issue195_\${SCENARIO}_\${CASE}"
printf '{"metrics":{"%s_duration":{"p(50)":1,"p(95)":2,"p(99)":3,"max":4},"%s_requests":{"count":10,"rate":2},"%s_failures":{"value":0,"passes":0,"fails":10}}}\\n' \\
	"$metric" "$metric" "$metric" > "$summary"
`);
	const fakeCollector = path.join(tempDirectory, 'collect-db-evidence.sh');
	fs.writeFileSync(
		fakeCollector,
		'#!/usr/bin/env bash\nset -euo pipefail\nprintf \'collector|%s|%s\\n\' "$1" "${PERF_ACCESS_TOKEN-}" >> "$TRACE"\nmkdir -p "$PERF_REPORT_DIR/db-evidence/$1"\n',
	);
	fs.chmodSync(fakeCollector, 0o755);
	fs.writeFileSync(path.join(tempDirectory, 'member-list-baseline.js'), '');

	try {
		const environment = {
				...process.env,
				PATH: `${binaryDirectory}:${process.env.PATH}`,
				REAL_NODE: process.execPath,
				TRACE: tracePath,
				FAKE_PROJECT: `issue195-${path.basename(tempDirectory)}`,
				K6_BIN: fakeK6,
				BASE_URL: 'http://127.0.0.1:18080',
				PERF_DATASET_ID: 'issue195-contract-dataset',
				PERF_FIXTURE_RUN_ID: 'issue195-contract-run',
				PERF_EXECUTION_RUN_ID: 'EXEC195_CONTRACT_RUN',
				PERF_SOURCE_COMMIT: preflightSourceMismatch
					? '0000000000000000000000000000000000000000'
					: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
				PERF_ADMIN_EMAIL: 'runtime-admin@example.test',
				PERF_ADMIN_PASSWORD: 'runtime-secret',
				PERF_ACCESS_TOKEN: 'stale-inherited-token',
				CAMPUS_ID: '101',
				ISOLATION_CAMPUS_ID: '102',
				ISOLATION_USER_ID: '103',
				APP_CONTAINER_ID: 'fake-app',
				EXPECTED_APP_COMPOSE_SERVICE: 'fake-service',
				EXPECTED_APP_IMAGE_ID: 'sha256:fake-app-image-0',
				POSTGRES_CONTAINER_ID: 'fake-postgres',
				EXPECTED_POSTGRES_COMPOSE_SERVICE: 'fake-service',
				EXPECTED_POSTGRES_IMAGE_ID: 'sha256:fake-postgres-image-0',
				REDIS_CONTAINER_ID: 'fake-redis',
				EXPECTED_REDIS_COMPOSE_SERVICE: 'fake-service',
				EXPECTED_REDIS_IMAGE_ID: 'sha256:fake-redis-image-0',
				POSTGRES_USER: 'runtime-db-user',
				POSTGRES_DB: 'runtime-db',
				POSTGRES_PASSWORD: 'runtime-db-secret',
				WARMUP_VUS: '1',
				WARMUP_DURATION: '2m',
				MEASURED_VUS: '1',
				MEASURED_DURATION: '2m',
				MAX_FAILURE_RATE: '0',
				TOKEN_SAFETY_MARGIN_SECONDS: '0',
				EXPECTED_ACTIVE_MEMBERS: '1000',
				EXPECTED_DUTY_ASSIGNMENTS: '101',
				RESOURCE_BOUNDARY_MAX_GAP_SECONDS: '60',
				FAKE_SWAP_AFTER_PRELOCK: swapAfterPreLock ? '1' : '0',
				PERF_REPORT_ROOT: path.join(tempDirectory, 'reports'),
		};
		const result = spawnSync('bash', [runnerPath], {
			cwd: repositoryRoot,
			encoding: 'utf8',
			env: environment,
		});
		const repeated = spawnSync('bash', [runnerPath], {
			cwd: repositoryRoot,
			encoding: 'utf8',
			env: environment,
		});
		const trace = fs.existsSync(tracePath)
			? fs.readFileSync(tracePath, 'utf8').trim().split('\n').filter(Boolean)
			: [];
		const firstRejectionPath = path.join(
			environment.PERF_REPORT_ROOT,
			environment.PERF_DATASET_ID,
			environment.PERF_FIXTURE_RUN_ID,
			environment.PERF_EXECUTION_RUN_ID,
			'first-rejection.json',
		);
		const firstRejection = fs.existsSync(firstRejectionPath)
			? JSON.parse(fs.readFileSync(firstRejectionPath, 'utf8'))
			: null;
		const fakeResult = { ...result, repeated, trace, firstRejection };
		if (!swapAfterPreLock && !preflightSourceMismatch) cachedFakeRunnerResult = fakeResult;
		return fakeResult;
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

function runRunnerReportRootPreflight(mode) {
	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-runner-report-root-'));
	try {
		const bin = path.join(tempDirectory, 'bin');
		const cwd = path.join(tempDirectory, 'cwd');
		const runnerPath = path.join(tempDirectory, 'run-baseline.sh');
		const tracePath = path.join(tempDirectory, 'trace');
		fs.mkdirSync(bin);
		fs.mkdirSync(cwd);
		fs.copyFileSync(files.runner, runnerPath);
		fs.chmodSync(runnerPath, 0o755);
		fs.copyFileSync(files.contract, path.join(tempDirectory, 'scenario-contract.json'));
		for (const command of ['node', 'docker']) {
			const executable = path.join(bin, command);
			fs.writeFileSync(executable, `#!/usr/bin/env bash
printf '${command}\\n' >> ${JSON.stringify(tracePath)}
exit 91
`);
			fs.chmodSync(executable, 0o755);
		}
		const environment = {
			PATH: `${bin}:${process.env.PATH}`,
			BASE_URL: 'http://127.0.0.1:28080',
			PERF_DATASET_ID: 'PERF_REPORT_ROOT_CONTRACT',
			PERF_FIXTURE_RUN_ID: 'ISSUE195_REPORT_ROOT_CONTRACT',
			PERF_EXECUTION_RUN_ID: 'EXEC195_REPORT_ROOT_CONTRACT',
			PERF_SOURCE_COMMIT: JSON.parse(read(files.contract)).sourceIdentity.originDevelopCommit,
			PERF_ADMIN_EMAIL: 'runtime-admin@example.test',
			PERF_ADMIN_PASSWORD: 'runtime-password',
			CAMPUS_ID: '1',
			ISOLATION_CAMPUS_ID: '2',
			ISOLATION_USER_ID: '3',
			APP_CONTAINER_ID: 'app',
			EXPECTED_APP_COMPOSE_SERVICE: 'app',
			EXPECTED_APP_IMAGE_ID: `sha256:${'1'.repeat(64)}`,
			POSTGRES_CONTAINER_ID: 'postgres',
			EXPECTED_POSTGRES_COMPOSE_SERVICE: 'postgres',
			EXPECTED_POSTGRES_IMAGE_ID: `sha256:${'2'.repeat(64)}`,
			REDIS_CONTAINER_ID: 'redis',
			EXPECTED_REDIS_COMPOSE_SERVICE: 'redis',
			EXPECTED_REDIS_IMAGE_ID: `sha256:${'3'.repeat(64)}`,
			POSTGRES_USER: 'runtime-db-user',
			POSTGRES_DB: 'runtime-db',
			POSTGRES_PASSWORD: 'runtime-db-password',
			WARMUP_VUS: '1',
			WARMUP_DURATION: '30s',
			MEASURED_VUS: '10',
			MEASURED_DURATION: '2m',
			MAX_FAILURE_RATE: '0',
			TOKEN_SAFETY_MARGIN_SECONDS: '120',
			EXPECTED_ACTIVE_MEMBERS: '1000',
			EXPECTED_DUTY_ASSIGNMENTS: '101',
			RESOURCE_BOUNDARY_MAX_GAP_SECONDS: '10',
			K6_BIN: '/usr/bin/false',
		};
		const forbiddenReportRoot = mode === 'relative'
			? path.join(cwd, 'relative-reports')
			: path.join(tempDirectory, 'reports');
		if (mode === 'relative') environment.PERF_REPORT_ROOT = 'relative-reports';
		const result = spawnSync('bash', [runnerPath], { cwd, encoding: 'utf8', env: environment });
		return {
			...result,
			trace: fs.existsSync(tracePath)
				? fs.readFileSync(tracePath, 'utf8').trim().split('\n').filter(Boolean)
				: [],
			reportMutated: fs.existsSync(forbiddenReportRoot),
		};
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

function runQueryDelta(beforeRows, afterRows) {
	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-query-delta-'));
	const beforePath = path.join(tempDirectory, 'before.ndjson');
	const afterPath = path.join(tempDirectory, 'after.ndjson');
	const outputPath = path.join(tempDirectory, 'output.json');
	const beforeAvailabilityPath = path.join(tempDirectory, 'before-query-availability.json');
	const afterAvailabilityPath = path.join(tempDirectory, 'after-query-availability.json');
	const writeRows = (file, rows) => fs.writeFileSync(
		file,
		rows.map((row) => JSON.stringify(row)).join('\n') + '\n',
	);

	try {
		writeRows(beforePath, beforeRows);
		writeRows(afterPath, afterRows);
		fs.writeFileSync(beforeAvailabilityPath, `${JSON.stringify(queryAvailability('before', 'available'))}\n`);
		fs.writeFileSync(afterAvailabilityPath, `${JSON.stringify(queryAvailability('after', 'available'))}\n`);
		const result = spawnSync(process.execPath, [
			files.queryDelta,
			beforePath,
			afterPath,
			beforeAvailabilityPath,
			afterAvailabilityPath,
			outputPath,
		], {
			encoding: 'utf8',
		});
		return {
			...result,
			output: JSON.parse(fs.readFileSync(outputPath, 'utf8')),
		};
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

function runQueryAvailabilityFixture({
	beforeAvailability,
	afterAvailability,
	beforeRows,
	afterRows,
	beforeRaw,
	afterRaw,
}) {
	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-query-availability-'));
	const beforePath = path.join(tempDirectory, 'before-query-evidence.ndjson');
	const afterPath = path.join(tempDirectory, 'after-query-evidence.ndjson');
	const beforeAvailabilityPath = path.join(tempDirectory, 'before-query-availability.json');
	const afterAvailabilityPath = path.join(tempDirectory, 'after-query-availability.json');
	const outputPath = path.join(tempDirectory, 'query-delta.json');
	const writeRows = (file, rows) => fs.writeFileSync(
		file,
		rows.map((row) => JSON.stringify(row)).join('\n') + '\n',
	);

	try {
		fs.writeFileSync(beforeAvailabilityPath, `${JSON.stringify(beforeAvailability)}\n`);
		fs.writeFileSync(afterAvailabilityPath, `${JSON.stringify(afterAvailability)}\n`);
		if (beforeRaw !== undefined) fs.writeFileSync(beforePath, beforeRaw);
		else if (beforeRows) writeRows(beforePath, beforeRows);
		if (afterRaw !== undefined) fs.writeFileSync(afterPath, afterRaw);
		else if (afterRows) writeRows(afterPath, afterRows);
		const result = spawnSync(process.execPath, [
			files.queryDelta,
			beforePath,
			afterPath,
			beforeAvailabilityPath,
			afterAvailabilityPath,
			outputPath,
		], { encoding: 'utf8' });
		return {
			...result,
			output: fs.existsSync(outputPath) ? JSON.parse(fs.readFileSync(outputPath, 'utf8')) : null,
		};
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

function queryAvailability(phase, status) {
	return {
		schemaVersion: 1,
		phase,
		status,
		relation: status === 'available' ? 'pg_stat_statements' : null,
	};
}

function runJsonTool(script, args, environment = {}) {
	return spawnSync(process.execPath, [script, ...args], {
		encoding: 'utf8',
		env: { ...process.env, ...environment },
	});
}

function runDbIntegrity(paths, expectedEvidenceCase = 'admin_users-first_page', outputPath = '') {
	const controlPath = path.join(path.dirname(paths[0]), 'control-adoption.json');
	fs.writeFileSync(controlPath, `${JSON.stringify(validDbControlAdoption())}\n`);
	return runJsonTool(files.dbIntegrityValidator, [
		...paths, controlPath, outputPath, expectedEvidenceCase, '2m', '10',
	]);
}

function withTempJsonFiles(prefix, values, callback) {
	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
	try {
		const paths = values.map((value, index) => {
			const file = path.join(tempDirectory, `${index}.json`);
			fs.writeFileSync(file, `${JSON.stringify(value)}\n`);
			return file;
		});
		return callback(paths, tempDirectory);
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

function unsignedJwt(exp) {
	const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
	return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ exp })}.signature`;
}

function pgQueryRow(overrides = {}) {
	return {
		userId: '10',
		dbId: '20',
		queryId: 'q1',
		topLevel: true,
		calls: '10',
		rows: '20',
		totalExecTime: 100,
		query: 'select users',
		...overrides,
	};
}

function validMeasuredAdoption(requestCount = 10) {
	return {
		status: 'adoptable',
		phase: 'measured',
		scenario: 'admin_users',
		case: 'first_page',
		metricName: 'issue195_admin_users_first_page',
		requestCount,
		throughput: 2,
		failureRate: 0,
		latency: { p50: 1, p95: 2, p99: 3, max: 4 },
	};
}

function validDbControlAdoption(overrides = {}) {
	return {
		schemaVersion: 1,
		status: 'supporting-only',
		automaticAdoption: false,
		evidenceUse: 'supporting-only',
		evidenceCase: 'admin_users-first_page',
		configuredDuration: '2m',
		beforeCaptureStartedAt: '2026-07-16T12:46:35.900Z',
		beforeCapturedAt: '2026-07-16T12:46:35.950Z',
		beforeCaptureCompletedAt: '2026-07-16T12:46:36.000Z',
		afterCaptureStartedAt: '2026-07-16T12:48:36.000Z',
		afterCapturedAt: '2026-07-16T12:48:36.050Z',
		afterCaptureCompletedAt: '2026-07-16T12:48:36.100Z',
		observedElapsedMilliseconds: 120_000,
		beforeCaptureOverheadMilliseconds: 100,
		afterCaptureOverheadMilliseconds: 100,
		controlCommitDelta: '18',
		observerCommitOverhead: 1,
		backgroundCommitDelta: '17',
		rollbackDelta: '0',
		backgroundSubtractionApplied: false,
		...overrides,
	};
}

function validDbIntegritySnapshots(requestCount = 10) {
	const maintenance = Object.fromEntries([
		'users',
		'campuses',
		'campus_members',
		'campus_duty_assignments',
	].map((table) => [table, {
		analyzeCount: 1,
		autoanalyzeCount: 2,
		lastAnalyze: null,
		lastAutoanalyze: '2026-07-14T00:00:00Z',
		vacuumCount: 3,
		autovacuumCount: 4,
		lastVacuum: null,
		lastAutovacuum: '2026-07-14T00:00:00Z',
	}]));
	const before = {
		snapshotCapturedAt: '2026-07-16T12:48:36.500Z',
		database: 'faithlog',
		observerApplicationName: 'faithlog-issue195-observer-admin_users-first_page-before',
		externalActiveSessions: 0,
		databaseStats: { xactCommit: '100', xactRollback: '2' },
		plannerSettings: {
			effective_cache_size: '524288',
			enable_hashjoin: 'on',
			enable_indexscan: 'on',
			enable_mergejoin: 'on',
			enable_nestloop: 'on',
			plan_cache_mode: 'auto',
			random_page_cost: '4',
			seq_page_cost: '1',
			work_mem: '4096',
		},
		tableMaintenance: maintenance,
		observerOverhead: {
			beforeSnapshotCommitIncludedInDelta: true,
			expectedCommitCount: 1,
		},
	};
	const after = structuredClone(before);
	after.snapshotCapturedAt = '2026-07-16T12:50:37.500Z';
	after.observerApplicationName = 'faithlog-issue195-observer-admin_users-first_page-after';
	after.databaseStats.xactCommit = (
		BigInt(after.databaseStats.xactCommit) + BigInt(requestCount * 2 + 1)
	).toString();
	return { before, after, measured: validMeasuredAdoption(requestCount) };
}

test('inventory matches the production controller and REST API query contract', () => {
	const adminController = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/admin/controller/AdminManagementController.java',
	));
	const campusController = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/campus/controller/AdminCampusController.java',
	));
	const adminRestDocs = read(path.join(
		repositoryRoot,
		'src/test/java/com/faithlog/admin/controller/AdminManagementApiRestDocsTest.java',
	));
	const campusRestDocs = read(path.join(
		repositoryRoot,
		'src/test/java/com/faithlog/campus/controller/CampusApiRestDocsTest.java',
	));
	const campusRole = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/campus/domain/type/CampusRole.java',
	));
	const campusAccessPolicy = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/campus/service/policy/CampusAccessPolicy.java',
	));

	assert.match(adminController, /@RequestMapping\("\/api\/v1\/admin"\)/);
	assert.match(adminController, /@GetMapping\("\/users"\)/);
	assert.match(adminController, /@GetMapping\("\/campuses"\)/);
	for (const query of ['name', 'email', 'userId', 'role', 'page', 'size', 'sort']) {
		assert.match(adminController, new RegExp(`\\b${query}\\b`));
	}
	for (const query of ['name', 'region', 'status', 'page', 'size', 'sort']) {
		assert.match(adminController, new RegExp(`\\b${query}\\b`));
	}
	assert.match(campusController, /@RequestMapping\("\/api\/v1\/admin\/campuses"\)/);
	assert.match(campusController, /@GetMapping\("\/\{campusId\}\/members"\)/);
	assert.match(campusController, /@GetMapping\("\/\{campusId\}\/duty-assignments"\)/);
	assert.match(adminRestDocs, /document\("admin-users-list-success"/);
	assert.match(adminRestDocs, /document\("admin-campuses-list-success"/);
	assert.match(campusRestDocs, /document\("admin-campus-members-list-success"/);
	assert.match(campusRestDocs, /document\("admin-duty-assignments-list-success"/);
	for (const role of ['MINISTER', 'ELDER', 'CAMPUS_LEADER', 'MEMBER']) {
		assert.match(campusRole, new RegExp(`\\b${role}\\b`));
	}
	assert.match(campusRole, /canManageCampusMembers\(\)[\s\S]*this != MEMBER/);
	assert.match(campusAccessPolicy, /requester\.isAdmin\(\)/);
	assert.match(campusAccessPolicy, /filter\(CampusMember::isActive\)/);
	assert.match(campusAccessPolicy, /requireCampusManager\(requesterMembership/);
});

test('current develop page, archive, permission, RLS, and ordering drift is explicit', () => {
	const contract = JSON.parse(read(files.contract));
	const readRepository = (relativePath) => read(path.join(repositoryRoot, relativePath));
	const adminController = readRepository('src/main/java/com/faithlog/admin/controller/AdminManagementController.java');
	const campusController = readRepository('src/main/java/com/faithlog/campus/controller/AdminCampusController.java');
	const pageValidator = readRepository('src/main/java/com/faithlog/global/controller/PageSortRequestValidator.java');
	const memberService = readRepository('src/main/java/com/faithlog/campus/service/CampusMemberManagementService.java');
	const dutyService = readRepository('src/main/java/com/faithlog/campus/service/CampusDutyAssignmentService.java');
	const memberRepository = readRepository('src/main/java/com/faithlog/campus/infrastructure/repository/CampusMemberRepository.java');
	const dutyRepository = readRepository('src/main/java/com/faithlog/campus/infrastructure/repository/CampusDutyAssignmentRepository.java');
	const rlsMigration = readRepository('src/main/resources/db/migration/V11__secure_supabase_data_api.sql');
	const currentAdminRestDocs = readRepository('src/test/java/com/faithlog/admin/controller/AdminManagementApiRestDocsTest.java');

	assert.equal((adminController.match(/defaultValue = "20"\) int size/g) || []).length, 2);
	assert.match(pageValidator, /MAX_SIZE\s*=\s*100/);
	assert.doesNotMatch(adminController, /includeArchived/);
	assert.doesNotMatch(campusController, /includeArchived/);
	assert.match(campusController, /@RequestParam\(defaultValue = "false"\) String staleOnly/);
	assert.match(campusController, /parseStaleOnly\(staleOnly\)/);
	assert.match(dutyService, /getDutyAssignments\(campusId, requesterId, false\)/);
	assert.match(dutyService, /findActiveWithActiveMemberByCampusIdOrderByIdAsc/);
	assert.match(dutyService, /campusAccessPolicy\.getUsers\(/);
	assert.match(memberService, /findByCampusIdAndStatusOrderByIdAsc[\s\S]*CampusMemberStatus\.ACTIVE/);
	assert.match(memberService, /getUserOrThrow\(member\.userId\(\)\)/);
	assert.match(memberRepository, /findByCampusIdAndStatusOrderByIdAsc/);
	assert.match(dutyRepository, /findActiveWithActiveMemberByCampusIdOrderByIdAsc/);
	assert.match(dutyRepository, /order by assignment\.id asc/);
	assert.match(currentAdminRestDocs, /document\("admin-stale-duty-assignments-list-success"/);
	assert.match(currentAdminRestDocs, /queryParameters\(parameterWithName\("staleOnly"\)/);
	assert.match(rlsMigration, /ENABLE ROW LEVEL SECURITY/);
	assert.doesNotMatch(rlsMigration, /FORCE ROW LEVEL SECURITY/);

	assert.deepEqual(contract.currentDevelop, {
		adminDefaultPageSize: 20,
		maximumPageSize: 100,
		includeArchivedSupported: false,
		dutyDefaultStaleOnly: false,
		dutyUserLookup: 'bulk-current-develop',
		campusMemberUserLookup: 'per-member-current-develop',
		rlsJdbcBoundary: 'owner-jdbc-no-force-rls-runtime-verification-required',
		stableOrdering: 'explicit-id-asc; issue-206-billing-tie-break-not-applicable',
	});
	const duty = contract.endpoints.find(({ key }) => key === 'duty_assignments');
	assert.deepEqual(duty.queryParameters, ['staleOnly']);
	assert.deepEqual(duty.cases, [{ key: 'full_list', query: { staleOnly: false } }]);
	assert.ok(contract.correctness.includes('activeMembershipOnly'));
	assert.ok(contract.correctness.includes('noUnsupportedArchiveParameter'));

	const readme = read(files.readme);
	assert.match(readme, /staleOnly=false/);
	assert.match(readme, /includeArchived.*not supported/i);
	assert.match(readme, /#200.*duty.*bulk/i);
	assert.match(readme, /#202.*FORCE ROW LEVEL SECURITY.*not used/i);
	assert.match(readme, /#206.*billing.*not applicable/i);
});

test('scenario manifest separates endpoints, page depths, filters, and correctness checks', () => {
	const contract = JSON.parse(read(files.contract));

	assert.equal(contract.issue, 195);
	assert.equal(contract.dataset.requiredActiveMembers, 1000);
	assert.deepEqual(contract.dataset.identifiers, ['datasetId', 'fixtureRunId']);
	assert.deepEqual(contract.dataset.measurementIdentifiers, ['executionRunId']);

	const endpoints = Object.fromEntries(contract.endpoints.map((endpoint) => [endpoint.key, endpoint]));
	assert.deepEqual(Object.keys(endpoints), [
		'admin_users',
		'admin_campuses',
		'campus_members',
		'duty_assignments',
	]);
	assert.equal(endpoints.admin_users.path, '/api/v1/admin/users');
	assert.equal(endpoints.admin_users.authorization, 'active service ADMIN');
	assert.deepEqual(endpoints.admin_users.queryParameters, [
		'name', 'email', 'userId', 'role', 'page', 'size', 'sort',
	]);
	assert.deepEqual(endpoints.admin_users.cases.map(({ key }) => key), [
		'first_page', 'middle_page', 'large_page', 'role_filter', 'search_filter',
	]);
	assert.equal(endpoints.admin_campuses.path, '/api/v1/admin/campuses');
	assert.equal(endpoints.admin_campuses.authorization, 'active service ADMIN');
	assert.deepEqual(endpoints.admin_campuses.queryParameters, [
		'name', 'region', 'status', 'page', 'size', 'sort',
	]);
	assert.deepEqual(endpoints.admin_campuses.cases.map(({ key }) => key), [
		'first_page', 'middle_page', 'large_page', 'active_search',
	]);
	assert.equal(endpoints.campus_members.path, '/api/v1/admin/campuses/{campusId}/members');
	assert.equal(
		endpoints.campus_members.authorization,
		'active service ADMIN or active campus MINISTER/ELDER/CAMPUS_LEADER',
	);
	assert.deepEqual(endpoints.campus_members.queryParameters, []);
	assert.deepEqual(endpoints.campus_members.cases.map(({ key }) => key), ['full_list']);
	assert.equal(endpoints.duty_assignments.path, '/api/v1/admin/campuses/{campusId}/duty-assignments');
	assert.equal(
		endpoints.duty_assignments.authorization,
		'active service ADMIN or active campus MINISTER/ELDER/CAMPUS_LEADER',
	);
	assert.deepEqual(endpoints.duty_assignments.queryParameters, ['staleOnly']);
	assert.deepEqual(endpoints.duty_assignments.cases.map(({ key }) => key), ['full_list']);
	assert.deepEqual(endpoints.duty_assignments.cases[0].query, { staleOnly: false });

	assert.deepEqual(contract.metrics, [
		'p50', 'p95', 'p99', 'max', 'throughput', 'failureRate',
		'cpuBoundarySnapshots', 'memoryBoundarySnapshots',
	]);
	assert.deepEqual(contract.correctness, [
		'http200',
		'successEnvelope',
		'resultShape',
		'stableOrder',
		'pageMetadata',
		'filterContract',
		'exactCardinality',
		'campusIsolation',
		'activeMembershipOnly',
		'noUnsupportedArchiveParameter',
	]);
	assert.deepEqual(contract.adoption, [
		'approvedSourceAndImageIdentity',
		'approvedPublishedTargetIdentity',
		'runtimeTargetContinuity',
		'redisRuntimeContinuity',
		'tokenLifetime',
		'nonEmptyFiniteSummary',
		'exactRateObservationCount',
		'validatedBoundaryResourceSnapshots',
		'resourceBoundaryCadence',
		'currentDatabaseCompositeQueryDelta',
		'queryAvailabilityContinuity',
		'nonEmptyProductionQueryInventory',
		'exactCaseEvidenceBinding',
		'externalDatabaseActivity',
		'analyzePlannerStability',
		'tableCounterExpectationsAndInvariance',
		'freshExecutionReportDirectory',
		'conditionalSharedStackClassification',
		'firstMachineReadableRejection',
	]);
	assert.equal(contract.expectedDatabaseTransactionsPerRequest, 2);
});

test('k6 scenario is one-endpoint-per-run and emits endpoint metrics', () => {
	const source = read(files.scenario);

	assert.match(source, /JSON\.parse\(open\(['"]\.\/scenario-contract\.json['"]\)\)/);
	assert.doesNotMatch(source, /import\s+\w+\s+from\s+['"]\.\/scenario-contract\.json['"]/);
	for (const environmentName of [
		'SCENARIO',
		'CASE',
		'PERF_DATASET_ID',
		'PERF_FIXTURE_RUN_ID',
		'CAMPUS_ID',
		'ISOLATION_CAMPUS_ID',
		'ISOLATION_USER_ID',
		'PERF_ACCESS_TOKEN',
		'VUS',
		'DURATION',
		'MAX_FAILURE_RATE',
	]) {
		assert.match(source, new RegExp(`__ENV\\.${environmentName}`));
	}
	assert.match(source, /new Trend\(/);
	assert.match(source, /new Counter\(/);
	assert.match(source, /new Rate\(/);
	assert.match(source, /summaryTrendStats:\s*\['p\(50\)', 'p\(95\)', 'p\(99\)', 'max'\]/);
	assert.match(source, /guardLocalTarget\(\)/);
	assert.match(source, /validateStableOrder/);
	assert.match(source, /validateCampusIsolation/);
	assert.match(source, /validateExactCardinality/);
	assert.doesNotMatch(source, /ALLOW_REMOTE_LOAD/);
	assert.doesNotMatch(source, /http\.(post|put|patch|del)\(/);
	assert.doesNotMatch(source, /__ENV\.VUS\s*\|\|/);
	assert.doesNotMatch(source, /__ENV\.DURATION\s*\|\|/);
	assert.doesNotMatch(source, /__ENV\.MAX_FAILURE_RATE\s*\|\|/);
	assert.match(source, /MAX_FAILURE_RATE[\s\S]*(?:!==|!=)\s*0/);
	assert.match(source, /rate<=\$\{MAX_FAILURE_RATE\}/);
	assert.doesNotMatch(source, /p\((?:90|95|99)\)\s*[<>]=?/);
});

test('fixture preparation is additive and keeps credentials runtime-only', () => {
	const source = read(files.fixture);
	const runtimeIdentity = read(files.runtimeContainerIdentity);

	for (const environmentName of [
		'BASE_URL',
		'PERF_ADMIN_EMAIL',
		'PERF_ADMIN_PASSWORD',
		'PERF_DATASET_ID',
		'PERF_FIXTURE_RUN_ID',
		'APP_CONTAINER_ID',
	]) {
		assert.match(source, new RegExp(`process\\.env\\.${environmentName}`));
	}
	assert.match(source, /requiredActiveMembers\s*=\s*Number\(process\.env\.EXPECTED_ACTIVE_MEMBERS\)/);
	assert.match(source, /requiredActiveMembers !== contract\.dataset\.requiredActiveMembers/);
	assert.match(source, /\/api\/v1\/admin\/campuses\/\$\{campusId\}\/members/);
	assert.match(source, /\/api\/v1\/admin\/campuses\/\$\{campusId\}\/duty-assignments\/meal/);
	assert.match(source, /\/api\/v1\/admin\/campuses\/\$\{campusId\}\/duty-assignments\/coffee/);
	assert.doesNotMatch(source, /method:\s*['"](?:PATCH|DELETE)['"]/);
	assert.doesNotMatch(source, /password\s*:\s*['"][^'"]+['"]/i);
	assert.match(source, /docker/);
	assert.match(`${source}\n${runtimeIdentity}`, /com\.docker\.compose\.project/);
	assert.match(source, /\/tmp\/faithlog-performance-/);
	assert.match(source, /mkdirSync/);
	assert.match(source, /finally[\s\S]*rmdirSync/);
});

test('runtime login is case-scoped before warmup/DB windows and never writes a token', () => {
	const login = read(files.runtimeLogin);
	const runner = read(files.runner);

	assert.match(login, /process\.env\.PERF_ADMIN_EMAIL/);
	assert.match(login, /process\.env\.PERF_ADMIN_PASSWORD/);
	assert.match(login, /\/api\/v1\/auth\/login/);
	assert.match(login, /process\.stdout\.write/);
	assert.doesNotMatch(login, /writeFile|appendFile/);
	assert.match(runner, /PERF_ACCESS_TOKEN=\$\([\s\S]*runtime-login\.mjs/);
	assert.match(runner, /export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD POSTGRES_USER POSTGRES_DB POSTGRES_PASSWORD/);
	assert.match(runner, /unset PERF_ACCESS_TOKEN/);
	assert.doesNotMatch(runner, /export PERF_ACCESS_TOKEN/);
	assert.match(runner, /PERF_ACCESS_TOKEN="\$PERF_ACCESS_TOKEN"[\s\S]*"\$K6_BIN" run/);
	assert.match(runner, /unset PERF_ACCESS_TOKEN/);
	const loop = runner.slice(runner.indexOf('for entry in'));
	const loginIndex = loop.indexOf('runtime-login.mjs');
	const warmupIndex = loop.indexOf('warmup-${SCENARIO}-${CASE}.json');
	const beforeSnapshotIndex = loop.indexOf('collect-db-evidence.sh" "$EVIDENCE_CASE" before');
	assert.ok(loginIndex >= 0 && loginIndex < warmupIndex);
	assert.ok(loginIndex < beforeSnapshotIndex);
});

test('runner refreshes one token per case before warmup across a nominal 44-minute run', () => {
	const result = runFakeBaseline();
	assert.notEqual(result.status, 0, 'complete evidence must still remain conditional-not-adoptable');
	const loginEvents = result.trace.filter((line) => line.startsWith('login|'));
	const k6Events = result.trace.filter((line) => line.startsWith('k6|'));
	assert.equal(11 * (2 + 2), 44, 'the fake orchestration must model more than 30 minutes');
	assert.equal(loginEvents.length, 11, 'each case must perform one fresh login');
	assert.equal(k6Events.length, 22, 'each case must run separate warmup and measured phases');

	const tokens = [];
	for (let index = 0; index < 11; index += 1) {
		const loginTraceIndex = result.trace.indexOf(loginEvents[index]);
		const warmupTraceIndex = result.trace.indexOf(k6Events[index * 2]);
		const measuredTraceIndex = result.trace.indexOf(k6Events[index * 2 + 1], warmupTraceIndex + 1);
		assert.ok(loginTraceIndex < warmupTraceIndex, `case ${index + 1} login must precede warmup`);
		assert.ok(warmupTraceIndex < measuredTraceIndex, `case ${index + 1} warmup must precede measured`);

		const loginFields = loginEvents[index].split('|');
		const warmupFields = k6Events[index * 2].split('|');
		const measuredFields = k6Events[index * 2 + 1].split('|');
		assert.deepEqual(loginFields.slice(1), warmupFields.slice(1, 3));
		assert.equal(warmupFields[5], measuredFields[5], 'warmup and measured must share one case token');
		assert.notEqual(warmupFields[5], '', 'the case token must be present');
		tokens.push(warmupFields[5]);
	}
	assert.equal(new Set(tokens).size, 11, 'a token must not be reused by another case');
});

test('k6 inherits only its access token and never PostgreSQL credentials', () => {
	const result = runFakeBaseline();
	assert.notEqual(result.status, 0, 'complete evidence must still remain conditional-not-adoptable');
	const k6Events = result.trace.filter((line) => line.startsWith('k6|'));
	assert.equal(k6Events.length, 22);
	for (const event of k6Events) {
		const fields = event.split('|');
		assert.notEqual(fields[5], '', 'k6 requires the runtime access token');
		assert.deepEqual(fields.slice(6), ['', '', ''], 'k6 must not inherit PostgreSQL credentials');
	}
	const collectorEvents = result.trace.filter((line) => line.startsWith('collector|'));
	assert.equal(collectorEvents.length, 22);
	for (const event of collectorEvents) {
		assert.equal(event.split('|')[2], '', 'the access token must not escape to DB collectors');
	}
});

test('PostgreSQL password reaches docker exec only through child environment forwarding', () => {
	const secret = 'issue195-db-secret-must-not-appear-in-argv';
	for (const source of [read(files.runner), read(files.dbCollector)]) {
		assert.doesNotMatch(source, /-e PGPASSWORD="\$POSTGRES_PASSWORD"/);
	}

	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-db-credential-'));
	const binaryDirectory = path.join(tempDirectory, 'bin');
	const tracePath = path.join(tempDirectory, 'docker-trace.txt');
	fs.mkdirSync(binaryDirectory);
	const dockerPath = path.join(binaryDirectory, 'docker');
	fs.writeFileSync(dockerPath, `#!/usr/bin/env bash
set -euo pipefail
printf 'argv|%s|env|%s\\n' "$*" "\${PGPASSWORD-}" >> "$TRACE"
if [[ "$*" == *to_regclass* ]]; then printf '%s\\n' ''; fi
`);
	fs.chmodSync(dockerPath, 0o755);

	try {
		const result = spawnSync('bash', [files.dbCollector, 'admin_users-first_page', 'before'], {
			encoding: 'utf8',
			env: {
				...process.env,
				PATH: `${binaryDirectory}:${process.env.PATH}`,
				TRACE: tracePath,
				PERF_REPORT_DIR: path.join(tempDirectory, 'reports'),
				POSTGRES_CONTAINER_ID: 'postgres-container',
				POSTGRES_USER: 'faithlog',
				POSTGRES_DB: 'faithlog',
				POSTGRES_PASSWORD: secret,
				PERF_DATASET_ID: 'ISSUE195_DATASET',
				PERF_FIXTURE_RUN_ID: 'ISSUE195_RUN',
				CAMPUS_ID: '101',
				ISOLATION_CAMPUS_ID: '102',
			},
		});
		assert.equal(result.status, 0, result.stderr);
		const trace = fs.readFileSync(tracePath, 'utf8');
		for (const line of trace.trim().split('\n')) {
			const [, argv, , childPassword] = line.split('|');
			assert.equal(argv.includes(secret), false, 'Docker CLI argv must not contain the password');
			assert.equal(childPassword, secret, 'docker client must receive the command-scoped password for forwarding');
		}
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
});

test('runner serializes every case, records compose labels, and never changes Docker lifecycle', () => {
	const source = read(files.runner);

	assert.match(source, /mkdir "\$LOCK_DIR"/);
	assert.match(source, /cleanup\(\)[\s\S]*rmdir "\$LOCK_DIR"[\s\S]*trap 'cleanup \$\?' EXIT/);
	assert.match(source, /docker inspect/);
	assert.match(source, /com\.docker\.compose\.project/);
	assert.match(source, /com\.docker\.compose\.service/);
	assert.match(source, /docker logs --since/);
	assert.match(source, /\[REDACTED\]/);
	assert.match(source, /docker stats --no-stream/);
	for (const environmentName of [
		'WARMUP_VUS',
		'WARMUP_DURATION',
		'MEASURED_VUS',
		'MEASURED_DURATION',
		'MAX_FAILURE_RATE',
	]) {
		assert.match(source, new RegExp(`\\$\\{${environmentName}:\\?`));
	}
	assert.doesNotMatch(source, /VUS=\$\{VUS:-/);
	assert.doesNotMatch(source, /DURATION=\$\{DURATION:-/);
	assert.match(source, /warmup-[^"']*\.json/);
	assert.match(source, /measured-[^"']*\.json/);
	assert.equal(source.match(/"\$K6_BIN" run/g)?.length, 2);
	assert.match(source, /\/tmp\/faithlog-performance-\$\{?COMPOSE_PROJECT/);
	assert.match(source, /admin_users:first_page/);
	assert.match(source, /admin_users:middle_page/);
	assert.match(source, /admin_users:large_page/);
	assert.match(source, /admin_campuses:first_page/);
	assert.match(source, /admin_campuses:middle_page/);
	assert.match(source, /admin_campuses:large_page/);
	assert.match(source, /campus_members:full_list/);
	assert.match(source, /duty_assignments:full_list/);
	assert.doesNotMatch(source, /docker compose .*\b(?:up|down|build)\b/);
	assert.doesNotMatch(source, /docker .*\bprune\b/);
	assert.doesNotMatch(source, /&\s*$/m);
});

test('DB evidence captures table counters and optional query evidence without writes', () => {
	const sql = read(files.dbCounters);
	const collector = read(files.dbCollector);

	for (const table of ['users', 'campuses', 'campus_members', 'campus_duty_assignments']) {
		assert.match(sql, new RegExp(`\\b${table}\\b`));
	}
	assert.match(sql, /:'fixture_run_id'/);
	assert.match(collector, /pg_stat_statements/);
	assert.match(collector, /query-availability\.json/);
	assert.match(collector, /case_name/);
	assert.match(collector, /before/);
	assert.match(collector, /after/);
	assert.match(collector, /query not ilike '%pg_stat_statements%'/i);
	assert.doesNotMatch(sql, /\b(?:INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE)\b/i);

	const runner = read(files.runner);
	const loopIndex = runner.indexOf('for entry in');
	assert.notEqual(loopIndex, -1);
	const loop = runner.slice(loopIndex);
	assert.match(loop, /collect-db-evidence\.sh[^\n]*before/);
	assert.match(loop, /collect-db-evidence\.sh[^\n]*after/);
	assert.match(runner, /case-windows\.ndjson/);
	assert.match(loop, /record_case_window/);
	assert.match(loop, /derive-query-delta\.mjs/);
	const delta = read(files.queryDelta);
	assert.match(delta, /BigInt\(after\.calls\) - BigInt\(before\.calls\)/);
	assert.match(delta, /after\.totalExecTime - before\.totalExecTime/);
});

test('DB collector keeps every table, runtime, and query artifact free of psql status banners', () => {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-psql-machine-output-'));
	const binDirectory = path.join(directory, 'bin');
	const reportDirectory = path.join(directory, 'report');
	fs.mkdirSync(binDirectory);
	const dockerPath = path.join(binDirectory, 'docker');
	fs.writeFileSync(dockerPath, `#!/usr/bin/env bash
set -euo pipefail
quiet=0
for argument in "$@"; do
	if [[ "$argument" == '-q' || "$argument" == '--quiet' ]]; then quiet=1; fi
done
if [[ "$*" == *to_regclass* ]]; then
	if [[ "\${FAKE_PGSS_STATUS}" == available ]]; then printf 'pg_stat_statements\\n'; fi
	exit 0
fi
if [[ "$*" == *json_build_object* && "$*" == *pg_stat_statements* ]]; then
	printf '%s\\n' '{"userId":"1","dbId":"1","queryId":"1","topLevel":true,"calls":"1","rows":"1","totalExecTime":1,"query":"select * from users"}'
	exit 0
fi
payload=$(cat)
if [[ "$payload" == *faithlog_issue195_runtime_integrity_observer* ]]; then
	if [[ "$quiet" == 0 ]]; then
		printf '%s\\n' 'Tuples only is on.' 'Output format is unaligned.'
	fi
	printf '%s\\n' '{"database":"runtime-db","observerApplicationName":"faithlog-issue195-observer-admin_users-first_page-before"}'
else
	if [[ "$quiet" == 0 ]]; then
		printf '%s\\n' 'Output format is csv.' 'Tuples only is on.'
	fi
	printf '%s\\n' 'users_total,1001' 'users_active,1001'
fi
`);
	fs.chmodSync(dockerPath, 0o755);

	const runCollector = (phase, pgssStatus, reportRoot) => spawnSync(
		'bash',
		[files.dbCollector, 'admin_users-first_page', phase],
		{
			cwd: repositoryRoot,
			encoding: 'utf8',
			env: {
				PATH: `${binDirectory}:${process.env.PATH}`,
				FAKE_PGSS_STATUS: pgssStatus,
				PERF_REPORT_DIR: reportRoot,
				POSTGRES_CONTAINER_ID: 'fake-postgres',
				POSTGRES_USER: 'runtime-user',
				POSTGRES_DB: 'runtime-db',
				POSTGRES_PASSWORD: 'runtime-only-password',
				PERF_DATASET_ID: 'PERF_FAKE_DATASET',
				PERF_FIXTURE_RUN_ID: 'ISSUE195_FAKE_FIXTURE',
				CAMPUS_ID: '101',
				ISOLATION_CAMPUS_ID: '102',
			},
		},
	);
	const assertNoStatusBanner = (contents, label) => {
		assert.doesNotMatch(contents, /^(?:Output format is .+|Tuples only is (?:on|off)\.)$/m, label);
	};

	try {
		for (const phase of ['before', 'after']) {
			const result = runCollector(phase, 'available', reportDirectory);
			assert.equal(result.status, 0, `${phase}: collector failed without exposing stderr`);
			const evidenceDirectory = path.join(reportDirectory, 'db-evidence', 'admin_users-first_page');
			const table = read(path.join(evidenceDirectory, `${phase}-table-counters.csv`));
			const runtime = read(path.join(evidenceDirectory, `${phase}-runtime-integrity.json`));
			const availability = read(path.join(evidenceDirectory, `${phase}-query-availability.json`));
			const queries = read(path.join(evidenceDirectory, `${phase}-query-evidence.ndjson`));
			assertNoStatusBanner(table, `${phase} table counters`);
			assertNoStatusBanner(runtime, `${phase} runtime integrity`);
			assertNoStatusBanner(availability, `${phase} query availability`);
			assertNoStatusBanner(queries, `${phase} query evidence`);
			assert.doesNotThrow(() => JSON.parse(runtime));
			assert.doesNotThrow(() => JSON.parse(availability));
			for (const line of queries.trim().split('\n')) assert.doesNotThrow(() => JSON.parse(line));
		}

		const unavailableReport = path.join(directory, 'unavailable-report');
		const unavailable = runCollector('before', 'unavailable', unavailableReport);
		assert.equal(unavailable.status, 0, 'unavailable collector failed without exposing stderr');
		const unavailableDirectory = path.join(unavailableReport, 'db-evidence', 'admin_users-first_page');
		const marker = read(path.join(unavailableDirectory, 'before-query-availability.json'));
		assertNoStatusBanner(marker, 'unavailable query marker');
		assert.equal(JSON.parse(marker).status, 'unavailable');
		assert.equal(fs.existsSync(path.join(unavailableDirectory, 'before-query-evidence.ndjson')), false);

		const preservedReport = process.env.ISSUE195_EXECUTION_D_REPORT;
		if (preservedReport) {
			assert.ok(path.isAbsolute(preservedReport));
			const paths = {
				rejection: path.join(preservedReport, 'first-rejection.json'),
				table: path.join(preservedReport, 'db-evidence', 'admin_users-first_page', 'after-table-counters.csv'),
				beforeRuntime: path.join(preservedReport, 'db-evidence', 'admin_users-first_page', 'before-runtime-integrity.json'),
				afterRuntime: path.join(preservedReport, 'db-evidence', 'admin_users-first_page', 'after-runtime-integrity.json'),
			};
			const before = Object.fromEntries(Object.entries(paths).map(([key, filePath]) => [key, {
				content: fs.readFileSync(filePath, 'utf8'),
				stats: fs.statSync(filePath),
			}]));
			const rejection = JSON.parse(before.rejection.content);
			assert.equal(rejection.stage, 'measured-evidence-after');
			assert.equal(rejection.scenario, 'admin_users');
			assert.equal(rejection.case, 'first_page');
			assert.match(before.table.content, /^Output format is csv\.$/m);
			for (const [key, filePath] of Object.entries(paths)) {
				assert.equal(fs.readFileSync(filePath, 'utf8'), before[key].content);
				const afterStats = fs.statSync(filePath);
				assert.equal(afterStats.size, before[key].stats.size);
				assert.equal(afterStats.mtimeMs, before[key].stats.mtimeMs);
			}
		}
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('query delta rejects a pg_stat_statements counter reset', () => {
	const result = runQueryDelta(
		[pgQueryRow()],
		[pgQueryRow({ calls: '2', rows: '3', totalExecTime: 10 })],
	);

	assert.notEqual(result.status, 0, 'counter regression must make the evidence non-adoptable');
	assert.equal(result.output.status, 'non-adoptable');
	assert.equal(result.output.integrity, 'lost');
	assert.ok(result.output.anomalies.some(({ reason }) => reason === 'counter-regression'));
});

test('query delta rejects a before query that disappears from the after inventory', () => {
	const result = runQueryDelta(
		[pgQueryRow()],
		[pgQueryRow({ queryId: 'q2', calls: '1', rows: '1', totalExecTime: 1, query: 'select campuses' })],
	);

	assert.notEqual(result.status, 0, 'a missing prior query must make the evidence non-adoptable');
	assert.equal(result.output.status, 'non-adoptable');
	assert.equal(result.output.integrity, 'lost');
	assert.ok(result.output.anomalies.some(({ reason }) => reason === 'before-query-missing-after'));
});

test('query delta distinguishes verified zero calls from unavailable evidence', () => {
	const snapshot = [pgQueryRow()];
	const result = runQueryDelta(snapshot, snapshot);

	assert.equal(result.status, 0);
	assert.equal(result.output.status, 'available');
	assert.equal(result.output.integrity, 'verified');
	assert.equal(result.output.observedCallDelta, '0');
	assert.deepEqual(result.output.deltas, []);
});

test('query evidence availability is exact, continuous, and fail-closed across phases', () => {
	const snapshot = [pgQueryRow()];
	const unavailable = runQueryAvailabilityFixture({
		beforeAvailability: queryAvailability('before', 'unavailable'),
		afterAvailability: queryAvailability('after', 'unavailable'),
	});
	assert.equal(unavailable.status, 0, unavailable.stderr);
	assert.equal(unavailable.output?.status, 'query-evidence-unavailable');

	const available = runQueryAvailabilityFixture({
		beforeAvailability: queryAvailability('before', 'available'),
		afterAvailability: queryAvailability('after', 'available'),
		beforeRows: snapshot,
		afterRows: snapshot,
	});
	assert.equal(available.status, 0, available.stderr);
	assert.equal(available.output?.integrity, 'verified');

	for (const fixture of [
		{
			beforeAvailability: queryAvailability('before', 'available'),
			afterAvailability: queryAvailability('after', 'unavailable'),
			beforeRows: snapshot,
		},
		{
			beforeAvailability: queryAvailability('before', 'unavailable'),
			afterAvailability: queryAvailability('after', 'available'),
			afterRows: snapshot,
		},
		{
			beforeAvailability: queryAvailability('before', 'unavailable'),
			afterAvailability: queryAvailability('after', 'unavailable'),
			beforeRows: snapshot,
		},
		{
			beforeAvailability: queryAvailability('before', 'available'),
			afterAvailability: queryAvailability('after', 'available'),
			afterRows: snapshot,
		},
		{
			beforeAvailability: { ...queryAvailability('before', 'unavailable'), unexpected: true },
			afterAvailability: queryAvailability('after', 'unavailable'),
		},
	]) {
		const result = runQueryAvailabilityFixture(fixture);
		assert.notEqual(result.status, 0);
		assert.equal(result.output?.status, 'non-adoptable');
	}

	const collector = read(files.dbCollector);
	assert.match(collector, /\$\{PHASE\}-query-availability\.json/);
	assert.doesNotMatch(collector, /query-evidence-unavailable\.txt/);
});

test('available query snapshots require strict non-empty production NDJSON evidence', () => {
	const availability = {
		beforeAvailability: queryAvailability('before', 'available'),
		afterAvailability: queryAvailability('after', 'available'),
	};
	for (const [label, fixture, expectedReason] of [
		['empty', { ...availability, beforeRows: [], afterRows: [] }, 'available-query-snapshot-empty'],
		['observer-only', {
			...availability,
			beforeRows: [pgQueryRow({ query: '/* faithlog_issue195_runtime_integrity_observer */ select users' })],
			afterRows: [pgQueryRow({ query: '/* faithlog_issue195_runtime_integrity_observer */ select users' })],
		}, 'available-query-snapshot-empty'],
		['malformed', { ...availability, beforeRaw: '{not-json}\n', afterRows: [pgQueryRow()] }, 'available-query-snapshot-malformed'],
	]) {
		const result = runQueryAvailabilityFixture(fixture);
		assert.notEqual(result.status, 0, label);
		assert.equal(result.output?.status, 'non-adoptable', label);
		assert.ok(result.output?.anomalies.some(({ reason }) => reason === expectedReason), label);
	}
});

test('BASE_URL is runtime-required and bound to approved Compose services and the app published port', () => {
	const runner = read(files.runner);
	const fixture = read(files.fixture);
	const scenario = read(files.scenario);
	const login = read(files.runtimeLogin);

	assert.doesNotMatch(runner, /BASE_URL=\$\{BASE_URL:-/);
	assert.doesNotMatch(fixture, /process\.env\.BASE_URL\s*\|\|/);
	assert.doesNotMatch(scenario, /__ENV\.BASE_URL\s*\|\|/);
	assert.doesNotMatch(login, /process\.env\.BASE_URL\s*\|\|/);
	for (const name of ['EXPECTED_APP_COMPOSE_SERVICE', 'EXPECTED_POSTGRES_COMPOSE_SERVICE']) {
		assert.match(runner, new RegExp(`\\$\\{${name}:\\?`));
	}
	assert.match(fixture, /process\.env\.EXPECTED_APP_COMPOSE_SERVICE/);
	assert.ok(fixture.indexOf('validateTargetIdentity') < fixture.indexOf('await prepareFixture'));

	const common = {
		BASE_URL: 'http://127.0.0.1:18080',
		APP_CONTAINER_ID: 'app-container',
		APP_IMAGE_ID: 'sha256:app-image',
		EXPECTED_APP_IMAGE_ID: 'sha256:app-image',
		APP_COMPOSE_SERVICE: 'app',
		EXPECTED_APP_COMPOSE_SERVICE: 'app',
		POSTGRES_COMPOSE_SERVICE: 'postgres',
		POSTGRES_CONTAINER_ID: 'postgres-container',
		EXPECTED_POSTGRES_COMPOSE_SERVICE: 'postgres',
		POSTGRES_IMAGE_ID: 'sha256:postgres-image',
		EXPECTED_POSTGRES_IMAGE_ID: 'sha256:postgres-image',
		REDIS_CONTAINER_ID: 'redis-container',
		REDIS_COMPOSE_SERVICE: 'redis',
		EXPECTED_REDIS_COMPOSE_SERVICE: 'redis',
		REDIS_IMAGE_ID: 'sha256:redis-image',
		EXPECTED_REDIS_IMAGE_ID: 'sha256:redis-image',
		APP_PUBLISHED_PORTS_JSON: JSON.stringify({
			'8080/tcp': [{ HostIp: '127.0.0.1', HostPort: '18080' }],
		}),
	};
	const valid = runJsonTool(files.targetIdentity, [], common);
	assert.equal(valid.status, 0, valid.stderr);
	const mismatch = runJsonTool(files.targetIdentity, [], {
		...common,
		BASE_URL: 'http://127.0.0.1:18081',
	});
	assert.notEqual(mismatch.status, 0, 'a port mismatch must stop before login or fixture mutation');
	assert.notEqual(runJsonTool(files.targetIdentity, [], {
		...common,
		EXPECTED_APP_IMAGE_ID: 'sha256:other-app-image',
	}).status, 0, 'an unapproved app image must fail before bootstrap');
	assert.notEqual(runJsonTool(files.targetIdentity, [], {
		...common,
		EXPECTED_REDIS_IMAGE_ID: 'sha256:other-redis-image',
	}).status, 0, 'an unapproved Redis image must fail before bootstrap');

	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-fixture-target-'));
	try {
		const dockerPath = path.join(tempDirectory, 'docker');
		fs.writeFileSync(dockerPath, `#!/usr/bin/env bash
container="\${2-}"
if [[ "$container" == fake-postgres ]]; then
	service=postgres
	id=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
	image=2222222222222222222222222222222222222222222222222222222222222222
elif [[ "$container" == fake-redis ]]; then
	service=redis
	id=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
	image=3333333333333333333333333333333333333333333333333333333333333333
else
	service=app
	id=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
	image=1111111111111111111111111111111111111111111111111111111111111111
fi
printf '[{"Id":"%s","Name":"/%s","Image":"sha256:%s","State":{"StartedAt":"2026-07-16T00:00:00Z"},"Config":{"Labels":{"com.docker.compose.project":"fixture-project","com.docker.compose.service":"%s"}},"NetworkSettings":{"Ports":{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"18080"}]}}}]\\n' "$id" "$container" "$image" "$service"
`);
		fs.chmodSync(dockerPath, 0o755);
		const fixtureResult = spawnSync(process.execPath, [files.fixture], {
			encoding: 'utf8',
			env: {
				...process.env,
				PATH: `${tempDirectory}:${process.env.PATH}`,
				BASE_URL: 'http://127.0.0.1:18081',
				PERF_ADMIN_EMAIL: 'must-not-login@example.test',
				PERF_ADMIN_PASSWORD: 'must-not-mutate',
				PERF_DATASET_ID: 'PERF_CONTRACT',
				PERF_FIXTURE_RUN_ID: 'ISSUE195_CONTRACT',
				PERF_SOURCE_COMMIT: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
				APP_CONTAINER_ID: 'fake-app',
				EXPECTED_APP_COMPOSE_SERVICE: 'app',
				EXPECTED_APP_IMAGE_ID: `sha256:${'1'.repeat(64)}`,
				POSTGRES_CONTAINER_ID: 'fake-postgres',
				EXPECTED_POSTGRES_COMPOSE_SERVICE: 'postgres',
				EXPECTED_POSTGRES_IMAGE_ID: `sha256:${'2'.repeat(64)}`,
				REDIS_CONTAINER_ID: 'fake-redis',
				EXPECTED_REDIS_COMPOSE_SERVICE: 'redis',
				EXPECTED_REDIS_IMAGE_ID: `sha256:${'3'.repeat(64)}`,
				EXPECTED_ACTIVE_MEMBERS: '1000',
				EXPECTED_DUTY_ASSIGNMENTS: '101',
				TOKEN_SAFETY_MARGIN_SECONDS: '120',
				PERF_REPORT_ROOT: path.join(tempDirectory, 'reports'),
			},
		});
		assert.notEqual(fixtureResult.status, 0);
		assert.match(fixtureResult.stderr, /must match exactly one published port/);
		const rejection = JSON.parse(fs.readFileSync(path.join(
			tempDirectory,
			'reports',
			'PERF_CONTRACT',
			'ISSUE195_CONTRACT',
			'first-rejection.json',
		), 'utf8'));
		assert.equal(rejection.stage, 'pre-lock-identity');
		assert.deepEqual(rejection.mutationCounts, { campuses: 0, memberships: 0, duties: 0 });
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
});

test('token lifetime validator fails closed against a fake clock before a long case starts', () => {
	const now = 2_000_000_000;
	const environment = {
		PERF_CONTRACT_TEST: '1',
		TOKEN_CLOCK_EPOCH_SECONDS: String(now),
		WARMUP_DURATION: '30s',
		MEASURED_DURATION: '2m',
		TOKEN_SAFETY_MARGIN_SECONDS: '120',
		TOKEN_LIFETIME_PHASE: 'case',
	};
	const valid = spawnSync(process.execPath, [files.tokenLifetime], {
		encoding: 'utf8',
		input: unsignedJwt(now + 390),
		env: { ...process.env, ...environment },
	});
	assert.equal(valid.status, 0, valid.stderr);
	const shortLived = spawnSync(process.execPath, [files.tokenLifetime], {
		encoding: 'utf8',
		input: unsignedJwt(now + 389),
		env: { ...process.env, ...environment },
	});
	assert.notEqual(shortLived.status, 0);
	assert.match(shortLived.stderr, /remaining lifetime/i);
	const runnerLoop = read(files.runner).slice(read(files.runner).indexOf('for entry in'));
	const caseLifetimeIndex = runnerLoop.indexOf('TOKEN_LIFETIME_PHASE=case');
	const warmupIndex = runnerLoop.indexOf('CURRENT_STAGE=warmup-k6');
	const controlIndex = runnerLoop.indexOf('CURRENT_STAGE=measured-control-before');
	const measuredLifetimeIndex = runnerLoop.indexOf('TOKEN_LIFETIME_PHASE=measured');
	assert.ok(caseLifetimeIndex >= 0 && caseLifetimeIndex < warmupIndex);
	assert.ok(warmupIndex < controlIndex && controlIndex < measuredLifetimeIndex);
	const measuredBoundary = {
		...environment,
		WARMUP_DURATION: '1s',
		MEASURED_DURATION: '9m',
		TOKEN_SAFETY_MARGIN_SECONDS: '0',
		TOKEN_LIFETIME_PHASE: 'measured',
	};
	const expiresAtBoundary = spawnSync(process.execPath, [files.tokenLifetime], {
		encoding: 'utf8',
		input: unsignedJwt(now + 540),
		env: { ...process.env, ...measuredBoundary },
	});
	assert.notEqual(expiresAtBoundary.status, 0);
	const survivesMeasured = spawnSync(process.execPath, [files.tokenLifetime], {
		encoding: 'utf8',
		input: unsignedJwt(now + 541),
		env: { ...process.env, ...measuredBoundary },
	});
	assert.equal(survivesMeasured.status, 0, survivesMeasured.stderr);
});

test('query evidence is current-database complete and rejects every after-only cumulative row', () => {
	const collector = read(files.dbCollector);
	assert.match(collector, /dbid\s*=\s*\(select oid from pg_database where datname = current_database\(\)\)/i);
	assert.doesNotMatch(collector, /limit\s+100/i);

	const result = runQueryDelta(
		[pgQueryRow({ rows: '10', totalExecTime: 10 })],
		[
			pgQueryRow({ calls: '11', rows: '11', totalExecTime: 11 }),
			pgQueryRow({ queryId: 'q2', calls: '50', rows: '50', totalExecTime: 50, query: 'select campuses' }),
		],
	);
	assert.notEqual(result.status, 0);
	assert.equal(result.output.status, 'non-adoptable');
	assert.ok(result.output.anomalies.some(({ reason }) => reason === 'after-query-missing-before'));
});

test('query delta preserves the full pg_stat_statements identity and rejects malformed duplicates', () => {
	const collector = read(files.dbCollector);
	for (const field of ['userid', 'dbid', 'queryid', 'toplevel']) {
		assert.match(collector, new RegExp(`['\"]${field === 'userid' ? 'userId' : field === 'dbid' ? 'dbId' : field === 'queryid' ? 'queryId' : 'topLevel'}['\"]\\s*,\\s*${field}`, 'i'));
	}
	const before = [
		pgQueryRow({ userId: '10', queryId: '42' }),
		pgQueryRow({ userId: '11', queryId: '42', calls: '20', rows: '40', totalExecTime: 200 }),
	];
	const after = [
		pgQueryRow({ userId: '10', queryId: '42', calls: '11', rows: '21', totalExecTime: 101 }),
		pgQueryRow({ userId: '11', queryId: '42', calls: '21', rows: '41', totalExecTime: 201 }),
	];
	const valid = runQueryDelta(before, after);
	assert.equal(valid.status, 0, valid.stderr);
	assert.equal(valid.output.observedCallDelta, '2');
	assert.equal(valid.output.deltas.length, 2);

	for (const [label, malformedBefore, malformedAfter] of [
		['duplicate', [before[0], structuredClone(before[0])], [after[0]]],
		['missing', [{ ...before[0], userId: undefined }], [{ ...after[0], userId: undefined }]],
	]) {
		const result = runQueryDelta(malformedBefore, malformedAfter);
		assert.notEqual(result.status, 0, label);
		assert.equal(result.output.status, 'non-adoptable', label);
	}
});

test('DB integrity validator rejects external activity, auto-analyze, and planner drift', () => {
	const tokenVersionChecker = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/user/infrastructure/adapter/UserAccessTokenVersionChecker.java',
	));
	assert.match(tokenVersionChecker, /matchesCurrentVersion[\s\S]*userRepository\.findById/);
	for (const [file, method] of [
		['src/main/java/com/faithlog/admin/service/AdminUserManagementService.java', 'searchUsers'],
		['src/main/java/com/faithlog/admin/service/AdminCampusManagementService.java', 'searchCampuses'],
		['src/main/java/com/faithlog/campus/service/CampusMemberManagementService.java', 'getCampusMembers'],
		['src/main/java/com/faithlog/campus/service/CampusDutyAssignmentService.java', 'getDutyAssignments'],
	]) {
		assert.match(read(path.join(repositoryRoot, file)), new RegExp(`@Transactional\\(readOnly = true\\)[\\s\\S]*${method}`));
	}
	const { before, after: validAfter, measured } = validDbIntegritySnapshots();

	withTempJsonFiles('faithlog-195-db-integrity-', [before, validAfter, measured], (paths) => {
		const valid = runDbIntegrity(paths);
		assert.equal(valid.status, 0, valid.stderr);
	});

	const externalAfter = structuredClone(validAfter);
	externalAfter.externalActiveSessions = 1;
	withTempJsonFiles('faithlog-195-db-integrity-', [before, externalAfter, measured], (paths) => {
		assert.notEqual(runDbIntegrity(paths).status, 0);
	});

	const analyzedAfter = structuredClone(validAfter);
	analyzedAfter.tableMaintenance.users.autoanalyzeCount = 3;
	withTempJsonFiles('faithlog-195-db-integrity-', [before, analyzedAfter, measured], (paths) => {
		assert.notEqual(runDbIntegrity(paths).status, 0);
	});

	assert.match(read(files.dbRuntimeIntegrity), /pid\s*<>\s*pg_backend_pid\(\)/);
});

test('DB-wide background commits stay exact, unattributed, and conditional instead of becoming a tolerance', () => {
	assert.equal(fs.existsSync(files.dbControlCapture), true, 'idle control capture must exist');
	assert.equal(fs.existsSync(files.dbControlValidator), true, 'idle control validator must exist');
	const collector = read(files.dbCollector);
	const branchStart = collector.indexOf('if [[ "$PHASE" == "before" ]]');
	const elseIndex = collector.indexOf('\nelse', branchStart);
	const fiIndex = collector.indexOf('\nfi', elseIndex);
	const beforeBranch = collector.slice(branchStart, elseIndex);
	const afterBranch = collector.slice(elseIndex, fiIndex);
	assert.ok(beforeBranch.indexOf('capture_table_counters') < beforeBranch.indexOf('capture_query_evidence'));
	assert.ok(beforeBranch.indexOf('capture_query_evidence') < beforeBranch.indexOf('capture_runtime_integrity'));
	assert.ok(afterBranch.indexOf('capture_runtime_integrity') < afterBranch.indexOf('capture_query_evidence'));
	assert.ok(afterBranch.indexOf('capture_query_evidence') < afterBranch.indexOf('capture_table_counters'));

	const runtimeSql = read(files.dbRuntimeIntegrity);
	assert.match(runtimeSql, /from\s+pg_stat_database/i);
	assert.doesNotMatch(runtimeSql, /database_stats[\s\S]*application_name/i);
	const runner = read(files.runner);
	const loop = runner.slice(runner.indexOf('for entry in'));
	const controlBeforeIndex = loop.indexOf('capture-db-control-snapshot.sh" "$EVIDENCE_CASE" before');
	const controlSleepIndex = loop.indexOf('sleep "$MEASURED_DURATION"');
	const controlAfterIndex = loop.indexOf('capture-db-control-snapshot.sh" "$EVIDENCE_CASE" after');
	const measuredBeforeIndex = loop.indexOf('collect-db-evidence.sh" "$EVIDENCE_CASE" before');
	assert.ok(controlBeforeIndex >= 0 && controlBeforeIndex < controlSleepIndex);
	assert.ok(controlSleepIndex < controlAfterIndex && controlAfterIndex < measuredBeforeIndex);
	assert.match(loop, /validate-db-integrity\.mjs[\s\S]*"\$EVIDENCE_CASE"[\s\S]*"\$MEASURED_DURATION"[\s\S]*"\$RESOURCE_BOUNDARY_MAX_GAP_SECONDS"/);
	assert.match(read(files.dbControlCapture), /\|\s*env -i PATH="\$PATH"[\s\S]*node -e/);

	const controlDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-db-control-'));
	try {
		const beforePath = path.join(controlDirectory, 'before.json');
		const afterPath = path.join(controlDirectory, 'after.json');
		const outputPath = path.join(controlDirectory, 'adoption.json');
		const { before: baseBefore, after: baseAfter } = validDbIntegritySnapshots();
		const controlBefore = {
			...baseBefore,
			schemaVersion: 1,
			phase: 'before',
			observerApplicationName: 'faithlog-issue195-control-admin_users-first_page-before',
			captureStartedAt: '2026-07-16T12:46:35.900Z',
			snapshotCapturedAt: '2026-07-16T12:46:35.950Z',
			captureCompletedAt: '2026-07-16T12:46:36.000Z',
			databaseStats: { xactCommit: '1000', xactRollback: '34' },
		};
		const controlAfter = {
			...baseAfter,
			schemaVersion: 1,
			phase: 'after',
			observerApplicationName: 'faithlog-issue195-control-admin_users-first_page-after',
			captureStartedAt: '2026-07-16T12:48:36.000Z',
			snapshotCapturedAt: '2026-07-16T12:48:36.050Z',
			captureCompletedAt: '2026-07-16T12:48:36.100Z',
			databaseStats: { xactCommit: '1018', xactRollback: '34' },
		};
		const writeControl = (beforeValue, afterValue) => {
			fs.writeFileSync(beforePath, `${JSON.stringify(beforeValue)}\n`);
			fs.writeFileSync(afterPath, `${JSON.stringify(afterValue)}\n`);
		};
		writeControl(controlBefore, controlAfter);
		const result = runJsonTool(files.dbControlValidator, [
			beforePath, afterPath, 'admin_users-first_page', '2m', outputPath,
		]);
		assert.equal(result.status, 0, 'idle control must preserve exact observed DB-wide counters');
		assert.deepEqual(JSON.parse(fs.readFileSync(outputPath, 'utf8')), validDbControlAdoption());

		for (const [label, mutate] of [
			['missing', (value) => { delete value.after.databaseStats; }],
			['rollback', (value) => { value.after.databaseStats.xactRollback = '35'; }],
			['maintenance', (value) => { value.after.tableMaintenance.users.autoanalyzeCount += 1; }],
			['external-session', (value) => { value.before.externalActiveSessions = 1; }],
			['short-window', (value) => { value.after.captureStartedAt = '2026-07-16T12:48:35.999Z'; }],
		]) {
			const value = { before: structuredClone(controlBefore), after: structuredClone(controlAfter) };
			mutate(value);
			writeControl(value.before, value.after);
			fs.rmSync(outputPath, { force: true });
			const invalid = runJsonTool(files.dbControlValidator, [
				beforePath, afterPath, 'admin_users-first_page', '2m', outputPath,
			]);
			assert.notEqual(invalid.status, 0, label);
		}
	} finally {
		fs.rmSync(controlDirectory, { recursive: true, force: true });
	}

	const childEnvironmentDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-control-child-env-'));
	try {
		const binaryDirectory = path.join(childEnvironmentDirectory, 'bin');
		const tracePath = path.join(childEnvironmentDirectory, 'node-env.txt');
		const outputPath = path.join(childEnvironmentDirectory, 'control.json');
		fs.mkdirSync(binaryDirectory);
		const fakeNode = path.join(binaryDirectory, 'node');
		fs.writeFileSync(fakeNode, `#!/usr/bin/env bash
set -euo pipefail
env | LC_ALL=C sort >> ${JSON.stringify(tracePath)}
printf '%s\\n' __child_boundary__ >> ${JSON.stringify(tracePath)}
exec ${JSON.stringify(process.execPath)} "$@"
`);
		fs.chmodSync(fakeNode, 0o755);
		const fakeDocker = path.join(binaryDirectory, 'docker');
		fs.writeFileSync(fakeDocker, `#!/usr/bin/env bash
set -euo pipefail
while IFS= read -r _; do :; done
printf '%s\\n' '{"snapshotCapturedAt":"2026-07-16T12:48:36.050Z"}'
`);
		fs.chmodSync(fakeDocker, 0o755);
		const secretValues = [
			'postgres-secret-value', 'admin-secret-value', 'api-secret-value', 'authorization-secret-value',
		];
		const result = spawnSync('bash', [
			files.dbControlCapture, 'admin_users-first_page', 'before', outputPath,
		], {
			encoding: 'utf8',
			env: {
				...process.env,
				PATH: `${binaryDirectory}:${process.env.PATH}`,
				POSTGRES_CONTAINER_ID: 'fake-postgres',
				POSTGRES_USER: 'runtime-user',
				POSTGRES_DB: 'runtime-db',
				POSTGRES_PASSWORD: secretValues[0],
				PERF_ADMIN_PASSWORD: secretValues[1],
				API_KEY: secretValues[2],
				AUTHORIZATION: secretValues[3],
				ARBITRARY_PRIVATE_VALUE: 'arbitrary-private-value',
			},
		});
		assert.equal(result.status, 0, 'control JSON conversion must work with a minimal child environment');
		const trace = fs.readFileSync(tracePath, 'utf8');
		assert.doesNotMatch(trace, /POSTGRES_|PERF_ADMIN_|PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|SESSION|COOKIE|ARBITRARY_PRIVATE_VALUE/i);
		for (const secret of [...secretValues, 'arbitrary-private-value']) assert.equal(trace.includes(secret), false);
		assert.equal(JSON.parse(fs.readFileSync(outputPath, 'utf8')).phase, 'before');
	} finally {
		fs.rmSync(childEnvironmentDirectory, { recursive: true, force: true });
	}

	for (const unattributedCommitDelta of [1n, 26n]) {
		const { before, after, measured } = validDbIntegritySnapshots(4_200);
		before.databaseStats.xactCommit = '129366';
		const expected = 4_200n * 2n + 1n;
		after.databaseStats.xactCommit = (129366n + expected + unattributedCommitDelta).toString();
		withTempJsonFiles('faithlog-195-db-wide-background-', [
			before, after, measured, validDbControlAdoption(),
		], (paths, directory) => {
			const outputPath = path.join(directory, 'adoption.json');
			const result = runJsonTool(files.dbIntegrityValidator, [
				...paths, outputPath, 'admin_users-first_page', '2m', '10',
			]);
			assert.equal(result.status, 0, 'unattributed DB-wide commits must preserve supporting evidence collection');
			const output = JSON.parse(fs.readFileSync(outputPath, 'utf8'));
			assert.equal(output.status, 'conditional-not-adoptable');
			assert.equal(output.automaticAdoption, false);
			assert.equal(output.evidenceUse, 'supporting-only');
			assert.equal(output.transactionAttribution, 'database-wide-unattributed');
			assert.equal(output.expectedCommitDelta, expected.toString());
			assert.equal(output.unattributedCommitDelta, unattributedCommitDelta.toString());
			assert.equal(output.backgroundSubtractionApplied, false);
		});
	}

	for (const [label, mutate] of [
		['commit-arithmetic', (control) => { control.backgroundCommitDelta = '16'; }],
		['duration', (control) => { control.configuredDuration = '1m'; }],
		['stale-control', (control) => {
			control.beforeCaptureStartedAt = '2026-07-16T12:46:19.900Z';
			control.beforeCapturedAt = '2026-07-16T12:46:19.950Z';
			control.beforeCaptureCompletedAt = '2026-07-16T12:46:20.000Z';
			control.afterCaptureStartedAt = '2026-07-16T12:48:20.000Z';
			control.afterCapturedAt = '2026-07-16T12:48:20.050Z';
			control.afterCaptureCompletedAt = '2026-07-16T12:48:20.100Z';
		}],
		['future-control', (control) => {
			control.beforeCaptureStartedAt = '2026-07-16T12:46:36.900Z';
			control.beforeCapturedAt = '2026-07-16T12:46:36.950Z';
			control.beforeCaptureCompletedAt = '2026-07-16T12:46:37.000Z';
			control.afterCaptureStartedAt = '2026-07-16T12:48:37.000Z';
			control.afterCapturedAt = '2026-07-16T12:48:37.050Z';
			control.afterCaptureCompletedAt = '2026-07-16T12:48:37.100Z';
		}],
	]) {
		const { before, after, measured } = validDbIntegritySnapshots();
		const control = validDbControlAdoption();
		mutate(control);
		withTempJsonFiles(`faithlog-195-control-binding-${label}-`, [before, after, measured, control], (paths) => {
			const result = runJsonTool(files.dbIntegrityValidator, [
				...paths, '', 'admin_users-first_page', '2m', '10',
			]);
			assert.notEqual(result.status, 0, label);
		});
	}

	const { before, after, measured } = validDbIntegritySnapshots(4_200);
	after.databaseStats.xactCommit = (BigInt(before.databaseStats.xactCommit) + 8_400n).toString();
	withTempJsonFiles('faithlog-195-db-wide-missing-', [
		before, after, measured, validDbControlAdoption(),
	], (paths) => {
		const result = runJsonTool(files.dbIntegrityValidator, [
			...paths, '', 'admin_users-first_page', '2m', '10',
		]);
		assert.notEqual(result.status, 0, 'a missing expected commit must still fail closed');
	});

	const preservedReport = process.env.ISSUE195_EXECUTION_E_REPORT;
	if (!preservedReport) return;
	assert.ok(path.isAbsolute(preservedReport));
	const evidenceDirectory = path.join(preservedReport, 'db-evidence', 'admin_users-first_page');
	const paths = {
		rejection: path.join(preservedReport, 'first-rejection.json'),
		windows: path.join(preservedReport, 'case-windows.ndjson'),
		measured: path.join(preservedReport, 'measured-admin_users-first_page-adoption.json'),
		before: path.join(evidenceDirectory, 'before-runtime-integrity.json'),
		after: path.join(evidenceDirectory, 'after-runtime-integrity.json'),
		adoption: path.join(evidenceDirectory, 'db-integrity-adoption.json'),
	};
	const snapshot = Object.fromEntries(Object.entries(paths).map(([key, filePath]) => {
		const content = fs.readFileSync(filePath, 'utf8');
		return [key, {
			content,
			hash: createHash('sha256').update(content).digest('hex'),
			stats: fs.statSync(filePath),
		}];
	}));
	const rejection = JSON.parse(snapshot.rejection.content);
	const preservedMeasured = JSON.parse(snapshot.measured.content);
	const preservedBefore = JSON.parse(snapshot.before.content);
	const preservedAfter = JSON.parse(snapshot.after.content);
	assert.deepEqual({ stage: rejection.stage, scenario: rejection.scenario, case: rejection.case }, {
		stage: 'measured-evidence-after', scenario: 'admin_users', case: 'first_page',
	});
	assert.equal(preservedMeasured.requestCount, 4_200);
	assert.equal(preservedBefore.databaseStats.xactCommit, '129366');
	assert.equal(preservedAfter.databaseStats.xactCommit, '137793');
	assert.equal(
		BigInt(preservedAfter.databaseStats.xactCommit) - BigInt(preservedBefore.databaseStats.xactCommit),
		8_427n,
	);
	for (const [key, filePath] of Object.entries(paths)) {
		const content = fs.readFileSync(filePath, 'utf8');
		assert.equal(content, snapshot[key].content);
		assert.equal(createHash('sha256').update(content).digest('hex'), snapshot[key].hash);
		const stats = fs.statSync(filePath);
		assert.equal(stats.size, snapshot[key].stats.size);
		assert.equal(stats.mtimeMs, snapshot[key].stats.mtimeMs);
	}
});

test('runtime integrity excludes only its own PID and counts same-name observer sessions', () => {
	const runtimeSql = read(files.dbRuntimeIntegrity);
	assert.match(runtimeSql, /pid\s*<>\s*pg_backend_pid\(\)/i);
	assert.doesNotMatch(runtimeSql, /application_name\s*<>\s*current_setting\('application_name'\)/i);

	const { before, after, measured } = validDbIntegritySnapshots();
	before.externalActiveSessions = 1;
	withTempJsonFiles('faithlog-195-same-observer-session-', [before, after, measured], (paths) => {
		assert.notEqual(runDbIntegrity(paths).status, 0);
	});
});

test('DB integrity accepts collector phase observer names and rejects another issue or case', () => {
	const { before, after, measured } = validDbIntegritySnapshots();
	withTempJsonFiles('faithlog-195-db-observer-', [before, after, measured], (paths) => {
		const valid = runDbIntegrity(paths);
		assert.equal(valid.status, 0, valid.stderr);
	});

	for (const observerApplicationName of [
		'faithlog-issue199-observer-admin_users-first_page-after',
		'faithlog-issue195-observer-admin_users-middle_page-after',
	]) {
		const mismatchedAfter = structuredClone(after);
		mismatchedAfter.observerApplicationName = observerApplicationName;
		withTempJsonFiles('faithlog-195-db-observer-mismatch-', [before, mismatchedAfter, measured], (paths) => {
			assert.notEqual(runDbIntegrity(paths).status, 0);
		});
	}
});

test('DB integrity binds observer, measured adoption, metric, and runner evidence case exactly', () => {
	const { before, after, measured } = validDbIntegritySnapshots(2);
	measured.scenario = 'duty_assignments';
	measured.case = 'full_list';
	measured.metricName = 'issue195_duty_assignments_full_list';
	withTempJsonFiles('faithlog-195-db-case-mismatch-', [before, after, measured], (paths) => {
		const result = runDbIntegrity(paths, 'admin_users-first_page');
		assert.notEqual(result.status, 0);
	});
});

test('runtime continuity rejects immutable app or PostgreSQL replacement between case boundaries', () => {
	assert.ok(fs.existsSync(files.runtimeContinuityValidator), 'runtime continuity validator is required');
	const runner = read(files.runner);
	assert.match(runner, /capture_runtime_identity[\s\S]*initial-runtime-identity\.json/);
	const loop = runner.slice(runner.indexOf('for entry in'));
	assert.ok((loop.match(/validate-runtime-continuity\.mjs/g) || []).length >= 2);

	const identity = {
		app: {
			containerId: 'sha256:app-container-1',
			name: '/faithlog-app',
			imageId: 'sha256:app-image-1',
			startedAt: '2026-07-14T00:00:00Z',
			composeProject: 'faithlog',
			composeService: 'app',
			publishedPorts: { '8080/tcp': [{ HostIp: '127.0.0.1', HostPort: '18080' }] },
		},
		postgres: {
			containerId: 'sha256:postgres-container-1',
			name: '/faithlog-postgres',
			imageId: 'sha256:postgres-image-1',
			startedAt: '2026-07-14T00:00:00Z',
			composeProject: 'faithlog',
			composeService: 'postgres',
			publishedPorts: { '5432/tcp': [{ HostIp: '127.0.0.1', HostPort: '25432' }] },
		},
		redis: {
			containerId: 'sha256:redis-container-1',
			name: '/faithlog-redis',
			imageId: 'sha256:redis-image-1',
			startedAt: '2026-07-14T00:00:00Z',
			composeProject: 'faithlog',
			composeService: 'redis',
			publishedPorts: { '6379/tcp': [{ HostIp: '127.0.0.1', HostPort: '26379' }] },
		},
		database: {
			name: 'faithlog',
			serverAddress: '127.0.0.1',
			serverPort: 5432,
			postmasterStartedAt: '2026-07-14T00:00:00Z',
		},
	};
	withTempJsonFiles('faithlog-195-runtime-continuity-', [identity, structuredClone(identity)], (paths, directory) => {
		const valid = runJsonTool(files.runtimeContinuityValidator, [...paths, path.join(directory, 'adoption.json')]);
		assert.equal(valid.status, 0, valid.stderr);
	});
	for (const mutate of [
		(value) => { value.app.containerId = 'sha256:app-container-2'; },
		(value) => { value.app.imageId = 'sha256:app-image-2'; },
		(value) => { value.postgres.startedAt = '2026-07-14T00:01:00Z'; },
		(value) => { value.redis.containerId = 'sha256:redis-container-2'; },
		(value) => { value.database.postmasterStartedAt = '2026-07-14T00:01:00Z'; },
	]) {
		const replacement = structuredClone(identity);
		mutate(replacement);
		withTempJsonFiles('faithlog-195-runtime-replacement-', [identity, replacement], (paths, directory) => {
			const result = runJsonTool(files.runtimeContinuityValidator, [...paths, path.join(directory, 'adoption.json')]);
			assert.notEqual(result.status, 0);
		});
	}
});

test('post-lock target replacement fails before login, k6, or DB evidence', () => {
	const result = runFakeBaseline({ swapAfterPreLock: true });
	assert.notEqual(result.status, 0, 'post-lock replacement must fail closed');
	assert.equal(result.trace.filter((line) => /^(login|k6|collector)\|/.test(line)).length, 0);
	assert.match(result.stderr, /target|service|project|published|identity/i);
});

test('DB integrity rejects missing and null evidence instead of coercing it', () => {
	const malformedBefore = {
		externalActiveSessions: 0,
		observerApplicationName: null,
		databaseStats: { xactCommit: null, xactRollback: null },
	};
	const malformedAfter = structuredClone(malformedBefore);
	malformedAfter.databaseStats.xactCommit = 3;
	withTempJsonFiles(
		'faithlog-195-db-malformed-',
		[malformedBefore, malformedAfter, { requestCount: 1 }],
		(paths) => assert.notEqual(runDbIntegrity(paths).status, 0),
	);

	const { before, after, measured } = validDbIntegritySnapshots(1);
	for (const mutate of [
		(value) => { value.before.databaseStats.xactCommit = 100; },
		(value) => { value.after.externalActiveSessions = null; },
		(value) => { delete value.before.plannerSettings.work_mem; },
		(value) => { delete value.after.tableMaintenance.campuses.lastAutoanalyze; },
		(value) => { value.measured.status = 'non-adoptable'; },
	]) {
		const value = { before: structuredClone(before), after: structuredClone(after), measured: structuredClone(measured) };
		mutate(value);
		withTempJsonFiles('faithlog-195-db-malformed-field-', [value.before, value.after, value.measured], (paths) => {
			assert.notEqual(runDbIntegrity(paths).status, 0);
		});
	}
});

test('runtime-integrity observer SQL is excluded from first and existing query snapshots', () => {
	const marker = 'faithlog_issue195_runtime_integrity_observer';
	assert.match(read(files.dbRuntimeIntegrity), new RegExp(marker));
	assert.match(read(files.dbCollector), new RegExp(`query not ilike '%${marker}%'`, 'i'));
	const productionBefore = pgQueryRow({
		queryId: 'production', rows: '10', totalExecTime: 10, query: 'select * from users',
	});
	const productionAfter = { ...productionBefore, calls: '11', rows: '11', totalExecTime: 11 };
	const observer = (calls) => pgQueryRow({
		userId: 'observer',
		queryId: 'observer',
		calls: String(calls),
		rows: String(calls),
		totalExecTime: calls,
		query: `/* ${marker} */ select * from pg_stat_user_tables where relname = 'users'`,
	});

	for (const [label, beforeRows, afterRows] of [
		['first', [productionBefore], [productionAfter, observer(1)]],
		['existing', [productionBefore, observer(5)], [productionAfter, observer(6)]],
	]) {
		const result = runQueryDelta(beforeRows, afterRows);
		assert.equal(result.status, 0, `${label}: ${result.stderr}`);
		assert.deepEqual(result.output.deltas.map(({ queryId }) => queryId), ['production']);
		assert.equal(result.output.observedCallDelta, '1');
	}
});

test('DB maintenance evidence includes vacuum fields and rejects transient autovacuum drift', () => {
	const runtimeSql = read(files.dbRuntimeIntegrity);
	for (const field of ['vacuum_count', 'autovacuum_count', 'last_vacuum', 'last_autovacuum']) {
		assert.match(runtimeSql, new RegExp(`\\b${field}\\b`));
	}
	const { before, after, measured } = validDbIntegritySnapshots();
	after.tableMaintenance.users.autovacuumCount += 1;
	after.tableMaintenance.users.lastAutovacuum = '2026-07-14T00:01:00Z';
	withTempJsonFiles('faithlog-195-db-autovacuum-', [before, after, measured], (paths) => {
		assert.notEqual(runDbIntegrity(paths).status, 0);
	});
});

test('k6 summary validator accepts direct/v2 shapes and rejects invalid counts or percentile order', () => {
	const metricName = 'issue195_admin_users_first_page';
	const validValues = {
		[`${metricName}_duration`]: { 'p(50)': 1, 'p(95)': 2, 'p(99)': 3, max: 4 },
		[`${metricName}_requests`]: { count: 10, rate: 2 },
		[`${metricName}_failures`]: { value: 0, passes: 0, fails: 10 },
	};
	for (const [label, metrics] of [
		['direct', validValues],
		['values', Object.fromEntries(Object.entries(validValues).map(([key, values]) => [key, { values }]))],
	]) {
		withTempJsonFiles(`faithlog-195-summary-${label}-`, [{ metrics }], ([summaryPath], directory) => {
			const outputPath = path.join(directory, 'normalized.json');
			const result = runJsonTool(
				files.summaryValidator,
				[summaryPath, 'admin_users', 'first_page', 'measured', outputPath],
			);
			assert.equal(result.status, 0, result.stderr);
			assert.equal(JSON.parse(fs.readFileSync(outputPath, 'utf8')).requestCount, 10);
		});
	}

	const zeroRequest = structuredClone(validValues);
	zeroRequest[`${metricName}_requests`].count = 0;
	withTempJsonFiles('faithlog-195-summary-zero-', [{ metrics: zeroRequest }], ([summaryPath], directory) => {
		const result = runJsonTool(
			files.summaryValidator,
			[summaryPath, 'admin_users', 'first_page', 'measured', path.join(directory, 'normalized.json')],
		);
		assert.notEqual(result.status, 0);
	});

	for (const [label, mutate] of [
		['fractional-count', (metrics) => { metrics[`${metricName}_requests`].count = 0.5; }],
		['negative-latency', (metrics) => { metrics[`${metricName}_duration`]['p(50)'] = -1; }],
		['reversed-percentiles', (metrics) => {
			metrics[`${metricName}_duration`] = { 'p(50)': 100, 'p(95)': 2, 'p(99)': 1, max: 3 };
		}],
	]) {
		for (const shape of ['direct', 'values']) {
			const malformed = structuredClone(validValues);
			mutate(malformed);
			const metrics = shape === 'values'
				? Object.fromEntries(Object.entries(malformed).map(([key, values]) => [key, { values }]))
				: malformed;
			withTempJsonFiles(`faithlog-195-summary-${label}-${shape}-`, [{ metrics }], ([summaryPath], directory) => {
				const result = runJsonTool(
					files.summaryValidator,
					[summaryPath, 'admin_users', 'first_page', 'measured', path.join(directory, 'normalized.json')],
				);
				assert.notEqual(result.status, 0, `${label}/${shape}`);
			});
		}
	}

	const { before, after, measured } = validDbIntegritySnapshots(1);
	measured.requestCount = 0.5;
	withTempJsonFiles('faithlog-195-db-fractional-request-', [before, after, measured], (paths) => {
		assert.notEqual(runDbIntegrity(paths).status, 0);
	});
});

test('actual k6 v2 Trend, Counter, and Rate shapes normalize losslessly through DB integrity', () => {
	const metricName = 'issue195_admin_users_first_page';
	const actualValues = {
		[`${metricName}_duration`]: {
			max: 1222.103,
			'p(50)': 173.60399999999998,
			'p(95)': 435.8418499999999,
			'p(99)': 667.8575499999997,
		},
		[`${metricName}_requests`]: { count: 142, rate: 4.648273187788043 },
		[`${metricName}_failures`]: {
			passes: 0,
			fails: 142,
			thresholds: { 'rate<=0': false },
			value: 0,
		},
	};
	const shapeMetrics = (shape, values) => shape === 'values'
		? Object.fromEntries(Object.entries(values).map(([key, metricValues]) => [key, { values: metricValues }]))
		: values;

	const preservedSummaryPath = process.env.ISSUE195_EXECUTION_B_SUMMARY;
	if (preservedSummaryPath) {
		assert.ok(path.isAbsolute(preservedSummaryPath), 'preserved B summary path must be absolute');
		assert.ok(fs.existsSync(preservedSummaryPath), 'preserved B summary must exist');
		const beforeContent = fs.readFileSync(preservedSummaryPath, 'utf8');
		const beforeStats = fs.statSync(preservedSummaryPath);
		const preserved = JSON.parse(beforeContent);
		assert.deepEqual(preserved.metrics[`${metricName}_duration`], actualValues[`${metricName}_duration`]);
		assert.deepEqual(preserved.metrics[`${metricName}_requests`], actualValues[`${metricName}_requests`]);
		assert.deepEqual(preserved.metrics[`${metricName}_failures`], actualValues[`${metricName}_failures`]);
		const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-execution-b-readonly-'));
		try {
			const result = runJsonTool(files.summaryValidator, [
				preservedSummaryPath,
				'admin_users',
				'first_page',
				'warmup',
				path.join(directory, 'normalized.json'),
			]);
			assert.equal(fs.readFileSync(preservedSummaryPath, 'utf8'), beforeContent);
			const afterStats = fs.statSync(preservedSummaryPath);
			assert.equal(afterStats.size, beforeStats.size);
			assert.equal(afterStats.mtimeMs, beforeStats.mtimeMs);
			assert.equal(result.status, 0, result.stderr);
		} finally {
			fs.rmSync(directory, { recursive: true, force: true });
		}
	}

	for (const phase of ['warmup', 'measured']) {
		for (const shape of ['direct', 'values']) {
			withTempJsonFiles(
				`faithlog-195-k6-v2-${phase}-${shape}-`,
				[{ metrics: shapeMetrics(shape, actualValues) }],
				([summaryPath], directory) => {
					const normalizedPath = path.join(directory, 'normalized.json');
					const result = runJsonTool(files.summaryValidator, [
						summaryPath, 'admin_users', 'first_page', phase, normalizedPath,
					]);
					assert.equal(result.status, 0, `${phase}/${shape}: ${result.stderr}`);
					const normalized = JSON.parse(fs.readFileSync(normalizedPath, 'utf8'));
					assert.equal(normalized.requestCount, 142);
					assert.equal(normalized.throughput, 4.648273187788043);
					assert.equal(normalized.failureRate, 0);
					assert.deepEqual(normalized.latency, {
						p50: 173.60399999999998,
						p95: 435.8418499999999,
						p99: 667.8575499999997,
						max: 1222.103,
					});
					if (phase === 'measured') {
						const { before, after } = validDbIntegritySnapshots(142);
						withTempJsonFiles(
							`faithlog-195-k6-v2-db-${shape}-`,
							[before, after, normalized],
							(paths, dbDirectory) => {
								const dbResult = runDbIntegrity(
									paths,
									'admin_users-first_page',
									path.join(dbDirectory, 'db-adoption.json'),
								);
								assert.equal(dbResult.status, 0, `${shape}: ${dbResult.stderr}`);
							},
						);
					}
				},
			);
		}
	}

	for (const [label, mutate] of [
		['positive-value', (values) => { values[`${metricName}_failures`].value = 0.01; }],
		['positive-passes', (values) => {
			values[`${metricName}_failures`].passes = 1;
			values[`${metricName}_failures`].fails = 141;
		}],
		['fails-count-mismatch', (values) => { values[`${metricName}_failures`].fails = 141; }],
		['missing-value', (values) => { delete values[`${metricName}_failures`].value; }],
		['null-value', (values) => { values[`${metricName}_failures`].value = null; }],
		['string-value', (values) => { values[`${metricName}_failures`].value = '0'; }],
	]) {
		for (const phase of ['warmup', 'measured']) {
			for (const shape of ['direct', 'values']) {
				const malformed = structuredClone(actualValues);
				mutate(malformed);
				withTempJsonFiles(
					`faithlog-195-k6-v2-${label}-${phase}-${shape}-`,
					[{ metrics: shapeMetrics(shape, malformed) }],
					([summaryPath], directory) => {
						assert.notEqual(runJsonTool(files.summaryValidator, [
							summaryPath,
							'admin_users',
							'first_page',
							phase,
							path.join(directory, 'normalized.json'),
						]).status, 0, `${label}/${phase}/${shape}`);
					},
				);
			}
		}
	}

	for (const shape of ['direct', 'values']) {
		const serialized = JSON.stringify({ metrics: shapeMetrics(shape, actualValues) });
		const raw = serialized.replace('"value":0', '"value":1e400');
		const directory = fs.mkdtempSync(path.join(os.tmpdir(), `faithlog-195-k6-v2-nonfinite-${shape}-`));
		try {
			const summaryPath = path.join(directory, 'summary.json');
			fs.writeFileSync(summaryPath, raw);
			assert.notEqual(runJsonTool(files.summaryValidator, [
				summaryPath,
				'admin_users',
				'first_page',
				'warmup',
				path.join(directory, 'normalized.json'),
			]).status, 0, `nonfinite/warmup/${shape}`);
		} finally {
			fs.rmSync(directory, { recursive: true, force: true });
		}
	}

	assert.doesNotMatch(read(files.dbIntegrityValidator), /\.metrics\?\.|metric\.values/);
	assert.match(read(files.runner), /validate-k6-summary\.mjs[\s\S]*MEASURED_ADOPTION[\s\S]*validate-db-integrity\.mjs/);
});

test('actual Docker rounded memory units normalize to safe bytes through resource adoption', () => {
	const identities = {
		app: { containerId: 'a'.repeat(64), name: '/faithlog-app', imageId: 'b'.repeat(64), startedAt: '2026-07-16T00:00:00Z' },
		postgres: { containerId: 'c'.repeat(64), name: '/faithlog-postgres', imageId: 'd'.repeat(64), startedAt: '2026-07-16T00:00:00Z' },
		redis: { containerId: 'e'.repeat(64), name: '/faithlog-redis', imageId: 'f'.repeat(64), startedAt: '2026-07-16T00:00:00Z' },
	};
	const capture = (memory, phase = 'before') => {
		const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-memory-capture-'));
		try {
			const outputPath = path.join(directory, 'resource.ndjson');
			const env = {
				APP_ACTUAL_ID: identities.app.containerId,
				APP_ACTUAL_NAME: identities.app.name,
				APP_IMAGE_ID: identities.app.imageId,
				APP_STARTED_AT: identities.app.startedAt,
				APP_STATS_JSON: JSON.stringify({ CPUPerc: '1.25%', MemUsage: memory.app }),
				POSTGRES_ACTUAL_ID: identities.postgres.containerId,
				POSTGRES_ACTUAL_NAME: identities.postgres.name,
				POSTGRES_IMAGE_ID: identities.postgres.imageId,
				POSTGRES_STARTED_AT: identities.postgres.startedAt,
				POSTGRES_STATS_JSON: JSON.stringify({ CPUPerc: '0.50%', MemUsage: memory.postgres }),
				REDIS_ACTUAL_ID: identities.redis.containerId,
				REDIS_ACTUAL_NAME: identities.redis.name,
				REDIS_IMAGE_ID: identities.redis.imageId,
				REDIS_STARTED_AT: identities.redis.startedAt,
				REDIS_STATS_JSON: JSON.stringify({ CPUPerc: '0.10%', MemUsage: memory.redis }),
			};
			const result = spawnSync(process.execPath, [
				files.resourceSnapshotCapture,
				'admin_users',
				'first_page',
				phase,
				outputPath,
			], { encoding: 'utf8', env });
			const snapshot = fs.existsSync(outputPath)
				? JSON.parse(fs.readFileSync(outputPath, 'utf8').trim())
				: null;
			return { ...result, snapshot };
		} finally {
			fs.rmSync(directory, { recursive: true, force: true });
		}
	};

	const actual = capture({
		app: '766.3MiB / 7.653GiB',
		postgres: '268.3MiB / 7.653GiB',
		redis: '19.55MiB / 7.653GiB',
	});
	assert.equal(actual.status, 0, actual.stderr);
	assert.deepEqual(actual.snapshot.containers.map(({ role, memoryBytes }) => [role, memoryBytes]), [
		['app', 803_523_789],
		['postgres', 281_332_941],
		['redis', 20_499_661],
	]);

	for (const [unit, expected] of Object.entries({
		B: 2,
		kB: 1_500,
		KB: 1_500,
		KiB: 1_536,
		MB: 1_500_000,
		MiB: 1_572_864,
		GB: 1_500_000_000,
		GiB: 1_610_612_736,
		TB: 1_500_000_000_000,
		TiB: 1_649_267_441_664,
	})) {
		const result = capture({
			app: `1.5${unit} / 2${unit}`,
			postgres: `1.5${unit} / 2${unit}`,
			redis: `1.5${unit} / 2${unit}`,
		});
		assert.equal(result.status, 0, `${unit}: ${result.stderr}`);
		assert.deepEqual(result.snapshot.containers.map(({ memoryBytes }) => memoryBytes), [expected, expected, expected]);
	}

	const maximumSafe = capture({
		app: '9007199254740991B / 9007199254740991B',
		postgres: '9007199254740991B / 9007199254740991B',
		redis: '9007199254740991B / 9007199254740991B',
	});
	assert.equal(maximumSafe.status, 0, maximumSafe.stderr);
	assert.deepEqual(maximumSafe.snapshot.containers.map(({ memoryBytes }) => memoryBytes), [
		Number.MAX_SAFE_INTEGER,
		Number.MAX_SAFE_INTEGER,
		Number.MAX_SAFE_INTEGER,
	]);

	for (const invalid of [
		'-1MiB / 1GiB',
		'NaNMiB / 1GiB',
		'InfinityMiB / 1GiB',
		'1.5XB / 2XB',
		'9007199254740992B / 9007199254740992B',
	]) {
		const result = capture({ app: invalid, postgres: '1MiB / 1GiB', redis: '1MiB / 1GiB' });
		assert.notEqual(result.status, 0, invalid);
	}

	const before = structuredClone(actual.snapshot);
	before.phase = 'before';
	before.capturedAt = '2026-07-16T00:00:01.000Z';
	const after = structuredClone(actual.snapshot);
	after.phase = 'after';
	after.capturedAt = '2026-07-16T00:00:04.000Z';
	const windows = [
		{ scenario: 'admin_users', case: 'first_page', event: 'measured-start', status: 'pending', at: '2026-07-16T00:00:02.000Z' },
		{ scenario: 'admin_users', case: 'first_page', event: 'measured-end', status: 'passed', at: '2026-07-16T00:00:03.000Z' },
	];
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-memory-adoption-'));
	try {
		const identityPath = path.join(directory, 'identity.json');
		const resourcePath = path.join(directory, 'resource.ndjson');
		const windowsPath = path.join(directory, 'windows.ndjson');
		const outputPath = path.join(directory, 'adoption.json');
		fs.writeFileSync(identityPath, `${JSON.stringify(identities)}\n`);
		fs.writeFileSync(resourcePath, `${JSON.stringify(before)}\n${JSON.stringify(after)}\n`);
		fs.writeFileSync(windowsPath, `${windows.map(JSON.stringify).join('\n')}\n`);
		assert.equal(runJsonTool(files.resourceSnapshotValidator, [
			identityPath,
			resourcePath,
			windowsPath,
			'admin_users',
			'first_page',
			'10',
			outputPath,
		]).status, 0);
		after.containers[0].memoryBytes = 803_523_789.5;
		fs.writeFileSync(resourcePath, `${JSON.stringify(before)}\n${JSON.stringify(after)}\n`);
		assert.notEqual(runJsonTool(files.resourceSnapshotValidator, [
			identityPath,
			resourcePath,
			windowsPath,
			'admin_users',
			'first_page',
			'10',
			outputPath,
		]).status, 0);
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('preserved C evidence stays read-only and future summaries cannot persist the access token', () => {
	const scenario = read(files.scenario);
	assert.doesNotMatch(scenario, /return\s*\{\s*token:\s*PERF_ACCESS_TOKEN\s*\}/);
	assert.match(scenario, /export default function\s*\(\s*\)/);
	assert.match(scenario, /Authorization:\s*`Bearer \$\{PERF_ACCESS_TOKEN\}`/);

	const reportDirectory = process.env.ISSUE195_EXECUTION_C_REPORT;
	if (!reportDirectory) return;
	assert.ok(path.isAbsolute(reportDirectory));
	const paths = {
		rejection: path.join(reportDirectory, 'first-rejection.json'),
		adoption: path.join(reportDirectory, 'warmup-admin_users-first_page-adoption.json'),
		summary: path.join(reportDirectory, 'warmup-admin_users-first_page.json'),
	};
	const before = Object.fromEntries(Object.entries(paths).map(([key, filePath]) => [key, {
		content: fs.readFileSync(filePath, 'utf8'),
		stats: fs.statSync(filePath),
	}]));
	assert.deepEqual(JSON.parse(before.rejection.content), {
		schemaVersion: 1,
		status: 'non-adoptable',
		automaticAdoption: false,
		stage: 'measured-resource-before',
		scenario: 'admin_users',
		case: 'first_page',
		exitCode: 1,
	});
	const adoption = JSON.parse(before.adoption.content);
	assert.equal(adoption.requestCount, 530);
	assert.equal(adoption.failureRate, 0);
	const historicalSummary = JSON.parse(before.summary.content);
	assert.equal(typeof historicalSummary.setup_data?.token, 'string', 'C must preserve the historical leak evidence');
	assert.ok(historicalSummary.setup_data.token.length > 0, 'historical token evidence must be non-empty');
	for (const [key, filePath] of Object.entries(paths)) {
		assert.equal(fs.readFileSync(filePath, 'utf8'), before[key].content);
		const afterStats = fs.statSync(filePath);
		assert.equal(afterStats.size, before[key].stats.size);
		assert.equal(afterStats.mtimeMs, before[key].stats.mtimeMs);
	}
});

test('installed k6 no-HTTP serialization keeps the runtime token out of handleSummary', (t) => {
	const k6Bin = process.env.ISSUE195_K6_BIN || '/opt/homebrew/bin/k6';
	if (!fs.existsSync(k6Bin)) {
		t.skip(`installed k6 is unavailable at ${k6Bin}`);
		return;
	}
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-k6-no-http-'));
	try {
		fs.copyFileSync(files.scenario, path.join(directory, 'member-list-baseline.js'));
		fs.copyFileSync(files.contract, path.join(directory, 'scenario-contract.json'));
		fs.writeFileSync(path.join(directory, 'micro.js'), `
import { setup as scenarioSetup } from './member-list-baseline.js';
export function setup() { return scenarioSetup(); }
export default function () {}
export function handleSummary(data) {
	return { 'micro-summary.json': JSON.stringify(data) };
}
`);
		const sentinel = 'ISSUE195_SENTINEL_TOKEN_MUST_NOT_SERIALIZE';
		const contract = JSON.parse(read(files.contract));
		const result = spawnSync(k6Bin, [
			'run',
			'--quiet',
			'--vus', '1',
			'--iterations', '1',
			'-e', 'BASE_URL=http://127.0.0.1:28080',
			'-e', 'SCENARIO=admin_users',
			'-e', 'CASE=first_page',
			'-e', 'PERF_DATASET_ID=PERF_NO_HTTP_ONLY',
			'-e', 'PERF_FIXTURE_RUN_ID=ISSUE195_NO_HTTP_ONLY',
			'-e', 'PERF_EXECUTION_RUN_ID=EXEC195_NO_HTTP_ONLY',
			'-e', 'CAMPUS_ID=1',
			'-e', 'ISOLATION_CAMPUS_ID=2',
			'-e', 'ISOLATION_USER_ID=3',
			'-e', `EXPECTED_ACTIVE_MEMBERS=${contract.dataset.requiredActiveMembers}`,
			'-e', `EXPECTED_DUTY_ASSIGNMENTS=${contract.dataset.activeDutyAssignments}`,
			'-e', 'VUS=1',
			'-e', 'DURATION=1s',
			'-e', 'MAX_FAILURE_RATE=0',
			'-e', `PERF_ACCESS_TOKEN=${sentinel}`,
			'micro.js',
		], {
			cwd: directory,
			encoding: 'utf8',
			env: {
				PATH: process.env.PATH,
				HOME: directory,
				TMPDIR: process.env.TMPDIR || os.tmpdir(),
				LANG: 'C',
				LC_ALL: 'C',
			},
		});
		assert.equal(result.status, 0, result.stderr);
		const summaryPath = path.join(directory, 'micro-summary.json');
		const serialized = fs.readFileSync(summaryPath, 'utf8');
		assert.doesNotMatch(serialized, new RegExp(sentinel));
		const summary = JSON.parse(serialized);
		const httpRequests = summary.metrics?.http_reqs;
		const httpRequestValues = httpRequests?.values ?? httpRequests;
		assert.ok(!httpRequests || httpRequestValues?.count === 0, 'no-HTTP micro script must emit zero HTTP requests');
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('resource boundary validator binds exact case, identities, metrics, and event ordering', () => {
	assert.ok(fs.existsSync(files.resourceSnapshotValidator), 'resource snapshot validator is required');
	const identity = {
		app: {
			containerId: 'sha256:app-1',
			name: '/faithlog-app',
			imageId: 'sha256:app-image',
			startedAt: '2026-07-14T00:00:00Z',
			composeProject: 'faithlog',
			composeService: 'app',
			publishedPorts: { '8080/tcp': [{ HostIp: '127.0.0.1', HostPort: '18080' }] },
		},
		postgres: {
			containerId: 'sha256:postgres-1',
			name: '/faithlog-postgres',
			imageId: 'sha256:postgres-image',
			startedAt: '2026-07-14T00:00:00Z',
			composeProject: 'faithlog',
			composeService: 'postgres',
			publishedPorts: { '5432/tcp': [{ HostIp: '127.0.0.1', HostPort: '25432' }] },
		},
		redis: {
			containerId: 'sha256:redis-1',
			name: '/faithlog-redis',
			imageId: 'sha256:redis-image',
			startedAt: '2026-07-14T00:00:00Z',
			composeProject: 'faithlog',
			composeService: 'redis',
			publishedPorts: { '6379/tcp': [{ HostIp: '127.0.0.1', HostPort: '26379' }] },
		},
		database: {
			name: 'faithlog', serverAddress: '127.0.0.1', serverPort: 5432,
			postmasterStartedAt: '2026-07-14T00:00:00Z',
		},
	};
	const snapshot = (phase, capturedAt) => ({
		scenario: 'admin_users',
		case: 'first_page',
		phase,
		capturedAt,
		coverage: 'boundary-only',
		containers: [
			{ role: 'app', containerId: identity.app.containerId, name: identity.app.name, imageId: identity.app.imageId, startedAt: identity.app.startedAt, cpuPercent: 1.5, memoryBytes: 1_048_576 },
			{ role: 'postgres', containerId: identity.postgres.containerId, name: identity.postgres.name, imageId: identity.postgres.imageId, startedAt: identity.postgres.startedAt, cpuPercent: 0, memoryBytes: 2_097_152 },
			{ role: 'redis', containerId: identity.redis.containerId, name: identity.redis.name, imageId: identity.redis.imageId, startedAt: identity.redis.startedAt, cpuPercent: 0.2, memoryBytes: 524_288 },
		],
	});
	const snapshots = [
		snapshot('before', '2026-07-14T00:00:00.001Z'),
		snapshot('after', '2026-07-14T00:00:00.004Z'),
	];
	const windows = [
		{ scenario: 'admin_users', case: 'first_page', event: 'measured-start', status: 'pending', at: '2026-07-14T00:00:00.002Z' },
		{ scenario: 'admin_users', case: 'first_page', event: 'measured-end', status: 'passed', at: '2026-07-14T00:00:00.003Z' },
	];

	const runResourceValidator = (resourceValues, windowValues) => {
		const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-resource-'));
		try {
			const identityPath = path.join(tempDirectory, 'identity.json');
			const resourcePath = path.join(tempDirectory, 'resources.ndjson');
			const windowsPath = path.join(tempDirectory, 'windows.ndjson');
			fs.writeFileSync(identityPath, `${JSON.stringify(identity)}\n`);
			fs.writeFileSync(resourcePath, resourceValues.map(JSON.stringify).join('\n') + '\n');
			fs.writeFileSync(windowsPath, windowValues.map(JSON.stringify).join('\n') + '\n');
			return runJsonTool(files.resourceSnapshotValidator, [
				identityPath, resourcePath, windowsPath, 'admin_users', 'first_page', '1', path.join(tempDirectory, 'adoption.json'),
			]);
		} finally {
			fs.rmSync(tempDirectory, { recursive: true, force: true });
		}
	};

	assert.equal(runResourceValidator(snapshots, windows).status, 0);
	for (const mutate of [
		(values) => { values[1].scenario = 'campus_members'; },
		(values) => { values[1].containers[0].containerId = 'sha256:other-app'; },
		(values) => { values[0].containers[1].cpuPercent = '0'; },
		(values) => { values.pop(); },
	]) {
		const malformed = structuredClone(snapshots);
		mutate(malformed);
		assert.notEqual(runResourceValidator(malformed, windows).status, 0);
	}
	const reversedWindows = structuredClone(windows);
	reversedWindows[0].at = '2026-07-14T00:00:00.005Z';
	assert.notEqual(runResourceValidator(snapshots, reversedWindows).status, 0);
});

test('shared-stack evidence remains conditional-not-adoptable without user-approved exclusive provenance', () => {
	assert.ok(fs.existsSync(files.measurementClassifier), 'final measurement classifier is required');
	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-classification-'));
	try {
		const outputPath = path.join(tempDirectory, 'classification.json');
		const classification = runJsonTool(files.measurementClassifier, [outputPath]);
		assert.notEqual(classification.status, 0, 'scenario-only shared-stack evidence must never auto-adopt');
		assert.deepEqual(JSON.parse(fs.readFileSync(outputPath, 'utf8')), {
			status: 'conditional-not-adoptable',
			automaticAdoption: false,
			requiresUserApprovedExclusiveProvenance: true,
			reason: 'boundary evidence and a cooperative lock do not prove absence of transient external load',
		});
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
	const result = runFakeBaseline();
	assert.notEqual(result.status, 0);
	assert.match(result.stderr, /conditional-not-adoptable|exclusive provenance/i);
	assert.deepEqual(result.firstRejection, {
		schemaVersion: 1,
		status: 'non-adoptable',
		automaticAdoption: false,
		stage: 'final-classification',
		scenario: null,
		case: null,
		exitCode: 2,
	});
});

test('PostgreSQL cumulative counters remain lossless beyond Number.MAX_SAFE_INTEGER', () => {
	assert.match(read(files.dbCollector), /'calls'\s*,\s*calls::text/i);
	assert.match(read(files.dbCollector), /'rows'\s*,\s*rows::text/i);
	assert.match(read(files.dbRuntimeIntegrity), /'xactCommit'\s*,\s*database_stats\.xact_commit::text/i);
	assert.match(read(files.dbRuntimeIntegrity), /'xactRollback'\s*,\s*database_stats\.xact_rollback::text/i);
	const beforeCalls = '9007199254740992';
	const afterCalls = '9007199254740993';
	const queryResult = runQueryDelta(
		[pgQueryRow({ calls: beforeCalls, rows: beforeCalls, totalExecTime: 100 })],
		[pgQueryRow({ calls: afterCalls, rows: afterCalls, totalExecTime: 101 })],
	);
	assert.equal(queryResult.status, 0, queryResult.stderr);
	assert.equal(queryResult.output.observedCallDelta, '1');
	assert.equal(queryResult.output.deltas[0].calls, '1');
	assert.equal(queryResult.output.deltas[0].rows, '1');

	const { before, after, measured } = validDbIntegritySnapshots(10);
	before.databaseStats.xactCommit = '9007199254740992';
	before.databaseStats.xactRollback = '9007199254740992';
	after.databaseStats.xactCommit = '9007199254741013';
	after.databaseStats.xactRollback = '9007199254740992';
	withTempJsonFiles('faithlog-195-bigint-db-', [before, after, measured], (paths, directory) => {
		const outputPath = path.join(directory, 'adoption.json');
		const result = runDbIntegrity(paths, 'admin_users-first_page', outputPath);
		assert.equal(result.status, 0, result.stderr);
		const adoption = JSON.parse(fs.readFileSync(outputPath, 'utf8'));
		assert.equal(adoption.commitDelta, '21');
		assert.equal(adoption.rollbackDelta, '0');
	});
});

test('measurement requires a unique executionRunId and atomically refuses an existing report directory', () => {
	const runner = read(files.runner);
	assert.match(runner, /PERF_EXECUTION_RUN_ID=\$\{PERF_EXECUTION_RUN_ID:\?/);
	assert.match(runner, /REPORT_DIR=.*\$PERF_DATASET_ID\/\$PERF_FIXTURE_RUN_ID\/\$PERF_EXECUTION_RUN_ID/);
	assert.match(runner, /if ! mkdir "\$REPORT_DIR"/);
	assert.doesNotMatch(runner, /mkdir -p "\$REPORT_DIR"/);
	assert.match(runner, /executionRunId/);
	const result = runFakeBaseline();
	assert.notEqual(result.status, 0, 'an unused executionRunId must finish as conditional-not-adoptable');
	assert.notEqual(result.repeated.status, 0);
	assert.match(result.repeated.stderr, /fresh PERF_EXECUTION_RUN_ID/);
	assert.equal(result.trace.filter((line) => line.startsWith('login|')).length, 11);
});

test('measurement runner requires an absolute report root before Docker, lock, or report mutation', () => {
	for (const mode of ['missing', 'relative']) {
		const result = runRunnerReportRootPreflight(mode);
		assert.notEqual(result.status, 0, mode);
		assert.match(result.stderr, /PERF_REPORT_ROOT|absolute/i);
		assert.equal(result.trace.length, 0, `${mode} must not start node or Docker`);
		assert.equal(result.reportMutated, false, `${mode} must not mutate reports`);
	}
});

test('table counter validator enforces required expectations and exact before/after invariance', () => {
	const rows = [
		['users_total', 1001],
		['users_active', 1001],
		['users_dataset_name_match', 1000],
		['users_active_dataset_name_match', 1000],
		['users_active_dataset_name_email_match', 1000],
		['users_inactive_dataset_name_match', 0],
		['users_active_dataset_user', 1000],
		['fixture_campuses', 25],
		['primary_active_members', 1000],
		['primary_active_members_member', 999],
		['primary_active_members_minister', 1],
		['isolation_active_members', 2],
		['primary_active_duties', 101],
		['primary_active_duties_meal', 100],
		['primary_active_duties_coffee', 1],
	];
	const csv = rows.map(([metric, value]) => `${metric},${value}`).join('\n') + '\n';
	const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-table-counters-'));
	try {
		const beforePath = path.join(tempDirectory, 'before.csv');
		const afterPath = path.join(tempDirectory, 'after.csv');
		fs.writeFileSync(beforePath, csv);
		fs.writeFileSync(afterPath, csv);
		assert.equal(runJsonTool(files.tableCounterValidator, [beforePath, afterPath]).status, 0);
		fs.writeFileSync(afterPath, csv.replace('primary_active_members,1000', 'primary_active_members,999'));
		assert.notEqual(runJsonTool(files.tableCounterValidator, [beforePath, afterPath]).status, 0);
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
});

test('README keeps reports ignored and records scenario-ready/not-measured status', () => {
	const readme = read(files.readme);
	const gitignore = read(path.join(repositoryRoot, '.gitignore'));

	assert.match(gitignore, /performance\/k6\/issue-195\/reports\//);
	assert.match(readme, /scenario-ready\/not-measured/);
	assert.match(readme, /Docker.*실행하지 않/i);
	assert.match(readme, /다른 부하.*병렬.*금지/);
	assert.match(readme, /production.*변경.*금지/i);
	assert.doesNotMatch(readme, /BASE_URL=http:\/\/localhost:8080/);
	assert.match(readme, /EXEC195_BEFORE_20260716_A[\s\S]*non-reusable/);
	assert.match(readme, /warmup and measured HTTP request counts were both exactly `0`/i);
	assert.match(readme, /EXEC195_BEFORE_20260716_B[\s\S]*non-reusable/);
	assert.match(readme, /Measured HTTP requests were exactly 0/i);
	assert.match(readme, /EXEC195_BEFORE_20260716_C[\s\S]*non-reusable/);
	assert.match(readme, /530 HTTP requests/);
	assert.match(readme, /Measured HTTP requests were exactly 0/i);
	assert.match(readme, /EXEC195_BEFORE_20260716_D[\s\S]*non-reusable/);
	assert.match(readme, /7,197 requests/);
	assert.match(readme, /rejected diagnostic evidence only/);
	assert.match(readme, /EXEC195_BEFORE_20260716_E/);
	assert.equal(
		(readme.match(/BASE_URL=http:\/\/127\.0\.0\.1:28080/g) || []).length,
		3,
		'all three handoff examples must use the exact approved numeric-loopback target',
	);
});

test('installed k6 can inspect the scenario contract without treating JSON as a JavaScript module', (t) => {
	const k6Bin = process.env.ISSUE195_K6_BIN || '/opt/homebrew/bin/k6';
	if (!fs.existsSync(k6Bin)) {
		t.skip(`installed k6 is unavailable at ${k6Bin}`);
		return;
	}

	const tempHome = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-k6-inspect-'));
	try {
		const contract = JSON.parse(read(files.contract));
		const result = spawnSync(k6Bin, [
			'inspect',
			'-e', 'BASE_URL=http://127.0.0.1:28080',
			'-e', 'SCENARIO=admin_users',
			'-e', 'CASE=first_page',
			'-e', 'PERF_DATASET_ID=PERF_INSPECT_ONLY',
			'-e', 'PERF_FIXTURE_RUN_ID=ISSUE195_INSPECT_ONLY',
			'-e', 'PERF_EXECUTION_RUN_ID=EXEC195_INSPECT_ONLY',
			'-e', 'CAMPUS_ID=1',
			'-e', 'ISOLATION_CAMPUS_ID=2',
			'-e', 'ISOLATION_USER_ID=3',
			'-e', `EXPECTED_ACTIVE_MEMBERS=${contract.dataset.requiredActiveMembers}`,
			'-e', `EXPECTED_DUTY_ASSIGNMENTS=${contract.dataset.activeDutyAssignments}`,
			'-e', 'VUS=1',
			'-e', 'DURATION=1s',
			'-e', 'MAX_FAILURE_RATE=0',
			'-e', 'PERF_ACCESS_TOKEN=inspect-only-placeholder',
			files.scenario,
		], {
			cwd: repositoryRoot,
			encoding: 'utf8',
			env: {
				PATH: process.env.PATH,
				HOME: tempHome,
				TMPDIR: process.env.TMPDIR || os.tmpdir(),
				LANG: 'C',
				LC_ALL: 'C',
			},
		});
		assert.equal(
			result.status,
			0,
			`k6 inspect must initialize without HTTP execution\nstdout=${result.stdout}\nstderr=${result.stderr}`,
		);
	} finally {
		fs.rmSync(tempHome, { recursive: true, force: true });
	}
});

test('common integrity audit - source, target image, credential, and workload inputs have no fallback', () => {
	const contract = JSON.parse(read(files.contract));
	const runner = read(files.runner);
	const fixture = read(files.fixture);
	const scenario = read(files.scenario);
	const originDevelop = spawnSync('git', ['rev-parse', 'origin/develop'], {
		cwd: repositoryRoot,
		encoding: 'utf8',
	});
	assert.equal(originDevelop.status, 0, originDevelop.stderr);
	assert.deepEqual(contract.sourceIdentity, {
		originDevelopCommit: originDevelop.stdout.trim(),
		flywayBoundary: 'V11__secure_supabase_data_api.sql',
		apiSources: [
			'src/main/java/com/faithlog/admin/controller/AdminManagementController.java',
			'src/main/java/com/faithlog/campus/controller/AdminCampusController.java',
		],
	});
	for (const name of [
		'PERF_SOURCE_COMMIT',
		'EXPECTED_APP_IMAGE_ID',
		'EXPECTED_POSTGRES_IMAGE_ID',
		'REDIS_CONTAINER_ID',
		'EXPECTED_REDIS_COMPOSE_SERVICE',
		'EXPECTED_REDIS_IMAGE_ID',
		'EXPECTED_ACTIVE_MEMBERS',
		'EXPECTED_DUTY_ASSIGNMENTS',
		'RESOURCE_BOUNDARY_MAX_GAP_SECONDS',
		'K6_BIN',
	]) {
		assert.match(runner, new RegExp(`\\$\\{${name}:\\?`), `${name} must be runtime-required`);
	}
	assert.match(runner, /PERF_SOURCE_COMMIT.*sourceIdentity\.originDevelopCommit/s);
	assert.match(runner, /EXPECTED_ACTIVE_MEMBERS.*requiredActiveMembers/s);
	assert.match(runner, /EXPECTED_DUTY_ASSIGNMENTS.*activeDutyAssignments/s);
	assert.ok(
		runner.indexOf('export -n PERF_ADMIN_EMAIL') < runner.indexOf('CURRENT_STAGE=source-contract'),
		'API/DB credentials must be unexported before the source-contract Node child starts',
	);
	assert.doesNotMatch(scenario, /EXPECTED_ACTIVE_MEMBERS\s*\|\|/);
	assert.doesNotMatch(scenario, /EXPECTED_DUTY_ASSIGNMENTS\s*\|\|/);
	for (const name of [
		'EXPECTED_APP_IMAGE_ID',
		'REDIS_CONTAINER_ID',
		'EXPECTED_REDIS_COMPOSE_SERVICE',
		'EXPECTED_REDIS_IMAGE_ID',
	]) {
		assert.match(fixture, new RegExp(`process\\.env\\.${name}`), `fixture must require ${name}`);
	}
	assert.match(fixture, /post-lock.*identity.*before.*login/is);
});

test('common integrity audit - Redis joins immutable pre/post/final runtime continuity', () => {
	const runner = read(files.runner);
	assert.match(runner, /REDIS_CONTAINER_ID/);
	assert.match(runner, /redis-compose-labels\.json/);
	assert.match(runner, /redis:\{containerId:/);
	assert.match(runner, /final-runtime-continuity-adoption\.json/);

	const identity = {
		app: {
			containerId: 'sha256:app-container', name: '/faithlog-app', imageId: 'sha256:app-image',
			startedAt: '2026-07-16T00:00:00Z', composeProject: 'faithlog', composeService: 'app',
			publishedPorts: { '8080/tcp': [{ HostIp: '127.0.0.1', HostPort: '18080' }] },
		},
		postgres: {
			containerId: 'sha256:postgres-container', name: '/faithlog-postgres', imageId: 'sha256:postgres-image',
			startedAt: '2026-07-16T00:00:00Z', composeProject: 'faithlog', composeService: 'postgres',
			publishedPorts: { '5432/tcp': [{ HostIp: '127.0.0.1', HostPort: '25432' }] },
		},
		redis: {
			containerId: 'sha256:redis-container', name: '/faithlog-redis', imageId: 'sha256:redis-image',
			startedAt: '2026-07-16T00:00:00Z', composeProject: 'faithlog', composeService: 'redis',
			publishedPorts: { '6379/tcp': [{ HostIp: '127.0.0.1', HostPort: '26379' }] },
		},
		database: {
			name: 'faithlog', serverAddress: '127.0.0.1', serverPort: 5432,
			postmasterStartedAt: '2026-07-16T00:00:00Z',
		},
	};
	withTempJsonFiles('faithlog-195-redis-continuity-', [identity, structuredClone(identity)], (paths, directory) => {
		const outputPath = path.join(directory, 'adoption.json');
		const valid = runJsonTool(files.runtimeContinuityValidator, [...paths, outputPath]);
		assert.equal(valid.status, 0, valid.stderr);
		assert.deepEqual(JSON.parse(fs.readFileSync(outputPath, 'utf8')).identityFields.redis, [
			'containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService', 'publishedPorts',
		]);
	});
	const replaced = structuredClone(identity);
	replaced.redis.imageId = 'sha256:redis-image-replaced';
	withTempJsonFiles('faithlog-195-redis-replacement-', [identity, replaced], (paths, directory) => {
		assert.notEqual(runJsonTool(
			files.runtimeContinuityValidator,
			[...paths, path.join(directory, 'adoption.json')],
		).status, 0);
	});
});

test('common integrity audit - Counter and Rate observations agree for direct and values summaries', () => {
	const metricName = 'issue195_admin_users_first_page';
	const validValues = {
		[`${metricName}_duration`]: { 'p(50)': 1, 'p(95)': 2, 'p(99)': 3, max: 4 },
		[`${metricName}_requests`]: { count: 10, rate: 2 },
		[`${metricName}_failures`]: { value: 0, passes: 0, fails: 10 },
	};
	for (const shape of ['direct', 'values']) {
		const shapeMetrics = (metrics) => shape === 'values'
			? Object.fromEntries(Object.entries(metrics).map(([key, values]) => [key, { values }]))
			: metrics;
		withTempJsonFiles(`faithlog-195-rate-valid-${shape}-`, [{ metrics: shapeMetrics(validValues) }], ([summaryPath], directory) => {
			assert.equal(runJsonTool(files.summaryValidator, [
				summaryPath, 'admin_users', 'first_page', 'measured', path.join(directory, 'normalized.json'),
			]).status, 0);
		});
		for (const [label, mutate] of [
			['observation-count-mismatch', (values) => { values[`${metricName}_failures`].fails = 9; }],
			['hidden-failure', (values) => {
				values[`${metricName}_failures`].value = 0.1;
				values[`${metricName}_failures`].passes = 1;
				values[`${metricName}_failures`].fails = 9;
			}],
		]) {
			const malformed = structuredClone(validValues);
			mutate(malformed);
			withTempJsonFiles(`faithlog-195-rate-${label}-${shape}-`, [{ metrics: shapeMetrics(malformed) }], ([summaryPath], directory) => {
				assert.notEqual(runJsonTool(files.summaryValidator, [
					summaryPath, 'admin_users', 'first_page', 'measured', path.join(directory, 'normalized.json'),
				]).status, 0, `${label}/${shape}`);
			});
		}
	}
});

test('common integrity audit - resource snapshots bind full three-container identity and boundary cadence', () => {
	assert.match(read(files.resourceSnapshotCapture), /container\('redis', 'REDIS'\)/);
	const identity = {
		app: { containerId: 'sha256:app', name: '/app', imageId: 'sha256:app-image', startedAt: '2026-07-16T00:00:00Z' },
		postgres: { containerId: 'sha256:postgres', name: '/postgres', imageId: 'sha256:postgres-image', startedAt: '2026-07-16T00:00:00Z' },
		redis: { containerId: 'sha256:redis', name: '/redis', imageId: 'sha256:redis-image', startedAt: '2026-07-16T00:00:00Z' },
	};
	const container = (role, cpuPercent, memoryBytes) => ({
		role,
		containerId: identity[role].containerId,
		name: identity[role].name,
		imageId: identity[role].imageId,
		startedAt: identity[role].startedAt,
		cpuPercent,
		memoryBytes,
	});
	const snapshot = (phase, capturedAt) => ({
		scenario: 'admin_users', case: 'first_page', phase, capturedAt, coverage: 'boundary-only',
		containers: [container('app', 1.5, 1_048_576), container('postgres', 0, 2_097_152), container('redis', 0.2, 524_288)],
	});
	const windows = [
		{ scenario: 'admin_users', case: 'first_page', event: 'measured-start', status: 'pending', at: '2026-07-16T00:00:02.000Z' },
		{ scenario: 'admin_users', case: 'first_page', event: 'measured-end', status: 'passed', at: '2026-07-16T00:00:03.000Z' },
	];
	const run = (snapshots) => {
		const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-resource-audit-'));
		try {
			const identityPath = path.join(directory, 'identity.json');
			const resourcePath = path.join(directory, 'resource.ndjson');
			const windowsPath = path.join(directory, 'windows.ndjson');
			fs.writeFileSync(identityPath, `${JSON.stringify(identity)}\n`);
			fs.writeFileSync(resourcePath, `${snapshots.map(JSON.stringify).join('\n')}\n`);
			fs.writeFileSync(windowsPath, `${windows.map(JSON.stringify).join('\n')}\n`);
			return runJsonTool(files.resourceSnapshotValidator, [
				identityPath, resourcePath, windowsPath, 'admin_users', 'first_page', '1', path.join(directory, 'adoption.json'),
			]);
		} finally {
			fs.rmSync(directory, { recursive: true, force: true });
		}
	};
	assert.equal(run([
		snapshot('before', '2026-07-16T00:00:01.500Z'),
		snapshot('after', '2026-07-16T00:00:03.500Z'),
	]).status, 0);
	const mixedIdentity = [
		snapshot('before', '2026-07-16T00:00:01.500Z'),
		snapshot('after', '2026-07-16T00:00:03.500Z'),
	];
	mixedIdentity[1].containers[2].imageId = 'sha256:other-redis-image';
	assert.notEqual(run(mixedIdentity).status, 0, 'every resource role must match the full immutable identity');
	assert.notEqual(run([
		snapshot('before', '2026-07-16T00:00:00.000Z'),
		snapshot('after', '2026-07-16T00:00:03.500Z'),
	]).status, 0, 'before-to-start cadence beyond the runtime-approved window must fail closed');
});

test('common integrity audit - first machine-readable rejection is atomic and never overwritten', () => {
	assert.ok(fs.existsSync(files.firstRejectionRecorder), 'first rejection recorder is required');
	const runner = read(files.runner);
	assert.match(runner, /first-rejection\.json/);
	assert.match(runner, /record-first-rejection\.mjs/);
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-195-first-rejection-'));
	try {
		const outputPath = path.join(directory, 'first-rejection.json');
		assert.equal(runJsonTool(files.firstRejectionRecorder, [
			outputPath, 'measured-summary', 'admin_users', 'first_page', '7',
		]).status, 0);
		assert.equal(runJsonTool(files.firstRejectionRecorder, [
			outputPath, 'later-cleanup', 'duty_assignments', 'full_list', '9',
		]).status, 0);
		assert.deepEqual(JSON.parse(fs.readFileSync(outputPath, 'utf8')), {
			schemaVersion: 1,
			status: 'non-adoptable',
			automaticAdoption: false,
			stage: 'measured-summary',
			scenario: 'admin_users',
			case: 'first_page',
			exitCode: 7,
		});
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('common integrity audit - fixture mutation binds PostgreSQL with app and Redis across lock boundaries', () => {
	const fixture = read(files.fixture);
	for (const name of [
		'POSTGRES_CONTAINER_ID',
		'EXPECTED_POSTGRES_COMPOSE_SERVICE',
		'EXPECTED_POSTGRES_IMAGE_ID',
	]) {
		assert.match(fixture, new RegExp(`process\\.env\\.${name}`), `fixture must require ${name}`);
	}
	assert.match(fixture, /postgres:\s*captureContainer\(POSTGRES_CONTAINER_ID\)/);
	assert.match(fixture, /App, PostgreSQL, and Redis Compose projects must match/);
});

test('common integrity audit - pre-lock rejection is preserved before login, DB evidence, or k6', () => {
	const result = runFakeBaseline({ preflightSourceMismatch: true });
	assert.notEqual(result.status, 0);
	assert.deepEqual(result.trace.filter((line) => /^(login|collector|k6)\|/.test(line)), []);
	assert.deepEqual(result.firstRejection, {
		schemaVersion: 1,
		status: 'non-adoptable',
		automaticAdoption: false,
		stage: 'source-contract',
		scenario: null,
		case: null,
		exitCode: 1,
	});
});
