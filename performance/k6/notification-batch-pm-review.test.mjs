import assert from 'node:assert/strict';
import {
	chmodSync,
	copyFileSync,
	existsSync,
	mkdirSync,
	mkdtempSync,
	readFileSync,
	rmSync,
	rmdirSync,
	writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn, spawnSync } from 'node:child_process';
import { test } from 'node:test';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);
const REPOSITORY_ROOT = fileURLToPath(new URL('../../', import.meta.url));

function scenarioPath(name) {
	return fileURLToPath(new URL(name, SCENARIO_ROOT));
}

function runNode(scriptName, env) {
	return spawnSync(process.execPath, [scenarioPath(scriptName)], {
		env: { ...process.env, ...env },
		encoding: 'utf8',
	});
}

function validRunArtifacts(fixtureRunId, sampleKind = 'measured') {
	const manifest = {
		datasetId: 'PERFORMANCE_198_SYNTHETIC',
		fixtureRunId,
		sampleKind,
		composeProject: 'faithlog-perf-198',
		postgresDatabase: 'faithlog',
		campusId: 198,
		memberCount: 1000,
		successCount: 600,
		transientCount: 100,
		permanentCount: 100,
		inactiveCount: 100,
		noTokenCount: 100,
		mixedTokenUserCount: 1,
		insertedDummyTokenCount: 901,
		fixturePolicy: 'dummy-token-and-generated-log-only',
		credentialRecorded: false,
	};
	const result = {
		datasetId: manifest.datasetId,
		fixtureRunId: manifest.fixtureRunId,
		sampleKind: manifest.sampleKind,
		campusId: manifest.campusId,
		requestId: `00000000-0000-0000-0000-${fixtureRunId.padEnd(12, '0').slice(0, 12)}`,
		javaRuntimeVersion: 'synthetic-jvm',
		externalFcmUsed: false,
		springProfile: 'local',
		fcmAdapter: 'deterministic-test-fake',
		notificationType: 'PAYMENT_UNPAID',
		productionContractBaseCommit: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		retryBackoffPolicy: 'production-thread-sleep-1s-5s-30s',
		deliveryTokenSnapshotPolicy: 'request-wide-bulk',
		dedupeKeyShape: 'notificationType + campusId + scopeId + targetUserId + businessDate',
		targetIsolationBoundary: 'scheduler-supplied same-campus ACTIVE member IDs',
		phaseOrder: ['creation', 'dedupe-replay', 'delivery'],
		scenarioFailureCount: 0,
		scenarioFailureRate: 0,
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
			tokenLookupCount: 1,
			tokenUpdateCount: 101,
			fakeSendAttemptCount: 901,
			fakePermanentFailureCount: 101,
			fakeTransientRetryCount: 100,
		},
		endToEnd: { durationMs: 1100, throughputPerSecond: 1000 / 1.1 },
		correctness: {
			duplicateReplayCreatedCount: 0,
			duplicateReplayDurationMs: 10,
			duplicateReplayDbPreparedStatements: 1000,
			unexpectedRequestLogCount: 0,
			nonFixtureTokenMutationCount: 0,
			partialFailureContinued: true,
			mixedTokenLogSent: true,
			mixedPermanentTokenDeactivated: 1,
		},
		capturedAt: '2026-07-14T00:02:01.000Z',
	};
	const environment = {
		springProfile: 'local',
		fcmAdapter: 'fake',
		postgresContainer: 'pg-198',
		postgresContainerId: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
		redisContainer: 'redis-198',
		redisContainerId: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
		dockerProject: 'faithlog-perf-198',
		postgresHost: '127.0.0.1',
		postgresHostPort: 15432,
		postgresDatabase: 'faithlog',
		expectedPostgresRole: 'postgres',
		redisHost: '127.0.0.1',
		redisHostPort: 16379,
		postgresImageId: 'sha256:postgres-synthetic',
		redisImageId: 'sha256:redis-synthetic',
		gitCommit: 'synthetic-commit',
		businessDate: '2026-07-14',
		executionModel: 'cold-jvm-per-sample',
		warmupScope: 'external-postgres-redis-cache-only',
		externalEvidenceWindow: 'gradle-spring-harness-lifecycle',
		dockerStatsSampleIntervalSeconds: 60,
		dockerStatsMaxGapMilliseconds: 60000,
		sharedStack: false,
		externalFcm: false,
	};
	const databaseBefore = {
		xact_commit: '10',
		xact_rollback: '0',
		blks_read: '1',
		blks_hit: '2',
		tup_returned: '3',
		tup_fetched: '4',
		tup_inserted: '5',
		tup_updated: '6',
		tup_deleted: '0',
	};
	const table = (values = {}) => ({
		seq_scan: '0',
		seq_tup_read: '0',
		idx_scan: '0',
		idx_tup_fetch: '0',
		n_tup_ins: '0',
		n_tup_upd: '0',
		n_tup_del: '0',
		...values,
	});
	const postgresBefore = {
		capturedAt: '2026-07-14T00:01:00.000Z',
		currentDatabase: 'faithlog',
		currentUser: 'postgres',
		statsReset: '2026-07-14T00:00:00.000Z',
		database: databaseBefore,
		tables: {
			campus_members: table(),
			user_fcm_tokens: table({ n_tup_upd: '2' }),
			notification_logs: table({ n_tup_ins: '1', n_tup_upd: '2' }),
		},
		cardinality: {
			userFcmTokensTotal: '2000',
			activeTokensTotal: '1000',
			issue198DummyTokensTotal: '901',
			issue198ActiveDummyTokens: '901',
			notificationLogsTotal: '10',
			issue198MarkerLogsTotal: '2',
		},
		relationBytes: { userFcmTokens: '65536', notificationLogs: '32768' },
	};
	const postgresAfter = {
		...postgresBefore,
		capturedAt: '2026-07-14T00:02:00.000Z',
		database: {
			...databaseBefore,
			xact_commit: '20',
			blks_hit: '200',
			tup_inserted: '1005',
			tup_updated: '907',
		},
		tables: {
			campus_members: table({ seq_scan: '1', seq_tup_read: '1000' }),
			user_fcm_tokens: table({ n_tup_upd: '103' }),
			notification_logs: table({ n_tup_ins: '1001', n_tup_upd: '802' }),
		},
		cardinality: {
			userFcmTokensTotal: '2000',
			activeTokensTotal: '899',
			issue198DummyTokensTotal: '901',
			issue198ActiveDummyTokens: '800',
			notificationLogsTotal: '1010',
			issue198MarkerLogsTotal: '1002',
		},
		relationBytes: { userFcmTokens: '69632', notificationLogs: '131072' },
	};
	const pgssBefore = { available: false, reason: 'extension-not-installed', rows: [] };
	const pgssAfter = { available: false, reason: 'extension-not-installed', rows: [] };
	const redisBefore = {
		capturedAt: '2026-07-14T00:01:00.000Z',
		runId: 'redis-run-id-198',
		uptimeSeconds: 100,
		tcpPort: 6379,
		dbSize: '10',
		commands: { set: '5' },
	};
	const redisAfter = {
		...redisBefore,
		capturedAt: '2026-07-14T00:02:00.000Z',
		uptimeSeconds: 160,
		dbSize: '1010',
		commands: { set: '2006' },
	};
	const evidenceWindow = {
		workloadStartedAt: '2026-07-14T00:01:10.000Z',
		workloadFinishedAt: '2026-07-14T00:01:50.000Z',
		dockerStatsSampleIntervalSeconds: 60,
		dockerStatsMaxGapMilliseconds: 60000,
	};
	const dockerStats = [
		'captured_at,component,container_name,container_id,cpu_percent,memory_used_bytes,memory_limit_bytes,memory_percent',
		`2026-07-14T00:01:00.000Z,postgres,pg-198,${environment.postgresContainerId},1.5,10485760,1073741824,0.98`,
		`2026-07-14T00:01:00.000Z,redis,redis-198,${environment.redisContainerId},0.5,5242880,1073741824,0.49`,
		`2026-07-14T00:02:00.000Z,postgres,pg-198,${environment.postgresContainerId},2.0,11534336,1073741824,1.07`,
		`2026-07-14T00:02:00.000Z,redis,redis-198,${environment.redisContainerId},0.7,6291456,1073741824,0.59`,
	].join('\n') + '\n';
	return {
		manifest,
		result,
		environment,
		postgresBefore,
		postgresAfter,
		pgssBefore,
		pgssAfter,
		redisBefore,
		redisAfter,
		evidenceWindow,
		dockerStats,
	};
}

