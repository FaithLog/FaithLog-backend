import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import {
	canonicalDecimal,
	decimalDelta,
	validatePgStatStatements,
	validateResourceSamples,
} from './integrity-contract.mjs';

const manifestPath = process.env.MANIFEST_PATH;
const runDir = process.env.RUN_DIR;
assert.ok(manifestPath, 'MANIFEST_PATH is required');
assert.ok(runDir, 'RUN_DIR is required');

const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
const result = JSON.parse(readFileSync(join(runDir, 'scenario-result.json'), 'utf8'));
const environment = JSON.parse(readFileSync(join(runDir, 'environment.json'), 'utf8'));
const postgresBefore = JSON.parse(readFileSync(join(runDir, 'postgres-before.json'), 'utf8'));
const postgresAfter = JSON.parse(readFileSync(join(runDir, 'postgres-after.json'), 'utf8'));
const pgssBefore = JSON.parse(readFileSync(join(runDir, 'pgss-before.json'), 'utf8'));
const pgssAfter = JSON.parse(readFileSync(join(runDir, 'pgss-after.json'), 'utf8'));
const redisBefore = JSON.parse(readFileSync(join(runDir, 'redis-before.json'), 'utf8'));
const redisAfter = JSON.parse(readFileSync(join(runDir, 'redis-after.json'), 'utf8'));
const evidenceWindow = JSON.parse(readFileSync(join(runDir, 'evidence-window.json'), 'utf8'));
const dockerStats = readFileSync(join(runDir, 'docker-stats.csv'), 'utf8');

const expectedPending = manifest.successCount + manifest.transientCount + manifest.permanentCount;
const expectedSkipped = manifest.inactiveCount + manifest.noTokenCount;
const expectedSent = manifest.successCount + manifest.transientCount;
const expectedFailed = manifest.permanentCount;
const expectedSendAttempts = manifest.successCount + (manifest.transientCount * 2) + manifest.permanentCount + 1;
const expectedPermanentTokenFailures = manifest.permanentCount + 1;
assert.deepEqual(Object.keys(manifest).sort(), [
	'datasetId', 'fixtureRunId', 'sampleKind', 'composeProject', 'postgresDatabase', 'campusId',
	'memberCount', 'successCount', 'transientCount', 'permanentCount', 'inactiveCount', 'noTokenCount',
	'mixedTokenUserCount', 'insertedDummyTokenCount', 'fixturePolicy', 'credentialRecorded',
].sort(), 'Fixture manifest schema mismatch');
assert.equal(manifest.fixturePolicy, 'dummy-token-and-generated-log-only');
assert.equal(manifest.credentialRecorded, false);

assert.equal(manifest.memberCount, 1000);
assert.equal(manifest.mixedTokenUserCount, 1);
assert.equal(manifest.insertedDummyTokenCount, manifest.memberCount - manifest.noTokenCount + 1);
assert.equal(
	manifest.successCount + manifest.transientCount + manifest.permanentCount
		+ manifest.inactiveCount + manifest.noTokenCount,
	manifest.memberCount,
);
assert.equal(result.datasetId, manifest.datasetId);
assert.equal(result.fixtureRunId, manifest.fixtureRunId);
assert.equal(result.sampleKind, manifest.sampleKind);
assert.equal(result.campusId, manifest.campusId);
assert.equal(result.creation.createdLogs, manifest.memberCount);
assert.equal(result.creation.logInsertCount, manifest.memberCount);
assert.equal(result.creation.pendingLogs, expectedPending);
assert.equal(result.creation.skippedLogs, expectedSkipped);
assert.equal(result.delivery.statusCounts.SENT, expectedSent);
assert.equal(result.delivery.statusCounts.FAILED, expectedFailed);
assert.equal(result.delivery.statusCounts.SKIPPED, expectedSkipped);
assert.equal(result.delivery.statusCounts.PENDING, 0);
assert.equal(result.delivery.tokenLookupCount, expectedPending > 0 ? 1 : 0);
assert.equal(result.delivery.logUpdateCount, expectedPending);
assert.equal(result.creation.tokenLookupCount, manifest.memberCount);
assert.equal(result.delivery.tokenUpdateCount, expectedPermanentTokenFailures);
assert.equal(result.delivery.fakeSendAttemptCount, expectedSendAttempts);
assert.equal(result.delivery.fakePermanentFailureCount, expectedPermanentTokenFailures);
assert.equal(result.delivery.fakeTransientRetryCount, manifest.transientCount);
assert.equal(result.correctness.duplicateReplayCreatedCount, 0);
assert.equal(result.correctness.unexpectedRequestLogCount, 0);
assert.equal(result.correctness.nonFixtureTokenMutationCount, 0);
assert.equal(result.correctness.partialFailureContinued, true);
assert.equal(result.correctness.mixedTokenLogSent, true);
assert.equal(result.correctness.mixedPermanentTokenDeactivated, 1);
assert.ok(Number.isFinite(result.correctness.duplicateReplayDurationMs)
	&& result.correctness.duplicateReplayDurationMs >= 0);
