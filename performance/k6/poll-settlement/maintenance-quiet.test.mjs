import assert from 'node:assert/strict';
import { test } from 'node:test';

const CONTRACT = Object.freeze({ pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180 });
const CASE = Object.freeze({ datasetId: 'PERFORMANCE_192_QUIET', fixtureRunId: 'QUIET_192', executionRunId: 'EXEC192_QUIET' });
const TABLES = ['users', 'campus_members', 'campus_duty_assignments', 'polls', 'poll_options', 'poll_responses', 'poll_response_options', 'payment_accounts', 'charge_items', 'meal_poll_settlements', 'meal_poll_charge_groups', 'notification_logs'];

test('maintenance gate passes only after the exact 30-second stable boundary and not at 29 seconds', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	const stable30 = Array.from({ length: 7 }, (_, index) => observation(index * 5));
	const passed = evaluateMaintenanceQuietObservations(stable30, CONTRACT);
	assert.equal(passed.finalStatus, 'passed'); assert.equal(passed.pollCount, 7); assert.equal(passed.resetCount, 0);
	assert.equal(Date.parse(passed.finishedAt) - Date.parse(passed.startedAt), 30_000);
	const stable29 = [0, 5, 11, 17, 23, 29].map((second) => observation(second));
	assert.equal(evaluateMaintenanceQuietObservations(stable29, CONTRACT).finalStatus, 'pending');
});

test('active autovacuum worker resets the quiet clock before a later 30-second pass', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	const seconds = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50];
	const result = evaluateMaintenanceQuietObservations(seconds.map((second) => observation(second, { activeWorkers: second === 15 ? 1 : 0 })), CONTRACT);
	assert.equal(result.finalStatus, 'passed'); assert.equal(result.resetCount, 1); assert.equal(result.finishedAt, iso(50));
});

test('any maintenance field drift resets the quiet clock before a later 30-second pass', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	const seconds = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45];
	const result = evaluateMaintenanceQuietObservations(seconds.map((second) => observation(second, { autovacuumCount: second >= 15 ? '2' : '1' })), CONTRACT);
	assert.equal(result.finalStatus, 'passed'); assert.equal(result.resetCount, 1); assert.equal(result.finishedAt, iso(45));
});

test('maintenance gate times out fail-closed at 180 seconds when quiet never reaches 30 seconds', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	const samples = Array.from({ length: 37 }, (_, index) => observation(index * 5, { activeWorkers: index % 2 }));
	const result = evaluateMaintenanceQuietObservations(samples, CONTRACT);
	assert.equal(result.finalStatus, 'rejected'); assert.equal(result.reason, 'maintenance-quiet-timeout'); assert.equal(result.pollCount, 37);
});

test('maintenance gate requires exact 5/30/180 inputs with no missing or implicit defaults', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	for (const value of [
		{},
		{ pollIntervalSeconds: 4, quietSeconds: 30, timeoutSeconds: 180 },
		{ pollIntervalSeconds: 5, quietSeconds: 29, timeoutSeconds: 180 },
		{ pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 179 },
		{ ...CONTRACT, extra: 1 },
	]) assert.throws(() => evaluateMaintenanceQuietObservations([observation(0)], value), /maintenance.*contract/i);
});

test('maintenance observations reject missing, null, malformed, and extra worker/table fields', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	const mutations = [
		(value) => { value.extra = true; },
		(value) => { value.activeAutovacuumWorkers = null; },
		(value) => { value.capturedAt = 'not-iso'; },
		(value) => { delete value.tables.users; },
		(value) => { value.tables.foreign_table = value.tables.users; },
		(value) => { delete value.tables.users.lastAutovacuum; },
		(value) => { value.tables.users.autovacuumCount = null; },
		(value) => { value.tables.users.autovacuumCount = '01'; },
		(value) => { value.tables.users.extra = 'secret'; },
	];
	for (const mutate of mutations) {
		const value = observation(0); mutate(value);
		assert.throws(() => evaluateMaintenanceQuietObservations([value], CONTRACT), /maintenance/i);
	}
});

