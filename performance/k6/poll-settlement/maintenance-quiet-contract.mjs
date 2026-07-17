import { TABLE_NAMES } from './evidence-contract.mjs';

const CONTRACT_KEYS = Object.freeze(['pollIntervalSeconds', 'quietSeconds', 'timeoutSeconds']);
const OBSERVATION_KEYS = Object.freeze(['capturedAt', 'activeAutovacuumWorkers', 'tables']);
const TABLE_KEYS = Object.freeze([
	'lastAnalyze', 'lastAutoanalyze', 'lastVacuum', 'lastAutovacuum',
	'analyzeCount', 'autoanalyzeCount', 'vacuumCount', 'autovacuumCount',
]);
const EVIDENCE_KEYS = Object.freeze(['case', 'contract', 'startedAt', 'quietStartedAt', 'finishedAt', 'pollCount', 'resetCount', 'finalStatus', 'reason']);
const READINESS_CONTRACT_KEYS = Object.freeze([...CONTRACT_KEYS, 'expectedChargeWrites']);
const READINESS_OBSERVATION_KEYS = Object.freeze([...OBSERVATION_KEYS, 'chargeItems']);
const CHARGE_ITEMS_KEYS = Object.freeze(['nModSinceAnalyze', 'reltuples', 'globalAutovacuumEnabled', 'relationAutovacuumEnabled', 'globalBaseThreshold', 'globalScaleFactor', 'relationBaseThreshold', 'relationScaleFactor']);
const HEADROOM_KEYS = Object.freeze(['nModSinceAnalyze', 'reltuples', 'effectiveBaseThreshold', 'effectiveScaleFactor', 'triggerAt', 'expectedWrites', 'projectedModifications', 'sufficient']);
const READINESS_EVIDENCE_KEYS = Object.freeze(['case', 'contract', 'startedAt', 'quietStartedAt', 'finishedAt', 'pollCount', 'resetCount', 'headroom', 'finalStatus', 'reason']);
const EXPECTED_CHARGE_WRITES = Object.freeze({ 'coffee-sequential': 11000, 'meal-sequential': 11000, 'coffee-concurrent': 6000, 'meal-concurrent': 6000 });
const APPROVED_CONTRACT = Object.freeze({ pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180 });
const DECIMAL = /^(0|[1-9]\d*)$/;
const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3,6})Z$/;

export function validateMaintenanceQuietContract(contract) {
	assertExactKeys(contract, CONTRACT_KEYS, 'maintenance quiet contract');
	for (const key of CONTRACT_KEYS) {
		if (!Number.isSafeInteger(contract[key]) || contract[key] !== APPROVED_CONTRACT[key]) throw new Error('maintenance quiet contract mismatch');
	}
	return contract;
}

export function validateMaintenanceReadinessContract(contract) {
	assertExactKeys(contract, READINESS_CONTRACT_KEYS, 'maintenance readiness contract');
	validateMaintenanceQuietContract(Object.fromEntries(CONTRACT_KEYS.map((key) => [key, contract[key]])));
	assertExactKeys(contract.expectedChargeWrites, Object.keys(EXPECTED_CHARGE_WRITES), 'maintenance readiness expected writes');
	for (const [mode, expected] of Object.entries(EXPECTED_CHARGE_WRITES)) {
		if (!Number.isSafeInteger(contract.expectedChargeWrites[mode]) || contract.expectedChargeWrites[mode] !== expected) throw new Error('maintenance readiness expected writes mismatch');
	}
	return contract;
}