assert.ok(Number.isSafeInteger(result.correctness.duplicateReplayDbPreparedStatements)
	&& result.correctness.duplicateReplayDbPreparedStatements >= 0);
assert.equal(result.externalFcmUsed, false);
assert.equal(result.productionContractBaseCommit, '6796ed146244d8f3f5b5dd7048ebe16865084a97');
assert.equal(result.deliveryTokenSnapshotPolicy, 'request-wide-bulk');
assert.equal(result.dedupeKeyShape, 'notificationType + campusId + scopeId + targetUserId + businessDate');
assert.equal(result.targetIsolationBoundary, 'scheduler-supplied same-campus ACTIVE member IDs');
assert.equal(result.scenarioFailureCount, 0);
assert.equal(result.scenarioFailureRate, 0);
assert.deepEqual(result.phaseOrder, ['creation', 'dedupe-replay', 'delivery']);
assert.equal(environment.externalFcm, false);
assert.equal(environment.sharedStack, false);
assert.equal(environment.springProfile, 'local');
assert.equal(environment.fcmAdapter, 'fake');
assert.equal(environment.postgresHost, '127.0.0.1');
assert.equal(environment.redisHost, '127.0.0.1');
assert.equal(manifest.composeProject, environment.dockerProject);
assert.equal(manifest.postgresDatabase, environment.postgresDatabase);
assert.equal(environment.executionModel, 'cold-jvm-per-sample');
assert.equal(environment.warmupScope, 'external-postgres-redis-cache-only');
assert.equal(environment.externalEvidenceWindow, 'gradle-spring-harness-lifecycle');
assert.match(environment.expectedPostgresRole, /^[A-Za-z_][A-Za-z0-9_]*$/);
assert.match(environment.businessDate, /^\d{4}-\d{2}-\d{2}$/);
assert.match(environment.dockerProject, /^(?!.*faithlog-latest)[A-Za-z0-9_-]+$/);
assert.ok(Number.isInteger(environment.postgresHostPort) && environment.postgresHostPort > 0);
assert.ok(Number.isInteger(environment.redisHostPort) && environment.redisHostPort > 0);
assert.ok(result.endToEnd.durationMs > 0);
assert.ok(result.endToEnd.throughputPerSecond > 0);
assert.ok(Math.abs(result.endToEnd.durationMs - (result.creation.durationMs + result.delivery.durationMs)) < 0.000001,
	'endToEnd duration must equal ordered creation plus delivery duration');
// Multi-sample latency ordering p50 <= p95 <= p99 <= max belongs to the disabled summarizer, not one run.

for (const phase of [result.creation, result.delivery]) {
	assert.ok(phase.durationMs > 0, 'durationMs must be positive');
	assert.ok(phase.throughputPerSecond > 0, 'throughputPerSecond must be positive');
	assert.ok(phase.dbPreparedStatements >= 0, 'dbPreparedStatements must be non-negative');
	assert.ok(phase.perUserDbCalls >= 0, 'perUserDbCalls must be non-negative');
}

