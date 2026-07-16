import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validatePgStatStatementsSnapshot } from './pg-stat-statements-contract.mjs';

const EXPECTED_INSERTS = {
	weekly_devotion_records: 1000,
	devotion_daily_checks: 7000,
	charge_items: 1000,
};
const EXPECTED_TABLES = ['users', 'campuses', 'campus_members', 'penalty_rules', 'payment_accounts', ...Object.keys(EXPECTED_INSERTS)].sort();
const COUNTER_FIELDS = ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del', 'n_mod_since_analyze'];
const DATABASE_COUNTER_FIELDS = ['xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched', 'tup_inserted', 'tup_updated', 'tup_deleted'];
const STABLE_MAINTENANCE_FIELDS = [
	'last_analyze', 'last_autoanalyze', 'analyze_count', 'autoanalyze_count',
	'last_vacuum', 'last_autovacuum', 'vacuum_count', 'autovacuum_count',
];
const TABLE_FIELDS = ['relname', ...COUNTER_FIELDS, ...STABLE_MAINTENANCE_FIELDS].sort();
const REQUIRED_PLANNER_SETTINGS = [
	'enable_bitmapscan', 'enable_hashagg', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
	'enable_material', 'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'jit', 'plan_cache_mode',
	'random_page_cost', 'work_mem',
].sort();
const OBSERVER_POLICY = {
	databaseWideCountersIncludeSnapshotTransaction: true,
	databaseWideDeltaIsExactQueryCount: false,
	appTableCountersReadApplicationTables: false,
};

