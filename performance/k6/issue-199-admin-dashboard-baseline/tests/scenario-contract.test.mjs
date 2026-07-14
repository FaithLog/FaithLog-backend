import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const issueRoot = path.resolve(here, '..');
const repositoryRoot = path.resolve(issueRoot, '../../..');

const files = {
	contract: path.join(issueRoot, 'scenario-contract.json'),
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
	dbCounters: path.join(issueRoot, 'collect-db-counters.sql'),
	dbCorrectness: path.join(issueRoot, 'collect-correctness-evidence.sql'),
	readme: path.join(issueRoot, 'README.md'),
	reportsIgnore: path.join(issueRoot, 'reports/.gitignore'),
};

const read = (file) => fs.readFileSync(file, 'utf8');

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
	assert.deepEqual(contract.authorization.denied, ['plain-member', 'other-campus-admin', 'global-manager-only']);
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
	assert.deepEqual(contract.dataset.modes, ['empty', 'small', 'thousand']);
	assert.deepEqual(contract.dataset.identifiers, ['datasetId', 'fixtureRunId']);
	assert.equal(contract.dataset.thousandMemberCount, 1000);
	assert.deepEqual(contract.dataset.fixtureDomains, ['devotion', 'penalty', 'coffee', 'meal', 'poll', 'prayer']);
	assert.equal(contract.dataset.seedPolicy, 'reference-only');

	assert.equal(manifest.issue, 199);
	assert.equal(manifest.datasetId, 'PERFORMANCE_SHARED_1000_EXAMPLE');
	assert.deepEqual(manifest.runtimeTarget, {
		app: {service: 'app', containerPort: 8080},
		postgres: {service: 'postgres'},
		redis: {service: 'redis'},
	});
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
});