const exactKeys = (value, expectedKeys, path) => {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${path} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), [...expectedKeys].sort(), `${path} schema mismatch`);
};
exactKeys(result, [
	'datasetId', 'fixtureRunId', 'sampleKind', 'campusId', 'requestId', 'externalFcmUsed',
	'springProfile', 'fcmAdapter', 'notificationType', 'productionContractBaseCommit', 'retryBackoffPolicy',
	'deliveryTokenSnapshotPolicy', 'phaseOrder', 'scenarioFailureCount', 'scenarioFailureRate',
	'javaRuntimeVersion', 'dedupeKeyShape', 'targetIsolationBoundary', 'creation', 'delivery',
	'endToEnd', 'correctness', 'capturedAt',
], 'result');
exactKeys(result.creation, [
	'durationMs', 'throughputPerSecond', 'dbPreparedStatements', 'perUserDbCalls',
	'processCpuDurationMs', 'heapUsedDeltaBytes', 'createdLogs', 'logInsertCount',
	'pendingLogs', 'skippedLogs', 'tokenLookupCount',
], 'result.creation');
exactKeys(result.delivery, [
	'durationMs', 'throughputPerSecond', 'dbPreparedStatements', 'perUserDbCalls',
	'processCpuDurationMs', 'heapUsedDeltaBytes', 'statusCounts', 'logUpdateCount',
	'tokenLookupCount', 'tokenUpdateCount', 'fakeSendAttemptCount',
	'fakePermanentFailureCount', 'fakeTransientRetryCount',
], 'result.delivery');
exactKeys(result.delivery.statusCounts, ['SENT', 'FAILED', 'SKIPPED', 'PENDING'],
	'result.delivery.statusCounts');
exactKeys(result.endToEnd, ['durationMs', 'throughputPerSecond'], 'result.endToEnd');
exactKeys(result.correctness, [
	'duplicateReplayCreatedCount', 'duplicateReplayDurationMs', 'duplicateReplayDbPreparedStatements',
	'unexpectedRequestLogCount', 'nonFixtureTokenMutationCount', 'partialFailureContinued',
	'mixedTokenLogSent', 'mixedPermanentTokenDeactivated',
], 'result.correctness');
assert.equal(result.springProfile, 'local');
assert.equal(result.fcmAdapter, 'deterministic-test-fake');
assert.equal(result.notificationType, 'PAYMENT_UNPAID');
assert.equal(result.retryBackoffPolicy, 'production-thread-sleep-1s-5s-30s');
assert.ok(Number.isFinite(Date.parse(result.capturedAt)), 'result.capturedAt must be an ISO timestamp');
const assertFiniteNumber = (value, path, minimum = 0) => {
	assert.ok(typeof value === 'number' && Number.isFinite(value) && value >= minimum,
		`${path} must be a finite number >= ${minimum}`);
};
const assertApproximately = (actual, expected, path) => {
	const tolerance = Math.max(1e-9, Math.abs(expected) * 1e-9);
	assert.ok(Math.abs(actual - expected) <= tolerance, `${path} math mismatch`);
};
for (const [phaseName, phase] of [['creation', result.creation], ['delivery', result.delivery]]) {
	assertFiniteNumber(phase.durationMs, `${phaseName}.durationMs`, Number.MIN_VALUE);
	assertFiniteNumber(phase.throughputPerSecond, `${phaseName}.throughputPerSecond`, Number.MIN_VALUE);
	assert.ok(Number.isSafeInteger(phase.dbPreparedStatements) && phase.dbPreparedStatements >= 0,
		`${phaseName}.dbPreparedStatements must be a non-negative safe integer`);
	assertFiniteNumber(phase.perUserDbCalls, `${phaseName}.perUserDbCalls`);
	assertFiniteNumber(phase.processCpuDurationMs, `${phaseName}.processCpuDurationMs`);
	assert.ok(Number.isSafeInteger(phase.heapUsedDeltaBytes), `${phaseName}.heapUsedDeltaBytes must be a safe integer`);
	assertApproximately(phase.perUserDbCalls, phase.dbPreparedStatements / manifest.memberCount,
		`${phaseName}.perUserDbCalls`);
}
assertApproximately(result.creation.throughputPerSecond,
	manifest.memberCount / (result.creation.durationMs / 1000), 'creation.throughputPerSecond');