export function validateDbWindow(before, after, externalActivity) {
	const failures = [];
	if (externalActivity !== 'none') failures.push({ name: 'externalActivity', expected: 'none', actual: externalActivity });
	for (const [name, evidence] of [['before', before], ['after', after]]) {
		if (!deepEqual(evidence.snapshot?.observerOverhead, OBSERVER_POLICY)) {
			failures.push({ name: `${name}.observerOverhead`, expected: OBSERVER_POLICY, actual: evidence.snapshot?.observerOverhead });
		}
		validateExternalSessionCount(evidence.snapshot?.externalActiveSessions, `${name}.externalActiveSessions`, failures);
		validateExternalSessionCount(evidence.snapshot?.externalActiveSessionsAllDatabases, `${name}.externalActiveSessionsAllDatabases`, failures);
	}
	const beforeTime = Date.parse(before.snapshot?.capturedAt);
	const afterTime = Date.parse(after.snapshot?.capturedAt);
	if (!Number.isFinite(beforeTime) || !Number.isFinite(afterTime) || afterTime <= beforeTime) {
		failures.push({ name: 'capturedAt order', expected: 'after > before', actual: { before: before.snapshot?.capturedAt, after: after.snapshot?.capturedAt } });
	}
	if (before.snapshot?.database?.datname !== after.snapshot?.database?.datname) {
		failures.push({ name: 'database.datname', expected: before.snapshot?.database?.datname, actual: after.snapshot?.database?.datname });
	}
	if ((before.snapshot?.database?.stats_reset ?? null) !== (after.snapshot?.database?.stats_reset ?? null)) {
		failures.push({ name: 'database.stats_reset', expected: before.snapshot?.database?.stats_reset ?? null, actual: after.snapshot?.database?.stats_reset ?? null });
	}
	const beforeTables = exactMap(before.snapshot?.tables, 'relname', EXPECTED_TABLES, 'tables', failures);
	const afterTables = exactMap(after.snapshot?.tables, 'relname', EXPECTED_TABLES, 'tables', failures);
	const beforeTableNames = [...beforeTables.keys()].sort();
	const afterTableNames = [...afterTables.keys()].sort();
	if (beforeTableNames.join(',') !== afterTableNames.join(',')) failures.push({ name: 'table set continuity', expected: beforeTableNames, actual: afterTableNames });
	const tableDeltas = {};
	for (const [relname, beforeTable] of beforeTables) {
		const afterTable = afterTables.get(relname);
		if (!afterTable) continue;
		validateTableSnapshot(beforeTable, `before.tables.${relname}`);
		validateTableSnapshot(afterTable, `after.tables.${relname}`);
		const delta = {};
		for (const field of COUNTER_FIELDS) {
			const value = counterDelta(beforeTable[field], afterTable[field], `${relname}.${field}`);
			delta[field] = value.toString();
			if (value < 0n) failures.push({ name: `${relname}.${field} delta`, expected: 'non-negative', actual: delta[field] });
		}
		for (const field of STABLE_MAINTENANCE_FIELDS) {
			if ((afterTable[field] ?? null) !== (beforeTable[field] ?? null)) {
				failures.push({ name: `${relname}.${field}`, expected: beforeTable[field] ?? null, actual: afterTable[field] ?? null });
			}
		}
		const expectedInserts = BigInt(EXPECTED_INSERTS[relname] || 0);
		if (BigInt(delta.n_tup_ins) !== expectedInserts) failures.push({ name: `${relname}.n_tup_ins delta`, expected: expectedInserts.toString(), actual: delta.n_tup_ins });
		if (BigInt(delta.n_tup_del) !== 0n) failures.push({ name: `${relname}.n_tup_del delta`, expected: '0', actual: delta.n_tup_del });
		if (!Object.hasOwn(EXPECTED_INSERTS, relname) && BigInt(delta.n_tup_upd) !== 0n) {
			failures.push({ name: `${relname}.n_tup_upd delta`, expected: '0', actual: delta.n_tup_upd });
		}
		tableDeltas[relname] = delta;
	}
	const beforePlanner = exactMap(before.snapshot?.plannerSettings, 'name', REQUIRED_PLANNER_SETTINGS, 'plannerSettings.before', failures);
	const afterPlanner = exactMap(after.snapshot?.plannerSettings, 'name', REQUIRED_PLANNER_SETTINGS, 'plannerSettings.after', failures);
	for (const name of REQUIRED_PLANNER_SETTINGS) {
		if (beforePlanner.has(name) && afterPlanner.has(name) && !deepEqual(beforePlanner.get(name), afterPlanner.get(name))) {
			failures.push({ name: `plannerSettings.${name}`, expected: beforePlanner.get(name), actual: afterPlanner.get(name) });
		}
	}
	const databaseDelta = cumulativeDelta(before.snapshot?.database, after.snapshot?.database);
	for (const [field, delta] of Object.entries(databaseDelta)) {
		if (BigInt(delta) < 0n) failures.push({ name: `database.${field} delta`, expected: 'non-negative', actual: delta });
	}
	if (BigInt(databaseDelta.tup_inserted) !== 9000n) failures.push({ name: 'database.tup_inserted delta', expected: '9000', actual: databaseDelta.tup_inserted });
	if (BigInt(databaseDelta.tup_deleted) !== 0n) failures.push({ name: 'database.tup_deleted delta', expected: '0', actual: databaseDelta.tup_deleted });
	if (BigInt(databaseDelta.xact_commit) < 1000n) failures.push({ name: 'database.xact_commit delta', expected: 'at least 1000', actual: databaseDelta.xact_commit });
	if (BigInt(databaseDelta.xact_rollback) !== 0n) failures.push({ name: 'database.xact_rollback delta', expected: '0', actual: databaseDelta.xact_rollback });
	const databaseInstanceDelta = validateDatabaseInstance(before.snapshot, after.snapshot, failures);
	const statementDelta = pgStatementDelta(before.pgStatStatements, after.pgStatStatements, failures);
	return {
		status: failures.length === 0 ? 'adoptable' : 'contaminated',
		adoptable: failures.length === 0,
		externalActivity,
		observerOverhead: OBSERVER_POLICY,
		databaseDelta,
		databaseInstanceDelta,
		tableDeltas,
		pgStatStatements: statementDelta,
		failures,
	};
}

function validateTableSnapshot(table, label) {
	const keys = Object.keys(table || {}).sort();
	if (!deepEqual(keys, TABLE_FIELDS)) throw new Error(`${label} must have exact table counter and maintenance fields.`);
	if (typeof table.relname !== 'string' || table.relname.length === 0) throw new Error(`${label}.relname must be a non-empty string.`);
	for (const field of COUNTER_FIELDS) decimalCounter(table[field], `${label}.${field}`);
	for (const field of ['analyze_count', 'autoanalyze_count', 'vacuum_count', 'autovacuum_count']) {
		nonNegativeInteger(table[field], `${label}.${field}`);
	}
	for (const field of ['last_analyze', 'last_autoanalyze', 'last_vacuum', 'last_autovacuum']) {
		if (table[field] !== null && !isStrictTimestamp(table[field])) throw new Error(`${label}.${field} must be null or an ISO timestamp.`);
	}
}

