import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
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

const samples = runDirs.map((runDir) => ({
	runDir,
	manifest: JSON.parse(readFileSync(join(runDir, 'manifest.json'), 'utf8')),
	result: JSON.parse(readFileSync(join(runDir, 'scenario-result.json'), 'utf8')),
	verification: JSON.parse(readFileSync(join(runDir, 'verification-report.json'), 'utf8')),
}));
for (const sample of samples) {
	assert.equal(sample.verification.status, 'verified');
	assert.equal(sample.manifest.fixtureRunId, sample.result.fixtureRunId);
	assert.equal(sample.manifest.sampleKind, sample.result.sampleKind);
}

const measured = samples.filter((sample) => sample.manifest.sampleKind === 'measured');
const warmups = samples.filter((sample) => sample.manifest.sampleKind === 'warmup');
assert.ok(measured.length > 0, 'At least one measured fixtureRunId is required');
assert.equal(new Set(measured.map((sample) => sample.result.datasetId)).size, 1);

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
});

const summary = {
	status: 'before-baseline',
	datasetId: measured[0].result.datasetId,
	warmupCount: warmups.length,
	measuredCount: measured.length,
	fixtureRunIds: measured.map((sample) => sample.result.fixtureRunId),
	creation: phaseSummary('creation'),
	delivery: phaseSummary('delivery'),
	providerFakeFailureRate: totals.pending === 0 ? 0 : totals.failed / totals.pending,
	scenarioFailureRate: 0,
	totals,
	externalFcmUsed: false,
	generatedAt: new Date().toISOString(),
};

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
		`- provider fake failure rate: ${summary.providerFakeFailureRate}`,
		`- scenario failure rate: ${summary.scenarioFailureRate}`,
		'- external FCM used: false',
		'',
	].join('\n'),
);
