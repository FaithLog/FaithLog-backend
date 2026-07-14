import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const COMPONENTS = ['app', 'postgres', 'redis'];
const CONTAINER_IDENTITY_FIELDS = [
	'id', 'imageId', 'imageRef', 'startedAt', 'composeProject', 'composeService', 'composeConfigHash',
];
const POSTGRES_IDENTITY_FIELDS = [
	'database', 'serverAddress', 'serverPort', 'serverVersion', 'postmasterStartedAt',
];

const [initialPath, beforePath, afterPath, outputPath] = process.argv.slice(2);

try {
	const initial = readSnapshot(initialPath, 'initial');
	const before = readSnapshot(beforePath, 'before');
	const after = readSnapshot(afterPath, 'after');
	const evidence = validateContinuity(initial, before, after);
	assert.ok(outputPath, 'Runtime continuity output path is required.');
	fs.writeFileSync(path.resolve(outputPath), `${JSON.stringify(evidence, null, 2)}\n`, {flag: 'wx', mode: 0o600});
	assert.equal(evidence.continuous, true, `Runtime identity changed: ${JSON.stringify(evidence.failures)}`);
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function readSnapshot(filePath, label) {
	assert.ok(filePath, `${label} runtime identity path is required.`);
	const snapshot = JSON.parse(fs.readFileSync(path.resolve(filePath), 'utf8'));
	object(snapshot, label);
	timestamp(snapshot.capturedAt, `${label}.capturedAt`);
	object(snapshot.containers, `${label}.containers`);
	assert.deepEqual(Object.keys(snapshot.containers).sort(), COMPONENTS, `${label}.containers set must be exact.`);
	for (const component of COMPONENTS) {
		object(snapshot.containers[component], `${label}.containers.${component}`);
		for (const field of CONTAINER_IDENTITY_FIELDS) {
			string(snapshot.containers[component][field], `${label}.containers.${component}.${field}`);
		}
		timestamp(snapshot.containers[component].startedAt, `${label}.containers.${component}.startedAt`);
	}
	object(snapshot.postgres, `${label}.postgres`);
	for (const field of POSTGRES_IDENTITY_FIELDS) {
		assert.ok(Object.hasOwn(snapshot.postgres, field), `${label}.postgres.${field} is required.`);
	}
	string(snapshot.postgres.database, `${label}.postgres.database`);
	if (snapshot.postgres.serverAddress !== null) {
		string(snapshot.postgres.serverAddress, `${label}.postgres.serverAddress`);
	}
	assert.ok(
		Number.isInteger(snapshot.postgres.serverPort) && snapshot.postgres.serverPort > 0,
		`${label}.postgres.serverPort must be a positive integer.`,
	);
	string(snapshot.postgres.serverVersion, `${label}.postgres.serverVersion`);
	timestamp(snapshot.postgres.postmasterStartedAt, `${label}.postgres.postmasterStartedAt`);
	return snapshot;
}

function validateContinuity(initial, before, after) {
	const failures = [];
	const initialTime = Date.parse(initial.capturedAt);
	const beforeTime = Date.parse(before.capturedAt);
	const afterTime = Date.parse(after.capturedAt);
	if (beforeTime < initialTime || afterTime < beforeTime) {
		failures.push({
			name: 'capturedAt order',
			expected: 'initial <= before <= after',
			actual: {initial: initial.capturedAt, before: before.capturedAt, after: after.capturedAt},
		});
	}
	for (const component of COMPONENTS) {
		for (const field of CONTAINER_IDENTITY_FIELDS) {
			compare(failures, `containers.${component}.${field}`, initial.containers[component][field], before.containers[component][field]);
			compare(failures, `containers.${component}.${field}`, initial.containers[component][field], after.containers[component][field]);
		}
	}
	for (const field of POSTGRES_IDENTITY_FIELDS) {
		compare(failures, `postgres.${field}`, initial.postgres[field], before.postgres[field]);
		compare(failures, `postgres.${field}`, initial.postgres[field], after.postgres[field]);
	}
	return {
		status: failures.length === 0 ? 'continuous' : 'runtime-identity-changed',
		continuous: failures.length === 0,
		identityFields: {
			containers: CONTAINER_IDENTITY_FIELDS,
			postgres: POSTGRES_IDENTITY_FIELDS,
		},
		failures,
	};
}

function compare(failures, name, expected, actual) {
	if (expected !== actual) failures.push({name, expected, actual});
}

function object(value, label) {
	assert.ok(value !== null && typeof value === 'object' && !Array.isArray(value), `${label} must be an object.`);
}

function string(value, label) {
	assert.ok(typeof value === 'string' && value.length > 0, `${label} must be a non-empty string.`);
}

function timestamp(value, label) {
	string(value, label);
	assert.ok(Number.isFinite(Date.parse(value)), `${label} must be an ISO timestamp.`);
}