export function computeChargeItemsHeadroom(raw, expectedWrites) {
	assertExactKeys(raw, CHARGE_ITEMS_KEYS, 'headroom observation');
	if (raw.globalAutovacuumEnabled !== true || ![null, true].includes(raw.relationAutovacuumEnabled)) throw new Error('headroom autoanalyze disabled');
	const nMod = unsignedInteger(raw.nModSinceAnalyze, 'headroom n_mod_since_analyze');
	const tuples = unsignedRational(raw.reltuples, 'headroom reltuples');
	const globalBase = unsignedInteger(raw.globalBaseThreshold, 'headroom global threshold');
	const globalScale = unsignedRational(raw.globalScaleFactor, 'headroom global scale');
	const relationBase = raw.relationBaseThreshold === null ? null : unsignedInteger(raw.relationBaseThreshold, 'headroom relation threshold');
	const relationScale = raw.relationScaleFactor === null ? null : unsignedRational(raw.relationScaleFactor, 'headroom relation scale');
	const expected = unsignedInteger(expectedWrites, 'headroom expected writes');
	const base = relationBase ?? globalBase;
	const scale = relationScale ?? globalScale;
	const scaledNumerator = scale.numerator * tuples.numerator;
	const scaledDenominator = scale.denominator * tuples.denominator;
	const triggerAt = base + scaledNumerator / scaledDenominator + 1n;
	const projected = nMod + expected;
	return {
		nModSinceAnalyze: String(nMod), reltuples: rationalDecimal(tuples), effectiveBaseThreshold: String(base),
		effectiveScaleFactor: rationalIdentity(scale), triggerAt: String(triggerAt), expectedWrites: String(expected),
		projectedModifications: String(projected), sufficient: projected < triggerAt,
	};
}

export function evaluateMaintenanceReadinessObservations(observations, contract, mode) {
	validateMaintenanceReadinessContract(contract);
	const expectedWrites = expectedWritesFor(contract, mode);
	if (!Array.isArray(observations) || observations.length === 0) throw new Error('maintenance readiness observations required');
	let quietStartedAt = null;
	let previousTables = null;
	let previousMs = null;
	let resetCount = 0;
	let headroom = null;
	const startedAt = validateMaintenanceReadinessObservation(observations[0]).capturedAt;
	const startedMs = Date.parse(startedAt);
	for (let index = 0; index < observations.length; index += 1) {
		const observation = validateMaintenanceReadinessObservation(observations[index]);
		const capturedMs = Date.parse(observation.capturedAt);
		if (previousMs !== null && capturedMs <= previousMs) throw new Error('maintenance readiness timestamps must be strictly increasing');
		if (previousMs !== null && capturedMs - previousMs < contract.pollIntervalSeconds * 1000) throw new Error('maintenance readiness poll interval is too short');
		headroom = computeChargeItemsHeadroom(observation.chargeItems, String(expectedWrites));
		const maintenanceDrift = previousTables !== null && stable(previousTables) !== stable(observation.tables);
		const activeWorker = observation.activeAutovacuumWorkers > 0;
		const blocked = activeWorker || !headroom.sufficient;
		if (blocked || maintenanceDrift) {
			resetCount += 1;
			quietStartedAt = !blocked && maintenanceDrift ? capturedMs : null;
		} else if (quietStartedAt === null) quietStartedAt = capturedMs;
		const elapsedMs = capturedMs - startedMs;
		const quietMs = quietStartedAt === null ? 0 : capturedMs - quietStartedAt;
		if (elapsedMs > contract.timeoutSeconds * 1000) return readinessResult(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, headroom, 'rejected', 'maintenance-readiness-timeout');
		if (quietStartedAt !== null && quietMs >= contract.quietSeconds * 1000) return readinessResult(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, headroom, 'passed', 'maintenance-readiness-achieved');
		if (elapsedMs >= contract.timeoutSeconds * 1000) return readinessResult(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, headroom, 'rejected', 'maintenance-readiness-timeout');
		previousTables = observation.tables;
		previousMs = capturedMs;
	}
	return readinessResult(contract, startedAt, quietStartedAt, observations.at(-1).capturedAt, observations.length, resetCount, headroom, 'pending', 'maintenance-readiness-pending');
}

export function validateMaintenanceReadinessEvidence(evidence, expectedCase, contract) {
	assertExactKeys(evidence, READINESS_EVIDENCE_KEYS, 'maintenance readiness evidence');
	assertExactObject(evidence.case, expectedCase, 'maintenance readiness case');
	validateMaintenanceReadinessContract(contract);
	validateMaintenanceReadinessContract(evidence.contract);
	if (stable(evidence.contract) !== stable(contract)) throw new Error('maintenance readiness contract binding mismatch');
	const expectedWrites = String(expectedWritesFor(contract, expectedCase.mode));
	validatePassedWindow(evidence, contract, 'maintenance readiness');
	validateFinalHeadroom(evidence.headroom, expectedWrites);
	if (evidence.finalStatus !== 'passed' || evidence.reason !== 'maintenance-readiness-achieved') throw new Error('maintenance readiness rejected');
	return evidence;
}