test('collector non-zero, empty, and malformed output fail with sanitized errors only', async () => {
	const { captureMaintenanceObservation } = await collectorModule();
	const target = { containers: { postgres: { id: 'a'.repeat(64) } }, database: { user: 'faithlog', name: 'faithlog' } };
	for (const fake of [
		() => ({ status: 42, stderr: 'raw-secret-password SQL /local/private/path', stdout: '' }),
		() => ({ status: 0, stderr: '', stdout: '' }),
		() => ({ status: 0, stderr: '', stdout: '{raw-secret-password' }),
	]) {
		let message = '';
		try { captureMaintenanceObservation(target, fake); } catch (error) { message = error.message; }
		assert.match(message, /^maintenance-collector-(?:failed|empty|invalid)$/);
		assert.doesNotMatch(message, /secret|SQL|local|path/i);
	}
});

test('passed evidence rejects 29 seconds after the last reset and accepts the exact 30-second quiet boundary', async () => {
	const { validateMaintenanceStabilityEvidence } = await contractModule();
	const forgedWithoutQuietStart = stabilityEvidence({ resetCount: 6 });
	delete forgedWithoutQuietStart.quietStartedAt;
	assert.throws(() => validateMaintenanceStabilityEvidence(forgedWithoutQuietStart, CASE, CONTRACT), /maintenance/i);
	assert.throws(() => validateMaintenanceStabilityEvidence(stabilityEvidence({ quietStartedAt: iso(1), resetCount: 1 }), CASE, CONTRACT), /maintenance/i);
	assert.doesNotThrow(() => validateMaintenanceStabilityEvidence(stabilityEvidence({ quietStartedAt: iso(0), resetCount: 0 }), CASE, CONTRACT));
	assert.doesNotThrow(() => validateMaintenanceStabilityEvidence(stabilityEvidence({ startedAt: iso(0), quietStartedAt: iso(5), finishedAt: iso(35), resetCount: 1, pollCount: 8 }), CASE, CONTRACT));
});

test('passed evidence exact-binds the first quiet start and rejects invalid quiet bounds or timeout', async () => {
	const { validateMaintenanceStabilityEvidence } = await contractModule();
	for (const value of [
		stabilityEvidence({ quietStartedAt: iso(5), resetCount: 0 }),
		stabilityEvidence({ quietStartedAt: '2026-07-16T00:00:00.0000Z', resetCount: 0 }),
		stabilityEvidence({ quietStartedAt: iso(-1), resetCount: 1 }),
		stabilityEvidence({ quietStartedAt: iso(31), resetCount: 1 }),
		stabilityEvidence({ quietStartedAt: '2026-07-16 00:00:00Z', resetCount: 1 }),
		stabilityEvidence({ finishedAt: iso(181), quietStartedAt: iso(151), resetCount: 1, pollCount: 38 }),
	]) assert.throws(() => validateMaintenanceStabilityEvidence(value, CASE, CONTRACT), /maintenance/i);
});

test('synthetic maintenance observations cannot be more frequent than the approved five-second poll interval', async () => {
	const { evaluateMaintenanceQuietObservations } = await contractModule();
	assert.throws(() => evaluateMaintenanceQuietObservations([observation(0), observation(4.999)], CONTRACT), /maintenance.*interval/i);
	assert.equal(evaluateMaintenanceQuietObservations([observation(0), observation(5.001)], CONTRACT).finalStatus, 'pending');
});

async function contractModule() { return import('./maintenance-quiet-contract.mjs'); }
async function collectorModule() { return import('./wait-maintenance-quiet.mjs'); }
function observation(second, { activeWorkers = 0, autovacuumCount = '1' } = {}) {
	return {
		capturedAt: iso(second), activeAutovacuumWorkers: activeWorkers,
		tables: Object.fromEntries(TABLES.map((name) => [name, {
			lastAnalyze: null, lastAutoanalyze: null, lastVacuum: null, lastAutovacuum: null,
			analyzeCount: '1', autoanalyzeCount: '0', vacuumCount: '0', autovacuumCount: name === 'users' ? autovacuumCount : '1',
		}])),
	};
}
function iso(second) { return new Date(Date.parse('2026-07-16T00:00:00.000Z') + second * 1000).toISOString(); }
function stabilityEvidence(overrides = {}) {
	return { case: CASE, contract: CONTRACT, startedAt: iso(0), quietStartedAt: iso(0), finishedAt: iso(30), pollCount: 7, resetCount: 0, finalStatus: 'passed', reason: 'maintenance-quiet-achieved', ...overrides };
}
