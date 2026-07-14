import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
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
	'db-activity.sql',
	'activity-sample.mjs',
	'token-lifetime.mjs',
	'summarize-run.mjs',
	'README.md',
];

const EXPECTED_TABLES = [
	'campus_duty_assignments', 'campus_members', 'campuses', 'charge_items', 'coffee_brands',
	'coffee_menu_catalog', 'devotion_daily_checks', 'meal_poll_charge_groups', 'meal_poll_settlements',
	'notification_logs', 'payment_accounts', 'penalty_rules', 'poll_comments', 'poll_options',
	'poll_response_options', 'poll_responses', 'poll_template_options', 'poll_templates', 'polls',
	'prayer_group_members', 'prayer_groups', 'prayer_seasons', 'prayer_submissions', 'prayer_weeks',
	'user_fcm_tokens', 'users', 'weekly_devotion_records',
];

function tableRow(relname, overrides = {}) {
	return {
		relname, seq_scan: 0, seq_tup_read: 0, idx_scan: 0, idx_tup_fetch: 0,
		n_tup_ins: 0, n_tup_upd: 0, n_tup_del: 0, n_live_tup: 0,
		last_analyze: null, last_autoanalyze: null, analyze_count: 0, autoanalyze_count: 0,
		...overrides,
	};
}

function dbSnapshot(capturedAt, overridesByTable = {}, plannerSettings = { plan_cache_mode: 'auto', random_page_cost: '4' }) {
	return {
		capturedAt,
		plannerSettings,
		tables: EXPECTED_TABLES.map((name) => tableRow(name, overridesByTable[name])),
	};
}

function integritySample(overrides = {}) {
	return JSON.stringify({
		capturedAt: '2026-07-14T00:00:01.500Z',
		observerApplicationName: 'faithlog_issue196_observer',
		unexpectedDbSessions: [],
		unexpectedHttpClients: [],
		...overrides,
	});
}

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
	assert.match(runner, /faithlog-performance-\$\{compose_project\}\.lock/);
	assert.doesNotMatch(runner, /faithlog-performance-global\.lock|PERF_(?:GLOBAL|PROJECT)_LOCK:-/);
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
	for (const name of ['WARMUP_VUS', 'WARMUP_DURATION', 'MEASURED_VUS', 'MEASURED_DURATION', 'EXECUTION_RUN_ID']) {
		assert.match(runner, new RegExp(`${name}=.*:\\?`), `${name} must be runtime-required`);
	}
	assert.doesNotMatch(runner, /REQUESTED_MODE="\$\{1:-all\}"/);
	assert.match(runner, /REQUESTED_MODE="\$\{1:\?/);
	assert.match(runner, /warmup[\s\S]*k6 run[\s\S]*snapshot_db_tables "\$\{before_file\}"[\s\S]*measured[\s\S]*k6 run/);
	assert.match(runner, /if \(\( warmup_status != 0 \)\)[\s\S]*return/);
	assert.match(runner, /EXECUTION_RUN_ID[\s\S]*REPORT_ROOT/);
	assert.match(runner, /Report directory already exists|Refusing to overwrite/);
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
	for (const marker of ['last_analyze', 'last_autoanalyze', 'analyze_count', 'autoanalyze_count', 'plannerSettings']) {
		assert.match(sql, new RegExp(marker), `missing DB integrity marker ${marker}`);
	}
	assert.match(read('db-activity.sql'), /pg_stat_activity/);
	assert.match(read('db-activity.sql'), /faithlog_issue196_observer/);
	assert.match(runner, /activity-sample\.mjs/);
	assert.match(runner, /lsof/);
	assert.doesNotMatch(sql, /\b(?:insert|update|delete|truncate|alter|drop|create)\b/i);
	assert.match(runner, /org\.hibernate\.SQL/);
	assert.match(runner, /docker\s+logs/);
	assert.match(summarizer, /queriesPerRequest/);
	assert.match(summarizer, /repeatedSql/);
	assert.match(summarizer, /tableCounterDelta/);
	assert.match(summarizer, /scenario-ready/);
	for (const marker of ['counter-regression', 'write-counter-delta', 'snapshot-time-order', 'planner-settings-changed',
		'analyze-state-changed', 'external-http-activity', 'unexpected-db-session']) {
		assert.match(summarizer, new RegExp(marker), `missing rejection reason ${marker}`);
	}
});

