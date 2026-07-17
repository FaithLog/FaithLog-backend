import fs from 'node:fs';
import path from 'node:path';
import { readJson, validateRetentionManifest } from './lib/fixture-contract.mjs';

const [manifestPath, actualCountsPath, outputPath, composeProject] = process.argv.slice(2);
const manifest = validateRetentionManifest(readJson(manifestPath, 'retention manifest'));
const actualEvidence = readJson(actualCountsPath, 'actual candidate count evidence');

if (actualEvidence.datasetId !== manifest.datasetId || actualEvidence.fixtureRunId !== manifest.fixtureRunId) {
	throw new Error('actual candidate evidence does not match datasetId/fixtureRunId.');
}

const expectedDeleteCounts = manifest.expectedDeleteCounts;
const actualCandidateCounts = actualEvidence.actualCandidateCounts;
const mismatches = [];
if (Number(actualEvidence.annualForeignKeyBlockers) !== 0) {
	mismatches.push({
		name: 'annualForeignKeyBlockers',
		expected: 0,
		actual: Number(actualEvidence.annualForeignKeyBlockers),
	});
}
if (Number(actualEvidence.outsideFixtureCandidateRoots) !== 0) {
	mismatches.push({
		name: 'outsideFixtureCandidateRoots',
		expected: 0,
		actual: Number(actualEvidence.outsideFixtureCandidateRoots),
	});
}
for (const [name, expected] of Object.entries(expectedDeleteCounts)) {
	const actual = Number(actualCandidateCounts?.[name]);
	if (actual !== expected) {
		mismatches.push({ name, expected, actual });
	}
}

const evidence = {
	scenario: 'retention-dry-verify-only',
	status: mismatches.length === 0 ? 'scenario-ready' : 'contract-mismatch',
	measurementStatus: 'not-measured',
	datasetId: manifest.datasetId,
	fixtureRunId: manifest.fixtureRunId,
	datasetPrefix: manifest.datasetPrefix,
	composeProject,
	referenceInstant: manifest.referenceInstant,
	expectedDeleteCounts,
	actualCandidateCounts,
	annualForeignKeyBlockers: Number(actualEvidence.annualForeignKeyBlockers),
	outsideFixtureCandidateRoots: Number(actualEvidence.outsideFixtureCandidateRoots),
	mismatches,
	cleanupBatchEvidence: {
		productionBehavior: 'one unbounded repository operation sequence per daily/annual transaction',
		safeManualTriggerAvailable: false,
		executionMode: 'dry-verify-only',
	},
	metrics: {
		p50: null,
		p95: null,
		p99: null,
		max: null,
		throughput: null,
		failureRate: null,
		cpu: null,
		ram: null,
	},
	fcmImpact: 'none: scheduler and cleanup were not triggered',
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o600 });
if (mismatches.length > 0) {
	throw new Error(`retention candidate counts do not match expectedDeleteCounts: ${JSON.stringify(mismatches)}`);
}
process.stdout.write(`${JSON.stringify(evidence)}\n`);
