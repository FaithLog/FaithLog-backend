import assert from 'node:assert/strict';
import fs from 'node:fs';
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
	const login = source.indexOf("name: 'frontend_login'");
	const currentUser = source.indexOf("name: 'frontend_users_me'");
	const campuses = source.indexOf("name: 'frontend_campuses_me'");
	const summary = source.indexOf("name: 'admin_dashboard_summary'");

	assert.ok(login >= 0);
	assert.ok(currentUser > login);
	assert.ok(campuses > login);
	assert.ok(summary > currentUser);
	assert.ok(summary > campuses);
	assert.match(source, /http\.batch\(/);
	assert.match(source, /\/api\/v1\/users\/me/);
	assert.match(source, /\/api\/v1\/campuses\/me/);
	assert.match(source, /\/api\/v1\/admin\/campuses\/\$\{dataset\.campusId\}\/dashboard\/summary\?weekStartDate=/);
	assert.doesNotMatch(source, /\/members|duty-assignments|prayers\/weeks/);
});

test('scenario exposes required latency, throughput, failure, and exact correctness checks', () => {
	const source = read(files.scenario);
	const contract = JSON.parse(read(files.contract));

	assert.deepEqual(contract.metrics, ['p50', 'p95', 'p99', 'max', 'throughput', 'failureRate']);
	assert.match(source, /new Trend\('admin_dashboard_duration'/);
	assert.match(source, /new Counter\('admin_dashboard_requests'/);
	assert.match(source, /new Rate\('admin_dashboard_failure_rate'/);
	assert.match(source, /summaryTrendStats:\s*\['p\(50\)', 'p\(95\)', 'p\(99\)', 'max'\]/);
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
	assert.match(source, /ADMIN_DASHBOARD_ACCESS_FORBIDDEN/);
	assert.match(source, /fail\(/);
});

test('runner separates warmup and measured phases, serializes modes, records actual Compose labels and resources', () => {
	const source = read(files.runner);

	assert.match(source, /\/tmp\/faithlog-performance-runner\.lock/);
	assert.match(source, /mkdir "\$LOCK_DIR"/);
	assert.match(source, /trap .*rmdir "\$LOCK_DIR"/);
	assert.match(source, /DATASET_MODES:-empty,small,thousand/);
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
	const sql = read(files.dbEvidence);

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
});

test('verifier enforces summary totals, status/category basis, poll response counts and campus isolation', () => {
	const source = read(files.verifier);

	assert.match(source, /process\.env\.PERF_ADMIN_EMAIL/);
	assert.match(source, /process\.env\.PERF_ADMIN_PASSWORD/);
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
	assert.doesNotMatch(source, /password\s*[:=]\s*['"][^'"]+['"]/i);
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
