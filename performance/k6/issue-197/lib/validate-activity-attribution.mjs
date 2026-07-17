import assert from 'node:assert/strict';
import { createHash, timingSafeEqual } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validatePgStatStatementsSnapshot } from './pg-stat-statements-contract.mjs';

const TABLE_COUNTERS = [
	'seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch',
	'n_tup_ins', 'n_tup_upd', 'n_tup_del', 'n_mod_since_analyze',
];
const DATABASE_COUNTERS = [
	'xact_commit', 'xact_rollback', 'blks_read', 'blks_hit',
	'tup_returned', 'tup_fetched', 'tup_inserted', 'tup_updated', 'tup_deleted',
];
const EXPECTED_INSERTS_PER_USER = {
	weekly_devotion_records: 1,
	devotion_daily_checks: 7,
	charge_items: 1,
};
const EXPECTED_TABLES = [
	'users', 'campuses', 'campus_members', 'penalty_rules', 'payment_accounts',
	...Object.keys(EXPECTED_INSERTS_PER_USER),
].sort();

export function validateApprovedSignature(signature, context = {}) {
	if (!signature || typeof signature !== 'object' || Array.isArray(signature)) {
		throw new Error('approved activity signature must be an object.');
	}
	exactKeys(signature, ['schemaVersion', 'datasetId', 'fixtureRunId', 'databaseName', 'warmupUsers', 'measuredUsers', 'windows'], 'root');
	if (signature.schemaVersion !== 1) throw new Error('approved activity signature schemaVersion must be 1.');
	if (typeof signature.datasetId !== 'string' || !/^PERFORMANCE_/.test(signature.datasetId)) throw new Error('approved activity signature datasetId must start with PERFORMANCE_.');
	if (typeof signature.fixtureRunId !== 'string' || !/^ISSUE197_/.test(signature.fixtureRunId)) throw new Error('approved activity signature fixtureRunId must start with ISSUE197_.');
	if (typeof signature.databaseName !== 'string' || signature.databaseName.length === 0) throw new Error('approved activity signature databaseName must be a non-empty string.');
	for (const field of ['datasetId', 'fixtureRunId', 'databaseName']) {
		if (context[field] !== undefined && signature[field] !== context[field]) {
			throw new Error(`approved activity signature ${field} mismatch.`);
		}
	}
	for (const [field, value] of [['warmupUsers', signature.warmupUsers], ['measuredUsers', signature.measuredUsers]]) {
		if (!Number.isInteger(value) || value < 1) {
			throw new Error(`approved activity signature ${field} must be a positive integer.`);
		}
		if (context[field] !== undefined && value !== Number(context[field])) {
			throw new Error(`approved activity signature ${field} mismatch.`);
		}
	}
	if (signature.measuredUsers !== 1000) throw new Error('approved activity signature measuredUsers must be exactly 1000.');
	if (!signature.windows || typeof signature.windows !== 'object' || Array.isArray(signature.windows)) throw new Error('approved activity signature windows must be an object.');
	exactKeys(signature.windows, ['warmup', 'measured'], 'windows');
	for (const [phase, users] of [['warmup', Number(signature.warmupUsers)], ['measured', Number(signature.measuredUsers)]]) {
		validateSignatureWindow(signature.windows?.[phase], phase, users);
	}
	return signature;
}