function writeRun(root, fixtureRunId, sampleKind = 'measured') {
	const runDir = join(root, fixtureRunId);
	mkdirSync(runDir, { recursive: true });
	const artifacts = validRunArtifacts(fixtureRunId, sampleKind);
	for (const [name, value] of Object.entries({
		'manifest.json': artifacts.manifest,
		'scenario-result.json': artifacts.result,
		'environment.json': artifacts.environment,
		'postgres-before.json': artifacts.postgresBefore,
		'postgres-after.json': artifacts.postgresAfter,
		'pgss-before.json': artifacts.pgssBefore,
		'pgss-after.json': artifacts.pgssAfter,
		'redis-before.json': artifacts.redisBefore,
		'redis-after.json': artifacts.redisAfter,
		'evidence-window.json': artifacts.evidenceWindow,
		'run-status.json': { status: 'verified', accepted: false, automaticAdoption: false },
	})) {
		writeFileSync(join(runDir, name), `${JSON.stringify(value)}\n`);
	}
	writeFileSync(join(runDir, 'docker-stats.csv'), artifacts.dockerStats);
	writeFileSync(join(runDir, 'verification-report.json'), `${JSON.stringify({
		status: 'verified',
		accepted: false,
		automaticAdoption: false,
		evidence: {
			postgresBeforeCardinality: artifacts.postgresBefore.cardinality,
			postgresBeforeRelationBytes: artifacts.postgresBefore.relationBytes,
			redisDbSizeBefore: artifacts.redisBefore.dbSize,
		},
	})}\n`);
	return { runDir, artifacts };
}

function assertFails(result, message) {
	assert.notEqual(result.status, 0, `${message}\nstdout=${result.stdout}\nstderr=${result.stderr}`);
}

function writeEvidenceArtifacts(runDir, artifacts) {
	for (const [name, value] of Object.entries({
		'postgres-before.json': artifacts.postgresBefore,
		'postgres-after.json': artifacts.postgresAfter,
		'pgss-before.json': artifacts.pgssBefore,
		'pgss-after.json': artifacts.pgssAfter,
		'redis-before.json': artifacts.redisBefore,
		'redis-after.json': artifacts.redisAfter,
		'evidence-window.json': artifacts.evidenceWindow,
		'environment.json': artifacts.environment,
	})) {
		writeFileSync(join(runDir, name), `${JSON.stringify(value)}\n`);
	}
	writeFileSync(join(runDir, 'docker-stats.csv'), artifacts.dockerStats);
}

