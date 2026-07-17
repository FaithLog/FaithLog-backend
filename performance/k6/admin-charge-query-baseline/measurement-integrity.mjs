import {readFile, writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {semanticEqual} from './scenario-definition.mjs';

const COUNTER_NAMES = ['xactCommit', 'xactRollback', 'blksRead', 'blksHit', 'tupReturned', 'tupFetched'];
const PLANNER_NAMES = [
	'enable_bitmapscan', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
	'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'effective_cache_size',
	'random_page_cost', 'seq_page_cost', 'work_mem',
];
const TABLE_NAMES = ['campus_members', 'charge_items', 'payment_accounts', 'users'];
const TABLE_COUNT_NAMES = [
	'nModSinceAnalyze', 'analyzeCount', 'autoanalyzeCount', 'vacuumCount', 'autovacuumCount',
];
const TABLE_TIME_NAMES = [
	'lastAnalyze', 'lastAutoanalyze', 'lastVacuum', 'lastAutovacuum',
];
const TABLE_STATE_NAMES = [...TABLE_COUNT_NAMES, ...TABLE_TIME_NAMES];

export function validateMeasurementIntegrity(input) {
	const {
		beforeState, afterState, calibrationCounter, beforeCounter, afterCounter, expectedDatabaseName,
	} = input;
	validateState(beforeState, 'before', expectedDatabaseName);
	validateState(afterState, 'after', expectedDatabaseName);
	if (Date.parse(beforeState.capturedAt) >= Date.parse(afterState.capturedAt)) {
		throw new Error('Measurement capturedAt order is invalid.');
	}
	if (beforeState.externalActiveCount !== 0 || afterState.externalActiveCount !== 0) {
		throw new Error('External activity was present at a measured boundary.');
	}
	if (!semanticEqual(beforeState.database, afterState.database)) {
		throw new Error('PostgreSQL database/server/postmaster identity or stats reset changed during measurement.');
	}
	if (!semanticEqual(beforeState.plannerSettings, afterState.plannerSettings)) {
		throw new Error('PostgreSQL planner settings changed during measurement.');
	}
	validatePgStatStatements(beforeState.pgStatStatements, afterState.pgStatStatements);
	validateTableStates(beforeState.tables, afterState.tables);

	const observerApproximation = {};
	const rawCounters = {};
	const observerAdjustedCounters = {};
	for (const name of COUNTER_NAMES) {
		const calibration = strictCounter(calibrationCounter, name);
		const before = strictCounter(beforeCounter, name);
		const after = strictCounter(afterCounter, name);
		const approximation = before - calibration;
		const raw = after - before;
		const adjusted = raw - approximation;
		if (approximation < 0n || raw < 0n || adjusted < 0n) {
			throw new Error(`PostgreSQL counter ${name} decreased/reset or observer approximation became negative.`);
		}
		observerApproximation[name] = approximation.toString();
		rawCounters[name] = raw.toString();
		observerAdjustedCounters[name] = adjusted.toString();
	}
	return {
		evidenceIntegrity: 'valid-supporting-evidence',
		interpretation: 'observer adjustment is approximate supporting evidence until repeated real PostgreSQL calibration is validated',
		observerApproximation,
		rawCounters,
		observerAdjustedCounters,
	};
}

export function counterDelta(before, after) {
	const beforeValue = strictDecimal(before, 'before');
	const afterValue = strictDecimal(after, 'after');
	const delta = afterValue - beforeValue;
	if (delta < 0n) {
		throw new Error('PostgreSQL counter delta cannot be negative.');
	}
	return delta.toString();
}

function validateState(state, label, expectedDatabaseName) {
	assertExactKeys(
		state,
		['capturedAt', 'externalActiveCount', 'database', 'plannerSettings', 'pgStatStatements', 'tables'],
		`${label} measurement state`
	);
	assertTimestamp(state.capturedAt, `${label}.capturedAt`, false);
	assertNonNegativeInteger(state.externalActiveCount, `${label}.externalActiveCount`);
	validateDatabase(state.database, label, expectedDatabaseName);
	assertExactKeys(state.plannerSettings, PLANNER_NAMES, `${label}.plannerSettings`);
	for (const name of PLANNER_NAMES) {
		if (typeof state.plannerSettings[name] !== 'string' || state.plannerSettings[name].length === 0) {
			throw new Error(`${label}.plannerSettings.${name} is required.`);
		}
	}
	validatePgStatStatementsSchema(state.pgStatStatements, label);
	assertExactKeys(state.tables, TABLE_NAMES, `${label}.tables`);
	for (const table of TABLE_NAMES) {
		const tableState = state.tables[table];
		assertExactKeys(tableState, TABLE_STATE_NAMES, `${label}.tables.${table}`);
		for (const name of TABLE_COUNT_NAMES) {
			assertNonNegativeInteger(tableState[name], `${label}.tables.${table}.${name}`);
		}
		for (const name of TABLE_TIME_NAMES) {
			assertTimestamp(tableState[name], `${label}.tables.${table}.${name}`, true);
		}
	}
}

function validateDatabase(database, label, expectedDatabaseName) {
	assertExactKeys(
		database,
		['name', 'serverAddress', 'serverPort', 'postmasterStartTime', 'statsReset'],
		`${label}.database`
	);
	if (typeof expectedDatabaseName !== 'string' || expectedDatabaseName.length === 0) {
		throw new Error('Expected PostgreSQL database identity is required.');
	}
	if (database.name !== expectedDatabaseName) {
		throw new Error(`${label} current_database does not match approved database ${expectedDatabaseName}.`);
	}
	if (typeof database.serverAddress !== 'string' || database.serverAddress.length === 0) {
		throw new Error(`${label}.database.serverAddress is required.`);
	}
	if (!Number.isInteger(database.serverPort) || database.serverPort <= 0) {
		throw new Error(`${label}.database.serverPort must be a positive integer.`);
	}
	assertTimestamp(database.postmasterStartTime, `${label}.database.postmasterStartTime`, false);
	assertTimestamp(database.statsReset, `${label}.database.statsReset`, true);
}

function validatePgStatStatementsSchema(pgss, label) {
	assertExactKeys(pgss, ['available', 'statsReset', 'dealloc'], `${label}.pgStatStatements`);
	if (typeof pgss.available !== 'boolean') {
		throw new Error(`${label}.pgStatStatements.available must be boolean.`);
	}
	assertNonNegativeInteger(pgss.dealloc, `${label}.pgStatStatements.dealloc`);
	if (pgss.available) {
		assertTimestamp(pgss.statsReset, `${label}.pgStatStatements.statsReset`, false);
	} else if (pgss.statsReset !== null || pgss.dealloc !== 0) {
		throw new Error(`${label}.pgStatStatements unavailable schema is inconsistent.`);
	}
}

function validatePgStatStatements(before, after) {
	if (before.available !== after.available) {
		throw new Error('pg_stat_statements availability changed during measurement.');
	}
	if (!before.available) {
		return;
	}
	if (before.statsReset !== after.statsReset) {
		throw new Error('pg_stat_statements reset changed during measurement.');
	}
	if (before.dealloc !== after.dealloc) {
		throw new Error('pg_stat_statements eviction/dealloc changed during measurement.');
	}
}

function validateTableStates(beforeTables, afterTables) {
	for (const table of TABLE_NAMES) {
		for (const name of TABLE_STATE_NAMES) {
			if (!Object.is(beforeTables[table][name], afterTables[table][name])) {
				throw new Error(`PostgreSQL planner/analyze/vacuum maintenance state changed for ${table}.${name}.`);
			}
		}
	}
}

function strictCounter(source, name) {
	assertExactKeys(source, COUNTER_NAMES, 'PostgreSQL counter snapshot');
	const value = source[name];
	if (typeof value !== 'string' || !/^(?:0|[1-9][0-9]*)$/.test(value)) {
		throw new Error(`PostgreSQL counter ${name} must be a strict non-negative decimal string; JSON numbers are unsafe.`);
	}
	return BigInt(value);
}

function strictDecimal(value, label) {
	if (typeof value !== 'string' || !/^(?:0|[1-9][0-9]*)$/.test(value)) {
		throw new Error(`PostgreSQL counter ${label} must be a strict non-negative decimal string.`);
	}
	return BigInt(value);
}

function assertExactKeys(value, expected, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) {
		throw new Error(`${label} required object schema is missing.`);
	}
	const actual = Object.keys(value).sort();
	const keys = [...expected].sort();
	if (!semanticEqual(actual, keys)) {
		throw new Error(`${label} required schema keys are invalid.`);
	}
}

function assertTimestamp(value, label, nullable) {
	if (nullable && value === null) {
		return;
	}
	if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) {
		throw new Error(`${label} must be a valid timestamp${nullable ? ' or null' : ''}.`);
	}
}

function assertNonNegativeInteger(value, label) {
	if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
		throw new Error(`${label} must be a non-negative integer number.`);
	}
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const [beforeStatePath, afterStatePath, calibrationPath, beforeCounterPath, afterCounterPath, outputPath] = process.argv.slice(2);
	if (!outputPath) {
		throw new Error('Usage: measurement-integrity.mjs <before-state> <after-state> <calibration> <before-counter> <after-counter> <output>');
	}
	const [beforeState, afterState, calibrationCounter, beforeCounter, afterCounter] = await Promise.all(
		[beforeStatePath, afterStatePath, calibrationPath, beforeCounterPath, afterCounterPath]
			.map(async (file) => JSON.parse(await readFile(file, 'utf8')))
	);
	const result = validateMeasurementIntegrity({
		beforeState,
		afterState,
		calibrationCounter,
		beforeCounter,
		afterCounter,
		expectedDatabaseName: process.env.EXPECTED_DATABASE_NAME,
	});
	await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, {mode: 0o600});
}
