import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

const policy = JSON.parse(readFileSync(new URL('./snapshot-policy.json', import.meta.url), 'utf8'));
assert.deepEqual(Object.keys(policy).sort(), [
	'schemaVersion', 'expectedWarmupSamples', 'expectedMeasuredSamples',
	'cumulativeStateStrategy', 'fixturePreparationCount', 'snapshotCaptureCount',
	'snapshotRestoreCount', 'automaticAdoption',
].sort(), 'snapshot policy schema mismatch');
assert.equal(policy.schemaVersion, 1);
assert.equal(policy.fixturePreparationCount, 1);
assert.equal(policy.snapshotCaptureCount, 1);
assert.equal(policy.snapshotRestoreCount, 11);
assert.equal(policy.automaticAdoption, false);
export const APPROVED_SNAPSHOT_POLICY = Object.freeze({
	warmupSamples: policy.expectedWarmupSamples,
	measuredSamples: policy.expectedMeasuredSamples,
	strategy: policy.cumulativeStateStrategy,
});

const DECIMAL = /^(0|[1-9][0-9]*)$/;
const SHA1 = /^[a-f0-9]{40}$/;
const SHA256 = /^[a-f0-9]{64}$/;
const SAFE_ID = /^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/;

const assertDecimal = (value, name) => {
	assert.equal(typeof value, 'string', `${name} must be a canonical decimal string`);
	assert.match(value, DECIMAL, `${name} must be a canonical decimal string`);
	return BigInt(value);
};

const exactObject = (actual, keys, name) => {
	assert.ok(actual && typeof actual === 'object' && !Array.isArray(actual), `${name} must be an object`);
	assert.deepEqual(Object.keys(actual).sort(), [...keys].sort(), `${name} schema mismatch`);
};

export const sha256Json = (value) => createHash('sha256')
	.update(`${JSON.stringify(value)}\n`)
	.digest('hex');

export function assertApprovedSnapshotInputs({
	expectedWarmupSamples,
	expectedMeasuredSamples,
	cumulativeStateStrategy,
	expectedComposeProject,
	actualComposeProject,
}) {
	assert.equal(expectedWarmupSamples, String(APPROVED_SNAPSHOT_POLICY.warmupSamples),
		'EXPECTED_WARMUP_SAMPLES must be exactly the approved warmup count 1');
	assert.equal(expectedMeasuredSamples, String(APPROVED_SNAPSHOT_POLICY.measuredSamples),
		'EXPECTED_MEASURED_SAMPLES must be exactly the approved measured count 10');
	assert.equal(cumulativeStateStrategy, APPROVED_SNAPSHOT_POLICY.strategy,
		'CUMULATIVE_STATE_STRATEGY must be snapshot-restore');
	assert.equal(actualComposeProject, expectedComposeProject,
		'Actual Compose project must match the approved isolated target');
	assert.match(expectedComposeProject ?? '', /^faithlog-perf-198(?:$|-[A-Za-z0-9_-]+)$/,
		'Snapshot restore requires a dedicated #198 isolated Compose project');
	assert.doesNotMatch(expectedComposeProject, /(?:shared|latest|frontend|qa)/i,
		'Shared or general QA projects are not dedicated #198 isolated targets');
	return expectedComposeProject;
}

export function buildApprovedSamplePlan(batchId) {
	assert.match(batchId ?? '', SAFE_ID, 'Batch ID must be a safe identifier');
	return [
		{ sampleKind: 'warmup', sampleIndex: 1, sampleId: `${batchId}-warmup-01` },
		...Array.from({ length: APPROVED_SNAPSHOT_POLICY.measuredSamples }, (_, index) => ({
			sampleKind: 'measured',
			sampleIndex: index + 1,
			sampleId: `${batchId}-measured-${String(index + 1).padStart(2, '0')}`,
		})),
	];
}

