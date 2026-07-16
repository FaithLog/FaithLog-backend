import assert from 'node:assert/strict';
import { test } from 'node:test';

const MODES = ['coffee-sequential', 'meal-sequential', 'coffee-concurrent', 'meal-concurrent'];
const EXPECTED_WRITES = Object.freeze({ 'coffee-sequential': 11000, 'meal-sequential': 11000, 'coffee-concurrent': 6000, 'meal-concurrent': 6000 });
const CONTRACT = Object.freeze({ pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180, expectedChargeWrites: EXPECTED_WRITES });
const CASE = Object.freeze({ datasetId: 'PERFORMANCE_192_READY', fixtureRunId: 'READY_192', executionRunId: 'EXEC192_READY' });
const TABLES = ['users', 'campus_members', 'campus_duty_assignments', 'polls', 'poll_options', 'poll_responses', 'poll_response_options', 'payment_accounts', 'charge_items', 'meal_poll_settlements', 'meal_poll_charge_groups', 'notification_logs'];

test('insufficient sequential headroom prevents quiet start even with zero workers and stable maintenance', async () => {
	const { evaluateMaintenanceReadinessObservations } = await contractModule();
	const result = evaluateMaintenanceReadinessObservations(Array.from({ length: 7 }, (_, index) => observation(index * 5, { nMod: '1', reltuples: '100000' })), CONTRACT, 'coffee-sequential');
	assert.equal(result.finalStatus, 'pending');
	assert.equal(result.quietStartedAt, null);
	assert.equal(result.headroom.sufficient, false);
});

test('natural autoanalyze drift and n_mod reset starts a new exact 30-second quiet window', async () => {
	const { evaluateMaintenanceReadinessObservations } = await contractModule();
	const seconds = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45];
	const observations = seconds.map((second) => observation(second, second < 15
		? { nMod: '5000', reltuples: '100000', autoanalyzeCount: '4' }
		: { nMod: '0', reltuples: '200000', autoanalyzeCount: '5', lastAutoanalyze: iso(15) }));
	const result = evaluateMaintenanceReadinessObservations(observations, CONTRACT, 'coffee-sequential');
	assert.equal(result.finalStatus, 'passed');
	assert.equal(result.quietStartedAt, iso(15));
	assert.equal(result.finishedAt, iso(45));
	assert.ok(result.resetCount >= 1);
});

test('projected modifications pass at triggerAt minus one and fail at triggerAt', async () => {
	const { computeChargeItemsHeadroom } = await contractModule();
	const pass = computeChargeItemsHeadroom(headroomRaw({ nMod: '999', reltuples: '1', globalBase: '11999', globalScale: '0' }), '11000');
	const fail = computeChargeItemsHeadroom(headroomRaw({ nMod: '1000', reltuples: '1', globalBase: '11999', globalScale: '0' }), '11000');
	assert.equal(pass.triggerAt, '12000'); assert.equal(pass.projectedModifications, '11999'); assert.equal(pass.sufficient, true);
	assert.equal(fail.triggerAt, '12000'); assert.equal(fail.projectedModifications, '12000'); assert.equal(fail.sufficient, false);
});

test('readiness contract exact-binds all four mode expected write counts', async () => {
	const { validateMaintenanceReadinessContract } = await contractModule();
	assert.doesNotThrow(() => validateMaintenanceReadinessContract(CONTRACT));
	for (const value of [
		{ ...CONTRACT, expectedChargeWrites: { ...EXPECTED_WRITES, 'coffee-sequential': 10999 } },
		{ ...CONTRACT, expectedChargeWrites: Object.fromEntries(Object.entries(EXPECTED_WRITES).slice(1)) },
		{ ...CONTRACT, expectedChargeWrites: { ...EXPECTED_WRITES, foreign: 1 } },
	]) assert.throws(() => validateMaintenanceReadinessContract(value), /maintenance.*readiness/i);
});

test('reloption overrides, global fallback, decimal scales, and unsafe-number boundaries remain lossless', async () => {
	const { computeChargeItemsHeadroom } = await contractModule();
	const override = computeChargeItemsHeadroom(headroomRaw({ relBase: '100', relScale: '0.25', reltuples: '3.5' }), '1');
	assert.equal(override.effectiveBaseThreshold, '100');
	assert.deepEqual(override.effectiveScaleFactor, { numerator: '1', denominator: '4' });
	assert.equal(override.triggerAt, '101');
	const fallback = computeChargeItemsHeadroom(headroomRaw({ globalBase: '50', globalScale: '0.1', reltuples: '3.5' }), '1');
	assert.equal(fallback.triggerAt, '51');
	assert.deepEqual(fallback.effectiveScaleFactor, { numerator: '1', denominator: '10' });
	const large = computeChargeItemsHeadroom(headroomRaw({ nMod: '9007199254740992', reltuples: '1', globalBase: '9007199254753000', globalScale: '0' }), '11000');
	assert.equal(large.projectedModifications, '9007199254751992');
	assert.equal(large.sufficient, true);
});