assertApproximately(result.delivery.throughputPerSecond,
	expectedPending / (result.delivery.durationMs / 1000), 'delivery.throughputPerSecond');
assertFiniteNumber(result.endToEnd.durationMs, 'endToEnd.durationMs', Number.MIN_VALUE);
assertFiniteNumber(result.endToEnd.throughputPerSecond, 'endToEnd.throughputPerSecond', Number.MIN_VALUE);
assertApproximately(result.endToEnd.throughputPerSecond,
	manifest.memberCount / (result.endToEnd.durationMs / 1000), 'endToEnd.throughputPerSecond');
const assertIsoTimestamp = (value, path) => {
	assert.equal(typeof value, 'string', `${path} must be an ISO timestamp string`);
	assert.ok(Number.isFinite(Date.parse(value)), `${path} must be a valid ISO timestamp`);
	return Date.parse(value);
};
const assertNullableStatsReset = (value, path) => {
	if (value === null) return null;
	return assertIsoTimestamp(value, path);
};
const assertExactDecimalRecord = (value, expectedKeys, path) => {
	exactKeys(value, expectedKeys, path);
	for (const key of expectedKeys) canonicalDecimal(value[key], `${path}.${key}`);
};
const exactDecimalDelta = (before, after, expectedKeys, path) => {
	assertExactDecimalRecord(before, expectedKeys, `${path}.before`);
	assertExactDecimalRecord(after, expectedKeys, `${path}.after`);
	return Object.fromEntries(expectedKeys.map((key) => [
		key, decimalDelta(before[key], after[key], `${path}.${key}`),
	]));
};

const POSTGRES_TOP_LEVEL_KEYS = [
	'capturedAt', 'currentDatabase', 'currentUser', 'statsReset', 'database', 'tables', 'cardinality', 'relationBytes',
];
const POSTGRES_DATABASE_KEYS = [
	'xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched',
	'tup_inserted', 'tup_updated', 'tup_deleted',
];
const POSTGRES_TABLES = ['campus_members', 'user_fcm_tokens', 'notification_logs'];
const POSTGRES_TABLE_KEYS = [
	'seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del',
];
const POSTGRES_CARDINALITY_KEYS = [
	'userFcmTokensTotal', 'activeTokensTotal', 'issue198DummyTokensTotal',
	'issue198ActiveDummyTokens', 'notificationLogsTotal', 'issue198MarkerLogsTotal',
];
const POSTGRES_RELATION_BYTES_KEYS = ['userFcmTokens', 'notificationLogs'];
for (const [phase, snapshot] of [['before', postgresBefore], ['after', postgresAfter]]) {
	exactKeys(snapshot, POSTGRES_TOP_LEVEL_KEYS, `postgres.${phase}`);
	assert.equal(snapshot.currentDatabase, environment.postgresDatabase,
		`postgres.${phase}.currentDatabase must match the manifest runtime`);
	assert.equal(snapshot.currentUser, environment.expectedPostgresRole,
		`postgres.${phase}.currentUser must remain the approved direct owner JDBC role`);
	assertIsoTimestamp(snapshot.capturedAt, `postgres.${phase}.capturedAt`);
	assertNullableStatsReset(snapshot.statsReset, `postgres.${phase}.statsReset`);
	assertExactDecimalRecord(snapshot.database, POSTGRES_DATABASE_KEYS, `postgres.${phase}.database`);
	exactKeys(snapshot.tables, POSTGRES_TABLES, `postgres.${phase}.tables`);
	for (const tableName of POSTGRES_TABLES) {
		assertExactDecimalRecord(snapshot.tables[tableName], POSTGRES_TABLE_KEYS,
			`postgres.${phase}.tables.${tableName}`);
	}
	assertExactDecimalRecord(snapshot.cardinality, POSTGRES_CARDINALITY_KEYS,
		`postgres.${phase}.cardinality`);
	assertExactDecimalRecord(snapshot.relationBytes, POSTGRES_RELATION_BYTES_KEYS,
		`postgres.${phase}.relationBytes`);
}
assert.ok(Date.parse(postgresBefore.capturedAt) < Date.parse(postgresAfter.capturedAt),
	'PostgreSQL capturedAt must be strictly ordered');