test('summarizer requires approved exact warmup/measured counts and refuses one measured sample', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-count-gate-'));
	try {
		const oneMeasured = writeRun(root, 'measured-1');
		const runDirsFile = join(root, 'runs.txt');
		const outputPath = join(root, 'summary', 'baseline-summary.json');
		writeFileSync(runDirsFile, `${oneMeasured.runDir}\n`);

		assertFails(runNode('summarize-before.mjs', { RUN_DIRS_FILE: runDirsFile, OUTPUT_PATH: outputPath }),
			'missing expected sample-count approval must fail');
		assert.equal(existsSync(outputPath), false);

		assertFails(runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '0',
			EXPECTED_MEASURED_SAMPLES: '1',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		}), 'one measured sample and zero warmups must fail before percentile generation');
		assert.equal(existsSync(outputPath), false);

		const warmup = writeRun(root, 'warmup-1', 'warmup');
		writeFileSync(runDirsFile, `${warmup.runDir}\n${oneMeasured.runDir}\n`);
		assertFails(runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		}), 'measured sample count below the approved exact count must fail');

		const secondMeasured = writeRun(root, 'measured-2');
		writeFileSync(runDirsFile, `${oneMeasured.runDir}\n${secondMeasured.runDir}\n`);
		assertFails(runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		}), 'warmup sample count below the approved exact count must fail');

		const secondWarmup = writeRun(root, 'warmup-2', 'warmup');
		writeFileSync(runDirsFile,
			`${warmup.runDir}\n${secondWarmup.runDir}\n${oneMeasured.runDir}\n${secondMeasured.runDir}\n`);
		assertFails(runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		}), 'warmup sample count above the approved exact count must fail');

		const thirdMeasured = writeRun(root, 'measured-3');
		writeFileSync(runDirsFile,
			`${warmup.runDir}\n${oneMeasured.runDir}\n${secondMeasured.runDir}\n${thirdMeasured.runDir}\n`);
		assertFails(runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		}), 'measured sample count above the approved exact count must fail');
		assert.equal(existsSync(outputPath), false);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('summarizer requires the approved 1+10 canonical snapshot-restore sequence', () => {
	const summarizer = readFileSync(scenarioPath('summarize-before.mjs'), 'utf8');
	assert.match(summarizer, /EXPECTED_WARMUP_SAMPLES must be exactly 1/);
	assert.match(summarizer, /EXPECTED_MEASURED_SAMPLES must be exactly 10/);
	assert.match(summarizer, /CUMULATIVE_STATE_STRATEGY must be snapshot-restore/);
	assert.match(summarizer, /SNAPSHOT_RECEIPT_PATH/);
	assert.match(summarizer, /RESTORE_RECEIPTS_FILE/);
	assert.match(summarizer, /validateSnapshotSequence/);
	assert.match(summarizer, /one canonical prepared fixture snapshot/);
	assert.match(summarizer, /conditional-isolated-snapshot-restored/);
	assert.doesNotMatch(summarizer, /fixture-only-cleanup/);
});