export function validateActivityAttribution(
	warmupBefore,
	warmupAfter,
	measuredBefore,
	measuredAfter,
	warmupUsers,
	measuredUsers,
	approvedSignature,
	context = {}
) {
	const signature = validateApprovedSignature(approvedSignature, { ...context, warmupUsers, measuredUsers });
	const digest = signatureDigest(signature);
	if (context.expectedSignatureSha256 !== undefined) {
		if (typeof context.expectedSignatureSha256 !== 'string' || !/^[a-f0-9]{64}$/.test(context.expectedSignatureSha256)) {
			throw new Error('expected approved activity signature sha256 digest must be 64 lowercase hex characters.');
		}
		if (!timingSafeEqual(Buffer.from(digest, 'hex'), Buffer.from(context.expectedSignatureSha256, 'hex'))) {
			throw new Error('approved activity signature sha256 digest mismatch.');
		}
	}
	const failures = [];
	const snapshots = [
		['warmupBefore', warmupBefore], ['warmupAfter', warmupAfter],
		['measuredBefore', measuredBefore], ['measuredAfter', measuredAfter],
	];
	for (const [label, snapshot] of snapshots) {
		validateExternalSessionCount(snapshot.snapshot?.externalActiveSessions, `${label}.externalActiveSessions`, failures);
		validateExternalSessionCount(snapshot.snapshot?.externalActiveSessionsAllDatabases, `${label}.externalActiveSessionsAllDatabases`, failures);
		if (snapshot.snapshot?.database?.datname !== signature.databaseName) {
			failures.push({ name: `${label}.database.datname`, expected: signature.databaseName, actual: snapshot.snapshot?.database?.datname });
		}
	}

	const tableAttribution = compareTableCounters(
		warmupBefore.snapshot?.tables, warmupAfter.snapshot?.tables,
		measuredBefore.snapshot?.tables, measuredAfter.snapshot?.tables,
		signature.windows, failures
	);
	const transactionAttribution = compareTransactions(
		warmupBefore.snapshot?.database, warmupAfter.snapshot?.database,
		measuredBefore.snapshot?.database, measuredAfter.snapshot?.database,
		signature.windows, failures
	);
	const statementAttribution = compareStatements(
		warmupBefore.pgStatStatements, warmupAfter.pgStatStatements,
		measuredBefore.pgStatStatements, measuredAfter.pgStatStatements,
		signature.windows, failures
	);
	const databaseInstanceAttribution = compareDatabaseInstance(
		snapshots, signature.databaseName, failures
	);

	return {
		status: failures.length === 0 ? 'attributable' : 'contaminated',
		adoptable: failures.length === 0,
		warmupUsers: Number(warmupUsers),
		measuredUsers: Number(measuredUsers),
		approvedSignature: {
			schemaVersion: signature.schemaVersion,
			datasetId: signature.datasetId,
			fixtureRunId: signature.fixtureRunId,
			databaseName: signature.databaseName,
			sha256: digest,
		},
		tableAttribution,
		transactionAttribution,
		pgStatStatements: statementAttribution,
		databaseInstanceAttribution,
		failures,
	};
}

