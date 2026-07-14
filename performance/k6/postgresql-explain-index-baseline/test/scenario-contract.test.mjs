import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scenarioRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const expectedIssues = [192, 193, 195, 196, 197, 198, 199];
const expectedQueryIds = [
	'i192-coffee-response-graph',
	'i192-due-coffee-polls',
	'i192-existing-settlement-charges',
	'i192-meal-settlement-groups',
	'i193-admin-charge-filter-page',
	'i193-member-charge-history',
	'i193-my-account-aggregate',
	'i195-admin-campus-list',
	'i195-admin-user-page',
	'i195-campus-member-list',
	'i195-duty-assignment-list',
	'i196-poll-comments',
	'i196-poll-missing-responders',
	'i196-poll-result-graph',
	'i196-poll-template-options',
	'i196-prayer-assignable-members',
	'i196-prayer-board-graph',
	'i196-prayer-group-list',
	'i197-retention-candidate-counts',
	'i197-weekly-devotion-daily-graph',
	'i198-pending-notification-recovery',
	'i198-sendable-fcm-token-bulk',
	'i199-dashboard-summary',
	'i199-open-poll-response-counts',
];

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
	assert.deepEqual([...ids].sort(), expectedQueryIds);
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
	assert.doesNotMatch(
		runner,
		/runCommand\(['"]docker['"],\s*\[\s*['"](?:down|up|build|prune|restart)['"]/i
	);
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

test('canonical Compose-project lock conflicts across scenario owners and releases non-recursively', async () => {
	const runtimeUrl = pathToFileURL(path.join(scenarioRoot, 'runtime-contract.mjs')).href;
	const { acquireProjectLock, releaseProjectLock } = await import(runtimeUrl);
	const lockRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-issue-194-lock-'));
	try {
		const first = acquireProjectLock('faithlog-latest', 'issue-193', lockRoot);
		assert.equal(first.path, path.join(lockRoot, 'faithlog-performance-faithlog-latest.lock'));
		assert.throws(
			() => acquireProjectLock('faithlog-latest', 'issue-194', lockRoot),
			/already holds|another performance|lock/i
		);
		releaseProjectLock(first);
		assert.equal(fs.existsSync(first.path), false);
	} finally {
		fs.rmSync(lockRoot, { recursive: true, force: true });
	}
});

test('WARM_RUNS is mandatory and rejects missing or out-of-range workload input', async () => {
	const runtimeUrl = pathToFileURL(path.join(scenarioRoot, 'runtime-contract.mjs')).href;
	const { parseWarmRuns } = await import(runtimeUrl);
	assert.throws(() => parseWarmRuns(undefined), /WARM_RUNS.*required/i);
	assert.throws(() => parseWarmRuns('0'), /1 through 20/i);
	assert.throws(() => parseWarmRuns('21'), /1 through 20/i);
	assert.equal(parseWarmRuns('3'), 3);
});

test('database identity preflight fails closed when psql target is not the inspected container database', async () => {
	const runtimeUrl = pathToFileURL(path.join(scenarioRoot, 'runtime-contract.mjs')).href;
	const { validateDatabaseIdentity } = await import(runtimeUrl);
	const inspected = {
		postgresContainerId: 'container-194',
		composeProject: 'faithlog-latest',
		composeService: 'postgres',
		containerStartedAt: '2026-07-14T00:00:00.000Z',
		postgresInternalPort: 5432,
		containerNetworkAddresses: ['172.30.0.4'],
	};
	const matching = {
		serverAddress: '172.30.0.4',
		serverPort: 5432,
		database: 'faithlog',
		postmasterStartedAt: '2026-07-14T00:00:01.000Z',
	};
	assert.doesNotThrow(() => validateDatabaseIdentity(inspected, matching, 'faithlog'));
	assert.throws(
		() => validateDatabaseIdentity(inspected, { ...matching, serverAddress: '172.30.0.99' }, 'faithlog'),
		/not the inspected PostgreSQL container/i
	);
	assert.throws(
		() => validateDatabaseIdentity(inspected, { ...matching, database: 'other' }, 'faithlog'),
		/database/i
	);
});

test('planner integrity validator blocks adoption for setting, analyze, n-mod, or external-activity contamination', async () => {
	const runtimeUrl = pathToFileURL(path.join(scenarioRoot, 'runtime-contract.mjs')).href;
	const { validateMeasurementIntegrity } = await import(runtimeUrl);
	const clean = {
		settings: { enable_seqscan: 'on', work_mem: '4096' },
		tableStatistics: [{
			table: 'charge_items',
			lastAnalyze: '2026-07-14T00:00:00Z',
			lastAutoanalyze: null,
			nModSinceAnalyze: 0,
		}],
		externalActivity: { activeSessionCount: 0, sessions: [] },
	};
	assert.deepEqual(validateMeasurementIntegrity(clean, structuredClone(clean)), {
		adoptable: true,
		reasons: [],
	});
	for (const contaminated of [
		{ ...structuredClone(clean), settings: { enable_seqscan: 'off', work_mem: '4096' } },
		{ ...structuredClone(clean), tableStatistics: [{ ...clean.tableStatistics[0], lastAutoanalyze: '2026-07-14T00:01:00Z' }] },
		{ ...structuredClone(clean), tableStatistics: [{ ...clean.tableStatistics[0], nModSinceAnalyze: 1 }] },
		{ ...structuredClone(clean), externalActivity: { activeSessionCount: 1, sessions: [{ pid: 42 }] } },
	]) {
		assert.equal(validateMeasurementIntegrity(clean, contaminated).adoptable, false);
	}
});

test('inventory classifies exact, reconstructed, and synthetic SQL and forbids synthetic production-before evidence', async () => {
	const evidenceUrl = pathToFileURL(path.join(scenarioRoot, 'evidence-contract.mjs')).href;
	const { validateEvidenceInventory, groupEvidenceQueries } = await import(evidenceUrl);
	const inventory = JSON.parse(read('inventory.json'));
	assert.doesNotThrow(() => validateEvidenceInventory(inventory));
	const groups = groupEvidenceQueries(inventory.queries);
	assert.ok(Array.isArray(groups.exactCurrentProduction));
	assert.ok(Array.isArray(groups.reconstructedCurrentQuery));
	assert.ok(Array.isArray(groups.syntheticCandidate));
	for (const id of ['i193-admin-charge-filter-page', 'i199-dashboard-summary']) {
		const query = inventory.queries.find((item) => item.id === id);
		assert.equal(query.evidenceClass, 'synthetic-candidate');
		assert.equal(query.productionBeforeEligible, false);
		assert.ok(query.productionSourceRefs.length > 0);
	}
	assert.ok(inventory.queries.every((query) => (
		query.evidenceClass === 'exact-current-production'
		|| query.productionBeforeEligible === false
	)));
});

test('plan hash preserves topology while ignoring runtime-only metric differences', async () => {
	const normalizerUrl = pathToFileURL(path.join(scenarioRoot, 'normalize-plan.mjs')).href;
	const { normalizeExplain } = await import(normalizerUrl);
	const nested = normalizeExplain(JSON.parse(read('test/fixtures/explain-topology-nested.json')));
	const siblings = normalizeExplain(JSON.parse(read('test/fixtures/explain-topology-siblings.json')));
	assert.notEqual(nested.planHash, siblings.planHash);
	assert.deepEqual(nested.structure.tree.children[0].children[0].nodeType, 'Seq Scan');
	assert.equal(siblings.structure.tree.children.length, 2);
});