function nonNegativeInteger(value, label) {
	if (!Number.isSafeInteger(value) || value < 0) throw new Error(`${label} must be a non-negative safe integer.`);
}

function isStrictTimestamp(value) {
	if (typeof value !== 'string') return false;
	const parsed = Date.parse(value);
	return Number.isFinite(parsed)
		&& /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value);
}

function validateDatabaseInstance(before, after, failures) {
	if (!Array.isArray(before?.allDatabases) || !Array.isArray(after?.allDatabases)) {
		throw new Error('all database counters are required.');
	}
	const beforeMap = new Map(before.allDatabases.map((database) => [databaseKey(database), database]));
	const afterMap = new Map(after.allDatabases.map((database) => [databaseKey(database), database]));
	const beforeKeys = [...beforeMap.keys()].sort();
	const afterKeys = [...afterMap.keys()].sort();
	if (!deepEqual(beforeKeys, afterKeys)) failures.push({ name: 'database instance set continuity', expected: beforeKeys, actual: afterKeys });
	const currentDatabase = before.database?.datname;
	if (!deepEqual(beforeMap.get(currentDatabase), before.database)) failures.push({ name: 'before.allDatabases current database', expected: before.database, actual: beforeMap.get(currentDatabase) });
	if (!deepEqual(afterMap.get(currentDatabase), after.database)) failures.push({ name: 'after.allDatabases current database', expected: after.database, actual: afterMap.get(currentDatabase) });
	const deltas = {};
	for (const datname of beforeKeys.filter((name) => name !== currentDatabase)) {
		const beforeDatabase = beforeMap.get(datname);
		const afterDatabase = afterMap.get(datname);
		if (!afterDatabase) continue;
		if ((beforeDatabase.stats_reset ?? null) !== (afterDatabase.stats_reset ?? null)) {
			failures.push({ name: `databaseInstance.${datname}.stats_reset`, expected: beforeDatabase.stats_reset ?? null, actual: afterDatabase.stats_reset ?? null });
		}
		deltas[datname] = {};
		for (const field of DATABASE_COUNTER_FIELDS) {
			const value = counterDelta(beforeDatabase[field], afterDatabase[field], `${datname}.${field}`);
			deltas[datname][field] = value.toString();
			if (value !== 0n) failures.push({ name: `databaseInstance.${datname}.${field}`, expected: '0', actual: value.toString() });
		}
	}
	return { currentDatabase, databaseSet: beforeKeys, nonCurrentDatabaseDeltas: deltas };
}

function readEvidence(filePath) {
	const source = fs.readFileSync(filePath, 'utf8').trim();
	if (!source) throw new Error(`empty DB evidence: ${filePath}`);
	try { return JSON.parse(source); } catch (error) {
		return source.split('\n').filter(Boolean).map((line) => JSON.parse(line)).reduce((merged, item) => ({ ...merged, ...item }), {});
	}
}

function exactMap(items, key, expectedKeys, label, failures) {
	if (!Array.isArray(items)) throw new Error(`DB evidence ${label} must be an array.`);
	const map = new Map();
	for (const item of items) {
		if (map.has(item?.[key])) failures.push({ name: `${label} duplicate`, expected: 'unique', actual: item?.[key] });
		map.set(item?.[key], item);
	}
	const actualKeys = [...map.keys()].sort();
	if (!deepEqual(actualKeys, expectedKeys)) failures.push({ name: `${label} set`, expected: expectedKeys, actual: actualKeys });
	return map;
}

function cumulativeDelta(before, after) {
	if (!before || !after) throw new Error('database counters are required.');
	return Object.fromEntries(DATABASE_COUNTER_FIELDS
		.map((field) => [field, counterDelta(before[field], after[field], `database.${field}`).toString()]));
}

function databaseKey(database) {
	return database?.datname === null ? '<shared>' : String(database?.datname);
}

