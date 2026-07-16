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
	'db-runtime-identity.sql',
	'validate-runtime-identity.mjs',
	'redis-runtime-identity.mjs',
	'db-activity.sql',
	'activity-sample.mjs',
	'token-lifetime.mjs',
	'validate-published-target.mjs',
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
const SOURCE_REVISION = '6796ed146244d8f3f5b5dd7048ebe16865084a97';
const FAKE_REDIS_RUN_ID = 'a'.repeat(40);

function approvedTargetEnv() {
	return {
		BASE_URL: 'http://127.0.0.1:18080', APP_CONTAINER: 'approved-app', DB_CONTAINER: 'approved-db',
		REDIS_CONTAINER: 'approved-redis', EXPECTED_APP_SERVICE: 'app', EXPECTED_DB_SERVICE: 'postgres',
		EXPECTED_REDIS_SERVICE: 'redis', EXPECTED_APP_IMAGE: 'approved-image', EXPECTED_APP_IMAGE_ID: 'sha256:app',
		EXPECTED_DB_IMAGE: 'postgres:17', EXPECTED_DB_IMAGE_ID: 'sha256:db', EXPECTED_REDIS_IMAGE: 'redis:7-alpine',
		EXPECTED_REDIS_IMAGE_ID: 'sha256:redis', EXPECTED_REDIS_PORT: '6379', EXPECTED_FLYWAY_VERSION: '11',
		EXPECTED_SOURCE_REVISION: SOURCE_REVISION,
	};
}

function manifestRuntime(project = 'approved', overrides = {}) {
	return {
		composeProject: project, sourceRevision: SOURCE_REVISION, appService: 'app', dbService: 'postgres', redisService: 'redis',
		appConfigHash: 'app-hash', dbConfigHash: 'db-hash', redisConfigHash: 'redis-hash',
		appImage: 'approved-image', appImageId: 'sha256:app', dbImage: 'postgres:17', dbImageId: 'sha256:db',
		redisImage: 'redis:7-alpine', redisImageId: 'sha256:redis', targetPort: '18080',
		...overrides,
	};
}

function faithlogTargetEnv() {
	return {
		...approvedTargetEnv(), APP_CONTAINER: 'faithlog-backend', DB_CONTAINER: 'faithlog-postgres', REDIS_CONTAINER: 'faithlog-redis',
		EXPECTED_APP_IMAGE: 'faithlog-latest', EXPECTED_APP_IMAGE_ID: 'sha256:contract',
		EXPECTED_DB_IMAGE: 'postgres:17', EXPECTED_DB_IMAGE_ID: 'sha256:db',
		EXPECTED_REDIS_IMAGE: 'redis:7-alpine', EXPECTED_REDIS_IMAGE_ID: 'sha256:redis',
	};
}

function fakeRedisInfo(runId = FAKE_REDIS_RUN_ID) {
	return `# Server\nredis_version:7.2.0\nrun_id:${runId}\ntcp_port:6379\n`;
}

function actorManifest() {
	return {
		memberActor: { email: 'member@example.test' }, coffeeCreator: { email: 'coffee@example.test' },
		otherCoffeeDuty: { email: 'coffee-other@example.test' }, mealDuty: { email: 'meal@example.test' },
	};
}

function dutyWindows(now = Date.now()) {
	return {
		coffee: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
		mealOpen: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
		mealArchived: { endsAt: new Date(now - 91 * 86400000).toISOString() },
	};
}

function reportRuntime(overrides = {}) {
	return {
		sourceRevision: SOURCE_REVISION, expectedFlywayVersion: '11',
		appContainer: 'faithlog-backend', dbContainer: 'faithlog-postgres', redisContainer: 'faithlog-redis',
		appImageId: 'sha256:contract', expectedAppImageId: 'sha256:contract',
		dbImage: 'postgres:17', expectedDbImage: 'postgres:17', dbImageId: 'sha256:db', expectedDbImageId: 'sha256:db',
		redisImage: 'redis:7-alpine', expectedRedisImage: 'redis:7-alpine', redisImageId: 'sha256:redis', expectedRedisImageId: 'sha256:redis',
		resourceContainerIds: {
			'faithlog-backend': 'app-container-id', 'faithlog-postgres': 'db-container-id', 'faithlog-redis': 'redis-container-id',
		},
		measurementStartedAt: '2026-07-14T00:00:00.000Z', measurementEndedAt: '2026-07-14T00:00:02.000Z',
		samplingIntervalSeconds: 1, samplingMaxGapSeconds: 2,
		k6ExitStatus: 0, resourceSamplerExitStatus: 0, fixtureWindowExitStatus: 0,
		warmupExitStatus: 0, integritySamplerExitStatus: 0, runtimeContinuityExitStatus: 0,
		logCaptureExitStatus: 0, afterDbSnapshotExitStatus: 0,
		...overrides,
	};
}

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
			currentDatabase: 'faithlog', currentUser: 'faithlog', sessionUser: 'faithlog', sessionUserIsDatabaseOwner: true,
			serverAddress: '172.20.0.2', serverPort: 5432, postmasterStartedAt: '2026-07-14T00:00:00.000Z',
			latestFlywayVersion: '11', publicApplicationTableCount: 27, rlsEnabledTableCount: 27,
			forceRlsTableCount: 0, policyCount: 0, jdbcOwnedTableCount: 27,
			pgStatStatementsExtensionInstalled: false, pgStatStatementsPreloaded: false, pgStatStatementsViewAvailable: false,
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

