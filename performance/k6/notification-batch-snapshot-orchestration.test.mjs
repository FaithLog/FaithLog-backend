import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

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

test('fake full run executes capture once, restores before every sample, and stops on first drift', async () => {
	const { orchestrateNotificationBatchBefore } = await importScenario('orchestrate-before.mjs');
	const temporaryRoot = mkdtempSync(join(tmpdir(), 'faithlog-198-orchestration-'));
	try {
		const actions = [];
		const childEnvironments = [];
		const result = await orchestrateNotificationBatchBefore({
			batchId: 'issue198-fake-full-a',
			reportRoot: temporaryRoot,
			expectedWarmupSamples: '1',
			expectedMeasuredSamples: '10',
			cumulativeStateStrategy: 'snapshot-restore',
			expectedComposeProject: 'faithlog-perf-198-before',
			actualComposeProject: 'faithlog-perf-198-before',
			baseChildEnvironment: {
				PATH: process.env.PATH,
				HOME: temporaryRoot,
				POSTGRES_PASSWORD: 'runtime-secret',
				REDIS_PASSWORD: 'runtime-secret',
				API_KEY: 'must-not-leak',
				AUTHORIZATION: 'must-not-leak',
				COOKIE: 'must-not-leak',
			},
		}, {
			captureSnapshot: async ({ outputPath, childEnvironment }) => {
				actions.push('capture');
				childEnvironments.push(childEnvironment);
				writeFileSync(outputPath, `${JSON.stringify({
					schemaVersion: 1,
					snapshotId: 'snapshot-a',
					composeProject: 'faithlog-perf-198-before',
					postgres: { database: 'faithlog', dumpSha256: 'a'.repeat(64), dumpBytes: '1', cardinality: { userFcmTokens: '1000', notificationLogs: '0' }, stateSha256: 'b'.repeat(64) },
					redis: { database: 0, snapshotDatabase: 15, keyCount: '10', stateSha1: 'c'.repeat(40) },
					credentialRecorded: false,
					automaticAdoption: false,
				})}\n`, { flag: 'wx' });
			},
			restoreSnapshot: async ({ outputPath, sample, restoreOrdinal, snapshot, snapshotReceiptSha256, childEnvironment }) => {
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
			prepareFixture: async ({ sample, childEnvironment }) => {
				actions.push(`fixture:${sample.sampleKind}:${sample.sampleIndex}`);
				childEnvironments.push(childEnvironment);
			},
			runSample: async ({ sample, runDir, childEnvironment }) => {
				actions.push(`run:${sample.sampleKind}:${sample.sampleIndex}`);
				childEnvironments.push(childEnvironment);
				writeFileSync(join(runDir, 'k6-summary.json'), `${JSON.stringify({ metrics: {
					notification_batch_requests: { values: { count: 10, rate: 5 } },
					notification_batch_failures: { values: { value: 0, passes: 0, fails: 10 } },
					notification_batch_duration: { values: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 25, max: 30 } },
				} })}\n`, { flag: 'wx' });
			},
		});
		assert.equal(result.captureCount, 1);
		assert.equal(result.restoreCount, 11);
		assert.equal(result.warmupCount, 1);
		assert.equal(result.measuredCount, 10);
		assert.equal(result.automaticAdoption, false);
		assert.equal(actions.filter((action) => action === 'capture').length, 1);
		assert.equal(actions.filter((action) => action.startsWith('restore:')).length, 11);
		assert.equal(actions.filter((action) => action.startsWith('fixture:')).length, 11);
		assert.equal(actions.filter((action) => action.startsWith('run:')).length, 11);
		for (const childEnvironment of childEnvironments) {
			assert.equal(childEnvironment.API_KEY, undefined);
			assert.equal(childEnvironment.AUTHORIZATION, undefined);
			assert.equal(childEnvironment.COOKIE, undefined);
		}

		let runCount = 0;
		await assert.rejects(() => orchestrateNotificationBatchBefore({
			batchId: 'issue198-fake-drift-a',
			reportRoot: temporaryRoot,
			expectedWarmupSamples: '1',
			expectedMeasuredSamples: '10',
			cumulativeStateStrategy: 'snapshot-restore',
			expectedComposeProject: 'faithlog-perf-198-before',
			actualComposeProject: 'faithlog-perf-198-before',
			baseChildEnvironment: { PATH: process.env.PATH, POSTGRES_PASSWORD: 'secret' },
		}, {
			captureSnapshot: async ({ outputPath }) => writeFileSync(outputPath, readFileSync(join(temporaryRoot, 'issue198-fake-full-a', 'snapshot-receipt.json')), { flag: 'wx' }),
			restoreSnapshot: async ({ outputPath, sample, restoreOrdinal, snapshot, snapshotReceiptSha256 }) => writeFileSync(outputPath, `${JSON.stringify({
				schemaVersion: 1, snapshotId: snapshot.snapshotId, snapshotReceiptSha256,
				composeProject: snapshot.composeProject, restoreOrdinal, sampleKind: sample.sampleKind,
				sampleIndex: sample.sampleIndex, credentialRecorded: false, automaticAdoption: false,
				postgres: { ...snapshot.postgres, stateSha256: restoreOrdinal === 2 ? 'f'.repeat(64) : snapshot.postgres.stateSha256 },
				redis: snapshot.redis,
			})}\n`, { flag: 'wx' }),
			prepareFixture: async () => {},
			runSample: async () => { runCount += 1; },
		}), /snapshot|state|drift/i);
		assert.equal(runCount, 1, 'the first restore drift must prevent its sample and every later sample');
		const rejection = JSON.parse(readFileSync(join(
			temporaryRoot, 'issue198-fake-drift-a', 'first-rejection.json',
		), 'utf8'));
		assert.equal(rejection.automaticAdoption, false);
		assert.equal(rejection.stage, 'snapshot-restore');
		assert.doesNotMatch(JSON.stringify(rejection), /secret|API_KEY|AUTHORIZATION|COOKIE/);
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
	assert.doesNotMatch(combined, /docker\s+(?:compose\s+)?(?:down|stop|restart)|docker\s+volume\s+rm|volume\s+prune|system\s+prune/);
	assert.doesNotMatch(combined, /rm\s+-rf|DROP\s+DATABASE/i);
	assert.match(combined, /automaticAdoption/);
});