export function validateMaintenanceObservation(observation) {
	assertExactKeys(observation, OBSERVATION_KEYS, 'maintenance observation');
	canonicalIso(observation.capturedAt, 'maintenance capturedAt');
	if (!Number.isSafeInteger(observation.activeAutovacuumWorkers) || observation.activeAutovacuumWorkers < 0) throw new Error('maintenance active worker schema');
	assertExactKeys(observation.tables, TABLE_NAMES, 'maintenance table set');
	for (const name of TABLE_NAMES) {
		const table = observation.tables[name];
		assertExactKeys(table, TABLE_KEYS, `maintenance table ${name}`);
		for (const key of TABLE_KEYS.slice(0, 4)) nullableIso(table[key], `maintenance ${name} ${key}`);
		for (const key of TABLE_KEYS.slice(4)) decimal(table[key], `maintenance ${name} ${key}`);
	}
	return observation;
}

function validateMaintenanceReadinessObservation(observation) {
	assertExactKeys(observation, READINESS_OBSERVATION_KEYS, 'maintenance readiness observation');
	validateMaintenanceObservation(Object.fromEntries(OBSERVATION_KEYS.map((key) => [key, observation[key]])));
	computeChargeItemsHeadroom(observation.chargeItems, '0');
	return observation;
}

export function evaluateMaintenanceQuietObservations(observations, contract) {
	validateMaintenanceQuietContract(contract);
	if (!Array.isArray(observations) || observations.length === 0) throw new Error('maintenance observations required');
	let quietStartedAt = null;
	let previousTables = null;
	let previousMs = null;
	let resetCount = 0;
	const startedAt = validateMaintenanceObservation(observations[0]).capturedAt;
	const startedMs = Date.parse(startedAt);
	for (let index = 0; index < observations.length; index += 1) {
		const observation = validateMaintenanceObservation(observations[index]);
		const capturedMs = Date.parse(observation.capturedAt);
		if (previousMs !== null && capturedMs <= previousMs) throw new Error('maintenance timestamps must be strictly increasing');
		if (previousMs !== null && capturedMs - previousMs < contract.pollIntervalSeconds * 1000) throw new Error('maintenance poll interval is too short');
		const maintenanceDrift = previousTables !== null && stable(previousTables) !== stable(observation.tables);
		const activeWorker = observation.activeAutovacuumWorkers > 0;
		if (activeWorker || maintenanceDrift) {
			resetCount += 1;
			quietStartedAt = activeWorker ? null : capturedMs;
		} else if (quietStartedAt === null) {
			quietStartedAt = capturedMs;
		}
		const elapsedMs = capturedMs - startedMs;
		const quietMs = quietStartedAt === null ? 0 : capturedMs - quietStartedAt;
		if (elapsedMs > contract.timeoutSeconds * 1000) {
			return result(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, 'rejected', 'maintenance-quiet-timeout');
		}
		if (quietStartedAt !== null && quietMs >= contract.quietSeconds * 1000) {
			return result(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, 'passed', 'maintenance-quiet-achieved');
		}
		if (elapsedMs >= contract.timeoutSeconds * 1000) {
			return result(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, 'rejected', 'maintenance-quiet-timeout');
		}
		previousTables = observation.tables;
		previousMs = capturedMs;
	}
	return result(contract, startedAt, quietStartedAt, observations.at(-1).capturedAt, observations.length, resetCount, 'pending', 'maintenance-quiet-pending');
}

