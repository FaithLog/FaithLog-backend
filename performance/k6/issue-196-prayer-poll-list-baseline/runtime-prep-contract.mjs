import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { validateProvenanceShape } from './tooling-provenance.mjs';
import { APPROVED_INSTRUMENTATION_ENV } from './runtime-env-attestation.mjs';

const ROOT_KEYS = [
	'contractVersion', 'issue', 'attemptId', 'createdAt', 'attemptReceipt', 'tooling', 'source', 'compose',
	'previousApp', 'instrumentedApp', 'preservedDatabase', 'preservedRedis', 'evidenceLogging',
	'environmentAttestation', 'daemonLogRetention',
];
const CONTAINER_KEYS = [
	'containerName', 'service', 'configuredImage', 'imageId', 'containerId', 'startedAt', 'configHash',
];
const EVIDENCE_LOGGING = Object.freeze({
	sqlLogger: 'DEBUG',
	formatSql: false,
	showSql: false,
	bindLogger: 'OFF',
	extractLogger: 'OFF',
	statementOnlyArtifact: true,
});

export function validateRuntimePrepManifest(manifest, expectations = {}) {
	assertObject(manifest, 'runtime preparation manifest');
	assertExactKeys(manifest, ROOT_KEYS, 'runtime preparation manifest');
	if (manifest.contractVersion !== 1 || manifest.issue !== 196) {
		throw new Error('Unsupported runtime preparation contract.');
	}
	assertAttemptId(manifest.attemptId);
	assertTimestamp(manifest.createdAt, 'createdAt');
	validateAttemptReceipt(manifest.attemptReceipt, manifest.attemptId);
	validateProvenanceShape(manifest.tooling);
	validateSource(manifest.source);
	validateCompose(manifest.compose);
	for (const [name, value] of [
		['previousApp', manifest.previousApp],
		['instrumentedApp', manifest.instrumentedApp],
		['preservedDatabase', manifest.preservedDatabase],
		['preservedRedis', manifest.preservedRedis],
	]) validateContainer(value, name);
	if (!semanticEqual(manifest.attemptReceipt.previousApp, manifest.previousApp)
		|| !semanticEqual(manifest.attemptReceipt.preservedDatabase, manifest.preservedDatabase)
		|| !semanticEqual(manifest.attemptReceipt.preservedRedis, manifest.preservedRedis)
		|| manifest.attemptReceipt.toolingAggregateSha256 !== manifest.tooling.aggregateSha256) {
		throw new Error('Attempt receipt identity does not match the final runtime preparation manifest.');
	}
	validateEvidenceLogging(manifest.evidenceLogging);
	validateEnvironmentAttestation(manifest.environmentAttestation);
	validateDaemonLogRetention(manifest.daemonLogRetention);
	assertNoSensitiveKeys(manifest);

	if (manifest.previousApp.containerId === manifest.instrumentedApp.containerId
		|| manifest.previousApp.startedAt === manifest.instrumentedApp.startedAt
		|| manifest.previousApp.configHash === manifest.instrumentedApp.configHash) {
		throw new Error('Instrumented app must not reuse the previous app identity.');
	}
	for (const field of ['containerName', 'service', 'configuredImage']) {
		if (manifest.previousApp[field] !== manifest.instrumentedApp[field]) {
			throw new Error(`Instrumented app ${field} changed outside the approved app-only recreation.`);
		}
	}
	if (expectations.sourceRevision && manifest.source.revision !== expectations.sourceRevision) {
		throw new Error('Runtime preparation source revision mismatch.');
	}
	if (expectations.composeProject && manifest.compose.project !== expectations.composeProject) {
		throw new Error('Runtime preparation Compose project mismatch.');
	}
	if (expectations.targetPort && manifest.compose.targetPort !== String(expectations.targetPort)) {
		throw new Error('Runtime preparation target port mismatch.');
	}
	if (expectations.expectedDatabase && !semanticEqual(manifest.preservedDatabase, expectations.expectedDatabase)) {
		throw new Error('Runtime preparation database continuity mismatch.');
	}
	if (expectations.expectedRedis && !semanticEqual(manifest.preservedRedis, expectations.expectedRedis)) {
		throw new Error('Runtime preparation Redis continuity mismatch.');
	}
	return manifest;
}

