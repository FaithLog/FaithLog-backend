import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const runDir = process.env.RUN_DIR;
assert.ok(runDir, 'RUN_DIR is required');
const phases = (process.env.RUNTIME_IDENTITY_PHASES ?? 'locked,initial,before,after,final')
	.split(',')
	.map((phase) => phase.trim())
	.filter(Boolean);
assert.ok(phases.length >= 2, 'At least two runtime identity phases are required');
assert.equal(new Set(phases).size, phases.length, 'Runtime identity phases must be unique');
const identities = phases.map((phase) => JSON.parse(
	readFileSync(join(runDir, `runtime-identity-${phase}.json`), 'utf8'),
));
const exactKeys = (value, expected, path) => {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${path} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), [...expected].sort(), `${path} schema mismatch`);
};
const containerKeys = [
	'name', 'id', 'imageId', 'startedAt', 'composeProject', 'composeService', 'composeConfigHash', 'hostPort',
];
for (const [index, identity] of identities.entries()) {
	exactKeys(identity, ['capturedAt', 'postgres', 'redis'], `${phases[index]} identity`);
	assert.ok(Number.isFinite(Date.parse(identity.capturedAt)), `${phases[index]} capturedAt is invalid`);
	exactKeys(identity.postgres, ['container', 'server'], `${phases[index]}.postgres`);
	exactKeys(identity.redis, ['container', 'server'], `${phases[index]}.redis`);
	exactKeys(identity.postgres.container, containerKeys, `${phases[index]}.postgres.container`);
	exactKeys(identity.redis.container, containerKeys, `${phases[index]}.redis.container`);
	exactKeys(identity.postgres.server,
		['database', 'address', 'port', 'postmasterStartTime'], `${phases[index]}.postgres.server`);
	exactKeys(identity.redis.server, ['runId', 'uptimeSeconds', 'port'], `${phases[index]}.redis.server`);
	assert.ok(Number.isSafeInteger(identity.postgres.container.hostPort)
		&& identity.postgres.container.hostPort > 0, `${phases[index]} PostgreSQL host port must be positive`);
	assert.ok(Number.isSafeInteger(identity.redis.container.hostPort)
		&& identity.redis.container.hostPort > 0, `${phases[index]} Redis host port must be positive`);
	assert.ok(Number.isSafeInteger(identity.redis.server.uptimeSeconds)
		&& identity.redis.server.uptimeSeconds > 0, `${phases[index]} Redis uptime must be positive`);
	if (index > 0) {
		assert.ok(Date.parse(identity.capturedAt) > Date.parse(identities[index - 1].capturedAt),
			'Runtime identity capture timestamps must be strictly increasing');
		assert.ok(identity.redis.server.uptimeSeconds >= identities[index - 1].redis.server.uptimeSeconds,
			'Redis uptime must be monotonic');
	}
}

const initial = identities[0];
for (const [index, identity] of identities.slice(1).entries()) {
	const phase = phases[index + 1];
	assert.deepEqual(identity.postgres.container, initial.postgres.container,
		`PostgreSQL immutable container identity changed at ${phase}`);
	assert.deepEqual(identity.redis.container, initial.redis.container,
		`Redis immutable container identity changed at ${phase}`);
	assert.deepEqual(identity.postgres.server, initial.postgres.server,
		`PostgreSQL server identity changed at ${phase}`);
	assert.equal(identity.redis.server.runId, initial.redis.server.runId,
		`Redis run_id changed at ${phase}`);
	assert.equal(identity.redis.server.port, initial.redis.server.port,
		`Redis server endpoint changed at ${phase}`);
}

writeFileSync(join(runDir, 'runtime-continuity-report.json'), `${JSON.stringify({
	status: 'verified',
	phases,
	postgresContainerId: initial.postgres.container.id,
	postgresPostmasterStartTime: initial.postgres.server.postmasterStartTime,
	redisContainerId: initial.redis.container.id,
	redisRunId: initial.redis.server.runId,
	checkedAt: new Date().toISOString(),
}, null, 2)}\n`);
