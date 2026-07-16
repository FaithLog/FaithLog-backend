import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const [manifestPath, rawModes, nowSource, warmupVusSource, warmupDuration, measuredVusSource, measuredDuration, safetySource] = process.argv.slice(2);
const CURRENT_DEVELOP_BASE = '6796ed146244d8f3f5b5dd7048ebe16865084a97';

try {
	assert.ok(manifestPath, 'INPUT_MANIFEST is required.');
	assert.ok(rawModes, 'DATASET_MODES is required.');
	const now = integer(nowSource, 'Current epoch seconds', 1);
	const warmupVus = integer(warmupVusSource, 'WARMUP_VUS', 1);
	const measuredVus = integer(measuredVusSource, 'MEASURED_VUS', 1);
	const safetySeconds = integer(safetySource, 'FIXTURE_EXPIRY_SAFETY_SECONDS', 0);
	assert.ok(warmupVus > 0 && measuredVus > 0, 'Warmup and measured VUS must be positive safe integers.');
	const warmupSeconds = durationSeconds(warmupDuration, 'WARMUP_DURATION');
	const measuredSeconds = durationSeconds(measuredDuration, 'MEASURED_DURATION');
	const parts = rawModes.split(',');
	assert.ok(parts.every((part) => part.trim()), 'DATASET_MODES must not contain empty entries.');
	const modes = parts.map((part) => part.trim());
	assert.equal(new Set(modes).size, modes.length, 'Duplicate dataset mode is not allowed.');
	for (const mode of modes) {
		assert.ok(['empty', 'small', 'thousand'].includes(mode), `Unsupported dataset mode: ${mode}`);
	}

	const manifest = JSON.parse(fs.readFileSync(path.resolve(manifestPath), 'utf8'));
	assert.notEqual(manifest.exampleOnly, true, 'Example-only manifest cannot be used for a baseline run.');
	assert.equal(manifest.schemaVersion, 2, 'Manifest schemaVersion must be 2.');
	assert.equal(manifest.issue, 199, 'Manifest issue must be 199.');
	assert.equal(manifest.currentDevelopBase, CURRENT_DEVELOP_BASE, 'Manifest currentDevelopBase is stale.');
	segment(manifest.datasetId, 'datasetId');
	assert.ok(manifest.fixtureNamespace && typeof manifest.fixtureNamespace === 'object', 'fixtureNamespace is required.');
	segment(manifest.fixtureNamespace.namespaceId, 'fixtureNamespace.namespaceId');
	assert.equal(manifest.fixtureNamespace.immutable, true, 'fixtureNamespace must be immutable.');
	const preparedAt = timestamp(manifest.fixtureNamespace.preparedAt, 'fixtureNamespace.preparedAt');
	const expiresAt = timestamp(manifest.fixtureNamespace.expiresAt, 'fixtureNamespace.expiresAt');
	assert.ok(preparedAt <= now * 1000, 'Fixture namespace cannot be prepared in the future.');
	const requiredEnd = now + Math.ceil(modes.length * (warmupSeconds + measuredSeconds)) + safetySeconds;
	assert.ok(requiredEnd * 1000 <= expiresAt, 'Fixture namespace validity cannot cover the approved run window.');
	const fixtureRunIds = [];
	for (const mode of modes) {
		const fixture = manifest.modes?.[mode];
		assert.ok(fixture, `Missing dataset mode in manifest: ${mode}`);
		assert.equal(fixture.mode, mode, `Dataset mode self-identity mismatch: ${mode}`);
		segment(fixture.fixtureRunId, `${mode}.fixtureRunId`);
		fixtureRunIds.push(fixture.fixtureRunId);
		assert.ok(Number.isSafeInteger(fixture.campusId) && Number.isSafeInteger(fixture.isolationCampusId), `${mode} campus IDs are required.`);
		assert.notEqual(fixture.campusId, fixture.isolationCampusId, `${mode} isolation campus must differ.`);
	}
	assert.equal(new Set(fixtureRunIds).size, fixtureRunIds.length, 'Selected dataset modes must have unique fixtureRunId values.');
	for (const component of ['app', 'postgres', 'redis']) {
		assert.match(
			manifest.runtimeTarget?.[component]?.service || '',
			/^[A-Za-z0-9._-]+$/,
			`runtimeTarget.${component}.service is required and must be a safe exact label.`,
		);
		assert.match(manifest.runtimeTarget?.[component]?.imageId || '', /^sha256:[a-f0-9]{64}$/,
			`runtimeTarget.${component}.imageId must be an approved full Docker image ID.`);
		assert.ok(typeof manifest.runtimeTarget?.[component]?.imageRef === 'string' && manifest.runtimeTarget[component].imageRef.length > 0,
			`runtimeTarget.${component}.imageRef is required.`);
	}
	assert.ok(
		Number.isInteger(manifest.runtimeTarget?.app?.containerPort)
			&& manifest.runtimeTarget.app.containerPort > 0
			&& manifest.runtimeTarget.app.containerPort <= 65535,
		'runtimeTarget.app.containerPort must be an integer from 1 to 65535.',
	);
	for (const component of ['postgres', 'redis']) {
		assert.ok(Number.isInteger(manifest.runtimeTarget[component].containerPort)
			&& manifest.runtimeTarget[component].containerPort > 0 && manifest.runtimeTarget[component].containerPort <= 65535,
		`runtimeTarget.${component}.containerPort must be an integer from 1 to 65535.`);
	}
} catch (error) {
	process.stderr.write(`${JSON.stringify({status: 'rejected', automaticAdoption: false, failures: [{name: 'runInput', actual: error.message}]})}\n`);
	process.exitCode = 1;
}

function integer(value, label, minimum) {
	assert.ok(typeof value === 'string' && /^(?:0|[1-9]\d*)$/.test(value), `${label} must be a safe integer.`);
	const parsed = Number(value);
	assert.ok(Number.isSafeInteger(parsed) && parsed >= minimum, `${label} must be at least ${minimum}.`);
	return parsed;
}

function durationSeconds(value, label) {
	assert.ok(typeof value === 'string' && value.length > 0, `${label} is required.`);
	const units = {ms: 0.001, s: 1, m: 60, h: 3600, d: 86400};
	const pattern = /(\d+(?:\.\d+)?)(ms|s|m|h|d)/gy;
	let total = 0;
	let consumed = 0;
	for (let match = pattern.exec(value); match; match = pattern.exec(value)) {
		total += Number(match[1]) * units[match[2]];
		consumed = pattern.lastIndex;
	}
	assert.equal(consumed, value.length, `${label} must use supported k6 duration syntax.`);
	assert.ok(Number.isFinite(total) && total > 0, `${label} must be positive.`);
	return total;
}

function timestamp(value, label) {
	assert.ok(typeof value === 'string' && Number.isFinite(Date.parse(value)), `${label} must be an ISO timestamp.`);
	return Date.parse(value);
}

function segment(value, label) {
	assert.match(value || '', /^[A-Za-z0-9._-]+$/, `${label} must be a safe non-empty segment.`);
}
