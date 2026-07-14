import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);
const JAVA_HARNESS = new URL(
	'../../src/test/java/com/faithlog/performance/notification/NotificationBatchBeforeScenarioTest.java',
	import.meta.url,
);

function readScenario(name) {
	return readFileSync(new URL(name, SCENARIO_ROOT), 'utf8');
}

test('runtime guard rejects shared Docker and every Firebase-capable profile', () => {
	const guard = readScenario('guard-runtime.sh');

	assert.match(guard, /SPRING_PROFILE/);
	assert.match(guard, /PERF_SPRING_PROFILE.*local/);
	assert.match(guard, /docker\|prod|prod\|docker/);
	assert.match(guard, /faithlog-latest/);
	assert.match(guard, /com\.docker\.compose\.project/);
	assert.match(guard, /PERF_EXPECTED_COMPOSE_PROJECT/);
	assert.match(guard, /5432\/tcp/);
	assert.match(guard, /6379\/tcp/);
	assert.match(guard, /PERF_FCM_ADAPTER.*fake/);
	assert.match(guard, /FIREBASE_CONFIG_(JSON|PATH)/);
	assert.doesNotMatch(
		guard,
		/docker compose[^\n]*(up|build|down)|docker builder prune|docker (system|image|volume) prune/,
	);
});

test('fixture contract separates datasetId and fixtureRunId and permits dummy notification rows only', () => {
	const prepare = readScenario('prepare-fixtures.sh');
	const sql = readScenario('prepare-fixtures.sql');

	assert.match(prepare, /PERF_DATASET_ID/);
	assert.match(prepare, /PERF_FIXTURE_RUN_ID/);
	assert.match(prepare, /PERF_SAMPLE_KIND/);
	assert.match(prepare, /PERF_MEMBER_COUNT.*1000/);
	assert.match(prepare, /PERF_MEMBER_COUNT.*!=.*1000/s);
	assert.match(prepare, /PERF_CAMPUS_ID.*positive integer/);
	assert.match(prepare, /faithlog-performance-.*PERF_COMPOSE_PROJECT/);
	assert.match(prepare, /manifest\.json/);
	assert.match(prepare, /build\/reports\/k6\/notification-batch\/fixtures/);
	assert.match(sql, /user_fcm_tokens/);
	assert.match(sql, /campus\.name[\s\S]*dataset_id/);
	assert.match(sql, /:'campus_id'::bigint/);
	assert.match(sql, /PERFORMANCE_198_DUMMY/);
	assert.match(sql, /success_count/);
	assert.match(sql, /transient_count/);
	assert.match(sql, /permanent_count/);
	assert.match(sql, /inactive_count/);
	assert.match(sql, /no_token_count/);
	assert.match(sql, /UPDATE\s+user_fcm_tokens[\s\S]*PERFORMANCE_198_DUMMY/i);
	assert.doesNotMatch(sql, /^\s*(TRUNCATE|DELETE\s+FROM|DROP\s+TABLE)\b/im);
	assert.doesNotMatch(prepare, /FIREBASE|FCM_TOKEN=.*[^D]UMMY/i);
});

test('runner keeps fixture preparation separate, holds a global load lock, and records actual Compose labels', () => {
	const runner = readScenario('run-before.sh');

	assert.match(runner, /\/tmp\/faithlog-performance-/);
	assert.match(runner, /NotificationBatchBeforeScenarioTest/);
	assert.match(runner, /--no-daemon/);
	assert.match(runner, /docker stats/);
	assert.match(runner, /redis-cli.*INFO.*commandstats/is);
	assert.match(runner, /environment\.json/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /SPRING_DATASOURCE_URL/);
	assert.match(runner, /SPRING_DATA_REDIS_(HOST|PORT)/);
	assert.match(runner, /SPRING_JPA_HIBERNATE_DDL_AUTO.*validate/);
	assert.match(runner, /run-status\.json/);
	assert.doesNotMatch(runner, /prepare-fixtures\.sh/);
	assert.doesNotMatch(
		runner,
		/docker compose[^\n]*(up|build|down)|docker builder prune|docker (system|image|volume) prune/,
	);
});

