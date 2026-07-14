import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scenarioRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const expectedIssues = [192, 193, 195, 196, 197, 198, 199];

function read(relativePath) {
	return fs.readFileSync(path.join(scenarioRoot, relativePath), 'utf8');
}

test('inventory connects every approved performance issue to read-only SQL and index expectations', () => {
	const inventory = JSON.parse(read('inventory.json'));
	assert.deepEqual(inventory.issueNumbers, expectedIssues);
	assert.equal(inventory.datasetContract.memberCount, 1000);
	assert.equal(inventory.datasetContract.datasetIdField, 'datasetId');
	assert.equal(inventory.datasetContract.fixtureRunIdField, 'fixtureRunId');
	assert.ok(inventory.queries.length >= expectedIssues.length);

	const coveredIssues = new Set();
	const ids = new Set();
	for (const query of inventory.queries) {
		coveredIssues.add(query.issueNumber);
		assert.ok(!ids.has(query.id), `duplicate query id: ${query.id}`);
		ids.add(query.id);
		assert.equal(query.statementClass, 'SELECT');
		assert.ok(query.expectedAccess.predicateColumns.length > 0);
		assert.ok(Array.isArray(query.expectedAccess.orderColumns));
		assert.ok(Array.isArray(query.expectedAccess.joinColumns));
		assert.ok(query.candidateIndexes.length > 0);
		assert.ok(query.correctnessChecks.length > 0);

		const sql = read(query.sqlFile);
		assert.match(sql, /^\s*(?:--[^\n]*\n\s*)*(?:WITH|SELECT)\b/i);
		assert.doesNotMatch(sql, /\b(?:INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|VACUUM|ANALYZE)\b/i);
	}
	assert.deepEqual([...coveredIssues].sort((a, b) => a - b), expectedIssues);
});

test('runner requires traceable fixture identity, exclusive lock, runtime credentials, and actual Compose labels', () => {
	const wrapper = read('run-baseline.sh');
	const runner = read('run-baseline.mjs');
	assert.match(wrapper, /node .*run-baseline\.mjs/);
	for (const input of ['DATASET_ID', 'FIXTURE_RUN_ID', 'CROSS_ISSUE_REPORT', 'POSTGRES_CONTAINER']) {
		assert.match(runner, new RegExp(input));
	}
	assert.match(runner, /PGPASSWORD/);
	assert.match(runner, /mkdirSync\([^)]*recursive:\s*false/);
	assert.match(runner, /docker[\s\S]*inspect/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /com\.docker\.compose\.service/);
	assert.match(runner, /EXPLAIN \(ANALYZE, BUFFERS, FORMAT JSON\)/);
	assert.match(runner, /BEGIN READ ONLY/);
	assert.match(runner, /ALLOW_EXPLAIN_ANALYZE/);
	assert.match(runner, /another performance or load run/i);
	assert.doesNotMatch(runner, /docker[\s\S]*(?:down|up|build|prune|restart)/i);
	assert.doesNotMatch(runner, /(?:PGPASSWORD|password)\s*[:=]\s*["'][^"']+["']/i);
});

test('normalizer extracts comparable plan metrics and hashes only normalized plan structure', async () => {
	const normalizerUrl = pathToFileURL(path.join(scenarioRoot, 'normalize-plan.mjs')).href;
	const { normalizeExplain } = await import(normalizerUrl);
	const first = JSON.parse(read('test/fixtures/explain-plan-a.json'));
	const second = JSON.parse(read('test/fixtures/explain-plan-b.json'));

	const normalizedA = normalizeExplain(first);
	const normalizedB = normalizeExplain(second);
	assert.equal(normalizedA.planHash, normalizedB.planHash);
	assert.equal(normalizedA.metrics.planningTimeMs, 0.25);
	assert.equal(normalizedA.metrics.executionTimeMs, 3.5);
	assert.deepEqual(normalizedA.metrics.nodeTypes, ['Bitmap Heap Scan', 'Bitmap Index Scan']);
	assert.equal(normalizedA.metrics.planRows, 1000);
	assert.equal(normalizedA.metrics.actualRows, 900);
	assert.equal(normalizedA.metrics.loops, 3);
	assert.equal(normalizedA.metrics.sharedHitBlocks, 42);
	assert.equal(normalizedA.metrics.sharedReadBlocks, 7);
	assert.equal(normalizedA.metrics.rowsRemoved, 11);
	assert.equal(normalizedA.metrics.scanSummary.bitmapHeapScan, 1);
	assert.equal(normalizedA.metrics.scanSummary.bitmapIndexScan, 1);
	assert.equal(normalizedA.metrics.scanSummary.seqScan, 0);
	assert.equal(normalizedA.metrics.scanSummary.indexScan, 0);
	assert.equal(normalizedA.metrics.scanSummary.indexOnlyScan, 0);
	assert.equal(normalizedA.structure.nodes[0].relationName, 'charge_items');
	assert.equal(normalizedA.structure.nodes[1].indexName, 'candidate_charge_idx');
	assert.notEqual(normalizedA.metrics.executionTimeMs, normalizedB.metrics.executionTimeMs);
});

test('phase and planner-state contract never represents the first observation as a true cold cache', () => {
	const contract = JSON.parse(read('report-contract.json'));
	assert.deepEqual(contract.measurementPhases.map((phase) => phase.id), [
		'cold_like_observation',
		'warm_cache',
	]);
	assert.equal(contract.measurementPhases[0].cacheResetPerformed, false);
	assert.match(contract.measurementPhases[0].interpretation, /not (?:a )?cold cache/i);
	assert.ok(contract.plannerState.requiredFields.includes('capturedAt'));
	assert.ok(contract.plannerState.requiredFields.includes('lastAnalyze'));
	assert.ok(contract.plannerState.requiredFields.includes('lastAutoanalyze'));
	assert.ok(contract.plannerState.requiredFields.includes('nModSinceAnalyze'));
	assert.ok(contract.plannerState.requiredFields.includes('externalActivity'));
	assert.ok(contract.planMetrics.includes('planHash'));
	assert.ok(contract.planMetrics.includes('planningTimeMs'));
	assert.ok(contract.planMetrics.includes('executionTimeMs'));
});

test('raw and normalized reports remain in the issue-local ignored path', () => {
	const ignore = read('reports/.gitignore');
	assert.match(ignore, /^\*$/m);
	assert.match(ignore, /^!\.gitignore$/m);
});
