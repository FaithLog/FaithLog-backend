import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);

const importScenario = (name) => import(new URL(name, SCENARIO_ROOT));

test('approved snapshot policy is exact and shared projects fail before any action', async () => {
	const contract = await importScenario('snapshot-contract.mjs');
	assert.deepEqual(contract.APPROVED_SNAPSHOT_POLICY, {
		warmupSamples: 1,
		measuredSamples: 10,
		strategy: 'snapshot-restore',
	});
	assert.equal(contract.assertApprovedSnapshotInputs({
		expectedWarmupSamples: '1',
		expectedMeasuredSamples: '10',
		cumulativeStateStrategy: 'snapshot-restore',
		expectedComposeProject: 'faithlog-perf-198-before',
		actualComposeProject: 'faithlog-perf-198-before',
	}), 'faithlog-perf-198-before');

	for (const project of [
		'faithlog-frontend-latest',
		'faithlog-latest',
		'faithlog-qa-198',
		'faithlog-perf-197-before',
		'faithlog-perf-198-shared',
	]) {
		assert.throws(() => contract.assertApprovedSnapshotInputs({
			expectedWarmupSamples: '1',
			expectedMeasuredSamples: '10',
			cumulativeStateStrategy: 'snapshot-restore',
			expectedComposeProject: project,
			actualComposeProject: project,
		}), /dedicated.*#198|isolated/i);
	}
	assert.throws(() => contract.assertApprovedSnapshotInputs({
		expectedWarmupSamples: '2',
		expectedMeasuredSamples: '10',
		cumulativeStateStrategy: 'snapshot-restore',
		expectedComposeProject: 'faithlog-perf-198-before',
		actualComposeProject: 'faithlog-perf-198-before',
	}), /warmup/i);
	assert.throws(() => contract.assertApprovedSnapshotInputs({
		expectedWarmupSamples: '1',
		expectedMeasuredSamples: '9',
		cumulativeStateStrategy: 'snapshot-restore',
		expectedComposeProject: 'faithlog-perf-198-before',
		actualComposeProject: 'faithlog-perf-198-before',
	}), /measured/i);
});

test('snapshot and restore receipts prove one capture and eleven equivalent starts', async () => {
	const contract = await importScenario('snapshot-contract.mjs');
	const snapshot = {
		schemaVersion: 1,
		snapshotId: 'i198-snapshot-a',
		composeProject: 'faithlog-perf-198-before',
		postgres: {
			database: 'faithlog',
			dumpSha256: 'a'.repeat(64),
			dumpBytes: '123456',
			cardinality: { userFcmTokens: '1000', notificationLogs: '5' },
			stateSha256: 'b'.repeat(64),
		},
		redis: {
			database: 0,
			snapshotDatabase: 15,
			keyCount: '12',
			stateSha1: 'c'.repeat(40),
			ttlIntentSha1: 'e'.repeat(40),
		},
		credentialRecorded: false,
		automaticAdoption: false,
	};
	const snapshotReceiptSha256 = 'd'.repeat(64);
	const plan = contract.buildApprovedSamplePlan('issue198-before-a');
	assert.equal(plan.length, 11);
	assert.deepEqual(plan.map(({ sampleKind }) => sampleKind), [
		'warmup', ...Array(10).fill('measured'),
	]);
	const restores = plan.map((sample, index) => ({
		schemaVersion: 1,
		snapshotId: snapshot.snapshotId,
		snapshotReceiptSha256,
		composeProject: snapshot.composeProject,
		restoreOrdinal: index + 1,
		sampleKind: sample.sampleKind,
		sampleIndex: sample.sampleIndex,
		postgres: {
			database: snapshot.postgres.database,
			dumpSha256: snapshot.postgres.dumpSha256,
			cardinality: snapshot.postgres.cardinality,
			stateSha256: snapshot.postgres.stateSha256,
		},
		redis: {
			database: snapshot.redis.database,
			snapshotDatabase: snapshot.redis.snapshotDatabase,
			keyCount: snapshot.redis.keyCount,
			stateSha1: snapshot.redis.stateSha1,
			ttlIntentSha1: snapshot.redis.ttlIntentSha1,
		},
		credentialRecorded: false,
		automaticAdoption: false,
	}));
	const validated = contract.validateSnapshotSequence({
		snapshot,
		snapshotReceiptSha256,
		plan,
		restores,
	});
	assert.equal(validated.captureCount, 1);
	assert.equal(validated.restoreCount, 11);
	assert.equal(validated.warmupCount, 1);
	assert.equal(validated.measuredCount, 10);
	assert.equal(validated.automaticAdoption, false);

	const drifted = structuredClone(restores);
	drifted[6].postgres.cardinality.notificationLogs = '6';
	assert.throws(() => contract.validateSnapshotSequence({
		snapshot,
		snapshotReceiptSha256,
		plan,
		restores: drifted,
	}), /snapshot|cardinality|state/i);
	assert.throws(() => contract.validateSnapshotSequence({
		snapshot,
		snapshotReceiptSha256,
		plan,
		restores: restores.slice(0, 5),
	}), /restore|count|sequence/i);
	const tamperedTtl = structuredClone(restores);
	tamperedTtl[3].redis.ttlIntentSha1 = 'f'.repeat(40);
	assert.throws(() => contract.validateSnapshotSequence({
		snapshot,
		snapshotReceiptSha256,
		plan,
		restores: tamperedTtl,
	}), /Redis|TTL|snapshot|state/i);
	const missingTtl = structuredClone(restores);
	delete missingTtl[3].redis.ttlIntentSha1;
	assert.throws(() => contract.validateSnapshotSequence({
		snapshot,
		snapshotReceiptSha256,
		plan,
		restores: missingTtl,
	}), /Redis|TTL|snapshot|state/i);
});

test('k6 v2 metrics, PostgreSQL Redis evidence, and Docker resources stay strict', async () => {
	const contract = await importScenario('snapshot-contract.mjs');
	const direct = {
		notification_batch_requests: { count: 10, rate: 5 },
		notification_batch_failures: { value: 0, passes: 0, fails: 10 },
		notification_batch_duration: {
			'p(50)': 10, 'p(95)': 20, 'p(99)': 25, max: 30,
		},
	};
	const normalized = contract.validateK6V2Metrics(direct, 10);
	assert.deepEqual(normalized, {
		requestCount: 10,
		throughputPerSecond: 5,
		failureRate: 0,
		failurePasses: 0,
		failureFails: 10,
		latency: { p50: 10, p95: 20, p99: 25, max: 30 },
	});
	assert.deepEqual(contract.validateK6V2Metrics(Object.fromEntries(
		Object.entries(direct).map(([key, value]) => [key, { values: value }]),
	), 10), normalized);
	for (const invalid of [
		{ ...direct, notification_batch_failures: { value: 0.1, passes: 1, fails: 9 } },
		{ ...direct, notification_batch_failures: { value: 0, passes: 0, fails: 9 } },
		{ ...direct, notification_batch_requests: { count: 10.5, rate: 5 } },
		{ ...direct, notification_batch_duration: { 'p(50)': 20, 'p(95)': 10, 'p(99)': 25, max: 30 } },
	]) {
		assert.throws(() => contract.validateK6V2Metrics(invalid, 10));
	}

	assert.deepEqual(contract.validateSupportingEvidence({
		postgres: {
			before: { xactCommit: '100', xactRollback: '2' },
			after: { xactCommit: '120', xactRollback: '2' },
		},
		redis: {
			before: { commands: { set: '10' }, dbSize: '12' },
			after: { commands: { set: '30' }, dbSize: '12' },
		},
		resources: [
			{ component: 'postgres', cpuPercent: 1, memoryUsedBytes: '10', memoryLimitBytes: '100' },
			{ component: 'redis', cpuPercent: 2, memoryUsedBytes: '20', memoryLimitBytes: '100' },
		],
	}), {
		postgresCommitDelta: '20',
		postgresRollbackDelta: '0',
		redisSetDelta: '20',
		redisDbSizeDelta: '0',
		resourceComponents: ['postgres', 'redis'],
		automaticAdoption: false,
	});
	assert.throws(() => contract.validateSupportingEvidence({
		postgres: { before: { xactCommit: '100', xactRollback: '2' }, after: { xactCommit: '99', xactRollback: '2' } },
		redis: { before: { commands: { set: '10' }, dbSize: '12' }, after: { commands: { set: '30' }, dbSize: '12' } },
		resources: [],
	}));
});

test('fake full run prepares once, captures once, restores before every sample, and stops on first drift', async () => {
	const { orchestrateNotificationBatchBefore } = await importScenario('orchestrate-before.mjs');
	const temporaryRoot = mkdtempSync(join(tmpdir(), 'faithlog-198-orchestration-'));
	try {
		const actions = [];
		const childEnvironments = [];
		const forbiddenDefaultRoot = join(temporaryRoot, 'forbidden-default');
		let lockHeld = false;
		const lockCoordinator = ({ batchRoot, composeProject }) => {
			const receiptPath = join(batchRoot, 'orchestration-lock.json');
			writeFileSync(receiptPath, JSON.stringify({ composeProject }));
			lockHeld = true;
			return {
				childEnvironment: { PERF_ORCHESTRATION_LOCK_RECEIPT: receiptPath },
				release: () => { lockHeld = false; },
			};
		};
		const result = await orchestrateNotificationBatchBefore({
			batchId: 'issue198-fake-full-a',
			reportRoot: temporaryRoot,
			expectedWarmupSamples: '1',
			expectedMeasuredSamples: '10',
			cumulativeStateStrategy: 'snapshot-restore',
			expectedComposeProject: 'faithlog-perf-198-before',
			actualComposeProject: 'faithlog-perf-198-before',
			lockCoordinator,
			baseChildEnvironment: {
				PATH: process.env.PATH,
				HOME: temporaryRoot,
				PERF_REPORT_ROOT: forbiddenDefaultRoot,
				PERF_DATASET_ID: 'PERFORMANCE_198_FAKE',
				POSTGRES_PASSWORD: 'runtime-secret',
				REDIS_PASSWORD: 'runtime-secret',
				API_KEY: 'must-not-leak',
				AUTHORIZATION: 'must-not-leak',
				COOKIE: 'must-not-leak',
			},
		}, {
			provisionSyntheticDataset: async ({ receiptPath, childEnvironment }) => {
				actions.push('seed');
				childEnvironments.push(childEnvironment);
				writeFileSync(receiptPath, `${JSON.stringify({
					schemaVersion: 1, composeProject: 'faithlog-perf-198-before',
					postgresDatabase: 'faithlog', datasetId: 'PERFORMANCE_198_FAKE', campusId: 198,
					activeUserCount: 1000, activeMemberCount: 1000, migrationCount: 11,
					migrationContractSha256: 'a'.repeat(64), datasetStateSha256: 'b'.repeat(64),
					credentialRecorded: false, externalDataCopied: false, externalFcm: false,
					automaticAdoption: false,
				})}\n`, { flag: 'wx' });
			},
			prepareCanonicalFixture: async ({ manifestPath, childEnvironment }) => {
				assert.equal(lockHeld, true);
				assert.equal(childEnvironment.PERF_CAMPUS_ID, '198');
				actions.push('fixture:canonical');
				childEnvironments.push(childEnvironment);
				mkdirSync(join(manifestPath, '..'), { recursive: true });
				writeFileSync(manifestPath, `${JSON.stringify({
					datasetId: 'PERFORMANCE_198_FAKE', fixtureRunId: 'issue198-fake-full-a-fixture',
					sampleKind: 'canonical', composeProject: 'faithlog-perf-198-before',
					postgresDatabase: 'faithlog', campusId: 198, memberCount: 1000,
					successCount: 600, transientCount: 100, permanentCount: 100,
					inactiveCount: 100, noTokenCount: 100, mixedTokenUserCount: 1,
					insertedDummyTokenCount: 901,
					fixturePolicy: 'dummy-token-and-generated-log-only', credentialRecorded: false,
				})}\n`, { flag: 'wx' });
			},
			captureSnapshot: async ({ outputPath, childEnvironment }) => {
				assert.equal(lockHeld, true);
				actions.push('capture');
				childEnvironments.push(childEnvironment);
				writeFileSync(outputPath, `${JSON.stringify({
					schemaVersion: 1,
					snapshotId: childEnvironment.PERF_SNAPSHOT_ID,
					composeProject: 'faithlog-perf-198-before',
					postgres: { database: 'faithlog', dumpSha256: 'a'.repeat(64), dumpBytes: '1', cardinality: { userFcmTokens: '1000', notificationLogs: '0' }, stateSha256: 'b'.repeat(64) },
					redis: { database: 0, snapshotDatabase: 15, keyCount: '10', stateSha1: 'c'.repeat(40), ttlIntentSha1: 'e'.repeat(40) },
					credentialRecorded: false,
					automaticAdoption: false,
				})}\n`, { flag: 'wx' });
			},
			restoreSnapshot: async ({ outputPath, sample, restoreOrdinal, snapshot, snapshotReceiptSha256, childEnvironment }) => {
				assert.equal(lockHeld, true);
				actions.push(`restore:${sample.sampleKind}:${sample.sampleIndex}`);
				childEnvironments.push(childEnvironment);
				writeFileSync(outputPath, `${JSON.stringify({
					schemaVersion: 1,
					snapshotId: snapshot.snapshotId,
					snapshotReceiptSha256,
					composeProject: snapshot.composeProject,
					restoreOrdinal,
					sampleKind: sample.sampleKind,
					sampleIndex: sample.sampleIndex,
					postgres: { database: snapshot.postgres.database, dumpSha256: snapshot.postgres.dumpSha256, cardinality: snapshot.postgres.cardinality, stateSha256: snapshot.postgres.stateSha256 },
					redis: snapshot.redis,
					credentialRecorded: false,
					automaticAdoption: false,
				})}\n`, { flag: 'wx' });
			},
			runSample: async ({ sample, runDir, childEnvironment }) => {
				assert.equal(lockHeld, true);
				actions.push(`run:${sample.sampleKind}:${sample.sampleIndex}`);
				childEnvironments.push(childEnvironment);
				mkdirSync(runDir, { recursive: true });
				writeFileSync(join(runDir, 'k6-summary.json'), `${JSON.stringify({ metrics: {
					notification_batch_requests: { values: { count: 10, rate: 5 } },
					notification_batch_failures: { values: { value: 0, passes: 0, fails: 10 } },
					notification_batch_duration: { values: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 25, max: 30 } },
				} })}\n`, { flag: 'wx' });
			},
			summarize: async ({ childEnvironment }) => {
				actions.push('summarize');
				childEnvironments.push(childEnvironment);
			},
		});
		assert.equal(result.captureCount, 1);
		assert.equal(result.fixturePrepareCount, 1);
		assert.equal(result.restoreCount, 11);
		assert.equal(result.warmupCount, 1);
		assert.equal(result.measuredCount, 10);
		assert.equal(result.automaticAdoption, false);
		assert.equal(lockHeld, false);
		assert.deepEqual(actions.slice(0, 3), ['seed', 'fixture:canonical', 'capture']);
		assert.equal(actions.filter((action) => action === 'seed').length, 1);
		assert.equal(actions.filter((action) => action === 'capture').length, 1);
		assert.equal(actions.filter((action) => action.startsWith('restore:')).length, 11);
		assert.equal(actions.filter((action) => action.startsWith('fixture:')).length, 1);
		assert.equal(actions.filter((action) => action.startsWith('run:')).length, 11);
		assert.equal(actions.filter((action) => action === 'summarize').length, 1);
		for (const childEnvironment of childEnvironments) {
			assert.ok(childEnvironment.PERF_ORCHESTRATION_LOCK_RECEIPT);
			assert.equal(childEnvironment.PERF_REPORT_ROOT, temporaryRoot);
			for (const key of [
				'PERF_SEED_RECEIPT_PATH', 'PERF_SNAPSHOT_ROOT', 'PERF_SNAPSHOT_RECEIPT_PATH',
				'PERF_RESTORE_RECEIPT_PATH', 'MANIFEST_PATH', 'RUN_DIRS_FILE', 'OUTPUT_PATH',
				'SNAPSHOT_RECEIPT_PATH', 'RESTORE_RECEIPTS_FILE',
			]) {
				if (childEnvironment[key] !== undefined) {
					assert.ok(childEnvironment[key].startsWith(`${temporaryRoot}/`), `${key} escaped report root`);
				}
			}
			assert.equal(childEnvironment.API_KEY, undefined);
			assert.equal(childEnvironment.AUTHORIZATION, undefined);
			assert.equal(childEnvironment.COOKIE, undefined);
		}
		assert.equal(existsSync(forbiddenDefaultRoot), false, 'orchestration must not write to a fallback report root');

		let runCount = 0;
		let driftLockHeld = false;
		await assert.rejects(() => orchestrateNotificationBatchBefore({
			batchId: 'issue198-fake-drift-a',
			reportRoot: temporaryRoot,
			expectedWarmupSamples: '1',
			expectedMeasuredSamples: '10',
			cumulativeStateStrategy: 'snapshot-restore',
			expectedComposeProject: 'faithlog-perf-198-before',
			actualComposeProject: 'faithlog-perf-198-before',
			lockCoordinator: ({ batchRoot }) => {
				const receiptPath = join(batchRoot, 'orchestration-lock.json');
				writeFileSync(receiptPath, '{}');
				driftLockHeld = true;
				return {
					childEnvironment: { PERF_ORCHESTRATION_LOCK_RECEIPT: receiptPath },
					release: () => { driftLockHeld = false; },
				};
			},
			baseChildEnvironment: {
				PATH: process.env.PATH, POSTGRES_PASSWORD: 'secret', PERF_DATASET_ID: 'PERFORMANCE_198_FAKE',
			},
		}, {
			provisionSyntheticDataset: async ({ receiptPath }) => writeFileSync(receiptPath, `${JSON.stringify({
				schemaVersion: 1, composeProject: 'faithlog-perf-198-before',
				postgresDatabase: 'faithlog', datasetId: 'PERFORMANCE_198_FAKE', campusId: 198,
				activeUserCount: 1000, activeMemberCount: 1000, migrationCount: 11,
				migrationContractSha256: 'a'.repeat(64), datasetStateSha256: 'b'.repeat(64),
				credentialRecorded: false, externalDataCopied: false, externalFcm: false,
				automaticAdoption: false,
			})}\n`, { flag: 'wx' }),
			prepareCanonicalFixture: async ({ manifestPath }) => {
				mkdirSync(join(manifestPath, '..'), { recursive: true });
				writeFileSync(manifestPath, `${JSON.stringify({
				datasetId: 'PERFORMANCE_198_FAKE', fixtureRunId: 'issue198-fake-drift-a-fixture',
				sampleKind: 'canonical', composeProject: 'faithlog-perf-198-before',
				postgresDatabase: 'faithlog', campusId: 198, memberCount: 1000,
				successCount: 600, transientCount: 100, permanentCount: 100,
				inactiveCount: 100, noTokenCount: 100, mixedTokenUserCount: 1,
				insertedDummyTokenCount: 901,
				fixturePolicy: 'dummy-token-and-generated-log-only', credentialRecorded: false,
			})}\n`, { flag: 'wx' });
			},
			captureSnapshot: async ({ outputPath, childEnvironment }) => {
				const preserved = JSON.parse(readFileSync(join(temporaryRoot, 'orchestrations', 'issue198-fake-full-a', 'snapshot-receipt.json'), 'utf8'));
				writeFileSync(outputPath, `${JSON.stringify({
					...preserved,
					snapshotId: childEnvironment.PERF_SNAPSHOT_ID,
				})}\n`, { flag: 'wx' });
			},
			restoreSnapshot: async ({ outputPath, sample, restoreOrdinal, snapshot, snapshotReceiptSha256 }) => writeFileSync(outputPath, `${JSON.stringify({
				schemaVersion: 1, snapshotId: snapshot.snapshotId, snapshotReceiptSha256,
				composeProject: snapshot.composeProject, restoreOrdinal, sampleKind: sample.sampleKind,
				sampleIndex: sample.sampleIndex, credentialRecorded: false, automaticAdoption: false,
				postgres: {
					database: snapshot.postgres.database,
					dumpSha256: snapshot.postgres.dumpSha256,
					cardinality: snapshot.postgres.cardinality,
					stateSha256: restoreOrdinal === 2 ? 'f'.repeat(64) : snapshot.postgres.stateSha256,
				},
				redis: snapshot.redis,
			})}\n`, { flag: 'wx' }),
			runSample: async () => { runCount += 1; },
		}), /snapshot|state|drift/i);
		assert.equal(driftLockHeld, false);
		assert.equal(runCount, 1, 'the first restore drift must prevent its sample and every later sample');
		const rejection = JSON.parse(readFileSync(join(
			temporaryRoot, 'orchestrations', 'issue198-fake-drift-a', 'first-rejection.json',
		), 'utf8'));
		assert.equal(rejection.automaticAdoption, false);
		assert.equal(rejection.stage, 'snapshot-restore');
		assert.doesNotMatch(JSON.stringify(rejection), /secret|API_KEY|AUTHORIZATION|COOKIE/);
	} finally {
		rmSync(temporaryRoot, { recursive: true, force: true });
	}
});

test('fake restore receipt rejects tampered or missing Redis TTL intent metadata', () => {
	const temporaryRoot = mkdtempSync(join(tmpdir(), 'faithlog-198-ttl-intent-'));
	try {
		const postgresFingerprint = { cardinality: { userFcmTokens: '1000', notificationLogs: '0' } };
		const postgresStateSha256 = createHash('sha256')
			.update(JSON.stringify(postgresFingerprint)).digest('hex');
		const snapshot = {
			schemaVersion: 1,
			snapshotId: 'i198-ttl-snapshot-a',
			composeProject: 'faithlog-perf-198-before',
			postgres: {
				database: 'faithlog', dumpSha256: 'a'.repeat(64), dumpBytes: '1',
				cardinality: postgresFingerprint.cardinality, stateSha256: postgresStateSha256,
			},
			redis: {
				database: 0, snapshotDatabase: 15, keyCount: '2',
				stateSha1: 'b'.repeat(40), ttlIntentSha1: 'c'.repeat(40),
			},
			credentialRecorded: false,
			automaticAdoption: false,
		};
		const snapshotPath = join(temporaryRoot, 'snapshot.json');
		const postgresPath = join(temporaryRoot, 'postgres.json');
		const redisPath = join(temporaryRoot, 'redis.json');
		writeFileSync(snapshotPath, JSON.stringify(snapshot));
		writeFileSync(postgresPath, JSON.stringify(postgresFingerprint));
		writeFileSync(redisPath, JSON.stringify({ keyCount: '2', stateSha1: 'b'.repeat(40) }));
		const receiptScript = fileURLToPath(new URL('state-snapshot-receipt.mjs', SCENARIO_ROOT));
		const run = (metadata, outputName) => {
			const metadataPath = join(temporaryRoot, `${outputName}-metadata.json`);
			writeFileSync(metadataPath, JSON.stringify(metadata));
			return spawnSync(process.execPath, [receiptScript, 'restore'], {
				env: {
					PATH: process.env.PATH,
					PERF_POSTGRES_FINGERPRINT_PATH: postgresPath,
					PERF_REDIS_FINGERPRINT_PATH: redisPath,
					PERF_REDIS_RESTORE_METADATA_PATH: metadataPath,
					PERF_SNAPSHOT_RECEIPT_PATH: snapshotPath,
					PERF_RESTORE_RECEIPT_PATH: join(temporaryRoot, `${outputName}.json`),
					PERF_RESTORE_ORDINAL: '1', PERF_SAMPLE_KIND: 'warmup', PERF_SAMPLE_INDEX: '1',
					POSTGRES_DB: 'faithlog', PERF_REDIS_DATABASE: '0', PERF_REDIS_SNAPSHOT_DATABASE: '15',
				},
				encoding: 'utf8',
			});
		};
		const valid = { keyCount: '2', stateSha1: 'b'.repeat(40), ttlIntentSha1: 'c'.repeat(40) };
		assert.equal(run(valid, 'valid').status, 0);
		assert.notEqual(run({ ...valid, ttlIntentSha1: 'd'.repeat(40) }, 'tampered').status, 0);
		const missing = { ...valid };
		delete missing.ttlIntentSha1;
		assert.notEqual(run(missing, 'missing').status, 0);

		const captureLua = readFileSync(new URL('redis-capture-snapshot.lua', SCENARIO_ROOT), 'utf8');
		const restoreLua = readFileSync(new URL('redis-restore-snapshot.lua', SCENARIO_ROOT), 'utf8');
		assert.match(captureLua, /PTTL/);
		assert.match(captureLua, /ttlIntentSha1/);
		assert.match(restoreLua, /snapshot TTL intent hash drifted/);
		assert.match(restoreLua, /ttlIntentSha1=expectedTtlIntent/);
		assert.doesNotMatch(`${captureLua}\n${restoreLua}`, /redis\.call\(['"]SELECT/);
	} finally {
		rmSync(temporaryRoot, { recursive: true, force: true });
	}
});

test('snapshot scripts forbid lifecycle and volume deletion while using fixed snapshot files', () => {
	const capture = readFileSync(new URL('capture-state-snapshot.sh', SCENARIO_ROOT), 'utf8');
	const restore = readFileSync(new URL('restore-state-snapshot.sh', SCENARIO_ROOT), 'utf8');
	const orchestrator = readFileSync(new URL('orchestrate-before.mjs', SCENARIO_ROOT), 'utf8');
	const combined = `${capture}\n${restore}\n${orchestrator}`;
	assert.match(capture, /pg_dump/);
	assert.match(restore, /pg_restore/);
	assert.match(capture, /snapshot-receipt\.json/);
	assert.match(restore, /restore-receipt/);
	assert.match(orchestrator, /orchestration-lock\.json/);
	assert.match(orchestrator, /finally[\s\S]*lockHandle\.release/);
	assert.doesNotMatch(combined, /docker\s+(?:compose\s+)?(?:down|stop|restart)|docker\s+volume\s+rm|volume\s+prune|system\s+prune/);
	assert.doesNotMatch(combined, /rm\s+-rf|DROP\s+DATABASE/i);
	assert.match(combined, /automaticAdoption/);
	assert.ok(orchestrator.indexOf('prepareCanonicalFixture') < orchestrator.indexOf('captureSnapshot'));
});

test('isolated schema and synthetic dataset bootstrap is mandatory before canonical fixture', async () => {
	const orchestrator = readFileSync(new URL(
		'./notification-batch/orchestrate-before.mjs', import.meta.url), 'utf8');
	const provisioner = readFileSync(new URL(
		'./notification-batch/provision-isolated-dataset.sh', import.meta.url), 'utf8');
	const seedSql = readFileSync(new URL(
		'./notification-batch/provision-isolated-dataset.sql', import.meta.url), 'utf8');
	assert.ok(orchestrator.indexOf('provisionSyntheticDataset')
		< orchestrator.indexOf('prepareCanonicalFixture'));
	assert.match(orchestrator, /seed-receipt\.json/);
	assert.match(orchestrator, /PERF_CAMPUS_ID/);
	assert.match(provisioner, /verify-current-develop-contract\.mjs/);
	assert.match(provisioner, /src\/main\/resources\/db\/migration/);
	assert.match(provisioner, /V1__initial_schema\.sql/);
	assert.match(provisioner, /V11__secure_supabase_data_api\.sql/);
	assert.match(provisioner, /faithlog-perf-198/);
	assert.doesNotMatch(provisioner, /pg_dump|COPY\s+.*FROM\s+PROGRAM|faithlog-latest/i);
	assert.match(seedSql, /generate_series\(1,\s*1000\)/i);
	assert.match(seedSql, /PERFORMANCE_/);
	assert.match(seedSql, /campus_members/);
	assert.match(seedSql, /status[\s\S]*ACTIVE/i);
	assert.doesNotMatch(seedSql, /INSERT\s+INTO\s+(?:user_fcm_tokens|notification_logs)|firebase/i);

	const { validateSeedReceipt } = await importScenario('seed-contract.mjs');
	const receipt = {
		schemaVersion: 1, composeProject: 'faithlog-perf-198-before',
		postgresDatabase: 'faithlog_perf_198', datasetId: 'PERFORMANCE_198_SYNTHETIC', campusId: 1,
		activeUserCount: 1000, activeMemberCount: 1000, migrationCount: 11,
		migrationContractSha256: 'a'.repeat(64), datasetStateSha256: 'b'.repeat(64),
		credentialRecorded: false, externalDataCopied: false, externalFcm: false,
		automaticAdoption: false,
	};
	assert.equal(validateSeedReceipt(receipt), receipt);
	assert.throws(() => validateSeedReceipt({ ...receipt, activeMemberCount: 999 }), /1000/);
	assert.throws(() => validateSeedReceipt({ ...receipt, externalDataCopied: true }), /external|copied/i);
	assert.throws(() => validateSeedReceipt({ ...receipt, externalFcm: true }), /FCM/i);
});