test('fixture preparation and runner honor the canonical Compose-project lock before SQL or Gradle', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-project-lock-'));
	const syntheticRepository = join(root, 'repository');
	const syntheticScenario = join(syntheticRepository, 'performance/k6/notification-batch');
	const project = `faithlog-perf-198-lock-${process.pid}-${Date.now()}`;
	const projectLock = `/tmp/faithlog-performance-${project}.lock`;
	const fixtureRunId = `lock-${process.pid}-${Date.now()}`;
	const reportRoot = join(syntheticRepository, 'build/reports/k6/notification-batch');
	const fixtureDir = join(reportRoot, 'fixtures', fixtureRunId);
	const runId = `lock-run-${process.pid}-${Date.now()}`;
	try {
		assert.equal(existsSync(projectLock), false, 'synthetic canonical lock must start absent');
		mkdirSync(projectLock);
		const binDir = join(root, 'bin');
		mkdirSync(binDir);
		mkdirSync(syntheticScenario, { recursive: true });
		for (const name of [
			'guard-runtime.sh', 'runner-lifecycle.sh', 'rejection-contract.mjs',
			'runtime-inet-contract.mjs',
			'prepare-fixtures.sh', 'prepare-fixtures.sql', 'run-before.sh',
		]) {
			copyFileSync(scenarioPath(name), join(syntheticScenario, name));
		}
		writeFileSync(
			join(syntheticScenario, 'verify-current-develop-contract.mjs'),
			'process.stdout.write("synthetic current-develop contract verified\\n");\n',
		);
		const tracePath = join(root, 'docker.trace');
		const gradleTracePath = join(root, 'gradle.trace');
		writeFileSync(tracePath, '');
		const dockerPath = join(binDir, 'docker');
		writeFileSync(dockerPath, `#!/usr/bin/env bash\nset -eu\nprintf '%s\\n' "$*" >> "$TRACE_PATH"\ncase "$*" in\n  *com.docker.compose.project*) printf '%s\\n' "$FAKE_PROJECT" ;;\n  *com.docker.compose.service*pg-198*) printf 'postgres\\n' ;;\n  *com.docker.compose.service*redis-198*) printf 'redis\\n' ;;\n  *'{{.Id}}'*pg-198*) printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\\n' ;;\n  *'{{.Id}}'*redis-198*) printf 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\\n' ;;\n  *5432/tcp*) printf '15432\\n' ;;\n  *6379/tcp*) printf '16379\\n' ;;\n  *'{{.Image}}'*pg-198*) printf 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\\n' ;;\n  *'{{.Image}}'*redis-198*) printf 'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\\n' ;;\n  *) exit 91 ;;\nesac\n`);
		chmodSync(dockerPath, 0o755);
		const gitPath = join(binDir, 'git');
		writeFileSync(gitPath, `#!/usr/bin/env bash\ncase "$*" in\n  *status*) exit 0 ;;\n  *rev-parse*) printf 'synthetic-commit\\n' ;;\n  *) exit 92 ;;\nesac\n`);
		chmodSync(gitPath, 0o755);
		const gradlePath = join(syntheticRepository, 'gradlew');
		writeFileSync(gradlePath,
			`#!/usr/bin/env bash\nprintf '%s\\n' "$*" >> "$GRADLE_TRACE_PATH"\nexit 93\n`);
		chmodSync(gradlePath, 0o755);
		mkdirSync(fixtureDir, { recursive: true });
		const manifestPath = join(fixtureDir, 'manifest.json');
		writeFileSync(manifestPath, `${JSON.stringify({
			datasetId: 'PERFORMANCE_198_SYNTHETIC', fixtureRunId, sampleKind: 'measured',
			composeProject: project, postgresDatabase: 'faithlog', campusId: 198, memberCount: 1000,
			successCount: 600, transientCount: 100, permanentCount: 100, inactiveCount: 100, noTokenCount: 100,
		})}\n`);
		const commonEnv = {
			...process.env,
			PATH: `${binDir}:${process.env.PATH}`,
			TRACE_PATH: tracePath,
			GRADLE_TRACE_PATH: gradleTracePath,
			FAKE_PROJECT: project,
			ALLOW_NOTIFICATION_BATCH_BASELINE: 'true',
			PERF_SPRING_PROFILE: 'local',
			PERF_FCM_ADAPTER: 'fake',
			PERF_EXPECTED_COMPOSE_PROJECT: project,
			PERF_EXPECTED_POSTGRES_ROLE: 'postgres',
			PERF_EXPECTED_POSTGRES_SERVER_ADDRESS: '127.0.0.1',
			POSTGRES_CONTAINER: 'pg-198',
			REDIS_CONTAINER: 'redis-198',
			PERF_EXPECTED_POSTGRES_CONTAINER_ID: 'a'.repeat(64),
			PERF_EXPECTED_REDIS_CONTAINER_ID: 'b'.repeat(64),
			PERF_EXPECTED_POSTGRES_SERVICE: 'postgres',
			PERF_EXPECTED_REDIS_SERVICE: 'redis',
			PERF_EXPECTED_POSTGRES_IMAGE_ID: `sha256:${'c'.repeat(64)}`,
			PERF_EXPECTED_REDIS_IMAGE_ID: `sha256:${'d'.repeat(64)}`,
			POSTGRES_USER: 'postgres',
			POSTGRES_PASSWORD: 'runtime-only-synthetic',
			POSTGRES_DB: 'faithlog',
			PERF_REDIS_AUTH_MODE: 'none',
			REDIS_PASSWORD: '',
			FIREBASE_CONFIG_JSON: '',
			FIREBASE_CONFIG_PATH: '',
			PERF_MEMBER_COUNT: '1000',
			PERF_DATASET_ID: 'PERFORMANCE_198_SYNTHETIC',
			PERF_FIXTURE_RUN_ID: fixtureRunId,
			PERF_SAMPLE_KIND: 'canonical',
			PERF_CAMPUS_ID: '198',
			PERF_SUCCESS_COUNT: '600',
			PERF_TRANSIENT_COUNT: '100',
			PERF_PERMANENT_COUNT: '100',
			PERF_INACTIVE_COUNT: '100',
			PERF_NO_TOKEN_COUNT: '100',
			PERF_BUSINESS_DATE: '2026-07-14',
			PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS: '60',
			PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS: '60000',
		};
		const prepare = spawnSync('bash', [join(syntheticScenario, 'prepare-fixtures.sh')], {
			env: commonEnv, encoding: 'utf8',
		});
		assertFails(prepare, 'fixture preparation must fail on the canonical project lock');
		assert.match(readFileSync(tracePath, 'utf8'), /com\.docker\.compose\.project/, prepare.stderr);
		assert.doesNotMatch(readFileSync(tracePath, 'utf8'), /exec.*psql/);

		writeFileSync(tracePath, '');
		const runner = spawnSync('bash', [join(syntheticScenario, 'run-before.sh')], {
			env: { ...commonEnv, MANIFEST_PATH: manifestPath, RUN_ID: runId }, encoding: 'utf8',
		});
		assertFails(runner, 'runner must fail on the canonical project lock');
		assert.doesNotMatch(readFileSync(tracePath, 'utf8'), /exec.*(psql|redis-cli)/);
		assert.equal(existsSync(gradleTracePath), false, 'Gradle invocation count must be exactly zero');
		assert.equal(existsSync(join(reportRoot, 'runs', runId)), false);
		assert.equal(existsSync(projectLock), true, 'runner must never release a canonical lock it did not acquire');
	} finally {
		rmSync(root, { recursive: true, force: true });
		rmSync(fixtureDir, { recursive: true, force: true });
		if (existsSync(projectLock)) rmdirSync(projectLock);
	}
});