assert.equal(postgresAfter.statsReset, postgresBefore.statsReset, 'PostgreSQL stats_reset changed');
const postgresDelta = {
	database: exactDecimalDelta(
		postgresBefore.database, postgresAfter.database, POSTGRES_DATABASE_KEYS, 'postgres.database',
	),
	tables: Object.fromEntries(POSTGRES_TABLES.map((tableName) => [tableName, exactDecimalDelta(
		postgresBefore.tables[tableName], postgresAfter.tables[tableName],
		POSTGRES_TABLE_KEYS, `postgres.tables.${tableName}`,
	)])),
};

const REDIS_KEYS = ['capturedAt', 'runId', 'uptimeSeconds', 'tcpPort', 'dbSize', 'commands'];
for (const [phase, snapshot] of [['before', redisBefore], ['after', redisAfter]]) {
	exactKeys(snapshot, REDIS_KEYS, `redis.${phase}`);
	assertIsoTimestamp(snapshot.capturedAt, `redis.${phase}.capturedAt`);
	assert.equal(typeof snapshot.runId, 'string', `redis.${phase}.runId must be a string`);
	assert.match(snapshot.runId, /^[A-Za-z0-9_-]+$/, `redis.${phase}.runId is invalid`);
	for (const key of ['uptimeSeconds', 'tcpPort']) {
		assert.ok(Number.isSafeInteger(snapshot[key]) && snapshot[key] >= 1,
			`redis.${phase}.${key} must be an integer`);
	}
	canonicalDecimal(snapshot.dbSize, `redis.${phase}.dbSize`);
	assertExactDecimalRecord(snapshot.commands, ['set'], `redis.${phase}.commands`);
}
assert.ok(Date.parse(redisBefore.capturedAt) < Date.parse(redisAfter.capturedAt),
	'Redis capturedAt must be strictly ordered');
assert.equal(redisAfter.runId, redisBefore.runId, 'Redis run_id changed during evidence capture');
assert.equal(redisAfter.tcpPort, redisBefore.tcpPort, 'Redis server endpoint changed during evidence capture');
assert.ok(redisAfter.uptimeSeconds >= redisBefore.uptimeSeconds, 'Redis uptime must be monotonic');
assert.ok(canonicalDecimal(redisAfter.dbSize, 'redis.after.dbSize')
	>= canonicalDecimal(redisBefore.dbSize, 'redis.before.dbSize'), 'Redis DBSIZE must be monotonic');
const redisDbSizeBefore = redisBefore.dbSize;
const redisDbSizeDelta = decimalDelta(redisBefore.dbSize, redisAfter.dbSize, 'redis.dbSize');
const redisCommandCallDelta = exactDecimalDelta(
	redisBefore.commands, redisAfter.commands, ['set'], 'redis.commands',
);
const pgssEvidence = validatePgStatStatements(pgssBefore, pgssAfter);

exactKeys(evidenceWindow,
	[
		'workloadStartedAt', 'workloadFinishedAt', 'dockerStatsSampleIntervalSeconds',
		'dockerStatsMaxGapMilliseconds',
	], 'evidenceWindow');
const workloadStartedAt = assertIsoTimestamp(evidenceWindow.workloadStartedAt, 'workloadStartedAt');
const workloadFinishedAt = assertIsoTimestamp(evidenceWindow.workloadFinishedAt, 'workloadFinishedAt');
assert.ok(workloadStartedAt < workloadFinishedAt, 'Workload evidence window must be strictly ordered');
assert.ok(Number.isSafeInteger(evidenceWindow.dockerStatsSampleIntervalSeconds)
	&& evidenceWindow.dockerStatsSampleIntervalSeconds >= 1
	&& evidenceWindow.dockerStatsSampleIntervalSeconds <= 60,
'Docker stats cadence must be an approved integer from 1 through 60 seconds');
assert.equal(evidenceWindow.dockerStatsSampleIntervalSeconds, environment.dockerStatsSampleIntervalSeconds,
	'Docker stats cadence must match the runtime environment');