test('disabled autoanalyze and malformed or unknown statistics fail closed', async () => {
	const { computeChargeItemsHeadroom } = await contractModule();
	const mutations = [
		(value) => { value.globalAutovacuumEnabled = false; },
		(value) => { value.relationAutovacuumEnabled = false; },
		(value) => { value.reltuples = '-1'; },
		(value) => { value.reltuples = null; },
		(value) => { value.nModSinceAnalyze = null; },
		(value) => { value.nModSinceAnalyze = '-1'; },
		(value) => { value.nModSinceAnalyze = Number.MAX_SAFE_INTEGER + 1; },
		(value) => { value.globalScaleFactor = 'NaN'; },
		(value) => { value.relationBaseThreshold = '-1'; },
	];
	for (const mutate of mutations) { const value = headroomRaw(); mutate(value); assert.throws(() => computeChargeItemsHeadroom(value, '11000'), /headroom/i); }
});

test('mode evidence exact-binds lossless final headroom and rejects forged values', async () => {
	const { evaluateMaintenanceReadinessObservations, validateMaintenanceReadinessEvidence } = await contractModule();
	const mode = 'coffee-concurrent';
	const result = evaluateMaintenanceReadinessObservations(Array.from({ length: 7 }, (_, index) => observation(index * 5, { nMod: '0', reltuples: '100000' })), CONTRACT, mode);
	const evidence = { case: { ...CASE, mode }, ...result };
	assert.doesNotThrow(() => validateMaintenanceReadinessEvidence(evidence, { ...CASE, mode }, CONTRACT));
	for (const mutate of [
		(value) => { value.headroom.expectedWrites = '11000'; },
		(value) => { value.headroom.projectedModifications = value.headroom.triggerAt; value.headroom.sufficient = true; },
		(value) => { delete value.headroom.effectiveScaleFactor; },
	]) { const value = structuredClone(evidence); mutate(value); assert.throws(() => validateMaintenanceReadinessEvidence(value, { ...CASE, mode }, CONTRACT), /headroom|maintenance/i); }
});

test('readiness collector failures remain sanitized without SQL, stderr, paths, or credentials', async () => {
	const { captureMaintenanceReadinessObservation } = await import('./wait-maintenance-readiness.mjs');
	const target = { containers: { postgres: { id: 'a'.repeat(64) } }, database: { user: 'faithlog', name: 'faithlog' } };
	for (const fake of [
		() => ({ status: 42, stderr: 'raw-secret SQL /local/path', stdout: '' }),
		() => ({ status: 0, stderr: '', stdout: '' }),
		() => ({ status: 0, stderr: '', stdout: '{raw-secret' }),
	]) {
		let message = '';
		try { captureMaintenanceReadinessObservation(target, fake); } catch (error) { message = error.message; }
		assert.match(message, /^maintenance-readiness-collector-(?:failed|empty|invalid)$/);
		assert.doesNotMatch(message, /secret|SQL|local|path/i);
	}
});

async function contractModule() { return import('./maintenance-quiet-contract.mjs'); }
function observation(second, options = {}) {
	const { activeWorkers = 0, autoanalyzeCount = '4', lastAutoanalyze = null } = options;
	return {
		capturedAt: iso(second), activeAutovacuumWorkers: activeWorkers,
		tables: Object.fromEntries(TABLES.map((name) => [name, {
			lastAnalyze: null, lastAutoanalyze: name === 'charge_items' ? lastAutoanalyze : null, lastVacuum: null, lastAutovacuum: null,
			analyzeCount: '1', autoanalyzeCount: name === 'charge_items' ? autoanalyzeCount : '0', vacuumCount: '0', autovacuumCount: '0',
		}])),
		chargeItems: headroomRaw(options),
	};
}
function headroomRaw({ nMod = '0', reltuples = '100000', globalBase = '50', globalScale = '0.1', relBase = null, relScale = null } = {}) {
	return {
		nModSinceAnalyze: nMod, reltuples, globalAutovacuumEnabled: true, relationAutovacuumEnabled: null,
		globalBaseThreshold: globalBase, globalScaleFactor: globalScale,
		relationBaseThreshold: relBase, relationScaleFactor: relScale,
	};
}
function iso(second) { return new Date(Date.parse('2026-07-16T00:00:00.000Z') + second * 1000).toISOString(); }