function validateSignatureWindow(window, phase, users) {
	if (!window || typeof window !== 'object' || Array.isArray(window)) {
		throw new Error(`approved activity signature windows.${phase} is required.`);
	}
	exactKeys(window, ['applicationCommits', 'rollbacks', 'tableDeltas', 'pgStatStatements'], `windows.${phase}`);
	const applicationCommits = positiveDecimalCounter(window.applicationCommits, `windows.${phase}.applicationCommits`);
	if (applicationCommits < BigInt(users)) {
		throw new Error(`approved activity signature windows.${phase}.applicationCommits must cover every user.`);
	}
	if (window.rollbacks !== '0') {
		throw new Error(`approved activity signature windows.${phase}.rollbacks must be the decimal string 0.`);
	}
	const tables = window.tableDeltas;
	if (!tables || typeof tables !== 'object' || Array.isArray(tables)) {
		throw new Error(`approved activity signature windows.${phase}.tableDeltas is required.`);
	}
	if (!deepEqual(Object.keys(tables).sort(), EXPECTED_TABLES)) {
		throw new Error(`approved activity signature windows.${phase}.tableDeltas must contain the exact scenario table set.`);
	}
	for (const relname of EXPECTED_TABLES) {
		if (!deepEqual(Object.keys(tables[relname] || {}).sort(), [...TABLE_COUNTERS].sort())) {
			throw new Error(`approved activity signature windows.${phase}.tableDeltas.${relname} must contain the exact counter set.`);
		}
		for (const field of TABLE_COUNTERS) decimalCounter(tables[relname][field], `windows.${phase}.tableDeltas.${relname}.${field}`);
		const expectedInserts = BigInt(EXPECTED_INSERTS_PER_USER[relname] || 0) * BigInt(users);
		if (BigInt(tables[relname].n_tup_ins) !== expectedInserts) {
			throw new Error(`approved activity signature windows.${phase}.tableDeltas.${relname}.n_tup_ins must be ${expectedInserts}.`);
		}
		if (tables[relname].n_tup_del !== '0' || (!Object.hasOwn(EXPECTED_INSERTS_PER_USER, relname) && tables[relname].n_tup_upd !== '0')) {
			throw new Error(`approved activity signature windows.${phase}.tableDeltas.${relname} non-write update/delete counters must be 0.`);
		}
	}
	const pgss = window.pgStatStatements;
	if (!pgss || typeof pgss.available !== 'boolean' || !Array.isArray(pgss.statements)) {
		throw new Error(`approved activity signature windows.${phase}.pgStatStatements must declare availability and statements.`);
	}
	exactKeys(pgss, ['available', 'statements'], `windows.${phase}.pgStatStatements`);
	if (!pgss.available && pgss.statements.length !== 0) {
		throw new Error(`approved activity signature windows.${phase}.pgStatStatements statements must be empty when unavailable.`);
	}
	const queries = new Set();
	for (const statement of pgss.statements) {
		exactKeys(statement, ['query', 'calls', 'rows'], `windows.${phase}.pgStatStatements.statement`);
		if (typeof statement.query !== 'string' || statement.query.length === 0 || queries.has(statement.query)) {
			throw new Error(`approved activity signature windows.${phase}.pgStatStatements query text must be non-empty and unique.`);
		}
		queries.add(statement.query);
		for (const field of ['calls', 'rows']) decimalCounter(statement[field], `windows.${phase}.pgStatStatements.${field}`);
	}
}

function compareTableCounters(warmupBefore, warmupAfter, measuredBefore, measuredAfter, windows, failures) {
	const maps = [warmupBefore, warmupAfter, measuredBefore, measuredAfter].map(tableMap);
	for (const map of maps) {
		const actual = [...map.keys()].sort();
		if (!deepEqual(actual, EXPECTED_TABLES)) failures.push({ name: 'attribution table set', expected: EXPECTED_TABLES, actual });
	}
	const result = {};
	for (const relname of EXPECTED_TABLES) {
		result[relname] = {};
		for (const field of TABLE_COUNTERS) {
			const warmupDelta = delta(maps[0].get(relname), maps[1].get(relname), field, `warmup.${relname}`);
			const measuredDelta = delta(maps[2].get(relname), maps[3].get(relname), field, `measured.${relname}`);
			const expectedWarmupDelta = decimalCounter(windows.warmup.tableDeltas[relname][field], `warmup.${relname}.${field}`);
			const expectedMeasuredDelta = decimalCounter(windows.measured.tableDeltas[relname][field], `measured.${relname}.${field}`);
			if (warmupDelta !== expectedWarmupDelta) failures.push({ name: `${relname}.${field} warmup approved signature`, expected: expectedWarmupDelta.toString(), actual: warmupDelta.toString() });
			if (measuredDelta !== expectedMeasuredDelta) failures.push({ name: `${relname}.${field} measured approved signature`, expected: expectedMeasuredDelta.toString(), actual: measuredDelta.toString() });
			result[relname][field] = {
				expectedWarmupDelta: expectedWarmupDelta.toString(), warmupDelta: warmupDelta.toString(),
				expectedMeasuredDelta: expectedMeasuredDelta.toString(), measuredDelta: measuredDelta.toString(),
			};
		}
	}
	return result;
}

