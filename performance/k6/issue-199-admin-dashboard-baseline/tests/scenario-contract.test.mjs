import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath, pathToFileURL} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const issueRoot = path.resolve(here, '..');
const repositoryRoot = path.resolve(issueRoot, '../../..');

const files = {
	contract: path.join(issueRoot, 'scenario-contract.json'),
	currentDevelopContract: path.join(issueRoot, 'current-develop-contract.json'),
	currentDevelopVerifier: path.join(issueRoot, 'verify-current-develop-contract.mjs'),
	manifestExample: path.join(issueRoot, 'input-manifest.example.json'),
	scenario: path.join(issueRoot, 'admin-dashboard-baseline.js'),
	runner: path.join(issueRoot, 'run-baseline.sh'),
	dbEvidence: path.join(issueRoot, 'collect-db-evidence.sql'),
	verifier: path.join(issueRoot, 'verify-summary.mjs'),
	runtimeToken: path.join(issueRoot, 'prepare-runtime-token.mjs'),
	runtimeTargetValidator: path.join(issueRoot, 'validate-runtime-target.mjs'),
	tokenLifetimeValidator: path.join(issueRoot, 'validate-token-lifetime.mjs'),
	summaryValidator: path.join(issueRoot, 'validate-k6-summary.mjs'),
	dbCorrectnessValidator: path.join(issueRoot, 'validate-db-correctness.mjs'),
	dbWindowValidator: path.join(issueRoot, 'validate-db-window.mjs'),
	runtimeContinuityValidator: path.join(issueRoot, 'validate-runtime-continuity.mjs'),
	dockerResourceValidator: path.join(issueRoot, 'validate-docker-resources.mjs'),
	dbCounters: path.join(issueRoot, 'collect-db-counters.sql'),
	runtimeIdentity: path.join(issueRoot, 'collect-runtime-identity.sql'),
	runInputValidator: path.join(issueRoot, 'validate-run-input.mjs'),
	redisIdentityParser: path.join(issueRoot, 'parse-redis-server-identity.mjs'),
	prePostLockValidator: path.join(issueRoot, 'validate-pre-post-lock-target.mjs'),
	appConnectionValidator: path.join(issueRoot, 'validate-app-runtime-connections.mjs'),
	sourceImageProvenance: path.join(issueRoot, 'validate-source-image-provenance.mjs'),
	dbCorrectness: path.join(issueRoot, 'collect-correctness-evidence.sql'),
	readme: path.join(issueRoot, 'README.md'),
	reportsIgnore: path.join(issueRoot, 'reports/.gitignore'),
};

const read = (file) => fs.readFileSync(file, 'utf8');

test('current develop contract pins dashboard, Flyway, RLS JDBC, and applicability boundaries', () => {
	const current = JSON.parse(read(files.currentDevelopContract));
	assert.equal(current.issue, 199);
	assert.equal(current.baseCommit, '6796ed146244d8f3f5b5dd7048ebe16865084a97');
	assert.deepEqual(current.dashboard.queryParameters, ['weekStartDate']);
	assert.deepEqual(current.dashboard.deniedDutyOnlyActors, ['COFFEE', 'MEAL']);
	assert.equal(current.dashboard.chargeScope, 'all-age-UNPAID-PENALTY-COFFEE');
	assert.equal(current.dashboard.paginationArchive, 'not-applicable');
	assert.deepEqual(current.dashboard.stableOrdering, ['PENALTY', 'COFFEE']);
	assert.deepEqual(current.rlsJdbcBoundary, {
		dataApiRoles: 'deny-all',
		applicationPath: 'direct-owner-jdbc',
		forceRowLevelSecurity: false,
	});
	const migrations = fs.readdirSync(path.join(repositoryRoot, 'src/main/resources/db/migration'))
		.filter((name) => name.endsWith('.sql')).sort();
	assert.deepEqual(Object.keys(current.flywayMigrations).sort(), migrations);
	for (const [name, expectedHash] of Object.entries(current.flywayMigrations)) {
		const source = read(path.join(repositoryRoot, 'src/main/resources/db/migration', name));
		assert.equal(createHash('sha256').update(source).digest('hex'), expectedHash, `${name} identity drifted`);
	}
	assert.equal(runNode(files.currentDevelopVerifier, files.currentDevelopContract).status, 0);
});

test('inventory matches the production dashboard endpoint, optional query, response, and authorization contract', () => {
	const controller = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/admin/controller/AdminDashboardController.java',
	));
	const response = read(path.join(
		repositoryRoot,
		'src/main/java/com/faithlog/admin/controller/dto/response/AdminDashboardSummaryResponse.java',
	));
	const contract = JSON.parse(read(files.contract));

	assert.match(controller, /@RequestMapping\("\/api\/v1\/admin\/campuses\/\{campusId\}\/dashboard"\)/);
	assert.match(controller, /@GetMapping\("\/summary"\)/);
	assert.match(controller, /@RequestParam\(required = false\) LocalDate weekStartDate/);
	assert.equal(contract.endpoint.method, 'GET');
	assert.equal(contract.endpoint.path, '/api/v1/admin/campuses/{campusId}/dashboard/summary');
	assert.deepEqual(contract.endpoint.queryParameters, [{name: 'weekStartDate', required: false, format: 'YYYY-MM-DD Monday'}]);
	assert.deepEqual(contract.authorization.allowed, ['service-admin', 'active-campus-minister', 'active-campus-elder', 'active-campus-leader']);
	assert.deepEqual(contract.authorization.denied, [
		'plain-member', 'other-campus-admin', 'global-manager-only',
		'active COFFEE duty-only member', 'active MEAL duty-only member',
	]);
	assert.equal(contract.authorization.deniedCode, 'ADMIN_DASHBOARD_ACCESS_FORBIDDEN');

	for (const field of [
		'activeCount', 'inactiveCount', 'adminCount',
		'submittedCount', 'missingCount', 'submitRate',
		'unpaidAmount', 'unpaidMemberCount', 'paymentCategory',
		'openCount', 'recentlyClosedCount', 'missingResponseCount', 'recentlyClosedDays',
	]) {
		assert.match(response, new RegExp(`\\b${field}\\b`));
	}
});

test('manifest separates empty, small, and 1000-member modes and references shared domain fixtures without seeding', () => {
	const contract = JSON.parse(read(files.contract));
	const manifest = JSON.parse(read(files.manifestExample));

	assert.equal(contract.issue, 199);
	assert.equal(contract.status, 'scenario-ready/not-measured');
	assert.equal(contract.baselineAdoptionStatus, 'conditional-not-adoptable');
	assert.deepEqual(contract.dataset.modes, ['empty', 'small', 'thousand']);
	assert.deepEqual(contract.dataset.identifiers, ['datasetId', 'fixtureRunId']);
	assert.equal(contract.dataset.thousandMemberCount, 1000);
	assert.deepEqual(contract.dataset.fixtureDomains, ['devotion', 'penalty', 'coffee', 'meal', 'poll', 'prayer']);
	assert.equal(contract.dataset.seedPolicy, 'reference-only');

	assert.equal(manifest.issue, 199);
	assert.equal(manifest.datasetId, 'PERFORMANCE_SHARED_1000_EXAMPLE');
	assert.equal(manifest.schemaVersion, 2);
	assert.equal(manifest.currentDevelopBase, '6796ed146244d8f3f5b5dd7048ebe16865084a97');
	assert.equal(manifest.fixtureNamespace.immutable, true);
	for (const component of ['app', 'postgres', 'redis']) {
		assert.equal(typeof manifest.runtimeTarget[component].service, 'string');
		assert.equal(typeof manifest.runtimeTarget[component].containerPort, 'number');
		assert.equal(typeof manifest.runtimeTarget[component].imageId, 'string');
		assert.equal(typeof manifest.runtimeTarget[component].imageRef, 'string');
	}
	for (const mode of contract.dataset.modes) {
		assert.ok(manifest.modes[mode], `missing dataset mode: ${mode}`);
		assert.equal(manifest.modes[mode].mode, mode);
		assert.equal(typeof manifest.modes[mode].fixtureRunId, 'string');
	}
	assert.equal(manifest.modes.thousand.expected.members.activeCount, 1000);
	for (const domain of contract.dataset.fixtureDomains) {
		const reference = manifest.modes.thousand.fixtureReferences[domain];
		assert.equal(typeof reference.fixtureRunId, 'string', `${domain} fixtureRunId is required`);
		assert.equal(typeof reference.manifestPath, 'string', `${domain} manifestPath is required`);
	}
});

