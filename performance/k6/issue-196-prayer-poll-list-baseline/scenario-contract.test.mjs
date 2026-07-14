import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
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
	const { FIXTURE_CONTRACT, currentMonday, validateFixtureRunId } = await import(`${pathToFileURL(contractPath).href}?test=${Date.now()}`);

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
	assert.equal(currentMonday(new Date('2026-07-12T15:00:00Z')), '2026-07-13', 'Monday follows Asia/Seoul rather than UTC');
	assert.throws(() => validateFixtureRunId('I196-UPPER'));
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
	assert.match(scenario, /'rate==0'/);
	assert.match(scenario, /poll_member_isolation_campus_detail/);
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
	assert.match(scenario, /try\s*\{[\s\S]*config\.validate\(response\.status, payload\)[\s\S]*\}\s*catch[\s\S]*endpointFailures\.add\(!valid\)/);
	assert.match(scenario, /sameInstant\(poll\.startsAt, expected\.startsAt\)[\s\S]*sameInstant\(poll\.endsAt, expected\.endsAt\)/);
	assert.ok((scenario.match(/sameInstant\(data\.startsAt, expected\.startsAt\)/g) || []).length >= 2,
		'detail and results must compare exact startsAt instants');
	assert.ok((scenario.match(/sameInstant\(data\.endsAt, expected\.endsAt\)/g) || []).length >= 2,
		'detail and results must compare exact endsAt instants');
});

