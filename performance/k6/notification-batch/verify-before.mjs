import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const manifestPath = process.env.MANIFEST_PATH;
const runDir = process.env.RUN_DIR;
assert.ok(manifestPath, 'MANIFEST_PATH is required');
assert.ok(runDir, 'RUN_DIR is required');

const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
const result = JSON.parse(readFileSync(join(runDir, 'scenario-result.json'), 'utf8'));
const environment = JSON.parse(readFileSync(join(runDir, 'environment.json'), 'utf8'));
const postgresBefore = JSON.parse(readFileSync(join(runDir, 'postgres-before.json'), 'utf8'));
const postgresAfter = JSON.parse(readFileSync(join(runDir, 'postgres-after.json'), 'utf8'));
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
assert.equal(result.campusId, manifest.campusId);
assert.equal(result.creation.createdLogs, manifest.memberCount);
assert.equal(result.creation.logInsertCount, manifest.memberCount);
assert.equal(result.creation.pendingLogs, expectedPending);
assert.equal(result.creation.skippedLogs, expectedSkipped);
assert.equal(result.delivery.statusCounts.SENT, expectedSent);
assert.equal(result.delivery.statusCounts.FAILED, expectedFailed);
assert.equal(result.delivery.statusCounts.SKIPPED, expectedSkipped);
assert.equal(result.delivery.tokenLookupCount, expectedPending);
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
assert.equal(result.externalFcmUsed, false);
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
assert.match(environment.businessDate, /^\d{4}-\d{2}-\d{2}$/);
assert.match(environment.dockerProject, /^(?!.*faithlog-latest)[A-Za-z0-9_-]+$/);
assert.ok(Number.isInteger(environment.postgresHostPort) && environment.postgresHostPort > 0);
assert.ok(Number.isInteger(environment.redisHostPort) && environment.redisHostPort > 0);
assert.ok(result.endToEnd.durationMs > 0);
assert.ok(result.endToEnd.throughputPerSecond > 0);

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
const assertIsoTimestamp = (value, path) => {
	assert.equal(typeof value, 'string', `${path} must be an ISO timestamp string`);
	assert.ok(Number.isFinite(Date.parse(value)), `${path} must be a valid ISO timestamp`);
	return Date.parse(value);
};
const assertExactNumericRecord = (value, expectedKeys, path) => {
	exactKeys(value, expectedKeys, path);
	for (const key of expectedKeys) {
		assert.ok(typeof value[key] === 'number' && Number.isFinite(value[key]) && value[key] >= 0,
			`${path}.${key} must be a finite non-negative number`);
	}
};
const exactNumericDelta = (before, after, expectedKeys, path) => {
	assertExactNumericRecord(before, expectedKeys, `${path}.before`);
	assertExactNumericRecord(after, expectedKeys, `${path}.after`);
	return Object.fromEntries(expectedKeys.map((key) => {
		assert.ok(after[key] >= before[key], `${path}.${key} must be monotonic`);
		return [key, after[key] - before[key]];
	}));
};

const POSTGRES_TOP_LEVEL_KEYS = [
	'capturedAt', 'currentDatabase', 'statsReset', 'database', 'tables', 'cardinality', 'relationBytes',
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
	assertIsoTimestamp(snapshot.capturedAt, `postgres.${phase}.capturedAt`);
	assertIsoTimestamp(snapshot.statsReset, `postgres.${phase}.statsReset`);
	exactKeys(snapshot.tables, POSTGRES_TABLES, `postgres.${phase}.tables`);
	for (const tableName of POSTGRES_TABLES) {
		assertExactNumericRecord(snapshot.tables[tableName], POSTGRES_TABLE_KEYS,
			`postgres.${phase}.tables.${tableName}`);
	}
	assertExactNumericRecord(snapshot.cardinality, POSTGRES_CARDINALITY_KEYS,
		`postgres.${phase}.cardinality`);
	assertExactNumericRecord(snapshot.relationBytes, POSTGRES_RELATION_BYTES_KEYS,
		`postgres.${phase}.relationBytes`);
}
assert.ok(Date.parse(postgresBefore.capturedAt) < Date.parse(postgresAfter.capturedAt),
	'PostgreSQL capturedAt must be strictly ordered');
