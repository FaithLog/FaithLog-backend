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

const expectedPending = manifest.successCount + manifest.transientCount + manifest.permanentCount;
const expectedSkipped = manifest.inactiveCount + manifest.noTokenCount;
const expectedSent = manifest.successCount + manifest.transientCount;
const expectedFailed = manifest.permanentCount;
const expectedSendAttempts = manifest.successCount + (manifest.transientCount * 2) + manifest.permanentCount;

assert.equal(manifest.memberCount, 1000);
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
assert.equal(result.delivery.tokenUpdateCount, manifest.permanentCount);
assert.equal(result.delivery.fakeSendAttemptCount, expectedSendAttempts);
assert.equal(result.correctness.duplicateReplayCreatedCount, 0);
assert.equal(result.correctness.crossCampusMutationCount, 0);
assert.equal(result.correctness.nonFixtureTokenMutationCount, 0);
assert.equal(result.correctness.partialFailureContinued, true);
assert.equal(result.externalFcmUsed, false);
assert.equal(environment.externalFcm, false);
assert.equal(environment.sharedStack, false);

for (const phase of [result.creation, result.delivery]) {
	assert.ok(phase.durationMs > 0, 'durationMs must be positive');
	assert.ok(phase.throughputPerSecond > 0, 'throughputPerSecond must be positive');
	assert.ok(phase.dbPreparedStatements >= 0, 'dbPreparedStatements must be non-negative');
	assert.ok(phase.perUserDbCalls >= 0, 'perUserDbCalls must be non-negative');
}

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
	crossCampusMutationCount: result.correctness.crossCampusMutationCount,
	checkedAt: new Date().toISOString(),
};
writeFileSync(join(runDir, 'verification-report.json'), `${JSON.stringify(verification, null, 2)}\n`);