assert.ok(Number.isSafeInteger(evidenceWindow.dockerStatsMaxGapMilliseconds)
	&& evidenceWindow.dockerStatsMaxGapMilliseconds >= evidenceWindow.dockerStatsSampleIntervalSeconds * 1000
	&& evidenceWindow.dockerStatsMaxGapMilliseconds <= 300000,
'Docker stats maximum gap must be an approved integer from cadence through 300000 milliseconds');
assert.equal(evidenceWindow.dockerStatsMaxGapMilliseconds, environment.dockerStatsMaxGapMilliseconds,
	'Docker stats maximum gap must match the runtime environment');
assert.ok(Date.parse(postgresBefore.capturedAt) <= workloadStartedAt
	&& Date.parse(postgresAfter.capturedAt) >= workloadFinishedAt,
'PostgreSQL snapshots must cover the full workload lifecycle window');
assert.ok(Date.parse(redisBefore.capturedAt) <= workloadStartedAt
	&& Date.parse(redisAfter.capturedAt) >= workloadFinishedAt,
'Redis snapshots must cover the full workload lifecycle window');

const dockerLines = dockerStats.trim().split(/\r?\n/);
assert.equal(dockerLines.shift(),
	'captured_at,component,container_name,container_id,cpu_percent,memory_used_bytes,memory_limit_bytes,memory_percent',
	'Docker stats schema mismatch');
const resourceRows = dockerLines.map((row) => {
	const columns = row.split(',');
	assert.equal(columns.length, 8, `Invalid Docker stats row: ${row}`);
	const [
		capturedAt, component, containerName, containerId, cpuPercent,
		memoryUsedBytes, memoryLimitBytes, memoryPercent,
	] = columns;
	return {
		capturedAt,
		component,
		containerName,
		containerId,
		cpuPercent: Number(cpuPercent),
		memoryUsedBytes,
		memoryLimitBytes,
		memoryPercent: Number(memoryPercent),
	};
});
const resourceValidation = validateResourceSamples(resourceRows, {
	postgres: { name: environment.postgresContainer, id: environment.postgresContainerId },
	redis: { name: environment.redisContainer, id: environment.redisContainerId },
}, {
	workloadStartedAt: evidenceWindow.workloadStartedAt,
	workloadFinishedAt: evidenceWindow.workloadFinishedAt,
	maxGapMilliseconds: evidenceWindow.dockerStatsMaxGapMilliseconds,
});

const cardinalityBefore = postgresBefore.cardinality;
const cardinalityAfter = postgresAfter.cardinality;
const relationBytesBefore = postgresBefore.relationBytes;
const activeTokensBefore = canonicalDecimal(cardinalityBefore.activeTokensTotal,
	'cardinality.before.activeTokensTotal');
const activeTokensAfter = canonicalDecimal(cardinalityAfter.activeTokensTotal,
	'cardinality.after.activeTokensTotal');
const activeDummyTokensBefore = canonicalDecimal(cardinalityBefore.issue198ActiveDummyTokens,
	'cardinality.before.issue198ActiveDummyTokens');
const activeDummyTokensAfter = canonicalDecimal(cardinalityAfter.issue198ActiveDummyTokens,
	'cardinality.after.issue198ActiveDummyTokens');
