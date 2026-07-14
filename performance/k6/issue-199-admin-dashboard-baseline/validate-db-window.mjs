import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const REQUIRED_TABLES = [
	'campus_members', 'campuses', 'charge_items', 'devotion_daily_checks', 'meal_poll_settlements',
	'payment_accounts', 'poll_responses', 'polls', 'prayer_submissions', 'users', 'weekly_devotion_records',
];
const REQUIRED_PLANNER_SETTINGS = [
	'enable_bitmapscan', 'enable_hashagg', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
	'enable_material', 'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'jit', 'plan_cache_mode',
	'random_page_cost', 'work_mem',
];
const DATABASE_COUNTERS = [
	'xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched',
	'temp_files', 'temp_bytes', 'deadlocks',
];
const TABLE_COUNTERS = ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch'];
const TABLE_STABILITY_FIELDS = [
	'n_live_tup', 'n_dead_tup', 'n_mod_since_analyze', 'last_analyze', 'last_autoanalyze',
	'analyze_count', 'autoanalyze_count',
];
const OBSERVER_POLICY = {
	databaseWideCountersIncludeSnapshotTransaction: true,
	databaseWideDeltaIsExactQueryCount: false,
	appTableCountersReadApplicationTables: false,
};

const [beforePath, afterPath, externalActivity, outputPath] = process.argv.slice(2);

try {
	const before = readJson(beforePath);
	const after = readJson(afterPath);
	const evidence = validateWindow(before, after, externalActivity);
	assert.ok(outputPath, 'DB window adoption output path is required.');
	fs.writeFileSync(path.resolve(outputPath), `${JSON.stringify(evidence, null, 2)}\n`, {flag: 'wx', mode: 0o600});
	assert.equal(evidence.adoptable, true, `Measured DB window is contaminated: ${JSON.stringify(evidence.failures)}`);
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function validateWindow(before, after, declaredExternalActivity) {
	const failures = [];
	if (declaredExternalActivity !== 'none') {
		failures.push({name: 'externalActivity', expected: 'none', actual: declaredExternalActivity});
	}
	for (const [stage, snapshot] of [['before', before], ['after', after]]) {
		if (!deepEqual(snapshot.observerOverhead, OBSERVER_POLICY)) {
			failures.push({name: `${stage}.observerOverhead`, expected: OBSERVER_POLICY, actual: snapshot.observerOverhead});
		}
		if (number(snapshot.externalActiveSessions, `${stage}.externalActiveSessions`) !== 0) {
			failures.push({name: `${stage}.externalActiveSessions`, expected: 0, actual: snapshot.externalActiveSessions});
		}
	}
	const beforeTime = Date.parse(before.capturedAt);
	const afterTime = Date.parse(after.capturedAt);
	if (!Number.isFinite(beforeTime) || !Number.isFinite(afterTime) || afterTime <= beforeTime) {
		failures.push({name: 'capturedAt order', expected: 'after > before', actual: {before: before.capturedAt, after: after.capturedAt}});
	}

	if (before.database?.datname !== after.database?.datname) {
		failures.push({name: 'database.datname', expected: before.database?.datname, actual: after.database?.datname});
	}
	if ((before.database?.stats_reset ?? null) !== (after.database?.stats_reset ?? null)) {
		failures.push({name: 'database.stats_reset', expected: before.database?.stats_reset ?? null, actual: after.database?.stats_reset ?? null});
	}
	const databaseDelta = deltaFields(before.database, after.database, DATABASE_COUNTERS, 'database', failures);

	const beforeTables = exactMap(before.tables, 'relname', REQUIRED_TABLES, 'tables', failures);
	const afterTables = exactMap(after.tables, 'relname', REQUIRED_TABLES, 'tables', failures);
	const tableDeltas = {};
	for (const relname of REQUIRED_TABLES) {
		const left = beforeTables.get(relname);
		const right = afterTables.get(relname);
		if (!left || !right) continue;
		tableDeltas[relname] = deltaFields(left, right, TABLE_COUNTERS, `tables.${relname}`, failures);
		for (const field of TABLE_STABILITY_FIELDS) {
			if ((left[field] ?? null) !== (right[field] ?? null)) {
				failures.push({name: `tables.${relname}.${field}`, expected: left[field] ?? null, actual: right[field] ?? null});
			}
		}
	}

	const beforePlanner = exactMap(before.plannerSettings, 'name', REQUIRED_PLANNER_SETTINGS, 'plannerSettings', failures);
	const afterPlanner = exactMap(after.plannerSettings, 'name', REQUIRED_PLANNER_SETTINGS, 'plannerSettings', failures);
	for (const name of REQUIRED_PLANNER_SETTINGS) {
		const left = beforePlanner.get(name);
		const right = afterPlanner.get(name);
		if (left && right && !deepEqual(left, right)) {
			failures.push({name: `plannerSettings.${name}`, expected: left, actual: right});
		}
	}

	return {
		status: failures.length === 0 ? 'adoptable' : 'contaminated',
		adoptable: failures.length === 0,
		externalActivity: declaredExternalActivity,
		observerOverhead: OBSERVER_POLICY,
		databaseDelta,
		tableDeltas,
		failures,
	};
}

function readJson(filePath) {
	assert.ok(filePath, 'DB window evidence path is required.');
	return JSON.parse(fs.readFileSync(path.resolve(filePath), 'utf8'));
}

function exactMap(items, key, required, label, failures) {
	if (!Array.isArray(items)) {
		failures.push({name: label, expected: required, actual: items});
		return new Map();
	}
	const map = new Map();
	for (const item of items) {
		if (map.has(item?.[key])) failures.push({name: `${label} duplicate`, expected: 'unique', actual: item?.[key]});
		map.set(item?.[key], item);
	}
	const actual = [...map.keys()].sort();
	if (!deepEqual(actual, required)) failures.push({name: `${label} set`, expected: required, actual});
	return map;
}

function deltaFields(before, after, fields, label, failures) {
	const deltas = {};
	for (const field of fields) {
		const left = number(before?.[field], `${label}.${field}.before`);
		const right = number(after?.[field], `${label}.${field}.after`);
		deltas[field] = right - left;
		if (deltas[field] < 0) failures.push({name: `${label}.${field}`, expected: 'monotonic', actual: deltas[field]});
	}
	return deltas;
}

function number(value, label) {
	const numeric = Number(value);
	assert.ok(Number.isFinite(numeric), `${label} must be numeric.`);
	return numeric;
}

function deepEqual(left, right) {
	try {
		assert.deepEqual(left, right);
		return true;
	} catch {
		return false;
	}
}
