import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const REQUIRED_FILES = [
	'fixture-contract.mjs',
	'seed-fixture.mjs',
	'shape-fixture.sh',
	'scenario.js',
	'run-baseline.sh',
	'db-table-stats.sql',
	'summarize-run.mjs',
	'README.md',
];

function read(name) {
	const path = join(ROOT, name);
	return existsSync(path) ? readFileSync(path, 'utf8') : '';
}

test('issue #196 keeps every scenario artifact in one independent directory', () => {
	for (const name of REQUIRED_FILES) {
		assert.equal(existsSync(join(ROOT, name)), true, `missing ${name}`);
	}
});

test('fixture contract separates stable dataset identity from one immutable fixture run', async () => {
	const contractPath = join(ROOT, 'fixture-contract.mjs');
	assert.equal(existsSync(contractPath), true, 'missing fixture-contract.mjs');
	const { FIXTURE_CONTRACT } = await import(`${pathToFileURL(contractPath).href}?test=${Date.now()}`);

	assert.equal(FIXTURE_CONTRACT.datasetId, 'issue-196-prayer-poll-list-v1');
	assert.equal(FIXTURE_CONTRACT.fixtureRunIdRequired, true);
	assert.equal(FIXTURE_CONTRACT.primaryCampus.activeMemberCount, 1000);
	assert.equal(FIXTURE_CONTRACT.isolationCampus.activeMemberCount, 50);
	assert.deepEqual(FIXTURE_CONTRACT.prayer, {
		groupCount: 40,
		membersPerGroup: 25,
		submissionCount: 800,
		unsubmittedCount: 200,
	});
	assert.equal(FIXTURE_CONTRACT.polls.optionCount, 5);
	assert.equal(FIXTURE_CONTRACT.polls.responseCount, 800);
	assert.equal(FIXTURE_CONTRACT.polls.missingMemberCount, 200);
	assert.equal(FIXTURE_CONTRACT.polls.commentCount, 200);
	assert.equal(FIXTURE_CONTRACT.polls.templateCount, 40);
	assert.equal(FIXTURE_CONTRACT.polls.optionsPerTemplate, 8);
	assert.deepEqual(FIXTURE_CONTRACT.polls.visibilityCases.map(({ key }) => key), [
		'open',
		'closed_member_visible',
		'closed_admin_only',
		'closed_expired',
		'scheduled_future',
	]);
	assert.equal(FIXTURE_CONTRACT.existingRowsMayBeUpdatedOrDeleted, false);
});

