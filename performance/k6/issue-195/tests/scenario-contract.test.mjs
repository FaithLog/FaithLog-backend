import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
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
	runtimeLogin: path.join(issueRoot, 'runtime-login.mjs'),
	targetIdentity: path.join(issueRoot, 'validate-target-identity.mjs'),
	approvedRuntimeTarget: path.join(issueRoot, 'validate-approved-runtime-target.mjs'),
	tokenLifetime: path.join(issueRoot, 'validate-token-lifetime.mjs'),
	summaryValidator: path.join(issueRoot, 'validate-k6-summary.mjs'),
	dbIntegrityValidator: path.join(issueRoot, 'validate-db-integrity.mjs'),
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
		files.runtimeContinuityValidator,
		files.approvedRuntimeTarget,
		files.resourceSnapshotValidator,
		files.resourceSnapshotCapture,
		files.measurementClassifier,
		files.firstRejectionRecorder,
	]) {
		fs.copyFileSync(validator, path.join(tempDirectory, path.basename(validator)));
	}

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
if [[ "\${1-}" == *validate-db-integrity.mjs || "\${1-}" == *validate-table-counters.mjs ]]; then
	exit 0
fi
exec "$REAL_NODE" "$@"
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
printf '{"metrics":{"%s_duration":{"p(50)":1,"p(95)":2,"p(99)":3,"max":4},"%s_requests":{"count":10,"rate":2},"%s_failures":{"rate":0,"passes":10,"fails":0}}}\\n' \\
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

function runDbIntegrity(paths, expectedEvidenceCase = 'admin_users-first_page') {
	return runJsonTool(files.dbIntegrityValidator, [...paths, '', expectedEvidenceCase]);
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
	assert.match(source, /com\.docker\.compose\.project/);
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
if [[ "$*" == *'{{.Id}}'* ]]; then printf 'sha256:%s-container\\n' "\${*: -1}"
elif [[ "$*" == *'{{.Name}}'* ]]; then printf '/%s\\n' "\${*: -1}"
elif [[ "$*" == *'{{.Image}}'* ]]; then printf 'sha256:%s-image\\n' "\${*: -1}"
elif [[ "$*" == *State.StartedAt* ]]; then printf '%s\\n' '2026-07-16T00:00:00Z'
elif [[ "$*" == *com.docker.compose.project* ]]; then printf '%s\\n' fixture-project
elif [[ "$*" == *com.docker.compose.service* ]]; then
	if [[ "\${*: -1}" == fake-redis ]]; then printf '%s\\n' redis; else printf '%s\\n' app; fi
else printf '%s\\n' '{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"18080"}]}'
fi
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
				EXPECTED_APP_IMAGE_ID: 'sha256:fake-app-image',
				REDIS_CONTAINER_ID: 'fake-redis',
				EXPECTED_REDIS_COMPOSE_SERVICE: 'redis',
				EXPECTED_REDIS_IMAGE_ID: 'sha256:fake-redis-image',
				EXPECTED_ACTIVE_MEMBERS: '1000',
				EXPECTED_DUTY_ASSIGNMENTS: '101',
				PERF_REPORT_ROOT: path.join(tempDirectory, 'reports'),
			},
		});
		assert.notEqual(fixtureResult.status, 0);
		assert.match(fixtureResult.stderr, /must match exactly one published port/);
		assert.equal(fs.existsSync(path.join(tempDirectory, 'reports')), false, 'port mismatch must precede every API mutation/report');
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
});

test('token lifetime validator fails closed against a fake clock before a long case starts', () => {
	const now = 2_000_000_000;
	const environment = {
		PERF_CONTRACT_TEST: '1',
		TOKEN_CLOCK_EPOCH_SECONDS: String(now),
		WARMUP_DURATION: '20m',
		MEASURED_DURATION: '9m',
		TOKEN_SAFETY_MARGIN_SECONDS: '60',
	};
	const valid = spawnSync(process.execPath, [files.tokenLifetime], {
		encoding: 'utf8',
		input: unsignedJwt(now + 1800),
		env: { ...process.env, ...environment },
	});
	assert.equal(valid.status, 0, valid.stderr);
	const shortLived = spawnSync(process.execPath, [files.tokenLifetime], {
		encoding: 'utf8',
		input: unsignedJwt(now + 1799),
		env: { ...process.env, ...environment },
	});
	assert.notEqual(shortLived.status, 0);
	assert.match(shortLived.stderr, /remaining lifetime/i);
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
		[`${metricName}_failures`]: { rate: 0, passes: 10, fails: 0 },
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
		const result = runJsonTool(files.dbIntegrityValidator, [
			...paths, outputPath, 'admin_users-first_page',
		]);
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
		[`${metricName}_failures`]: { rate: 0, passes: 10, fails: 0 },
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
			['observation-count-mismatch', (values) => { values[`${metricName}_failures`].passes = 9; }],
			['hidden-failure', (values) => {
				values[`${metricName}_failures`].passes = 9;
				values[`${metricName}_failures`].fails = 1;
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
