import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

const runDirsFile = process.env.RUN_DIRS_FILE;
const outputPath = process.env.OUTPUT_PATH;
assert.ok(runDirsFile, 'RUN_DIRS_FILE is required');
assert.ok(outputPath, 'OUTPUT_PATH is required');

const runDirs = readFileSync(runDirsFile, 'utf8')
	.split(/\r?\n/)
	.map((line) => line.trim())
	.filter(Boolean);
assert.ok(runDirs.length > 0, 'At least one run directory is required');
assert.equal(new Set(runDirs).size, runDirs.length, 'Duplicate run directories are forbidden');

const samples = runDirs.map((runDir) => ({
	runDir,
	manifest: JSON.parse(readFileSync(join(runDir, 'manifest.json'), 'utf8')),
	result: JSON.parse(readFileSync(join(runDir, 'scenario-result.json'), 'utf8')),
	verification: JSON.parse(readFileSync(join(runDir, 'verification-report.json'), 'utf8')),
	environment: JSON.parse(readFileSync(join(runDir, 'environment.json'), 'utf8')),
	runStatus: JSON.parse(readFileSync(join(runDir, 'run-status.json'), 'utf8')),
}));
for (const sample of samples) {
	assert.equal(sample.verification.status, 'verified');
	assert.equal(sample.runStatus.status, 'verified');
	assert.equal(sample.manifest.fixtureRunId, sample.result.fixtureRunId);
	assert.equal(sample.manifest.sampleKind, sample.result.sampleKind);
}

const measured = samples.filter((sample) => sample.manifest.sampleKind === 'measured');
const warmups = samples.filter((sample) => sample.manifest.sampleKind === 'warmup');
assert.ok(measured.length > 0, 'At least one measured fixtureRunId is required');
assert.equal(new Set(measured.map((sample) => sample.result.datasetId)).size, 1);
assert.equal(new Set(samples.map((sample) => sample.result.fixtureRunId)).size, samples.length,
	'Duplicate fixtureRunIds are forbidden');

const workloadSignature = (sample) => JSON.stringify({
	datasetId: sample.manifest.datasetId,
	campusId: sample.manifest.campusId,
	memberCount: sample.manifest.memberCount,
	successCount: sample.manifest.successCount,
	transientCount: sample.manifest.transientCount,
	permanentCount: sample.manifest.permanentCount,
	inactiveCount: sample.manifest.inactiveCount,
	noTokenCount: sample.manifest.noTokenCount,
	mixedTokenUserCount: sample.manifest.mixedTokenUserCount,
	springProfile: sample.environment.springProfile,
	fcmAdapter: sample.environment.fcmAdapter,
	dockerProject: sample.environment.dockerProject,
	postgresContainer: sample.environment.postgresContainer,
	redisContainer: sample.environment.redisContainer,
	postgresHostPort: sample.environment.postgresHostPort,
	postgresDatabase: sample.environment.postgresDatabase,
	redisHostPort: sample.environment.redisHostPort,
	postgresImageId: sample.environment.postgresImageId,
	redisImageId: sample.environment.redisImageId,
	gitCommit: sample.environment.gitCommit,
	businessDate: sample.environment.businessDate,
	executionModel: sample.environment.executionModel,
	warmupScope: sample.environment.warmupScope,
	externalEvidenceWindow: sample.environment.externalEvidenceWindow,
	javaRuntimeVersion: sample.result.javaRuntimeVersion,
	notificationType: sample.result.notificationType,
	retryBackoffPolicy: sample.result.retryBackoffPolicy,
});
assert.equal(new Set(samples.map(workloadSignature)).size, 1,
	'Warmup and measured samples must share one workload and runtime fingerprint');

const percentile = (values, percentileValue) => {
	const sorted = [...values].sort((left, right) => left - right);
	if (sorted.length === 1) return sorted[0];
	const index = (sorted.length - 1) * percentileValue;
	const lower = Math.floor(index);
	const upper = Math.ceil(index);
	if (lower === upper) return sorted[lower];
	return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower);
};

const distribution = (values) => ({
	p50: percentile(values, 0.50),
	p95: percentile(values, 0.95),
	p99: percentile(values, 0.99),
	max: Math.max(...values),
});

const phaseSummary = (phaseName) => {
	const phases = measured.map((sample) => sample.result[phaseName]);
	const durationMs = phases.map((phase) => phase.durationMs);
	const throughputPerSecond = phases.map((phase) => phase.throughputPerSecond);
	const dbPreparedStatements = phases.map((phase) => phase.dbPreparedStatements);
	const perUserDbCalls = phases.map((phase) => phase.perUserDbCalls);
	return {
		durationMs: distribution(durationMs),
		throughputPerSecond: distribution(throughputPerSecond),
		dbPreparedStatements: distribution(dbPreparedStatements),
		perUserDbCalls: distribution(perUserDbCalls),
		processCpuDurationMs: distribution(phases.map((phase) => phase.processCpuDurationMs)),
		heapUsedDeltaBytes: distribution(phases.map((phase) => phase.heapUsedDeltaBytes)),
	};
};

