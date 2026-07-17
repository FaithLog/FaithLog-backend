import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const COMPONENTS = ['app', 'postgres', 'redis'];
const CONTAINER_IDENTITY_FIELDS = [
	'id', 'imageId', 'imageRef', 'startedAt', 'composeProject', 'composeService', 'composeConfigHash', 'name', 'publishedPorts',
];
const POSTGRES_IDENTITY_FIELDS = [
	'database', 'serverAddress', 'serverPort', 'serverVersion', 'systemIdentifier', 'postmasterStartedAt',
	'expectedRoleMatched', 'flyway', 'rls',
];
const REQUIRED_RLS_TABLES = ['campus_members', 'campuses', 'charge_items', 'poll_responses', 'polls', 'users', 'weekly_devotion_records'];

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
	if (outputPath && !fs.existsSync(path.resolve(outputPath))) {
		try {
			fs.writeFileSync(path.resolve(outputPath), `${JSON.stringify({status: 'contaminated', continuous: false,
				automaticAdoption: false, failures: [{name: 'runtimeContinuityEvidence', actual: error.message}]}, null, 2)}\n`,
			{flag: 'wx', mode: 0o600});
		} catch {
			// Preserve the original validation failure.
		}
	}
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
		assert.deepEqual(Object.keys(snapshot.containers[component]).sort(), [...CONTAINER_IDENTITY_FIELDS].sort(),
			`${label}.containers.${component} field set must be exact.`);
		for (const field of CONTAINER_IDENTITY_FIELDS) {
			if (field === 'publishedPorts') object(snapshot.containers[component][field], `${label}.containers.${component}.${field}`);
			else string(snapshot.containers[component][field], `${label}.containers.${component}.${field}`);
		}
		timestamp(snapshot.containers[component].startedAt, `${label}.containers.${component}.startedAt`);
	}
	for (const component of COMPONENTS) {
		object(snapshot.containers[component].publishedPorts, `${label}.containers.${component}.publishedPorts`);
		assert.match(snapshot.containers[component].id, /^sha256:[a-f0-9]{64}$/, `${label}.containers.${component}.id must be full.`);
		assert.match(snapshot.containers[component].imageId, /^sha256:[a-f0-9]{64}$/, `${label}.containers.${component}.imageId must be full.`);
	}
	object(snapshot.postgres, `${label}.postgres`);
	assert.deepEqual(Object.keys(snapshot.postgres).sort(), [...POSTGRES_IDENTITY_FIELDS].sort(),
		`${label}.postgres field set must be exact.`);
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
	string(snapshot.postgres.systemIdentifier, `${label}.postgres.systemIdentifier`);
	timestamp(snapshot.postgres.postmasterStartedAt, `${label}.postgres.postmasterStartedAt`);
	assert.equal(snapshot.postgres.expectedRoleMatched, true, `${label}.postgres expected role must match.`);
	object(snapshot.postgres.flyway, `${label}.postgres.flyway`);
	assert.deepEqual(snapshot.postgres.flyway, {latestVersion: '11', latestScript: 'V11__secure_supabase_data_api.sql', latestSuccess: true},
		`${label}.postgres.flyway must match current develop.`);
	object(snapshot.postgres.rls, `${label}.postgres.rls`);
	assert.deepEqual(snapshot.postgres.rls, {requiredTables: REQUIRED_RLS_TABLES, allEnabled: true, anyForced: false,
		policyCount: 0, allOwnedByCurrentUser: true}, `${label}.postgres.rls must preserve owner JDBC behavior.`);
	object(snapshot.redis, `${label}.redis`);
	assert.deepEqual(Object.keys(snapshot.redis).sort(), ['runId', 'serverPort', 'serverVersion', 'uptimeSeconds']);
	assert.match(snapshot.redis.runId, /^[a-f0-9]{40}$/);
	string(snapshot.redis.serverVersion, `${label}.redis.serverVersion`);
	assert.ok(Number.isInteger(snapshot.redis.serverPort) && snapshot.redis.serverPort > 0);
	decimal(snapshot.redis.uptimeSeconds, `${label}.redis.uptimeSeconds`);
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
		const fields = CONTAINER_IDENTITY_FIELDS;
		for (const field of fields) {
			compare(failures, `containers.${component}.${field}`, initial.containers[component][field], before.containers[component][field]);
			compare(failures, `containers.${component}.${field}`, initial.containers[component][field], after.containers[component][field]);
		}
	}
	for (const field of POSTGRES_IDENTITY_FIELDS) {
		compare(failures, `postgres.${field}`, initial.postgres[field], before.postgres[field]);
		compare(failures, `postgres.${field}`, initial.postgres[field], after.postgres[field]);
	}
	for (const field of ['runId', 'serverVersion', 'serverPort']) {
		compare(failures, `redis.${field}`, initial.redis[field], before.redis[field]);
		compare(failures, `redis.${field}`, initial.redis[field], after.redis[field]);
	}
	const initialUptime = BigInt(initial.redis.uptimeSeconds);
	const beforeUptime = BigInt(before.redis.uptimeSeconds);
	const afterUptime = BigInt(after.redis.uptimeSeconds);
	if (beforeUptime < initialUptime || afterUptime < beforeUptime) {
		failures.push({name: 'redis.uptimeSeconds', expected: 'monotonic', actual: {initial: initial.redis.uptimeSeconds,
			before: before.redis.uptimeSeconds, after: after.redis.uptimeSeconds}});
	}
	return {
		status: failures.length === 0 ? 'continuous' : 'runtime-identity-changed',
		continuous: failures.length === 0,
		automaticAdoption: false,
		identityFields: {
			containers: {app: CONTAINER_IDENTITY_FIELDS, postgres: CONTAINER_IDENTITY_FIELDS, redis: CONTAINER_IDENTITY_FIELDS},
			postgres: POSTGRES_IDENTITY_FIELDS,
			redis: ['runId', 'serverVersion', 'serverPort', 'uptimeSeconds'],
		},
		failures,
	};
}

function compare(failures, name, expected, actual) {
	if (!deepEqual(expected, actual)) failures.push({name, expected, actual});
}

function deepEqual(left, right) {
	try {
		assert.deepEqual(left, right);
		return true;
	} catch {
		return false;
	}
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

function decimal(value, label) {
	assert.ok(typeof value === 'string' && /^(?:0|[1-9]\d*)$/.test(value), `${label} must be a decimal string.`);
}