test('runner serializes endpoint phases and records runtime evidence without Docker lifecycle mutations', () => {
	const runner = read('run-baseline.sh');
	assert.match(runner, /faithlog-performance-global\.lock/);
	assert.doesNotMatch(runner, /PERF_GLOBAL_LOCK:-/);
	assert.doesNotMatch(runner, /EXPECTED_APP_IMAGE:-/);
	assert.match(runner, /prayer[\s\S]*poll-member[\s\S]*poll-admin/);
	assert.match(runner, /ENDPOINT=/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /com\.docker\.compose\.service/);
	assert.match(runner, /composeRuntime\.composeProject/);
	assert.match(runner, /composeRuntime\.appImageId/);
	assert.match(runner, /composeRuntime\.targetPort/);
	assert.match(runner, /docker port "\$\{APP_CONTAINER\}" 8080\/tcp/);
	assert.match(runner, /EXPECTED_APP_IMAGE/);
	assert.match(runner, /build\/reports\/k6\/issue-196/);
	assert.match(runner, /new Date\(\)\.toISOString\(\)/);
	assert.match(runner, /snapshot_db_tables "\$\{before_file\}"[\s\S]*log_since="\$\(rfc3339_now\)"[\s\S]*k6 run[\s\S]*log_until="\$\(rfc3339_now\)"[\s\S]*docker logs --since "\$\{log_since\}" --until "\$\{log_until\}"/);
	assert.ok((runner.match(/assert_fixture_windows/g) || []).length >= 4, 'window freshness must be checked globally and before/after every endpoint');
	for (const message of ['OPEN fixture window is stale', 'Member visibility fixture window is stale',
		'Admin visibility fixture window is stale', 'Expired fixture is not beyond the admin window',
		'Scheduled fixture is no longer in the future']) {
		assert.ok(runner.includes(message), `missing window guard ${message}`);
	}
	assert.match(runner, /WINDOW_STATUS_VALUE[\s\S]*fixtureWindowExitStatus/);
	assert.match(runner, /set \+e[\s\S]*docker logs[\s\S]*log_capture_status=\$\?[\s\S]*snapshot_db_tables "\$\{after_file\}"[\s\S]*after_snapshot_status=\$\?[\s\S]*set -e/);
	assert.match(runner, /summarize-run\.mjs[\s\S]*summarize_status=\$\?[\s\S]*if \(\( summarize_status != 0 \)\)/);
	assert.match(runner, /env -u PERF_ADMIN_EMAIL/);
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
			runtime: {
				appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
				k6ExitStatus: 0, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
				logCaptureExitStatus: 0, afterDbSnapshotExitStatus: 0,
			},
		}));

		execFileSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.metadata, paths.report]);
		const report = JSON.parse(readFileSync(paths.report, 'utf8'));
		assert.deepEqual(report.http, {
			p50Ms: 10, p95Ms: 20, p99Ms: 30, maxMs: 40,
			throughputPerSecond: 1.5, requestCount: 2, failureRate: 0,
		});
		assert.equal(report.accepted, true);
		assert.equal(report.measurementStatus, 'measured');
		assert.equal(report.db.queryCount, 4);
		assert.equal(report.db.queriesPerRequest, 2);
		assert.equal(report.db.tableCounterDelta[0].estimatedRowsAfter, 1000);
		assert.equal(report.nPlusOneEvidence.loopSignal[0].count, 4);
		assert.equal(report.resources.length, 2);
		const failedMetadata = join(temporary, 'metadata-k6-failed.json');
		const failedReport = join(temporary, 'k6-failed-report.json');
		writeFileSync(failedMetadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
			runtime: {
				appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
				k6ExitStatus: 99, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
				logCaptureExitStatus: 0, afterDbSnapshotExitStatus: 0,
			},
		}));
		const failedProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, failedMetadata, failedReport]);
		assert.notEqual(failedProcess.status, 0);
		const failed = JSON.parse(readFileSync(failedReport, 'utf8'));
		assert.equal(failed.accepted, false);
		assert.equal(failed.measurementStatus, 'rejected');

		const samplerFailedMetadata = join(temporary, 'metadata-sampler-failed.json');
		const samplerFailedReport = join(temporary, 'sampler-failed-report.json');
		writeFileSync(samplerFailedMetadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
			runtime: {
				appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
				k6ExitStatus: 97, resourceSamplerExitStatus: 98, fixtureWindowExitStatus: 1,
				logCaptureExitStatus: 96, afterDbSnapshotExitStatus: 95,
			},
		}));
		const samplerFailedProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			join(temporary, 'missing-summary.json'), paths.before, paths.after, paths.sql,
			join(temporary, 'missing-resources.tsv'), samplerFailedMetadata, samplerFailedReport]);
		assert.notEqual(samplerFailedProcess.status, 0);
		const samplerFailed = JSON.parse(readFileSync(samplerFailedReport, 'utf8'));
		assert.equal(samplerFailed.accepted, false);
		assert.equal(samplerFailed.measurementStatus, 'rejected');
		assert.ok(samplerFailed.rejectionReasons.includes('missing-k6-summary'));
		assert.ok(samplerFailed.rejectionReasons.includes('fixture-window-crossed'));
		assert.ok(samplerFailed.rejectionReasons.includes('after-db-snapshot-exit-95'));

		const malformedSummary = join(temporary, 'summary-missing-required-metrics.json');
		const malformedBefore = join(temporary, 'before-empty-tables.json');
		const malformedAfter = join(temporary, 'after-empty-tables.json');
		const malformedReport = join(temporary, 'malformed-report.json');
		writeFileSync(malformedSummary, JSON.stringify({ metrics: {
			endpoint_poll_member_list_duration: { values: { 'p(50)': null, 'p(95)': '', 'p(99)': null, max: '' } },
			endpoint_poll_member_list_requests: { values: { count: 2, rate: '' } },
			endpoint_poll_member_list_failures: { values: { rate: null } },
		} }));
		writeFileSync(malformedBefore, JSON.stringify({ tables: [{ relname: 'users', seq_scan: null, seq_tup_read: 0, idx_scan: 0, idx_tup_fetch: 0, n_tup_ins: 0, n_tup_upd: 0, n_tup_del: 0, n_live_tup: 1000 }] }));
		writeFileSync(malformedAfter, JSON.stringify({ tables: [{ relname: 'users', seq_scan: '', seq_tup_read: 0, idx_scan: 0, idx_tup_fetch: 0, n_tup_ins: 0, n_tup_upd: 0, n_tup_del: 0, n_live_tup: 1000 }] }));
		const malformedProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			malformedSummary, malformedBefore, malformedAfter, paths.sql, paths.resources, paths.metadata, malformedReport]);
		assert.notEqual(malformedProcess.status, 0);
		const malformed = JSON.parse(readFileSync(malformedReport, 'utf8'));
		assert.equal(malformed.accepted, false);
		assert.ok(malformed.rejectionReasons.includes('missing-latency-metrics'));
		assert.ok(malformed.rejectionReasons.includes('missing-throughput'));
		assert.ok(malformed.rejectionReasons.includes('invalid-db-table-snapshot'));

		const missingArtifactReport = join(temporary, 'missing-artifact-report.json');
		const emptySql = join(temporary, 'empty-sql.txt');
		const malformedDb = join(temporary, 'malformed-db.json');
		writeFileSync(emptySql, '');
		writeFileSync(malformedDb, '{not-json');
		const missingArtifactProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			join(temporary, 'absent-summary.json'), malformedDb, join(temporary, 'absent-after.json'),
			emptySql, paths.resources, paths.metadata, missingArtifactReport]);
		assert.notEqual(missingArtifactProcess.status, 0);
		const missingArtifact = JSON.parse(readFileSync(missingArtifactReport, 'utf8'));
		assert.equal(missingArtifact.accepted, false);
		assert.ok(missingArtifact.rejectionReasons.includes('missing-k6-summary'));
		assert.ok(missingArtifact.rejectionReasons.includes('missing-db-snapshot'));
		assert.ok(missingArtifact.rejectionReasons.includes('missing-sql-evidence'));

		const rejectedAfter = join(temporary, 'after-with-write.json');
		const rejectedReport = join(temporary, 'rejected-report.json');
		writeFileSync(rejectedAfter, JSON.stringify({ tables: [{ relname: 'users', seq_scan: 4, seq_tup_read: 4000, idx_scan: 0, idx_tup_fetch: 0, n_tup_ins: 0, n_tup_upd: 1, n_tup_del: 0, n_live_tup: 1000 }] }));
		const rejected = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, rejectedAfter, paths.sql, paths.resources, paths.metadata, rejectedReport]);
		assert.notEqual(rejected.status, 0);
		assert.equal(existsSync(rejectedReport), true, 'a read run with writes must preserve a rejected report');
		const writeRejected = JSON.parse(readFileSync(rejectedReport, 'utf8'));
		assert.equal(writeRejected.accepted, false);
		assert.ok(writeRejected.rejectionReasons.some((reason) => reason.startsWith('write-counter-delta:')));
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
	assert.match(seed, /docker['"], \['inspect'/);
	assert.match(seed, /adminToken = \(await login\(ADMIN_EMAIL, ADMIN_PASSWORD\)\)\.accessToken;[\s\S]*const isolationPoll/);
	assert.match(seed, /appImageId/);
	assert.doesNotMatch(seed, /\/api\/v1\/admin\/campuses\/\$\{campusId\}\/members/);
	assert.doesNotMatch(seed, /process\.env\.PERF_GLOBAL_LOCK/);
	assert.doesNotMatch(seed, /process\.env\.EXPECTED_APP_IMAGE/);
	assert.doesNotMatch(seed, /email:\s*ADMIN_EMAIL/);
	assert.doesNotMatch(seed, /\.\.\.(?:primary|isolation)Campus/);
	assert.doesNotMatch(seed, /method:\s*['\"]DELETE['\"]/);
	assert.doesNotMatch(seed, /\bdelete\s+from\b/i);
	assert.match(shaper, /fixture_run_id/);
	assert.match(shaper, /Atomically update all five rows created by this fixture run/);
	assert.match(shaper, /shaped_at/);
	assert.match(shaper, /shape-attempted/);
	assert.match(shaper, /composeRuntime\.composeProject/);
	assert.match(shaper, /composeRuntime\.appImageId/);
	assert.doesNotMatch(shaper, /PERF_GLOBAL_LOCK:-/);
	assert.doesNotMatch(shaper, /EXPECTED_APP_IMAGE:-/);
	assert.doesNotMatch(shaper, /docker exec -e PGPASSWORD=/);
	assert.doesNotMatch(shaper, /\bdelete\s+from\b/i);
	assert.doesNotMatch(shaper, /\btruncate\b/i);
	assert.doesNotMatch(seed, /(?:password|token)\s*:\s*['\"][^'\"]+['\"]/i);
});
