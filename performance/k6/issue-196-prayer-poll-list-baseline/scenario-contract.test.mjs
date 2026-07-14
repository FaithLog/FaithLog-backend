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
const EXPECTED_PLANNER_SETTINGS = {
	plan_cache_mode: 'auto', random_page_cost: '4', cpu_tuple_cost: '0.01', cpu_index_tuple_cost: '0.005',
	effective_cache_size: '4GB', work_mem: '4MB', jit: 'on', max_parallel_workers_per_gather: '2',
};
const COUNTER_FIELDS = new Set([
	'seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del',
	'n_live_tup', 'n_dead_tup', 'analyze_count', 'autoanalyze_count', 'vacuum_count', 'autovacuum_count',
]);

function tableRow(relname, overrides = {}) {
	const row = {
		schemaname: 'public', relname, seq_scan: '0', seq_tup_read: '0', idx_scan: '0', idx_tup_fetch: '0',
		n_tup_ins: '0', n_tup_upd: '0', n_tup_del: '0', n_live_tup: '0', n_dead_tup: '0',
		last_analyze: null, last_autoanalyze: null, analyze_count: '0', autoanalyze_count: '0',
		last_vacuum: null, last_autovacuum: null, vacuum_count: '0', autovacuum_count: '0',
	};
	for (const [key, value] of Object.entries(overrides)) {
		row[key] = COUNTER_FIELDS.has(key) && typeof value === 'number' && Number.isSafeInteger(value) ? String(value) : value;
	}
	return row;
}

