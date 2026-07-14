import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const [manifestArgument, datasetMode, evidenceArgument] = process.argv.slice(2);
if (!manifestArgument || !datasetMode || !evidenceArgument) {
	throw new Error('Usage: node validate-db-correctness.mjs <manifest.json> <datasetMode> <db-evidence.json>');
}

const manifest = JSON.parse(fs.readFileSync(path.resolve(manifestArgument), 'utf8'));
const dataset = manifest.modes?.[datasetMode];
assert.equal(manifest.issue, 199);
assert.ok(dataset, `Missing dataset mode: ${datasetMode}`);

const evidenceText = fs.readFileSync(path.resolve(evidenceArgument), 'utf8').trim();
assert.ok(evidenceText, 'DB correctness evidence must not be empty');
const evidence = JSON.parse(evidenceText);
assert.deepEqual(
	evidence,
	dataset.expected,
	'DB correctness evidence must exact-match every expected summary field and per-poll response count',
);

process.stdout.write(`${JSON.stringify({
	status: 'db-correctness-verified',
	datasetId: manifest.datasetId,
	fixtureRunId: dataset.fixtureRunId,
	datasetMode,
	pollResponseCounts: evidence.pollResponseCounts,
	missingResponseCount: evidence.polls.missingResponseCount,
}, null, 2)}\n`);
