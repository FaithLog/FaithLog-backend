import assert from 'node:assert/strict';
import {
	chmodSync,
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
import { spawnSync } from 'node:child_process';
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
	};
	const result = {
		...manifest,
		requestId: `00000000-0000-0000-0000-${fixtureRunId.padEnd(12, '0').slice(0, 12)}`,
		javaRuntimeVersion: 'synthetic-jvm',
		externalFcmUsed: false,
		notificationType: 'PAYMENT_UNPAID',
		retryBackoffPolicy: 'production-thread-sleep-1s-5s-30s',
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
			tokenUpdateCount: 101,
			fakeSendAttemptCount: 901,
			fakePermanentFailureCount: 101,
			fakeTransientRetryCount: 100,
		},
		endToEnd: { durationMs: 1100, throughputPerSecond: 909.09 },
		correctness: {
			duplicateReplayCreatedCount: 0,
			unexpectedRequestLogCount: 0,
			nonFixtureTokenMutationCount: 0,
			partialFailureContinued: true,
			mixedTokenLogSent: true,
			mixedPermanentTokenDeactivated: 1,
		},
	};
	const environment = {
		springProfile: 'local',
		fcmAdapter: 'fake',
		postgresContainer: 'pg-198',
		postgresContainerId: 'pg-id-198',
		redisContainer: 'redis-198',
		redisContainerId: 'redis-id-198',
		dockerProject: 'faithlog-perf-198',
		postgresHost: '127.0.0.1',
		postgresHostPort: 15432,
		postgresDatabase: 'faithlog',
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
		sharedStack: false,
		externalFcm: false,
	};
	const databaseBefore = {
		xact_commit: 10,
		xact_rollback: 0,
		blks_read: 1,
		blks_hit: 2,
		tup_returned: 3,
		tup_fetched: 4,
		tup_inserted: 5,
		tup_updated: 6,
		tup_deleted: 0,
	};
	const table = (values = {}) => ({
		seq_scan: 0,
		seq_tup_read: 0,
		idx_scan: 0,
		idx_tup_fetch: 0,
		n_tup_ins: 0,
		n_tup_upd: 0,
		n_tup_del: 0,
		...values,
	});
	const postgresBefore = {
		capturedAt: '2026-07-14T00:01:00.000Z',
		currentDatabase: 'faithlog',
		statsReset: '2026-07-14T00:00:00.000Z',
		database: databaseBefore,
		tables: {
			campus_members: table(),
			user_fcm_tokens: table({ n_tup_upd: 2 }),
			notification_logs: table({ n_tup_ins: 1, n_tup_upd: 2 }),
		},
		cardinality: {
			userFcmTokensTotal: 2000,
			activeTokensTotal: 1000,
			issue198DummyTokensTotal: 901,
			issue198ActiveDummyTokens: 901,
			notificationLogsTotal: 10,
			issue198MarkerLogsTotal: 2,
		},
		relationBytes: { userFcmTokens: 65536, notificationLogs: 32768 },
	};
	const postgresAfter = {
		...postgresBefore,
		capturedAt: '2026-07-14T00:02:00.000Z',
		database: {
			...databaseBefore,
			xact_commit: 20,
			blks_hit: 200,
			tup_inserted: 1005,
			tup_updated: 907,
		},
		tables: {
			campus_members: table({ seq_scan: 1, seq_tup_read: 1000 }),
			user_fcm_tokens: table({ n_tup_upd: 103 }),
			notification_logs: table({ n_tup_ins: 1001, n_tup_upd: 802 }),
		},
		cardinality: {
			userFcmTokensTotal: 2000,
			activeTokensTotal: 899,
			issue198DummyTokensTotal: 901,
			issue198ActiveDummyTokens: 800,
			notificationLogsTotal: 1010,
			issue198MarkerLogsTotal: 1002,
		},
		relationBytes: { userFcmTokens: 69632, notificationLogs: 131072 },
	};
	const redisBefore = {
		capturedAt: '2026-07-14T00:01:00.000Z',
		runId: 'redis-run-id-198',
		uptimeSeconds: 100,
		tcpPort: 6379,
		dbSize: 10,
		commands: { set: 5 },
	};
	const redisAfter = {
		...redisBefore,
		capturedAt: '2026-07-14T00:02:00.000Z',
		uptimeSeconds: 160,
		dbSize: 1010,
		commands: { set: 2006 },
	};
	const evidenceWindow = {
		workloadStartedAt: '2026-07-14T00:01:10.000Z',
		workloadFinishedAt: '2026-07-14T00:01:50.000Z',
		dockerStatsSampleIntervalSeconds: 60,
	};
	const dockerStats = [
		'captured_at,container_name,container_id,cpu_percent,memory_usage,memory_percent',
		'2026-07-14T00:01:00.000Z,pg-198,pg-id-198,1.5%,10MiB / 1GiB,2.5%',
		'2026-07-14T00:01:00.000Z,redis-198,redis-id-198,0.5%,5MiB / 1GiB,1.0%',
		'2026-07-14T00:02:00.000Z,pg-198,pg-id-198,2.0%,11MiB / 1GiB,2.7%',
		'2026-07-14T00:02:00.000Z,redis-198,redis-id-198,0.7%,6MiB / 1GiB,1.2%',
	].join('\n') + '\n';
	return {
		manifest,
		result,
		environment,
		postgresBefore,
		postgresAfter,
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
		'redis-before.json': artifacts.redisBefore,
		'redis-after.json': artifacts.redisAfter,
		'evidence-window.json': artifacts.evidenceWindow,
		'run-status.json': { status: 'verified' },
	})) {
		writeFileSync(join(runDir, name), `${JSON.stringify(value)}\n`);
	}
	writeFileSync(join(runDir, 'docker-stats.csv'), artifacts.dockerStats);
	writeFileSync(join(runDir, 'verification-report.json'), `${JSON.stringify({
		status: 'verified',
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
		assert.equal(existsSync(outputPath), false);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('summarizer validates exact sample kinds and stays disabled until cumulative-state strategy is implemented', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-cumulative-gate-'));
	try {
		const runs = [
			writeRun(root, 'warmup-1', 'warmup').runDir,
			writeRun(root, 'measured-1').runDir,
			writeRun(root, 'measured-2').runDir,
		];
		const runDirsFile = join(root, 'runs.txt');
		const outputPath = join(root, 'summary', 'baseline-summary.json');
		writeFileSync(runDirsFile, `${runs.join('\n')}\n`);
		const result = runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		});
		assertFails(result, 'unimplemented cumulative-state strategy must keep aggregation disabled');
		assert.match(result.stderr, /cumulative-state|snapshot|cleanup|not implemented/i);
		assert.equal(existsSync(outputPath), false);

		const duplicate = writeRun(root, 'duplicate-directory').runDir;
		for (const file of ['manifest.json', 'scenario-result.json']) {
			const value = JSON.parse(readFileSync(join(duplicate, file), 'utf8'));
			value.fixtureRunId = 'measured-1';
			writeFileSync(join(duplicate, file), `${JSON.stringify(value)}\n`);
		}
		writeFileSync(runDirsFile, `${runs[0]}\n${runs[1]}\n${duplicate}\n`);
		const duplicateResult = runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'snapshot-restore',
		});
		assertFails(duplicateResult, 'duplicate fixtureRunId across different run directories must fail');
		assert.match(duplicateResult.stderr, /Duplicate fixtureRunIds/);

		const invalidKind = writeRun(root, 'invalid-kind').runDir;
		for (const file of ['manifest.json', 'scenario-result.json']) {
			const value = JSON.parse(readFileSync(join(invalidKind, file), 'utf8'));
			value.sampleKind = 'calibration';
			writeFileSync(join(invalidKind, file), `${JSON.stringify(value)}\n`);
		}
		writeFileSync(runDirsFile, `${runs[0]}\n${runs[1]}\n${invalidKind}\n`);
		const invalidKindResult = runNode('summarize-before.mjs', {
			RUN_DIRS_FILE: runDirsFile,
			OUTPUT_PATH: outputPath,
			EXPECTED_WARMUP_SAMPLES: '1',
			EXPECTED_MEASURED_SAMPLES: '2',
			CUMULATIVE_STATE_STRATEGY: 'fixture-only-cleanup',
		});
		assertFails(invalidKindResult, 'unapproved sampleKind must fail');
		assert.match(invalidKindResult.stderr, /sampleKind=warmup or measured/);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('fixture preparation and runner honor the canonical Compose-project lock before SQL or Gradle', () => {
	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-project-lock-'));
	const project = `faithlog-perf-198-lock-${process.pid}-${Date.now()}`;
	const projectLock = `/tmp/faithlog-performance-${project}.lock`;
	const fixtureRunId = `lock-${process.pid}-${Date.now()}`;
	const reportRoot = join(REPOSITORY_ROOT, 'build/reports/k6/notification-batch');
	const fixtureDir = join(reportRoot, 'fixtures', fixtureRunId);
	const runId = `lock-run-${process.pid}-${Date.now()}`;
	try {
		assert.equal(existsSync(projectLock), false, 'synthetic canonical lock must start absent');
		mkdirSync(projectLock);
		const binDir = join(root, 'bin');
		mkdirSync(binDir);
		const tracePath = join(root, 'docker.trace');
		const dockerPath = join(binDir, 'docker');
		writeFileSync(dockerPath, `#!/usr/bin/env bash\nset -eu\nprintf '%s\\n' "$*" >> "$TRACE_PATH"\ncase "$*" in\n  *com.docker.compose.project*) printf '%s\\n' "$FAKE_PROJECT" ;;\n  *5432/tcp*) printf '15432\\n' ;;\n  *6379/tcp*) printf '16379\\n' ;;\n  *'{{.Image}}'*) printf 'sha256:synthetic\\n' ;;\n  *) exit 91 ;;\nesac\n`);
		chmodSync(dockerPath, 0o755);
		const gitPath = join(binDir, 'git');
		writeFileSync(gitPath, `#!/usr/bin/env bash\ncase "$*" in\n  *status*) exit 0 ;;\n  *rev-parse*) printf 'synthetic-commit\\n' ;;\n  *) exit 92 ;;\nesac\n`);
		chmodSync(gitPath, 0o755);
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
			FAKE_PROJECT: project,
			ALLOW_NOTIFICATION_BATCH_BASELINE: 'true',
			PERF_SPRING_PROFILE: 'local',
			PERF_FCM_ADAPTER: 'fake',
			PERF_EXPECTED_COMPOSE_PROJECT: project,
			POSTGRES_CONTAINER: 'pg-198',
			REDIS_CONTAINER: 'redis-198',
			PERF_DATASET_ID: 'PERFORMANCE_198_SYNTHETIC',
			PERF_FIXTURE_RUN_ID: fixtureRunId,
			PERF_SAMPLE_KIND: 'measured',
			PERF_CAMPUS_ID: '198',
			PERF_SUCCESS_COUNT: '600',
			PERF_TRANSIENT_COUNT: '100',
			PERF_PERMANENT_COUNT: '100',
			PERF_INACTIVE_COUNT: '100',
			PERF_NO_TOKEN_COUNT: '100',
			PERF_BUSINESS_DATE: '2026-07-14',
			PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS: '60',
		};
		const prepare = spawnSync('bash', [scenarioPath('prepare-fixtures.sh')], {
			env: commonEnv, encoding: 'utf8',
		});
		assertFails(prepare, 'fixture preparation must fail on the canonical project lock');
		assert.doesNotMatch(readFileSync(tracePath, 'utf8'), /exec.*psql/);

		writeFileSync(tracePath, '');
		const runner = spawnSync('bash', [scenarioPath('run-before.sh')], {
			env: { ...commonEnv, MANIFEST_PATH: manifestPath, RUN_ID: runId }, encoding: 'utf8',
		});
		assertFails(runner, 'runner must fail on the canonical project lock');
		assert.doesNotMatch(readFileSync(tracePath, 'utf8'), /exec.*(psql|redis-cli)/);
		assert.equal(existsSync(join(reportRoot, 'runs', runId)), false);
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
		for (const phase of ['initial', 'before', 'after', 'final']) {
			assert.match(runner, new RegExp(`runtime-identity-${phase}\\.json`));
		}
		assert.ok(
			runner.indexOf('assert-runtime-continuity.mjs') < runner.indexOf('verify-before.mjs'),
			'runtime continuity must execute before result verification',
		);
		const identity = (capturedAt, uptimeSeconds) => ({
			capturedAt,
			postgres: {
				container: {
					name: 'pg-198', id: 'pg-id-198', imageId: 'sha256:pg', startedAt: '2026-07-14T00:00:00Z',
					composeProject: 'faithlog-perf-198', composeService: 'postgres', composeConfigHash: 'pg-config',
				},
				server: {
					database: 'faithlog', address: '127.0.0.1', port: 5432,
					postmasterStartTime: '2026-07-14T00:00:00Z',
				},
			},
			redis: {
				container: {
					name: 'redis-198', id: 'redis-id-198', imageId: 'sha256:redis', startedAt: '2026-07-14T00:00:00Z',
					composeProject: 'faithlog-perf-198', composeService: 'redis', composeConfigHash: 'redis-config',
				},
				server: { runId: 'redis-run-198', uptimeSeconds, port: 6379 },
			},
		});
		const phases = [
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

		const replaced = identity('2026-07-14T00:02:01Z', 1);
		replaced.postgres.container.id = 'replacement-id-same-name';
		writeFileSync(join(root, 'runtime-identity-final.json'), `${JSON.stringify(replaced)}\n`);
		assertFails(runNode('assert-runtime-continuity.mjs', { RUN_DIR: root }),
			'same-name container replacement must fail continuity');
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

		const writePostgres = (value) => writeFileSync(
			join(runDir, 'postgres-after.json'), `${JSON.stringify(value)}\n`,
		);
		const missing = structuredClone(artifacts.postgresAfter);
		delete missing.database.xact_rollback;
		writePostgres(missing);
		assertFails(verify(), 'missing PostgreSQL counter must fail');

		const nullValue = structuredClone(artifacts.postgresAfter);
		nullValue.database.blks_read = null;
		writePostgres(nullValue);
		assertFails(verify(), 'null PostgreSQL counter must fail');

		const stringValue = structuredClone(artifacts.postgresAfter);
		stringValue.tables.campus_members.seq_scan = '1';
		writePostgres(stringValue);
		assertFails(verify(), 'string PostgreSQL counter must fail');

		writePostgres(artifacts.postgresAfter);
		const writeRedis = (name, value) => writeFileSync(
			join(runDir, name), `${JSON.stringify(value)}\n`,
		);
		const missingRedis = structuredClone(artifacts.redisBefore);
		delete missingRedis.runId;
		writeRedis('redis-before.json', missingRedis);
		assertFails(verify(), 'missing Redis identity must fail');

		writeRedis('redis-before.json', artifacts.redisBefore);
		const nullRedis = structuredClone(artifacts.redisAfter);
		nullRedis.dbSize = null;
		writeRedis('redis-after.json', nullRedis);
		assertFails(verify(), 'null Redis evidence must fail');

		const stringRedis = structuredClone(artifacts.redisAfter);
		stringRedis.commands.set = '2006';
		writeRedis('redis-after.json', stringRedis);
		assertFails(verify(), 'string Redis counter must fail');

		writeRedis('redis-after.json', artifacts.redisAfter);
		writeFileSync(
			join(runDir, 'docker-stats.csv'),
			artifacts.dockerStats.replace('redis-198,redis-id-198', 'other-redis,other-id'),
		);
		assertFails(verify(), 'mixed-container Docker evidence must fail');

		writeFileSync(
			join(runDir, 'docker-stats.csv'),
			artifacts.dockerStats.split('\n').slice(0, 3).join('\n') + '\n',
		);
		assertFails(verify(), 'one Docker sample instant must fail');
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});
