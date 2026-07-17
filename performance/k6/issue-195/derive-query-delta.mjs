import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [beforePath, afterPath, beforeAvailabilityPath, afterAvailabilityPath, outputPath] = process.argv.slice(2);
if (!beforePath || !afterPath || !beforeAvailabilityPath || !afterAvailabilityPath || !outputPath) {
	throw new Error('beforePath, afterPath, beforeAvailabilityPath, afterAvailabilityPath, and outputPath are required.');
}

const anomalies = [];
const beforeAvailability = readAvailability(beforeAvailabilityPath, 'before');
const afterAvailability = readAvailability(afterAvailabilityPath, 'after');
validateSnapshotPresence(beforeAvailability, beforePath, 'before');
validateSnapshotPresence(afterAvailability, afterPath, 'after');
if (beforeAvailability?.status !== afterAvailability?.status) {
	anomalies.push({
		reason: 'query-availability-changed',
		before: beforeAvailability?.status ?? null,
		after: afterAvailability?.status ?? null,
	});
}
if (anomalies.length > 0) {
	writeResult({
		status: 'non-adoptable',
		integrity: 'lost',
		reason: 'pg_stat_statements availability evidence is malformed or changed across phases',
		anomalies,
	});
	process.exit(2);
}
if (beforeAvailability.status === 'unavailable') {
	writeResult({
		status: 'query-evidence-unavailable',
		integrity: 'verified-continuously-unavailable',
		reason: 'pg_stat_statements was unavailable in both before and after phases',
	});
	process.exit(0);
}

const observerMarker = 'faithlog_issue195_runtime_integrity_observer';
const beforeRows = readProductionRows(beforePath, 'before');
const afterRows = readProductionRows(afterPath, 'after');
if (anomalies.length > 0) {
	writeResult({
		status: 'non-adoptable',
		integrity: 'lost',
		reason: 'available pg_stat_statements snapshots must contain strict non-empty production NDJSON evidence',
		anomalies,
	});
	process.exit(2);
}
const integerCounterNames = ['calls', 'rows'];
const decimalCounterNames = ['totalExecTime'];
const beforeByIdentity = indexRows(beforeRows, 'before');
const afterByIdentity = indexRows(afterRows, 'after');

for (const [identity, before] of beforeByIdentity) {
	const after = afterByIdentity.get(identity);
	if (!after) {
		anomalies.push({ reason: 'before-query-missing-after', identity: identityObject(before) });
		continue;
	}
	for (const counter of integerCounterNames) {
		if (BigInt(after[counter]) < BigInt(before[counter])) {
			anomalies.push({
				reason: 'counter-regression',
				identity: identityObject(before),
				counter,
				before: before[counter],
				after: after[counter],
			});
		}
	}
	for (const counter of decimalCounterNames) {
		if (after[counter] < before[counter]) {
			anomalies.push({
				reason: 'counter-regression',
				identity: identityObject(before),
				counter,
				before: before[counter],
				after: after[counter],
			});
		}
	}
}

for (const [identity, after] of afterByIdentity) {
	if (!beforeByIdentity.has(identity)) {
		anomalies.push({ reason: 'after-query-missing-before', identity: identityObject(after) });
	}
}

if (anomalies.length > 0) {
	writeResult({
		status: 'non-adoptable',
		integrity: 'lost',
		reason: 'pg_stat_statements snapshots are not uniquely and monotonically comparable',
		anomalies,
	});
	process.exit(2);
}

const deltas = [...afterByIdentity.entries()]
	.map(([identity, after]) => {
		const before = beforeByIdentity.get(identity);
		const calls = BigInt(after.calls) - BigInt(before.calls);
		const rows = BigInt(after.rows) - BigInt(before.rows);
		const totalExecTime = after.totalExecTime - before.totalExecTime;
		const callsAsNumber = Number(calls);
		return {
			...identityObject(after),
			calls: calls.toString(),
			rows: rows.toString(),
			totalExecTime,
			meanExecTime: calls > 0n && Number.isFinite(callsAsNumber) ? totalExecTime / callsAsNumber : 0,
			query: after.query,
		};
	})
	.filter(({ calls }) => BigInt(calls) > 0n)
	.sort((left, right) => right.totalExecTime - left.totalExecTime);

writeResult({
	status: 'available',
	integrity: 'verified',
	identity: ['userId', 'dbId', 'queryId', 'topLevel'],
	observedCallDelta: deltas.reduce((sum, { calls }) => sum + BigInt(calls), 0n).toString(),
	deltas,
});