function compareTransactions(warmupBefore, warmupAfter, measuredBefore, measuredAfter, windows, failures) {
	const warmupApplicationCommits = delta(warmupBefore, warmupAfter, 'xact_commit', 'warmup.database') - 1n;
	const measuredApplicationCommits = delta(measuredBefore, measuredAfter, 'xact_commit', 'measured.database') - 1n;
	if (warmupApplicationCommits !== decimalCounter(windows.warmup.applicationCommits, 'warmup.applicationCommits')) {
		failures.push({ name: 'database.xact_commit warmup approved signature', expected: windows.warmup.applicationCommits, actual: warmupApplicationCommits.toString() });
	}
	if (measuredApplicationCommits !== decimalCounter(windows.measured.applicationCommits, 'measured.applicationCommits')) {
		failures.push({ name: 'database.xact_commit measured approved signature', expected: windows.measured.applicationCommits, actual: measuredApplicationCommits.toString() });
	}
	const warmupRollbacks = delta(warmupBefore, warmupAfter, 'xact_rollback', 'warmup.database');
	const measuredRollbacks = delta(measuredBefore, measuredAfter, 'xact_rollback', 'measured.database');
	if (warmupRollbacks !== decimalCounter(windows.warmup.rollbacks, 'warmup.rollbacks') || measuredRollbacks !== decimalCounter(windows.measured.rollbacks, 'measured.rollbacks')) {
		failures.push({
			name: 'database.xact_rollback approved signature',
			expected: { warmup: windows.warmup.rollbacks, measured: windows.measured.rollbacks },
			actual: { warmup: warmupRollbacks.toString(), measured: measuredRollbacks.toString() },
		});
	}
	return {
		observerTransactionsPerWindow: 1,
		warmupApplicationCommits: warmupApplicationCommits.toString(),
		measuredApplicationCommits: measuredApplicationCommits.toString(),
		warmupRollbacks: warmupRollbacks.toString(),
		measuredRollbacks: measuredRollbacks.toString(),
	};
}

function compareStatements(warmupBefore, warmupAfter, measuredBefore, measuredAfter, windows, failures) {
	const snapshots = [warmupBefore, warmupAfter, measuredBefore, measuredAfter];
	const labels = ['warmupBefore', 'warmupAfter', 'measuredBefore', 'measuredAfter'];
	const contracts = snapshots.map((item, index) => validatePgStatStatementsSnapshot(item, `${labels[index]}.pgStatStatements`, failures));
	const availability = contracts.map((contract) => contract.available);
	for (const [phase, expected] of [['warmup', windows.warmup.pgStatStatements.available], ['measured', windows.measured.pgStatStatements.available]]) {
		const actual = phase === 'warmup' ? availability.slice(0, 2) : availability.slice(2, 4);
		if (!actual.every((value) => value === expected)) failures.push({ name: `pg_stat_statements.${phase}.availability approved signature`, expected, actual });
		if (actual[0] !== null && actual[1] !== null && actual[0] !== actual[1]) {
			failures.push({ name: `pg_stat_statements.${phase}.availability drift`, expected: actual[0], actual: actual[1] });
		}
	}
	const result = {
		warmup: statementWindow(warmupBefore, warmupAfter, contracts[0], contracts[1]),
		measured: statementWindow(measuredBefore, measuredAfter, contracts[2], contracts[3]),
	};
	for (const phase of ['warmup', 'measured']) {
		if (!windows[phase].pgStatStatements.available) continue;
		const expectedMap = new Map(windows[phase].pgStatStatements.statements.map((statement) => [statement.query, statement]));
		const actualMap = new Map(result[phase].statements.map((statement) => [statement.query, statement]));
		if (!deepEqual([...actualMap.keys()].sort(), [...expectedMap.keys()].sort())) {
			failures.push({ name: `pg_stat_statements.${phase}.query set approved signature`, expected: [...expectedMap.keys()].sort(), actual: [...actualMap.keys()].sort() });
		}
		for (const [query, expected] of expectedMap) {
			const actual = actualMap.get(query);
			for (const field of ['calls', 'rows']) {
				if (actual?.[field] !== expected[field]) failures.push({ name: `pg_stat_statements.${phase}.${field} approved signature`, query, expected: expected[field], actual: actual?.[field] });
			}
		}
	}
	return result;
}

