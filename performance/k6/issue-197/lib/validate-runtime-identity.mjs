import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const CHECKPOINTS = ['warmupBefore', 'measuredBefore', 'measuredAfter', 'final'];
const PAIR_CHECKPOINTS = [...CHECKPOINTS, 'retentionAfter'];
const ROOT_KEYS = ['app', 'databaseContainer', 'databaseServer', 'redisContainer', 'redisServer'].sort();
const APP_KEYS = ['apiContractSha256', 'composeProject', 'composeService', 'containerId', 'imageId', 'jarSha256', 'publishedPort', 'revision', 'startedAt'].sort();
const CONTAINER_KEYS = ['composeProject', 'composeService', 'containerId', 'imageId', 'startedAt'].sort();
const DB_SERVER_KEYS = ['currentDatabase', 'flywayChecksum', 'flywayScript', 'flywayVersion', 'postmasterStartTime', 'serverAddress', 'serverPort'].sort();
const REDIS_SERVER_KEYS = ['redisVersion', 'runId', 'tcpPort'].sort();

export function validateRuntimeIdentity(identity) {
	assertExactKeys(identity, ROOT_KEYS, 'runtime identity');
	assertExactKeys(identity.app, APP_KEYS, 'runtime identity app');
	assertExactKeys(identity.databaseContainer, CONTAINER_KEYS, 'runtime identity databaseContainer');
	assertExactKeys(identity.redisContainer, CONTAINER_KEYS, 'runtime identity redisContainer');
	assertExactKeys(identity.databaseServer, DB_SERVER_KEYS, 'runtime identity databaseServer');
	assertExactKeys(identity.redisServer, REDIS_SERVER_KEYS, 'runtime identity redisServer');
	for (const section of [identity.app, identity.databaseContainer, identity.redisContainer]) {
		fullContainerId(section.containerId, 'containerId');
		dockerImageId(section.imageId, 'imageId');
		for (const field of ['composeProject', 'composeService']) nonEmptyString(section[field], field);
		strictTimestamp(section.startedAt, 'startedAt');
	}
	for (const field of ['revision', 'jarSha256', 'apiContractSha256']) nonEmptyString(identity.app[field], `app.${field}`);
	assert.equal(new Set([identity.app.containerId, identity.databaseContainer.containerId, identity.redisContainer.containerId]).size, 3,
		'app, database, and Redis full Docker container IDs must be distinct');
	positivePort(identity.app.publishedPort, 'app.publishedPort');
	for (const field of ['currentDatabase', 'serverAddress', 'flywayVersion', 'flywayScript']) nonEmptyString(identity.databaseServer[field], `databaseServer.${field}`);
	assert.match(identity.databaseServer.flywayChecksum, /^-?\d+$/, 'databaseServer.flywayChecksum must be a signed decimal string');
	positivePort(identity.databaseServer.serverPort, 'databaseServer.serverPort');
	strictTimestamp(identity.databaseServer.postmasterStartTime, 'databaseServer.postmasterStartTime');
	assert.match(identity.redisServer.runId, /^[a-f0-9]{40}$/, 'redisServer.runId must be a 40-character Redis run ID');
	nonEmptyString(identity.redisServer.redisVersion, 'redisServer.redisVersion');
	positivePort(identity.redisServer.tcpPort, 'redisServer.tcpPort');
	return identity;
}

export function validateExpectedFlywayIdentity(expected, actual) {
	for (const field of ['version', 'script']) {
		nonEmptyString(expected[field], `expected Flyway ${field}`);
		nonEmptyString(actual[field], `actual Flyway ${field}`);
	}
	for (const [label, value] of [['expected Flyway checksum', expected.checksum], ['actual Flyway checksum', actual.checksum]]) {
		assert.equal(typeof value, 'string', `${label} must be a string`);
		assert.match(value, /^-?\d+$/, `${label} must be a signed decimal string`);
	}
	for (const field of ['version', 'script', 'checksum']) {
		assert.equal(actual[field], expected[field], `actual Flyway ${field} must match the runtime-approved identity`);
	}
	return actual;
}

export function validateRuntimeIdentitySeries(initial, checkpoints) {
	validateRuntimeIdentity(initial);
	assertExactKeys(checkpoints, [...CHECKPOINTS].sort(), 'runtime identity checkpoints');
	const failures = [];
	for (const checkpoint of CHECKPOINTS) {
		validateRuntimeIdentity(checkpoints[checkpoint]);
		compareExact(initial, checkpoints[checkpoint], checkpoint, failures);
	}
	return {
		status: failures.length === 0 ? 'continuous' : 'replaced',
		adoptable: failures.length === 0,
		initial,
		checkpoints,
		failures,
	};
}

function compareExact(expected, actual, prefix, failures) {
	for (const section of ROOT_KEYS) {
		for (const field of Object.keys(expected[section]).sort()) {
			if (actual[section][field] !== expected[section][field]) {
				failures.push({ name: `${prefix}.${section}.${field}`, expected: expected[section][field], actual: actual[section][field] });
			}
		}
	}
}