function dbSnapshot(capturedAt, overridesByTable = {}, plannerSettings = EXPECTED_PLANNER_SETTINGS) {
	return {
		capturedAt,
		plannerSettings,
		databaseIdentity: {
			currentDatabase: 'faithlog', serverAddress: '172.20.0.2', serverPort: 5432,
			postmasterStartedAt: '2026-07-14T00:00:00.000Z',
		},
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
	for (const marker of ['assert_runtime_continuity', 'appContainerId', 'appContainerStartedAt',
		'dbContainerId', 'dbImageId', 'dbContainerStartedAt']) {
		assert.match(runner, new RegExp(marker), `missing runtime continuity marker ${marker}`);
	}
	assert.ok((runner.match(/assert_runtime_continuity/g) || []).length >= 5,
		'runtime identity must be checked before warmup, around measured, and before adoption');
	assert.match(runner, /set \+e[\s\S]*docker logs[\s\S]*log_capture_status=\$\?[\s\S]*snapshot_db_tables "\$\{after_file\}"[\s\S]*after_snapshot_status=\$\?[\s\S]*set -e/);
	assert.match(runner, /summarize-run\.mjs[\s\S]*summarize_status=\$\?[\s\S]*if \(\( summarize_status != 0 \)\)/);
	assert.match(runner, /env -u PERF_ADMIN_EMAIL/);
	for (const name of ['WARMUP_VUS', 'WARMUP_DURATION', 'MEASURED_VUS', 'MEASURED_DURATION', 'EXECUTION_RUN_ID']) {
		assert.match(runner, new RegExp(`${name}=.*:\\?`), `${name} must be runtime-required`);
	}
	for (const name of ['BASE_URL', 'APP_CONTAINER', 'DB_CONTAINER', 'EXPECTED_APP_SERVICE', 'EXPECTED_DB_SERVICE', 'EXPECTED_APP_IMAGE',
		'SAMPLING_INTERVAL_SECONDS', 'SAMPLING_MAX_GAP_SECONDS']) {
		assert.match(runner, new RegExp(`${name}=.*:\\?`), `${name} must be runtime-required without a target/policy default`);
	}
	assert.doesNotMatch(runner, /SAMPLING_INTERVAL_SECONDS=1|SAMPLING_MAX_GAP_SECONDS=2/);
	assert.match(runner, /automaticAdoption:\s*false/);
	assert.doesNotMatch(runner, /REQUESTED_MODE="\$\{1:-all\}"/);
	assert.match(runner, /REQUESTED_MODE="\$\{1:\?/);
	assert.match(runner, /warmup[\s\S]*k6 run[\s\S]*snapshot_db_tables "\$\{before_file\}"[\s\S]*measured[\s\S]*k6 run/);
	assert.match(runner, /if \(\( warmup_status != 0 \)\)[\s\S]*return/);
	assert.match(runner, /EXECUTION_RUN_ID[\s\S]*REPORT_ROOT/);
	assert.match(runner, /Report directory already exists|Refusing to overwrite/);
	assert.doesNotMatch(runner, /docker\s+compose\s+(?:up|down|build)|docker\s+(?:system|builder|image|volume)\s+prune/);
	assert.doesNotMatch(runner, /(?:^|\s)(?:source|\.)\s+\.env(?:\s|$)/m);
});

test('runner rejects every missing target identity before inspect or login', () => {
	const runner = join(ROOT, 'run-baseline.sh');
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-target-'));
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const calls = join(temporary, 'calls.log');
		writeFileSync(join(bin, 'docker'), `#!/usr/bin/env bash\necho docker >> "${calls}"\nexit 99\n`);
		for (const command of ['k6', 'lsof']) writeFileSync(join(bin, command), '#!/usr/bin/env bash\nexit 99\n');
		for (const command of ['docker', 'k6', 'lsof']) chmodSync(join(bin, command), 0o755);
		const required = {
			FIXTURE_RUN_ID: 'i196target', EXECUTION_RUN_ID: 'exectarget',
			WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s',
			PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'secret', PERF_MEMBER_PASSWORD: 'secret',
			PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'secret',
			BASE_URL: 'http://127.0.0.1:18080', APP_CONTAINER: 'approved-app', DB_CONTAINER: 'approved-db',
			EXPECTED_APP_SERVICE: 'app', EXPECTED_DB_SERVICE: 'postgres', EXPECTED_APP_IMAGE: 'approved-image',
			SAMPLING_INTERVAL_SECONDS: '1', SAMPLING_MAX_GAP_SECONDS: '2',
		};
		for (const missing of ['BASE_URL', 'APP_CONTAINER', 'DB_CONTAINER', 'EXPECTED_APP_SERVICE', 'EXPECTED_DB_SERVICE', 'EXPECTED_APP_IMAGE']) {
			const env = { ...process.env, ...required, PATH: `${bin}:${process.env.PATH}` };
			delete env[missing];
			const result = spawnSync('bash', [runner, 'prayer'], { env, encoding: 'utf8' });
			assert.notEqual(result.status, 0);
			assert.match(result.stderr, new RegExp(`${missing}.*required`, 'i'), `${missing} omission must be the failure cause`);
			assert.equal(existsSync(calls), false, `${missing} omission must happen before docker inspect/login`);
		}
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('seed, shape, and direct scenario require every approved target identity before side effects', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-entry-target-'));
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const calls = join(temporary, 'calls.log');
		writeFileSync(join(bin, 'docker'), `#!/usr/bin/env bash\necho docker >> "${calls}"\nexit 99\n`);
		chmodSync(join(bin, 'docker'), 0o755);
		const required = {
			BASE_URL: 'http://127.0.0.1:18080', APP_CONTAINER: 'approved-app', DB_CONTAINER: 'approved-db',
			EXPECTED_APP_SERVICE: 'app', EXPECTED_DB_SERVICE: 'postgres', EXPECTED_APP_IMAGE: 'approved-image',
		};
		const manifest = join(temporary, 'fixture-manifest.json');
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196entry', shapedAt: null,
			composeRuntime: { composeProject: 'approved', appConfigHash: 'app-hash', dbConfigHash: 'db-hash', appImageId: 'sha256:app' },
			primaryCampus: { campusId: 1 }, polls: { byKey: {
				open: { id: 1 }, closed_member_visible: { id: 2 }, closed_admin_only: { id: 3 },
				closed_expired: { id: 4 }, scheduled_future: { id: 5 },
			} },
		}));
		for (const entrypoint of ['seed', 'shape']) {
			for (const missing of Object.keys(required)) {
				rmSync(calls, { force: true });
				const env = {
					...process.env, ...required, PATH: `${bin}:${process.env.PATH}`,
					FIXTURE_RUN_ID: 'i196entry', FIXTURE_MANIFEST: manifest,
					PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'secret', PERF_MEMBER_PASSWORD: 'secret',
					PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'secret',
				};
				delete env[missing];
				const command = entrypoint === 'seed' ? process.execPath : 'bash';
				const args = entrypoint === 'seed' ? [join(ROOT, 'seed-fixture.mjs')] : [join(ROOT, 'shape-fixture.sh')];
				const result = spawnSync(command, args, { env, encoding: 'utf8' });
				assert.notEqual(result.status, 0);
				assert.match(result.stderr, new RegExp(`${missing}.*required`, 'i'), `${entrypoint} must reject missing ${missing}`);
				assert.equal(existsSync(calls), false, `${entrypoint} must reject missing ${missing} before Docker/API/DB`);
			}
		}
		const scenario = read('scenario.js');
		assert.match(scenario, /BASE_URL.*required/i);
		assert.doesNotMatch(scenario, /BASE_URL\s*=\s*\(__ENV\.BASE_URL\s*\|\|/);
		for (const source of [read('seed-fixture.mjs'), read('shape-fixture.sh')]) {
			assert.doesNotMatch(source, /faithlog-backend|faithlog-postgres|faithlog-latest|http:\/\/localhost:8080/);
		}
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('DB and log evidence contract is endpoint-scoped and read-only', () => {
	const runner = read('run-baseline.sh');
	const sql = read('db-table-stats.sql');
	const activitySql = read('db-activity.sql');
	const summarizer = read('summarize-run.mjs');

	assert.match(sql, /pg_stat_user_tables/);
	assert.match(sql, /seq_scan/);
	assert.match(sql, /idx_scan/);
	for (const marker of ['last_analyze', 'last_autoanalyze', 'analyze_count', 'autoanalyze_count',
		'last_vacuum', 'last_autovacuum', 'vacuum_count', 'autovacuum_count', 'plannerSettings',
		'databaseIdentity', 'pg_postmaster_start_time']) {
		assert.match(sql, new RegExp(marker), `missing DB integrity marker ${marker}`);
	}
	for (const counter of ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del',
		'analyze_count', 'autoanalyze_count', 'vacuum_count', 'autovacuum_count']) {
		assert.match(sql, new RegExp(`${counter}::text`), `${counter} must be emitted as an exact decimal string`);
	}
	assert.match(summarizer, /BigInt/);
	assert.match(activitySql, /pg_stat_activity/);
	assert.match(activitySql, /pid <> pg_backend_pid\(\)/);
	assert.match(activitySql, /client_addr = any\(string_to_array\(:'app_client_addrs'/);
	assert.doesNotMatch(activitySql, /application_name[^\n]*faithlog_issue196_observer/);
	assert.match(runner, /activity-sample\.mjs/);
	assert.match(runner, /lsof/);
	const activity = spawnSync(process.execPath, [join(ROOT, 'activity-sample.mjs')], {
		env: {
			...process.env, DB_ACTIVITY_JSON: '{"unexpectedSessions":[]}', K6_PID: '100',
			LSOF_TEXT: 'p100\nck6\np200\nck6\np300\ncChrome\n',
		},
		encoding: 'utf8',
	});
	assert.equal(activity.status, 0);
	assert.deepEqual(JSON.parse(activity.stdout).unexpectedHttpClients, [
		{ pid: 200, command: 'k6' }, { pid: 300, command: 'Chrome' },
	]);
	assert.doesNotMatch(sql, /\b(?:insert|update|delete|truncate|alter|drop|create)\b/i);
	assert.match(runner, /org\.hibernate\.SQL/);
	assert.match(runner, /docker\s+logs/);
	assert.match(summarizer, /queriesPerRequest/);
	assert.match(summarizer, /repeatedSql/);
	assert.match(summarizer, /tableCounterDelta/);
	assert.match(summarizer, /scenario-ready/);
	for (const marker of ['counter-regression', 'write-counter-delta', 'snapshot-time-order', 'planner-settings-changed',
		'analyze-state-changed', 'vacuum-state-changed', 'invalid-planner-settings', 'invalid-database-identity',
		'external-http-activity', 'unexpected-db-session', 'adoption-policy-pending-user-approval']) {
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
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
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
				APP_CONTAINER: 'faithlog-backend', DB_CONTAINER: 'faithlog-postgres', EXPECTED_APP_SERVICE: 'app',
				EXPECTED_DB_SERVICE: 'postgres', EXPECTED_APP_IMAGE: 'faithlog-latest',
				SAMPLING_INTERVAL_SECONDS: '2', SAMPLING_MAX_GAP_SECONDS: '4',
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

test('warmup failure blocks measured evidence and keeps credentials out of unrelated children', () => {
	const runner = join(ROOT, 'run-baseline.sh');
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-warmup-'));
	const project = `faithlog-warmup-${process.pid}`;
	const fixtureRunId = `i196warm${process.pid}`.slice(0, 32);
	const executionRunId = `execwarm${process.pid}`.slice(0, 32);
	const reportBase = join(ROOT, '../../..', 'build/reports/k6/issue-196', fixtureRunId);
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const calls = join(temporary, 'calls.log');
		const manifest = join(temporary, 'fixture.json');
		const now = Date.now();
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId, shapedAt: new Date(now).toISOString(),
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
			'#!/usr/bin/env bash',
			'if [[ -n "${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}" ]]; then echo docker-credential-leak >> "' + calls + '"; fi',
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
			'*"{{.Id}}"*faithlog-backend*) echo app-container-id ;;',
			'*"{{.Id}}"*faithlog-postgres*) echo db-container-id ;;',
			'*"{{.State.StartedAt}}"*) echo 2026-07-14T00:00:00.000Z ;;',
			'*"{{.Config.Image}}"*) echo faithlog-latest ;;', '*"{{.Image}}"*) echo sha256:contract ;;',
			'*"port faithlog-backend 8080/tcp"*) echo 0.0.0.0:18080 ;;',
			'*"range .Config.Env"*) printf "%s\\n" LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false FAITHLOG_SCHEDULER_ENABLED=false ;;',
			'*"pg_postmaster_start_time"*) printf "%s\\n" \'{"currentDatabase":"faithlog","serverAddress":"127.0.0.1","serverPort":5432,"postmasterStartedAt":"2026-07-14T00:00:00.000Z"}\' ;;',
			`*) echo "docker-unexpected:$*" >> "${calls}"; exit 88 ;;`, 'esac', '',
		].join('\n'));
		writeFileSync(join(bin, 'node'), [
			'#!/usr/bin/env bash',
			'if [[ -n "${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}" ]]; then echo node-credential-leak >> "' + calls + '"; fi',
			`if [[ "$*" == *"await fetch"* ]]; then echo login >> "${calls}"; printf 'x.eyJleHAiOjQxMDI0NDQ4MDB9.x'; exit 0; fi`,
			`exec "${process.execPath}" "$@"`, '',
		].join('\n'));
		writeFileSync(join(bin, 'k6'), [
			'#!/usr/bin/env bash',
			'if [[ -n "${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}" ]]; then echo k6-credential-leak >> "' + calls + '"; fi',
			'if [[ -z "${PERF_ADMIN_ACCESS_TOKEN:-}" || -z "${PERF_MEMBER_ACCESS_TOKEN:-}" ]]; then echo k6-token-missing >> "' + calls + '"; fi',
			`echo warmup-k6 >> "${calls}"`, 'exit 42', '',
		].join('\n'));
		for (const command of ['docker', 'k6', 'node']) chmodSync(join(bin, command), 0o755);
		const result = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId, EXECUTION_RUN_ID: executionRunId,
				FIXTURE_MANIFEST: manifest, BASE_URL: 'http://localhost:18080',
				WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s',
				APP_CONTAINER: 'faithlog-backend', DB_CONTAINER: 'faithlog-postgres', EXPECTED_APP_SERVICE: 'app',
				EXPECTED_DB_SERVICE: 'postgres', EXPECTED_APP_IMAGE: 'faithlog-latest',
				SAMPLING_INTERVAL_SECONDS: '2', SAMPLING_MAX_GAP_SECONDS: '4',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'admin-secret', PERF_MEMBER_PASSWORD: 'member-secret',
				PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'db-secret',
				PERF_ACCESS_TOKEN: 'stale-token', PERF_ADMIN_ACCESS_TOKEN: 'stale-admin-token', PERF_MEMBER_ACCESS_TOKEN: 'stale-member-token',
			},
		});
		assert.notEqual(result.status, 0);
		const observed = readFileSync(calls, 'utf8').trim().split(/\r?\n/);
		assert.equal(observed.filter((line) => line === 'warmup-k6').length, 1, 'measured k6 must not start');
		assert.equal(observed.filter((line) => line === 'login').length, 2, 'only warmup tokens are issued');
		assert.equal(observed.some((line) => line.includes('credential-leak') || line.includes('token-missing') || line.startsWith('docker-unexpected')), false);
		assert.equal(existsSync(join(reportBase, executionRunId, 'prayer', 'prayer_current_season', 'db-before.json')), false);
	} finally {
		rmSync(reportBase, { recursive: true, force: true });
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('fake orchestration scopes tokens and DB credentials to their required children', () => {
	const runner = join(ROOT, 'run-baseline.sh');
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-scope-'));
	const project = `faithlog-scope-${process.pid}`;
	const fixtureRunId = `i196scope${process.pid}`.slice(0, 32);
	const executionRunId = `execscope${process.pid}`.slice(0, 32);
	const reportBase = join(ROOT, '../../..', 'build/reports/k6/issue-196', fixtureRunId);
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const calls = join(temporary, 'calls.log');
		const k6Count = join(temporary, 'k6-count');
		const dbCount = join(temporary, 'db-count');
		const identityCount = join(temporary, 'identity-count');
		const before = join(temporary, 'before.json');
		const after = join(temporary, 'after.json');
		const manifest = join(temporary, 'fixture.json');
		const now = Date.now();
		writeFileSync(before, JSON.stringify(dbSnapshot(new Date(now - 1000).toISOString())));
		writeFileSync(after, JSON.stringify(dbSnapshot(new Date(now + 1000).toISOString(), { users: { seq_scan: 1, seq_tup_read: 1000 } })));
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId, shapedAt: new Date(now).toISOString(),
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
			'#!/usr/bin/env bash',
			`if [[ "$*" == *'{{.Id}}'*faithlog-backend* ]]; then count=$(cat "${identityCount}" 2>/dev/null || echo 0); count=$((count + 1)); echo "$count" > "${identityCount}"; if [[ "${'${FAKE_REPLACE_RUNTIME:-0}'}" == 1 && "$count" -gt 1 ]]; then echo app-container-replaced; else echo app-container-id; fi; exit 0; fi`,
			'if [[ "$1" == exec ]]; then',
			`  if [[ -z "${'${PGPASSWORD+x}'}" || -n "${'${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_PASSWORD+x}${PERF_ACCESS_TOKEN+x}${PERF_ADMIN_ACCESS_TOKEN+x}${PERF_MEMBER_ACCESS_TOKEN+x}'}" ]]; then echo db-scope-bad >> "${calls}"; fi`,
			`  echo db-collector >> "${calls}"`,
			`  if [[ "$*" == *pg_postmaster_start_time* ]]; then printf '%s\\n' '{"currentDatabase":"faithlog","serverAddress":"127.0.0.1","serverPort":5432,"postmasterStartedAt":"2026-07-14T00:00:00.000Z"}'; exit 0; fi`,
			`  if [[ "$*" == *faithlog_issue196_observer* ]]; then printf '%s\\n' '{"capturedAt":"2026-07-14T00:00:00Z","unexpectedSessions":[]}'; exit 0; fi`,
			`  count=$(cat "${dbCount}" 2>/dev/null || echo 0); count=$((count + 1)); echo "$count" > "${dbCount}"`,
			`  if (( count == 1 )); then cat "${before}"; else cat "${after}"; fi`,
			'  exit 0', 'fi',
			`if [[ -n "${'${PGPASSWORD+x}${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}${PERF_ACCESS_TOKEN+x}${PERF_ADMIN_ACCESS_TOKEN+x}${PERF_MEMBER_ACCESS_TOKEN+x}'}" ]]; then echo docker-scope-bad >> "${calls}"; fi`,
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
			'*"{{.Id}}"*faithlog-postgres*) echo db-container-id ;;',
			'*"{{.State.StartedAt}}"*) echo 2026-07-14T00:00:00.000Z ;;',
			'*"{{.Config.Image}}"*) echo faithlog-latest ;;', '*"{{.Image}}"*) echo sha256:contract ;;',
			'*"port faithlog-backend 8080/tcp"*) echo 0.0.0.0:18080 ;;',
			'*"range .Config.Env"*) printf "%s\\n" LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false FAITHLOG_SCHEDULER_ENABLED=false ;;',
			'*"stats --no-stream"*) printf "faithlog-backend\\t10.0%%\\t100MiB / 1GiB\\t9.8%%\\nfaithlog-postgres\\t20.0%%\\t200MiB / 1GiB\\t19.5%%\\n" ;;',
			'*"logs --since"*) echo "INFO org.hibernate.SQL: select 1" ;;',
			`*) echo "docker-unexpected:$*" >> "${calls}"; exit 88 ;;`, 'esac', '',
		].join('\n'));
		writeFileSync(join(bin, 'node'), [
			'#!/usr/bin/env bash',
			`if [[ -n "${'${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}'}" ]]; then echo node-scope-bad >> "${calls}"; fi`,
			`if [[ "$*" == *"await fetch"* ]]; then echo login >> "${calls}"; printf 'x.eyJleHAiOjQxMDI0NDQ4MDB9.x'; exit 0; fi`,
			`if [[ "$*" == *"const metadata"* ]]; then [[ -n "${'${PERF_ACCESS_TOKEN+x}${PERF_ADMIN_ACCESS_TOKEN+x}${PERF_MEMBER_ACCESS_TOKEN+x}'}" ]] && echo metadata-scope-bad >> "${calls}"; echo metadata-child >> "${calls}"; fi`,
			`if [[ "$*" == *"summarize-run.mjs"* ]]; then [[ -n "${'${PERF_ACCESS_TOKEN+x}${PERF_ADMIN_ACCESS_TOKEN+x}${PERF_MEMBER_ACCESS_TOKEN+x}'}" ]] && echo summarizer-scope-bad >> "${calls}"; echo summarizer-child >> "${calls}"; fi`,
			`exec "${process.execPath}" "$@"`, '',
		].join('\n'));
		writeFileSync(join(bin, 'k6'), [
			'#!/usr/bin/env bash',
			`if [[ -n "${'${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}'}" ]]; then echo k6-scope-bad >> "${calls}"; fi`,
			`if [[ -z "${'${PERF_ADMIN_ACCESS_TOKEN:-}'}" || -z "${'${PERF_MEMBER_ACCESS_TOKEN:-}'}" ]]; then echo k6-token-missing >> "${calls}"; fi`,
			`count=$(cat "${k6Count}" 2>/dev/null || echo 0); count=$((count + 1)); echo "$count" > "${k6Count}"`,
			'summary=""; while (( $# > 0 )); do if [[ "$1" == --summary-export ]]; then summary="$2"; shift 2; else shift; fi; done',
			'mkdir -p "$(dirname "${summary}")"',
			`if (( count == 3 )); then echo second-warmup-stop >> "${calls}"; exit 42; fi`,
			`if (( count == 1 )); then printf '%s\\n' '{}' > "${'${summary}'}"; echo warmup-k6 >> "${calls}"; exit 0; fi`,
			`printf '%s\\n' '{"metrics":{"endpoint_prayer_current_season_duration":{"values":{"p(50)":1,"p(95)":2,"p(99)":3,"max":4}},"endpoint_prayer_current_season_requests":{"values":{"count":1,"rate":1}},"endpoint_prayer_current_season_failures":{"values":{"rate":0}}}}' > "${'${summary}'}"`,
			`sleep 3; echo measured-k6 >> "${calls}"; exit 0`, '',
		].join('\n'));
		for (const command of ['docker', 'k6', 'node']) chmodSync(join(bin, command), 0o755);
		const result = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId, EXECUTION_RUN_ID: executionRunId,
				FIXTURE_MANIFEST: manifest, BASE_URL: 'http://localhost:18080',
				WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s',
				APP_CONTAINER: 'faithlog-backend', DB_CONTAINER: 'faithlog-postgres', EXPECTED_APP_SERVICE: 'app',
				EXPECTED_DB_SERVICE: 'postgres', EXPECTED_APP_IMAGE: 'faithlog-latest',
				SAMPLING_INTERVAL_SECONDS: '2', SAMPLING_MAX_GAP_SECONDS: '4',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'admin-secret', PERF_MEMBER_PASSWORD: 'member-secret',
				PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'db-secret',
				PERF_ACCESS_TOKEN: 'stale-token', PERF_ADMIN_ACCESS_TOKEN: 'stale-admin-token', PERF_MEMBER_ACCESS_TOKEN: 'stale-member-token',
			},
			encoding: 'utf8',
			timeout: 60000,
		});
		assert.notEqual(result.status, 0, 'pending adoption policy must stop the runner after preserving evidence');
		const observed = readFileSync(calls, 'utf8').trim().split(/\r?\n/);
		for (const marker of ['warmup-k6', 'measured-k6', 'db-collector', 'metadata-child', 'summarizer-child']) {
			assert.ok(observed.includes(marker), `missing fake orchestration marker ${marker}; stdout=${result.stdout}; stderr=${result.stderr}; observed=${observed.join(',')}`);
		}
		assert.equal(observed.some((line) => line.endsWith('scope-bad') || line === 'k6-token-missing' || line.startsWith('docker-unexpected')), false);
		const report = JSON.parse(readFileSync(join(reportBase, executionRunId, 'prayer', 'prayer_current_season', 'report.json'), 'utf8'));
		assert.equal(report.accepted, false);
		assert.equal(report.automaticAdoption, false);
		assert.equal(report.measurementStatus, 'rejected');

		for (const stateFile of [k6Count, dbCount, identityCount]) rmSync(stateFile, { force: true });
		const callCountBeforeReplacement = readFileSync(calls, 'utf8').trim().split(/\r?\n/).length;
		const replacementExecutionRunId = `execreplace${process.pid}`.slice(0, 32);
		const replacementResult = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId,
				EXECUTION_RUN_ID: replacementExecutionRunId, FIXTURE_MANIFEST: manifest,
				BASE_URL: 'http://localhost:18080', WARMUP_VUS: '1', WARMUP_DURATION: '1s',
				MEASURED_VUS: '1', MEASURED_DURATION: '1s',
				APP_CONTAINER: 'faithlog-backend', DB_CONTAINER: 'faithlog-postgres', EXPECTED_APP_SERVICE: 'app',
				EXPECTED_DB_SERVICE: 'postgres', EXPECTED_APP_IMAGE: 'faithlog-latest',
				SAMPLING_INTERVAL_SECONDS: '2', SAMPLING_MAX_GAP_SECONDS: '4',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'admin-secret',
				PERF_MEMBER_PASSWORD: 'member-secret', PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog',
				PERF_DB_PASSWORD: 'db-secret', FAKE_REPLACE_RUNTIME: '1',
			},
			encoding: 'utf8',
			timeout: 60000,
		});
		assert.notEqual(replacementResult.status, 0, 'same-name container replacement must fail closed');
		assert.match(replacementResult.stderr, /App container ID changed/);
		const replacementCalls = readFileSync(calls, 'utf8').trim().split(/\r?\n/).slice(callCountBeforeReplacement);
		assert.equal(replacementCalls.some((line) => line === 'warmup-k6' || line === 'measured-k6' || line === 'login'), false,
			'runtime replacement must block login and both k6 phases');
	} finally {
		rmSync(reportBase, { recursive: true, force: true });
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
			'2026-07-14T00:00:02Z\tfaithlog-backend\t11.0%\t101MiB / 1GiB\t9.9%',
			'2026-07-14T00:00:02Z\tfaithlog-postgres\t21.0%\t201MiB / 1GiB\t19.6%',
		].join('\n'));
		writeFileSync(paths.integrity, `${integritySample()}\n${integritySample({ capturedAt: '2026-07-14T00:00:02.000Z' })}\n`);
		writeFileSync(paths.metadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
			runtime: {
				appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
				measurementStartedAt: '2026-07-14T00:00:00.000Z', measurementEndedAt: '2026-07-14T00:00:02.000Z',
				samplingIntervalSeconds: 1, samplingMaxGapSeconds: 2,
				k6ExitStatus: 0, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
				warmupExitStatus: 0, integritySamplerExitStatus: 0,
				runtimeContinuityExitStatus: 0, exclusiveWindowConfirmed: true,
				logCaptureExitStatus: 0, afterDbSnapshotExitStatus: 0,
			},
		}));

		const pendingAdoptionProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, paths.report]);
		assert.notEqual(pendingAdoptionProcess.status, 0, 'clean sampled evidence cannot auto-adopt before user policy approval');
		const report = JSON.parse(readFileSync(paths.report, 'utf8'));
		assert.deepEqual(report.http, {
			p50Ms: 10, p95Ms: 20, p99Ms: 30, maxMs: 40,
			throughputPerSecond: 1.5, requestCount: 2, failureRate: 0,
		});
		assert.equal(report.accepted, false);
		assert.equal(report.automaticAdoption, false);
		assert.equal(report.measurementStatus, 'conditional-not-adoptable');
		assert.ok(report.rejectionReasons.includes('adoption-policy-pending-user-approval'));
		assert.equal(report.db.queryCount, 4);
		assert.equal(report.db.queriesPerRequest, 2);
		assert.equal(report.db.tableCounterDelta[0].estimatedRowsAfter, '1000');
		assert.equal(report.nPlusOneEvidence.loopSignal[0].count, 4);
		assert.equal(report.resources.length, 2);
		const approvedSamplingMetadata = join(temporary, 'metadata-approved-sampling.json');
		const approvedSamplingReport = join(temporary, 'approved-sampling-report.json');
		const approvedSampling = JSON.parse(readFileSync(paths.metadata, 'utf8'));
		approvedSampling.runtime.samplingIntervalSeconds = 2;
		approvedSampling.runtime.samplingMaxGapSeconds = 4;
		writeFileSync(approvedSamplingMetadata, JSON.stringify(approvedSampling));
		const approvedSamplingProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity,
			approvedSamplingMetadata, approvedSamplingReport]);
		assert.notEqual(approvedSamplingProcess.status, 0, 'automatic adoption must remain disabled');
		const approvedSamplingResult = JSON.parse(readFileSync(approvedSamplingReport, 'utf8'));
		assert.equal(approvedSamplingResult.measurementStatus, 'conditional-not-adoptable');
		assert.equal(approvedSamplingResult.automaticAdoption, false);
		assert.equal(approvedSamplingResult.rejectionReasons.includes('invalid-sampling-contract'), false);

		for (const [name, resourceLines, expectedReason] of [
			['negative', [
				'2026-07-14T00:00:00Z\tfaithlog-backend\t-1.0%\t100MiB / 1GiB\t9.8%',
				'2026-07-14T00:00:00Z\tfaithlog-postgres\t20.0%\t200MiB / 1GiB\t19.5%',
				'2026-07-14T00:00:02Z\tfaithlog-backend\t11.0%\t101MiB / 1GiB\t9.9%',
				'2026-07-14T00:00:02Z\tfaithlog-postgres\t21.0%\t201MiB / 1GiB\t19.6%',
			], 'invalid-resource-sample'],
			['extra-container', [
				...readFileSync(paths.resources, 'utf8').split('\n'),
				'2026-07-14T00:00:00Z\tforeign-container\t1.0%\t1MiB / 1GiB\t0.1%',
				'2026-07-14T00:00:02Z\tforeign-container\t1.0%\t1MiB / 1GiB\t0.1%',
			], 'unexpected-resource-container'],
		]) {
			const resourcePath = join(temporary, `${name}-resources.tsv`);
			const resourceReport = join(temporary, `${name}-resource-report.json`);
			writeFileSync(resourcePath, resourceLines.join('\n'));
			const resourceProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
				paths.summary, paths.before, paths.after, paths.sql, resourcePath, paths.integrity, paths.metadata, resourceReport]);
			assert.notEqual(resourceProcess.status, 0);
			const resourceResult = JSON.parse(readFileSync(resourceReport, 'utf8'));
			assert.equal(resourceResult.measurementStatus, 'rejected');
			assert.ok(resourceResult.rejectionReasons.includes(expectedReason), `${name} must add ${expectedReason}`);
		}
		const unconfirmedMetadata = join(temporary, 'metadata-unconfirmed-window.json');
		const unconfirmedReport = join(temporary, 'unconfirmed-window-report.json');
		const unconfirmed = JSON.parse(readFileSync(paths.metadata, 'utf8'));
		unconfirmed.runtime.exclusiveWindowConfirmed = false;
		writeFileSync(unconfirmedMetadata, JSON.stringify(unconfirmed));
		const unconfirmedProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, unconfirmedMetadata, unconfirmedReport]);
		assert.notEqual(unconfirmedProcess.status, 0);
		assert.ok(JSON.parse(readFileSync(unconfirmedReport, 'utf8')).rejectionReasons.includes('adoption-policy-pending-user-approval'));
		const failedMetadata = join(temporary, 'metadata-k6-failed.json');
		const failedReport = join(temporary, 'k6-failed-report.json');
		writeFileSync(failedMetadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
				runtime: {
					appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
					measurementStartedAt: '2026-07-14T00:00:00.000Z', measurementEndedAt: '2026-07-14T00:00:02.000Z',
					samplingIntervalSeconds: 1, samplingMaxGapSeconds: 2,
					k6ExitStatus: 99, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
					warmupExitStatus: 0, integritySamplerExitStatus: 0,
					runtimeContinuityExitStatus: 0, exclusiveWindowConfirmed: true,
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
					measurementStartedAt: '2026-07-14T00:00:00.000Z', measurementEndedAt: '2026-07-14T00:00:02.000Z',
					samplingIntervalSeconds: 1, samplingMaxGapSeconds: 2,
					k6ExitStatus: 97, resourceSamplerExitStatus: 98, fixtureWindowExitStatus: 1,
					warmupExitStatus: 0, integritySamplerExitStatus: 94,
					runtimeContinuityExitStatus: 0, exclusiveWindowConfirmed: true,
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
		const directProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			directSummary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, directReport]);
		assert.notEqual(directProcess.status, 0);
		assert.equal(JSON.parse(readFileSync(directReport, 'utf8')).measurementStatus, 'conditional-not-adoptable');

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

		for (const [shape, metric] of [
			['values', (values) => ({ values })],
			['direct', (values) => values],
		]) {
			const invertedSummary = join(temporary, `inverted-${shape}-summary.json`);
			const invertedReport = join(temporary, `inverted-${shape}-report.json`);
			writeFileSync(invertedSummary, JSON.stringify({ metrics: {
				endpoint_poll_member_list_duration: metric({ 'p(50)': 100, 'p(95)': 50, 'p(99)': 10, max: 1 }),
				endpoint_poll_member_list_requests: metric({ count: 2, rate: 1 }),
				endpoint_poll_member_list_failures: metric({ rate: 0 }),
			} }));
			const invertedProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
				invertedSummary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, invertedReport]);
			assert.notEqual(invertedProcess.status, 0);
			assert.ok(JSON.parse(readFileSync(invertedReport, 'utf8')).rejectionReasons.includes('invalid-latency-order'));
		}

		const existingReport = join(temporary, 'existing-report.json');
		writeFileSync(existingReport, 'preserve-existing-evidence\n');
		const overwriteProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, existingReport]);
		assert.notEqual(overwriteProcess.status, 0);
		assert.equal(readFileSync(existingReport, 'utf8'), 'preserve-existing-evidence\n');

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

		const unsafeBefore = join(temporary, 'unsafe-counter-before.json');
		const unsafeAfter = join(temporary, 'unsafe-counter-after.json');
		const unsafeReport = join(temporary, 'unsafe-counter-report.json');
		writeFileSync(unsafeBefore, JSON.stringify(dbSnapshot('2026-07-14T00:00:01.000Z', {
			users: { n_tup_upd: Number.MAX_SAFE_INTEGER + 1 },
		})));
		writeFileSync(unsafeAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', {
			users: { n_tup_upd: Number.MAX_SAFE_INTEGER + 2 },
		})));
		const unsafeProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, unsafeBefore, unsafeAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, unsafeReport]);
		assert.notEqual(unsafeProcess.status, 0, 'unsafe JSON numbers must not collapse a +1 write counter delta to zero');
		assert.ok(JSON.parse(readFileSync(unsafeReport, 'utf8')).rejectionReasons.includes('invalid-db-table-snapshot'));

		const exactBefore = join(temporary, 'exact-counter-before.json');
		const exactAfter = join(temporary, 'exact-counter-after.json');
		const exactReport = join(temporary, 'exact-counter-report.json');
		writeFileSync(exactBefore, JSON.stringify(dbSnapshot('2026-07-14T00:00:01.000Z', {
			users: { seq_scan: '9007199254740992', n_tup_upd: '9007199254740992' },
		})));
		writeFileSync(exactAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', {
			users: { seq_scan: '9007199254740993', n_tup_upd: '9007199254740993' },
		})));
		const exactProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, exactBefore, exactAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, exactReport]);
		assert.notEqual(exactProcess.status, 0);
		const exactRejected = JSON.parse(readFileSync(exactReport, 'utf8'));
		assert.ok(exactRejected.rejectionReasons.includes('write-counter-delta:users:n_tup_upd:1'));
		const exactUserDelta = exactRejected.db.tableCounterDelta.find((entry) => entry.table === 'users');
		assert.equal(exactUserDelta.seq_scan, '1');
		assert.equal(exactUserDelta.n_tup_upd, '1');

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
		}, { ...EXPECTED_PLANNER_SETTINGS, plan_cache_mode: 'force_generic_plan' })));
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

		const missingSchemaBefore = join(temporary, 'missing-schema-before.json');
		const missingSchemaAfter = join(temporary, 'missing-schema-after.json');
		const missingSchemaReport = join(temporary, 'missing-schema-report.json');
		const beforeWithoutSchema = dbSnapshot('2026-07-14T00:00:00.000Z');
		const afterWithoutSchema = dbSnapshot('2026-07-14T00:00:01.000Z');
		delete beforeWithoutSchema.plannerSettings;
		delete afterWithoutSchema.plannerSettings;
		for (const row of [...beforeWithoutSchema.tables, ...afterWithoutSchema.tables]) {
			delete row.last_analyze;
			delete row.last_autoanalyze;
		}
		writeFileSync(missingSchemaBefore, JSON.stringify(beforeWithoutSchema));
		writeFileSync(missingSchemaAfter, JSON.stringify(afterWithoutSchema));
		const missingSchemaProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, missingSchemaBefore, missingSchemaAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, missingSchemaReport]);
		assert.notEqual(missingSchemaProcess.status, 0, 'identically missing planner/analyze fields must reject');
		const missingSchemaRejected = JSON.parse(readFileSync(missingSchemaReport, 'utf8'));
		assert.ok(missingSchemaRejected.rejectionReasons.includes('invalid-planner-settings'));
		assert.ok(missingSchemaRejected.rejectionReasons.includes('invalid-db-table-snapshot'));

		const sparseMetadata = join(temporary, 'sparse-metadata.json');
		const sparseResources = join(temporary, 'sparse-resources.tsv');
		const sparseIntegrity = join(temporary, 'sparse-integrity.jsonl');
		const sparseReport = join(temporary, 'sparse-report.json');
		writeFileSync(sparseMetadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v1', fixtureRunId: 'i196-test',
			runtime: {
				appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres',
				measurementStartedAt: '2026-07-14T00:00:00.000Z', measurementEndedAt: '2026-07-14T00:10:00.000Z',
				samplingIntervalSeconds: 1, samplingMaxGapSeconds: 2,
				k6ExitStatus: 0, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
				warmupExitStatus: 0, integritySamplerExitStatus: 0,
				runtimeContinuityExitStatus: 0, exclusiveWindowConfirmed: true,
				logCaptureExitStatus: 0, afterDbSnapshotExitStatus: 0,
			},
		}));
		writeFileSync(sparseResources, [
			'2026-07-14T00:00:00Z\tfaithlog-backend\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\t20.0%\t200MiB / 1GiB\t19.5%',
		].join('\n'));
		writeFileSync(sparseIntegrity, `${integritySample()}\n`);
		const sparseProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, sparseResources, sparseIntegrity, sparseMetadata, sparseReport]);
		assert.notEqual(sparseProcess.status, 0, 'a ten-minute window with one sample must reject');
		assert.ok(JSON.parse(readFileSync(sparseReport, 'utf8')).rejectionReasons.some((reason) => reason.startsWith('insufficient-samples:')));

		const gapResources = join(temporary, 'gap-resources.tsv');
		const gapIntegrity = join(temporary, 'gap-integrity.jsonl');
		const gapReport = join(temporary, 'gap-report.json');
		writeFileSync(gapResources, [
			'2026-07-14T00:00:00Z\tfaithlog-backend\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\t20.0%\t200MiB / 1GiB\t19.5%',
			'2026-07-14T00:10:00Z\tfaithlog-backend\t11.0%\t101MiB / 1GiB\t9.9%',
			'2026-07-14T00:10:00Z\tfaithlog-postgres\t21.0%\t201MiB / 1GiB\t19.6%',
		].join('\n'));
		writeFileSync(gapIntegrity, `${integritySample({ capturedAt: '2026-07-14T00:00:00.000Z' })}\n${integritySample({ capturedAt: '2026-07-14T00:10:00.000Z' })}\n`);
		const gapProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, gapResources, gapIntegrity, sparseMetadata, gapReport]);
		assert.notEqual(gapProcess.status, 0, 'a long unsampled middle gap must reject');
		assert.ok(JSON.parse(readFileSync(gapReport, 'utf8')).rejectionReasons.some((reason) => reason.startsWith('sample-gap:')));

		const vacuumAfter = join(temporary, 'vacuum-after.json');
		const vacuumReport = join(temporary, 'vacuum-report.json');
		writeFileSync(vacuumAfter, JSON.stringify(dbSnapshot('2026-07-14T00:00:02.000Z', {
			polls: { autovacuum_count: 1, last_autovacuum: '2026-07-14T00:00:01.500Z' },
		})));
		const vacuumProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, vacuumAfter, paths.sql, paths.resources, paths.integrity, paths.metadata, vacuumReport]);
		assert.notEqual(vacuumProcess.status, 0, 'vacuum/autovacuum drift must reject');
		assert.ok(JSON.parse(readFileSync(vacuumReport, 'utf8')).rejectionReasons.includes('vacuum-state-changed'));
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
	for (const name of ['BASE_URL', 'APP_CONTAINER', 'DB_CONTAINER', 'EXPECTED_APP_SERVICE', 'EXPECTED_DB_SERVICE', 'EXPECTED_APP_IMAGE']) {
		assert.match(seed, new RegExp(`required\\(['\"]${name}['\"]\\)`));
		assert.match(shaper, new RegExp(`${name}=.*:\\?`));
	}
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
	assert.match(shaper, /unset PERF_ACCESS_TOKEN PERF_ADMIN_ACCESS_TOKEN PERF_MEMBER_ACCESS_TOKEN/);
	assert.match(read('run-baseline.sh'), /export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_MEMBER_PASSWORD PERF_DB_USER PERF_DB_NAME PERF_DB_PASSWORD/);
	assert.match(read('run-baseline.sh'), /unset PERF_ACCESS_TOKEN PERF_ADMIN_ACCESS_TOKEN PERF_MEMBER_ACCESS_TOKEN/);
	assert.match(seed, /delete process\.env\[name\]/);
	assert.doesNotMatch(shaper, /docker exec -e PGPASSWORD=/);
	assert.doesNotMatch(shaper, /\bdelete\s+from\b/i);
	assert.doesNotMatch(shaper, /\btruncate\b/i);
	assert.doesNotMatch(seed, /(?:password|token)\s*:\s*['\"][^'\"]+['\"]/i);
});