function indexRows(rows, snapshot) {
	const indexed = new Map();
	for (const row of rows) {
		if (!hasValidIdentity(row)) {
			anomalies.push({ reason: 'invalid-query-identity', snapshot, identity: identityObject(row) });
			continue;
		}
		let countersValid = true;
		for (const counter of integerCounterNames) {
			if (!isCanonicalNonNegativeIntegerString(row[counter])) {
				anomalies.push({
					reason: 'invalid-snapshot-counter',
					snapshot,
					identity: identityObject(row),
					counter,
					value: row[counter],
				});
				countersValid = false;
			}
		}
		for (const counter of decimalCounterNames) {
			if (typeof row[counter] !== 'number' || !Number.isFinite(row[counter]) || row[counter] < 0) {
				anomalies.push({
					reason: 'invalid-snapshot-counter',
					snapshot,
					identity: identityObject(row),
					counter,
					value: row[counter],
				});
				countersValid = false;
			}
		}
		if (!countersValid) continue;
		const identity = identityKey(row);
		if (indexed.has(identity)) {
			anomalies.push({ reason: 'duplicate-query-identity', snapshot, identity: identityObject(row) });
			continue;
		}
		indexed.set(identity, row);
	}
	return indexed;
}

function isCanonicalNonNegativeIntegerString(value) {
	return typeof value === 'string' && /^(0|[1-9]\d*)$/.test(value);
}

function readAvailability(filePath, expectedPhase) {
	if (!fs.existsSync(filePath)) {
		anomalies.push({ reason: 'query-availability-marker-missing', phase: expectedPhase });
		return null;
	}
	let value;
	try {
		value = JSON.parse(fs.readFileSync(filePath, 'utf8'));
	} catch {
		anomalies.push({ reason: 'query-availability-marker-invalid-json', phase: expectedPhase });
		return null;
	}
	const expectedKeys = ['phase', 'relation', 'schemaVersion', 'status'];
	if (value === null || typeof value !== 'object' || Array.isArray(value)
		|| !isDeepStrictEqual(Object.keys(value).sort(), expectedKeys)) {
		anomalies.push({ reason: 'query-availability-marker-invalid-schema', phase: expectedPhase });
		return null;
	}
	if (value.schemaVersion !== 1 || value.phase !== expectedPhase
		|| !['available', 'unavailable'].includes(value.status)
		|| (value.status === 'available' && value.relation !== 'pg_stat_statements')
		|| (value.status === 'unavailable' && value.relation !== null)) {
		anomalies.push({ reason: 'query-availability-marker-invalid-schema', phase: expectedPhase });
		return null;
	}
	return value;
}

function validateSnapshotPresence(availability, snapshotPath, phase) {
	if (!availability) return;
	const snapshotExists = fs.existsSync(snapshotPath);
	if (availability.status === 'available' && !snapshotExists) {
		anomalies.push({ reason: 'available-query-snapshot-missing', phase });
	}
	if (availability.status === 'unavailable' && snapshotExists) {
		anomalies.push({ reason: 'unavailable-query-snapshot-present', phase });
	}
}

function hasValidIdentity(row) {
	return row !== null
		&& typeof row === 'object'
		&& !Array.isArray(row)
		&& ['userId', 'dbId', 'queryId'].every((field) => typeof row[field] === 'string' && row[field].length > 0)
		&& typeof row.topLevel === 'boolean'
		&& typeof row.query === 'string'
		&& row.query.length > 0;
}

function identityKey(row) {
	return JSON.stringify([row.userId, row.dbId, row.queryId, row.topLevel]);
}

function identityObject(row = {}) {
	return {
		userId: row.userId ?? null,
		dbId: row.dbId ?? null,
		queryId: row.queryId ?? null,
		topLevel: row.topLevel ?? null,
	};
}

function readProductionRows(filePath, phase) {
	const lines = fs.readFileSync(filePath, 'utf8')
		.split('\n')
		.filter((line) => line.trim().length > 0);
	const rows = [];
	for (let index = 0; index < lines.length; index += 1) {
		try {
			rows.push(JSON.parse(lines[index]));
		} catch {
			anomalies.push({
				reason: 'available-query-snapshot-malformed',
				phase,
				line: index + 1,
			});
			return [];
		}
	}
	const productionRows = rows.filter(isProductionQuery);
	if (productionRows.length === 0) {
		anomalies.push({ reason: 'available-query-snapshot-empty', phase });
	}
	return productionRows;
}

function isProductionQuery(row) {
	return typeof row.query !== 'string' || !row.query.toLowerCase().includes(observerMarker);
}

function writeResult(result) {
	fs.writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`);
}
