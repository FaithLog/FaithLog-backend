import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [identityPath, resourcePath, windowsPath, scenario, caseName, outputPath] = process.argv.slice(2);
if (!identityPath || !resourcePath || !windowsPath || !scenario || !caseName || !outputPath) {
	throw new Error('identityPath, resourcePath, windowsPath, scenario, caseName, and outputPath are required.');
}

const identity = readJson(identityPath);
const snapshots = readNdjson(resourcePath);
const windows = readNdjson(windowsPath).filter((row) => row.scenario === scenario && row.case === caseName);
const failures = [];
const expectedContainers = new Map([
	['app', identity?.app],
	['postgres', identity?.postgres],
]);

if (snapshots.length !== 2) failures.push('exactly two resource snapshots are required');
const byPhase = new Map();
for (const snapshot of snapshots) {
	validateSnapshot(snapshot);
	if (typeof snapshot?.phase === 'string') {
		if (byPhase.has(snapshot.phase)) failures.push(`duplicate ${snapshot.phase} resource snapshot`);
		byPhase.set(snapshot.phase, snapshot);
	}
}
for (const phase of ['before', 'after']) {
	if (!byPhase.has(phase)) failures.push(`${phase} resource snapshot is missing`);
}

const byEvent = new Map();
for (const window of windows) {
	if (!isObject(window)) {
		failures.push('case window must be an object');
		continue;
	}
	if (!isDeepStrictEqual(Object.keys(window).sort(), ['at', 'case', 'event', 'scenario', 'status'])) {
		failures.push('case window schema is incomplete or unexpected');
	}
	if (!['measured-start', 'measured-end'].includes(window.event)) continue;
	if (byEvent.has(window.event)) failures.push(`duplicate ${window.event} case window`);
	byEvent.set(window.event, window);
}
const start = byEvent.get('measured-start');
const end = byEvent.get('measured-end');
if (!start || !end) failures.push('exactly one measured-start and measured-end window are required');
if (start?.status !== 'pending') failures.push('measured-start status must be pending');
if (end?.status !== 'passed') failures.push('measured-end status must be passed');

const beforeAt = timestamp(byPhase.get('before')?.capturedAt, 'before.capturedAt');
const startAt = timestamp(start?.at, 'measured-start.at');
const endAt = timestamp(end?.at, 'measured-end.at');
const afterAt = timestamp(byPhase.get('after')?.capturedAt, 'after.capturedAt');
if ([beforeAt, startAt, endAt, afterAt].every((value) => value !== null)
	&& !(beforeAt < startAt && startAt <= endAt && endAt < afterAt)) {
	failures.push('resource/case timestamps must satisfy before < measured-start <= measured-end < after');
}

const result = {
	status: failures.length === 0 ? 'adoptable' : 'non-adoptable',
	coverage: 'boundary-only',
	scenario,
	case: caseName,
	failures,
	meaning: 'two validated boundary observations; not continuous or peak CPU/RAM coverage',
};
fs.writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`);
if (failures.length > 0) throw new Error(`Resource snapshots are non-adoptable: ${failures.join('; ')}`);

function validateSnapshot(snapshot) {
	if (!isObject(snapshot)) {
		failures.push('resource snapshot must be an object');
		return;
	}
	if (!isDeepStrictEqual(
		Object.keys(snapshot).sort(),
		['capturedAt', 'case', 'containers', 'coverage', 'phase', 'scenario'],
	)) failures.push('resource snapshot schema is incomplete or unexpected');
	if (snapshot.scenario !== scenario || snapshot.case !== caseName) failures.push('resource snapshot case identity mismatch');
	if (!['before', 'after'].includes(snapshot.phase)) failures.push('resource snapshot phase is invalid');
	if (snapshot.coverage !== 'boundary-only') failures.push('resource snapshot coverage must be boundary-only');
	timestamp(snapshot.capturedAt, 'resource capturedAt');
	if (!Array.isArray(snapshot.containers) || snapshot.containers.length !== 2) {
		failures.push('resource snapshot must contain exactly app and postgres');
		return;
	}
	const roles = new Set();
	for (const container of snapshot.containers) {
		if (!isObject(container)) {
			failures.push('resource container must be an object');
			continue;
		}
		if (!isDeepStrictEqual(
			Object.keys(container).sort(),
			['containerId', 'cpuPercent', 'memoryBytes', 'name', 'role'],
		)) failures.push('resource container schema is incomplete or unexpected');
		if (roles.has(container.role)) failures.push(`duplicate resource role ${container.role}`);
		roles.add(container.role);
		const expected = expectedContainers.get(container.role);
		if (!expected) {
			failures.push(`unexpected resource role ${container.role}`);
		} else if (container.containerId !== expected.containerId || container.name !== expected.name) {
			failures.push(`${container.role} resource identity does not match initial runtime identity`);
		}
		if (typeof container.cpuPercent !== 'number' || !Number.isFinite(container.cpuPercent) || container.cpuPercent < 0) {
			failures.push(`${container.role}.cpuPercent must be a finite non-negative number`);
		}
		if (!Number.isSafeInteger(container.memoryBytes) || container.memoryBytes < 0) {
			failures.push(`${container.role}.memoryBytes must be a non-negative safe integer`);
		}
	}
	if (!roles.has('app') || !roles.has('postgres')) failures.push('resource snapshot roles must be exactly app and postgres');
}

function timestamp(value, label) {
	if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) {
		failures.push(`${label} must be an ISO timestamp`);
		return null;
	}
	return Date.parse(value);
}

function readJson(filePath) {
	return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function readNdjson(filePath) {
	return fs.readFileSync(filePath, 'utf8').split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
}

function isObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}