export function validateRuntimePrepRejection(receipt) {
	assertObject(receipt, 'runtime preparation rejection');
	assertExactKeys(receipt, [
		'contractVersion', 'issue', 'attemptId', 'status', 'failedAt', 'stage', 'lifecycleStarted',
		'reusable', 'automaticCleanup', 'previousApp', 'currentApp', 'preservedDatabase', 'preservedRedis',
		'tooling', 'restoreHandoff', 'primaryRejectionReason',
	], 'runtime preparation rejection');
	if (receipt.contractVersion !== 1 || receipt.issue !== 196 || receipt.status !== 'rejected') throw new Error('Unsupported runtime preparation rejection contract.');
	assertAttemptId(receipt.attemptId);
	assertTimestamp(receipt.failedAt, 'failedAt');
	assertNonEmpty(receipt.stage, 'stage');
	if (receipt.lifecycleStarted !== true) throw new Error('Rejected lifecycle receipt must record lifecycleStarted=true.');
	if (receipt.reusable !== false) throw new Error('Rejected runtime preparation attempt must not be reusable.');
	if (receipt.automaticCleanup !== false) throw new Error('Rejected runtime preparation must record automatic cleanup disabled.');
	for (const [name, value] of [
		['previousApp', receipt.previousApp], ['currentApp', receipt.currentApp],
		['preservedDatabase', receipt.preservedDatabase], ['preservedRedis', receipt.preservedRedis],
	]) validateContainer(value, name);
	validateProvenanceShape(receipt.tooling);
	if (receipt.restoreHandoff !== 'recreate-app-from-approved-base-compose-without-runtime-evidence-override') throw new Error('Rejected runtime preparation restore handoff is invalid.');
	assertNonEmpty(receipt.primaryRejectionReason, 'primaryRejectionReason');
	assertNoSensitiveKeys(receipt);
	return receipt;
}

export function assertRuntimePreparationMatches(manifest, runtime) {
	validateRuntimePrepManifest(manifest, {
		sourceRevision: runtime.sourceRevision,
		composeProject: runtime.composeProject,
		targetPort: runtime.targetPort,
	});
	assertRuntimeContainer(manifest.instrumentedApp, runtime, 'app');
	assertRuntimeContainer(manifest.preservedDatabase, runtime, 'db');
	assertRuntimeContainer(manifest.preservedRedis, runtime, 'redis');
	const retention = manifest.daemonLogRetention;
	if (runtime.appLogDriver !== retention.driver
		|| runtime.appLogMaxSize !== retention.maxSize
		|| runtime.appLogMaxFile !== retention.maxFile
		|| runtime.appLogCompress !== String(retention.compress)) {
		throw new Error('Runtime preparation app daemon log retention mismatch.');
	}
	return manifest;
}

function validateDaemonLogRetention(retention) {
	assertObject(retention, 'daemonLogRetention');
	assertExactKeys(retention, ['driver', 'maxSize', 'maxFile', 'compress', 'maximumRetainedBytes'], 'daemonLogRetention');
	if (retention.driver !== 'local' || !/^[1-9][0-9]*[kmg]$/.test(retention.maxSize || '')
		|| !/^[1-9][0-9]*$/.test(retention.maxFile || '') || retention.compress !== true
		|| !/^[1-9][0-9]*$/.test(retention.maximumRetainedBytes || '')) {
		throw new Error('App daemon log retention contract is invalid.');
	}
	const match = /^([1-9][0-9]*)([kmg])$/.exec(retention.maxSize);
	const unit = { k: 1024n, m: 1024n ** 2n, g: 1024n ** 3n }[match[2]];
	const expected = BigInt(match[1]) * unit * BigInt(retention.maxFile);
	if (expected.toString() !== retention.maximumRetainedBytes) {
		throw new Error('App daemon log retention byte bound is inconsistent.');
	}
}