export function validateSnapshotReceipt(snapshot) {
	exactObject(snapshot, [
		'schemaVersion', 'snapshotId', 'composeProject', 'postgres', 'redis',
		'credentialRecorded', 'automaticAdoption',
	], 'snapshot receipt');
	assert.equal(snapshot.schemaVersion, 1);
	assert.match(snapshot.snapshotId, SAFE_ID);
	assert.match(snapshot.composeProject, /^faithlog-perf-198(?:$|-[A-Za-z0-9_-]+)$/);
	assert.equal(snapshot.credentialRecorded, false);
	assert.equal(snapshot.automaticAdoption, false);
	exactObject(snapshot.postgres, [
		'database', 'dumpSha256', 'dumpBytes', 'cardinality', 'stateSha256',
	], 'snapshot postgres');
	assert.match(snapshot.postgres.database, /^[A-Za-z_][A-Za-z0-9_]*$/);
	assert.match(snapshot.postgres.dumpSha256, SHA256);
	assertDecimal(snapshot.postgres.dumpBytes, 'snapshot postgres dumpBytes');
	assert.match(snapshot.postgres.stateSha256, SHA256);
	assert.ok(snapshot.postgres.cardinality && typeof snapshot.postgres.cardinality === 'object');
	for (const [key, value] of Object.entries(snapshot.postgres.cardinality)) {
		assert.match(key, /^[A-Za-z][A-Za-z0-9]*$/);
		assertDecimal(value, `snapshot postgres cardinality ${key}`);
	}
	exactObject(snapshot.redis, [
		'database', 'snapshotDatabase', 'keyCount', 'stateSha1', 'ttlIntentSha1',
	], 'snapshot redis');
	assert.ok(Number.isSafeInteger(snapshot.redis.database) && snapshot.redis.database >= 0);
	assert.ok(Number.isSafeInteger(snapshot.redis.snapshotDatabase) && snapshot.redis.snapshotDatabase >= 0);
	assert.notEqual(snapshot.redis.database, snapshot.redis.snapshotDatabase);
	assertDecimal(snapshot.redis.keyCount, 'snapshot redis keyCount');
	assert.match(snapshot.redis.stateSha1, SHA1);
	assert.match(snapshot.redis.ttlIntentSha1, SHA1);
	return snapshot;
}

export function validateRestoreReceipt({ snapshot, snapshotReceiptSha256, sample, restore, restoreOrdinal }) {
	validateSnapshotReceipt(snapshot);
	assert.match(snapshotReceiptSha256, SHA256);
	exactObject(restore, [
		'schemaVersion', 'snapshotId', 'snapshotReceiptSha256', 'composeProject',
		'restoreOrdinal', 'sampleKind', 'sampleIndex', 'postgres', 'redis',
		'credentialRecorded', 'automaticAdoption',
	], 'restore receipt');
	assert.equal(restore.schemaVersion, 1);
	assert.equal(restore.snapshotId, snapshot.snapshotId);
	assert.equal(restore.snapshotReceiptSha256, snapshotReceiptSha256);
	assert.equal(restore.composeProject, snapshot.composeProject);
	assert.equal(restore.restoreOrdinal, restoreOrdinal);
	assert.equal(restore.sampleKind, sample.sampleKind);
	assert.equal(restore.sampleIndex, sample.sampleIndex);
	assert.equal(restore.credentialRecorded, false);
	assert.equal(restore.automaticAdoption, false);
	assert.deepEqual(restore.postgres, {
		database: snapshot.postgres.database,
		dumpSha256: snapshot.postgres.dumpSha256,
		cardinality: snapshot.postgres.cardinality,
		stateSha256: snapshot.postgres.stateSha256,
	}, 'PostgreSQL snapshot state or cardinality drifted after restore');
	assert.deepEqual(restore.redis, snapshot.redis,
		'Redis snapshot state or cardinality drifted after restore');
	return restore;
}

export function validateSnapshotSequence({ snapshot, snapshotReceiptSha256, plan, restores }) {
	validateSnapshotReceipt(snapshot);
	assert.deepEqual(plan, buildApprovedSamplePlan(plan[0]?.sampleId?.replace(/-warmup-01$/, '')),
		'Sample plan must be the exact approved 1+10 sequence');
	assert.equal(restores.length, plan.length, 'Restore count must exactly match the approved sample count');
	restores.forEach((restore, index) => validateRestoreReceipt({
		snapshot,
		snapshotReceiptSha256,
		sample: plan[index],
		restore,
		restoreOrdinal: index + 1,
	}));
	return {
		captureCount: 1,
		restoreCount: restores.length,
		warmupCount: plan.filter(({ sampleKind }) => sampleKind === 'warmup').length,
		measuredCount: plan.filter(({ sampleKind }) => sampleKind === 'measured').length,
		automaticAdoption: false,
	};
}

const metricValues = (metric, name) => {
	assert.ok(metric && typeof metric === 'object' && !Array.isArray(metric), `${name} is required`);
	if (Object.hasOwn(metric, 'values')) {
		exactObject(metric, ['values'], `${name} wrapper`);
		assert.ok(metric.values && typeof metric.values === 'object' && !Array.isArray(metric.values));
		return metric.values;
	}
	return metric;
};