export function validateMaintenanceStabilityEvidence(evidence, expectedCase, contract) {
	assertExactKeys(evidence, EVIDENCE_KEYS, 'maintenance stability evidence');
	assertExactObject(evidence.case, expectedCase, 'maintenance case');
	validateMaintenanceQuietContract(contract);
	validateMaintenanceQuietContract(evidence.contract);
	if (stable(evidence.contract) !== stable(contract)) throw new Error('maintenance contract binding mismatch');
	canonicalIso(evidence.startedAt, 'maintenance startedAt');
	canonicalIso(evidence.quietStartedAt, 'maintenance quietStartedAt');
	canonicalIso(evidence.finishedAt, 'maintenance finishedAt');
	const elapsed = Date.parse(evidence.finishedAt) - Date.parse(evidence.startedAt);
	if (elapsed < contract.quietSeconds * 1000 || elapsed > contract.timeoutSeconds * 1000) throw new Error('maintenance evidence duration mismatch');
	const quietOffset = Date.parse(evidence.quietStartedAt) - Date.parse(evidence.startedAt);
	const quietElapsed = Date.parse(evidence.finishedAt) - Date.parse(evidence.quietStartedAt);
	if (quietOffset < 0 || quietElapsed < contract.quietSeconds * 1000) throw new Error('maintenance quiet interval mismatch');
	if (!Number.isSafeInteger(evidence.pollCount) || evidence.pollCount < Math.ceil(contract.quietSeconds / contract.pollIntervalSeconds) + 1) throw new Error('maintenance poll count schema');
	if (!Number.isSafeInteger(evidence.resetCount) || evidence.resetCount < 0 || evidence.resetCount >= evidence.pollCount) throw new Error('maintenance reset count schema');
	if ((evidence.resetCount === 0 && evidence.quietStartedAt !== evidence.startedAt) || (evidence.resetCount > 0 && quietOffset <= 0)) throw new Error('maintenance quiet start binding mismatch');
	if (evidence.finalStatus !== 'passed' || evidence.reason !== 'maintenance-quiet-achieved') throw new Error('maintenance stability rejected');
	return evidence;
}

function validatePassedWindow(evidence, contract, label) {
	canonicalIso(evidence.startedAt, `${label} startedAt`);
	canonicalIso(evidence.quietStartedAt, `${label} quietStartedAt`);
	canonicalIso(evidence.finishedAt, `${label} finishedAt`);
	const elapsed = Date.parse(evidence.finishedAt) - Date.parse(evidence.startedAt);
	if (elapsed < contract.quietSeconds * 1000 || elapsed > contract.timeoutSeconds * 1000) throw new Error(`${label} duration mismatch`);
	const quietOffset = Date.parse(evidence.quietStartedAt) - Date.parse(evidence.startedAt);
	const quietElapsed = Date.parse(evidence.finishedAt) - Date.parse(evidence.quietStartedAt);
	if (quietOffset < 0 || quietElapsed < contract.quietSeconds * 1000) throw new Error(`${label} quiet interval mismatch`);
	if (!Number.isSafeInteger(evidence.pollCount) || evidence.pollCount < Math.ceil(contract.quietSeconds / contract.pollIntervalSeconds) + 1) throw new Error(`${label} poll count schema`);
	if (!Number.isSafeInteger(evidence.resetCount) || evidence.resetCount < 0 || evidence.resetCount >= evidence.pollCount) throw new Error(`${label} reset count schema`);
	if ((evidence.resetCount === 0 && evidence.quietStartedAt !== evidence.startedAt) || (evidence.resetCount > 0 && quietOffset <= 0)) throw new Error(`${label} quiet start binding mismatch`);
}

function validateFinalHeadroom(headroom, expectedWrites) {
	assertExactKeys(headroom, HEADROOM_KEYS, 'headroom identity');
	const nMod = unsignedInteger(headroom.nModSinceAnalyze, 'headroom n_mod_since_analyze');
	const tuples = unsignedRational(headroom.reltuples, 'headroom reltuples');
	if (rationalDecimal(tuples) !== headroom.reltuples) throw new Error('headroom reltuples canonical');
	const base = unsignedInteger(headroom.effectiveBaseThreshold, 'headroom base threshold');
	assertExactKeys(headroom.effectiveScaleFactor, ['numerator', 'denominator'], 'headroom scale factor');
	const scale = normalizedRational(headroom.effectiveScaleFactor.numerator, headroom.effectiveScaleFactor.denominator, 'headroom scale factor');
	if (stable(rationalIdentity(scale)) !== stable(headroom.effectiveScaleFactor)) throw new Error('headroom scale factor canonical');
	const triggerAt = base + (scale.numerator * tuples.numerator) / (scale.denominator * tuples.denominator) + 1n;
	const expected = unsignedInteger(headroom.expectedWrites, 'headroom expected writes');
	const projected = nMod + expected;
	if (headroom.expectedWrites !== expectedWrites || headroom.triggerAt !== String(triggerAt) || headroom.projectedModifications !== String(projected) || headroom.sufficient !== (projected < triggerAt) || headroom.sufficient !== true) throw new Error('headroom identity mismatch');
}