test('test-profile harness exercises the production request and delivery services with a deterministic fake sender', () => {
	const harness = readFileSync(JAVA_HARNESS, 'utf8');

	assert.match(harness, /@ActiveProfiles\("local"\)/);
	assert.match(harness, /@EnabledIfEnvironmentVariable/);
	assert.match(harness, /NotificationRequestCommandService/);
	assert.match(harness, /requestAutomaticNotification/);
	assert.match(harness, /NotificationDeliveryWorker/);
	assert.match(harness, /processRequest/);
	assert.match(harness, /FakeFcmSendPort/);
	assert.match(harness, /Only Issue #198 dummy tokens/);
	assert.match(harness, /FcmSendFailureType\.PERMANENT/);
	assert.match(harness, /FcmSendFailureType\.TRANSIENT/);
	assert.match(harness, /NotificationType\.PAYMENT_UNPAID/);
	assert.match(harness, /production-thread-sleep-1s-5s-30s/);
	assert.match(harness, /permanentFailurePrecededLaterSuccess/);
	assert.match(harness, /campus\.name\(\)/);
	assert.match(harness, /CapturingNotificationDispatchPort/);
	assert.match(harness, /getPrepareStatementCount/);
	assert.match(harness, /scenario-result\.json/);
	assert.doesNotMatch(harness, /FirebaseMessaging|GoogleCredentials/);
});

test('verification contract covers throughput, DB calls, status counts, dedupe, isolation, and partial failure', () => {
	const verifier = readScenario('verify-before.mjs');
	const summarizer = readScenario('summarize-before.mjs');
	const readme = readScenario('README.md');
	const requiredTerms = [
		'durationMs',
		'throughputPerSecond',
		'endToEnd',
		'dbPreparedStatements',
		'perUserDbCalls',
		'createdLogs',
		'logInsertCount',
		'logUpdateCount',
		'tokenLookupCount',
		'tokenUpdateCount',
		'SENT',
		'FAILED',
		'SKIPPED',
		'duplicateReplayCreatedCount',
		'unexpectedRequestLogCount',
	];

	for (const term of requiredTerms) assert.match(verifier, new RegExp(term));
	for (const percentile of ['p50', 'p95', 'p99', 'max']) {
		assert.match(summarizer, new RegExp(percentile));
	}
	assert.match(summarizer, /providerFakeFailureRate/);
	assert.match(summarizer, /workloadSignature/);
	assert.match(summarizer, /evidenceTotals/);
	assert.match(verifier, /postgresDelta/);
	assert.match(verifier, /redisCommandCallDelta/);
	assert.match(verifier, /dockerPeakByContainer/);
	assert.match(readme, /notificationType \+ campusId \+ scopeId \+ targetUserId \+ businessDate/);
	assert.match(readme, /partial failure/i);
	assert.match(readme, /scenario-ready/i);
	assert.match(readme, /not-measured/i);
	assert.match(readme, /실제 Firebase|external FCM/i);
	assert.match(readme, /production Java/i);
	assert.match(readme, /병렬/);
});

test('verification and summary scripts parse and aggregate synthetic non-Docker evidence', () => {
	const temporaryRoot = mkdtempSync(join(tmpdir(), 'faithlog-198-reporting-'));
	try {
		const runDir = join(temporaryRoot, 'measured-run');
		mkdirSync(runDir);
		const manifest = {
			datasetId: 'PERFORMANCE_198_SYNTHETIC',
			fixtureRunId: 'synthetic-measured-1',
			sampleKind: 'measured',
			composeProject: 'faithlog-perf-198',
			postgresDatabase: 'faithlog',
			campusId: 198,
			memberCount: 1000,
			successCount: 600,
			transientCount: 100,
			permanentCount: 100,
			inactiveCount: 100,
			noTokenCount: 100,
		};
		const result = {
			...manifest,
			requestId: '00000000-0000-0000-0000-000000000198',
			javaRuntimeVersion: 'synthetic-jvm',
			externalFcmUsed: false,
			requestServiceRevalidatesCampusMembership: false,
			creation: {
				durationMs: 100,
				throughputPerSecond: 10000,
				dbPreparedStatements: 10,
				perUserDbCalls: 0.01,
				processCpuDurationMs: 20,
				heapUsedDeltaBytes: 1024,
				createdLogs: 1000,
				logInsertCount: 1000,
				pendingLogs: 800,
				skippedLogs: 200,
				tokenLookupCount: 1000,
			},
			delivery: {
				durationMs: 1000,
				throughputPerSecond: 800,
				dbPreparedStatements: 2400,
				perUserDbCalls: 2.4,
				processCpuDurationMs: 200,
				heapUsedDeltaBytes: 2048,
				statusCounts: { SENT: 700, FAILED: 100, SKIPPED: 200, PENDING: 0 },
				logUpdateCount: 800,
				tokenLookupCount: 800,
				tokenUpdateCount: 100,
				fakeSendAttemptCount: 900,
			},
			endToEnd: { durationMs: 1100, throughputPerSecond: 909.09 },
			correctness: {
				duplicateReplayCreatedCount: 0,
				unexpectedRequestLogCount: 0,
				nonFixtureTokenMutationCount: 0,
				partialFailureContinued: true,
			},
		};
		const environment = {
			springProfile: 'local',
			fcmAdapter: 'fake',
			dockerProject: 'faithlog-perf-198',
			postgresHost: '127.0.0.1',
			postgresHostPort: 15432,
			postgresDatabase: 'faithlog',
			redisHost: '127.0.0.1',
			redisHostPort: 16379,
			postgresImageId: 'sha256:postgres-synthetic',
			redisImageId: 'sha256:redis-synthetic',
			gitCommit: 'synthetic-commit',
			sharedStack: false,
			externalFcm: false,
		};
		const postgresBefore = {
			database: { xact_commit: 10 },
			tables: { notification_logs: { n_tup_ins: 1 }, user_fcm_tokens: { n_tup_upd: 2 } },
		};
		const postgresAfter = {
			database: { xact_commit: 20 },
			tables: { notification_logs: { n_tup_ins: 1001 }, user_fcm_tokens: { n_tup_upd: 102 } },
		};

		for (const [name, value] of Object.entries({
			'manifest.json': manifest,
			'scenario-result.json': result,
			'environment.json': environment,
			'postgres-before.json': postgresBefore,
			'postgres-after.json': postgresAfter,
		})) {
			writeFileSync(join(runDir, name), `${JSON.stringify(value)}\n`, { flag: name === 'manifest.json' ? 'wx' : 'w' });
		}
		writeFileSync(join(runDir, 'redis-commandstats-before.txt'), 'cmdstat_get:calls=5,usec=10\n');
		writeFileSync(join(runDir, 'redis-commandstats-after.txt'), 'cmdstat_get:calls=8,usec=16\n');
		writeFileSync(
			join(runDir, 'docker-stats.csv'),
			'captured_at,container,cpu_percent,memory_usage,memory_percent\n'
				+ '2026-07-14T00:00:00Z,pg-198,1.5%,10MiB / 1GiB,2.5%\n',
		);

		const verifier = spawnSync(
			process.execPath,
			[fileURLToPath(new URL('verify-before.mjs', SCENARIO_ROOT))],
			{
				env: { ...process.env, MANIFEST_PATH: join(runDir, 'manifest.json'), RUN_DIR: runDir },
				encoding: 'utf8',
			},
		);
		assert.equal(verifier.status, 0, verifier.stderr);
		writeFileSync(join(runDir, 'run-status.json'), '{"status":"verified"}\n');

		const runDirsFile = join(temporaryRoot, 'run-dirs.txt');
		const outputPath = join(temporaryRoot, 'summary', 'baseline-summary.json');
		writeFileSync(runDirsFile, `${runDir}\n`);
		const summarizer = spawnSync(
			process.execPath,
			[fileURLToPath(new URL('summarize-before.mjs', SCENARIO_ROOT))],
			{
				env: { ...process.env, RUN_DIRS_FILE: runDirsFile, OUTPUT_PATH: outputPath },
				encoding: 'utf8',
			},
		);
		assert.equal(summarizer.status, 0, summarizer.stderr);
		const summary = JSON.parse(readFileSync(outputPath, 'utf8'));
		assert.equal(summary.measuredCount, 1);
		assert.equal(summary.totals.createdLogs, 1000);
		assert.equal(summary.evidenceTotals.postgresDelta.tables.notification_logs.n_tup_ins, 1000);
		assert.equal(summary.evidenceTotals.redisCommandCallDelta.get, 3);
		assert.equal(summary.evidenceTotals.dockerPeakByContainer['pg-198'].sampleCount, 1);
	} finally {
		rmSync(temporaryRoot, { recursive: true, force: true });
	}
});
