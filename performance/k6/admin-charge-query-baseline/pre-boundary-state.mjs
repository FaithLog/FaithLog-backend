import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {semanticEqual} from './scenario-definition.mjs';

export const PRE_BOUNDARY_STABILIZATION = Object.freeze({
	intervalSeconds: 1,
	maximumAttempts: 5,
});

export function validateStablePreBoundaryState(first, second, expectedDatabaseName) {
	assertSnapshot(first, 'first', expectedDatabaseName);
	assertSnapshot(second, 'second', expectedDatabaseName);
	const elapsedMs = Date.parse(second.capturedAt) - Date.parse(first.capturedAt);
	if (elapsedMs < PRE_BOUNDARY_STABILIZATION.intervalSeconds * 1000) {
		throw new Error('Pre-boundary maintenance snapshots must be captured at least one second apart.');
	}
	for (const key of ['database', 'plannerSettings', 'tables']) {
		if (!semanticEqual(first[key], second[key])) {
			throw new Error(`Pre-boundary ${key} did not stabilize exactly.`);
		}
	}
	return second;
}

function assertSnapshot(value, label, expectedDatabaseName) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) {
		throw new Error(`${label} pre-boundary snapshot is required.`);
	}
	if (typeof value.capturedAt !== 'string' || !Number.isFinite(Date.parse(value.capturedAt))) {
		throw new Error(`${label} pre-boundary capturedAt is invalid.`);
	}
	if (typeof expectedDatabaseName !== 'string' || expectedDatabaseName.length === 0) {
		throw new Error('Expected PostgreSQL database identity is required.');
	}
	for (const key of ['database', 'plannerSettings', 'tables']) {
		if (!value[key] || typeof value[key] !== 'object' || Array.isArray(value[key])) {
			throw new Error(`${label} pre-boundary ${key} is required.`);
		}
	}
	if (value.database.name !== expectedDatabaseName) {
		throw new Error(`${label} pre-boundary database identity does not match.`);
	}
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const [firstPath, secondPath] = process.argv.slice(2);
	if (!secondPath) {
		throw new Error('Usage: pre-boundary-state.mjs <first-state> <second-state>');
	}
	const [first, second] = await Promise.all(
		[firstPath, secondPath].map(async (file) => JSON.parse(await readFile(file, 'utf8')))
	);
	validateStablePreBoundaryState(first, second, process.env.EXPECTED_DATABASE_NAME);
}