test('scenario reproduces frontend session entry order before isolated summary measurement', () => {
	const source = read(files.scenario);
	const bootstrap = read(files.runtimeToken);
	const login = bootstrap.indexOf('/api/v1/auth/login');
	const currentUser = bootstrap.indexOf('/api/v1/users/me');
	const campuses = bootstrap.indexOf('/api/v1/campuses/me');
	const summary = source.indexOf("name: 'admin_dashboard_summary'");

	assert.ok(login >= 0);
	assert.ok(currentUser > login);
	assert.ok(campuses > login);
	assert.ok(summary >= 0);
	assert.match(bootstrap, /Promise\.all\(/);
	assert.match(bootstrap, /\/api\/v1\/users\/me/);
	assert.match(bootstrap, /\/api\/v1\/campuses\/me/);
	assert.match(source, /PERF_ACCESS_TOKEN/);
	assert.doesNotMatch(source, /\/api\/v1\/auth\/login|\/api\/v1\/users\/me|\/api\/v1\/campuses\/me/);
	assert.match(source, /\/api\/v1\/admin\/campuses\/\$\{dataset\.campusId\}\/dashboard\/summary\?weekStartDate=/);
	assert.doesNotMatch(source, /\/members|duty-assignments|prayers\/weeks/);
});

test('scenario exposes required latency, throughput, failure, and exact correctness checks', () => {
	const source = read(files.scenario);
	const verifier = read(files.verifier);
	const contract = JSON.parse(read(files.contract));

	assert.deepEqual(contract.metrics, ['p50', 'p95', 'p99', 'max', 'throughput', 'failureRate']);
	assert.match(source, /new Trend\('admin_dashboard_duration'/);
	assert.match(source, /new Counter\('admin_dashboard_requests'/);
	assert.match(source, /new Rate\('admin_dashboard_failure_rate'/);
	assert.match(source, /summaryTrendStats:\s*\['p\(50\)', 'p\(95\)', 'p\(99\)', 'max'\]/);
	assert.match(source, /admin_dashboard_failure_rate:\s*\['rate==0'\]/);
	assert.doesNotMatch(source, /admin_dashboard_duration:\s*\[/);
	for (const invariant of [
		'members.activeCount', 'members.inactiveCount', 'members.adminCount',
		'devotion.submittedCount', 'devotion.missingCount', 'devotion.submitRate',
		'charges.unpaidAmount', 'charges.unpaidMemberCount', 'charges.byCategory',
		'polls.openCount', 'polls.recentlyClosedCount', 'polls.missingResponseCount',
	]) {
		assert.match(source, new RegExp(invariant.replace('.', '\\.')));
	}
	assert.match(source, /PENALTY/);
	assert.match(source, /COFFEE/);
	assert.match(verifier, /ADMIN_DASHBOARD_ACCESS_FORBIDDEN/);
	assert.match(source, /fail\(/);
	assert.match(source, /parsed !== null.*typeof parsed === 'object'.*!Array\.isArray\(parsed\)/s);
});

test('runner separates warmup and measured phases, serializes modes, records actual Compose labels and resources', () => {
	const source = read(files.runner);
	const tokenSource = read(files.runtimeToken);

	assert.match(source, /BASE_URL="\$\{BASE_URL:\?/);
	assert.match(source, /DATASET_MODES="\$\{DATASET_MODES:\?/);
	assert.match(source, /APP_CONTAINER="\$\{APP_CONTAINER:\?/);
	assert.match(source, /POSTGRES_CONTAINER="\$\{POSTGRES_CONTAINER:\?/);
	assert.match(source, /REDIS_CONTAINER="\$\{REDIS_CONTAINER:\?/);
	assert.doesNotMatch(source, /POSTGRES_CONTAINER="\$\{POSTGRES_CONTAINER:-/);
	assert.doesNotMatch(source, /REDIS_CONTAINER="\$\{REDIS_CONTAINER:-/);
	assert.doesNotMatch(tokenSource, /BASE_URL\s*=.*\|\|/);
	assert.doesNotMatch(tokenSource, /DATASET_MODES\s*=.*\|\|/);
	assert.match(source, /\/tmp\/faithlog-performance-\$\{COMPOSE_PROJECT\}\.lock/);
	assert.match(source, /APP_PROJECT.*POSTGRES_PROJECT.*REDIS_PROJECT/s);
	assert.match(source, /Compose project labels do not match/);
	assert.match(source, /mkdir "\$LOCK_DIR"/);
	assert.match(source, /trap cleanup EXIT/);
	assert.match(source, /rmdir "\$LOCK_DIR"/);
	assert.match(source, /validate-runtime-target\.mjs/);
	assert.match(source, /validate-token-lifetime\.mjs/);
	assert.match(source, /validate-db-window\.mjs/);
	assert.match(source, /validate-runtime-continuity\.mjs/);
	assert.match(source, /validate-docker-resources\.mjs/);
	assert.match(source, /Report directory already exists/);
	assert.match(source, /PHASE=warmup/);
	assert.match(source, /PHASE=measured/);
	assert.ok(source.indexOf('PHASE=warmup') < source.indexOf('PHASE=measured'));
	assert.match(source, /warmup\/summary\.json/);
	assert.match(source, /measured\/summary\.json/);
	assert.match(source, /docker inspect/);
	assert.match(source, /com\.docker\.compose\.project/);
	assert.match(source, /com\.docker\.compose\.service/);
	assert.match(source, /docker stats --no-stream --no-trunc/);
	assert.match(source, /EXTERNAL_ACTIVITY/);
	assert.doesNotMatch(source, /-e PGPASSWORD="\$PERF_DB_PASSWORD"/);
	assert.match(source, /PGPASSWORD="\$PERF_DB_PASSWORD"\s+docker exec[\s\S]*-e PGPASSWORD/);
	assert.doesNotMatch(source, /docker compose .*\b(?:up|down|build|restart|rm)\b/);
	assert.doesNotMatch(source, /docker (?:builder |system |image |volume )?prune/);
	assert.doesNotMatch(source, /&\s*$/m);
});

test('DB evidence is read-only and captures counters, query evidence, analyze and planner state', () => {
	const contextSql = read(files.dbEvidence);
	const counterSql = read(files.dbCounters);
	const correctnessSql = read(files.dbCorrectness);
	const runtimeIdentitySql = read(files.runtimeIdentity);
	const sql = `${contextSql}\n${counterSql}\n${correctnessSql}\n${runtimeIdentitySql}`;

	for (const table of [
		'users', 'campuses', 'campus_members', 'weekly_devotion_records', 'charge_items',
		'polls', 'poll_responses', 'devotion_daily_checks', 'payment_accounts',
		'meal_poll_settlements', 'prayer_submissions',
	]) {
		assert.match(sql, new RegExp(`\\b${table}\\b`));
	}
	assert.match(sql, /pg_stat_database/);
	assert.match(sql, /pg_stat_user_tables/);
	assert.match(sql, /pg_stat_statements/);
	assert.match(sql, /last_analyze/);
	assert.match(sql, /last_autoanalyze/);
	assert.match(sql, /last_vacuum/);
	assert.match(sql, /last_autovacuum/);
	assert.match(sql, /vacuum_count/);
	assert.match(sql, /autovacuum_count/);
	assert.match(sql, /n_mod_since_analyze/);
	assert.match(sql, /pg_settings/);
	assert.match(sql, /poll_response_count/);
	assert.match(sql, /missing_response_count/);
	assert.doesNotMatch(sql, /\b(?:INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE|VACUUM|ANALYZE)\b/i);
	assert.match(counterSql, /issue199:evidence=counters/);
	assert.match(counterSql, /pg_stat_activity/);
	assert.match(counterSql, /application_name/);
	assert.doesNotMatch(counterSql, /application_name\s+IS\s+DISTINCT\s+FROM/i);
	assert.match(counterSql, /boundary-snapshot-only/);
	assert.match(counterSql, /stats_reset/);
	assert.match(counterSql, /plannerSettings/);
	assert.match(counterSql, /plannerContext/);
	assert.doesNotMatch(
		counterSql,
		/\b(?:FROM|JOIN)\s+(?:users|campuses|campus_members|weekly_devotion_records|charge_items|polls|poll_responses)\b/i,
	);
	assert.match(correctnessSql, /issue199:evidence=correctness/);
	assert.match(runtimeIdentitySql, /issue199:evidence=runtime-identity/);
	assert.match(runtimeIdentitySql, /pg_postmaster_start_time/);
});

test('counter evidence reports database-wide observer overhead separately from application-table counters', () => {
	const counterSql = read(files.dbCounters);
	const readme = read(files.readme);

	assert.match(counterSql, /databaseWideCountersIncludeSnapshotTransaction/);
	assert.match(counterSql, /databaseWideDeltaIsExactQueryCount/);
	assert.match(counterSql, /appTableCountersReadApplicationTables/);
	assert.match(readme, /pg_stat_database.*observer.*overhead/is);
	assert.match(readme, /exact query count.*해석하지/is);
	assert.match(readme, /pg_stat_user_tables.*app-table/is);
});

test('verifier enforces summary totals, status/category basis, poll response counts and campus isolation', () => {
	const source = read(files.verifier);
	const tokenSource = read(files.runtimeToken);
	const dbValidator = read(files.dbCorrectnessValidator);

	assert.match(tokenSource, /process\.env\.PERF_ADMIN_EMAIL/);
	assert.match(tokenSource, /process\.env\.PERF_ADMIN_PASSWORD/);
	assert.match(source, /process\.env\.PERF_ACCESS_TOKEN/);
	assert.match(source, /process\.env\.INPUT_MANIFEST/);
	assert.match(source, /statusBasis/);
	assert.match(source, /UNPAID/);
	assert.match(source, /PENALTY/);
	assert.match(source, /COFFEE/);
	assert.match(source, /pollResponseCounts/);
	assert.match(source, /missingResponseCount/);
	assert.match(source, /submittedCount/);
	assert.match(source, /missingCount/);
	assert.match(source, /isolationCampusId/);
	assert.match(source, /ADMIN_DASHBOARD_ACCESS_FORBIDDEN/);
	assert.match(dbValidator, /assert\.deepEqual/);
	assert.match(dbValidator, /pollResponseCounts/);
	assert.doesNotMatch(`${source}\n${tokenSource}`, /password\s*[:=]\s*['"][^'"]+['"]/i);
});

test('README and report path keep this issue scenario-ready/not-measured and prohibit writes and shared lifecycle changes', () => {
	const readme = read(files.readme);
	const ignore = read(files.reportsIgnore);

	assert.match(readme, /scenario-ready\/not-measured/i);
	assert.match(readme, /conditional-not-adoptable/i);
	assert.match(readme, /seed.*수행하지 않/i);
	assert.match(readme, /Docker.*실행하지 않/i);
	assert.match(readme, /다른.*부하.*병렬.*금지/);
	assert.match(readme, /production.*변경.*없/i);
	assert.match(readme, /faithlog-latest/);
	assert.match(readme, /frontend.*users\/me.*campuses\/me/is);
	assert.match(ignore, /^\*$/m);
	assert.match(ignore, /^!\.gitignore$/m);
});

test('k6 summary validator rejects non-zero failures and missing adoption metrics', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-summary-'));
	try {
		const validSummary = {
			metrics: {
				admin_dashboard_duration: {'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40},
				admin_dashboard_requests: {count: 12, rate: 6},
				admin_dashboard_failure_rate: {rate: 0},
			},
		};
		const validPath = path.join(temporaryDirectory, 'valid.json');
		const failedPath = path.join(temporaryDirectory, 'failed.json');
		const missingPath = path.join(temporaryDirectory, 'missing.json');
		const valuesPath = path.join(temporaryDirectory, 'values.json');
		const fractionalPath = path.join(temporaryDirectory, 'fractional.json');
		const invertedPath = path.join(temporaryDirectory, 'inverted.json');
		const contradictoryDirectPath = path.join(temporaryDirectory, 'contradictory-direct.json');
		const contradictoryValuesPath = path.join(temporaryDirectory, 'contradictory-values.json');
		fs.writeFileSync(validPath, JSON.stringify(validSummary));
		fs.writeFileSync(failedPath, JSON.stringify({
			...validSummary,
			metrics: {
				...validSummary.metrics,
				admin_dashboard_failure_rate: {rate: 0.01},
			},
		}));
		fs.writeFileSync(missingPath, JSON.stringify({metrics: {admin_dashboard_failure_rate: {rate: 0}}}));
		fs.writeFileSync(valuesPath, JSON.stringify({
			metrics: Object.fromEntries(Object.entries(validSummary.metrics).map(([name, values]) => [name, {values}])),
		}));
		fs.writeFileSync(fractionalPath, JSON.stringify({
			...validSummary,
			metrics: {...validSummary.metrics, admin_dashboard_requests: {count: 0.5, rate: 1}},
		}));
		fs.writeFileSync(invertedPath, JSON.stringify({
			metrics: {
				admin_dashboard_duration: {values: {'p(50)': 100, 'p(95)': 2, 'p(99)': 1, max: 3}},
				admin_dashboard_requests: {values: {count: 1, rate: 1}},
				admin_dashboard_failure_rate: {values: {rate: 0}},
			},
		}));
		fs.writeFileSync(contradictoryDirectPath, JSON.stringify({
			metrics: {
				...validSummary.metrics,
				admin_dashboard_failure_rate: {rate: 0, passes: 1, fails: 11},
			},
		}));
		fs.writeFileSync(contradictoryValuesPath, JSON.stringify({
			metrics: Object.fromEntries(Object.entries({
				...validSummary.metrics,
				admin_dashboard_failure_rate: {value: 0, passes: 1, fails: 11},
			}).map(([name, values]) => [name, {values}])),
		}));

		assert.equal(runNode(files.summaryValidator, validPath).status, 0);
		assert.equal(runNode(files.summaryValidator, valuesPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, failedPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, missingPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, fractionalPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, invertedPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, contradictoryDirectPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, contradictoryValuesPath).status, 0);
		const failedGate = JSON.parse(runNode(files.summaryValidator, failedPath).stdout);
		assert.equal(failedGate.status, 'contaminated');
		assert.equal(failedGate.adoptable, false);
		assert.equal(failedGate.automaticAdoption, false);
		assert.match(failedGate.failures[0].actual, /failure_rate|exactly zero/i);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runner fake execution refreshes the token per mode and keeps bootstrap outside each measured DB counter window', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty,small'});
		assert.notEqual(result.status, 0, 'boundary-only activity evidence must block baseline adoption');
		assert.match(result.stderr, /conditional-not-adoptable|boundary-snapshot-only/);
		const log = harness.log();
		assert.equal(Number(fs.readFileSync(harness.tokenCountPath, 'utf8')), 4);
		for (const [mode, counterOccurrence] of [
			['empty', 1],
			['small', 3],
		]) {
			const environment = JSON.parse(fs.readFileSync(
				harness.reportPath(mode, 'environment.json'),
				'utf8',
			));
			assert.equal(environment.externalActivityCoverage, 'boundary-snapshot-only');
			const warmupTokenIndex = findLog(log, `token:${mode}:warmup:`);
			const measuredTokenIndex = findLog(log, `token:${mode}:measured:`);
			const measuredIndex = findLog(log, `k6:${mode}:measured`);
			const preCounterIndex = findNthLog(log, 'docker:db-counters', counterOccurrence);
			const postCounterIndex = findNthLog(log, 'docker:db-counters', counterOccurrence + 1);
			assert.ok(warmupTokenIndex >= 0 && warmupTokenIndex < measuredTokenIndex);
			assert.ok(measuredTokenIndex < preCounterIndex);
			assert.ok(preCounterIndex < measuredIndex && measuredIndex < postCounterIndex);
			assert.equal(
				log.slice(preCounterIndex + 1, postCounterIndex).filter((line) => line.startsWith('docker:db-')).length,
				0,
				`${mode}: no correctness/context DB query may run inside the measured counter window`,
			);
		}
		assert.match(log.find((line) => line.startsWith('token:small:warmup:')), /:1901$/);
	} finally {
		harness.cleanup();
	}
});

test('runner binds BASE_URL to the inspected app published port before credential bootstrap', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({
			DATASET_MODES: 'empty',
			FAKE_APP_PORTS_JSON: JSON.stringify({'8080/tcp': [{HostIp: '127.0.0.1', HostPort: '28081'}]}),
		});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /published port|BASE_URL/i);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runtime target validator rejects localhost against a wildcard family binding', () => {
	const wildcardBinding = JSON.stringify({'8080/tcp': [{HostIp: '0.0.0.0', HostPort: '28080'}]});
	const result = runNode(files.runtimeTargetValidator, 'http://localhost:28080', '8080', wildcardBinding);
	assert.notEqual(result.status, 0);
	assert.match(result.stderr, /numeric loopback|address family|HostIp/i);
});

test('runtime target validator selects exactly one compatible family from dual-stack bindings', () => {
	const dualStack = JSON.stringify({'8080/tcp': [
		{HostIp: '0.0.0.0', HostPort: '28080'},
		{HostIp: '::', HostPort: '28080'},
	]});
	assert.equal(runNode(files.runtimeTargetValidator, 'http://127.0.0.1:28080', '8080', dualStack).status, 0);
	assert.equal(runNode(files.runtimeTargetValidator, 'http://[::1]:28080', '8080', dualStack).status, 0);

	const duplicateIpv4 = JSON.stringify({'8080/tcp': [
		{HostIp: '0.0.0.0', HostPort: '28080'},
		{HostIp: '127.0.0.1', HostPort: '28080'},
		{HostIp: '::', HostPort: '28080'},
	]});
	assert.notEqual(
		runNode(files.runtimeTargetValidator, 'http://127.0.0.1:28080', '8080', duplicateIpv4).status,
		0,
	);
	const ipv6Only = JSON.stringify({'8080/tcp': [{HostIp: '::', HostPort: '28080'}]});
	assert.notEqual(runNode(files.runtimeTargetValidator, 'http://127.0.0.1:28080', '8080', ipv6Only).status, 0);
	const wrongIpv4Port = JSON.stringify({'8080/tcp': [
		{HostIp: '0.0.0.0', HostPort: '28081'},
		{HostIp: '::', HostPort: '28080'},
	]});
	assert.notEqual(
		runNode(files.runtimeTargetValidator, 'http://127.0.0.1:28080', '8080', wrongIpv4Port).status,
		0,
	);
	assert.notEqual(runNode(files.runtimeTargetValidator, 'http://127.0.0.1:28080', '9090', dualStack).status, 0);
});

test('runner revalidates post-lock immutable endpoint identity before measured traffic', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty', FAKE_RUNTIME_IDENTITY_MODE: 'replace-before-initial'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /published port|runtime target|BASE_URL|image identity/i);
		assert.equal(findLog(harness.log(), 'k6:empty:measured'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runner rejects an app, postgres, or redis service-label mismatch before credential bootstrap', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty', FAKE_SERVICE_MODE: 'mismatch'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /Compose service labels do not match/);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runner blocks a measured phase when the fresh JWT cannot cover duration plus approved safety', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({
			DATASET_MODES: 'empty',
			MEASURED_DURATION: '60s',
			TOKEN_EXPIRY_SAFETY_SECONDS: '10',
			FAKE_MEASURED_TOKEN_TTL_SECONDS: '30',
		});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /token.*lifetime|expire/i);
		assert.equal(findLog(harness.log(), 'k6:empty:measured'), -1);
		assert.match(harness.log().find((line) => line.startsWith('token:empty:measured:')), /:1901$/);
	} finally {
		harness.cleanup();
	}
});

test('runner blocks warmup before k6 and DB context when its fresh JWT cannot cover duration plus safety', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({
			DATASET_MODES: 'empty',
			WARMUP_DURATION: '60s',
			TOKEN_EXPIRY_SAFETY_SECONDS: '10',
			FAKE_WARMUP_TOKEN_TTL_SECONDS: '30',
		});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /token.*lifetime|expire/i);
		assert.equal(findLog(harness.log(), 'k6:empty:warmup'), -1);
		assert.equal(findLog(harness.log(), 'docker:db-context'), -1);
	} finally {
		harness.cleanup();
	}
});

test('DB window validator rejects missing or regressed counters and produces semantic deltas', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-window-'));
	try {
		const before = dbWindowFixture();
		const after = dbWindowFixture({after: true});
		const valid = runDbWindowValidator(temporaryDirectory, before, after, 'none', 'valid');
		assert.notEqual(valid.result.status, 0, 'boundary-only activity evidence must not be adoptable');
		assert.equal(valid.output.status, 'conditional-not-adoptable');
		assert.equal(valid.output.adoptable, false);
		assert.equal(valid.output.externalActivityCoverage, 'boundary-snapshot-only');
		assert.equal(valid.output.tableDeltas.users.seq_scan, '1');
		assert.match(valid.result.stderr, /conditional-not-adoptable/);
		assert.doesNotMatch(valid.result.stderr, /contaminated/i);

		const bigintBefore = dbWindowFixture();
		const bigintAfter = dbWindowFixture({after: true});
		bigintBefore.tables.find(({relname}) => relname === 'users').seq_scan = '9007199254740992';
		bigintAfter.tables.find(({relname}) => relname === 'users').seq_scan = '9007199254740994';
		const bigint = runDbWindowValidator(temporaryDirectory, bigintBefore, bigintAfter, 'none', 'bigint');
		assert.equal(bigint.output.tableDeltas.users.seq_scan, '2');

		const safeNumber = structuredClone(after);
		safeNumber.database.xact_commit = 11;
		const nonCanonical = runDbWindowValidator(temporaryDirectory, before, safeNumber, 'none', 'safe-number');
		assert.notEqual(nonCanonical.result.status, 0);
		assert.match(nonCanonical.result.stderr, /decimal string/i);

		const unsafeNumber = structuredClone(after);
		unsafeNumber.database.xact_commit = Number.MAX_SAFE_INTEGER + 1;
		const unsafe = runDbWindowValidator(temporaryDirectory, before, unsafeNumber, 'none', 'unsafe-number');
		assert.notEqual(unsafe.result.status, 0);
		assert.match(unsafe.result.stderr, /safe integer|decimal string/i);

		const missing = structuredClone(after);
		missing.tables.pop();
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, missing, 'none', 'missing').result.status, 0);

		const regressed = structuredClone(after);
		regressed.tables[0].seq_scan = before.tables[0].seq_scan - 1;
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, regressed, 'none', 'regressed').result.status, 0);

		const reset = structuredClone(after);
		reset.database.stats_reset = '2026-07-14T01:00:00.000Z';
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, reset, 'none', 'reset').result.status, 0);

		const outOfOrder = structuredClone(after);
		outOfOrder.capturedAt = before.capturedAt;
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, outOfOrder, 'none', 'time').result.status, 0);

		const wrongObserverPolicy = structuredClone(after);
		wrongObserverPolicy.observerOverhead.databaseWideDeltaIsExactQueryCount = true;
		assert.notEqual(
			runDbWindowValidator(temporaryDirectory, before, wrongObserverPolicy, 'none', 'observer').result.status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('DB window validator blocks a short external request hidden between boundaries and other contamination', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-activity-'));
	try {
		const before = dbWindowFixture();
		const shortExternalRequest = {
			startedAt: '2026-07-14T00:00:20.000Z',
			endedAt: '2026-07-14T00:00:40.000Z',
		};
		assert.ok(Date.parse(shortExternalRequest.startedAt) > Date.parse(before.capturedAt));
		assert.ok(Date.parse(shortExternalRequest.endedAt) < Date.parse(dbWindowFixture({after: true}).capturedAt));
		const boundaryOnly = runDbWindowValidator(
			temporaryDirectory,
			before,
			dbWindowFixture({after: true}),
			'none',
			'control',
		);
		assert.notEqual(boundaryOnly.result.status, 0);
		assert.deepEqual(boundaryOnly.output.failures, [{
			name: 'externalActivityCoverage',
			expected: 'continuous approved provenance or isolation',
			actual: 'boundary-snapshot-only',
		}]);
		const autoanalyzed = dbWindowFixture({after: true});
		autoanalyzed.tables[0].autoanalyze_count = '1';
		autoanalyzed.tables[0].last_autoanalyze = '2026-07-14T00:01:00.000Z';
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, autoanalyzed, 'none', 'autoanalyze').result.status, 0);

		const externalSession = dbWindowFixture({after: true});
		externalSession.externalActiveSessions = '1';
		externalSession.externalActiveSessionDetails = [{pid: 42, applicationName: 'frontend'}];
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, externalSession, 'none', 'session').result.status, 0);

		const declaredRequest = dbWindowFixture({after: true});
		assert.notEqual(
			runDbWindowValidator(temporaryDirectory, before, declaredRequest, 'frontend-request', 'request').result.status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('DB window validator requires vacuum fields and marks vacuum drift contaminated', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-vacuum-'));
	try {
		const before = dbWindowFixture();
		const after = dbWindowFixture({after: true});
		const vacuumed = structuredClone(after);
		vacuumed.tables[0].vacuum_count = '1';
		vacuumed.tables[0].last_vacuum = '2026-07-14T00:00:30.000Z';
		const drift = runDbWindowValidator(temporaryDirectory, before, vacuumed, 'none', 'vacuum-drift');
		assert.notEqual(drift.result.status, 0);
		assert.equal(drift.output.status, 'contaminated');
		assert.ok(drift.output.failures.some(({name}) => /vacuum/.test(name)));

		const missingBefore = structuredClone(before);
		const missingAfter = structuredClone(after);
		delete missingBefore.tables[0].autovacuum_count;
		delete missingAfter.tables[0].autovacuum_count;
		const missing = runDbWindowValidator(temporaryDirectory, missingBefore, missingAfter, 'none', 'vacuum-missing');
		assert.notEqual(missing.result.status, 0);
		assert.match(missing.result.stderr, /autovacuum_count/);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('DB window validator exact-binds pg_stat_statements availability and reset continuity', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-pgss-'));
	try {
		const before = dbWindowFixture();
		const after = dbWindowFixture({after: true});
		const unavailable = runDbWindowValidator(temporaryDirectory, before, after, 'none', 'unavailable');
		assert.equal(unavailable.output.pgStatStatements.status, 'unavailable');
		assert.equal(unavailable.output.pgStatStatements.statsReset, null);

		for (const snapshot of [before, after]) {
			snapshot.pgStatStatements = {
				status: 'available',
				extensionInstalled: true,
				viewAvailable: true,
				extensionVersion: '1.10',
				statsReset: '2026-07-13T00:00:00.000Z',
			};
		}
		const available = runDbWindowValidator(temporaryDirectory, before, after, 'none', 'available');
		assert.equal(available.output.pgStatStatements.status, 'available');
		assert.equal(available.output.pgStatStatements.statsReset, '2026-07-13T00:00:00.000Z');

		const reset = structuredClone(after);
		reset.pgStatStatements.statsReset = '2026-07-14T00:00:30.000Z';
		const changed = runDbWindowValidator(temporaryDirectory, before, reset, 'none', 'reset');
		assert.notEqual(changed.result.status, 0);
		assert.equal(changed.output.status, 'contaminated');

		const unavailableAfter = dbWindowFixture({after: true});
		const becameUnavailable = runDbWindowValidator(
			temporaryDirectory, before, unavailableAfter, 'none', 'became-unavailable',
		);
		assert.notEqual(becameUnavailable.result.status, 0);
		assert.equal(becameUnavailable.output.status, 'contaminated');

		const unavailableBefore = dbWindowFixture();
		const becameAvailable = runDbWindowValidator(
			temporaryDirectory, unavailableBefore, after, 'none', 'became-available',
		);
		assert.notEqual(becameAvailable.result.status, 0);
		assert.equal(becameAvailable.output.status, 'contaminated');

		const missing = dbWindowFixture();
		delete missing.pgStatStatements;
		assert.notEqual(
			runDbWindowValidator(temporaryDirectory, missing, dbWindowFixture({after: true}), 'none', 'missing').result.status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('Docker resource validator exact-binds mode, boundary, component identity, CPU, and RAM', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-resources-'));
	try {
		const identity = runtimeIdentityFixture();
		const valid = runDockerResourceValidator(
			temporaryDirectory, dockerResourceFixture(), identity, 'small', 'before', 'valid',
		);
		assert.equal(valid.result.status, 0, valid.result.stderr);
		assert.equal(valid.output.status, 'docker-resource-evidence-valid');
		assert.equal(valid.output.adoptable, true);
		assert.deepEqual(valid.output.components.map(({component}) => component), ['app', 'postgres', 'redis']);
		const validAfter = runDockerResourceValidator(
			temporaryDirectory,
			dockerResourceFixture({boundary: 'after'}),
			runtimeIdentityFixture({capturedAt: '2026-07-14T00:01:00.000Z'}),
			'small',
			'after',
			'valid-after',
		);
		assert.equal(validAfter.result.status, 0, validAfter.result.stderr);
		const multicoreCpu = dockerResourceFixture();
		multicoreCpu[0].stats.CPUPerc = '250.50%';
		assert.equal(
			runDockerResourceValidator(
				temporaryDirectory, multicoreCpu, identity, 'small', 'before', 'multicore-cpu',
			).result.status,
			0,
			'multi-core CPU percentages above 100 must remain valid',
		);

		for (const [label, mutate] of [
			['malformed', () => ['not-json']],
			['missing', (rows) => rows.slice(0, 2)],
			['mixed-mode', (rows) => rows.map((row, index) => index === 1 ? {...row, datasetMode: 'thousand'} : row)],
			['mixed-boundary', (rows) => rows.map((row, index) => index === 1 ? {...row, boundary: 'after'} : row)],
			['wrong-id', (rows) => rows.map((row, index) => index === 0 ? {...row, stats: {...row.stats, ID: 'sha256:other'}} : row)],
			['negative-cpu', (rows) => rows.map((row, index) => index === 0 ? {...row, stats: {...row.stats, CPUPerc: '-1%'}} : row)],
			['nan-memory', (rows) => rows.map((row, index) => index === 0 ? {...row, stats: {...row.stats, MemUsage: 'NaNMiB / 1GiB'}} : row)],
			['zero-limit', (rows) => rows.map((row, index) => index === 0 ? {...row, stats: {...row.stats, MemUsage: '0B / 0B', MemPerc: '0%'}} : row)],
			['over-100-memory', (rows) => rows.map((row, index) => index === 0 ? {...row, stats: {...row.stats, MemPerc: '150%'}} : row)],
			['unsafe-memory', (rows) => rows.map((row, index) => index === 0 ? {...row, stats: {...row.stats, MemUsage: '9PB / 10PB'}} : row)],
		]) {
			const invalid = runDockerResourceValidator(
				temporaryDirectory, mutate(dockerResourceFixture()), identity, 'small', 'before', label,
			);
			assert.notEqual(invalid.result.status, 0, `${label} resource evidence must fail`);
			assert.equal(invalid.output.status, 'contaminated', `${label} must be classified as contaminated`);
			assert.equal(invalid.output.adoptable, false);
		}
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('Docker resource validator reports canonical RAM percent and defers inconsistent raw MemPerc', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-resource-percent-'));
	try {
		const rows = dockerResourceFixture().map((row) => ({
			...row,
			stats: {...row.stats, MemUsage: '64MiB / 1GiB', MemPerc: '99%'},
		}));
		const inconsistent = runDockerResourceValidator(
			temporaryDirectory, rows, runtimeIdentityFixture(), 'small', 'before', 'inconsistent-percent',
		);
		assert.notEqual(inconsistent.result.status, 0);
		assert.equal(inconsistent.output.status, 'conditional-not-adoptable');
		assert.equal(inconsistent.output.adoptable, false);
		assert.equal(inconsistent.output.automaticAdoption, false);
		for (const component of inconsistent.output.components) {
			assert.equal(component.canonicalMemoryPercent, 6.25);
			assert.equal(component.reportedMemoryPercent, 99);
		}
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('Docker resource evidence carries an exact boundary sample cadence and timestamp', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-resource-cadence-'));
	try {
		const rows = dockerResourceFixture().map((row) => ({
			...row,
			sampledAt: '2026-07-14T00:00:10.000Z',
			sampleSequence: 1,
			samplingCadence: 'one-no-stream-snapshot-per-boundary',
		}));
		const valid = runDockerResourceValidator(
			temporaryDirectory, rows, runtimeIdentityFixture(), 'small', 'before', 'cadence-valid',
		);
		assert.equal(valid.result.status, 0, valid.result.stderr);
		assert.equal(valid.output.sampledAt, '2026-07-14T00:00:10.000Z');
		assert.equal(valid.output.samplingCadence, 'one-no-stream-snapshot-per-boundary');

		const mixed = structuredClone(rows);
		mixed[1].sampledAt = '2026-07-14T00:00:11.000Z';
		const rejected = runDockerResourceValidator(
			temporaryDirectory, mixed, runtimeIdentityFixture(), 'small', 'before', 'cadence-mixed',
		);
		assert.notEqual(rejected.result.status, 0);
		assert.equal(rejected.output.status, 'contaminated');
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runner requires explicit PostgreSQL and Redis containers before inspect or credential bootstrap', () => {
	for (const variable of ['POSTGRES_CONTAINER', 'REDIS_CONTAINER']) {
		const harness = createFakeRunnerHarness();
		try {
			const result = harness.run({DATASET_MODES: 'empty', [variable]: undefined});
			assert.notEqual(result.status, 0, `${variable} must be runtime-required`);
			assert.match(result.stderr, new RegExp(variable));
			assert.equal(findLog(harness.log(), 'docker:inspect'), -1);
			assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
		} finally {
			harness.cleanup();
		}
	}
	const source = read(files.runner);
	assert.match(source, /CONTAINER_ALIAS="\$\{CONTAINER_ALIAS:-faithlog-latest\}"/);
	assert.doesNotMatch(source, /docker (?:inspect|stats)[^\n]*\$CONTAINER_ALIAS/);
});

test('runner blocks malformed or mixed-mode Docker resource evidence before adoption', () => {
	for (const mode of ['malformed', 'mixed-mode']) {
		const harness = createFakeRunnerHarness();
		try {
			const result = harness.run({DATASET_MODES: 'empty', FAKE_DOCKER_RESOURCE_MODE: mode});
			assert.notEqual(result.status, 0);
			assert.match(result.stderr, /Docker resource|resource evidence|JSON/i);
			assert.equal(findLog(harness.log(), 'k6:empty:measured'), -1);
		} finally {
			harness.cleanup();
		}
	}
});

test('DB window validator rejects null counters and missing identity, stability, or planner fields', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-schema-'));
	try {
		const controlBefore = dbWindowFixture();
		const controlAfter = dbWindowFixture({after: true});

		const nullCountersBefore = structuredClone(controlBefore);
		const nullCountersAfter = structuredClone(controlAfter);
		for (const snapshot of [nullCountersBefore, nullCountersAfter]) {
			for (const field of [
				'xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched',
				'temp_files', 'temp_bytes', 'deadlocks',
			]) snapshot.database[field] = null;
			for (const table of snapshot.tables) {
				for (const field of ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch']) table[field] = null;
			}
		}
		const nullCounters = runDbWindowValidator(
			temporaryDirectory, nullCountersBefore, nullCountersAfter, 'none', 'null-counters',
		);
		assert.notEqual(nullCounters.result.status, 0);
		assert.match(nullCounters.result.stderr, /finite non-negative|safe integer|decimal string/i);

		const missingDatabaseIdentityBefore = structuredClone(controlBefore);
		const missingDatabaseIdentityAfter = structuredClone(controlAfter);
		delete missingDatabaseIdentityBefore.database.datname;
		delete missingDatabaseIdentityAfter.database.datname;
		const missingDatabaseIdentity = runDbWindowValidator(
			temporaryDirectory, missingDatabaseIdentityBefore, missingDatabaseIdentityAfter, 'none', 'database-identity',
		);
		assert.notEqual(missingDatabaseIdentity.result.status, 0);
		assert.match(missingDatabaseIdentity.result.stderr, /database\.datname/);

		const missingStabilityBefore = structuredClone(controlBefore);
		const missingStabilityAfter = structuredClone(controlAfter);
		for (const snapshot of [missingStabilityBefore, missingStabilityAfter]) {
			for (const table of snapshot.tables) delete table.n_mod_since_analyze;
		}
		const missingStability = runDbWindowValidator(
			temporaryDirectory, missingStabilityBefore, missingStabilityAfter, 'none', 'missing-stability',
		);
		assert.notEqual(missingStability.result.status, 0);
		assert.match(missingStability.result.stderr, /n_mod_since_analyze/);

		const missingPlannerBefore = structuredClone(controlBefore);
		const missingPlannerAfter = structuredClone(controlAfter);
		for (const snapshot of [missingPlannerBefore, missingPlannerAfter]) {
			snapshot.plannerSettings = snapshot.plannerSettings.map(({name}) => ({name}));
		}
		const missingPlanner = runDbWindowValidator(
			temporaryDirectory, missingPlannerBefore, missingPlannerAfter, 'none', 'missing-planner',
		);
		assert.notEqual(missingPlanner.result.status, 0);
		assert.match(missingPlanner.result.stderr, /plannerSettings.*setting|plannerSettings.*source/s);

		const missingPlannerContextBefore = structuredClone(controlBefore);
		const missingPlannerContextAfter = structuredClone(controlAfter);
		delete missingPlannerContextBefore.plannerContext;
		delete missingPlannerContextAfter.plannerContext;
		const missingPlannerContext = runDbWindowValidator(
			temporaryDirectory, missingPlannerContextBefore, missingPlannerContextAfter, 'none', 'planner-context',
		);
		assert.notEqual(missingPlannerContext.result.status, 0);
		assert.match(missingPlannerContext.result.stderr, /plannerContext/);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runtime continuity validator rejects container or PostgreSQL identity replacement', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-runtime-identity-'));
	try {
		const initial = runtimeIdentityFixture();
		const before = runtimeIdentityFixture({capturedAt: '2026-07-14T00:00:10.000Z'});
		const after = runtimeIdentityFixture({capturedAt: '2026-07-14T00:01:10.000Z'});
		const control = runRuntimeContinuityValidator(temporaryDirectory, initial, before, after, 'control');
		assert.equal(control.result.status, 0, control.result.stderr);
		assert.equal(control.output.continuous, true);

		for (const component of ['app', 'postgres', 'redis']) {
			for (const field of [
				'id', 'imageId', 'imageRef', 'startedAt', 'composeProject', 'composeService', 'composeConfigHash',
			]) {
				const replacedContainer = structuredClone(after);
				replacedContainer.containers[component][field] = field === 'startedAt'
					? '2026-07-14T00:00:30.000Z'
					: `replacement-${component}-${field}`;
				const containerResult = runRuntimeContinuityValidator(
					temporaryDirectory, initial, before, replacedContainer, `${component}-${field}`,
				);
				assert.notEqual(containerResult.result.status, 0, `${component}.${field} replacement must fail`);
				assert.match(containerResult.result.stderr, new RegExp(`runtime identity|containers\\.${component}`));
			}
		}
		const replacedPublishedPorts = structuredClone(after);
		replacedPublishedPorts.containers.app.publishedPorts['8080/tcp'][0].HostPort = '28081';
		const publishedPortsResult = runRuntimeContinuityValidator(
			temporaryDirectory, initial, before, replacedPublishedPorts, 'app-published-ports',
		);
		assert.notEqual(publishedPortsResult.result.status, 0);
		assert.match(publishedPortsResult.result.stderr, /containers\.app\.publishedPorts/);

		const replacedPostmaster = structuredClone(after);
		replacedPostmaster.postgres.postmasterStartedAt = '2026-07-14T00:00:45.000Z';
		const postgresResult = runRuntimeContinuityValidator(
			temporaryDirectory, initial, before, replacedPostmaster, 'postmaster-replacement',
		);
		assert.notEqual(postgresResult.result.status, 0);
		assert.match(postgresResult.result.stderr, /runtime identity|postmaster/i);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runtime continuity validator rejects PostgreSQL contract or Redis server replacement', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-server-identity-'));
	try {
		const initial = runtimeIdentityFixture();
		const before = runtimeIdentityFixture({capturedAt: '2026-07-14T00:00:10.000Z'});
		const after = runtimeIdentityFixture({capturedAt: '2026-07-14T00:01:00.000Z'});
		after.redis.runId = 'replacement-run-id';
		assert.notEqual(
			runRuntimeContinuityValidator(temporaryDirectory, initial, before, after, 'redis-replacement').result.status,
			0,
		);

		const migratedAfter = runtimeIdentityFixture({capturedAt: '2026-07-14T00:01:00.000Z'});
		migratedAfter.postgres.flyway.latestVersion = '10';
		assert.notEqual(
			runRuntimeContinuityValidator(temporaryDirectory, initial, before, migratedAfter, 'flyway-drift').result.status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('direct scenario inputs and campus-manager bootstrap are fail-closed without defaults', () => {
	const scenario = read(files.scenario);
	const verifier = read(files.verifier);
	const token = read(files.runtimeToken);
	for (const source of [scenario, verifier]) {
		assert.doesNotMatch(source, /BASE_URL\s*=.*(?:\|\||127\.0\.0\.1)/);
		assert.doesNotMatch(source, /DATASET_MODE\s*=.*\|\|/);
	}
	assert.doesNotMatch(scenario, /VUS\s*=.*\|\|/);
	assert.doesNotMatch(scenario, /DURATION\s*=.*\|\|/);
	assert.doesNotMatch(scenario, /PHASE\s*=.*\|\|/);
	assert.doesNotMatch(read(files.tokenLifetimeValidator), /purposeSource\s*=\s*['"]runtime/);
	assert.match(token, /MINISTER/);
	assert.match(token, /ELDER/);
	assert.match(token, /CAMPUS_LEADER/);
	assert.match(token, /campusRole/);
});

test('run input rejects examples, expired or reused fixture namespaces, and missing image identities before target access', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-run-input-'));
	try {
		const manifest = {
			schemaVersion: 2,
			issue: 199,
			currentDevelopBase: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
			datasetId: 'CONTRACT_DATASET',
			fixtureNamespace: {
				namespaceId: 'CONTRACT_NAMESPACE',
				preparedAt: '2026-07-14T00:00:00.000Z',
				expiresAt: '2026-07-14T02:00:00.000Z',
				immutable: true,
			},
			runtimeTarget: {
				app: {service: 'app', containerPort: 8080, imageId: `sha256:${'d'.repeat(64)}`, imageRef: 'faithlog/app:approved',
					sourceProvenance: {sourceWorktree: '/private/tmp/FaithLog-perf-206-deploy',
						revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97', apiContractSha256: '2'.repeat(64)}},
				postgres: {service: 'postgres', containerPort: 5432, imageId: `sha256:${'e'.repeat(64)}`, imageRef: 'postgres:17'},
				redis: {service: 'redis', containerPort: 6379, imageId: `sha256:${'f'.repeat(64)}`, imageRef: 'redis:7-alpine'},
			},
			modes: {
				empty: {mode: 'empty', fixtureRunId: 'EMPTY', campusId: 1, isolationCampusId: 2},
				small: {mode: 'small', fixtureRunId: 'SMALL', campusId: 3, isolationCampusId: 4},
			},
		};
		const manifestPath = path.join(temporaryDirectory, 'manifest.json');
		fs.writeFileSync(manifestPath, JSON.stringify(manifest));
		assert.equal(runNode(
			files.runInputValidator, manifestPath, 'empty,small', '1783990800', '1', '1s', '1', '1s', '60',
		).status, 0);

		const example = structuredClone(manifest);
		example.exampleOnly = true;
		fs.writeFileSync(manifestPath, JSON.stringify(example));
		assert.notEqual(runNode(files.runInputValidator, manifestPath, 'empty', '1783990800', '1', '1s', '1', '1s', '60').status, 0);

		const expired = structuredClone(manifest);
		expired.fixtureNamespace.expiresAt = '2026-07-14T00:30:00.000Z';
		fs.writeFileSync(manifestPath, JSON.stringify(expired));
		assert.notEqual(runNode(files.runInputValidator, manifestPath, 'empty', '1783990800', '1', '1s', '1', '1s', '60').status, 0);

		const reused = structuredClone(manifest);
		reused.modes.small.fixtureRunId = 'EMPTY';
		fs.writeFileSync(manifestPath, JSON.stringify(reused));
		assert.notEqual(runNode(files.runInputValidator, manifestPath, 'empty,small', '1783990800', '1', '1s', '1', '1s', '60').status, 0);

		const missingImage = structuredClone(manifest);
		delete missingImage.runtimeTarget.redis.imageId;
		fs.writeFileSync(manifestPath, JSON.stringify(missingImage));
		assert.notEqual(runNode(files.runInputValidator, manifestPath, 'empty', '1783990800', '1', '1s', '1', '1s', '60').status, 0);

		const missingProvenance = structuredClone(manifest);
		delete missingProvenance.runtimeTarget.app.sourceProvenance;
		fs.writeFileSync(manifestPath, JSON.stringify(missingProvenance));
		assert.notEqual(runNode(files.runInputValidator, manifestPath, 'empty', '1783990800', '1', '1s', '1', '1s', '60').status, 0);

		fs.writeFileSync(manifestPath, JSON.stringify(manifest));
		for (const invalidArgs of [
			['0', '1s', '1', '1s', '60'],
			['1', 'bad', '1', '1s', '60'],
			['1', '1s', 'NaN', '1s', '60'],
			['1', '1s', '1', '0s', '60'],
			['1', '1s', '1', '1s', '-1'],
		]) {
			assert.notEqual(
				runNode(files.runInputValidator, manifestPath, 'empty', '1783990800', ...invalidArgs).status,
				0,
			);
		}
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runner validates approved image identity across pre-lock, post-lock, and final continuity', () => {
	const source = read(files.runner);
	assert.match(source, /EXPECTED_APP_IMAGE_ID/);
	assert.match(source, /EXPECTED_POSTGRES_IMAGE_ID/);
	assert.match(source, /EXPECTED_REDIS_IMAGE_ID/);
	assert.match(source, /validate-pre-post-lock-target\.mjs/);
	assert.match(source, /runtime-identity-final/);
	assert.match(source, /parse-redis-server-identity\.mjs/);
});

test('runner accepts approved clean detached source provenance when OCI revision labels are unavailable', async () => {
	assert.equal(fs.existsSync(files.sourceImageProvenance), true, 'source/image provenance validator must exist');
	const {validateSourceImageProvenance} = await import(pathToFileURL(files.sourceImageProvenance));
	const sourceWorktree = '/private/tmp/FaithLog-perf-206-deploy';
	const facts = {
		schemaVersion: 1,
		proofMode: 'clean-detached-checkout-image-created-after-checkout',
		sourceWorktree,
		composeWorkingDir: sourceWorktree,
		revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		detached: true,
		clean: true,
		checkoutAt: '2026-07-16T13:20:28+09:00',
		imageId: `sha256:${'1'.repeat(64)}`,
		imageCreatedAt: '2026-07-16T04:22:48.810414883Z',
		apiContractSha256: '2'.repeat(64),
		limitation: 'image-alone-revision-label-unavailable',
	};
	const expected = {
		sourceWorktree,
		revision: facts.revision,
		imageId: facts.imageId,
		apiContractSha256: facts.apiContractSha256,
	};
	assert.equal(validateSourceImageProvenance(facts, expected), facts);
	assert.throws(() => validateSourceImageProvenance({...facts, clean: false}, expected), /clean/i);
	assert.throws(() => validateSourceImageProvenance({...facts, imageCreatedAt: facts.checkoutAt}, expected), /after checkout/i);

	const runner = read(files.runner);
	const inputValidator = read(files.runInputValidator);
	assert.match(inputValidator, /sourceProvenance/);
	assert.match(runner, /validate-source-image-provenance\.mjs/);
	assert.match(runner, /com\.docker\.compose\.project\.working_dir/);
	assert.match(runner, /docker image inspect[^\n]*\.Created/);
	assert.doesNotMatch(runner, /org\.opencontainers\.image\.(?:revision|api-contract-sha256)/);
});

test('pre/post-lock target gate rejects same-label replacement and app connections bind approved DB and Redis', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-pre-post-lock-'));
	try {
		const before = runtimeIdentityFixture();
		const after = runtimeIdentityFixture({capturedAt: '2026-07-14T00:00:10.000Z'});
		const beforePath = path.join(temporaryDirectory, 'before.json');
		const afterPath = path.join(temporaryDirectory, 'after.json');
		const outputPath = path.join(temporaryDirectory, 'gate.json');
		fs.writeFileSync(beforePath, JSON.stringify({capturedAt: before.capturedAt, containers: before.containers}));
		fs.writeFileSync(afterPath, JSON.stringify(after));
		assert.equal(runNode(files.prePostLockValidator, beforePath, afterPath, outputPath).status, 0);

		const replaced = structuredClone(after);
		replaced.containers.app.id = `sha256:${'9'.repeat(64)}`;
		fs.writeFileSync(afterPath, JSON.stringify(replaced));
		const replacementOutput = path.join(temporaryDirectory, 'replacement-gate.json');
		assert.notEqual(runNode(files.prePostLockValidator, beforePath, afterPath, replacementOutput).status, 0);
		assert.equal(JSON.parse(fs.readFileSync(replacementOutput, 'utf8')).automaticAdoption, false);

		const appEnvironment = JSON.stringify([
			'SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/faithlog',
			'SPRING_DATASOURCE_USERNAME=runtime-db-user',
			'SPRING_DATASOURCE_PASSWORD=never-report-this',
			'SPRING_DATA_REDIS_HOST=redis',
			'SPRING_DATA_REDIS_PORT=6379',
		]);
		const connection = runNode(
			files.appConnectionValidator,
			appEnvironment, 'postgres', '5432', 'faithlog', 'runtime-db-user', 'redis', '6379',
		);
		assert.equal(connection.status, 0, connection.stderr);
		assert.doesNotMatch(connection.stdout, /never-report-this/);
		assert.notEqual(
			runNode(files.appConnectionValidator, appEnvironment, 'postgres', '5432', 'faithlog', 'other-user', 'redis', '6379').status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('Redis INFO parser returns strict server identity without optional fallbacks', () => {
	const input = [
		'# Server',
		'redis_version:7.4.0',
		'run_id:0123456789abcdef0123456789abcdef01234567',
		'tcp_port:6379',
		'uptime_in_seconds:123',
		'',
	].join('\r\n');
	const result = spawnSync(process.execPath, [files.redisIdentityParser], {encoding: 'utf8', input});
	assert.equal(result.status, 0, result.stderr);
	assert.deepEqual(JSON.parse(result.stdout), {
		runId: '0123456789abcdef0123456789abcdef01234567',
		serverVersion: '7.4.0',
		serverPort: 6379,
		uptimeSeconds: '123',
	});
	assert.notEqual(spawnSync(process.execPath, [files.redisIdentityParser], {encoding: 'utf8', input: 'redis_version:7\n'}).status, 0);
});

test('malformed DB and runtime evidence preserve the first machine-readable rejection', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-machine-rejection-'));
	try {
		const malformedPath = path.join(temporaryDirectory, 'malformed.json');
		fs.writeFileSync(malformedPath, '{not-json');
		const dbOutput = path.join(temporaryDirectory, 'db-output.json');
		const db = runNode(files.dbWindowValidator, malformedPath, malformedPath, 'none', dbOutput);
		assert.notEqual(db.status, 0);
		const dbGate = JSON.parse(fs.readFileSync(dbOutput, 'utf8'));
		assert.equal(dbGate.status, 'contaminated');
		assert.equal(dbGate.automaticAdoption, false);

		const runtimeOutput = path.join(temporaryDirectory, 'runtime-output.json');
		const runtime = runNode(
			files.runtimeContinuityValidator, malformedPath, malformedPath, malformedPath, runtimeOutput,
		);
		assert.notEqual(runtime.status, 0);
		const runtimeGate = JSON.parse(fs.readFileSync(runtimeOutput, 'utf8'));
		assert.equal(runtimeGate.status, 'contaminated');
		assert.equal(runtimeGate.automaticAdoption, false);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runner blocks a container identity replacement during the measured window', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty', FAKE_RUNTIME_IDENTITY_MODE: 'replace-after'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /runtime identity|containers\.app/i);
		assert.ok(findLog(harness.log(), 'k6:empty:measured') >= 0);
	} finally {
		harness.cleanup();
	}
});

test('runner blocks a container identity replacement between dataset modes before second measured traffic', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty,small', FAKE_RUNTIME_IDENTITY_MODE: 'replace-second-mode'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /runtime identity|containers\.app/i);
		assert.equal(findLog(harness.log(), 'k6:small:measured'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runner refuses an existing mode report directory instead of mixing stale evidence', () => {
	const harness = createFakeRunnerHarness();
	fs.mkdirSync(harness.reportPath('empty'), {recursive: true});
	fs.writeFileSync(harness.reportPath('empty', 'stale.json'), '{}');
	try {
		const result = harness.run({DATASET_MODES: 'empty'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /Report directory already exists/);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runner requires an explicit unique allowlisted DATASET_MODES selection', () => {
	const harness = createFakeRunnerHarness();
	try {
		const missing = harness.run({DATASET_MODES: ''});
		assert.notEqual(missing.status, 0);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
		const duplicated = harness.run({DATASET_MODES: 'empty,empty'});
		assert.notEqual(duplicated.status, 0);
		assert.match(duplicated.stderr, /duplicate dataset mode/i);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runner rejects mismatched Compose project labels before credential bootstrap', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty', FAKE_LABEL_MODE: 'mismatch'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /Compose project labels do not match/);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
	} finally {
		harness.cleanup();
	}
});

test('runner uses the shared Compose-project lock and rejects a lock held by another issue', () => {
	const harness = createFakeRunnerHarness();
	const lockDirectory = `/tmp/faithlog-performance-${harness.composeProject}.lock`;
	fs.mkdirSync(lockDirectory);
	try {
		const result = harness.run({DATASET_MODES: 'empty'});
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /Another performance/);
		assert.equal(findLog(harness.log(), 'prepare-runtime-token.mjs'), -1);
	} finally {
		fs.rmdirSync(lockDirectory);
		harness.cleanup();
	}
});

test('DB correctness validator exact-matches per-poll counts even when aggregate missing is unchanged', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-correctness-'));
	try {
		const manifest = {
			issue: 199,
			datasetId: 'CONTRACT_DATASET',
			modes: {small: {mode: 'small', fixtureRunId: 'SMALL', expected: expectedFixture()}},
		};
		const manifestPath = path.join(temporaryDirectory, 'manifest.json');
		const validEvidencePath = path.join(temporaryDirectory, 'valid.json');
		const redistributedEvidencePath = path.join(temporaryDirectory, 'redistributed.json');
		const validEvidence = expectedFixture();
		const redistributedEvidence = structuredClone(validEvidence);
		redistributedEvidence.pollResponseCounts = [
			{pollId: 201, responseCount: 19},
			{pollId: 202, responseCount: 16},
		];
		fs.writeFileSync(manifestPath, JSON.stringify(manifest));
		fs.writeFileSync(validEvidencePath, JSON.stringify({evidenceBoundary: 'before', ...validEvidence}));
		fs.writeFileSync(redistributedEvidencePath, JSON.stringify({evidenceBoundary: 'before', ...redistributedEvidence}));

		assert.equal(runNode(files.dbCorrectnessValidator, manifestPath, 'small', 'before', validEvidencePath).status, 0);
		assert.notEqual(
			runNode(files.dbCorrectnessValidator, manifestPath, 'small', 'before', redistributedEvidencePath).status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('DB correctness validator exact-binds before, pre-measured, and after evidence boundaries', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-correctness-boundary-'));
	try {
		const manifestPath = path.join(temporaryDirectory, 'manifest.json');
		const evidencePath = path.join(temporaryDirectory, 'evidence.json');
		const manifest = {
			issue: 199,
			datasetId: 'CONTRACT',
			modes: {small: {mode: 'small', fixtureRunId: 'FIXTURE', expected: expectedFixture()}},
		};
		fs.writeFileSync(manifestPath, JSON.stringify(manifest));
		fs.writeFileSync(evidencePath, JSON.stringify({
			evidenceBoundary: 'pre-measured',
			...expectedFixture(),
		}));
		assert.equal(
			runNode(files.dbCorrectnessValidator, manifestPath, 'small', 'pre-measured', evidencePath).status,
			0,
		);
		assert.notEqual(
			runNode(files.dbCorrectnessValidator, manifestPath, 'small', 'after', evidencePath).status,
			0,
		);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

function runNode(script, ...args) {
	return spawnSync(process.execPath, [script, ...args], {encoding: 'utf8'});
}

function expectedFixture() {
	return {
		campus: {campusId: 101, campusName: 'CONTRACT', region: 'TEST'},
		members: {activeCount: 30, inactiveCount: 2, adminCount: 3},
		devotion: {weekStartDate: '2026-07-13', submittedCount: 20, missingCount: 10, submitRate: 66.7},
		charges: {
			statusBasis: ['UNPAID'],
			unpaidAmount: 120000,
			unpaidMemberCount: 12,
			byCategory: [
				{paymentCategory: 'PENALTY', unpaidAmount: 80000},
				{paymentCategory: 'COFFEE', unpaidAmount: 40000},
			],
		},
		polls: {openCount: 2, recentlyClosedCount: 1, missingResponseCount: 25, recentlyClosedDays: 7},
		pollResponseCounts: [
			{pollId: 201, responseCount: 20},
			{pollId: 202, responseCount: 15},
		],
	};
}

function shellQuote(value) {
	return `'${value.replaceAll("'", "'\\''")}'`;
}

function findLog(log, fragment) {
	return log.findIndex((line) => line.includes(fragment));
}

function findNthLog(log, fragment, occurrence) {
	let seen = 0;
	for (let index = 0; index < log.length; index += 1) {
		if (log[index].includes(fragment) && ++seen === occurrence) {
			return index;
		}
	}
	return -1;
}

const requiredDbTables = [
	'users', 'campuses', 'campus_members', 'weekly_devotion_records', 'devotion_daily_checks',
	'payment_accounts', 'charge_items', 'polls', 'poll_responses', 'meal_poll_settlements', 'prayer_submissions',
];

const requiredPlannerSettings = [
	'enable_bitmapscan', 'enable_hashagg', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
	'enable_material', 'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'jit', 'plan_cache_mode',
	'random_page_cost', 'work_mem',
];

function dbWindowFixture({after = false} = {}) {
	const increment = after ? 1 : 0;
	return {
		capturedAt: after ? '2026-07-14T00:01:00.000Z' : '2026-07-14T00:00:00.000Z',
		externalActivityCoverage: 'boundary-snapshot-only',
		externalActiveSessions: '0',
		externalActiveSessionDetails: [],
		plannerContext: {
			currentUser: 'observer',
			sessionUser: 'observer',
			database: 'faithlog',
			applicationName: 'faithlog-issue199-observer',
			scope: 'observer-session',
		},
		observerOverhead: {
			databaseWideCountersIncludeSnapshotTransaction: true,
			databaseWideDeltaIsExactQueryCount: false,
			appTableCountersReadApplicationTables: false,
		},
		database: {
			datname: 'faithlog',
			stats_reset: '2026-07-13T00:00:00.000Z',
			xact_commit: String(10 + increment),
			xact_rollback: '0',
			blks_read: '1',
			blks_hit: String(100 + increment),
			tup_returned: String(1000 + increment),
			tup_fetched: String(100 + increment),
			temp_files: '0',
			temp_bytes: '0',
			deadlocks: '0',
		},
		tables: requiredDbTables.map((relname, index) => ({
			relname,
			seq_scan: String(index + 10 + increment),
			seq_tup_read: String(index + 100 + increment),
			idx_scan: String(index + 20 + increment),
			idx_tup_fetch: String(index + 30 + increment),
			n_live_tup: String(index + 1000),
			n_dead_tup: '0',
			n_mod_since_analyze: '0',
			last_analyze: null,
			last_autoanalyze: null,
			analyze_count: '0',
			autoanalyze_count: '0',
			last_vacuum: null,
			last_autovacuum: null,
			vacuum_count: '0',
			autovacuum_count: '0',
		})),
		plannerSettings: requiredPlannerSettings.map((name) => ({name, setting: 'contract-value', source: 'default'})),
		pgStatStatements: {
			status: 'unavailable',
			extensionInstalled: false,
			viewAvailable: false,
			extensionVersion: null,
			statsReset: null,
		},
	};
}

function runtimeIdentityFixture({capturedAt = '2026-07-14T00:00:00.000Z'} = {}) {
	const idCharacters = {app: 'a', postgres: 'b', redis: 'c'};
	const imageCharacters = {app: 'd', postgres: 'e', redis: 'f'};
	const container = (service) => ({
		id: `sha256:${idCharacters[service].repeat(64)}`,
		imageId: `sha256:${imageCharacters[service].repeat(64)}`,
		imageRef: `faithlog/${service}:contract`,
		startedAt: '2026-07-13T23:00:00.000Z',
		composeProject: 'contract-project',
		composeService: service,
		composeConfigHash: `sha256:${service}-config`,
		name: `faithlog-latest-${service}`,
		publishedPorts: {},
	});
	return {
		capturedAt,
		containers: {
			app: {
				...container('app'),
				publishedPorts: {'8080/tcp': [{HostIp: '127.0.0.1', HostPort: '28080'}]},
			},
			postgres: container('postgres'),
			redis: container('redis'),
		},
		postgres: {
			database: 'faithlog',
			serverAddress: '172.18.0.2',
			serverPort: 5432,
			serverVersion: '17.5',
			systemIdentifier: 'contract-postgres-system',
			postmasterStartedAt: '2026-07-13T23:00:05.000Z',
			expectedRoleMatched: true,
			flyway: {
				latestVersion: '11',
				latestScript: 'V11__secure_supabase_data_api.sql',
				latestSuccess: true,
			},
			rls: {
				requiredTables: [
					'campus_members', 'campuses', 'charge_items', 'poll_responses',
					'polls', 'users', 'weekly_devotion_records',
				],
				allEnabled: true,
				anyForced: false,
				policyCount: 0,
				allOwnedByCurrentUser: true,
			},
		},
		redis: {
		runId: '0123456789abcdef0123456789abcdef01234567',
			serverVersion: '7.4.0',
			serverPort: 6379,
			uptimeSeconds: '100',
		},
	};
}

function dockerResourceFixture({mode = 'small', boundary = 'before'} = {}) {
	const idCharacters = {app: 'a', postgres: 'b', redis: 'c'};
	return ['app', 'postgres', 'redis'].map((component, index) => ({
		datasetMode: mode,
		boundary,
		sampledAt: boundary === 'before' ? '2026-07-14T00:00:10.000Z' : '2026-07-14T00:00:55.000Z',
		sampleSequence: 1,
		samplingCadence: 'one-no-stream-snapshot-per-boundary',
		stats: {
			ID: `sha256:${idCharacters[component].repeat(64)}`,
			Name: `faithlog-latest-${component}`,
			CPUPerc: `${index + 1}.25%`,
			MemUsage: `${64 * (index + 1)}MiB / 1GiB`,
			MemPerc: `${6.25 * (index + 1)}%`,
		},
	}));
}

function runRuntimeContinuityValidator(temporaryDirectory, initial, before, after, label) {
	const initialPath = path.join(temporaryDirectory, `${label}-initial.json`);
	const beforePath = path.join(temporaryDirectory, `${label}-before.json`);
	const afterPath = path.join(temporaryDirectory, `${label}-after.json`);
	const outputPath = path.join(temporaryDirectory, `${label}-output.json`);
	fs.writeFileSync(initialPath, JSON.stringify(initial));
	fs.writeFileSync(beforePath, JSON.stringify(before));
	fs.writeFileSync(afterPath, JSON.stringify(after));
	const result = runNode(files.runtimeContinuityValidator, initialPath, beforePath, afterPath, outputPath);
	return {
		result,
		output: fs.existsSync(outputPath) ? JSON.parse(fs.readFileSync(outputPath, 'utf8')) : {},
	};
}

function runDbWindowValidator(temporaryDirectory, before, after, externalActivity, label) {
	const beforePath = path.join(temporaryDirectory, `${label}-before.json`);
	const afterPath = path.join(temporaryDirectory, `${label}-after.json`);
	const outputPath = path.join(temporaryDirectory, `${label}-output.json`);
	fs.writeFileSync(beforePath, JSON.stringify(before));
	fs.writeFileSync(afterPath, JSON.stringify(after));
	const result = runNode(files.dbWindowValidator, beforePath, afterPath, externalActivity, outputPath);
	return {
		result,
		output: fs.existsSync(outputPath) ? JSON.parse(fs.readFileSync(outputPath, 'utf8')) : {},
	};
}

function runDockerResourceValidator(temporaryDirectory, rows, identity, mode, boundary, label) {
	const rawPath = path.join(temporaryDirectory, `${label}-resources.jsonl`);
	const identityPath = path.join(temporaryDirectory, `${label}-identity.json`);
	const outputPath = path.join(temporaryDirectory, `${label}-output.json`);
	fs.writeFileSync(rawPath, `${rows.map((row) => typeof row === 'string' ? row : JSON.stringify(row)).join('\n')}\n`);
	fs.writeFileSync(identityPath, JSON.stringify(identity));
	const result = runNode(files.dockerResourceValidator, rawPath, identityPath, mode, boundary, outputPath);
	return {
		result,
		output: fs.existsSync(outputPath) ? JSON.parse(fs.readFileSync(outputPath, 'utf8')) : {},
	};
}

function createFakeRunnerHarness() {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-runner-'));
	const fakeBin = path.join(temporaryDirectory, 'bin');
	const fakeLog = path.join(temporaryDirectory, 'commands.log');
	const tokenCountPath = path.join(temporaryDirectory, 'token-count');
	const dbCounterCountPath = path.join(temporaryDirectory, 'db-counter-count');
	const fakeClockPath = path.join(temporaryDirectory, 'clock');
	const fakeMeasuredRanPath = path.join(temporaryDirectory, 'measured-ran');
	const fakeSecondModePath = path.join(temporaryDirectory, 'second-mode');
	const fixtureNamespace = `ISSUE_199_CONTRACT_${process.pid}_${path.basename(temporaryDirectory)}`;
	const generatedReport = path.join(issueRoot, 'reports', 'CONTRACT_DATASET');
	const composeProject = `contract-${process.pid}-${path.basename(temporaryDirectory).replace(/[^A-Za-z0-9._-]/g, '-')}`;
	fs.mkdirSync(fakeBin);
	fs.writeFileSync(fakeClockPath, '0');

	const modes = {};
	for (const mode of ['empty', 'small']) {
		modes[mode] = {
			mode,
			fixtureRunId: `${fixtureNamespace}_${mode}`,
			campusId: 101,
			isolationCampusId: 102,
			weekStartDate: '2026-07-13',
			expected: expectedFixture(),
		};
	}
	const manifestPath = path.join(temporaryDirectory, 'manifest.json');
	fs.writeFileSync(manifestPath, JSON.stringify({
		schemaVersion: 2,
		issue: 199,
		currentDevelopBase: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		datasetId: 'CONTRACT_DATASET',
		fixtureNamespace: {namespaceId: fixtureNamespace, preparedAt: '2020-01-01T00:00:00.000Z', expiresAt: '2030-01-01T00:00:00.000Z', immutable: true},
		runtimeTarget: {
			app: {service: 'app', containerPort: 8080, imageId: `sha256:${'d'.repeat(64)}`, imageRef: 'faithlog/app:contract',
				sourceProvenance: {sourceWorktree: '/private/tmp/FaithLog-perf-206-deploy',
					revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97', apiContractSha256: '2'.repeat(64)}},
			postgres: {service: 'postgres', containerPort: 5432, imageId: `sha256:${'e'.repeat(64)}`, imageRef: 'faithlog/postgres:contract'},
			redis: {service: 'redis', containerPort: 6379, imageId: `sha256:${'f'.repeat(64)}`, imageRef: 'faithlog/redis:contract'},
		},
		modes,
	}));

	const fakeNode = path.join(fakeBin, 'node');
	fs.writeFileSync(fakeNode, `#!/usr/bin/env bash
printf 'node:%s\\n' "$*" >> "$FAKE_LOG"
case "$1" in
  *prepare-runtime-token.mjs)
    count=0
    [[ -f "$FAKE_TOKEN_COUNT_PATH" ]] && count="$(<"$FAKE_TOKEN_COUNT_PATH")"
    count=$((count + 1))
    printf '%s' "$count" > "$FAKE_TOKEN_COUNT_PATH"
    clock="$(<"$FAKE_CLOCK_PATH")"
	    purpose="\${TOKEN_PURPOSE:-warmup}"
	    if [[ "$DATASET_MODES" == small ]]; then : > "$FAKE_SECOND_MODE_PATH"; fi
    ttl="\${FAKE_WARMUP_TOKEN_TTL_SECONDS:-1800}"
    if [[ "$purpose" == measured ]]; then ttl="\${FAKE_MEASURED_TOKEN_TTL_SECONDS:-1800}"; fi
    exp=$((FAKE_EPOCH_BASE + clock + ttl))
    payload="$(${shellQuote(process.execPath)} -e 'process.stdout.write(Buffer.from(JSON.stringify({exp:Number(process.argv[1])})).toString("base64url"))' "$exp")"
    printf 'token:%s:%s:%s:%s\\n' "$DATASET_MODES" "$purpose" "$count" "$clock" >> "$FAKE_LOG"
    printf 'e30.%s.contract-signature\\n' "$payload"
    exit 0
    ;;
  *verify-summary.mjs) printf '{"status":"api-correctness-verified"}\\n'; exit 0 ;;
  *validate-source-image-provenance.mjs)
    printf '%s\\n' '{"schemaVersion":1,"proofMode":"clean-detached-checkout-image-created-after-checkout","sourceWorktree":"/private/tmp/FaithLog-perf-206-deploy","composeWorkingDir":"/private/tmp/FaithLog-perf-206-deploy","revision":"6796ed146244d8f3f5b5dd7048ebe16865084a97","detached":true,"clean":true,"checkoutAt":"2026-07-16T13:20:28+09:00","imageId":"sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","imageCreatedAt":"2026-07-16T04:22:48.810414883Z","apiContractSha256":"2222222222222222222222222222222222222222222222222222222222222222","limitation":"image-alone-revision-label-unavailable"}' > "$7"
    exit 0
    ;;
esac
exec ${shellQuote(process.execPath)} "$@"
`);
	const fakeDocker = path.join(fakeBin, 'docker');
	fs.writeFileSync(fakeDocker, `#!/usr/bin/env bash
case "$1" in
	  image)
	    if [[ "$2" == inspect && "$*" == *'.Created'* ]]; then
	      printf '%s\\n' '2026-07-16T04:22:48.810414883Z'
	      exit 0
	    fi
	    ;;
	  inspect)
	    printf 'docker:inspect:%s\\n' "\${!#}" >> "$FAKE_LOG"
	    container="\${!#}"
	    if [[ "$*" == *issue199-runtime-identity* ]]; then
	      if [[ "$container" == *postgres* ]]; then service=postgres
	      elif [[ "$container" == *redis* ]]; then service=redis
	      else service=app
	      fi
	      if [[ "$service" == app ]]; then id='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'; image_id='sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
	      elif [[ "$service" == postgres ]]; then id='sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'; image_id='sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee'
	      else id='sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'; image_id='sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff'; fi
	      started_at='2026-07-13T23:00:00.000Z'
	      published_ports='{}'
	      if [[ "$service" == app ]]; then published_ports='{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"28080"}]}'; fi
	      if [[ "\${FAKE_RUNTIME_IDENTITY_MODE:-}" == replace-before-initial && "$service" == app ]]; then
	        id='sha256:9999999999999999999999999999999999999999999999999999999999999999'
	        image_id='sha256:8888888888888888888888888888888888888888888888888888888888888888'
	        started_at='2026-07-14T00:00:15.000Z'
	        published_ports='{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"28081"}]}'
	      elif [[ "\${FAKE_RUNTIME_IDENTITY_MODE:-}" == replace-after && "$service" == app && -f "$FAKE_MEASURED_RAN_PATH" ]]; then
	        id='sha256:9999999999999999999999999999999999999999999999999999999999999999'
	        image_id='sha256:8888888888888888888888888888888888888888888888888888888888888888'
	        started_at='2026-07-14T00:00:30.000Z'
	      elif [[ "\${FAKE_RUNTIME_IDENTITY_MODE:-}" == replace-second-mode && "$service" == app && -f "$FAKE_SECOND_MODE_PATH" ]]; then
	        id='sha256:9999999999999999999999999999999999999999999999999999999999999999'
	        image_id='sha256:8888888888888888888888888888888888888888888888888888888888888888'
	        started_at='2026-07-14T00:00:45.000Z'
	      fi
	      name="faithlog-latest-\${service}"
	      printf '{"id":"%s","imageId":"%s","imageRef":"faithlog/%s:contract","startedAt":"%s","composeProject":"%s","composeService":"%s","composeConfigHash":"sha256:%s-config","name":"%s","publishedPorts":%s}\\n' \\
	        "$id" "$image_id" "$service" "$started_at" "$FAKE_COMPOSE_PROJECT" "$service" "$service" "$name" "$published_ports"
	    elif [[ "$*" == *Config.Env* ]]; then
	      printf '%s\n' '["SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/runtime-only-db-name","SPRING_DATASOURCE_USERNAME=runtime-only-db-user","SPRING_DATASOURCE_PASSWORD=runtime-only-db-secret","SPRING_DATA_REDIS_HOST=redis","SPRING_DATA_REDIS_PORT=6379"]'
	    elif [[ "$*" == *NetworkSettings.Ports* ]]; then
      printf '%s\\n' "$FAKE_APP_PORTS_JSON"
	    elif [[ "$*" == *com.docker.compose.project.working_dir* ]]; then
	      printf '%s\\n' '/private/tmp/FaithLog-perf-206-deploy'
	    elif [[ "$*" == *com.docker.compose.project* ]]; then
      if [[ "\${FAKE_LABEL_MODE:-}" == mismatch && "$container" == *redis* ]]; then
        printf '%s-other\\n' "$FAKE_COMPOSE_PROJECT"
      else
        printf '%s\\n' "$FAKE_COMPOSE_PROJECT"
      fi
    elif [[ "$*" == *com.docker.compose.service* ]]; then
      if [[ "$container" == *postgres* ]]; then service=postgres
      elif [[ "$container" == *redis* ]]; then service=redis
      else service=app
      fi
      if [[ "\${FAKE_SERVICE_MODE:-}" == mismatch && "$service" == redis ]]; then service=wrong-redis; fi
      printf '%s\\n' "$service"
    fi
    exit 0
    ;;
  stats)
    printf 'docker:stats\\n' >> "$FAKE_LOG"
    if [[ "\${FAKE_DOCKER_RESOURCE_MODE:-}" == malformed ]]; then
      printf '{not-json\\n'
      exit 0
    fi
    for component in app postgres redis; do
      evidence_mode="$RESOURCE_DATASET_MODE"
      if [[ "\${FAKE_DOCKER_RESOURCE_MODE:-}" == mixed-mode && "$component" == postgres ]]; then evidence_mode=thousand; fi
      if [[ "$component" == app ]]; then full_id='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
      elif [[ "$component" == postgres ]]; then full_id='sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
      else full_id='sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'; fi
      printf '{"datasetMode":"%s","boundary":"%s","stats":{"ID":"%s","Name":"faithlog-latest-%s","CPUPerc":"1.25%%","MemUsage":"64MiB / 1GiB","MemPerc":"6.25%%"}}\\n' \\
        "$evidence_mode" "$RESOURCE_BOUNDARY" "$full_id" "$component"
    done
    exit 0
    ;;
  exec)
    if [[ "$*" == *redis-cli* ]]; then
      printf 'redis_version:7.4.0\\r\\nrun_id:0123456789abcdef0123456789abcdef01234567\\r\\ntcp_port:6379\\r\\nuptime_in_seconds:100\\r\\n'
      exit 0
    fi
    sql="$(</dev/stdin)"
    if [[ "$sql" == *issue199:evidence=correctness* ]]; then
      printf 'docker:db-correctness\\n' >> "$FAKE_LOG"
      boundary="$(printf '%s' "$*" | sed -n 's/.*evidence_boundary=\\([^ ]*\\).*/\\1/p')"
      FAKE_BOUNDARY="$boundary" ${shellQuote(process.execPath)} -e 'const v=JSON.parse(process.env.FAKE_DB_EVIDENCE_JSON); process.stdout.write(JSON.stringify({evidenceBoundary:process.env.FAKE_BOUNDARY,...v})+"\\n")'
	    elif [[ "$sql" == *issue199:evidence=counters* ]]; then
      printf 'docker:db-counters\\n' >> "$FAKE_LOG"
      count=0
      [[ -f "$FAKE_DB_COUNTER_COUNT_PATH" ]] && count="$(<"$FAKE_DB_COUNTER_COUNT_PATH")"
      count=$((count + 1))
      printf '%s' "$count" > "$FAKE_DB_COUNTER_COUNT_PATH"
      if (( count % 2 == 1 )); then printf '%s\\n' "$FAKE_DB_COUNTER_BEFORE_JSON"
      else printf '%s\\n' "$FAKE_DB_COUNTER_AFTER_JSON"
      fi
	    elif [[ "$sql" == *issue199:evidence=runtime-identity* ]]; then
	      printf 'docker:db-runtime-identity\\n' >> "$FAKE_LOG"
	      printf '{"database":"faithlog","serverAddress":"172.18.0.2","serverPort":5432,"serverVersion":"17.5","systemIdentifier":"contract-postgres-system","postmasterStartedAt":"2026-07-13T23:00:05.000Z","expectedRoleMatched":true,"flyway":{"latestVersion":"11","latestScript":"V11__secure_supabase_data_api.sql","latestSuccess":true},"rls":{"requiredTables":["campus_members","campuses","charge_items","poll_responses","polls","users","weekly_devotion_records"],"allEnabled":true,"anyForced":false,"policyCount":0,"allOwnedByCurrentUser":true}}\\n'
	    else
      printf 'docker:db-context\\n' >> "$FAKE_LOG"
      printf 'context evidence\\n'
    fi
    exit 0
    ;;
esac
exit 1
`);
	const fakeK6 = path.join(fakeBin, 'k6');
	fs.writeFileSync(fakeK6, `#!/usr/bin/env bash
printf 'k6:%s:%s\\n' "$DATASET_MODE" "$PHASE" >> "$FAKE_LOG"
[[ -n "$PERF_ACCESS_TOKEN" ]]
[[ -z "\${PERF_ADMIN_PASSWORD:-}" ]]
[[ -z "\${PERF_DB_PASSWORD:-}" ]]
while [[ $# -gt 0 ]]; do
  if [[ "$1" == '--summary-export' ]]; then summary_path="$2"; shift 2; else shift; fi
done
printf '%s\\n' "$FAKE_K6_SUMMARY_JSON" > "$summary_path"
	if [[ "$PHASE" == warmup ]]; then
  clock="$(<"$FAKE_CLOCK_PATH")"
  printf '%s' "$((clock + 1901))" > "$FAKE_CLOCK_PATH"
	else
	  : > "$FAKE_MEASURED_RAN_PATH"
	fi
`);
	const fakeDate = path.join(fakeBin, 'date');
	fs.writeFileSync(fakeDate, `#!/usr/bin/env bash
if [[ "$1" == '+%s' ]]; then
  clock="$(<"$FAKE_CLOCK_PATH")"
  printf '%s\\n' "$((FAKE_EPOCH_BASE + clock))"
else
  exec /bin/date "$@"
fi
`);
	for (const executable of [fakeNode, fakeDocker, fakeK6, fakeDate]) {
		fs.chmodSync(executable, 0o755);
	}

	const summary = {
		metrics: {
			admin_dashboard_duration: {'p(50)': 1, 'p(95)': 2, 'p(99)': 3, max: 4},
			admin_dashboard_requests: {count: 2, rate: 2},
			admin_dashboard_failure_rate: {rate: 0},
		},
	};
	const baseEnvironment = {
		...process.env,
		PATH: `${fakeBin}:${process.env.PATH}`,
		FAKE_LOG: fakeLog,
		FAKE_TOKEN_COUNT_PATH: tokenCountPath,
		FAKE_DB_COUNTER_COUNT_PATH: dbCounterCountPath,
			FAKE_CLOCK_PATH: fakeClockPath,
			FAKE_MEASURED_RAN_PATH: fakeMeasuredRanPath,
			FAKE_SECOND_MODE_PATH: fakeSecondModePath,
		FAKE_EPOCH_BASE: '1783980000',
		FAKE_COMPOSE_PROJECT: composeProject,
		FAKE_APP_PORTS_JSON: JSON.stringify({'8080/tcp': [{HostIp: '127.0.0.1', HostPort: '28080'}]}),
		FAKE_DB_EVIDENCE_JSON: JSON.stringify(expectedFixture()),
		FAKE_DB_COUNTER_BEFORE_JSON: JSON.stringify(dbWindowFixture()),
		FAKE_DB_COUNTER_AFTER_JSON: JSON.stringify(dbWindowFixture({after: true})),
		FAKE_K6_SUMMARY_JSON: JSON.stringify(summary),
		INPUT_MANIFEST: manifestPath,
		BASE_URL: 'http://127.0.0.1:28080',
		APP_CONTAINER: 'faithlog-latest-app',
		POSTGRES_CONTAINER: 'faithlog-latest-postgres',
		REDIS_CONTAINER: 'faithlog-latest-redis',
		WARMUP_VUS: '1',
		WARMUP_DURATION: '1s',
		MEASURED_VUS: '1',
		MEASURED_DURATION: '1s',
		TOKEN_EXPIRY_SAFETY_SECONDS: '10',
		FIXTURE_EXPIRY_SAFETY_SECONDS: '60',
		EXTERNAL_ACTIVITY: 'none',
		PERF_ADMIN_EMAIL: 'runtime-only@example.com',
		PERF_ADMIN_PASSWORD: 'runtime-only-secret',
		PERF_DB_USER: 'runtime-only-db-user',
		PERF_DB_PASSWORD: 'runtime-only-db-secret',
		PERF_DB_NAME: 'runtime-only-db-name',
	};

	return {
		composeProject,
		generatedReport,
		reportPath(mode, ...segments) {
			return path.join(generatedReport, modes[mode].fixtureRunId, mode, ...segments);
		},
		tokenCountPath,
		run(environment = {}) {
			const childEnvironment = {...baseEnvironment, ...environment};
			for (const [name, value] of Object.entries(childEnvironment)) {
				if (value === undefined) delete childEnvironment[name];
			}
			return spawnSync('bash', [files.runner], {
				encoding: 'utf8',
				env: childEnvironment,
			});
		},
		log() {
			if (!fs.existsSync(fakeLog)) return [];
			return fs.readFileSync(fakeLog, 'utf8').trim().split('\n').filter(Boolean);
		},
		cleanup() {
			fs.rmSync(generatedReport, {recursive: true, force: true});
			try {
				fs.rmdirSync(path.dirname(generatedReport));
			} catch (error) {
				if (error.code !== 'ENOENT' && error.code !== 'ENOTEMPTY') throw error;
			}
			fs.rmSync(temporaryDirectory, {recursive: true, force: true});
		},
	};
}