function captureFromEnvironment(databaseServer, redisServer) {
	validateExpectedFlywayIdentity({
		version: process.env.EXPECTED_FLYWAY_VERSION,
		script: process.env.EXPECTED_FLYWAY_SCRIPT,
		checksum: process.env.EXPECTED_FLYWAY_CHECKSUM,
	}, {
		version: databaseServer.flywayVersion,
		script: databaseServer.flywayScript,
		checksum: databaseServer.flywayChecksum,
	});
	assert.equal(databaseServer.serverPort, runtimePort(process.env.EXPECTED_DB_PORT, 'EXPECTED_DB_PORT'),
		'PostgreSQL server port must match the runtime-approved target');
	assert.equal(databaseServer.serverAddress, process.env.DB_HOST,
		'PostgreSQL server address must match the explicit runtime-approved target');
	assert.equal(redisServer.tcpPort, runtimePort(process.env.EXPECTED_REDIS_PORT, 'EXPECTED_REDIS_PORT'),
		'Redis server port must match the runtime-approved target');
	const identity = {
		app: {
			containerId: process.env.APP_CONTAINER_ID,
			imageId: process.env.APP_IMAGE_ID,
			startedAt: process.env.APP_STARTED_AT,
			composeProject: process.env.APP_COMPOSE_PROJECT,
			composeService: process.env.APP_COMPOSE_SERVICE,
			publishedPort: Number(process.env.APP_PUBLISHED_PORT),
			revision: process.env.APP_REVISION,
			jarSha256: process.env.APP_JAR_SHA256,
			apiContractSha256: process.env.APP_API_CONTRACT_SHA256,
		},
		databaseContainer: {
			containerId: process.env.DB_CONTAINER_ID,
			imageId: process.env.DB_IMAGE_ID,
			startedAt: process.env.DB_STARTED_AT,
			composeProject: process.env.DB_COMPOSE_PROJECT,
			composeService: process.env.DB_COMPOSE_SERVICE,
		},
		redisContainer: {
			containerId: process.env.REDIS_CONTAINER_ID,
			imageId: process.env.REDIS_IMAGE_ID,
			startedAt: process.env.REDIS_STARTED_AT,
			composeProject: process.env.REDIS_COMPOSE_PROJECT,
			composeService: process.env.REDIS_COMPOSE_SERVICE,
		},
		databaseServer,
		redisServer,
	};
	return validateRuntimeIdentity(identity);
}

function readRedisServer(filePath) {
	const values = new Map(fs.readFileSync(filePath, 'utf8').split(/\r?\n/)
		.filter((line) => line && !line.startsWith('#') && line.includes(':'))
		.map((line) => [line.slice(0, line.indexOf(':')), line.slice(line.indexOf(':') + 1)]));
	return {
		runId: values.get('run_id'),
		redisVersion: values.get('redis_version'),
		tcpPort: Number(values.get('tcp_port')),
	};
}

function readJson(filePath) {
	return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeSecureJson(filePath, value) {
	fs.mkdirSync(path.dirname(filePath), { recursive: true });
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}

function assertExactKeys(value, expectedKeys, label) {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${label} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), expectedKeys, `${label} must have exact keys`);
}

function nonEmptyString(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.ok(value.length > 0, `${label} must not be empty`);
}

function fullContainerId(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.match(value, /^[a-f0-9]{64}$/, `${label} must be a full Docker container ID`);
}

function dockerImageId(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.match(value, /^sha256:[a-f0-9]{64}$/, `${label} must be a full Docker image ID`);
}

function positivePort(value, label) {
	assert.ok(Number.isInteger(value) && value > 0 && value <= 65535, `${label} must be an integer port`);
}

function runtimePort(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.match(value, /^[1-9]\d*$/, `${label} must be a decimal port string`);
	const port = Number(value);
	positivePort(port, label);
	return port;
}

function strictTimestamp(value, label) {
	assert.equal(typeof value, 'string', `${label} must be an ISO timestamp string`);
	const parsed = Date.parse(value);
	assert.ok(
		Number.isFinite(parsed) && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value),
		`${label} must be a valid ISO timestamp`
	);
}

async function main() {
	const [command, ...args] = process.argv.slice(2);
	if (command === 'capture') {
		const [databaseServerPath, redisServerPath, outputPath] = args;
		writeSecureJson(outputPath, captureFromEnvironment(readJson(databaseServerPath), readRedisServer(redisServerPath)));
		return;
	}
	if (command === 'field') {
		const [identityPath, section, field] = args;
		const identity = validateRuntimeIdentity(readJson(identityPath));
		process.stdout.write(String(identity[section][field]));
		return;
	}
	if (command === 'validate-pair') {
		const [initialPath, candidatePath, checkpoint, outputPath] = args;
		assert.ok(PAIR_CHECKPOINTS.includes(checkpoint), 'runtime identity checkpoint is invalid');
		const initial = validateRuntimeIdentity(readJson(initialPath));
		const candidate = validateRuntimeIdentity(readJson(candidatePath));
		const failures = [];
		compareExact(initial, candidate, checkpoint, failures);
		const pairEvidence = { status: failures.length === 0 ? 'continuous' : 'replaced', adoptable: failures.length === 0, checkpoint, initial, candidate, failures };
		writeSecureJson(outputPath, pairEvidence);
		if (!pairEvidence.adoptable) throw new Error(`runtime identity changed at ${checkpoint}: ${JSON.stringify(pairEvidence.failures)}`);
		return;
	}
	if (command === 'validate-series') {
		const [initialPath, warmupPath, measuredBeforePath, measuredAfterPath, finalPath, outputPath] = args;
		const evidence = validateRuntimeIdentitySeries(readJson(initialPath), {
			warmupBefore: readJson(warmupPath), measuredBefore: readJson(measuredBeforePath),
			measuredAfter: readJson(measuredAfterPath), final: readJson(finalPath),
		});
		writeSecureJson(outputPath, evidence);
		if (!evidence.adoptable) throw new Error(`runtime identity changed: ${JSON.stringify(evidence.failures)}`);
		process.stdout.write(`${JSON.stringify(evidence)}\n`);
		return;
	}
	throw new Error(`Unknown runtime identity command: ${command}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