test('project-scoped lock blocks a fake same-project runner before login, DB, or k6', () => {
	const runner = join(ROOT, 'run-baseline.sh');
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-lock-'));
	const project = `faithlog-contract-${process.pid}`;
	const canonicalLock = `/tmp/faithlog-performance-${project}.lock`;
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const calls = join(temporary, 'dangerous.log');
		const manifest = join(temporary, 'fixture.json');
		const now = Date.now();
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196lock', shapedAt: new Date(now).toISOString(),
			primaryCampus: { memberActor: { email: 'member@example.test' } },
			composeRuntime: { composeProject: project, appConfigHash: 'app-hash', dbConfigHash: 'db-hash', appImageId: 'sha256:contract', targetPort: '18080' },
			polls: { byKey: {
				open: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
				closed_member_visible: { endsAt: new Date(now - 2 * 86400000).toISOString() },
				closed_admin_only: { endsAt: new Date(now - 5 * 86400000).toISOString() },
				closed_expired: { endsAt: new Date(now - 8 * 86400000).toISOString() },
				scheduled_future: { startsAt: new Date(now + 2 * 86400000).toISOString() },
			} },
		}));
		writeFileSync(join(bin, 'docker'), [
			'#!/usr/bin/env bash', 'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*"{{.Config.Image}}"*) echo faithlog-latest ;;', '*"{{.Image}}"*) echo sha256:contract ;;',
			'*"port faithlog-backend 8080/tcp"*) echo 0.0.0.0:18080 ;;',
			`*) echo "docker:$*" >> "${calls}"; exit 88 ;;`, 'esac', '',
		].join('\n'));
		writeFileSync(join(bin, 'k6'), ['#!/usr/bin/env bash', `echo "k6:$*" >> "${calls}"`, 'exit 89', ''].join('\n'));
		writeFileSync(join(bin, 'node'), [
			'#!/usr/bin/env bash', `if [[ "$*" == *"await fetch"* ]]; then echo login >> "${calls}"; exit 90; fi`,
			`exec "${process.execPath}" "$@"`, '',
		].join('\n'));
		for (const command of ['docker', 'k6', 'node']) chmodSync(join(bin, command), 0o755);
		mkdirSync(canonicalLock);
		const result = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: 'i196lock', EXECUTION_RUN_ID: 'exec-lock',
				FIXTURE_MANIFEST: manifest, REPORT_ROOT: join(temporary, 'reports'), BASE_URL: 'http://localhost:18080',
				WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s', VUS: '1', DURATION: '1s',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'secret', PERF_MEMBER_PASSWORD: 'secret',
				PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'secret',
			},
		});
		assert.notEqual(result.status, 0);
		assert.equal(existsSync(calls) ? readFileSync(calls, 'utf8') : '', '');
	} finally {
		rmSync(canonicalLock, { recursive: true, force: true });
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('token lifetime gate rejects a fake clock that cannot cover the phase', async () => {
	const modulePath = join(ROOT, 'token-lifetime.mjs');
	assert.equal(existsSync(modulePath), true, 'missing token-lifetime.mjs');
	const { assertTokenLifetime } = await import(`${pathToFileURL(modulePath).href}?test=${Date.now()}`);
	const token = (exp) => `${Buffer.from('{}').toString('base64url')}.${Buffer.from(JSON.stringify({ exp })).toString('base64url')}.signature`;
	assert.doesNotThrow(() => assertTokenLifetime(token(1400), '5m', 1000, 60));
	assert.throws(() => assertTokenLifetime(token(1300), '5m', 1000, 60), /remaining lifetime/i);
});

test('summarizer materializes endpoint latency, throughput, SQL loop, table, and resource evidence', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-contract-'));
	try {
		const endpoint = 'poll_member_list';
		const paths = Object.fromEntries(['summary', 'before', 'after', 'sql', 'resources', 'integrity', 'metadata', 'report']
			.map((name) => [name, join(temporary, `${name}.${name === 'sql' || name === 'resources' ? 'txt' : 'json'}`)]));
		writeFileSync(paths.summary, JSON.stringify({ metrics: {
			endpoint_poll_member_list_duration: { values: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40 } },
			endpoint_poll_member_list_requests: { values: { count: 2, rate: 1.5 } },
			endpoint_poll_member_list_failures: { values: { rate: 0 } },
		} }));
		writeFileSync(paths.before, JSON.stringify(dbSnapshot('2026-07-14T00:00:01.000Z', { users: { n_live_tup: 1000 } })));
		writeFileSync(paths.after, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', { users: { seq_scan: 4, seq_tup_read: 4000, n_live_tup: 1000 } })));
		writeFileSync(paths.sql, Array.from({ length: 4 }, (_, index) => `INFO org.hibernate.SQL: select * from users where id=${index + 1}`).join('\n'));
		writeFileSync(paths.resources, [
			'2026-07-14T00:00:00Z\tfaithlog-backend\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\t20.0%\t200MiB / 1GiB\t19.5%',
		].join('\n'));
		writeFileSync(paths.integrity, `${integritySample()}\n`);
		writeFileSync(paths.metadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
			runtime: {
				appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
				k6ExitStatus: 0, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
				logCaptureExitStatus: 0, afterDbSnapshotExitStatus: 0,
			},
		}));

		execFileSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, paths.report]);
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
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, failedMetadata, failedReport]);
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
			join(temporary, 'missing-resources.tsv'), paths.integrity, samplerFailedMetadata, samplerFailedReport]);
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
		writeFileSync(malformedBefore, JSON.stringify(dbSnapshot('2026-07-14T00:00:01.000Z', { users: { seq_scan: null } })));
		writeFileSync(malformedAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', { users: { seq_scan: '' } })));
		const malformedProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			malformedSummary, malformedBefore, malformedAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, malformedReport]);
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
			emptySql, paths.resources, paths.integrity, paths.metadata, missingArtifactReport]);
		assert.notEqual(missingArtifactProcess.status, 0);
		const missingArtifact = JSON.parse(readFileSync(missingArtifactReport, 'utf8'));
		assert.equal(missingArtifact.accepted, false);
		assert.ok(missingArtifact.rejectionReasons.includes('missing-k6-summary'));
		assert.ok(missingArtifact.rejectionReasons.includes('missing-db-snapshot'));
		assert.ok(missingArtifact.rejectionReasons.includes('missing-sql-evidence'));

		const rejectedAfter = join(temporary, 'after-with-write.json');
		const rejectedReport = join(temporary, 'rejected-report.json');
		writeFileSync(rejectedAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', { users: { seq_scan: 4, seq_tup_read: 4000, n_tup_upd: 1, n_live_tup: 1000 } })));
		const rejected = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, rejectedAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, rejectedReport]);
		assert.notEqual(rejected.status, 0);
		assert.equal(existsSync(rejectedReport), true, 'a read run with writes must preserve a rejected report');
		const writeRejected = JSON.parse(readFileSync(rejectedReport, 'utf8'));
		assert.equal(writeRejected.accepted, false);
		assert.ok(writeRejected.rejectionReasons.some((reason) => reason.startsWith('write-counter-delta:')));

		const directSummary = join(temporary, 'direct-summary.json');
		const directReport = join(temporary, 'direct-report.json');
		writeFileSync(directSummary, JSON.stringify({ metrics: {
			endpoint_poll_member_list_duration: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40 },
			endpoint_poll_member_list_requests: { count: 2, rate: 1.5 },
			endpoint_poll_member_list_failures: { rate: 0 },
		} }));
		execFileSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			directSummary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, directReport]);
		assert.equal(JSON.parse(readFileSync(directReport, 'utf8')).accepted, true);

		const zeroSummary = join(temporary, 'zero-summary.json');
		const zeroReport = join(temporary, 'zero-report.json');
		writeFileSync(zeroSummary, JSON.stringify({ metrics: {
			endpoint_poll_member_list_duration: { values: { 'p(50)': 0, 'p(95)': 0, 'p(99)': 0, max: 0 } },
			endpoint_poll_member_list_requests: { values: { count: 1, rate: 0 } },
			endpoint_poll_member_list_failures: { values: { rate: 0 } },
		} }));
		const zeroProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			zeroSummary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, zeroReport]);
		assert.notEqual(zeroProcess.status, 0);
		assert.ok(JSON.parse(readFileSync(zeroReport, 'utf8')).rejectionReasons.includes('non-positive-throughput'));

		const counterBefore = join(temporary, 'counter-before.json');
		const counterAfter = join(temporary, 'counter-after.json');
		const counterReport = join(temporary, 'counter-report.json');
		writeFileSync(counterBefore, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', {
			users: { seq_scan: 5 }, campuses: { n_tup_upd: 1 },
		})));
		writeFileSync(counterAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:01.000Z', {
			users: { seq_scan: 4, n_tup_ins: 1 }, campuses: { n_tup_upd: 0 },
		})));
		const counterProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, counterBefore, counterAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, counterReport]);
		assert.notEqual(counterProcess.status, 0);
		const counterRejected = JSON.parse(readFileSync(counterReport, 'utf8'));
		assert.ok(counterRejected.rejectionReasons.includes('snapshot-time-order'));
		assert.ok(counterRejected.rejectionReasons.includes('counter-regression'));
		assert.ok(counterRejected.rejectionReasons.some((reason) => reason.startsWith('write-counter-delta:')));
		const missingTableAfter = join(temporary, 'missing-table-after.json');
		const missingTableReport = join(temporary, 'missing-table-report.json');
		const missingTableSnapshot = dbSnapshot('2026-07-14T00:00:02.000Z');
		missingTableSnapshot.tables.pop();
		writeFileSync(missingTableAfter, JSON.stringify(missingTableSnapshot));
		const missingTableProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, missingTableAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, missingTableReport]);
		assert.notEqual(missingTableProcess.status, 0);
		assert.ok(JSON.parse(readFileSync(missingTableReport, 'utf8')).rejectionReasons.includes('required-table-set-mismatch'));

		const analysisAfter = join(temporary, 'analysis-after.json');
		const externalIntegrity = join(temporary, 'external-integrity.jsonl');
		const integrityReport = join(temporary, 'integrity-rejected-report.json');
		writeFileSync(analysisAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', {
			polls: { autoanalyze_count: 1, last_autoanalyze: '2026-07-14T00:00:01.700Z' },
		}, { plan_cache_mode: 'force_generic_plan', random_page_cost: '4' })));
		writeFileSync(externalIntegrity, `${integritySample({
			unexpectedDbSessions: [{ pid: 999, applicationName: 'psql' }],
			unexpectedHttpClients: [{ pid: 998, command: 'Chrome' }],
		})}\n`);
		const integrityProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, analysisAfter, paths.sql, paths.resources, externalIntegrity, paths.metadata, integrityReport]);
		assert.notEqual(integrityProcess.status, 0);
		const integrityRejected = JSON.parse(readFileSync(integrityReport, 'utf8'));
		for (const reason of ['planner-settings-changed', 'analyze-state-changed', 'external-http-activity', 'unexpected-db-session']) {
			assert.ok(integrityRejected.rejectionReasons.includes(reason), `missing ${reason}`);
		}
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
	assert.match(seed, /sanitizedChildEnv/);
	assert.match(seed, /faithlog-performance-\$\{composeRuntime\.composeProject\}\.lock/);
	assert.doesNotMatch(seed, /\/api\/v1\/admin\/campuses\/\$\{campusId\}\/members/);
	assert.doesNotMatch(seed, /process\.env\.PERF_(?:GLOBAL|PROJECT)_LOCK/);
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
	assert.match(shaper, /faithlog-performance-\$\{compose_project\}\.lock/);
	assert.doesNotMatch(shaper, /PERF_(?:GLOBAL|PROJECT)_LOCK:-/);
	assert.doesNotMatch(shaper, /EXPECTED_APP_IMAGE:-/);
	assert.match(shaper, /export -n PERF_DB_USER PERF_DB_NAME PERF_DB_PASSWORD/);
	assert.match(read('run-baseline.sh'), /export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_MEMBER_PASSWORD PERF_DB_USER PERF_DB_NAME PERF_DB_PASSWORD/);
	assert.doesNotMatch(shaper, /docker exec -e PGPASSWORD=/);
	assert.doesNotMatch(shaper, /\bdelete\s+from\b/i);
	assert.doesNotMatch(shaper, /\btruncate\b/i);
	assert.doesNotMatch(seed, /(?:password|token)\s*:\s*['\"][^'\"]+['\"]/i);
});