test('runner separates warmup and measured phases, serializes modes, records actual Compose labels and resources', () => {
	const source = read(files.runner);
	const tokenSource = read(files.runtimeToken);

	assert.match(source, /BASE_URL="\$\{BASE_URL:\?/);
	assert.match(source, /DATASET_MODES="\$\{DATASET_MODES:\?/);
	assert.match(source, /APP_CONTAINER="\$\{APP_CONTAINER:\?/);
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
	assert.match(source, /Report directory already exists/);
	assert.match(source, /PHASE=warmup/);
	assert.match(source, /PHASE=measured/);
	assert.ok(source.indexOf('PHASE=warmup') < source.indexOf('PHASE=measured'));
	assert.match(source, /warmup\/summary\.json/);
	assert.match(source, /measured\/summary\.json/);
	assert.match(source, /docker inspect/);
	assert.match(source, /com\.docker\.compose\.project/);
	assert.match(source, /com\.docker\.compose\.service/);
	assert.match(source, /docker stats --no-stream/);
	assert.match(source, /EXTERNAL_ACTIVITY/);
	assert.doesNotMatch(source, /docker compose .*\b(?:up|down|build|restart|rm)\b/);
	assert.doesNotMatch(source, /docker (?:builder |system |image |volume )?prune/);
	assert.doesNotMatch(source, /&\s*$/m);
});

test('DB evidence is read-only and captures counters, query evidence, analyze and planner state', () => {
	const contextSql = read(files.dbEvidence);
	const counterSql = read(files.dbCounters);
	const correctnessSql = read(files.dbCorrectness);
	const sql = `${contextSql}\n${counterSql}\n${correctnessSql}`;

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
	assert.match(sql, /n_mod_since_analyze/);
	assert.match(sql, /pg_settings/);
	assert.match(sql, /poll_response_count/);
	assert.match(sql, /missing_response_count/);
	assert.doesNotMatch(sql, /\b(?:INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE|VACUUM|ANALYZE)\b/i);
	assert.match(counterSql, /issue199:evidence=counters/);
	assert.match(counterSql, /pg_stat_activity/);
	assert.match(counterSql, /application_name/);
	assert.match(counterSql, /stats_reset/);
	assert.match(counterSql, /plannerSettings/);
	assert.doesNotMatch(
		counterSql,
		/\b(?:FROM|JOIN)\s+(?:users|campuses|campus_members|weekly_devotion_records|charge_items|polls|poll_responses)\b/i,
	);
	assert.match(correctnessSql, /issue199:evidence=correctness/);
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
		fs.writeFileSync(validPath, JSON.stringify(validSummary));
		fs.writeFileSync(failedPath, JSON.stringify({
			...validSummary,
			metrics: {
				...validSummary.metrics,
				admin_dashboard_failure_rate: {rate: 0.01},
			},
		}));
		fs.writeFileSync(missingPath, JSON.stringify({metrics: {admin_dashboard_failure_rate: {rate: 0}}}));

		assert.equal(runNode(files.summaryValidator, validPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, failedPath).status, 0);
		assert.notEqual(runNode(files.summaryValidator, missingPath).status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('runner fake execution refreshes the token per mode and keeps bootstrap outside each measured DB counter window', () => {
	const harness = createFakeRunnerHarness();
	try {
		const result = harness.run({DATASET_MODES: 'empty,small'});
		assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
		const log = harness.log();
		assert.equal(Number(fs.readFileSync(harness.tokenCountPath, 'utf8')), 4);
		for (const [mode, counterOccurrence] of [
			['empty', 1],
			['small', 3],
		]) {
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

test('DB window validator rejects missing or regressed counters and produces semantic deltas', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-window-'));
	try {
		const before = dbWindowFixture();
		const after = dbWindowFixture({after: true});
		const valid = runDbWindowValidator(temporaryDirectory, before, after, 'none', 'valid');
		assert.equal(valid.result.status, 0, valid.result.stderr);
		assert.equal(valid.output.adoptable, true);
		assert.equal(valid.output.tableDeltas.users.seq_scan, 1);

		const missing = structuredClone(after);
		missing.tables.pop();
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, missing, 'none', 'missing').result.status, 0);

		const regressed = structuredClone(after);
		regressed.tables[0].seq_scan = before.tables[0].seq_scan - 1;
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, regressed, 'none', 'regressed').result.status, 0);

		const reset = structuredClone(after);
		reset.database.stats_reset = '2026-07-14T01:00:00.000Z';
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, reset, 'none', 'reset').result.status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, {recursive: true, force: true});
	}
});

test('DB window validator blocks autoanalyze changes, external sessions, and declared external requests', () => {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-db-activity-'));
	try {
		const before = dbWindowFixture();
		assert.equal(
			runDbWindowValidator(temporaryDirectory, before, dbWindowFixture({after: true}), 'none', 'control').result.status,
			0,
		);
		const autoanalyzed = dbWindowFixture({after: true});
		autoanalyzed.tables[0].autoanalyze_count += 1;
		autoanalyzed.tables[0].last_autoanalyze = '2026-07-14T00:01:00.000Z';
		assert.notEqual(runDbWindowValidator(temporaryDirectory, before, autoanalyzed, 'none', 'autoanalyze').result.status, 0);

		const externalSession = dbWindowFixture({after: true});
		externalSession.externalActiveSessions = 1;
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

test('runner refuses an existing mode report directory instead of mixing stale evidence', () => {
	const harness = createFakeRunnerHarness();
	fs.mkdirSync(path.join(harness.generatedReport, 'empty'), {recursive: true});
	fs.writeFileSync(path.join(harness.generatedReport, 'empty', 'stale.json'), '{}');
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
		fs.writeFileSync(validEvidencePath, JSON.stringify(validEvidence));
		fs.writeFileSync(redistributedEvidencePath, JSON.stringify(redistributedEvidence));

		assert.equal(runNode(files.dbCorrectnessValidator, manifestPath, 'small', validEvidencePath).status, 0);
		assert.notEqual(
			runNode(files.dbCorrectnessValidator, manifestPath, 'small', redistributedEvidencePath).status,
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
		externalActiveSessions: 0,
		observerOverhead: {
			databaseWideCountersIncludeSnapshotTransaction: true,
			databaseWideDeltaIsExactQueryCount: false,
			appTableCountersReadApplicationTables: false,
		},
		database: {
			datname: 'faithlog',
			stats_reset: '2026-07-13T00:00:00.000Z',
			xact_commit: 10 + increment,
			xact_rollback: 0,
			blks_read: 1,
			blks_hit: 100 + increment,
			tup_returned: 1000 + increment,
			tup_fetched: 100 + increment,
			temp_files: 0,
			temp_bytes: 0,
			deadlocks: 0,
		},
		tables: requiredDbTables.map((relname, index) => ({
			relname,
			seq_scan: index + 10 + increment,
			seq_tup_read: index + 100 + increment,
			idx_scan: index + 20 + increment,
			idx_tup_fetch: index + 30 + increment,
			n_live_tup: index + 1000,
			n_dead_tup: 0,
			n_mod_since_analyze: 0,
			last_analyze: null,
			last_autoanalyze: null,
			analyze_count: 0,
			autoanalyze_count: 0,
		})),
		plannerSettings: requiredPlannerSettings.map((name) => ({name, setting: 'contract-value', source: 'default'})),
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

function createFakeRunnerHarness() {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-199-runner-'));
	const fakeBin = path.join(temporaryDirectory, 'bin');
	const fakeLog = path.join(temporaryDirectory, 'commands.log');
	const tokenCountPath = path.join(temporaryDirectory, 'token-count');
	const dbCounterCountPath = path.join(temporaryDirectory, 'db-counter-count');
	const fakeClockPath = path.join(temporaryDirectory, 'clock');
	const fixtureRunId = `ISSUE_199_CONTRACT_${process.pid}_${path.basename(temporaryDirectory)}`;
	const generatedReport = path.join(issueRoot, 'reports', 'CONTRACT_DATASET', fixtureRunId);
	const composeProject = `contract-${process.pid}-${path.basename(temporaryDirectory).replace(/[^A-Za-z0-9._-]/g, '-')}`;
	fs.mkdirSync(fakeBin);
	fs.writeFileSync(fakeClockPath, '0');

	const modes = {};
	for (const mode of ['empty', 'small']) {
		modes[mode] = {
			mode,
			fixtureRunId,
			campusId: 101,
			isolationCampusId: 102,
			weekStartDate: '2026-07-13',
			expected: expectedFixture(),
		};
	}
	const manifestPath = path.join(temporaryDirectory, 'manifest.json');
	fs.writeFileSync(manifestPath, JSON.stringify({
		issue: 199,
		datasetId: 'CONTRACT_DATASET',
		runtimeTarget: {
			app: {service: 'app', containerPort: 8080},
			postgres: {service: 'postgres'},
			redis: {service: 'redis'},
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
    ttl=1800
    if [[ "$purpose" == measured ]]; then ttl="\${FAKE_MEASURED_TOKEN_TTL_SECONDS:-1800}"; fi
    exp=$((FAKE_EPOCH_BASE + clock + ttl))
    payload="$(${shellQuote(process.execPath)} -e 'process.stdout.write(Buffer.from(JSON.stringify({exp:Number(process.argv[1])})).toString("base64url"))' "$exp")"
    printf 'token:%s:%s:%s:%s\\n' "$DATASET_MODES" "$purpose" "$count" "$clock" >> "$FAKE_LOG"
    printf 'e30.%s.contract-signature\\n' "$payload"
    exit 0
    ;;
  *verify-summary.mjs) printf '{"status":"api-correctness-verified"}\\n'; exit 0 ;;
esac
exec ${shellQuote(process.execPath)} "$@"
`);
	const fakeDocker = path.join(fakeBin, 'docker');
	fs.writeFileSync(fakeDocker, `#!/usr/bin/env bash
case "$1" in
  inspect)
    container="\${!#}"
    if [[ "$*" == *NetworkSettings.Ports* ]]; then
      printf '%s\\n' "$FAKE_APP_PORTS_JSON"
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
    printf '{"Name":"contract","CPUPerc":"0%%","MemUsage":"0B / 0B"}\\n'
    exit 0
    ;;
  exec)
    sql="$(</dev/stdin)"
    if [[ "$sql" == *issue199:evidence=correctness* ]]; then
      printf 'docker:db-correctness\\n' >> "$FAKE_LOG"
      printf '%s\\n' "$FAKE_DB_EVIDENCE_JSON"
    elif [[ "$sql" == *issue199:evidence=counters* ]]; then
      printf 'docker:db-counters\\n' >> "$FAKE_LOG"
      count=0
      [[ -f "$FAKE_DB_COUNTER_COUNT_PATH" ]] && count="$(<"$FAKE_DB_COUNTER_COUNT_PATH")"
      count=$((count + 1))
      printf '%s' "$count" > "$FAKE_DB_COUNTER_COUNT_PATH"
      if (( count % 2 == 1 )); then printf '%s\\n' "$FAKE_DB_COUNTER_BEFORE_JSON"
      else printf '%s\\n' "$FAKE_DB_COUNTER_AFTER_JSON"
      fi
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
		WARMUP_VUS: '1',
		WARMUP_DURATION: '1s',
		MEASURED_VUS: '1',
		MEASURED_DURATION: '1s',
		TOKEN_EXPIRY_SAFETY_SECONDS: '10',
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
		tokenCountPath,
		run(environment = {}) {
			return spawnSync('bash', [files.runner], {
				encoding: 'utf8',
				env: {...baseEnvironment, ...environment},
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