test('runtime continuity rejects same-name container replacement before verification', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-runtime-continuity-'));
	try {
		const runner = readFileSync(scenarioPath('run-before.sh'), 'utf8');
		for (const phase of ['locked', 'initial', 'before', 'after', 'final']) {
			assert.match(runner, new RegExp(`runtime-identity-${phase}\\.json`));
		}
		assert.ok(
			runner.indexOf('assert-runtime-continuity.mjs') < runner.indexOf('verify-before.mjs'),
			'runtime continuity must execute before result verification',
		);
		assert.ok(
			runner.indexOf('RUNTIME_IDENTITY_PHASES=locked,initial,before')
				< runner.indexOf('--tests com.faithlog.performance.notification.NotificationBatchBeforeScenarioTest'),
			'locked/initial/before continuity must execute before the mutating Gradle workload',
		);
		assert.match(runner,
			/RUNTIME_IDENTITY_PHASES=locked,initial,before,after,final RUN_DIR=.*assert-runtime-continuity\.mjs/s,
			'final continuity must not inherit a reduced phase list');
		const identity = (capturedAt, uptimeSeconds) => ({
			capturedAt,
			postgres: {
				container: {
					name: 'pg-198', id: 'pg-id-198', imageId: 'sha256:pg', startedAt: '2026-07-14T00:00:00Z',
					composeProject: 'faithlog-perf-198', composeService: 'postgres', composeConfigHash: 'pg-config',
					hostPort: 15432,
				},
				server: {
					database: 'faithlog', currentUser: 'postgres', address: '127.0.0.1', port: 5432,
					postmasterStartTime: '2026-07-14T00:00:00Z',
				},
			},
			redis: {
				container: {
					name: 'redis-198', id: 'redis-id-198', imageId: 'sha256:redis', startedAt: '2026-07-14T00:00:00Z',
					composeProject: 'faithlog-perf-198', composeService: 'redis', composeConfigHash: 'redis-config',
					hostPort: 16379,
				},
				server: { runId: 'redis-run-198', uptimeSeconds, port: 6379 },
			},
		});
		const phases = [
			['locked', '2026-07-14T00:00:59Z', 99],
			['initial', '2026-07-14T00:01:00Z', 100],
			['before', '2026-07-14T00:01:01Z', 101],
			['after', '2026-07-14T00:02:00Z', 160],
			['final', '2026-07-14T00:02:01Z', 161],
		];
		for (const [phase, capturedAt, uptime] of phases) {
			writeFileSync(join(root, `runtime-identity-${phase}.json`), `${JSON.stringify(identity(capturedAt, uptime))}\n`);
		}
		const valid = runNode('assert-runtime-continuity.mjs', { RUN_DIR: root });
		assert.equal(valid.status, 0, valid.stderr);

		const writeValidPhases = () => {
			for (const [phase, capturedAt, uptime] of phases) {
				writeFileSync(join(root, `runtime-identity-${phase}.json`),
					`${JSON.stringify(identity(capturedAt, uptime))}\n`);
			}
		};
		const mutations = [];
		for (const phase of ['initial', 'before', 'after', 'final']) {
			for (const side of ['postgres', 'redis']) {
				for (const field of [
					'name', 'id', 'imageId', 'startedAt', 'composeProject', 'composeService', 'composeConfigHash',
					'hostPort',
				]) {
					mutations.push({
						name: `${phase} ${side} container ${field}`,
						phase,
						mutate: (value) => {
							value[side].container[field] = field === 'hostPort'
								? (side === 'postgres' ? 25432 : 26379)
								: `replacement-${field}`;
						},
					});
				}
			}
			for (const field of ['database', 'currentUser', 'address', 'port', 'postmasterStartTime']) {
				mutations.push({
					name: `${phase} PostgreSQL server ${field}`,
					phase,
					mutate: (value) => { value.postgres.server[field] = field === 'port' ? 15432 : `replacement-${field}`; },
				});
			}
			for (const [field, replacement] of [['runId', 'replacement-run'], ['port', 16379]]) {
				mutations.push({
					name: `${phase} Redis server ${field}`,
					phase,
					mutate: (value) => { value.redis.server[field] = replacement; },
				});
			}
		}
		for (const mutation of mutations) {
			writeValidPhases();
			const phaseIndex = phases.findIndex(([phase]) => phase === mutation.phase);
			const [, capturedAt, uptime] = phases[phaseIndex];
			const changed = identity(capturedAt, uptime);
			mutation.mutate(changed);
			writeFileSync(join(root, `runtime-identity-${mutation.phase}.json`), `${JSON.stringify(changed)}\n`);
			assertFails(runNode('assert-runtime-continuity.mjs', { RUN_DIR: root }),
				`${mutation.name} change must fail continuity`);
		}
		for (const phase of ['initial', 'before', 'after', 'final']) {
			writeValidPhases();
			const phaseIndex = phases.findIndex(([name]) => name === phase);
			const [, capturedAt] = phases[phaseIndex];
			const restarted = identity(capturedAt, 1);
			writeFileSync(join(root, `runtime-identity-${phase}.json`), `${JSON.stringify(restarted)}\n`);
			assertFails(runNode('assert-runtime-continuity.mjs', { RUN_DIR: root }),
				`${phase} Redis uptime reset must fail continuity`);
		}

		writeValidPhases();
		const beforeReplacement = identity('2026-07-14T00:01:01Z', 101);
		beforeReplacement.redis.container.id = 'replacement-before-workload';
		writeFileSync(join(root, 'runtime-identity-before.json'), `${JSON.stringify(beforeReplacement)}\n`);
		const mutationTrace = join(root, 'gradle-mutation.trace');
		const fakeGradle = join(root, 'gradlew');
		writeFileSync(fakeGradle, `#!/usr/bin/env bash\nprintf 'mutation\\n' >> "$MUTATION_TRACE"\n`);
		chmodSync(fakeGradle, 0o755);
		const preWorkload = spawnSync('bash', ['-c',
			`RUNTIME_IDENTITY_PHASES=locked,initial,before RUN_DIR="$RUN_DIR" node "$VALIDATOR" && "$FAKE_GRADLE"`], {
			env: {
				...process.env,
				RUN_DIR: root,
				VALIDATOR: scenarioPath('assert-runtime-continuity.mjs'),
				FAKE_GRADLE: fakeGradle,
				MUTATION_TRACE: mutationTrace,
			},
			encoding: 'utf8',
		});
		assertFails(preWorkload, 'before-phase replacement must fail before the fake mutating workload');
		assert.equal(existsSync(mutationTrace), false, 'Gradle/DB mutation count must be exactly zero');
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('PostgreSQL inet evidence preserves raw host CIDR and compares only canonical loopback hosts', async () => {
	const { validatePostgresServerAddress } = await import(
		new URL('./notification-batch/runtime-inet-contract.mjs', import.meta.url));
	for (const [actual, approved] of [
		['127.0.0.1/32', '127.0.0.1'],
		['127.0.0.1', '127.0.0.1'],
		['::1/128', '::1'],
		['0:0:0:0:0:0:0:1/128', '::1'],
		['0:0:0:0:0:0:0:1', '::1'],
	]) assert.equal(validatePostgresServerAddress(actual, approved), actual);
	for (const [actual, approved] of [
		['127.0.0.1/24', '127.0.0.1'], ['::1/64', '::1'],
		['127.0.0.2/32', '127.0.0.1'], ['127.1.0.1/32', '127.0.0.1'],
		['10.0.0.1/32', '127.0.0.1'], ['2001:db8::1/128', '::1'],
		['127.0.0.1/32/32', '127.0.0.1'], ['not-an-ip', '127.0.0.1'],
		['127.0.0.1/32', '127.0.0.1/32'], ['::1/128', '::1/128'],
	]) assert.throws(() => validatePostgresServerAddress(actual, approved), /loopback|CIDR|address|target/i);

	const capture = readFileSync(scenarioPath('capture-runtime-identity.sh'), 'utf8');
	assert.match(capture, /PERF_EXPECTED_POSTGRES_SERVER_ADDRESS/);
	assert.match(capture, /runtime-inet-contract\.mjs/);
	assert.match(capture, /server:\s*postgresServer/);
});

test('post-lock runtime truth rejects a guard-to-lock replacement before SQL or workload', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-post-lock-race-'));
	const project = `faithlog-perf-198-race-${process.pid}-${Date.now()}`;
	const projectLock = `/tmp/faithlog-performance-${project}.lock`;
	try {
		const runner = readFileSync(scenarioPath('run-before.sh'), 'utf8');
		const prepare = readFileSync(scenarioPath('prepare-fixtures.sh'), 'utf8');
		for (const script of [runner, prepare]) {
			assert.ok(script.indexOf('acquire_notification_batch_locks')
				< script.indexOf('verify_notification_batch_runtime_after_lock'));
		}
		assert.match(runner, /runtime-identity-locked\.json/);

		const binDir = join(root, 'bin');
		mkdirSync(binDir);
		const tracePath = join(root, 'docker.trace');
		const projectCountPath = join(root, 'project.count');
		writeFileSync(tracePath, '');
		const dockerPath = join(binDir, 'docker');
		writeFileSync(dockerPath, `#!/usr/bin/env bash
set -eu
printf '%s\\n' "$*" >> "$TRACE_PATH"
case "$*" in
  *com.docker.compose.project*)
    count=0; [[ -f "$PROJECT_COUNT_PATH" ]] && count="$(<"$PROJECT_COUNT_PATH")"
    count=$((count + 1)); printf '%s' "$count" > "$PROJECT_COUNT_PATH"
    if (( count <= 2 )); then printf '%s\\n' "$FAKE_PROJECT"; else printf 'replacement-project\\n'; fi ;;
  *'{{.Id}}'*pg-198*) printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\\n' ;;
  *'{{.Id}}'*redis-198*) printf 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\\n' ;;
  *'{{.Image}}'*pg-198*) printf 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\\n' ;;
  *'{{.Image}}'*redis-198*) printf 'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\\n' ;;
  *'{{.State.StartedAt}}'*) printf '2026-07-14T00:00:00Z\\n' ;;
  *com.docker.compose.service*pg-198*) printf 'postgres\\n' ;;
  *com.docker.compose.service*redis-198*) printf 'redis\\n' ;;
  *com.docker.compose.config-hash*) printf 'synthetic-config\\n' ;;
  *5432/tcp*) printf '15432\\n' ;;
  *6379/tcp*) printf '16379\\n' ;;
  *) exit 91 ;;
esac
`);
		chmodSync(dockerPath, 0o755);
		const command = `
source "${scenarioPath('guard-runtime.sh')}"
trap release_notification_batch_locks EXIT
guard_notification_batch_runtime
acquire_notification_batch_locks
verify_notification_batch_runtime_after_lock "${join(root, 'runtime-identity-locked.json')}"
`;
		const result = spawnSync('bash', ['-c', command], {
			env: {
				...process.env,
				PATH: `${binDir}:${process.env.PATH}`,
				TRACE_PATH: tracePath,
				PROJECT_COUNT_PATH: projectCountPath,
				FAKE_PROJECT: project,
				ALLOW_NOTIFICATION_BATCH_BASELINE: 'true',
				PERF_SPRING_PROFILE: 'local',
				PERF_FCM_ADAPTER: 'fake',
				PERF_EXPECTED_COMPOSE_PROJECT: project,
				POSTGRES_CONTAINER: 'pg-198',
				REDIS_CONTAINER: 'redis-198',
				PERF_EXPECTED_POSTGRES_CONTAINER_ID: 'a'.repeat(64),
				PERF_EXPECTED_REDIS_CONTAINER_ID: 'b'.repeat(64),
				PERF_EXPECTED_POSTGRES_SERVICE: 'postgres',
				PERF_EXPECTED_REDIS_SERVICE: 'redis',
				PERF_EXPECTED_POSTGRES_IMAGE_ID: `sha256:${'c'.repeat(64)}`,
				PERF_EXPECTED_REDIS_IMAGE_ID: `sha256:${'d'.repeat(64)}`,
				POSTGRES_USER: 'faithlog',
				POSTGRES_PASSWORD: 'runtime-only-synthetic',
				POSTGRES_DB: 'faithlog',
				PERF_EXPECTED_POSTGRES_ROLE: 'faithlog',
				PERF_EXPECTED_POSTGRES_SERVER_ADDRESS: '127.0.0.1',
				PERF_REDIS_AUTH_MODE: 'none',
				REDIS_PASSWORD: '',
				FIREBASE_CONFIG_JSON: '',
				FIREBASE_CONFIG_PATH: '',
			},
			encoding: 'utf8',
		});
		assertFails(result, 'same-name replacement after preliminary discovery must fail post-lock validation');
		assert.match(readFileSync(tracePath, 'utf8'), /com\.docker\.compose\.project/, result.stderr);
		assert.doesNotMatch(readFileSync(tracePath, 'utf8'), /exec.*(psql|redis-cli)/);
	} finally {
		if (existsSync(projectLock)) rmdirSync(projectLock);
		rmSync(root, { recursive: true, force: true });
	}
});

test('dummy-token classification uses an exact prefix and sampling is immutable fail-closed', () => {
	const sql = readFileSync(scenarioPath('prepare-fixtures.sql'), 'utf8');
	const runner = readFileSync(scenarioPath('run-before.sh'), 'utf8');
	const redisParser = readFileSync(scenarioPath('parse-redis-evidence.mjs'), 'utf8');
	const dockerCapture = readFileSync(scenarioPath('capture-docker-stats.sh'), 'utf8');
	const identityCapture = readFileSync(scenarioPath('capture-runtime-identity.sh'), 'utf8');
	const prepare = readFileSync(scenarioPath('prepare-fixtures.sh'), 'utf8');
	assert.doesNotMatch(sql, /LIKE\s+'PERFORMANCE_198_DUMMY:/i);
	assert.doesNotMatch(runner, /LIKE\s+'PERFORMANCE_198_DUMMY:/i);
	assert.match(sql, /starts_with\(token\.token,\s*'PERFORMANCE_198_DUMMY:'\)/i);
	assert.match(sql, /starts_with\(\s*token\.client_instance_id,\s*'PERFORMANCE_198_DUMMY:'\s*\|\|\s*config\.fixture_run_id\s*\|\|\s*':'\s*\)/i);
	assert.match(dockerCapture, /POSTGRES_OBSERVED_ID=.*PERF_POSTGRES_CONTAINER_ID/);
	assert.match(dockerCapture, /REDIS_OBSERVED_ID=.*PERF_REDIS_CONTAINER_ID/);
	assert.match(dockerCapture, /docker stats[\s\S]*POSTGRES_OBSERVED_ID/);
	assert.match(dockerCapture, /docker stats[\s\S]*REDIS_OBSERVED_ID/);
	assert.doesNotMatch(redisParser, /cmdstat_set[\s\S]{0,120}\?\?\s*["']0["']/);
	assert.match(redisParser, /Redis evidence missing cmdstat_set/);
	for (const script of [runner, identityCapture, prepare]) {
		assert.doesNotMatch(script, /docker exec(?: -i)? "\$\{POSTGRES_CONTAINER\}"/);
		assert.doesNotMatch(script, /docker exec(?: -i)? "\$\{REDIS_CONTAINER\}"/);
	}
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-missing-redis-set-'));
	try {
		const outputPath = join(root, 'redis.json');
		const result = runNode('parse-redis-evidence.mjs', {
			REDIS_EVIDENCE_OUTPUT_PATH: outputPath,
			REDIS_DBSIZE: '0',
			REDIS_SERVER_INFO: 'run_id:redis-run-198\nuptime_in_seconds:100\ntcp_port:6379\n',
			REDIS_COMMANDSTATS: 'cmdstat_get:calls=1,usec=1,usec_per_call=1.00\n',
			REDIS_CAPTURED_AT: '2026-07-14T00:00:00.000Z',
		});
		assertFails(result, 'missing Redis cmdstat_set must fail at capture time');
		assert.equal(existsSync(outputPath), false);
		const binDir = join(root, 'bin');
		mkdirSync(binDir);
		const tracePath = join(root, 'docker.trace');
		const dockerPath = join(binDir, 'docker');
		writeFileSync(dockerPath, `#!/usr/bin/env bash\nprintf '%s\\n' "$*" >> "$TRACE_PATH"\nif [[ "$*" == *inspect* ]]; then printf 'replacement-id\\n'; exit 0; fi\nexit 91\n`);
		chmodSync(dockerPath, 0o755);
		const statsOutput = join(root, 'docker-stats.csv');
		const replacement = spawnSync('bash', [scenarioPath('capture-docker-stats.sh'), statsOutput], {
			env: {
				...process.env,
				PATH: `${binDir}:${process.env.PATH}`,
				TRACE_PATH: tracePath,
				POSTGRES_CONTAINER: 'pg-198',
				REDIS_CONTAINER: 'redis-198',
				PERF_POSTGRES_CONTAINER_ID: 'pg-id-198',
				PERF_REDIS_CONTAINER_ID: 'redis-id-198',
			},
			encoding: 'utf8',
		});
		assertFails(replacement, 'transient same-name replacement must fail before Docker stats capture');
		assert.doesNotMatch(readFileSync(tracePath, 'utf8'), /stats/);
		assert.equal(existsSync(statsOutput), false);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('runner lifecycle cleans its sampler marker and both locks on TERM', async () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-signal-cleanup-'));
	const marker = join(root, 'sampling.marker');
	const globalLock = join(root, 'global.lock');
	const projectLock = join(root, 'project.lock');
	const ready = join(root, 'ready');
	try {
		const runner = readFileSync(scenarioPath('run-before.sh'), 'utf8');
		const prepare = readFileSync(scenarioPath('prepare-fixtures.sh'), 'utf8');
		assert.ok(runner.indexOf('install_notification_batch_runner_traps')
			< runner.indexOf('acquire_notification_batch_locks'));
		assert.ok(prepare.indexOf('install_notification_batch_fixture_traps')
			< prepare.indexOf('acquire_notification_batch_locks'));
		const command = `
source "${scenarioPath('guard-runtime.sh')}"
source "${scenarioPath('runner-lifecycle.sh')}"
PERF_GLOBAL_LOCK_DIR="${globalLock}"
PERF_PROJECT_LOCK_DIR="${projectLock}"
PERF_GLOBAL_LOCK_HELD=true
PERF_PROJECT_LOCK_HELD=true
SAMPLER_MARKER="${marker}"
mkdir "$PERF_GLOBAL_LOCK_DIR" "$PERF_PROJECT_LOCK_DIR"
touch "$SAMPLER_MARKER"
sleep 30 & SAMPLER_PID=$!
install_notification_batch_runner_traps
touch "${ready}"
while :; do sleep 1; done
`;
		const child = spawn('bash', ['-c', command], { stdio: 'ignore' });
		for (let attempt = 0; attempt < 50 && !existsSync(ready); attempt += 1) {
			await new Promise((resolve) => setTimeout(resolve, 20));
		}
		assert.equal(existsSync(ready), true, 'synthetic runner must reach its signal wait');
		child.kill('SIGTERM');
		const status = await new Promise((resolve) => child.once('close', resolve));
		assert.equal(status, 143);
		assert.equal(existsSync(marker), false);
		assert.equal(existsSync(globalLock), false);
		assert.equal(existsSync(projectLock), false);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('verifier requires exact PostgreSQL Redis and two-container lifecycle evidence', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-strict-evidence-'));
	try {
		const { runDir, artifacts } = writeRun(root, 'strict-evidence');
		const verify = () => runNode('verify-before.mjs', {
			MANIFEST_PATH: join(runDir, 'manifest.json'), RUN_DIR: runDir,
		});
		assert.equal(verify().status, 0, verify().stderr);
		const verification = JSON.parse(readFileSync(join(runDir, 'verification-report.json'), 'utf8'));
		assert.equal(verification.accepted, false);
		assert.equal(verification.automaticAdoption, false);
		const freshPostgres = structuredClone(artifacts);
		freshPostgres.postgresBefore.statsReset = null;
		freshPostgres.postgresAfter.statsReset = null;
		writeEvidenceArtifacts(runDir, freshPostgres);
		assert.equal(verify().status, 0, 'coherent fresh PostgreSQL null/null stats_reset must verify');

		const cases = [
			['missing PostgreSQL counter', (value) => { delete value.postgresAfter.database.xact_rollback; }],
			['null PostgreSQL counter', (value) => { value.postgresAfter.database.blks_read = null; }],
			['numeric PostgreSQL counter', (value) => { value.postgresAfter.tables.campus_members.seq_scan = 1; }],
			['extra PostgreSQL schema key', (value) => { value.postgresAfter.unapproved = 1; }],
			['wrong PostgreSQL current database', (value) => { value.postgresAfter.currentDatabase = 'other'; }],
			['changed PostgreSQL stats_reset', (value) => {
				value.postgresAfter.statsReset = '2026-07-14T00:00:01.000Z';
			}],
			['null/timestamp PostgreSQL stats_reset mismatch', (value) => {
				value.postgresBefore.statsReset = null;
			}],
			['reversed PostgreSQL capturedAt', (value) => {
				value.postgresAfter.capturedAt = '2026-07-14T00:00:59.000Z';
			}],
			['missing required PostgreSQL table', (value) => { delete value.postgresAfter.tables.campus_members; }],
			['extra PostgreSQL table', (value) => {
				value.postgresAfter.tables.unapproved_table = structuredClone(value.postgresAfter.tables.campus_members);
			}],
			['decreasing PostgreSQL database counter', (value) => { value.postgresAfter.database.xact_commit = '9'; }],
			['decreasing PostgreSQL table counter', (value) => {
				value.postgresAfter.tables.user_fcm_tokens.n_tup_upd = '1';
			}],
			['pg_stat_statements availability drift', (value) => {
				value.pgssAfter = {
					available: true,
					databaseId: '1',
					statsReset: '2026-07-14T00:00:00.000Z',
					rows: [],
				};
			}],
			['missing Redis identity', (value) => { delete value.redisBefore.runId; }],
			['null Redis evidence', (value) => { value.redisAfter.dbSize = null; }],
			['numeric Redis counter', (value) => { value.redisAfter.commands.set = 2006; }],
			['extra Redis schema key', (value) => { value.redisAfter.unapproved = true; }],
			['changed Redis run_id', (value) => { value.redisAfter.runId = 'replacement-run'; }],
			['reversed Redis capturedAt', (value) => {
				value.redisAfter.capturedAt = '2026-07-14T00:00:59.000Z';
			}],
			['decreasing Redis uptime', (value) => { value.redisAfter.uptimeSeconds = 99; }],
			['decreasing Redis DBSIZE', (value) => { value.redisAfter.dbSize = '9'; }],
			['decreasing Redis command counter', (value) => { value.redisAfter.commands.set = '4'; }],
			['mixed Docker container', (value) => {
				value.dockerStats = value.dockerStats.replace(
					`redis,redis-198,${value.environment.redisContainerId}`,
					'redis,other-redis,other-id',
				);
			}],
			['one Docker sample instant', (value) => {
				value.dockerStats = value.dockerStats.split('\n').slice(0, 3).join('\n') + '\n';
			}],
			['Docker sampling starts after workload', (value) => {
				value.evidenceWindow.workloadStartedAt = '2026-07-13T23:59:59.000Z';
			}],
			['Docker sampling finishes before workload', (value) => {
				value.evidenceWindow.workloadFinishedAt = '2026-07-14T00:02:01.000Z';
			}],
			['Docker sample gap exceeds approved maximum', (value) => {
				value.environment.dockerStatsMaxGapMilliseconds = 10000;
				value.evidenceWindow.dockerStatsMaxGapMilliseconds = 10000;
			}],
			['out-of-order Docker sample pairs', (value) => {
				const lines = value.dockerStats.trim().split('\n');
				value.dockerStats = [lines[0], lines[3], lines[4], lines[1], lines[2]].join('\n') + '\n';
			}],
		];
		for (const [name, mutate] of cases) {
			const value = structuredClone(artifacts);
			mutate(value);
			writeEvidenceArtifacts(runDir, value);
			assertFails(verify(), `${name} must fail`);
		}
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});