assert.equal(postgresAfter.statsReset, postgresBefore.statsReset, 'PostgreSQL stats_reset changed');
const postgresDelta = {
	database: exactNumericDelta(
		postgresBefore.database, postgresAfter.database, POSTGRES_DATABASE_KEYS, 'postgres.database',
	),
	tables: Object.fromEntries(POSTGRES_TABLES.map((tableName) => [tableName, exactNumericDelta(
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
	for (const key of ['uptimeSeconds', 'tcpPort', 'dbSize']) {
		assert.ok(Number.isSafeInteger(snapshot[key]) && snapshot[key] >= (key === 'dbSize' ? 0 : 1),
			`redis.${phase}.${key} must be an integer`);
	}
	assertExactNumericRecord(snapshot.commands, ['set'], `redis.${phase}.commands`);
}
assert.ok(Date.parse(redisBefore.capturedAt) < Date.parse(redisAfter.capturedAt),
	'Redis capturedAt must be strictly ordered');
assert.equal(redisAfter.runId, redisBefore.runId, 'Redis run_id changed during evidence capture');
assert.equal(redisAfter.tcpPort, redisBefore.tcpPort, 'Redis server endpoint changed during evidence capture');
assert.ok(redisAfter.uptimeSeconds >= redisBefore.uptimeSeconds, 'Redis uptime must be monotonic');
assert.ok(redisAfter.dbSize >= redisBefore.dbSize, 'Redis DBSIZE must be monotonic');
const redisDbSizeBefore = redisBefore.dbSize;
const redisDbSizeDelta = redisAfter.dbSize - redisBefore.dbSize;
const redisCommandCallDelta = exactNumericDelta(
	redisBefore.commands, redisAfter.commands, ['set'], 'redis.commands',
);

exactKeys(evidenceWindow,
	['workloadStartedAt', 'workloadFinishedAt', 'dockerStatsSampleIntervalSeconds'], 'evidenceWindow');
const workloadStartedAt = assertIsoTimestamp(evidenceWindow.workloadStartedAt, 'workloadStartedAt');
const workloadFinishedAt = assertIsoTimestamp(evidenceWindow.workloadFinishedAt, 'workloadFinishedAt');
assert.ok(workloadStartedAt < workloadFinishedAt, 'Workload evidence window must be strictly ordered');
assert.ok(Number.isSafeInteger(evidenceWindow.dockerStatsSampleIntervalSeconds)
	&& evidenceWindow.dockerStatsSampleIntervalSeconds >= 1
	&& evidenceWindow.dockerStatsSampleIntervalSeconds <= 60,
'Docker stats cadence must be an approved integer from 1 through 60 seconds');
assert.equal(evidenceWindow.dockerStatsSampleIntervalSeconds, environment.dockerStatsSampleIntervalSeconds,
	'Docker stats cadence must match the runtime environment');

const dockerLines = dockerStats.trim().split(/\r?\n/);
assert.equal(dockerLines.shift(),
	'captured_at,container_name,container_id,cpu_percent,memory_usage,memory_percent',
	'Docker stats schema mismatch');
const expectedContainers = new Map([
	[environment.postgresContainer, environment.postgresContainerId],
	[environment.redisContainer, environment.redisContainerId],
]);
assert.equal(expectedContainers.size, 2, 'PostgreSQL and Redis containers must be distinct');
const dockerSamples = new Map();
const dockerPeakByContainer = {};
let previousDockerRowTimestamp = null;
for (const row of dockerLines) {
	const columns = row.split(',');
	assert.equal(columns.length, 6, `Invalid Docker stats row: ${row}`);
	const [capturedAt, container, containerId, cpuPercent, memoryUsage, memoryPercent] = columns;
	const capturedTimestamp = assertIsoTimestamp(capturedAt, `docker.${container}.capturedAt`);
	if (previousDockerRowTimestamp !== null) {
		assert.ok(capturedTimestamp >= previousDockerRowTimestamp,
			'Docker sample rows must be timestamp-monotonic');
	}
	previousDockerRowTimestamp = capturedTimestamp;
	assert.equal(containerId, expectedContainers.get(container), `Unexpected Docker container identity: ${row}`);
	assert.ok(memoryUsage.length > 0, `Docker memory usage is required: ${row}`);
	const cpu = Number(cpuPercent.replace(/%$/, ''));
	const memory = Number(memoryPercent.replace(/%$/, ''));
	assert.ok(Number.isFinite(cpu) && cpu >= 0, `Invalid Docker CPU sample: ${row}`);
	assert.ok(Number.isFinite(memory) && memory >= 0, `Invalid Docker memory sample: ${row}`);
	const sampleContainers = dockerSamples.get(capturedAt) ?? new Set();
	assert.equal(sampleContainers.has(container), false, `Duplicate Docker container row at ${capturedAt}`);
	sampleContainers.add(container);
	dockerSamples.set(capturedAt, sampleContainers);
	const peak = dockerPeakByContainer[container]
		?? { containerId, cpuPercent: 0, memoryPercent: 0, sampleCount: 0 };
	peak.cpuPercent = Math.max(peak.cpuPercent, cpu);
	peak.memoryPercent = Math.max(peak.memoryPercent, memory);
	peak.sampleCount += 1;
	dockerPeakByContainer[container] = peak;
}
assert.ok(dockerSamples.size >= 2, 'At least two Docker sample instants are required');
const dockerTimestamps = [...dockerSamples.keys()].sort((left, right) => Date.parse(left) - Date.parse(right));
for (const [capturedAt, containers] of dockerSamples) {
	assert.deepEqual([...containers].sort(), [...expectedContainers.keys()].sort(),
		`Docker sample ${capturedAt} must contain exact PostgreSQL and Redis identities`);
}
for (let index = 1; index < dockerTimestamps.length; index += 1) {
	const gapMs = Date.parse(dockerTimestamps[index]) - Date.parse(dockerTimestamps[index - 1]);
	assert.ok(gapMs > 0, 'Docker sample timestamps must be strictly monotonic');
	assert.ok(gapMs <= (evidenceWindow.dockerStatsSampleIntervalSeconds * 1000) + 5000,
		'Docker sample gap exceeds the approved cadence tolerance');
}
assert.ok(Date.parse(dockerTimestamps[0]) <= workloadStartedAt,
	'Docker sampling must begin before the workload');
assert.ok(Date.parse(dockerTimestamps.at(-1)) >= workloadFinishedAt,
	'Docker sampling must finish after the workload');
const dockerRows = dockerLines;

const cardinalityBefore = postgresBefore.cardinality;
const cardinalityAfter = postgresAfter.cardinality;
const relationBytesBefore = postgresBefore.relationBytes;
assert.equal(
	cardinalityAfter.notificationLogsTotal - cardinalityBefore.notificationLogsTotal,
	result.creation.logInsertCount,
	'notification_logs total cardinality must grow by the exact request-scoped insert count',
);
assert.equal(
	cardinalityAfter.issue198MarkerLogsTotal - cardinalityBefore.issue198MarkerLogsTotal,
	result.creation.logInsertCount,
	'Issue #198 marker log cardinality must grow by the exact insert count',
);
assert.equal(
	cardinalityAfter.userFcmTokensTotal - cardinalityBefore.userFcmTokensTotal,
	0,
	'the measured scenario must not insert or delete FCM token rows',
);
assert.equal(
	cardinalityBefore.activeTokensTotal - cardinalityAfter.activeTokensTotal,
	result.delivery.tokenUpdateCount,
	'active token cardinality must decrease by the exact permanent failure count',
);
assert.equal(
	cardinalityBefore.issue198ActiveDummyTokens - cardinalityAfter.issue198ActiveDummyTokens,
	result.delivery.tokenUpdateCount,
	'active Issue #198 dummy token cardinality must decrease exactly',
);
assert.equal(redisDbSizeDelta, manifest.memberCount, 'one retained dedupe key per target is required');
assert.equal(
	postgresDelta.tables.notification_logs.n_tup_ins,
	result.creation.logInsertCount,
	'notification_logs physical insert evidence must exactly match the logical insert count',
);
assert.equal(
	postgresDelta.tables.notification_logs.n_tup_upd,
	result.delivery.logUpdateCount,
	'notification_logs physical update evidence must exactly match the logical update count',
);
assert.equal(
	postgresDelta.tables.user_fcm_tokens.n_tup_upd,
	result.delivery.tokenUpdateCount,
	'user_fcm_tokens physical update evidence must exactly match permanent dummy-token deactivation',
);
assert.equal(
	redisCommandCallDelta.set,
	(manifest.memberCount * 2) + 1,
	'Redis SET evidence must equal creation + replay dedupe reservations + one delivery lock',
);

const verification = {
	status: 'verified',
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
		postgresStatsReset: postgresBefore.statsReset,
		postgresCapturedAt: { before: postgresBefore.capturedAt, after: postgresAfter.capturedAt },
		postgresBeforeCardinality: cardinalityBefore,
		postgresBeforeRelationBytes: relationBytesBefore,
		postgresDelta,
		redisRunId: redisBefore.runId,
		redisUptimeBefore: redisBefore.uptimeSeconds,
		redisCapturedAt: { before: redisBefore.capturedAt, after: redisAfter.capturedAt },
		redisDbSizeBefore,
		redisDbSizeDelta,
		redisCommandCallDelta,
		dockerSampleCount: dockerRows.length,
		dockerSampleInstantCount: dockerSamples.size,
		dockerStatsSampleIntervalSeconds: evidenceWindow.dockerStatsSampleIntervalSeconds,
		evidenceWindow: {
			workloadStartedAt: evidenceWindow.workloadStartedAt,
			workloadFinishedAt: evidenceWindow.workloadFinishedAt,
		},
		dockerPeakByContainer,
	},
	checkedAt: new Date().toISOString(),
};
writeFileSync(join(runDir, 'verification-report.json'), `${JSON.stringify(verification, null, 2)}\n`);
