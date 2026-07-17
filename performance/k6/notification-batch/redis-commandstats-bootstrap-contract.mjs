import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { realpathSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const DECIMAL = /^(0|[1-9][0-9]*)$/;
const exactObject = (value, keys) => {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value));
	assert.deepEqual(Object.keys(value).sort(), [...keys].sort());
};

export function validateRedisCommandstatsBootstrapReceipt(value) {
	exactObject(value, [
		'schemaVersion', 'composeProject', 'redisContainerId', 'database', 'bootstrapKeySha256',
		'setCommandsExecuted', 'delCommandsExecuted', 'dbSizeAfter', 'commandstatsSetCalls',
		'commandstatsDelCalls', 'credentialRecorded', 'automaticAdoption',
	]);
	assert.equal(value.schemaVersion, 1);
	assert.match(value.composeProject, /^faithlog-perf-198(?:$|-[A-Za-z0-9_-]+)$/);
	assert.match(value.redisContainerId, /^[a-f0-9]{64}$/);
	assert.ok(Number.isSafeInteger(value.database) && value.database >= 0);
	assert.match(value.bootstrapKeySha256, /^[a-f0-9]{64}$/);
	assert.equal(value.setCommandsExecuted, 1);
	assert.equal(value.delCommandsExecuted, 1);
	for (const field of ['dbSizeAfter', 'commandstatsSetCalls', 'commandstatsDelCalls']) {
		assert.match(value[field], DECIMAL);
	}
	assert.equal(value.dbSizeAfter, '0');
	assert.ok(BigInt(value.commandstatsSetCalls) >= 1n);
	assert.ok(BigInt(value.commandstatsDelCalls) >= 1n);
	assert.equal(value.credentialRecorded, false);
	assert.equal(value.automaticAdoption, false);
	return value;
}

function capture(outputPath) {
	const value = validateRedisCommandstatsBootstrapReceipt({
		schemaVersion: 1,
		composeProject: process.env.PERF_COMPOSE_PROJECT,
		redisContainerId: process.env.PERF_REDIS_CONTAINER_ID,
		database: Number(process.env.PERF_REDIS_DATABASE),
		bootstrapKeySha256: createHash('sha256').update(process.env.PERF_REDIS_BOOTSTRAP_KEY).digest('hex'),
		setCommandsExecuted: 1,
		delCommandsExecuted: 1,
		dbSizeAfter: process.env.PERF_REDIS_BOOTSTRAP_DBSIZE_AFTER,
		commandstatsSetCalls: process.env.PERF_REDIS_BOOTSTRAP_SET_CALLS,
		commandstatsDelCalls: process.env.PERF_REDIS_BOOTSTRAP_DEL_CALLS,
		credentialRecorded: false,
		automaticAdoption: false,
	});
	writeFileSync(outputPath, `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
	const [command, outputPath] = process.argv.slice(2);
	if (command !== 'capture' || !outputPath) throw new Error('Redis bootstrap receipt arguments are required');
	capture(outputPath);
}