test('k6 scenario exposes exact Prayer and member/admin Poll read modes', () => {
	const scenario = read('scenario.js');
	for (const mode of ['prayer', 'poll-member', 'poll-admin']) {
		assert.match(scenario, new RegExp(`['\"]${mode}['\"]`));
	}
	for (const endpoint of [
		'/api/v1/admin/campuses/${campusId}/prayer-seasons/current',
		'/api/v1/admin/prayer-seasons/${seasonId}/groups',
		'/api/v1/admin/prayer-seasons/${seasonId}/members/assignable',
		'/api/v1/campuses/${campusId}/prayers/weeks/${weekStartDate}',
		'/api/v1/campuses/${campusId}/polls',
		'/api/v1/campuses/${campusId}/polls/${pollId}',
		'/api/v1/campuses/${campusId}/polls/${pollId}/results',
		'/api/v1/campuses/${campusId}/polls/${pollId}/comments',
		'/api/v1/admin/campuses/${campusId}/polls/${pollId}/missing-members',
		'/api/v1/admin/campuses/${campusId}/poll-templates',
		'/api/v1/admin/campuses/${campusId}/poll-templates/${templateId}',
	]) {
		assert.ok(scenario.includes(endpoint), `missing endpoint ${endpoint}`);
	}
	assert.match(scenario, /summaryTrendStats:\s*\[[^\]]*'p\(50\)'[^\]]*'p\(95\)'[^\]]*'p\(99\)'[^\]]*'max'/s);
	assert.match(scenario, /new Trend\(/);
	assert.match(scenario, /new Counter\(/);
	assert.match(scenario, /new Rate\(/);
});

test('correctness checks lock count, ordering, editable, myGroup, isolation, and poll windows', () => {
	const scenario = read('scenario.js');
	for (const marker of [
		'assertAscending',
		'assertDescending',
		'targetMemberCount',
		'submittedCount',
		'myGroupId',
		'editable',
		'isolation',
		'closed_admin_only',
		'closed_expired',
		'scheduled_future',
		'notRespondedCount',
		'responseCount',
		'respondents',
	]) {
		assert.ok(scenario.includes(marker), `missing correctness marker ${marker}`);
	}
});

test('runner serializes endpoint phases and records runtime evidence without Docker lifecycle mutations', () => {
	const runner = read('run-baseline.sh');
	assert.match(runner, /faithlog-performance-global\.lock/);
	assert.match(runner, /prayer[\s\S]*poll-member[\s\S]*poll-admin/);
	assert.match(runner, /ENDPOINT=/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /com\.docker\.compose\.service/);
	assert.match(runner, /EXPECTED_APP_IMAGE/);
	assert.match(runner, /build\/reports\/k6\/issue-196/);
	assert.doesNotMatch(runner, /docker\s+compose\s+(?:up|down|build)|docker\s+(?:system|builder|image|volume)\s+prune/);
	assert.doesNotMatch(runner, /(?:^|\s)(?:source|\.)\s+\.env(?:\s|$)/m);
});

test('DB and log evidence contract is endpoint-scoped and read-only', () => {
	const runner = read('run-baseline.sh');
	const sql = read('db-table-stats.sql');
	const summarizer = read('summarize-run.mjs');

	assert.match(sql, /pg_stat_user_tables/);
	assert.match(sql, /seq_scan/);
	assert.match(sql, /idx_scan/);
	assert.doesNotMatch(sql, /\b(?:insert|update|delete|truncate|alter|drop|create)\b/i);
	assert.match(runner, /org\.hibernate\.SQL/);
	assert.match(runner, /docker\s+logs/);
	assert.match(summarizer, /queriesPerRequest/);
	assert.match(summarizer, /repeatedSql/);
	assert.match(summarizer, /tableCounterDelta/);
	assert.match(summarizer, /scenario-ready/);
});

test('summarizer materializes endpoint latency, throughput, SQL loop, table, and resource evidence', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-contract-'));
	try {
		const endpoint = 'poll_member_list';
		const paths = Object.fromEntries(['summary', 'before', 'after', 'sql', 'resources', 'metadata', 'report']
			.map((name) => [name, join(temporary, `${name}.${name === 'sql' || name === 'resources' ? 'txt' : 'json'}`)]));
		writeFileSync(paths.summary, JSON.stringify({ metrics: {
			endpoint_poll_member_list_duration: { values: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40 } },
			endpoint_poll_member_list_requests: { values: { count: 2, rate: 1.5 } },
			endpoint_poll_member_list_failures: { values: { rate: 0 } },
		} }));
		writeFileSync(paths.before, JSON.stringify({ tables: [{ relname: 'users', seq_scan: 0, seq_tup_read: 0, idx_scan: 0, idx_tup_fetch: 0, n_tup_ins: 0, n_tup_upd: 0, n_tup_del: 0, n_live_tup: 1000 }] }));
		writeFileSync(paths.after, JSON.stringify({ tables: [{ relname: 'users', seq_scan: 4, seq_tup_read: 4000, idx_scan: 0, idx_tup_fetch: 0, n_tup_ins: 0, n_tup_upd: 0, n_tup_del: 0, n_live_tup: 1000 }] }));
		writeFileSync(paths.sql, Array.from({ length: 4 }, (_, index) => `INFO org.hibernate.SQL: select * from users where id=${index + 1}`).join('\n'));
		writeFileSync(paths.resources, [
			'2026-07-14T00:00:00Z\tfaithlog-backend\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\t20.0%\t200MiB / 1GiB\t19.5%',
		].join('\n'));
		writeFileSync(paths.metadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
			runtime: { appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres' },
		}));

		execFileSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.metadata, paths.report]);
		const report = JSON.parse(readFileSync(paths.report, 'utf8'));
		assert.deepEqual(report.http, {
			p50Ms: 10, p95Ms: 20, p99Ms: 30, maxMs: 40,
			throughputPerSecond: 1.5, requestCount: 2, failureRate: 0,
		});
		assert.equal(report.db.queryCount, 4);
		assert.equal(report.db.queriesPerRequest, 2);
		assert.equal(report.db.tableCounterDelta[0].estimatedRowsAfter, 1000);
		assert.equal(report.nPlusOneEvidence.loopSignal[0].count, 4);
		assert.equal(report.resources.length, 2);
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('fixture preparation is create-only outside rows owned by the current fixtureRunId', () => {
	const seed = read('seed-fixture.mjs');
	const shaper = read('shape-fixture.sh');

	assert.match(seed, /FIXTURE_RUN_ID/);
	assert.match(seed, /DATASET_ID/);
	assert.match(seed, /manifest/);
	assert.match(seed, /request\('POST', '\/api\/v1\/auth\/signup'/);
	assert.match(seed, /request\('POST', '\/api\/v1\/campuses\/join'/);
	assert.doesNotMatch(seed, /\/api\/v1\/admin\/campuses\/\$\{campusId\}\/members/);
	assert.doesNotMatch(seed, /method:\s*['\"]DELETE['\"]/);
	assert.doesNotMatch(seed, /\bdelete\s+from\b/i);
	assert.match(shaper, /fixture_run_id/);
	assert.match(shaper, /created fixture rows only/);
	assert.doesNotMatch(shaper, /\bdelete\s+from\b/i);
	assert.doesNotMatch(shaper, /\btruncate\b/i);
	assert.doesNotMatch(seed, /(?:password|token)\s*:\s*['\"][^'\"]+['\"]/i);
});