function result(contract, startedAt, quietStartedMs, finishedAt, pollCount, resetCount, finalStatus, reason) {
	return { contract: { ...contract }, startedAt, quietStartedAt: quietStartedMs === null ? null : new Date(quietStartedMs).toISOString(), finishedAt, pollCount, resetCount, finalStatus, reason };
}
function readinessResult(contract, startedAt, quietStartedMs, finishedAt, pollCount, resetCount, headroom, finalStatus, reason) {
	return { contract: structuredClone(contract), startedAt, quietStartedAt: quietStartedMs === null ? null : new Date(quietStartedMs).toISOString(), finishedAt, pollCount, resetCount, headroom, finalStatus, reason };
}
function expectedWritesFor(contract, mode) { if (!Object.hasOwn(contract.expectedChargeWrites, mode)) throw new Error('maintenance readiness mode mismatch'); return contract.expectedChargeWrites[mode]; }
function unsignedInteger(value, label) { if (typeof value !== 'string' || !DECIMAL.test(value)) throw new Error(`${label} headroom schema`); return BigInt(value); }
function unsignedRational(value, label) {
	if (typeof value !== 'string' || !/^(0|[1-9]\d*)(?:\.\d+)?$/.test(value)) throw new Error(`${label} headroom schema`);
	const [whole, fraction = ''] = value.split('.');
	return normalizeBigInts(BigInt(`${whole}${fraction}`), 10n ** BigInt(fraction.length), label);
}
function normalizedRational(numeratorValue, denominatorValue, label) {
	const numerator = unsignedInteger(numeratorValue, label); const denominator = unsignedInteger(denominatorValue, label);
	return normalizeBigInts(numerator, denominator, label);
}
function normalizeBigInts(numerator, denominator, label) {
	if (denominator === 0n) throw new Error(`${label} headroom schema`);
	const divisor = gcd(numerator, denominator);
	return { numerator: numerator / divisor, denominator: denominator / divisor };
}
function rationalIdentity(value) { return { numerator: String(value.numerator), denominator: String(value.denominator) }; }
function rationalDecimal(value) {
	let denominator = value.denominator; let twos = 0; let fives = 0;
	while (denominator % 2n === 0n) { denominator /= 2n; twos += 1; }
	while (denominator % 5n === 0n) { denominator /= 5n; fives += 1; }
	if (denominator !== 1n) throw new Error('headroom decimal is not finite');
	const places = Math.max(twos, fives); const scaled = value.numerator * 2n ** BigInt(places - twos) * 5n ** BigInt(places - fives);
	if (places === 0) return String(scaled);
	const text = String(scaled).padStart(places + 1, '0');
	return `${text.slice(0, -places)}.${text.slice(-places)}`.replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1');
}
function gcd(left, right) { while (right !== 0n) [left, right] = [right, left % right]; return left; }
function assertExactObject(value, expected, label) {
	assertExactKeys(value, Object.keys(expected), label);
	for (const [key, item] of Object.entries(expected)) if (value[key] !== item) throw new Error(`${label} mismatch`);
}
function assertExactKeys(value, expected, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${label} schema`);
	if (stable(Object.keys(value).sort()) !== stable([...expected].sort())) throw new Error(`${label} schema`);
}
function canonicalIso(value, label) {
	if (typeof value !== 'string' || !ISO.test(value) || !Number.isFinite(Date.parse(value))) throw new Error(`${label} schema`);
	return value;
}
function nullableIso(value, label) { if (value !== null) canonicalIso(value, label); }
function decimal(value, label) { if (typeof value !== 'string' || !DECIMAL.test(value)) throw new Error(`${label} schema`); return BigInt(value); }
function stable(value) { return JSON.stringify(value); }