function runFakeAdoptionSequence({ summaryBehavior = 'conditional', failure = '' } = {}) {
	const startedAt = Date.now();
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-adoption-sequence-'));
	const project = `faithlog-adoption-${process.pid}`;
	const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
	const fixtureRunId = `i196seq${suffix}`.slice(0, 32);
	const executionRunId = `execseq${suffix}`.slice(0, 32);
	const reportRoot = join(temporary, 'reports');
	const calls = join(temporary, 'calls.log');
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const manifest = join(temporary, 'fixture.json');
		const identityCount = join(temporary, 'identity-count');
		const now = Date.now();
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId, shapedAt: new Date(now).toISOString(),
			primaryCampus: actorManifest(),
			composeRuntime: manifestRuntime(project, { appImage: 'faithlog-latest', appImageId: 'sha256:contract' }),
			polls: { byKey: {
				open: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
				closed_member_visible: { endsAt: new Date(now - 2 * 86400000).toISOString() },
				closed_admin_only: { endsAt: new Date(now - 5 * 86400000).toISOString() },
				closed_expired: { endsAt: new Date(now - 8 * 86400000).toISOString() },
				scheduled_future: { startsAt: new Date(now + 2 * 86400000).toISOString() },
			}, duty: dutyWindows(now) },
		}));
		writeFileSync(join(bin, 'docker'), [
			'#!/usr/bin/env bash',
			`if [[ "$*" == *'{{.Id}}'*faithlog-backend* ]]; then count=$(cat "${identityCount}" 2>/dev/null || echo 0); count=$((count + 1)); echo "$count" > "${identityCount}"; if [[ "${'${FAKE_FAILURE:-}'}" == runtime && "$count" -ge 5 ]]; then echo app-container-replaced; else echo app-container-id; fi; exit 0; fi`,
			`if [[ "$1" == exec && "$*" == *redis-cli* ]]; then printf '%b' ${JSON.stringify(fakeRedisInfo())}; exit 0; fi`,
			'if [[ "$1" == exec ]]; then',
			`  if [[ "$*" == *app_client_addrs* ]]; then printf '%s\\n' '{"capturedAt":"2026-07-14T00:00:00Z","unexpectedSessions":[]}'; exit 0; fi`,
			`  if [[ "$*" == *faithlog_issue196_observer* ]]; then printf '%s\\n' '${JSON.stringify(dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity)}'; exit 0; fi`,
			`  printf '%s\\n' '${JSON.stringify(dbSnapshot('2026-07-14T00:00:00.000Z'))}'; exit 0`,
			'fi',
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.service*faithlog-redis*) echo redis ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*com.docker.compose.config-hash*faithlog-redis*) echo redis-hash ;;',
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
			'*"{{.Id}}"*faithlog-postgres*) echo db-container-id ;;',
			'*"{{.Id}}"*faithlog-redis*) echo redis-container-id ;;',
			'*"{{.State.StartedAt}}"*) echo 2026-07-14T00:00:00.000Z ;;',
			'*"{{.Config.Image}}"*faithlog-backend*) echo faithlog-latest ;;',
			'*"{{.Config.Image}}"*faithlog-postgres*) echo postgres:17 ;;',
			'*"{{.Config.Image}}"*faithlog-redis*) echo redis:7-alpine ;;',
			'*"{{.Image}}"*faithlog-backend*) echo sha256:contract ;;',
			'*"{{.Image}}"*faithlog-postgres*) echo sha256:db ;;',
			'*"{{.Image}}"*faithlog-redis*) echo sha256:redis ;;',
			'*"port faithlog-backend 8080/tcp"*) echo 0.0.0.0:18080 ;;',
			'*"range .Config.Env"*) printf "%s\\n" LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false FAITHLOG_SCHEDULER_ENABLED=false ;;',
			`*"stats --no-stream"*) if [[ "${'${FAKE_FAILURE:-}'}" == sampler ]]; then exit 43; fi; printf "faithlog-backend\\t10.0%%%%\\t100MiB / 1GiB\\t9.8%%%%\\nfaithlog-postgres\\t20.0%%%%\\t200MiB / 1GiB\\t19.5%%%%\\nfaithlog-redis\\t5.0%%%%\\t50MiB / 1GiB\\t4.9%%%%\\n" ;;`,
			'*"logs --since"*) echo "INFO org.hibernate.SQL: select 1" ;;',
			`*) echo "docker-unexpected:$*" >> "${calls}"; exit 88 ;;`,
			'esac', '',
		].join('\n'));
		writeFileSync(join(bin, 'node'), [
			'#!/usr/bin/env bash',
			`if [[ "$*" == *"await fetch"* ]]; then echo "login:${'${LOGIN_EMAIL}'}" >> "${calls}"; printf 'x.eyJleHAiOjQxMDI0NDQ4MDB9.x'; exit 0; fi`,
			`if [[ "$1" == *summarize-run.mjs ]]; then endpoint="$2"; report="${'${10}'}"; echo "summarize:$endpoint" >> "${calls}"; case "${'${FAKE_SUMMARY_BEHAVIOR}'}" in conditional) printf '%s\\n' '{"accepted":false,"automaticAdoption":false,"measurementStatus":"conditional-not-adoptable"}' > "$report" ;; rejected) printf '%s\\n' '{"accepted":false,"automaticAdoption":false,"measurementStatus":"rejected"}' > "$report" ;; malformed) printf '{' > "$report" ;; missing) : ;; esac; exit 2; fi`,
			'if [[ "$1" == *validate-runtime-identity.mjs ]]; then printf "%s" "$DB_RUNTIME_IDENTITY_JSON"; exit 0; fi',
			`if [[ "$1" == *redis-runtime-identity.mjs ]]; then printf '%s\\n' '{"runId":"${FAKE_REDIS_RUN_ID}","redisVersion":"7.2.0","tcpPort":6379}'; exit 0; fi`,
			'if [[ "$1" == *validate-published-target.mjs ]]; then echo 18080; exit 0; fi',
			'if [[ "$1" == *token-lifetime.mjs ]]; then exit 0; fi',
			`if [[ "$1" == *activity-sample.mjs ]]; then printf '%s\\n' '{"capturedAt":"2026-07-14T00:00:00.050Z","observerApplicationName":"faithlog_issue196_observer","unexpectedDbSessions":[],"unexpectedHttpClients":[]}'; exit 0; fi`,
			'if [[ "$1" == -e ]]; then',
			'  script="$2"',
			'  if [[ "$script" == *\'process.argv[2].split(".")\'* ]]; then',
			'    case "$4" in',
			'      fixtureRunId) printf "%s" "$FIXTURE_RUN_ID" ;;',
			'      datasetId) printf issue-196-prayer-poll-list-v2 ;;',
			'      primaryCampus.memberActor.email) printf member@example.test ;;',
			'      primaryCampus.coffeeCreator.email) printf coffee@example.test ;;',
			'      primaryCampus.otherCoffeeDuty.email) printf coffee-other@example.test ;;',
			'      primaryCampus.mealDuty.email) printf meal@example.test ;;',
			'      shapedAt) printf 2026-07-14T00:00:00.000Z ;;',
			'      composeRuntime.composeProject) printf "%s" "$FAKE_PROJECT" ;;',
			'      composeRuntime.appConfigHash) printf app-hash ;;',
			'      composeRuntime.dbConfigHash) printf db-hash ;;',
			'      composeRuntime.redisConfigHash) printf redis-hash ;;',
			'      composeRuntime.appImageId) printf sha256:contract ;;',
			'      composeRuntime.dbImageId) printf sha256:db ;;',
			'      composeRuntime.redisImageId) printf sha256:redis ;;',
			'      composeRuntime.sourceRevision) printf "%s" "$EXPECTED_SOURCE_REVISION" ;;',
			'      composeRuntime.targetPort) printf 18080 ;;',
			'      *) exit 91 ;;',
			'    esac',
			'    exit 0',
			'  fi',
			'  if [[ "$script" == *"Invalid manifest instant"* || "$script" == *"SAMPLING_INTERVAL_VALUE"* ]]; then exit 0; fi',
			'  if [[ "$script" == *"EXPECTED_DB_IDENTITY"* || "$script" == *"EXPECTED_REDIS_IDENTITY"* ]]; then exit 0; fi',
			'  if [[ "$script" == *"new Date().toISOString()"* ]]; then printf 2026-07-14T00:00:00.000Z; exit 0; fi',
			'  if [[ "$script" == *"const metadata"* ]]; then printf "%s\\n" "{}" > "$3"; exit 0; fi',
			'  if [[ "$script" == *"report.accepted !== false"* ]]; then case "$FAKE_SUMMARY_BEHAVIOR" in conditional) printf conditional-not-adoptable ;; rejected) printf rejected ;; *) exit 1 ;; esac; exit 0; fi',
			'fi',
			`exec "${process.execPath}" "$@"`, '',
		].join('\n'));
		writeFileSync(join(bin, 'k6'), [
			'#!/usr/bin/env bash',
			'summary=""; while (( $# > 0 )); do if [[ "$1" == --summary-export ]]; then summary="$2"; shift 2; else shift; fi; done',
			'phase=measured; [[ "$summary" == *warmup* ]] && phase=warmup',
			`echo "k6:${'${ENDPOINT}'}:$phase" >> "${calls}"`,
			'mkdir -p "$(dirname "$summary")"; printf "%s\\n" "{}" > "$summary"',
			`if [[ "$phase" == measured ]]; then sleep 0.12; [[ "${'${FAKE_FAILURE:-}'}" == k6 ]] && exit 42; fi`,
			'exit 0', '',
		].join('\n'));
		writeFileSync(join(bin, 'lsof'), [
			'#!/usr/bin/env bash',
			`[[ "${'${FAKE_FAILURE:-}'}" == integrity ]] && exit 44`,
			'exit 0', '',
		].join('\n'));
		for (const command of ['docker', 'node', 'k6', 'lsof']) chmodSync(join(bin, command), 0o755);

		const result = spawnSync('bash', [join(ROOT, 'run-baseline.sh'), 'prayer'], {
			env: {
				...process.env, ...faithlogTargetEnv(), PATH: `${bin}:${process.env.PATH}`,
				FIXTURE_RUN_ID: fixtureRunId, EXECUTION_RUN_ID: executionRunId, FIXTURE_MANIFEST: manifest,
				PERF_REPORT_ROOT: reportRoot,
				BASE_URL: 'http://127.0.0.1:18080', WARMUP_VUS: '1', WARMUP_DURATION: '1s',
				MEASURED_VUS: '1', MEASURED_DURATION: '1s', SAMPLING_INTERVAL_SECONDS: '0.05', SAMPLING_MAX_GAP_SECONDS: '0.1',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'admin-secret', PERF_MEMBER_PASSWORD: 'member-secret',
				PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'db-secret',
				FAKE_SUMMARY_BEHAVIOR: summaryBehavior, FAKE_FAILURE: failure, FAKE_PROJECT: project,
			},
			encoding: 'utf8', timeout: 60000,
		});
		return {
			result,
			calls: existsSync(calls) ? readFileSync(calls, 'utf8').trim().split(/\r?\n/).filter(Boolean) : [],
			reportRoot,
			elapsedMs: Date.now() - startedAt,
		};
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
}