function statementWindow(before, after, beforeContract, afterContract) {
	if (!beforeContract.valid || !afterContract.valid) return { available: false, reason: 'invalid pg_stat_statements snapshot schema', statements: [] };
	if (!beforeContract.available || !afterContract.available) return { available: false, reason: after.reason || before.reason, statements: [] };
	const beforeMap = statementMap(before.statements);
	const afterMap = statementMap(after.statements);
	const queries = [...new Set([...beforeMap.keys(), ...afterMap.keys()])].sort();
	return {
		available: true,
		statements: queries.map((query) => {
			const previous = beforeMap.get(query) || { calls: 0n, rows: 0n };
			const current = afterMap.get(query) || { calls: 0n, rows: 0n };
			return { query, calls: (current.calls - previous.calls).toString(), rows: (current.rows - previous.rows).toString() };
		}),
	};
}

function compareDatabaseInstance(snapshots, currentDatabase, failures) {
	const maps = snapshots.map(([label, evidence]) => {
		if (!Array.isArray(evidence.snapshot?.allDatabases)) throw new Error(`${label}.allDatabases must be an array.`);
		const map = new Map(evidence.snapshot.allDatabases.map((database) => [databaseKey(database), database]));
		if (!deepEqual(map.get(currentDatabase), evidence.snapshot.database)) {
			failures.push({ name: `${label}.allDatabases current database`, expected: evidence.snapshot.database, actual: map.get(currentDatabase) });
		}
		return map;
	});
	const expectedKeys = [...maps[0].keys()].sort();
	for (const map of maps.slice(1)) {
		if (!deepEqual([...map.keys()].sort(), expectedKeys)) failures.push({ name: 'database instance set continuity', expected: expectedKeys, actual: [...map.keys()].sort() });
	}
	const windows = {};
	for (const [phase, beforeIndex, afterIndex] of [['warmup', 0, 1], ['measured', 2, 3]]) {
		windows[phase] = {};
		for (const datname of expectedKeys.filter((name) => name !== currentDatabase)) {
			const before = maps[beforeIndex].get(datname);
			const after = maps[afterIndex].get(datname);
			if ((before?.stats_reset ?? null) !== (after?.stats_reset ?? null)) failures.push({ name: `${phase}.databaseInstance.${datname}.stats_reset`, expected: before?.stats_reset ?? null, actual: after?.stats_reset ?? null });
			windows[phase][datname] = {};
			for (const field of DATABASE_COUNTERS) {
				const counterDelta = delta(before, after, field, `${phase}.databaseInstance.${datname}`);
				windows[phase][datname][field] = counterDelta.toString();
				if (counterDelta !== 0n) failures.push({ name: `${phase}.databaseInstance.${datname}.${field}`, expected: '0', actual: counterDelta.toString() });
			}
		}
	}
	return { currentDatabase, databaseSet: expectedKeys, windows };
}

function tableMap(tables) {
	if (!Array.isArray(tables)) throw new Error('activity attribution tables must be arrays.');
	return new Map(tables.map((table) => [table.relname, table]));
}

function statementMap(statements) {
	const map = new Map();
	for (const statement of statements || []) {
		if (typeof statement.query !== 'string' || statement.query.length === 0) {
			throw new Error('pg_stat_statements normalized query text must be non-empty.');
		}
		const current = map.get(statement.query) || { query: statement.query, calls: 0n, rows: 0n };
		current.calls += decimalCounter(statement.calls, 'pg_stat_statements.calls');
		current.rows += decimalCounter(statement.rows, 'pg_stat_statements.rows');
		map.set(statement.query, current);
	}
	return map;
}

function delta(before, after, field, label, missingValue) {
	const left = decimalCounter(before?.[field] ?? missingValue, `${label}.${field}.before`);
	const right = decimalCounter(after?.[field] ?? missingValue, `${label}.${field}.after`);
	return right - left;
}

function positiveDecimalCounter(value, label) {
	const counter = decimalCounter(value, label);
	if (counter < 1n) throw new Error(`approved activity signature ${label} must be a positive decimal string.`);
	return counter;
}

