import { readFileSync } from 'node:fs';

const EXPECTED_TABLES = [
	'campus_duty_assignments', 'campus_members', 'campuses', 'charge_items', 'coffee_brands',
	'coffee_menu_catalog', 'devotion_daily_checks', 'meal_poll_charge_groups', 'meal_poll_settlements',
	'notification_logs', 'payment_accounts', 'penalty_rules', 'poll_comments', 'poll_options',
	'poll_response_options', 'poll_responses', 'poll_template_options', 'poll_templates', 'polls',
	'prayer_group_members', 'prayer_groups', 'prayer_seasons', 'prayer_submissions', 'prayer_weeks',
	'user_fcm_tokens', 'users', 'weekly_devotion_records',
];
const ROOT_KEYS = ['activeAutovacuumWorkers', 'capturedAt', 'tables'];
const TABLE_KEYS = [
	'analyzeCount', 'autoanalyzeCount', 'autovacuumCount', 'lastAnalyze', 'lastAutoanalyze',
	'lastAutovacuum', 'lastVacuum', 'nTupDel', 'nTupIns', 'nTupUpd', 'vacuumCount',
];
const DECIMAL = /^(0|[1-9][0-9]*)$/;

try {
	const [path, intervalText, quietText, timeoutText] = process.argv.slice(2);
	const contract = validateContract({
		pollIntervalSeconds: number(intervalText),
		quietSeconds: number(quietText),
		timeoutSeconds: number(timeoutText),
	});
	const lines = readFileSync(path, 'utf8').split(/\r?\n/).filter(Boolean);
	if (lines.length === 0) throw new Error('maintenance observations required');
	const state = evaluate(lines.map((line) => JSON.parse(line)), contract);
	process.stdout.write(`${JSON.stringify(state)}\n`);
	process.exit(state.finalStatus === 'passed' ? 0 : state.finalStatus === 'pending' ? 2 : 1);
} catch {
	console.error('Invalid database quiescence evidence.');
	process.exit(1);
}

function evaluate(observations, contract) {
	let quietStartedAt = null;
	let previousTables = null;
	let previousMs = null;
	let resetCount = 0;
	const startedAt = validateObservation(observations[0]).capturedAt;
	const startedMs = Date.parse(startedAt);
	for (let index = 0; index < observations.length; index += 1) {
		const observation = validateObservation(observations[index]);
		const capturedMs = Date.parse(observation.capturedAt);
		if (previousMs !== null && capturedMs <= previousMs) throw new Error('maintenance timestamps must be strictly increasing');
		if (previousMs !== null && capturedMs - previousMs < contract.pollIntervalSeconds * 1000) {
			throw new Error('maintenance poll interval is too short');
		}
		const stateDrift = previousTables !== null && JSON.stringify(previousTables) !== JSON.stringify(observation.tables);
		const activeWorker = observation.activeAutovacuumWorkers > 0;
		if (activeWorker || stateDrift) {
			resetCount += 1;
			quietStartedAt = activeWorker ? null : capturedMs;
		} else if (quietStartedAt === null) quietStartedAt = capturedMs;
		const elapsedMs = capturedMs - startedMs;
		const quietMs = quietStartedAt === null ? 0 : capturedMs - quietStartedAt;
		if (elapsedMs > contract.timeoutSeconds * 1000) return result(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, 'rejected', 'maintenance-quiet-timeout');
		if (quietStartedAt !== null && quietMs >= contract.quietSeconds * 1000) return result(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, 'passed', 'maintenance-quiet-achieved');
		if (elapsedMs >= contract.timeoutSeconds * 1000) return result(contract, startedAt, quietStartedAt, observation.capturedAt, index + 1, resetCount, 'rejected', 'maintenance-quiet-timeout');
		previousTables = observation.tables;
		previousMs = capturedMs;
	}
	return result(contract, startedAt, quietStartedAt, observations.at(-1).capturedAt, observations.length, resetCount, 'pending', 'maintenance-quiet-pending');
}

function validateObservation(observation) {
	assertExactKeys(observation, ROOT_KEYS);
	canonicalIso(observation.capturedAt);
	if (!Number.isSafeInteger(observation.activeAutovacuumWorkers) || observation.activeAutovacuumWorkers < 0) throw new Error('maintenance active worker schema');
	assertExactKeys(observation.tables, EXPECTED_TABLES);
	for (const name of EXPECTED_TABLES) {
		const table = observation.tables[name];
		assertExactKeys(table, TABLE_KEYS);
		for (const key of ['lastAnalyze', 'lastAutoanalyze', 'lastVacuum', 'lastAutovacuum']) if (table[key] !== null) canonicalIso(table[key]);
		for (const key of ['analyzeCount', 'autoanalyzeCount', 'vacuumCount', 'autovacuumCount', 'nTupIns', 'nTupUpd', 'nTupDel']) {
			if (typeof table[key] !== 'string' || !DECIMAL.test(table[key])) throw new Error('maintenance counter schema');
		}
	}
	return observation;
}

function validateContract(contract) {
	assertExactKeys(contract, ['pollIntervalSeconds', 'quietSeconds', 'timeoutSeconds']);
	for (const value of Object.values(contract)) if (!Number.isFinite(value) || value <= 0) throw new Error('maintenance quiet contract mismatch');
	if (contract.quietSeconds < contract.pollIntervalSeconds || contract.timeoutSeconds < contract.quietSeconds) throw new Error('maintenance quiet contract mismatch');
	return contract;
}

function result(contract, startedAt, quietStartedMs, finishedAt, pollCount, resetCount, finalStatus, reason) {
	return {
		contract: { ...contract }, startedAt,
		quietStartedAt: quietStartedMs === null ? null : new Date(quietStartedMs).toISOString(),
		finishedAt, pollCount, resetCount, finalStatus, reason,
	};
}

function assertExactKeys(value, keys) {
	if (!value || typeof value !== 'object' || Array.isArray(value)
		|| JSON.stringify(Object.keys(value).sort()) !== JSON.stringify([...keys].sort())) throw new Error('maintenance schema');
}

function canonicalIso(value) {
	if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3,6})Z$/.test(value)
		|| !Number.isFinite(Date.parse(value))) throw new Error('maintenance timestamp schema');
}

function number(value) {
	if (typeof value !== 'string' || !/^(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$/.test(value)) throw new Error('maintenance quiet contract mismatch');
	return Number(value);
}
