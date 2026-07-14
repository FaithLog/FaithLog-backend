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
const redisBefore = readFileSync(join(runDir, 'redis-commandstats-before.txt'), 'utf8');
const redisAfter = readFileSync(join(runDir, 'redis-commandstats-after.txt'), 'utf8');
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

const numericDelta = (before, after) => {
	const resultDelta = {};
	for (const [key, afterValue] of Object.entries(after ?? {})) {
		const beforeValue = before?.[key];
		if (typeof afterValue === 'number') {
			resultDelta[key] = afterValue - Number(beforeValue ?? 0);
		} else if (afterValue && typeof afterValue === 'object') {
			resultDelta[key] = numericDelta(beforeValue, afterValue);
		}
	}
	return resultDelta;
};

const assertFiniteNonNegativeNumbers = (value, path = 'evidence') => {
	for (const [key, nested] of Object.entries(value ?? {})) {
		const nestedPath = `${path}.${key}`;
		if (typeof nested === 'number') {
			assert.ok(Number.isFinite(nested) && nested >= 0, `${nestedPath} must be finite and non-negative`);
		} else if (nested && typeof nested === 'object') {
			assertFiniteNonNegativeNumbers(nested, nestedPath);
		}
	}
};

const redisCalls = (text) => Object.fromEntries(
	text.split(/\r?\n/)
		.filter((line) => line.startsWith('cmdstat_'))
		.map((line) => {
			const [name, values] = line.split(':', 2);
			const calls = values.split(',').find((value) => value.startsWith('calls='));
			return [name.slice('cmdstat_'.length), Number(calls?.slice('calls='.length) ?? 0)];
		}),
);

const redisDbSize = (text) => {
	const value = Number(text.match(/^dbsize=(\d+)$/m)?.[1]);
	assert.ok(Number.isInteger(value) && value >= 0, 'Redis DBSIZE evidence is required');
	return value;
};

const redisBeforeCalls = redisCalls(redisBefore);
const redisAfterCalls = redisCalls(redisAfter);
const redisDbSizeBefore = redisDbSize(redisBefore);
const redisDbSizeAfter = redisDbSize(redisAfter);
const redisDbSizeDelta = redisDbSizeAfter - redisDbSizeBefore;
const redisCommandCallDelta = Object.fromEntries(
	Object.entries(redisAfterCalls).map(([command, calls]) => [command, calls - (redisBeforeCalls[command] ?? 0)]),
);
const dockerRows = dockerStats.trim().split(/\r?\n/).slice(1).filter(Boolean);
assert.ok(dockerRows.length > 0, 'docker-stats.csv must contain measured samples');
const dockerPeakByContainer = {};
for (const row of dockerRows) {
	const [, container, cpuPercent, , memoryPercent] = row.split(',');
	assert.ok(container && cpuPercent && memoryPercent, `Invalid docker stats row: ${row}`);
	const cpu = Number(cpuPercent.replace('%', ''));
	const memory = Number(memoryPercent.replace('%', ''));
	assert.ok(Number.isFinite(cpu) && cpu >= 0, `Invalid Docker CPU sample: ${row}`);
	assert.ok(Number.isFinite(memory) && memory >= 0, `Invalid Docker memory sample: ${row}`);
	const peak = dockerPeakByContainer[container] ?? { cpuPercent: 0, memoryPercent: 0, sampleCount: 0 };
	peak.cpuPercent = Math.max(peak.cpuPercent, cpu);
	peak.memoryPercent = Math.max(peak.memoryPercent, memory);
	peak.sampleCount += 1;
	dockerPeakByContainer[container] = peak;
}

const postgresDelta = {
	database: numericDelta(postgresBefore.database, postgresAfter.database),
	tables: numericDelta(postgresBefore.tables, postgresAfter.tables),
};
assertFiniteNonNegativeNumbers(postgresDelta, 'postgresDelta');
assertFiniteNonNegativeNumbers(redisCommandCallDelta, 'redisCommandCallDelta');
assert.ok(postgresDelta.tables?.notification_logs, 'notification_logs PostgreSQL evidence is required');
assert.ok(postgresDelta.tables?.user_fcm_tokens, 'user_fcm_tokens PostgreSQL evidence is required');
const cardinalityBefore = postgresBefore.cardinality;
const cardinalityAfter = postgresAfter.cardinality;
const relationBytesBefore = postgresBefore.relationBytes;
assertFiniteNonNegativeNumbers(cardinalityBefore, 'postgresBefore.cardinality');
assertFiniteNonNegativeNumbers(cardinalityAfter, 'postgresAfter.cardinality');
assertFiniteNonNegativeNumbers(relationBytesBefore, 'postgresBefore.relationBytes');
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
		postgresBeforeCardinality: cardinalityBefore,
		postgresBeforeRelationBytes: relationBytesBefore,
		postgresDelta,
		redisDbSizeBefore,
		redisDbSizeDelta,
		redisCommandCallDelta,
		dockerSampleCount: dockerRows.length,
		dockerPeakByContainer,
	},
	checkedAt: new Date().toISOString(),
};
writeFileSync(join(runDir, 'verification-report.json'), `${JSON.stringify(verification, null, 2)}\n`);
