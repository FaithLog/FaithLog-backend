import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const SHA256 = /^[a-f0-9]{64}$/;

const exactObject = (value, keys, name) => {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${name} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), [...keys].sort(), `${name} schema mismatch`);
};

export function validateSeedReceipt(value) {
	exactObject(value, [
		'schemaVersion', 'composeProject', 'postgresDatabase', 'datasetId', 'campusId',
		'activeUserCount', 'activeMemberCount', 'migrationCount', 'migrationContractSha256',
		'datasetStateSha256', 'credentialRecorded', 'externalDataCopied', 'externalFcm',
		'automaticAdoption',
	], 'isolated seed receipt');
	assert.equal(value.schemaVersion, 1);
	assert.match(value.composeProject, /^faithlog-perf-198(?:$|-[A-Za-z0-9_-]+)$/);
	assert.match(value.postgresDatabase, /^[A-Za-z_][A-Za-z0-9_]*$/);
	assert.match(value.datasetId, /^PERFORMANCE_[A-Za-z0-9_-]+$/);
	assert.ok(Number.isSafeInteger(value.campusId) && value.campusId > 0);
	assert.equal(value.activeUserCount, 1000, 'synthetic seed must contain exactly 1000 active users');
	assert.equal(value.activeMemberCount, 1000, 'synthetic seed must contain exactly 1000 active members');
	assert.equal(value.migrationCount, 11, 'synthetic seed must apply exact V1-V11 migrations');
	assert.match(value.migrationContractSha256, SHA256);
	assert.match(value.datasetStateSha256, SHA256);
	assert.equal(value.credentialRecorded, false);
	assert.equal(value.externalDataCopied, false, 'external business data must not be copied');
	assert.equal(value.externalFcm, false, 'external FCM must remain disabled');
	assert.equal(value.automaticAdoption, false);
	return value;
}

function capture(rawPath, contractPath, outputPath) {
	const raw = JSON.parse(readFileSync(rawPath, 'utf8'));
	exactObject(raw, [
		'composeProject', 'postgresDatabase', 'datasetId', 'campusId', 'activeUserCount',
		'activeMemberCount', 'userStateMd5', 'memberStateMd5', 'fcmTokenCount',
		'notificationLogCount',
	], 'isolated seed raw evidence');
	assert.equal(raw.fcmTokenCount, 0);
	assert.equal(raw.notificationLogCount, 0);
	assert.match(raw.userStateMd5, /^[a-f0-9]{32}$/);
	assert.match(raw.memberStateMd5, /^[a-f0-9]{32}$/);
	const contract = JSON.parse(readFileSync(contractPath, 'utf8'));
	assert.equal(Object.keys(contract.flywayMigrations ?? {}).length, 11);
	const migrationContractSha256 = createHash('sha256')
		.update(`${JSON.stringify(contract.flywayMigrations)}\n`).digest('hex');
	const datasetStateSha256 = createHash('sha256').update(`${JSON.stringify(raw)}\n`).digest('hex');
	const receipt = validateSeedReceipt({
		schemaVersion: 1,
		composeProject: raw.composeProject,
		postgresDatabase: raw.postgresDatabase,
		datasetId: raw.datasetId,
		campusId: raw.campusId,
		activeUserCount: raw.activeUserCount,
		activeMemberCount: raw.activeMemberCount,
		migrationCount: 11,
		migrationContractSha256,
		datasetStateSha256,
		credentialRecorded: false,
		externalDataCopied: false,
		externalFcm: false,
		automaticAdoption: false,
	});
	writeFileSync(outputPath, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
	const [command, rawPath, contractPath, outputPath] = process.argv.slice(2);
	if (command !== 'capture' || !outputPath) throw new Error('seed receipt capture arguments are required');
	capture(rawPath, contractPath, outputPath);
}