function pgStatementDelta(before, after, failures) {
	const beforeContract = validatePgStatStatementsSnapshot(before, 'before.pgStatStatements', failures, { requireInventory: true });
	const afterContract = validatePgStatStatementsSnapshot(after, 'after.pgStatStatements', failures, { requireInventory: true });
	if (beforeContract.available !== null && afterContract.available !== null && beforeContract.available !== afterContract.available) {
		failures.push({ name: 'pg_stat_statements availability', expected: before?.available, actual: after?.available });
	}
	if (!beforeContract.valid || !afterContract.valid) return { available: false, reason: 'invalid pg_stat_statements snapshot schema', statements: [] };
	if (!beforeContract.available || !afterContract.available) return { available: false, reason: after.reason || before.reason, statements: [] };
	const beforeMap = aggregateStatements(before.statements);
	const afterMap = aggregateStatements(after.statements);
	for (const query of beforeMap.keys()) {
		if (!afterMap.has(query)) failures.push({ name: 'pg_stat_statements query', expected: query, actual: 'missing after measured window' });
	}
	return {
		available: true,
		statements: [...afterMap.values()].map((item) => {
			const previous = beforeMap.get(item.query) || {};
			if (typeof item.query !== 'string' || item.query.length === 0) {
				failures.push({ name: 'pg_stat_statements.query', expected: 'normalized query text', actual: item.query });
			}
			const calls = item.calls - (previous.calls ?? 0n);
			const rows = item.rows - (previous.rows ?? 0n);
			const sharedBlksHit = item.sharedBlksHit - (previous.sharedBlksHit ?? 0n);
			const sharedBlksRead = item.sharedBlksRead - (previous.sharedBlksRead ?? 0n);
			const totalExecTime = finiteNumber(item.totalExecTime, 'statement.totalExecTime') - finiteNumber(previous.totalExecTime ?? 0, 'statement.totalExecTime');
			const delta = {
				query: item.query,
				calls: calls.toString(), rows: rows.toString(), totalExecTime,
				sharedBlksHit: sharedBlksHit.toString(), sharedBlksRead: sharedBlksRead.toString(),
			};
			for (const [field, value] of Object.entries({ calls, rows, sharedBlksHit, sharedBlksRead })) {
				if (value < 0n) failures.push({ name: `pg_stat_statements.${item.query}.${field} delta`, expected: 'non-negative', actual: value.toString() });
			}
			if (totalExecTime < 0) failures.push({ name: `pg_stat_statements.${item.query}.totalExecTime delta`, expected: 'non-negative', actual: totalExecTime });
			return delta;
		}),
	};
}

function aggregateStatements(statements) {
	const map = new Map();
	for (const statement of statements || []) {
		if (typeof statement.query !== 'string' || statement.query.length === 0) throw new Error('pg_stat_statements query text is required.');
		const current = map.get(statement.query) || {
			query: statement.query,
			calls: 0n,
			rows: 0n,
			totalExecTime: 0,
			sharedBlksHit: 0n,
			sharedBlksRead: 0n,
		};
		for (const field of ['calls', 'rows', 'sharedBlksHit', 'sharedBlksRead']) current[field] += decimalCounter(statement[field], `statement.${field}`);
		current.totalExecTime += finiteNumber(statement.totalExecTime, 'statement.totalExecTime');
		map.set(statement.query, current);
	}
	return map;
}

function decimalCounter(value, label) {
	if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)$/.test(value)) throw new Error(`${label} must be a lossless non-negative decimal string.`);
	return BigInt(value);
}

function counterDelta(before, after, label) {
	return decimalCounter(after, `${label}.after`) - decimalCounter(before, `${label}.before`);
}

function finiteNumber(value, label) {
	if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${label} must be a finite number.`);
	return value;
}

function validateExternalSessionCount(value, label, failures) {
	if (!Number.isSafeInteger(value) || value < 0) {
		failures.push({ name: label, expected: 'non-negative safe integer', actual: value });
		return;
	}
	if (value !== 0) failures.push({ name: label, expected: 0, actual: value });
}

function deepEqual(left, right) {
	try {
		assert.deepEqual(left, right);
		return true;
	} catch {
		return false;
	}
}

async function main() {
	const [beforePath, afterPath, externalActivity, outputPath] = process.argv.slice(2);
	const evidence = validateDbWindow(readEvidence(beforePath), readEvidence(afterPath), externalActivity);
	fs.mkdirSync(path.dirname(outputPath), { recursive: true });
	fs.writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o600 });
	if (!evidence.adoptable) throw new Error(`measured DB window is contaminated: ${JSON.stringify(evidence.failures)}`);
	process.stdout.write(`${JSON.stringify(evidence)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