function decimalCounter(value, label) {
	if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)$/.test(value)) {
		throw new Error(`approved activity signature ${label} must be a lossless non-negative decimal string.`);
	}
	return BigInt(value);
}

function validateExternalSessionCount(value, label, failures) {
	if (!Number.isSafeInteger(value) || value < 0) {
		failures.push({ name: label, expected: 'non-negative safe integer', actual: value });
		return;
	}
	if (value !== 0) failures.push({ name: label, expected: 0, actual: value });
}

function exactKeys(value, expected, label) {
	const actual = Object.keys(value || {}).sort();
	const sortedExpected = [...expected].sort();
	if (!deepEqual(actual, sortedExpected)) throw new Error(`approved activity signature ${label} must contain exact properties: ${sortedExpected.join(', ')}; actual: ${actual.join(', ')}.`);
}

function signatureDigest(signature) {
	return createHash('sha256').update(JSON.stringify(signature)).digest('hex');
}

function databaseKey(database) {
	return database?.datname === null ? '<shared>' : String(database?.datname);
}

function deepEqual(left, right) {
	try { assert.deepEqual(left, right); return true; } catch { return false; }
}

function readEvidence(filePath) {
	const source = fs.readFileSync(filePath, 'utf8').trim();
	if (!source) throw new Error(`empty evidence: ${filePath}`);
	try { return JSON.parse(source); } catch {
		return source.split('\n').filter(Boolean).map((line) => JSON.parse(line)).reduce((merged, item) => ({ ...merged, ...item }), {});
	}
}

async function main() {
	const args = process.argv.slice(2);
	if (args[0] === 'validate-signature') {
		const [, signaturePath, manifestPath, databaseName] = args;
		const signature = readEvidence(signaturePath);
		const manifest = readEvidence(manifestPath);
		validateApprovedSignature(signature, {
			datasetId: manifest.datasetId,
			fixtureRunId: manifest.fixtureRunId,
			databaseName,
			warmupUsers: manifest.warmupUserIds?.length,
			measuredUsers: manifest.measuredUserIds?.length,
		});
		process.stdout.write(`${JSON.stringify({ status: 'approved-signature-valid', fixtureRunId: signature.fixtureRunId })}\n`);
		return;
	}
	if (args[0] === 'freeze-signature') {
		const [, signaturePath, manifestPath, databaseName, outputPath] = args;
		const signature = readEvidence(signaturePath);
		const manifest = readEvidence(manifestPath);
		validateApprovedSignature(signature, {
			datasetId: manifest.datasetId,
			fixtureRunId: manifest.fixtureRunId,
			databaseName,
			warmupUsers: manifest.warmupUserIds?.length,
			measuredUsers: manifest.measuredUserIds?.length,
		});
		fs.mkdirSync(path.dirname(outputPath), { recursive: true });
		fs.writeFileSync(outputPath, `${JSON.stringify(signature, null, 2)}\n`, { mode: 0o600, flag: 'wx' });
		fs.chmodSync(outputPath, 0o600);
		process.stdout.write(`${signatureDigest(signature)}\n`);
		return;
	}
	const [warmupBeforePath, warmupAfterPath, measuredBeforePath, measuredAfterPath, warmupUsers, measuredUsers, signaturePath, expectedSignatureSha256, manifestPath, databaseName, outputPath] = args;
	const manifest = readEvidence(manifestPath);
	const evidence = validateActivityAttribution(
		readEvidence(warmupBeforePath), readEvidence(warmupAfterPath),
		readEvidence(measuredBeforePath), readEvidence(measuredAfterPath),
		Number(warmupUsers), Number(measuredUsers), readEvidence(signaturePath), {
			datasetId: manifest.datasetId,
			fixtureRunId: manifest.fixtureRunId,
			databaseName,
			expectedSignatureSha256,
		}
	);
	fs.mkdirSync(path.dirname(outputPath), { recursive: true });
	fs.writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o600 });
	if (!evidence.adoptable) throw new Error(`measured activity does not match the approved signature: ${JSON.stringify(evidence.failures)}`);
	process.stdout.write(`${JSON.stringify(evidence)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