function validateSource(source) {
	assertObject(source, 'source');
	assertExactKeys(source, [
		'revision', 'deployDirectory', 'clean', 'detached', 'committedAt', 'imageCreatedAt',
		'operationalProvenance', 'imageAloneCryptographicProof',
	], 'source');
	assertNonEmpty(source.revision, 'source.revision');
	assertNonEmpty(source.deployDirectory, 'source.deployDirectory');
	if (source.clean !== true || source.detached !== true) throw new Error('Runtime preparation source must be clean and detached.');
	assertTimestamp(source.committedAt, 'source.committedAt');
	assertTimestamp(source.imageCreatedAt, 'source.imageCreatedAt');
	if (Date.parse(source.imageCreatedAt) < Date.parse(source.committedAt)) {
		throw new Error('Runtime image must be created after the approved source commit.');
	}
	if (source.operationalProvenance !== 'clean-detached-checkout-image-created-after-source'
		|| source.imageAloneCryptographicProof !== false) {
		throw new Error('Runtime preparation operational provenance contract mismatch.');
	}
}

function validateCompose(compose) {
	assertObject(compose, 'compose');
	assertExactKeys(compose, ['project', 'workingDirectory', 'configFiles', 'targetPort'], 'compose');
	assertNonEmpty(compose.project, 'compose.project');
	assertNonEmpty(compose.workingDirectory, 'compose.workingDirectory');
	if (!Array.isArray(compose.configFiles) || compose.configFiles.length !== 3
		|| compose.configFiles.some((value) => typeof value !== 'string' || value.length === 0)) {
		throw new Error('Runtime preparation must record the exact three Compose config files.');
	}
	if (!/^[1-9]\d{0,4}$/.test(compose.targetPort) || Number(compose.targetPort) > 65535) {
		throw new Error('Runtime preparation target port is invalid.');
	}
}

function validateAttemptReceipt(receipt, attemptId) {
	assertObject(receipt, 'attemptReceipt');
	assertExactKeys(receipt, [
		'contractVersion', 'issue', 'attemptId', 'status', 'reservedAt', 'reportDirectory', 'reusable',
		'previousApp', 'preservedDatabase', 'preservedRedis', 'toolingAggregateSha256',
	], 'attemptReceipt');
	if (receipt.contractVersion !== 1 || receipt.issue !== 196 || receipt.status !== 'reserved' || receipt.reusable !== false) {
		throw new Error('Attempt receipt contract is invalid.');
	}
	if (receipt.attemptId !== attemptId) throw new Error('Attempt receipt identifier mismatch.');
	assertTimestamp(receipt.reservedAt, 'attemptReceipt.reservedAt');
	assertNonEmpty(receipt.reportDirectory, 'attemptReceipt.reportDirectory');
	if (!receipt.reportDirectory.startsWith('/')) throw new Error('Attempt receipt reportDirectory must be absolute.');
	validateContainer(receipt.previousApp, 'attemptReceipt.previousApp');
	validateContainer(receipt.preservedDatabase, 'attemptReceipt.preservedDatabase');
	validateContainer(receipt.preservedRedis, 'attemptReceipt.preservedRedis');
	if (!/^[a-f0-9]{64}$/.test(receipt.toolingAggregateSha256)) throw new Error('Attempt receipt tooling digest is invalid.');
}

function validateContainer(container, name) {
	assertObject(container, name);
	assertExactKeys(container, CONTAINER_KEYS, name);
	for (const field of CONTAINER_KEYS) assertNonEmpty(container[field], `${name}.${field}`);
	assertTimestamp(container.startedAt, `${name}.startedAt`);
}

function validateEvidenceLogging(logging) {
	assertObject(logging, 'evidenceLogging');
	assertExactKeys(logging, Object.keys(EVIDENCE_LOGGING), 'evidenceLogging');
	for (const [key, expected] of Object.entries(EVIDENCE_LOGGING)) {
		if (logging[key] !== expected) {
			const reason = key === 'bindLogger' ? 'bind logger must be OFF' : `${key} evidence logging mismatch`;
			throw new Error(reason);
		}
	}
}

