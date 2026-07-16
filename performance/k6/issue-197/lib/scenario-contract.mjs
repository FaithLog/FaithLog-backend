import fs from 'node:fs';
import path from 'node:path';
import { readJson, validateDevotionManifest } from './fixture-contract.mjs';
import { validateDevotionCardinality } from './validate-devotion-cardinality.mjs';
import { validateSummary } from './validate-k6-summary.mjs';

const [
	manifestPath, warmupSummaryPath, measuredSummaryPath, rollbackSummaryPath,
	measuredDirectCardinalityPath, finalCardinalityPath, resourceEvidencePath, dbWindowPath,
	runtimeIdentityPath, measuredCardinalityRuntimePath, outputPath, composeProject,
] = process.argv.slice(2);
const manifest = validateDevotionManifest(readJson(manifestPath, 'devotion manifest'));
const warmupSummary = readJson(warmupSummaryPath, 'warmup k6 summary');
const measuredSummary = readJson(measuredSummaryPath, 'measured k6 summary');
const rollbackSummary = readJson(rollbackSummaryPath, 'rollback k6 summary');
const measuredDirectCardinalityEvidence = readJson(measuredDirectCardinalityPath, 'measured direct cardinality evidence');
const finalCardinalityEvidence = readJson(finalCardinalityPath, 'final cardinality evidence');
const measuredDirectCardinality = validateDevotionCardinality(manifest, measuredDirectCardinalityEvidence.counts, 'measured');
const finalCardinality = validateDevotionCardinality(manifest, finalCardinalityEvidence.counts, 'final');
const dbCounters = finalCardinality.counts;
const dbWindowEvidence = readJson(dbWindowPath, 'measured DB window evidence');
const resources = readJson(resourceEvidencePath, 'measured resource window evidence');
const runtimeIdentityEvidence = readJson(runtimeIdentityPath, 'runtime identity evidence');
const measuredCardinalityRuntimeEvidence = readJson(measuredCardinalityRuntimePath, 'measured cardinality runtime continuity evidence');
const phaseMetrics = {
	warmup: validateSummary(warmupSummary, 'warmup', manifest.warmupUserIds.length),
	measured: validateSummary(measuredSummary, 'measured', manifest.measuredUserIds.length),
	rollback: validateSummary(rollbackSummary, 'rollback', manifest.rollbackUserIds.length),
};

