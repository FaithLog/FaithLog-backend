import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const MODES = ['coffee-sequential', 'meal-sequential', 'coffee-concurrent', 'meal-concurrent'];
const EXPECTED_WRITES = { 'coffee-sequential': 11000, 'meal-sequential': 11000, 'coffee-concurrent': 6000, 'meal-concurrent': 6000 };

test('MODE has no missing, alias, default, or all form', async () => {
	const { requireExactMode } = await import('./single-mode-contract.mjs');
	for (const mode of MODES) assert.equal(requireExactMode(mode), mode);
	for (const value of [undefined, '', 'all', 'default', 'coffee', 'COFFEE-SEQUENTIAL', 'coffee_sequential']) assert.throws(() => requireExactMode(value), /MODE/);
});

test('selected readiness exact-binds only the approved mode write count', async () => {
	const { expectedWritesForSelectedMode } = await import('./single-mode-contract.mjs');
	const target = { maintenanceReadiness: { pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180, expectedChargeWrites: EXPECTED_WRITES } };
	for (const mode of MODES) assert.equal(expectedWritesForSelectedMode(target, mode), EXPECTED_WRITES[mode]);
	for (const mutate of [
		(value) => { value.maintenanceReadiness.expectedChargeWrites['coffee-sequential'] = 10999; },
		(value) => { delete value.maintenanceReadiness.expectedChargeWrites['meal-sequential']; },
		(value) => { value.maintenanceReadiness.expectedChargeWrites.foreign = 1; },
	]) { const value = structuredClone(target); mutate(value); assert.throws(() => expectedWritesForSelectedMode(value, 'coffee-sequential'), /expected|readiness|mode/i); }
});

test('runtime target source, Flyway, services, images, resources, and workload fail closed before mutable work', async () => {
	const { validateTargetContract } = await import('./single-mode-contract.mjs');
	assert.equal(typeof validateTargetContract, 'function');
	const target = JSON.parse(readFileSync(new URL('target-contract.json', import.meta.url), 'utf8'));
	assert.equal(validateTargetContract(target), target);
	for (const mutate of [
		(value) => { value.sourceCommit = 'not-a-commit'; },
		(value) => { value.flywayVersion = 11; },
		(value) => { value.containers.app.imageId = 'latest'; },
		(value) => { value.containers.postgres.composeService = ''; },
		(value) => { delete value.containers.redis.configHash; },
		(value) => { value.resourceSampling.maxGapMs = 999; },
		(value) => { value.maintenanceReadiness.expectedChargeWrites['meal-concurrent'] = 5999; },
		(value) => { value.unapprovedFallback = true; },
	]) {
		const changed = structuredClone(target); mutate(changed);
		assert.throws(() => validateTargetContract(changed), /target|source|flyway|runtime|resource|readiness|schema/i);
	}
	const runner = readFileSync(new URL('run-baseline.sh', import.meta.url), 'utf8');
	const targetGate = runner.indexOf('validateTargetContract');
	const firstRuntime = runner.indexOf('INITIAL_TEMP=');
	const seed = runner.indexOf('seed-fixtures.mjs');
	assert.ok(targetGate > 0 && targetGate < firstRuntime && firstRuntime < seed, 'full target gate must precede runtime and fixture work');
});

test('mode-specific correctness requires selected final cardinality and nonselected untouched state', async () => {
	const { validateSelectedCorrectness } = await import('./single-mode-contract.mjs');
	const expected = {
		'coffee-sequential': outcome({ selectedPolls: 11, coffeeClosed: 11, coffeeOpen: 6, coffeeCharges: 11000 }),
		'meal-sequential': outcome({ selectedPolls: 11, coffeeClosed: 0, coffeeOpen: 17, mealCharges: 11000, mealSettlements: 11, mealGroups: 44 }),
		'coffee-concurrent': outcome({ selectedPolls: 6, coffeeClosed: 6, coffeeOpen: 11, coffeeCharges: 6000 }),
		'meal-concurrent': outcome({ selectedPolls: 6, coffeeClosed: 0, coffeeOpen: 17, mealCharges: 6000, mealSettlements: 6, mealGroups: 24 }),
	};
	for (const mode of MODES) {
		assert.deepEqual(validateSelectedCorrectness(expected[mode], mode), expected[mode]);
		for (const mutate of [
			(value) => { value.selectedPolls += 1; },
			(value) => { value.nonselectedChargeCount = 1; },
			(value) => { value.nonselectedPollStateDrift = 1; },
		]) { const value = structuredClone(expected[mode]); mutate(value); assert.throws(() => validateSelectedCorrectness(value, mode), /correctness|selected|untouched/i); }
	}
});

test('per-mode summarizer requires only selected artifacts and rejects foreign mode artifacts', () => {
	const source = readFileSync(new URL('summarize-results.mjs', import.meta.url), 'utf8');
	assert.match(source, /requireExactMode\(required\('MODE'\)\)/);
	assert.match(source, /assertNoForeignModeArtifacts/);
	assert.doesNotMatch(source, /for \(const mode of modes\)/);
});

function outcome(overrides) { return { activeMembers: 1000, polls: 34, pollResponses: 34000, pollResponseOptions: 34000, selectedPolls: 0, coffeeClosed: 0, coffeeOpen: 17, mealClosed: 17, mealOpen: 0, coffeeCharges: 0, mealCharges: 0, mealSettlements: 0, mealGroups: 0, nonselectedChargeCount: 0, nonselectedPollStateDrift: 0, missingExpectedIdentity: 0, unexpectedIdentity: 0, terminalStatusCharges: 0, notificationSideEffects: 0, sourceUniqueDuplicates: 0, groupTotalViolations: 0, ...overrides }; }
