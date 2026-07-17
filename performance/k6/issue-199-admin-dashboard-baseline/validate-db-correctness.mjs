import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const [manifestArgument, datasetMode, evidenceBoundary, evidenceArgument] = process.argv.slice(2);
if (!manifestArgument || !datasetMode || !evidenceBoundary || !evidenceArgument) {
	throw new Error('Usage: node validate-db-correctness.mjs <manifest.json> <datasetMode> <boundary> <db-evidence.json>');
}
assert.ok(['before', 'pre-measured', 'after'].includes(evidenceBoundary));

const manifest = JSON.parse(fs.readFileSync(path.resolve(manifestArgument), 'utf8'));
const dataset = manifest.modes?.[datasetMode];
assert.equal(manifest.issue, 199);
assert.ok(dataset, `Missing dataset mode: ${datasetMode}`);

const evidenceText = fs.readFileSync(path.resolve(evidenceArgument), 'utf8').trim();
assert.ok(evidenceText, 'DB correctness evidence must not be empty');
const evidence = JSON.parse(evidenceText);
assert.equal(evidence.evidenceBoundary, evidenceBoundary, 'DB correctness evidence boundary mismatch');
const {evidenceBoundary: ignoredBoundary, ...actualSummary} = evidence;
assert.deepEqual(
	actualSummary,
	dataset.expected,
	'DB correctness evidence must exact-match every expected summary field and per-poll response count',
);

process.stdout.write(`${JSON.stringify({
	status: 'db-correctness-verified',
	automaticAdoption: false,
	evidenceBoundary,
	datasetId: manifest.datasetId,
	fixtureRunId: dataset.fixtureRunId,
	datasetMode,
	pollResponseCounts: actualSummary.pollResponseCounts,
	missingResponseCount: actualSummary.polls.missingResponseCount,
}, null, 2)}\n`);