const finite = (value, name) => {
	assert.equal(typeof value, 'number', `${name} must be numeric`);
	assert.ok(Number.isFinite(value), `${name} must be finite`);
	return value;
};

export function validateK6V2Metrics(metrics, expectedRequestCount) {
	assert.ok(Number.isSafeInteger(expectedRequestCount) && expectedRequestCount > 0);
	const counter = metricValues(metrics.notification_batch_requests, 'notification batch request Counter');
	assert.ok(Number.isSafeInteger(counter.count) && counter.count === expectedRequestCount,
		'Request Counter must exactly match expected request count');
	const throughput = finite(counter.rate, 'request Counter rate');
	assert.ok(throughput > 0, 'Request throughput must be positive');
	const failure = metricValues(metrics.notification_batch_failures, 'notification batch failure Rate');
	const failureRate = failure.value ?? failure.rate;
	finite(failureRate, 'failure Rate value');
	assert.equal(failureRate, 0, 'Failure Rate must be exactly zero');
	assert.ok(Number.isSafeInteger(failure.passes) && failure.passes === 0,
		'k6 v2 zero-failure Rate passes must be zero');
	assert.ok(Number.isSafeInteger(failure.fails) && failure.fails === expectedRequestCount,
		'k6 v2 zero-failure Rate fails must equal the request Counter');
	if (failure.value !== undefined && failure.rate !== undefined) {
		assert.equal(failure.value, failure.rate, 'Rate value and rate must agree');
	}
	const trend = metricValues(metrics.notification_batch_duration, 'notification batch duration Trend');
	const latency = {
		p50: finite(trend['p(50)'], 'Trend p50'),
		p95: finite(trend['p(95)'], 'Trend p95'),
		p99: finite(trend['p(99)'], 'Trend p99'),
		max: finite(trend.max, 'Trend max'),
	};
	assert.ok(Object.values(latency).every((value) => value >= 0));
	assert.ok(latency.p50 <= latency.p95 && latency.p95 <= latency.p99 && latency.p99 <= latency.max,
		'Trend percentiles must be ordered');
	return {
		requestCount: counter.count,
		throughputPerSecond: throughput,
		failureRate,
		failurePasses: failure.passes,
		failureFails: failure.fails,
		latency,
	};
}

export function validateSupportingEvidence({ postgres, redis, resources }) {
	const pgBeforeCommit = assertDecimal(postgres?.before?.xactCommit, 'PostgreSQL before commit');
	const pgAfterCommit = assertDecimal(postgres?.after?.xactCommit, 'PostgreSQL after commit');
	const pgBeforeRollback = assertDecimal(postgres?.before?.xactRollback, 'PostgreSQL before rollback');
	const pgAfterRollback = assertDecimal(postgres?.after?.xactRollback, 'PostgreSQL after rollback');
	assert.ok(pgAfterCommit >= pgBeforeCommit && pgAfterRollback >= pgBeforeRollback,
		'PostgreSQL cumulative counters must not regress');
	const redisBeforeSet = assertDecimal(redis?.before?.commands?.set, 'Redis before SET');
	const redisAfterSet = assertDecimal(redis?.after?.commands?.set, 'Redis after SET');
	const redisBeforeSize = assertDecimal(redis?.before?.dbSize, 'Redis before DBSIZE');
	const redisAfterSize = assertDecimal(redis?.after?.dbSize, 'Redis after DBSIZE');
	assert.ok(redisAfterSet >= redisBeforeSet, 'Redis cumulative command counters must not regress');
	assert.ok(Array.isArray(resources));
	assert.deepEqual(resources.map(({ component }) => component).sort(), ['postgres', 'redis']);
	for (const resource of resources) {
		finite(resource.cpuPercent, `${resource.component} CPU`);
		assert.ok(resource.cpuPercent >= 0);
		const used = assertDecimal(resource.memoryUsedBytes, `${resource.component} used memory`);
		const limit = assertDecimal(resource.memoryLimitBytes, `${resource.component} memory limit`);
		assert.ok(limit > 0n && used <= limit);
	}
	return {
		postgresCommitDelta: String(pgAfterCommit - pgBeforeCommit),
		postgresRollbackDelta: String(pgAfterRollback - pgBeforeRollback),
		redisSetDelta: String(redisAfterSet - redisBeforeSet),
		redisDbSizeDelta: String(redisAfterSize - redisBeforeSize),
		resourceComponents: resources.map(({ component }) => component).sort(),
		automaticAdoption: false,
	};
}
