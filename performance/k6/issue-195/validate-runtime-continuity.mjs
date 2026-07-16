import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [initialPath, observedPath, outputPath] = process.argv.slice(2);
if (!initialPath || !observedPath || !outputPath) {
	throw new Error('initialPath, observedPath, and outputPath are required.');
}

const initial = readJson(initialPath);
const observed = readJson(observedPath);
const failures = [];
validateIdentity(initial, 'initial');
validateIdentity(observed, 'observed');
if (!isDeepStrictEqual(initial, observed)) failures.push('runtime identity changed');

const result = {
	status: failures.length === 0 ? 'continuous' : 'non-adoptable',
	failures,
	identityFields: {
		app: ['containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService', 'publishedPorts'],
		postgres: ['containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService', 'publishedPorts'],
		database: ['name', 'serverAddress', 'serverPort', 'postmasterStartedAt'],
	},
};
fs.writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`);
if (failures.length > 0) throw new Error(`Runtime continuity is non-adoptable: ${failures.join('; ')}`);

function validateIdentity(identity, label) {
	if (!isObject(identity)) {
		failures.push(`${label} identity must be an object`);
		return;
	}
	requireExactKeys(identity, ['app', 'postgres', 'database'], label);
	validateContainer(identity.app, `${label}.app`, true);
	validateContainer(identity.postgres, `${label}.postgres`, true);
	if (!isObject(identity.database)) {
		failures.push(`${label}.database must be an object`);
		return;
	}
	requireExactKeys(identity.database, ['name', 'serverAddress', 'serverPort', 'postmasterStartedAt'], `${label}.database`);
	for (const key of ['name', 'serverAddress', 'postmasterStartedAt']) {
		requireNonEmptyString(identity.database[key], `${label}.database.${key}`);
	}
	if (typeof identity.database.serverPort !== 'number'
		|| !Number.isInteger(identity.database.serverPort)
		|| identity.database.serverPort <= 0) {
		failures.push(`${label}.database.serverPort must be a positive integer`);
	}
}

function validateContainer(container, label, includePublishedPorts) {
	if (!isObject(container)) {
		failures.push(`${label} must be an object`);
		return;
	}
	const keys = ['containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService'];
	if (includePublishedPorts) keys.push('publishedPorts');
	requireExactKeys(container, keys, label);
	for (const key of ['containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService']) {
		requireNonEmptyString(container[key], `${label}.${key}`);
	}
	if (includePublishedPorts && !isObject(container.publishedPorts)) {
		failures.push(`${label}.publishedPorts must be an object`);
	}
}

function requireExactKeys(value, expected, label) {
	if (!isObject(value)) return;
	const actual = Object.keys(value).sort();
	const required = [...expected].sort();
	if (!isDeepStrictEqual(actual, required)) failures.push(`${label} keys are incomplete or unexpected`);
}

function requireNonEmptyString(value, label) {
	if (typeof value !== 'string' || value.length === 0) failures.push(`${label} must be a non-empty string`);
}

function isObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function readJson(filePath) {
	return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}
