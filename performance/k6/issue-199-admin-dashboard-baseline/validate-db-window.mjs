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
	'analyze_count', 'autoanalyze_count', 'last_vacuum', 'last_autovacuum', 'vacuum_count', 'autovacuum_count',
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
	if (evidence.status === 'conditional-not-adoptable') {
		process.stderr.write('Measured DB window is conditional-not-adoptable: external activity coverage is boundary-snapshot-only.\n');
		process.exitCode = 2;
	} else {
		assert.equal(evidence.adoptable, true, `Measured DB window is contaminated: ${JSON.stringify(evidence.failures)}`);
	}
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function validateWindow(before, after, declaredExternalActivity) {
	const failures = [];
	validateSnapshotSchema(before, 'before');
	validateSnapshotSchema(after, 'after');
	if (declaredExternalActivity !== 'none') {
		failures.push({name: 'externalActivity', expected: 'none', actual: declaredExternalActivity});
	}
	for (const [stage, snapshot] of [['before', before], ['after', after]]) {
		if (!deepEqual(snapshot.observerOverhead, OBSERVER_POLICY)) {
			failures.push({name: `${stage}.observerOverhead`, expected: OBSERVER_POLICY, actual: snapshot.observerOverhead});
		}
		if (safeIntegerNumber(snapshot.externalActiveSessions, `${stage}.externalActiveSessions`) !== 0) {
			failures.push({name: `${stage}.externalActiveSessions`, expected: 0, actual: snapshot.externalActiveSessions});
		}
	}
	const externalActivityCoverage = before.externalActivityCoverage;
	if (before.externalActivityCoverage !== after.externalActivityCoverage) {
		failures.push({
			name: 'externalActivityCoverage stability',
			expected: before.externalActivityCoverage,
			actual: after.externalActivityCoverage,
		});
	}
	if (externalActivityCoverage !== 'continuous-approved-provenance-or-isolation') {
		failures.push({
			name: 'externalActivityCoverage',
			expected: 'continuous approved provenance or isolation',
			actual: externalActivityCoverage,
		});
	}
	const beforeTime = Date.parse(before.capturedAt);
	const afterTime = Date.parse(after.capturedAt);
	if (!Number.isFinite(beforeTime) || !Number.isFinite(afterTime) || afterTime <= beforeTime) {
		failures.push({name: 'capturedAt order', expected: 'after > before', actual: {before: before.capturedAt, after: after.capturedAt}});
	}

	if (before.database.datname !== after.database.datname) {
		failures.push({name: 'database.datname', expected: before.database.datname, actual: after.database.datname});
	}
	if (before.database.stats_reset !== after.database.stats_reset) {
		failures.push({name: 'database.stats_reset', expected: before.database.stats_reset, actual: after.database.stats_reset});
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
			if (left[field] !== right[field]) {
				failures.push({name: `tables.${relname}.${field}`, expected: left[field], actual: right[field]});
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
	if (!deepEqual(before.plannerContext, after.plannerContext)) {
		failures.push({name: 'plannerContext', expected: before.plannerContext, actual: after.plannerContext});
	}

	const coverageOnly = failures.length === 1 && failures[0].name === 'externalActivityCoverage';
	return {
		status: coverageOnly ? 'conditional-not-adoptable' : failures.length === 0 ? 'adoptable' : 'contaminated',
		adoptable: failures.length === 0,
		externalActivity: declaredExternalActivity,
		externalActivityCoverage,
		observerOverhead: OBSERVER_POLICY,
		plannerContext: before.plannerContext,
		databaseDelta,
		tableDeltas,
		failures,
	};
}

function validateSnapshotSchema(snapshot, label) {
	object(snapshot, label);
	timestamp(snapshot.capturedAt, `${label}.capturedAt`, false);
	string(snapshot.externalActivityCoverage, `${label}.externalActivityCoverage`);
	safeIntegerNumber(snapshot.externalActiveSessions, `${label}.externalActiveSessions`);
	object(snapshot.observerOverhead, `${label}.observerOverhead`);

	object(snapshot.database, `${label}.database`);
	string(snapshot.database.datname, `${label}.database.datname`);
	timestamp(snapshot.database.stats_reset, `${label}.database.stats_reset`, true);
	for (const field of DATABASE_COUNTERS) counter(snapshot.database[field], `${label}.database.${field}`);

	assert.ok(Array.isArray(snapshot.tables), `${label}.tables must be an array.`);
	for (const [index, table] of snapshot.tables.entries()) {
		object(table, `${label}.tables[${index}]`);
		string(table.relname, `${label}.tables[${index}].relname`);
		for (const field of [
			...TABLE_COUNTERS, 'n_live_tup', 'n_dead_tup', 'n_mod_since_analyze',
			'analyze_count', 'autoanalyze_count', 'vacuum_count', 'autovacuum_count',
		]) {
			counter(table[field], `${label}.tables[${index}].${field}`);
		}
		timestamp(table.last_analyze, `${label}.tables[${index}].last_analyze`, true);
		timestamp(table.last_autoanalyze, `${label}.tables[${index}].last_autoanalyze`, true);
		timestamp(table.last_vacuum, `${label}.tables[${index}].last_vacuum`, true);
		timestamp(table.last_autovacuum, `${label}.tables[${index}].last_autovacuum`, true);
	}

	assert.ok(Array.isArray(snapshot.plannerSettings), `${label}.plannerSettings must be an array.`);
	for (const [index, planner] of snapshot.plannerSettings.entries()) {
		object(planner, `${label}.plannerSettings[${index}]`);
		string(planner.name, `${label}.plannerSettings[${index}].name`);
		string(planner.setting, `${label}.plannerSettings[${index}].setting`);
		string(planner.source, `${label}.plannerSettings[${index}].source`);
	}
	object(snapshot.plannerContext, `${label}.plannerContext`);
	assert.deepEqual(
		Object.keys(snapshot.plannerContext).sort(),
		['applicationName', 'currentUser', 'database', 'scope', 'sessionUser'],
		`${label}.plannerContext field set must be exact.`,
	);
	for (const field of ['applicationName', 'currentUser', 'database', 'scope', 'sessionUser']) {
		string(snapshot.plannerContext[field], `${label}.plannerContext.${field}`);
	}
	assert.equal(snapshot.plannerContext.scope, 'observer-session', `${label}.plannerContext.scope must be observer-session.`);
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
		if (map.has(item[key])) failures.push({name: `${label} duplicate`, expected: 'unique', actual: item[key]});
		map.set(item[key], item);
	}
	const actual = [...map.keys()].sort();
	if (!deepEqual(actual, required)) failures.push({name: `${label} set`, expected: required, actual});
	return map;
}

function deltaFields(before, after, fields, label, failures) {
	const deltas = {};
	for (const field of fields) {
		const left = counter(before?.[field], `${label}.${field}.before`);
		const right = counter(after?.[field], `${label}.${field}.after`);
		const delta = right - left;
		deltas[field] = delta.toString();
		if (delta < 0n) failures.push({name: `${label}.${field}`, expected: 'monotonic', actual: delta.toString()});
	}
	return deltas;
}

function counter(value, label) {
	if (typeof value === 'number') {
		assert.ok(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative safe integer or decimal string.`);
		return BigInt(value);
	}
	assert.ok(typeof value === 'string' && /^(?:0|[1-9]\d*)$/.test(value),
		`${label} must be a non-negative safe integer or decimal string.`);
	return BigInt(value);
}

function safeIntegerNumber(value, label) {
	assert.ok(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative safe integer.`);
	return value;
}

function object(value, label) {
	assert.ok(value !== null && typeof value === 'object' && !Array.isArray(value), `${label} must be an object.`);
}

function string(value, label) {
	assert.ok(typeof value === 'string' && value.length > 0, `${label} must be a non-empty string.`);
}

function timestamp(value, label, nullable) {
	if (nullable && value === null) return;
	string(value, label);
	assert.ok(Number.isFinite(Date.parse(value)), `${label} must be an ISO timestamp${nullable ? ' or null' : ''}.`);
}

function deepEqual(left, right) {
	try {
		assert.deepEqual(left, right);
		return true;
	} catch {
		return false;
	}
}