function read(name) {
	const path = join(ROOT, name);
	return existsSync(path) ? readFileSync(path, 'utf8') : '';
}

function readRepository(relativePath) {
	return readFileSync(join(ROOT, '../../..', relativePath), 'utf8');
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

	assert.equal(FIXTURE_CONTRACT.datasetId, 'issue-196-prayer-poll-list-v2');
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

test('current develop Poll contracts are pinned without paginating the generic list', async () => {
	const pollListResponse = readRepository('src/main/java/com/faithlog/poll/controller/dto/response/PollListResponse.java');
	const pollController = readRepository('src/main/java/com/faithlog/poll/controller/PollController.java');
	const mealController = readRepository('src/main/java/com/faithlog/poll/controller/MealPollController.java');
	const pageResponse = readRepository('src/main/java/com/faithlog/global/response/PageResponse.java');
	assert.match(pollListResponse, /boolean manageableByMe/);
	assert.match(pollController, /ApiResponse<List<PollListResponse>>/);
	assert.doesNotMatch(pollController, /PageResponse<PollListResponse>/);
	assert.match(mealController, /ApiResponse<PageResponse<MealPollManagementListItemResponse>>/);
	for (const marker of ['includeArchived', 'defaultValue = "0"', 'defaultValue = "10"', 'defaultValue = "createdAt,desc"']) {
		assert.ok(mealController.includes(marker), `current MEAL list contract drifted: ${marker}`);
	}
	for (const field of ['content', 'page', 'size', 'totalElements', 'totalPages']) {
		assert.match(pageResponse, new RegExp(`\\b${field}\\b`));
	}

	const { FIXTURE_CONTRACT, MODE_ENDPOINTS } = await import(`${pathToFileURL(join(ROOT, 'fixture-contract.mjs')).href}?develop=${Date.now()}`);
	assert.equal(FIXTURE_CONTRACT.datasetId, 'issue-196-prayer-poll-list-v2');
	assert.deepEqual(FIXTURE_CONTRACT.currentDevelop, {
		sourceRevision: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		flywayVersion: '11', publicApplicationTableCount: 27, genericPollListPaginated: false,
		mealManagementMaxPageSize: 100, mealArchiveDays: 90, deterministicMealSort: 'id,desc',
	});
	assert.deepEqual(FIXTURE_CONTRACT.polls.manageableByMe, {
		custom: { admin: true, member: false, coffeeCreator: false, otherCoffeeDuty: false, mealDuty: false },
		coffee: { admin: false, member: false, coffeeCreator: true, otherCoffeeDuty: false, mealDuty: false },
		meal: { admin: false, member: false, coffeeCreator: false, otherCoffeeDuty: false, mealDuty: true },
	});
	assert.deepEqual(MODE_ENDPOINTS['poll-duty'], [
		'poll_coffee_creator_list', 'poll_other_coffee_duty_list', 'poll_meal_duty_list',
		'poll_coffee_creator_detail', 'poll_meal_duty_detail', 'poll_meal_management_default',
		'poll_meal_management_archive', 'poll_meal_management_forbidden',
	]);

	const seed = read('seed-fixture.mjs');
	for (const endpoint of [
		'/duty-assignments/coffee', '/duty-assignments/meal', '/api/v1/coffee-brands',
		'/payment-accounts', '/meal/polls',
	]) {
		assert.ok(seed.includes(endpoint), `seed does not create current-develop duty fixture: ${endpoint}`);
	}
	const scenario = read('scenario.js');
	assert.match(scenario, /mode:\s*'poll-duty'/);
	assert.match(scenario, /manageableByMe/);
	assert.match(scenario, /Object\.prototype\.hasOwnProperty\.call\([^\n]*'createdBy'\)/);
	assert.match(scenario, /includeArchived=false&page=0&size=100&sort=id%2Cdesc/);
	assert.match(scenario, /includeArchived=true&page=0&size=100&sort=id%2Cdesc/);
	for (const field of ['content', 'page', 'size', 'totalElements', 'totalPages']) {
		assert.match(scenario, new RegExp(`\\b${field}\\b`));
	}
	assert.match(scenario, /MEAL_DUTY_REQUIRED/);
	assert.doesNotMatch(scenario, /expectedResponded\s*=\s*!admin/, 'actor-specific list validation must not reference the removed admin boolean');
});

test('current develop Flyway, RLS JDBC bypass, and immutable image identity are fail-closed contracts', () => {
	const v11 = readRepository('src/main/resources/db/migration/V11__secure_supabase_data_api.sql');
	assert.match(v11, /enable row level security/i);
	assert.doesNotMatch(v11, /force row level security/i);
	assert.doesNotMatch(v11, /create policy/i);

	for (const entrypoint of ['seed-fixture.mjs', 'shape-fixture.sh', 'run-baseline.sh']) {
		const source = read(entrypoint);
		assert.match(source, /EXPECTED_APP_IMAGE_ID.*required/i, `${entrypoint} must require the approved immutable image ID`);
		assert.match(source, /EXPECTED_FLYWAY_VERSION.*required/i, `${entrypoint} must require the approved Flyway version`);
		assert.match(source, /(?:db-runtime-identity|validate-runtime-identity)/, `${entrypoint} must use the shared DB identity contract`);
	}
	const tableStats = read('db-table-stats.sql');
	const summarizer = read('summarize-run.mjs');
	for (const marker of ['latestFlywayVersion', 'rlsEnabledTableCount', 'forceRlsTableCount', 'policyCount', 'jdbcOwnedTableCount']) {
		assert.match(tableStats, new RegExp(marker));
		assert.match(summarizer, new RegExp(marker));
	}
	for (const reason of ['flyway-version-drift', 'rls-contract-drift', 'jdbc-owner-bypass-drift', 'immutable-app-image-mismatch']) {
		assert.match(summarizer, new RegExp(reason));
	}
});

test('common integrity audit pins fallback-free workload and app DB Redis continuity', () => {
	const compose = readRepository('docker-compose.yml');
	assert.match(compose, /^\s{2}redis:/m, 'Redis is part of the current app runtime and continuity is therefore relevant');
	for (const entrypoint of ['seed-fixture.mjs', 'shape-fixture.sh', 'run-baseline.sh']) {
		const source = read(entrypoint);
		for (const requiredName of [
			'EXPECTED_SOURCE_REVISION', 'EXPECTED_APP_IMAGE_ID', 'EXPECTED_DB_IMAGE', 'EXPECTED_DB_IMAGE_ID',
			'REDIS_CONTAINER', 'EXPECTED_REDIS_SERVICE', 'EXPECTED_REDIS_IMAGE', 'EXPECTED_REDIS_IMAGE_ID',
			'EXPECTED_FLYWAY_VERSION',
		]) {
			assert.match(source, new RegExp(`${requiredName}.*required`, 'i'), `${entrypoint} must require ${requiredName}`);
			assert.doesNotMatch(source, new RegExp(`${requiredName}\\s*=\\s*[^\\n]*(?::-|\\|\\|)`), `${entrypoint} must not default ${requiredName}`);
		}
		for (const marker of ['redis(?:_|)container(?:_|)id', 'redis(?:_|)image(?:_|)id', 'redis(?:_|)container(?:_|)started(?:_|)at']) {
			assert.match(source, new RegExp(marker, 'i'), `${entrypoint} missing ${marker} continuity`);
		}
		assert.match(source, /redis-runtime-identity/, `${entrypoint} must validate Redis process identity`);
	}
	const shape = read('shape-fixture.sh');
	for (const containerName of ['APP_CONTAINER', 'DB_CONTAINER', 'REDIS_CONTAINER']) {
		assert.match(shape, new RegExp(`Config\\.Image[^\\n]+\\$\\{${containerName}\\}`),
			`shape post-lock continuity must rebind the configured image for ${containerName}`);
	}
	assert.match(read('redis-runtime-identity.mjs'), /redisRunId/);
	assert.match(read('seed-fixture.mjs'), /required\(['"]PERF_WEEK_START_DATE['"]\)/i);
	assert.doesNotMatch(read('seed-fixture.mjs'), /PERF_WEEK_START_DATE\s*\|\|/);
	const runner = read('run-baseline.sh');
	assert.ok((runner.match(/assert_runtime_continuity/g) || []).length >= 5,
		'app, DB, and Redis identity must be checked before warmup, around measured, and before final reporting');
	assert.match(runner, /docker stats[^\n]*|sample_resources/);
	assert.match(runner, /redis_container_id/);
	assert.match(runner, /APP_CONTAINER_ID_VALUE/);
	assert.match(runner, /DB_CONTAINER_ID_VALUE/);
	assert.match(runner, /REDIS_CONTAINER_ID_VALUE/);
	for (const entrypoint of ['seed-fixture.mjs', 'shape-fixture.sh', 'run-baseline.sh']) {
		const source = read(entrypoint);
		for (const tokenName of [
			'PERF_COFFEE_CREATOR_ACCESS_TOKEN', 'PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN', 'PERF_MEAL_DUTY_ACCESS_TOKEN',
		]) {
			assert.match(source, new RegExp(tokenName), `${entrypoint} must remove caller-supplied ${tokenName}`);
		}
	}
});

test('common integrity audit pins pgss state, full resource identity, and primary machine rejection', () => {
	const tableStats = read('db-table-stats.sql');
	const summarizer = read('summarize-run.mjs');
	for (const marker of ['pgStatStatementsExtensionInstalled', 'pgStatStatementsPreloaded', 'pgStatStatementsViewAvailable']) {
		assert.match(tableStats, new RegExp(marker));
		assert.match(summarizer, new RegExp(marker));
	}
	assert.match(summarizer, /pgss-state-changed/);
	assert.match(summarizer, /invalid-pgss-state/);
	assert.match(summarizer, /resourceContainerId/);
	assert.match(summarizer, /primaryRejectionReason/);
	assert.match(summarizer, /automaticAdoption:\s*false/);

	const runner = read('run-baseline.sh');
	assert.match(runner, /resourceContainerId/);
	assert.match(runner, /"?\$\{REDIS_CONTAINER\}"?/);
	assert.match(runner, /APP_CONTAINER_ID_VALUE/);
	assert.match(runner, /DB_CONTAINER_ID_VALUE/);
	assert.match(runner, /REDIS_CONTAINER_ID_VALUE/);
});

test('shared DB and Redis identity validators reject source/runtime drift without side effects', async () => {
	const { validateRuntimeIdentity } = await import(`${pathToFileURL(join(ROOT, 'validate-runtime-identity.mjs')).href}?identity=${Date.now()}`);
	const valid = dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity;
	assert.doesNotThrow(() => validateRuntimeIdentity(valid, { expectedFlywayVersion: '11', expectedTableCount: 27 }));
	assert.throws(() => validateRuntimeIdentity({ ...valid, latestFlywayVersion: '10' }, {
		expectedFlywayVersion: '11', expectedTableCount: 27,
	}), /flyway-version-drift/);
	assert.throws(() => validateRuntimeIdentity({ ...valid, rlsEnabledTableCount: 26 }, {
		expectedFlywayVersion: '11', expectedTableCount: 27,
	}), /rls-contract-drift/);
	assert.throws(() => validateRuntimeIdentity({ ...valid, jdbcOwnedTableCount: 26 }, {
		expectedFlywayVersion: '11', expectedTableCount: 27,
	}), /jdbc-owner-bypass-drift/);

	const { parseRedisRuntimeIdentity } = await import(`${pathToFileURL(join(ROOT, 'redis-runtime-identity.mjs')).href}?redis=${Date.now()}`);
	assert.deepEqual(parseRedisRuntimeIdentity(fakeRedisInfo(), '6379'), {
		redisRunId: FAKE_REDIS_RUN_ID, redisVersion: '7.2.0', redisPort: 6379,
	});
	assert.throws(() => parseRedisRuntimeIdentity(fakeRedisInfo(), '6380'), /unexpected port/);
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

test('conditional evidence continues the explicit mode sequence but the completed run remains non-adoptable', () => {
	const runner = read('run-baseline.sh');
	assert.match(runner, /conditional-not-adoptable/,
		'runner must distinguish clean conditional evidence from rejected evidence');
	assert.match(runner, /overall_non_adoptable/,
		'runner must remember that every collected report remains non-adoptable');
	assert.match(runner, /for mode in "\$\{modes\[@\]\}"[\s\S]*for endpoint in[\s\S]*run_endpoint/,
		'conditional evidence must return control to the existing sequential mode loop');
	assert.match(runner, /Issue #196 baseline evidence collection finished[\s\S]*exit 2/,
		'the full evidence collection must still finish with a non-adoptable status');
});

test('fake runner preserves conditional sequencing and fails closed on report or operational errors', () => {
	const completed = runFakeAdoptionSequence();
	assert.equal(completed.result.error, undefined, `conditional sequence must not time out: ${completed.result.error}`);
	assert.equal(completed.result.signal, null, 'conditional sequence must exit normally rather than by signal');
	assert.equal(completed.result.status, 2, completed.result.stderr);
	assert.ok(completed.elapsedMs < 15000, `conditional fake sequence took ${completed.elapsedMs}ms`);
	assert.equal(completed.calls.filter((line) => line.startsWith('summarize:')).length, 5);
	assert.ok(completed.calls.includes('k6:prayer_groups:warmup'), 'conditional first endpoint must advance to the next endpoint');
	assert.match(completed.result.stderr, /baseline evidence collection finished requested mode=prayer/);
	assert.ok(completed.result.stderr.includes(completed.reportRoot), 'fake reports must stay under the isolated temporary artifact root');

	for (const [summaryBehavior, expectedStatus] of [['rejected', 2], ['malformed', 1], ['missing', 1]]) {
		const failed = runFakeAdoptionSequence({ summaryBehavior });
		assert.equal(failed.result.error, undefined, `${summaryBehavior} report must not time out: ${failed.result.error}`);
		assert.equal(failed.result.signal, null, `${summaryBehavior} report must exit normally rather than by signal`);
		assert.equal(failed.result.status, expectedStatus, `${summaryBehavior} report must return the exact fail-closed status`);
		assert.ok(failed.elapsedMs < 15000, `${summaryBehavior} fake case took ${failed.elapsedMs}ms`);
		assert.deepEqual(failed.calls.filter((line) => line.startsWith('summarize:')), ['summarize:prayer_current_season']);
		assert.equal(failed.calls.some((line) => line.startsWith('k6:prayer_groups:')), false,
			`${summaryBehavior} report must block the next endpoint`);
	}

	for (const [failure, expectedStatus] of [['k6', 42], ['sampler', 43], ['integrity', 44], ['runtime', 1]]) {
		const failed = runFakeAdoptionSequence({ failure });
		assert.equal(failed.result.error, undefined, `${failure} failure must not time out: ${failed.result.error}`);
		assert.equal(failed.result.signal, null, `${failure} failure must exit normally rather than by signal`);
		assert.equal(failed.result.status, expectedStatus, `${failure} failure must preserve its exact runner status`);
		assert.ok(failed.elapsedMs < 15000, `${failure} fake case took ${failed.elapsedMs}ms`);
		assert.deepEqual(failed.calls.filter((line) => line.startsWith('summarize:')), ['summarize:prayer_current_season']);
		assert.equal(failed.calls.some((line) => line.startsWith('k6:prayer_groups:')), false,
			`${failure} failure must block the next endpoint`);
		assert.doesNotMatch(failed.result.stderr, /continuing the approved sequential scope/,
			`${failure} failure must not claim that the runner is continuing`);
	}
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
			...approvedTargetEnv(),
			FIXTURE_RUN_ID: 'i196target', EXECUTION_RUN_ID: 'exectarget',
			WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s',
			PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'secret', PERF_MEMBER_PASSWORD: 'secret',
			PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'secret',
			SAMPLING_INTERVAL_SECONDS: '1', SAMPLING_MAX_GAP_SECONDS: '2',
		};
		for (const missing of Object.keys(approvedTargetEnv())) {
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
		const required = approvedTargetEnv();
		const manifest = join(temporary, 'fixture-manifest.json');
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId: 'i196entry', shapedAt: null,
			composeRuntime: manifestRuntime(),
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
					PERF_WEEK_START_DATE: '2026-07-13',
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

test('published target validator requires one address-family-compatible numeric loopback binding', () => {
	const validator = join(ROOT, 'validate-published-target.mjs');
	for (const [baseUrl, bindings] of [
		['http://127.0.0.1:18080', ['0.0.0.0:18080', '[::]:18080']],
		['http://127.0.0.1:18080', ['127.0.0.1:18080']],
		['http://[::1]:18080', ['[::]:18080', '0.0.0.0:18080']],
	]) {
		const valid = spawnSync(process.execPath, [validator, baseUrl, ...bindings], { encoding: 'utf8' });
		assert.equal(valid.status, 0, `${baseUrl} should bind to ${bindings.join(', ')}: ${valid.stderr}`);
		const parsed = JSON.parse(valid.stdout);
		assert.equal(parsed.hostPort, 18080);
		assert.equal(parsed.compatibleBindingCount, 1);
	}
	for (const [baseUrl, bindings] of [
		['http://localhost:18080', ['0.0.0.0:18080']],
		['http://127.0.0.1:18080', ['127.0.0.2:18080']],
		['http://127.0.0.1:18080', ['[::]:18080']],
		['http://127.0.0.1:18080', ['0.0.0.0:18080', '127.0.0.1:18080']],
		['http://127.0.0.1:18080/path', ['0.0.0.0:18080']],
	]) {
		const invalid = spawnSync(process.execPath, [validator, baseUrl, ...bindings], { encoding: 'utf8' });
		assert.notEqual(invalid.status, 0, `${baseUrl} must reject ${bindings.join(', ')}`);
	}
	for (const entrypoint of ['seed-fixture.mjs', 'shape-fixture.sh', 'run-baseline.sh']) {
		assert.match(read(entrypoint), /validate-published-target\.mjs/, `${entrypoint} must use the shared binding validator`);
	}
});

test('seed and shape reject a same-name post-lock runtime replacement before API or mutation SQL', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-post-lock-'));
	const project = `faithlog-post-lock-${process.pid}`;
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const calls = join(temporary, 'calls.log');
		const dbIdentity = JSON.stringify(dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity);
		const dbIdentityChanged = JSON.stringify({
			...dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity,
			postmasterStartedAt: '2026-07-14T00:00:01.000Z',
		});
		const makeDocker = (marker) => [
			'#!/usr/bin/env bash',
			`if [[ "$1" == port ]]; then touch "${marker}"; echo 0.0.0.0:18080; exit 0; fi`,
			`if [[ "$1" == exec && "$*" == *redis-cli* ]]; then printf '%b' ${JSON.stringify(fakeRedisInfo())}; exit 0; fi`,
			`if [[ "$1" == exec ]]; then if [[ "$*" == *"UPDATE polls"* ]]; then echo shape-sql >> "${calls}"; echo '{}'; else if [[ -f "${marker}" ]]; then printf '%s\\n' '${dbIdentityChanged}'; else printf '%s\\n' '${dbIdentity}'; fi; fi; exit 0; fi`,
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*approved-app*) echo app ;;',
			'*com.docker.compose.service*approved-db*) echo postgres ;;',
			'*com.docker.compose.service*approved-redis*) echo redis ;;',
			'*com.docker.compose.config-hash*approved-app*) echo app-hash ;;',
			'*com.docker.compose.config-hash*approved-db*) echo db-hash ;;',
			'*com.docker.compose.config-hash*approved-redis*) echo redis-hash ;;',
			'*"{{.Config.Image}}"*approved-app*) echo approved-image ;;',
			'*"{{.Config.Image}}"*approved-db*) echo postgres:17 ;;',
			'*"{{.Config.Image}}"*approved-redis*) echo redis:7-alpine ;;',
			`*"{{.Id}}"*approved-app*) if [[ -f "${marker}" ]]; then echo app-B; else echo app-A; fi ;;`,
			`*"{{.Id}}"*approved-db*) if [[ -f "${marker}" ]]; then echo db-B; else echo db-A; fi ;;`,
			`*"{{.Id}}"*approved-redis*) if [[ -f "${marker}" ]]; then echo redis-B; else echo redis-A; fi ;;`,
			'*"{{.Image}}"*approved-app*) echo sha256:app ;;',
			'*"{{.Image}}"*approved-db*) echo sha256:db ;;',
			'*"{{.Image}}"*approved-redis*) echo sha256:redis ;;',
			`*"{{.State.StartedAt}}"*) if [[ -f "${marker}" ]]; then echo 2026-07-14T00:00:01.000Z; else echo 2026-07-14T00:00:00.000Z; fi ;;`,
			'*) exit 98 ;;', 'esac', '',
		].join('\n');
		const preload = join(temporary, 'preload.cjs');
		writeFileSync(preload, `const fs=require('node:fs'); global.fetch=async()=>{fs.appendFileSync(${JSON.stringify(calls)},'seed-api\\n'); return {status:500,text:async()=>'{"success":false}'};};\n`);
		const commonEnv = {
			...process.env, ...approvedTargetEnv(), PATH: `${bin}:${process.env.PATH}`, PERF_WEEK_START_DATE: '2026-07-13',
			PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'db-secret',
		};

		const seedMarker = join(temporary, 'seed-port-seen');
		writeFileSync(join(bin, 'docker'), makeDocker(seedMarker));
		chmodSync(join(bin, 'docker'), 0o755);
		const seed = spawnSync(process.execPath, [join(ROOT, 'seed-fixture.mjs')], {
			env: { ...commonEnv, NODE_OPTIONS: `--require=${preload}`, FIXTURE_RUN_ID: `i196seed${process.pid}`.slice(0, 32),
				FIXTURE_MANIFEST: join(temporary, 'seed-manifest.json'), PERF_ADMIN_EMAIL: 'admin@example.test',
				PERF_ADMIN_PASSWORD: 'secret', PERF_MEMBER_PASSWORD: 'secret' }, encoding: 'utf8',
		});
		assert.notEqual(seed.status, 0);
		assert.match(seed.stderr, /runtime identity changed after project lock/i);
		assert.equal(existsSync(calls) ? readFileSync(calls, 'utf8').includes('seed-api') : false, false);

		rmSync(calls, { force: true });
		const shapeMarker = join(temporary, 'shape-port-seen');
		writeFileSync(join(bin, 'docker'), makeDocker(shapeMarker));
		chmodSync(join(bin, 'docker'), 0o755);
		const fixtureRunId = `i196shape${process.pid}`.slice(0, 32);
		const manifest = join(temporary, 'shape-manifest.json');
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId, shapedAt: null,
			composeRuntime: manifestRuntime(project),
			primaryCampus: { campusId: 1 }, polls: { byKey: {
				open: { id: 1 }, closed_member_visible: { id: 2 }, closed_admin_only: { id: 3 }, closed_expired: { id: 4 }, scheduled_future: { id: 5 },
			}, duty: { coffee: { id: 6 }, mealOpen: { id: 7 }, mealArchived: { id: 8 } } },
		}));
		const shape = spawnSync('bash', [join(ROOT, 'shape-fixture.sh')], {
			env: { ...commonEnv, FIXTURE_RUN_ID: fixtureRunId, FIXTURE_MANIFEST: manifest }, encoding: 'utf8',
		});
		assert.notEqual(shape.status, 0);
		assert.match(shape.stderr, /runtime identity changed after project lock/i);
		assert.equal(existsSync(calls) ? readFileSync(calls, 'utf8').includes('shape-sql') : false, false);
		assert.equal(existsSync(`${manifest}.shape-attempted`), false);
	} finally {
		rmSync(`/tmp/faithlog-performance-${project}.lock`, { recursive: true, force: true });
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

test('project-scoped lock blocks a fake same-project runner before login, mutation/measurement DB, or k6', () => {
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
			datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId: 'i196lock', shapedAt: new Date(now).toISOString(),
			primaryCampus: actorManifest(),
			composeRuntime: manifestRuntime(project, { appImage: 'faithlog-latest', appImageId: 'sha256:contract' }),
			polls: { byKey: {
				open: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
				closed_member_visible: { endsAt: new Date(now - 2 * 86400000).toISOString() },
				closed_admin_only: { endsAt: new Date(now - 5 * 86400000).toISOString() },
				closed_expired: { endsAt: new Date(now - 8 * 86400000).toISOString() },
				scheduled_future: { startsAt: new Date(now + 2 * 86400000).toISOString() },
			}, duty: dutyWindows(now) },
		}));
		writeFileSync(join(bin, 'docker'), [
			'#!/usr/bin/env bash',
			`if [[ "$1" == exec && "$*" == *redis-cli* ]]; then printf '%b' ${JSON.stringify(fakeRedisInfo())}; exit 0; fi`,
			`if [[ "$1" == exec ]]; then printf '%s\\n' '${JSON.stringify(dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity)}'; exit 0; fi`,
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.service*faithlog-redis*) echo redis ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*com.docker.compose.config-hash*faithlog-redis*) echo redis-hash ;;',
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
			'*"{{.Id}}"*faithlog-backend*) echo app-container-id ;;',
			'*"{{.Id}}"*faithlog-postgres*) echo db-container-id ;;',
			'*"{{.Id}}"*faithlog-redis*) echo redis-container-id ;;',
			'*"{{.State.StartedAt}}"*) echo 2026-07-14T00:00:00.000Z ;;',
			'*"{{.Config.Image}}"*faithlog-backend*) echo faithlog-latest ;;',
			'*"{{.Config.Image}}"*faithlog-postgres*) echo postgres:17 ;;',
			'*"{{.Config.Image}}"*faithlog-redis*) echo redis:7-alpine ;;',
			'*"{{.Image}}"*faithlog-backend*) echo sha256:contract ;;',
			'*"{{.Image}}"*faithlog-postgres*) echo sha256:db ;;',
			'*"{{.Image}}"*faithlog-redis*) echo sha256:redis ;;',
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
				...process.env, ...faithlogTargetEnv(), PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: 'i196lock', EXECUTION_RUN_ID: 'exec-lock',
				FIXTURE_MANIFEST: manifest, REPORT_ROOT: join(temporary, 'reports'), BASE_URL: 'http://127.0.0.1:18080',
				WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s', VUS: '1', DURATION: '1s',
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
			datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId, shapedAt: new Date(now).toISOString(),
			primaryCampus: actorManifest(),
			composeRuntime: manifestRuntime(project, { appImage: 'faithlog-latest', appImageId: 'sha256:contract' }),
			polls: { byKey: {
				open: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
				closed_member_visible: { endsAt: new Date(now - 2 * 86400000).toISOString() },
				closed_admin_only: { endsAt: new Date(now - 5 * 86400000).toISOString() },
				closed_expired: { endsAt: new Date(now - 8 * 86400000).toISOString() },
				scheduled_future: { startsAt: new Date(now + 2 * 86400000).toISOString() },
			}, duty: dutyWindows(now) },
		}));
		writeFileSync(join(bin, 'docker'), [
			'#!/usr/bin/env bash',
			'if [[ -n "${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}" ]]; then echo docker-credential-leak >> "' + calls + '"; fi',
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.service*faithlog-redis*) echo redis ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*com.docker.compose.config-hash*faithlog-redis*) echo redis-hash ;;',
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
			'*"{{.Id}}"*faithlog-backend*) echo app-container-id ;;',
			'*"{{.Id}}"*faithlog-postgres*) echo db-container-id ;;',
			'*"{{.Id}}"*faithlog-redis*) echo redis-container-id ;;',
			'*"{{.State.StartedAt}}"*) echo 2026-07-14T00:00:00.000Z ;;',
			'*"{{.Config.Image}}"*faithlog-backend*) echo faithlog-latest ;;',
			'*"{{.Config.Image}}"*faithlog-postgres*) echo postgres:17 ;;',
			'*"{{.Config.Image}}"*faithlog-redis*) echo redis:7-alpine ;;',
			'*"{{.Image}}"*faithlog-backend*) echo sha256:contract ;;',
			'*"{{.Image}}"*faithlog-postgres*) echo sha256:db ;;',
			'*"{{.Image}}"*faithlog-redis*) echo sha256:redis ;;',
			'*"port faithlog-backend 8080/tcp"*) echo 0.0.0.0:18080 ;;',
			'*"range .Config.Env"*) printf "%s\\n" LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false FAITHLOG_SCHEDULER_ENABLED=false ;;',
			`*"redis-cli --raw INFO server"*) printf '%b' ${JSON.stringify(fakeRedisInfo())} ;;`,
			`*"psql -X"*) printf '%s\\n' '${JSON.stringify(dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity)}' ;;`,
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
			'if [[ -z "${PERF_ADMIN_ACCESS_TOKEN:-}" || -z "${PERF_MEMBER_ACCESS_TOKEN:-}" || -z "${PERF_COFFEE_CREATOR_ACCESS_TOKEN:-}" || -z "${PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN:-}" || -z "${PERF_MEAL_DUTY_ACCESS_TOKEN:-}" ]]; then echo k6-token-missing >> "' + calls + '"; fi',
			`echo warmup-k6 >> "${calls}"`, 'exit 42', '',
		].join('\n'));
		for (const command of ['docker', 'k6', 'node']) chmodSync(join(bin, command), 0o755);
		const result = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, ...faithlogTargetEnv(), PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId, EXECUTION_RUN_ID: executionRunId,
				FIXTURE_MANIFEST: manifest, BASE_URL: 'http://127.0.0.1:18080',
				WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s',
				SAMPLING_INTERVAL_SECONDS: '2', SAMPLING_MAX_GAP_SECONDS: '4',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'admin-secret', PERF_MEMBER_PASSWORD: 'member-secret',
				PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'db-secret',
				PERF_ACCESS_TOKEN: 'stale-token', PERF_ADMIN_ACCESS_TOKEN: 'stale-admin-token', PERF_MEMBER_ACCESS_TOKEN: 'stale-member-token',
			},
		});
		assert.notEqual(result.status, 0);
		const observed = readFileSync(calls, 'utf8').trim().split(/\r?\n/);
		assert.equal(observed.filter((line) => line === 'warmup-k6').length, 1, 'measured k6 must not start');
		assert.equal(observed.filter((line) => line === 'login').length, 5, 'only warmup actor tokens are issued');
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
		const postLockMarker = join(temporary, 'post-lock-port-seen');
		const before = join(temporary, 'before.json');
		const after = join(temporary, 'after.json');
		const manifest = join(temporary, 'fixture.json');
		const now = Date.now();
		writeFileSync(before, JSON.stringify(dbSnapshot(new Date(now - 1000).toISOString())));
		writeFileSync(after, JSON.stringify(dbSnapshot(new Date(now + 1000).toISOString(), { users: { seq_scan: 1, seq_tup_read: 1000 } })));
		writeFileSync(manifest, JSON.stringify({
			datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId, shapedAt: new Date(now).toISOString(),
			primaryCampus: actorManifest(),
			composeRuntime: manifestRuntime(project, { appImage: 'faithlog-latest', appImageId: 'sha256:contract' }),
			polls: { byKey: {
				open: { startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString() },
				closed_member_visible: { endsAt: new Date(now - 2 * 86400000).toISOString() },
				closed_admin_only: { endsAt: new Date(now - 5 * 86400000).toISOString() },
				closed_expired: { endsAt: new Date(now - 8 * 86400000).toISOString() },
				scheduled_future: { startsAt: new Date(now + 2 * 86400000).toISOString() },
			}, duty: dutyWindows(now) },
		}));
		writeFileSync(join(bin, 'docker'), [
			'#!/usr/bin/env bash',
			`if [[ "$*" == *'{{.Id}}'*faithlog-backend* ]]; then count=$(cat "${identityCount}" 2>/dev/null || echo 0); count=$((count + 1)); echo "$count" > "${identityCount}"; if [[ "${'${FAKE_POST_LOCK_REPLACE:-0}'}" == 1 && -f "${postLockMarker}" ]]; then echo app-container-post-lock-replaced; elif [[ "${'${FAKE_REPLACE_RUNTIME:-0}'}" == 1 && "$count" -gt 1 ]]; then echo app-container-replaced; else echo app-container-id; fi; exit 0; fi`,
			`if [[ "$1" == exec && "$*" == *redis-cli* ]]; then printf '%b' ${JSON.stringify(fakeRedisInfo())}; exit 0; fi`,
			'if [[ "$1" == exec ]]; then',
			`  if [[ -z "${'${PGPASSWORD+x}'}" || -n "${'${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_PASSWORD+x}${PERF_ACCESS_TOKEN+x}${PERF_ADMIN_ACCESS_TOKEN+x}${PERF_MEMBER_ACCESS_TOKEN+x}'}" ]]; then echo db-scope-bad >> "${calls}"; fi`,
			`  echo db-collector >> "${calls}"`,
			`  if [[ "$*" == *app_client_addrs* ]]; then printf '%s\\n' '{"capturedAt":"2026-07-14T00:00:00Z","unexpectedSessions":[]}'; exit 0; fi`,
			`  if [[ "$*" == *faithlog_issue196_observer* ]]; then printf '%s\\n' '${JSON.stringify(dbSnapshot('2026-07-14T00:00:00.000Z').databaseIdentity)}'; exit 0; fi`,
			`  count=$(cat "${dbCount}" 2>/dev/null || echo 0); count=$((count + 1)); echo "$count" > "${dbCount}"`,
			`  if (( count == 1 )); then cat "${before}"; else cat "${after}"; fi`,
			'  exit 0', 'fi',
			`if [[ -n "${'${PGPASSWORD+x}${PERF_ADMIN_EMAIL+x}${PERF_ADMIN_PASSWORD+x}${PERF_MEMBER_PASSWORD+x}${PERF_DB_USER+x}${PERF_DB_NAME+x}${PERF_DB_PASSWORD+x}${PERF_ACCESS_TOKEN+x}${PERF_ADMIN_ACCESS_TOKEN+x}${PERF_MEMBER_ACCESS_TOKEN+x}'}" ]]; then echo docker-scope-bad >> "${calls}"; fi`,
			'case "$*" in',
			`*com.docker.compose.project*) echo "${project}" ;;`,
			'*com.docker.compose.service*faithlog-backend*) echo app ;;',
			'*com.docker.compose.service*faithlog-postgres*) echo postgres ;;',
			'*com.docker.compose.service*faithlog-redis*) echo redis ;;',
			'*com.docker.compose.config-hash*faithlog-backend*) echo app-hash ;;',
			'*com.docker.compose.config-hash*faithlog-postgres*) echo db-hash ;;',
			'*com.docker.compose.config-hash*faithlog-redis*) echo redis-hash ;;',
			'*NetworkSettings.Networks*) echo 172.20.0.3 ;;',
			'*"{{.Id}}"*faithlog-postgres*) echo db-container-id ;;',
			'*"{{.Id}}"*faithlog-redis*) echo redis-container-id ;;',
			'*"{{.State.StartedAt}}"*) echo 2026-07-14T00:00:00.000Z ;;',
			'*"{{.Config.Image}}"*faithlog-backend*) echo faithlog-latest ;;',
			'*"{{.Config.Image}}"*faithlog-postgres*) echo postgres:17 ;;',
			'*"{{.Config.Image}}"*faithlog-redis*) echo redis:7-alpine ;;',
			'*"{{.Image}}"*faithlog-backend*) echo sha256:contract ;;',
			'*"{{.Image}}"*faithlog-postgres*) echo sha256:db ;;',
			'*"{{.Image}}"*faithlog-redis*) echo sha256:redis ;;',
			`*"port faithlog-backend 8080/tcp"*) [[ "${'${FAKE_POST_LOCK_REPLACE:-0}'}" == 1 ]] && touch "${postLockMarker}"; echo 0.0.0.0:18080 ;;`,
			'*"range .Config.Env"*) printf "%s\\n" LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false FAITHLOG_SCHEDULER_ENABLED=false ;;',
			'*"stats --no-stream"*) printf "faithlog-backend\\t10.0%%\\t100MiB / 1GiB\\t9.8%%\\nfaithlog-postgres\\t20.0%%\\t200MiB / 1GiB\\t19.5%%\\nfaithlog-redis\\t5.0%%\\t50MiB / 1GiB\\t4.9%%\\n" ;;',
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
			`if [[ -z "${'${PERF_ADMIN_ACCESS_TOKEN:-}'}" || -z "${'${PERF_MEMBER_ACCESS_TOKEN:-}'}" || -z "${'${PERF_COFFEE_CREATOR_ACCESS_TOKEN:-}'}" || -z "${'${PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN:-}'}" || -z "${'${PERF_MEAL_DUTY_ACCESS_TOKEN:-}'}" ]]; then echo k6-token-missing >> "${calls}"; fi`,
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
				...process.env, ...faithlogTargetEnv(), PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId, EXECUTION_RUN_ID: executionRunId,
				FIXTURE_MANIFEST: manifest, BASE_URL: 'http://127.0.0.1:18080',
				WARMUP_VUS: '1', WARMUP_DURATION: '1s', MEASURED_VUS: '1', MEASURED_DURATION: '1s',
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
		const reportPath = join(reportBase, executionRunId, 'prayer', 'prayer_current_season', 'report.json');
		assert.equal(existsSync(reportPath), true, `missing report: stdout=${result.stdout}; stderr=${result.stderr}; calls=${observed.join(',')}`);
		const report = JSON.parse(readFileSync(reportPath, 'utf8'));
		assert.equal(report.accepted, false);
		assert.equal(report.automaticAdoption, false);
		assert.ok(['rejected', 'conditional-not-adoptable'].includes(report.measurementStatus));
		assert.ok(report.rejectionReasons.includes('adoption-policy-pending-user-approval'));

		for (const stateFile of [k6Count, dbCount, identityCount]) rmSync(stateFile, { force: true });
		const callCountBeforeReplacement = readFileSync(calls, 'utf8').trim().split(/\r?\n/).length;
		const replacementExecutionRunId = `execreplace${process.pid}`.slice(0, 32);
		const replacementResult = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, ...faithlogTargetEnv(), PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId,
				EXECUTION_RUN_ID: replacementExecutionRunId, FIXTURE_MANIFEST: manifest,
				BASE_URL: 'http://127.0.0.1:18080', WARMUP_VUS: '1', WARMUP_DURATION: '1s',
				MEASURED_VUS: '1', MEASURED_DURATION: '1s',
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

		for (const stateFile of [k6Count, dbCount, identityCount, postLockMarker]) rmSync(stateFile, { force: true });
		const callCountBeforePostLock = readFileSync(calls, 'utf8').trim().split(/\r?\n/).length;
		const postLockExecutionRunId = `execpostlock${process.pid}`.slice(0, 32);
		const postLockResult = spawnSync('bash', [runner, 'prayer'], {
			env: {
				...process.env, ...faithlogTargetEnv(), PATH: `${bin}:${process.env.PATH}`, FIXTURE_RUN_ID: fixtureRunId,
				EXECUTION_RUN_ID: postLockExecutionRunId, FIXTURE_MANIFEST: manifest,
				BASE_URL: 'http://127.0.0.1:18080', WARMUP_VUS: '1', WARMUP_DURATION: '1s',
				MEASURED_VUS: '1', MEASURED_DURATION: '1s', SAMPLING_INTERVAL_SECONDS: '2', SAMPLING_MAX_GAP_SECONDS: '4',
				PERF_ADMIN_EMAIL: 'admin@example.test', PERF_ADMIN_PASSWORD: 'admin-secret', PERF_MEMBER_PASSWORD: 'member-secret',
				PERF_DB_USER: 'faithlog', PERF_DB_NAME: 'faithlog', PERF_DB_PASSWORD: 'db-secret', FAKE_POST_LOCK_REPLACE: '1',
			},
			encoding: 'utf8', timeout: 60000,
		});
		assert.notEqual(postLockResult.status, 0, 'post-lock replacement must fail before the new runtime becomes the baseline');
		assert.match(postLockResult.stderr, /runtime identity changed after project lock/i);
		const postLockCalls = readFileSync(calls, 'utf8').trim().split(/\r?\n/).slice(callCountBeforePostLock);
		assert.equal(postLockCalls.some((line) => line === 'warmup-k6' || line === 'measured-k6' || line === 'login'), false,
			'post-lock replacement must block login and k6');
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
			'2026-07-14T00:00:00Z\tfaithlog-backend\tapp-container-id\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\tdb-container-id\t20.0%\t200MiB / 1GiB\t19.5%',
			'2026-07-14T00:00:00Z\tfaithlog-redis\tredis-container-id\t5.0%\t50MiB / 1GiB\t4.9%',
			'2026-07-14T00:00:02Z\tfaithlog-backend\tapp-container-id\t11.0%\t101MiB / 1GiB\t9.9%',
			'2026-07-14T00:00:02Z\tfaithlog-postgres\tdb-container-id\t21.0%\t201MiB / 1GiB\t19.6%',
			'2026-07-14T00:00:02Z\tfaithlog-redis\tredis-container-id\t6.0%\t51MiB / 1GiB\t5.0%',
		].join('\n'));
		writeFileSync(paths.integrity, `${integritySample()}\n${integritySample({ capturedAt: '2026-07-14T00:00:02.000Z' })}\n`);
		writeFileSync(paths.metadata, JSON.stringify({
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId: 'i196-test',
			runtime: reportRuntime(),
		}));

		const pendingAdoptionProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, paths.resources, paths.integrity, paths.metadata, paths.report]);
		assert.notEqual(pendingAdoptionProcess.status, 0, 'clean sampled evidence cannot auto-adopt before user policy approval');
		assert.equal(existsSync(paths.report), true, `summarizer did not preserve its report: ${pendingAdoptionProcess.stderr?.toString()}`);
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
		assert.equal(report.resources.length, 3);
		assert.equal(report.primaryRejectionReason, 'adoption-policy-pending-user-approval');
		assert.equal(report.resources.find((entry) => entry.container === 'faithlog-backend').maxObservedMemoryBytes, '105906176');
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
				'2026-07-14T00:00:00Z\tfaithlog-backend\tapp-container-id\t-1.0%\t100MiB / 1GiB\t9.8%',
				...readFileSync(paths.resources, 'utf8').split('\n').filter((line) => !line.includes('faithlog-backend')),
			], 'invalid-resource-sample'],
			['extra-container', [
				...readFileSync(paths.resources, 'utf8').split('\n'),
				'2026-07-14T00:00:00Z\tforeign-container\tforeign-id\t1.0%\t1MiB / 1GiB\t0.1%',
				'2026-07-14T00:00:02Z\tforeign-container\tforeign-id\t1.0%\t1MiB / 1GiB\t0.1%',
			], 'unexpected-resource-container'],
			['wrong-container-id', readFileSync(paths.resources, 'utf8').split('\n')
				.map((line) => line.replace('\tapp-container-id\t', '\tapp-container-replaced\t')),
			'resource-container-id-mismatch:faithlog-backend'],
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
		for (const [name, memoryUsage, memoryPercent] of [
			['malformed-memory', 'not-a-size / 0B', '50%'],
			['zero-limit', '100MiB / 0B', '50%'],
			['used-over-limit', '2GiB / 1GiB', '50%'],
			['percent-over-100', '100MiB / 1GiB', '999%'],
		]) {
			const malformedResource = join(temporary, `${name}.tsv`);
			const malformedResourceReport = join(temporary, `${name}-report.json`);
			writeFileSync(malformedResource, [
				`2026-07-14T00:00:00Z\tfaithlog-backend\tapp-container-id\t10.0%\t${memoryUsage}\t${memoryPercent}`,
				...readFileSync(paths.resources, 'utf8').split('\n').filter((line) => !line.includes('faithlog-backend')),
			].join('\n'));
			const malformedResourceProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
				paths.summary, paths.before, paths.after, paths.sql, malformedResource, paths.integrity, paths.metadata, malformedResourceReport]);
			assert.notEqual(malformedResourceProcess.status, 0);
			const malformedResourceResult = JSON.parse(readFileSync(malformedResourceReport, 'utf8'));
			assert.equal(malformedResourceResult.measurementStatus, 'rejected');
			assert.ok(malformedResourceResult.rejectionReasons.includes('invalid-resource-sample'), `${name} must reject`);
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
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId: 'i196-test',
			runtime: reportRuntime({ k6ExitStatus: 99 }),
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
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId: 'i196-test',
			runtime: reportRuntime({ k6ExitStatus: 97, resourceSamplerExitStatus: 98, fixtureWindowExitStatus: 1,
				integritySamplerExitStatus: 94, logCaptureExitStatus: 96, afterDbSnapshotExitStatus: 95 }),
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
			mode: 'poll-member', datasetId: 'issue-196-prayer-poll-list-v2', fixtureRunId: 'i196-test',
			runtime: reportRuntime({ measurementEndedAt: '2026-07-14T00:10:00.000Z' }),
		}));
		writeFileSync(sparseResources, [
			'2026-07-14T00:00:00Z\tfaithlog-backend\tapp-container-id\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\tdb-container-id\t20.0%\t200MiB / 1GiB\t19.5%',
			'2026-07-14T00:00:00Z\tfaithlog-redis\tredis-container-id\t5.0%\t50MiB / 1GiB\t4.9%',
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
			'2026-07-14T00:00:00Z\tfaithlog-backend\tapp-container-id\t10.0%\t100MiB / 1GiB\t9.8%',
			'2026-07-14T00:00:00Z\tfaithlog-postgres\tdb-container-id\t20.0%\t200MiB / 1GiB\t19.5%',
			'2026-07-14T00:00:00Z\tfaithlog-redis\tredis-container-id\t5.0%\t50MiB / 1GiB\t4.9%',
			'2026-07-14T00:10:00Z\tfaithlog-backend\tapp-container-id\t11.0%\t101MiB / 1GiB\t9.9%',
			'2026-07-14T00:10:00Z\tfaithlog-postgres\tdb-container-id\t21.0%\t201MiB / 1GiB\t19.6%',
			'2026-07-14T00:10:00Z\tfaithlog-redis\tredis-container-id\t6.0%\t51MiB / 1GiB\t5.0%',
		].join('\n'));
		writeFileSync(gapIntegrity, `${integritySample({ capturedAt: '2026-07-14T00:00:00.000Z' })}\n${integritySample({ capturedAt: '2026-07-14T00:10:00.000Z' })}\n`);
		const gapProcess = spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, paths.before, paths.after, paths.sql, gapResources, gapIntegrity, sparseMetadata, gapReport]);
		assert.notEqual(gapProcess.status, 0, 'a long unsampled middle gap must reject');
		assert.ok(JSON.parse(readFileSync(gapReport, 'utf8')).rejectionReasons.some((reason) => reason.startsWith('sample-gap:')));

		const pgssBefore = dbSnapshot('2026-07-14T00:00:01.000Z');
		const pgssAfter = dbSnapshot('2026-07-14T00:00:02.000Z');
		for (const snapshot of [pgssBefore, pgssAfter]) {
			Object.assign(snapshot.databaseIdentity, {
				pgStatStatementsExtensionInstalled: true,
				pgStatStatementsPreloaded: true,
				pgStatStatementsViewAvailable: true,
			});
		}
		const pgssBeforePath = join(temporary, 'pgss-before.json');
		const pgssAfterPath = join(temporary, 'pgss-after.json');
		const pgssAvailableReport = join(temporary, 'pgss-available-report.json');
		writeFileSync(pgssBeforePath, JSON.stringify(pgssBefore));
		writeFileSync(pgssAfterPath, JSON.stringify(pgssAfter));
		spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, pgssBeforePath, pgssAfterPath, paths.sql, paths.resources, paths.integrity, paths.metadata, pgssAvailableReport]);
		const pgssAvailable = JSON.parse(readFileSync(pgssAvailableReport, 'utf8'));
		assert.equal(pgssAvailable.measurementStatus, 'conditional-not-adoptable', 'stable pgss available state remains valid evidence');

		pgssAfter.databaseIdentity.pgStatStatementsPreloaded = false;
		writeFileSync(pgssAfterPath, JSON.stringify(pgssAfter));
		const pgssDriftReport = join(temporary, 'pgss-drift-report.json');
		spawnSync(process.execPath, [join(ROOT, 'summarize-run.mjs'), endpoint,
			paths.summary, pgssBeforePath, pgssAfterPath, paths.sql, paths.resources, paths.integrity, paths.metadata, pgssDriftReport]);
		const pgssDrift = JSON.parse(readFileSync(pgssDriftReport, 'utf8'));
		assert.equal(pgssDrift.measurementStatus, 'rejected');
		assert.ok(pgssDrift.rejectionReasons.includes('pgss-state-changed'));

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
	assert.match(shaper, /Atomically update all eight rows created by this fixture run/);
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