const endToEndSummary = {
	durationMs: distribution(measured.map((sample) => sample.result.endToEnd.durationMs)),
	throughputPerSecond: distribution(measured.map((sample) => sample.result.endToEnd.throughputPerSecond)),
};

const addNumericObjects = (left, right) => {
	const result = { ...left };
	for (const [key, value] of Object.entries(right ?? {})) {
		if (typeof value === 'number') {
			result[key] = (result[key] ?? 0) + value;
		} else if (value && typeof value === 'object') {
			result[key] = addNumericObjects(result[key] ?? {}, value);
		}
	}
	return result;
};

const mergeDockerPeaks = (left, right) => {
	const containers = new Set([...Object.keys(left ?? {}), ...Object.keys(right ?? {})]);
	return Object.fromEntries([...containers].map((container) => {
		const previous = left?.[container] ?? {};
		const current = right?.[container] ?? {};
		return [container, {
			cpuPercent: Math.max(previous.cpuPercent ?? 0, current.cpuPercent ?? 0),
			memoryPercent: Math.max(previous.memoryPercent ?? 0, current.memoryPercent ?? 0),
			sampleCount: (previous.sampleCount ?? 0) + (current.sampleCount ?? 0),
		}];
	}));
};

const evidenceTotals = measured.reduce((totals, sample) => ({
	postgresDelta: addNumericObjects(totals.postgresDelta, sample.verification.evidence.postgresDelta),
	redisCommandCallDelta: addNumericObjects(
		totals.redisCommandCallDelta,
		sample.verification.evidence.redisCommandCallDelta,
	),
	dockerSampleCount: totals.dockerSampleCount + sample.verification.evidence.dockerSampleCount,
	dockerPeakByContainer: mergeDockerPeaks(
		totals.dockerPeakByContainer,
		sample.verification.evidence.dockerPeakByContainer,
	),
}), { postgresDelta: {}, redisCommandCallDelta: {}, dockerSampleCount: 0, dockerPeakByContainer: {} });

const totals = measured.reduce((accumulator, sample) => {
	const statuses = sample.result.delivery.statusCounts;
	accumulator.targets += sample.manifest.memberCount;
	accumulator.pending += sample.result.creation.pendingLogs;
	accumulator.sent += statuses.SENT;
	accumulator.failed += statuses.FAILED;
	accumulator.skipped += statuses.SKIPPED;
	accumulator.createdLogs += sample.result.creation.createdLogs;
	accumulator.logInserts += sample.result.creation.logInsertCount;
	accumulator.logUpdates += sample.result.delivery.logUpdateCount;
	accumulator.tokenUpdates += sample.result.delivery.tokenUpdateCount;
	accumulator.fakeSendAttempts += sample.result.delivery.fakeSendAttemptCount;
	accumulator.fakePermanentFailures += sample.result.delivery.fakePermanentFailureCount;
	return accumulator;
}, {
	targets: 0,
	pending: 0,
	sent: 0,
	failed: 0,
	skipped: 0,
	createdLogs: 0,
	logInserts: 0,
	logUpdates: 0,
	tokenUpdates: 0,
	fakeSendAttempts: 0,
	fakePermanentFailures: 0,
});

const summary = {
	status: 'before-baseline',
	datasetId: measured[0].result.datasetId,
	warmupCount: warmups.length,
	measuredCount: measured.length,
	fixtureRunIds: measured.map((sample) => sample.result.fixtureRunId),
	creation: phaseSummary('creation'),
	delivery: phaseSummary('delivery'),
	endToEnd: endToEndSummary,
	providerFakeFailureRate: totals.fakeSendAttempts === 0
		? 0
		: totals.fakePermanentFailures / totals.fakeSendAttempts,
	finalLogFailureRate: totals.pending === 0 ? 0 : totals.failed / totals.pending,
	workloadFingerprint: workloadSignature(measured[0]),
	evidenceTotals,
	externalEvidenceWindow: measured[0].environment.externalEvidenceWindow,
	executionModel: measured[0].environment.executionModel,
	warmupScope: measured[0].environment.warmupScope,
	totals,
	externalFcmUsed: false,
	generatedAt: new Date().toISOString(),
};

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(summary, null, 2)}\n`);
writeFileSync(
	join(dirname(outputPath), 'baseline-summary.md'),
	[
		'# Issue #198 notification batch before baseline',
		'',
		`- measured samples: ${summary.measuredCount}`,
		`- warmups excluded: ${summary.warmupCount}`,
		`- creation duration p50/p95/p99/max (ms): ${Object.values(summary.creation.durationMs).join(' / ')}`,
		`- delivery duration p50/p95/p99/max (ms): ${Object.values(summary.delivery.durationMs).join(' / ')}`,
		`- end-to-end duration p50/p95/p99/max (ms): ${Object.values(summary.endToEnd.durationMs).join(' / ')}`,
		`- provider fake failure rate: ${summary.providerFakeFailureRate}`,
		`- final log failure rate: ${summary.finalLogFailureRate}`,
		`- execution model: ${summary.executionModel}`,
		`- external evidence window: ${summary.externalEvidenceWindow}`,
		'- external FCM used: false',
		'',
	].join('\n'),
);
