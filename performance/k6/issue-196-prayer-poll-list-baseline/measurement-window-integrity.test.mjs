import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const QUIESCENCE = join(ROOT, 'db-quiescence.mjs');
const APP_TABLES = [
	'campus_duty_assignments', 'campus_members', 'campuses', 'charge_items', 'coffee_brands',
	'coffee_menu_catalog', 'devotion_daily_checks', 'meal_poll_charge_groups', 'meal_poll_settlements',
	'notification_logs', 'payment_accounts', 'penalty_rules', 'poll_comments', 'poll_options',
	'poll_response_options', 'poll_responses', 'poll_template_options', 'poll_templates', 'polls',
	'prayer_group_members', 'prayer_groups', 'prayer_seasons', 'prayer_submissions', 'prayer_weeks',
	'user_fcm_tokens', 'users', 'weekly_devotion_records',
];
const SPRING_APPLICATION_JSON = JSON.stringify({
	logging: { level: {
		'org.hibernate.SQL': 'DEBUG',
		'org.hibernate.orm.jdbc.bind': 'OFF',
		'org.hibernate.orm.jdbc.extract': 'OFF',
	} },
	spring: { jpa: { 'show-sql': false, properties: { hibernate: { format_sql: false } } } },
});

test('DB evidence excludes Flyway metadata and gates exact write/maintenance stability before each measured snapshot', () => {
	const sql = read('db-table-stats.sql');
	const runner = read('run-baseline.sh');
	assert.match(sql, /from pg_stat_user_tables\s+where schemaname = 'public' and relname <> 'flyway_schema_history'/);
	assert.match(runner, /PERF_QUIESCENCE_TIMEOUT_SECONDS=.*:\?/);
	assert.match(runner, /wait_for_database_quiescence[\s\S]*snapshot_db_tables "\$\{before_file\}"/);
	assert.match(runner, /login_token[\s\S]*logger-probe[\s\S]*wait_for_database_quiescence/);
	assert.match(runner, /next_tick_ms[\s\S]*sleep_until_tick/,
		'runtime sampler cadence must subtract capture cost instead of sleeping after it');
});

test('quiescence requires exact application-table schema, no pending maintenance, and stable counters for the approved quiet window', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-quiescence-'));
	try {
		const evidence = join(temporary, 'quiescence.jsonl');
		writeFileSync(evidence, `${JSON.stringify(snapshot('2026-07-17T00:00:00.000Z'))}\n`);
		assert.equal(runQuiescence(evidence).status, 2, 'one snapshot cannot prove a quiet window');

		writeFileSync(evidence, `${JSON.stringify(snapshot('2026-07-17T00:00:01.000Z', { pendingMaintenanceTables: ['poll_template_options'] }))}\n`, { flag: 'a' });
		assert.equal(runQuiescence(evidence).status, 2, 'pending autoanalyze must remain non-quiescent');

		writeFileSync(evidence, `${JSON.stringify(snapshot('2026-07-17T00:00:02.000Z', { usersUpdate: '5' }))}\n`, { flag: 'a' });
		assert.equal(runQuiescence(evidence).status, 2, 'published login writes must restart the exact quiet window');

		writeFileSync(evidence, [3, 4, 5, 6].map((second) => JSON.stringify(snapshot(`2026-07-17T00:00:0${second}.000Z`, { usersUpdate: '5' }))).join('\n') + '\n', { flag: 'a' });
		const stable = runQuiescence(evidence);
		assert.equal(stable.status, 0, stable.stderr);
		assert.match(stable.stdout, /"status":"quiescent"/);
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('exact-case Hibernate statement logging is runtime-only and filter remains statement-only', () => {
	const override = read('runtime-evidence.override.yml');
	const attestation = read('runtime-env-attestation.mjs');
	assert.ok(override.includes(`SPRING_APPLICATION_JSON: '${SPRING_APPLICATION_JSON}'`));
	assert.ok(attestation.includes(`SPRING_APPLICATION_JSON=${SPRING_APPLICATION_JSON}`));
	assert.doesNotMatch(override, /LOGGING_LEVEL_ORG_HIBERNATE_SQL/,
		'relaxed environment binding lowercases the case-sensitive SQL logger name');

	const statement = '2026-07-17T00:00:00.000Z DEBUG 1 --- [nio-8080-exec-1] org.hibernate.SQL : select u1_0.id from users u1_0 where u1_0.email=?';
	const filtered = spawnSync(process.execPath, [join(ROOT, 'filter-sql-log.mjs')], { encoding: 'utf8', input: `${statement}\n` });
	assert.equal(filtered.status, 0);
	assert.equal(filtered.stdout.trim(), statement);
	const bind = spawnSync(process.execPath, [join(ROOT, 'filter-sql-log.mjs')], {
		encoding: 'utf8', input: 'org.hibernate.orm.jdbc.bind : binding parameter (1:VARCHAR) <- [secret]\n',
	});
	assert.equal(bind.status, 2);
	assert.equal(bind.stdout, '');
});

test('Docker Desktop proxy is classified separately while any other established client remains external activity', () => {
	const lsof = [
		'p123', 'ck6',
		'p456', 'ccom.docker.backend',
		'p789', 'cGoogle Chrome',
	].join('\n');
	const result = spawnSync(process.execPath, [join(ROOT, 'activity-sample.mjs')], {
		encoding: 'utf8',
		env: { ...process.env, K6_PID: '123', LSOF_TEXT: lsof, DB_ACTIVITY_JSON: '{"unexpectedSessions":[]}' },
	});
	assert.equal(result.status, 0, result.stderr);
	const sample = JSON.parse(result.stdout);
	assert.deepEqual(sample.expectedHttpInfrastructure, [{ pid: 456, command: 'com.docker.backend' }]);
	assert.deepEqual(sample.unexpectedHttpClients, [{ pid: 789, command: 'Google Chrome' }]);
	assert.match(read('run-baseline.sh'), /lsof[^\n]*-sTCP:ESTABLISHED/);
});

function read(name) {
	return readFileSync(join(ROOT, name), 'utf8');
}

function runQuiescence(path) {
	return spawnSync(process.execPath, [QUIESCENCE, path, '1', '3'], { encoding: 'utf8' });
}

function snapshot(capturedAt, { pendingMaintenanceTables = [], usersUpdate = '0' } = {}) {
	return {
		capturedAt,
		activeMaintenanceCount: '0',
		pendingMaintenanceTables,
		tables: APP_TABLES.map((relname) => ({
			relname,
			n_tup_ins: '0', n_tup_upd: relname === 'users' ? usersUpdate : '0', n_tup_del: '0',
			analyze_count: '0', autoanalyze_count: '0', vacuum_count: '0', autovacuum_count: '0',
			last_analyze: null, last_autoanalyze: null, last_vacuum: null, last_autovacuum: null,
		})),
	};
}
