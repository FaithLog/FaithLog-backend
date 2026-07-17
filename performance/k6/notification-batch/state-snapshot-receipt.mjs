import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, statSync, writeFileSync } from 'node:fs';
import { validateSnapshotReceipt } from './snapshot-contract.mjs';

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));
const sha256File = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');
const sha256Json = (value) => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const writeExclusive = (path, value) => writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, {
	flag: 'wx', mode: 0o600,
});

const mode = process.argv[2];
if (mode === 'capture') {
	const postgresFingerprint = readJson(process.env.PERF_POSTGRES_FINGERPRINT_PATH);
	const redisFingerprint = readJson(process.env.PERF_REDIS_FINGERPRINT_PATH);
	const dumpPath = process.env.PERF_POSTGRES_DUMP_PATH;
	const receipt = {
		schemaVersion: 1,
		snapshotId: process.env.PERF_SNAPSHOT_ID,
		composeProject: process.env.PERF_COMPOSE_PROJECT,
		postgres: {
			database: process.env.POSTGRES_DB,
			dumpSha256: sha256File(dumpPath),
			dumpBytes: String(statSync(dumpPath).size),
			cardinality: postgresFingerprint.cardinality,
			stateSha256: sha256Json(postgresFingerprint),
		},
		redis: {
			database: Number(process.env.PERF_REDIS_DATABASE),
			snapshotDatabase: Number(process.env.PERF_REDIS_SNAPSHOT_DATABASE),
			keyCount: String(redisFingerprint.keyCount),
			stateSha1: redisFingerprint.stateSha1,
			ttlIntentSha1: redisFingerprint.ttlIntentSha1,
		},
		credentialRecorded: false,
		automaticAdoption: false,
	};
	validateSnapshotReceipt(receipt);
	writeExclusive(process.env.PERF_SNAPSHOT_RECEIPT_PATH, receipt);
} else if (mode === 'restore') {
	const snapshot = validateSnapshotReceipt(readJson(process.env.PERF_SNAPSHOT_RECEIPT_PATH));
	const postgresFingerprint = readJson(process.env.PERF_POSTGRES_FINGERPRINT_PATH);
	const redisFingerprint = readJson(process.env.PERF_REDIS_FINGERPRINT_PATH);
	const redisRestoreMetadata = readJson(process.env.PERF_REDIS_RESTORE_METADATA_PATH);
	const actualPostgres = {
		database: process.env.POSTGRES_DB,
		dumpSha256: snapshot.postgres.dumpSha256,
		cardinality: postgresFingerprint.cardinality,
		stateSha256: sha256Json(postgresFingerprint),
	};
	const actualRedis = {
		database: Number(process.env.PERF_REDIS_DATABASE),
		snapshotDatabase: Number(process.env.PERF_REDIS_SNAPSHOT_DATABASE),
		keyCount: String(redisFingerprint.keyCount),
		stateSha1: redisFingerprint.stateSha1,
		ttlIntentSha1: redisRestoreMetadata.ttlIntentSha1,
	};
	assert.equal(String(redisRestoreMetadata.keyCount), actualRedis.keyCount,
		'Redis restored key cardinality does not match live state');
	assert.equal(redisRestoreMetadata.stateSha1, actualRedis.stateSha1,
		'Redis restore payload hash does not match live state');
	assert.deepEqual(actualPostgres, {
		database: snapshot.postgres.database,
		dumpSha256: snapshot.postgres.dumpSha256,
		cardinality: snapshot.postgres.cardinality,
		stateSha256: snapshot.postgres.stateSha256,
	}, 'PostgreSQL restore fingerprint drifted');
	assert.deepEqual(actualRedis, snapshot.redis, 'Redis restore fingerprint drifted');
	writeExclusive(process.env.PERF_RESTORE_RECEIPT_PATH, {
		schemaVersion: 1,
		snapshotId: snapshot.snapshotId,
		snapshotReceiptSha256: sha256File(process.env.PERF_SNAPSHOT_RECEIPT_PATH),
		composeProject: snapshot.composeProject,
		restoreOrdinal: Number(process.env.PERF_RESTORE_ORDINAL),
		sampleKind: process.env.PERF_SAMPLE_KIND,
		sampleIndex: Number(process.env.PERF_SAMPLE_INDEX),
		postgres: actualPostgres,
		redis: actualRedis,
		credentialRecorded: false,
		automaticAdoption: false,
	});
} else {
	throw new Error('state-snapshot-receipt mode must be capture or restore');
}
