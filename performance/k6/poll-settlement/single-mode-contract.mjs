import { validateMaintenanceReadinessContract } from './maintenance-quiet-contract.mjs';
import { validateRuntimeTarget } from './evidence-contract.mjs';

export const SETTLEMENT_MODES = Object.freeze(['coffee-sequential', 'meal-sequential', 'coffee-concurrent', 'meal-concurrent']);
export const SINGLE_MODE_PROTOCOL_VERSION = 'faithlog-192-single-mode-v1';
const OUTCOME_KEYS = Object.freeze(['activeMembers', 'polls', 'pollResponses', 'pollResponseOptions', 'selectedPolls', 'coffeeClosed', 'coffeeOpen', 'mealClosed', 'mealOpen', 'coffeeCharges', 'mealCharges', 'mealSettlements', 'mealGroups', 'nonselectedChargeCount', 'nonselectedPollStateDrift', 'missingExpectedIdentity', 'unexpectedIdentity', 'terminalStatusCharges', 'notificationSideEffects', 'sourceUniqueDuplicates', 'groupTotalViolations']);
const TARGET_KEYS = Object.freeze(['contractType', 'sourceCommit', 'baseUrl', 'flywayVersion', 'containers', 'database', 'redis', 'resourceSampling', 'maintenanceReadiness']);

export function validateTargetContract(target) {
	assertExactKeys(target, TARGET_KEYS, 'target contract');
	if (!['before', 'after'].includes(target.contractType)) throw new Error('target contract type');
	if (typeof target.sourceCommit !== 'string' || !/^[a-f0-9]{40}$/.test(target.sourceCommit)) throw new Error('target source commit');
	if (target.baseUrl !== 'http://127.0.0.1:28080') throw new Error('target base URL');
	if (typeof target.flywayVersion !== 'string' || !/^[1-9]\d*$/.test(target.flywayVersion)) throw new Error('target Flyway version');
	validateRuntimeTarget(target);
	validateMaintenanceReadinessContract(target.maintenanceReadiness);
	return target;
}

export function requireExactMode(value) {
	if (!SETTLEMENT_MODES.includes(value)) throw new Error('MODE must be one exact approved settlement mode.');
	return value;
}

export function expectedWritesForSelectedMode(target, mode) {
	requireExactMode(mode);
	validateMaintenanceReadinessContract(target?.maintenanceReadiness);
	return target.maintenanceReadiness.expectedChargeWrites[mode];
}

export function workloadContract(target) {
	validateMaintenanceReadinessContract(target?.maintenanceReadiness);
	return {
		protocolVersion: SINGLE_MODE_PROTOCOL_VERSION,
		memberCount: 1000, polls: 34, responses: 34000, warmup: 1,
		sequentialMeasured: 10, concurrentVus: 5, concurrentMeasured: 5,
		expectedChargeWrites: structuredClone(target.maintenanceReadiness.expectedChargeWrites),
	};
}

export function targetRoleIdentity(target) {
	return {
		baseUrl: target.baseUrl,
		composeProject: target.containers.app.composeProject,
		appService: target.containers.app.composeService,
		postgresService: target.containers.postgres.composeService,
		redisService: target.containers.redis.composeService,
		appPublishedPorts: target.containers.app.publishedPorts,
		postgresPublishedPorts: target.containers.postgres.publishedPorts,
		redisPublishedPorts: target.containers.redis.publishedPorts,
		databaseName: target.database.name,
		databaseUser: target.database.user,
		redisPort: target.redis.serverPort,
		resourceSampling: target.resourceSampling,
	};
}

export function expectedSelectedCorrectness(mode) {
	requireExactMode(mode);
	const sequential = mode.endsWith('-sequential');
	const selectedPolls = sequential ? 11 : 6;
	const coffee = mode.startsWith('coffee-');
	return {
		activeMembers: 1000, polls: 34, pollResponses: 34000, pollResponseOptions: 34000, selectedPolls,
		coffeeClosed: coffee ? selectedPolls : 0, coffeeOpen: coffee ? 17 - selectedPolls : 17,
		mealClosed: 17, mealOpen: 0,
		coffeeCharges: coffee ? selectedPolls * 1000 : 0, mealCharges: coffee ? 0 : selectedPolls * 1000,
		mealSettlements: coffee ? 0 : selectedPolls, mealGroups: coffee ? 0 : selectedPolls * 4,
		nonselectedChargeCount: 0, nonselectedPollStateDrift: 0, missingExpectedIdentity: 0, unexpectedIdentity: 0,
		terminalStatusCharges: 0, notificationSideEffects: 0, sourceUniqueDuplicates: 0, groupTotalViolations: 0,
	};
}

export function validateSelectedCorrectness(actual, mode) {
	const expected = expectedSelectedCorrectness(mode);
	assertExactKeys(actual, OUTCOME_KEYS, 'selected correctness');
	for (const key of OUTCOME_KEYS) if (!Number.isSafeInteger(actual[key]) || actual[key] !== expected[key]) throw new Error(`selected correctness mismatch: ${key}`);
	return actual;
}

function assertExactKeys(value, keys, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value) || JSON.stringify(Object.keys(value).sort()) !== JSON.stringify([...keys].sort())) throw new Error(`${label} schema`);
}
