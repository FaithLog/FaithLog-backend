import assert from 'node:assert/strict';
import { test } from 'node:test';
import { compareModeBundles } from './compare-bundles.mjs';
import { SETTLEMENT_MODES, SINGLE_MODE_PROTOCOL_VERSION } from './single-mode-contract.mjs';

test('before and after bundles compare only with the same role and workload while preserving every mode', () => {
	const before = bundle('1', 'a', 100);
	const after = bundle('2', 'b', 60);

	const comparison = compareModeBundles(before, after);

	assert.equal(comparison.accepted, false);
	assert.equal(comparison.automaticAdoption, false);
	assert.equal(comparison.measurementStatus, 'conditional-boundary-only');
	assert.deepEqual(Object.keys(comparison.modes).sort(), [...SETTLEMENT_MODES].sort());
	assert.deepEqual(comparison.modes['coffee-sequential'], {
		before: before.modes['coffee-sequential'],
		after: after.modes['coffee-sequential'],
	});
	assert.equal('combinedLatency' in comparison, false);
});

test('bundle comparison rejects role, workload, mode-set, and conditional-status drift', () => {
	for (const mutate of [
		(value) => { value.comparisonIdentity.targetRoleIdentity.baseUrl = 'http://127.0.0.1:9999'; },
		(value) => { value.comparisonIdentity.workloadContract.memberCount = 999; },
		(value) => { delete value.modes['meal-concurrent']; },
		(value) => { value.accepted = true; },
		(value) => { value.automaticAdoption = true; },
		(value) => { value.evidenceIntegrity = 'rejected'; },
	]) {
		const after = bundle('2', 'b', 60);
		mutate(after);
		assert.throws(() => compareModeBundles(bundle('1', 'a', 100), after), /bundle comparison/i);
	}
});

test('bundle comparison requires distinct valid source and immutable target identities', () => {
	const before = bundle('1', 'a', 100);
	const sameSource = bundle('1', 'b', 60);
	const sameTarget = bundle('2', 'a', 60);

	assert.throws(() => compareModeBundles(before, sameSource), /bundle comparison/i);
	assert.throws(() => compareModeBundles(before, sameTarget), /bundle comparison/i);
	for (const mutate of [
		(value) => { value.comparisonIdentity.sourceCommit = 'not-a-commit'; },
		(value) => { value.comparisonIdentity.targetIdentitySha256 = 'not-a-sha'; },
	]) {
		const after = bundle('2', 'b', 60);
		mutate(after);
		assert.throws(() => compareModeBundles(before, after), /bundle comparison/i);
	}
});

function bundle(sourceDigit, targetDigit, latency) {
	const workloadContract = {
		protocolVersion: SINGLE_MODE_PROTOCOL_VERSION,
		memberCount: 1000,
		polls: 34,
		responses: 34000,
		warmup: 1,
		sequentialMeasured: 10,
		concurrentVus: 5,
		concurrentMeasured: 5,
		expectedChargeWrites: {
			'coffee-sequential': 11000,
			'meal-sequential': 11000,
			'coffee-concurrent': 6000,
			'meal-concurrent': 6000,
		},
	};
	const targetRoleIdentity = {
		baseUrl: 'http://127.0.0.1:28080',
		composeProject: 'faithlog-integration',
		appService: 'app',
		postgresService: 'postgres',
		redisService: 'redis',
		appPublishedPorts: ['0.0.0.0:28080->8080/tcp'],
		postgresPublishedPorts: ['0.0.0.0:25432->5432/tcp'],
		redisPublishedPorts: ['0.0.0.0:26379->6379/tcp'],
		databaseName: 'faithlog',
		databaseUser: 'faithlog',
		redisPort: 6379,
		resourceSampling: { minSamples: 2, samplingIntervalMs: 1000, maxGapMs: 3000 },
	};
	return {
		protocolVersion: SINGLE_MODE_PROTOCOL_VERSION,
		comparisonIdentity: {
			sourceCommit: sourceDigit.repeat(40),
			targetIdentitySha256: targetDigit.repeat(64),
			targetRoleIdentity,
			workloadContract,
		},
		accepted: false,
		automaticAdoption: false,
		evidenceIntegrity: 'validated',
		measurementStatus: 'conditional-boundary-only',
		modes: Object.fromEntries(SETTLEMENT_MODES.map((mode) => [mode, {
			measuredRequestCount: mode.endsWith('sequential') ? 10 : 5,
			latencyMs: { p50: latency, p95: latency + 10, p99: latency + 20, max: latency + 30 },
			throughputRps: 1,
			failureRate: 0,
		}])),
	};
}