assert.ok(activeTokensBefore >= activeTokensAfter, 'active token cardinality must not increase');
assert.ok(activeDummyTokensBefore >= activeDummyTokensAfter, 'active dummy token cardinality must not increase');
assert.equal(
	decimalDelta(cardinalityBefore.notificationLogsTotal, cardinalityAfter.notificationLogsTotal,
		'cardinality.notificationLogsTotal'),
	String(result.creation.logInsertCount),
	'notification_logs total cardinality must grow by the exact request-scoped insert count',
);
assert.equal(
	decimalDelta(cardinalityBefore.issue198MarkerLogsTotal, cardinalityAfter.issue198MarkerLogsTotal,
		'cardinality.issue198MarkerLogsTotal'),
	String(result.creation.logInsertCount),
	'Issue #198 marker log cardinality must grow by the exact insert count',
);
assert.equal(
	decimalDelta(cardinalityBefore.userFcmTokensTotal, cardinalityAfter.userFcmTokensTotal,
		'cardinality.userFcmTokensTotal'),
	'0',
	'the measured scenario must not insert or delete FCM token rows',
);
assert.equal(
	String(activeTokensBefore - activeTokensAfter),
	String(result.delivery.tokenUpdateCount),
	'active token cardinality must decrease by the exact permanent failure count',
);
assert.equal(
	String(activeDummyTokensBefore - activeDummyTokensAfter),
	String(result.delivery.tokenUpdateCount),
	'active Issue #198 dummy token cardinality must decrease exactly',
);
assert.equal(redisDbSizeDelta, String(manifest.memberCount), 'one retained dedupe key per target is required');
assert.equal(
	postgresDelta.tables.notification_logs.n_tup_ins,
	String(result.creation.logInsertCount),
	'notification_logs physical insert evidence must exactly match the logical insert count',
);
assert.equal(
	postgresDelta.tables.notification_logs.n_tup_upd,
	String(result.delivery.logUpdateCount),
	'notification_logs physical update evidence must exactly match the logical update count',
);
assert.equal(
	postgresDelta.tables.user_fcm_tokens.n_tup_upd,
	String(result.delivery.tokenUpdateCount),
	'user_fcm_tokens physical update evidence must exactly match permanent dummy-token deactivation',
);
assert.equal(
	redisCommandCallDelta.set,
	String((manifest.memberCount * 2) + 1),
	'Redis SET evidence must equal creation + replay dedupe reservations + one delivery lock',
);

const verification = {
	status: 'verified',
	accepted: false,
	automaticAdoption: false,
	measurementStatus: 'scenario-verified-not-baseline',
	datasetId: result.datasetId,
	fixtureRunId: result.fixtureRunId,
	requestId: result.requestId,
	createdLogs: result.creation.createdLogs,
	logInsertCount: result.creation.logInsertCount,
	logUpdateCount: result.delivery.logUpdateCount,
	statusCounts: result.delivery.statusCounts,
	tokenUpdateCount: result.delivery.tokenUpdateCount,
	duplicateReplayCreatedCount: result.correctness.duplicateReplayCreatedCount,
	unexpectedRequestLogCount: result.correctness.unexpectedRequestLogCount,
	evidence: {
		window: environment.externalEvidenceWindow,
		postgresCurrentDatabase: postgresBefore.currentDatabase,
		postgresCurrentUser: postgresBefore.currentUser,
		postgresStatsReset: postgresBefore.statsReset,
		postgresCapturedAt: { before: postgresBefore.capturedAt, after: postgresAfter.capturedAt },
		postgresBeforeCardinality: cardinalityBefore,
		postgresBeforeRelationBytes: relationBytesBefore,
		postgresDelta,
		pgStatStatements: pgssEvidence,
		redisRunId: redisBefore.runId,
		redisUptimeBefore: redisBefore.uptimeSeconds,
		redisCapturedAt: { before: redisBefore.capturedAt, after: redisAfter.capturedAt },
		redisDbSizeBefore,
		redisDbSizeDelta,
		redisCommandCallDelta,
		dockerSampleCount: resourceRows.length,
		dockerSampleInstantCount: resourceValidation.sampleInstants,
		dockerStatsSampleIntervalSeconds: evidenceWindow.dockerStatsSampleIntervalSeconds,
		dockerStatsMaxGapMilliseconds: evidenceWindow.dockerStatsMaxGapMilliseconds,
		evidenceWindow: {
			workloadStartedAt: evidenceWindow.workloadStartedAt,
			workloadFinishedAt: evidenceWindow.workloadFinishedAt,
		},
		dockerPeakByContainer: resourceValidation.peaks,
	},
	checkedAt: new Date().toISOString(),
};
writeFileSync(join(runDir, 'verification-report.json'), `${JSON.stringify(verification, null, 2)}\n`);