const correctnessFailures = [];
if (measuredDirectCardinalityEvidence.exact !== true || measuredDirectCardinalityEvidence.status !== 'exact'
	|| measuredDirectCardinalityEvidence.phase !== 'measured') {
	correctnessFailures.push({ label: 'measured direct fixture cardinality', expected: 'exact/measured', actual: measuredDirectCardinalityEvidence });
}
if (finalCardinalityEvidence.exact !== true || finalCardinalityEvidence.status !== 'exact'
	|| finalCardinalityEvidence.phase !== 'final') {
	correctnessFailures.push({ label: 'final fixture cardinality', expected: 'exact/final', actual: finalCardinalityEvidence });
}
if (measuredCardinalityRuntimeEvidence.adoptable !== true || measuredCardinalityRuntimeEvidence.status !== 'continuous'
	|| measuredCardinalityRuntimeEvidence.checkpoint !== 'measuredCardinalityAfter') {
	correctnessFailures.push({ label: 'measured direct cardinality runtime continuity', expected: 'continuous', actual: measuredCardinalityRuntimeEvidence });
}
if (dbWindowEvidence.supporting !== true || dbWindowEvidence.status !== 'supporting-clean' || dbWindowEvidence.automaticAdoption !== false) {
	correctnessFailures.push({ label: 'measured DB supporting window', expected: 'supporting-clean/non-adoptable', actual: dbWindowEvidence });
}
if (resources.adoptable !== true || resources.status !== 'adoptable') {
	correctnessFailures.push({ label: 'measured resource window', expected: 'adoptable', actual: resources });
}
if (runtimeIdentityEvidence.adoptable !== true || runtimeIdentityEvidence.status !== 'continuous') {
	correctnessFailures.push({ label: 'runtime identity continuity', expected: 'continuous', actual: runtimeIdentityEvidence });
}
expectEqual(dbCounters.datasetId, manifest.datasetId, 'DB datasetId', correctnessFailures);
expectEqual(dbCounters.fixtureRunId, manifest.fixtureRunId, 'DB fixtureRunId', correctnessFailures);
expectEqual(Number(dbCounters.warmup?.weeklyCount), manifest.warmupUserIds.length, 'warmup weekly rows', correctnessFailures);
expectEqual(Number(dbCounters.warmup?.submittedCount), manifest.warmupUserIds.length, 'warmup submitted rows', correctnessFailures);
expectEqual(Number(dbCounters.measured?.weeklyCount), 1000, 'measured weekly rows', correctnessFailures);
expectEqual(Number(dbCounters.measured?.distinctWeeklyUsers), 1000, 'measured distinct weekly users', correctnessFailures);
expectEqual(Number(dbCounters.measured?.submittedCount), 1000, 'measured submitted rows', correctnessFailures);
expectEqual(Number(dbCounters.measured?.dailyCount), 7000, 'measured daily rows', correctnessFailures);
expectEqual(Number(dbCounters.measured?.distinctDailyUsers), 1000, 'measured distinct daily users', correctnessFailures);
expectEqual(Number(dbCounters.measured?.usersWithSevenDaily), 1000, 'users with daily 7 rows', correctnessFailures);
expectEqual(Number(dbCounters.measured?.correctDailyDateCount), 7000, 'daily rows in exact measured week', correctnessFailures);
expectEqual(Number(dbCounters.measured?.chargeCount), 1000, 'measured penalty charges', correctnessFailures);
expectEqual(Number(dbCounters.measured?.distinctChargeUsers), 1000, 'measured distinct charge users', correctnessFailures);
expectEqual(Number(dbCounters.measured?.correctChargeAmountCount), 1000, 'correct penalty amounts', correctnessFailures);
expectEqual(Number(dbCounters.measured?.distinctChargeSourceCount), 1000, 'unique charge sources', correctnessFailures);
expectEqual(Number(dbCounters.measured?.correctChargeBindingCount), 1000, 'charge source/week/user bindings', correctnessFailures);
expectEqual(Number(dbCounters.measured?.chargeAmountSum), 1000 * manifest.expectedPenaltyAmount, 'measured charge amount sum', correctnessFailures);
expectEqual(Number(dbCounters.measured?.duplicateChargeSourceGroups), 0, 'duplicate charge source groups', correctnessFailures);
expectEqual(Number(dbCounters.warmup?.chargeCount), manifest.warmupUserIds.length, 'warmup charge rows', correctnessFailures);
expectEqual(Number(dbCounters.successCampusDevotionChargeCount), manifest.warmupUserIds.length + 1000, 'success campus devotion charges', correctnessFailures);
expectEqual(Number(dbCounters.rollback?.weeklyCount), 0, 'rollback weekly rows', correctnessFailures);
expectEqual(Number(dbCounters.rollback?.dailyCount), 0, 'rollback daily rows', correctnessFailures);
expectEqual(Number(dbCounters.rollback?.chargeCount), 0, 'rollback charge rows', correctnessFailures);
expectEqual(Object.keys(resources.byRole || {}).sort().join(','), 'app,database,redis', 'Docker CPU/RAM roles', correctnessFailures);
for (const metric of ['p50', 'p95', 'p99', 'max', 'throughput', 'failureRate', 'transactions']) {
	if (phaseMetrics.measured[metric] === null) {
		correctnessFailures.push({ label: `measured ${metric}`, expected: 'numeric metric', actual: null });
	}
}
expectEqual(phaseMetrics.measured.transactions, 1000, 'measured transaction attempts', correctnessFailures);
expectEqual(phaseMetrics.rollback.transactions, manifest.rollbackUserIds.length, 'rollback transaction attempts', correctnessFailures);
expectEqual(phaseMetrics.measured.failureRate, 0, 'measured failure rate', correctnessFailures);
expectEqual(phaseMetrics.rollback.failureRate, 0, 'rollback contract failure rate', correctnessFailures);

const evidence = {
	scenario: 'devotion-write',
	status: correctnessFailures.length === 0 ? 'conditional-not-adoptable' : 'correctness-failed',
	automaticAdoption: false,
	classificationReason: 'fixture-owned direct joins are exact; shared-stack cumulative counters and unrelated activity are source-unattributed supporting evidence',
	datasetId: manifest.datasetId,
	fixtureRunId: manifest.fixtureRunId,
	composeProject,
	cohorts: {
		warmupUsers: manifest.warmupUserIds.length,
		measuredUsers: manifest.measuredUserIds.length,
		rollbackUsers: manifest.rollbackUserIds.length,
		warmupWeekStartDate: manifest.warmupWeekStartDate,
		measuredWeekStartDate: manifest.measuredWeekStartDate,
		rollbackWeekStartDate: manifest.rollbackWeekStartDate,
	},
	metrics: {
		...phaseMetrics,
	},
	resources,
	dbCounters,
	measuredDirectCardinalityEvidence: measuredDirectCardinality,
	finalCardinalityEvidence: finalCardinality,
	dbWindowEvidence,
	runtimeIdentityEvidence,
	measuredCardinalityRuntimeEvidence,
	correctnessFailures,
	transactionEvidence: {
		measuredAttemptCount: phaseMetrics.measured.transactions,
		rollbackAttemptCount: phaseMetrics.rollback.transactions,
		rollbackPersistedRows: Number(dbCounters.rollback?.weeklyCount || 0)
			+ Number(dbCounters.rollback?.dailyCount || 0)
			+ Number(dbCounters.rollback?.chargeCount || 0),
	},
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o600 });
if (correctnessFailures.length > 0) {
	throw new Error(`devotion correctness contract failed: ${JSON.stringify(correctnessFailures)}`);
}
process.stdout.write(`${JSON.stringify(evidence)}\n`);

function expectEqual(actual, expected, label, failures) {
	if (actual !== expected) {
		failures.push({ label, expected, actual });
	}
}