function validateEnvironmentAttestation(attestation) {
	assertObject(attestation, 'environmentAttestation');
	assertExactKeys(attestation, ['previousSanitizedSha256', 'newSanitizedSha256', 'allowedDelta'], 'environmentAttestation');
	if (!/^[a-f0-9]{64}$/.test(attestation.previousSanitizedSha256)
		|| attestation.previousSanitizedSha256 !== attestation.newSanitizedSha256) {
		throw new Error('Sanitized app environment digest continuity mismatch.');
	}
	if (!semanticEqual(attestation.allowedDelta, [...APPROVED_INSTRUMENTATION_ENV])) {
		throw new Error('Runtime preparation environment delta exceeds the approved instrumentation variables.');
	}
}

function assertRuntimeContainer(expected, runtime, prefix) {
	const mapping = prefix === 'app'
		? { containerName: 'appContainer', service: 'appService', configuredImage: 'appImage', imageId: 'appImageId', containerId: 'appContainerId', startedAt: 'appContainerStartedAt', configHash: 'appConfigHash' }
		: prefix === 'db'
			? { containerName: 'dbContainer', service: 'dbService', configuredImage: 'dbImage', imageId: 'dbImageId', containerId: 'dbContainerId', startedAt: 'dbContainerStartedAt', configHash: 'dbConfigHash' }
			: { containerName: 'redisContainer', service: 'redisService', configuredImage: 'redisImage', imageId: 'redisImageId', containerId: 'redisContainerId', startedAt: 'redisContainerStartedAt', configHash: 'redisConfigHash' };
	for (const [manifestField, runtimeField] of Object.entries(mapping)) {
		if (expected[manifestField] !== runtime[runtimeField]) {
			throw new Error(`Runtime preparation ${prefix} identity mismatch at ${manifestField}.`);
		}
	}
}

function assertExactKeys(value, expected, name) {
	const actual = Object.keys(value).sort();
	const sortedExpected = [...expected].sort();
	if (!semanticEqual(actual, sortedExpected)) throw new Error(`${name} has an invalid exact schema.`);
}

function assertNoSensitiveKeys(value, path = 'manifest') {
	if (!value || typeof value !== 'object') return;
	for (const [key, child] of Object.entries(value)) {
		if (/(?:password|secret|credential|accessToken|refreshToken|jwt)/i.test(key)) {
			throw new Error(`Sensitive field is forbidden in runtime preparation manifest: ${path}.${key}`);
		}
		assertNoSensitiveKeys(child, `${path}.${key}`);
	}
}

function assertObject(value, name) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${name} must be an object.`);
}

function assertNonEmpty(value, name) {
	if (typeof value !== 'string' || value.length === 0) throw new Error(`${name} must be a non-empty string.`);
}

function assertAttemptId(value) {
	if (typeof value !== 'string' || !/^[a-z0-9][a-z0-9_-]{7,31}$/.test(value)) throw new Error('Runtime preparation attemptId is invalid.');
}

function assertTimestamp(value, name) {
	assertNonEmpty(value, name);
	if (!Number.isFinite(Date.parse(value))) throw new Error(`${name} must be a valid timestamp.`);
}

function semanticEqual(left, right) {
	if (Object.is(left, right)) return true;
	if (Array.isArray(left) && Array.isArray(right)) return left.length === right.length && left.every((value, index) => semanticEqual(value, right[index]));
	if (left && right && typeof left === 'object' && typeof right === 'object') {
		const leftKeys = Object.keys(left).sort();
		const rightKeys = Object.keys(right).sort();
		return semanticEqual(leftKeys, rightKeys) && leftKeys.every((key) => semanticEqual(left[key], right[key]));
	}
	return false;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const mode = process.argv[2];
	if (!['--write-from-env', '--write-rejection-from-env'].includes(mode) || !process.argv[3]) {
		throw new Error('Usage: runtime-prep-contract.mjs --write-from-env OUTPUT | --write-rejection-from-env OUTPUT');
	}
	const input = JSON.parse(process.env.RUNTIME_PREP_MANIFEST_JSON || 'null');
	const document = mode === '--write-from-env' ? validateRuntimePrepManifest(input) : validateRuntimePrepRejection(input);
	writeFileSync(process.argv[3], `${JSON.stringify(document, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}
