import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {semanticEqual} from './scenario-definition.mjs';

const CONTAINER_KEYS = ['id', 'image', 'project', 'running', 'service', 'startedAt'];
const ROLES = ['app', 'postgres', 'redis'];
const FULL_CONTAINER_ID = /^[a-f0-9]{64}$/;
const IMAGE_ID = /^sha256:[a-f0-9]{64}$/;
const DATABASE_KEYS = ['name', 'serverAddress', 'serverPort', 'postmasterStartTime'];
const BINDING_KEYS = ['baseUrl', 'containerPort', 'hostPort', 'service'];

export function validateRuntimeBootstrap(identity, expected) {
	assertExactKeys(identity, ROLES, 'runtime identity');
	assertExactKeys(expected, ['project', 'services', 'containerIds', 'imageIds'], 'approved runtime identity');
	for (const key of ['services', 'containerIds', 'imageIds']) {
		assertExactKeys(expected[key], ROLES, `approved runtime ${key}`);
	}
	if (typeof expected.project !== 'string' || expected.project.length === 0) {
		throw new Error('Approved Compose project is required.');
	}
	for (const role of ROLES) {
		validateContainer(identity[role], role);
		if (identity[role].project !== expected.project
			|| identity[role].service !== expected.services[role]
			|| identity[role].id !== expected.containerIds[role]
			|| identity[role].image !== expected.imageIds[role]) {
			throw new Error(`${role} runtime identity does not match the approved immutable target.`);
		}
	}
	return structuredClone(identity);
}

export function validateRuntimeStability(before, after) {
	assertExactKeys(before, ROLES, 'before runtime identity');
	assertExactKeys(after, ROLES, 'after runtime identity');
	for (const role of ROLES) {
		validateContainer(before[role], `before ${role}`);
		validateContainer(after[role], `after ${role}`);
	}
	if (!semanticEqual(before, after)) {
		throw new Error('Container runtime identity changed or was replaced during measurement.');
	}
	return true;
}

export function validatePostLockTarget(before, after, expectedDatabaseName) {
	assertExactKeys(before, ['runtime', 'database', 'binding'], 'pre-lock approved target');
	assertExactKeys(after, ['runtime', 'database', 'binding'], 'post-lock target');
	validateRuntimeStability(before.runtime, after.runtime);
	validateDatabaseIdentity(before.database, expectedDatabaseName, 'pre-lock database');
	validateDatabaseIdentity(after.database, expectedDatabaseName, 'post-lock database');
	validateBindingIdentity(before.binding, 'pre-lock binding');
	validateBindingIdentity(after.binding, 'post-lock binding');
	if (!semanticEqual(before.database, after.database) || !semanticEqual(before.binding, after.binding)) {
		throw new Error('Database or published target binding identity changed after the canonical lock was acquired.');
	}
	return true;
}

export function validateDatabaseIdentity(database, expectedDatabaseName, label = 'database') {
	assertExactKeys(database, DATABASE_KEYS, `${label} identity`);
	if (typeof expectedDatabaseName !== 'string' || expectedDatabaseName.length === 0
		|| database.name !== expectedDatabaseName) {
		throw new Error(`${label} current database does not match the approved database identity.`);
	}
	if (typeof database.serverAddress !== 'string' || database.serverAddress.length === 0
		|| !Number.isSafeInteger(database.serverPort) || database.serverPort <= 0
		|| typeof database.postmasterStartTime !== 'string'
		|| !Number.isFinite(Date.parse(database.postmasterStartTime))) {
		throw new Error(`${label} server endpoint or postmaster identity is invalid.`);
	}
	return structuredClone(database);
}

function validateContainer(container, label) {
	assertExactKeys(container, CONTAINER_KEYS, `${label} container identity`);
	for (const name of ['id', 'image', 'project', 'service', 'startedAt']) {
		if (typeof container[name] !== 'string' || container[name].length === 0) {
			throw new Error(`${label} container identity requires ${name}.`);
		}
	}
	if (container.running !== true || !Number.isFinite(Date.parse(container.startedAt))) {
		throw new Error(`${label} container must be running with a valid StartedAt identity.`);
	}
	if (!FULL_CONTAINER_ID.test(container.id) || !IMAGE_ID.test(container.image)) {
		throw new Error(`${label} container requires full immutable container and image IDs.`);
	}
}

function validateBindingIdentity(binding, label) {
	assertExactKeys(binding, BINDING_KEYS, `${label} target`);
	for (const name of BINDING_KEYS) {
		if (typeof binding[name] !== 'string' || binding[name].length === 0) {
			throw new Error(`${label} target binding requires ${name}.`);
		}
	}
}

function assertExactKeys(value, expected, label) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) {
		throw new Error(`${label} schema is required.`);
	}
	const actual = Object.keys(value).sort();
	const keys = [...expected].sort();
	if (!semanticEqual(actual, keys)) {
		throw new Error(`${label} schema keys do not match the required identity.`);
	}
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const mode = process.argv[2];
	if (mode === 'bootstrap') {
		const identity = {
			app: JSON.parse(process.env.APP_IDENTITY_JSON ?? 'null'),
			postgres: JSON.parse(process.env.POSTGRES_IDENTITY_JSON ?? 'null'),
			redis: JSON.parse(process.env.REDIS_IDENTITY_JSON ?? 'null'),
		};
		const validated = validateRuntimeBootstrap(identity, {
			project: process.env.EXPECTED_COMPOSE_PROJECT,
			services: {
				app: process.env.EXPECTED_APP_COMPOSE_SERVICE,
				postgres: process.env.EXPECTED_POSTGRES_COMPOSE_SERVICE,
				redis: process.env.EXPECTED_REDIS_COMPOSE_SERVICE,
			},
			containerIds: {
				app: process.env.EXPECTED_APP_CONTAINER_ID,
				postgres: process.env.EXPECTED_POSTGRES_CONTAINER_ID,
				redis: process.env.EXPECTED_REDIS_CONTAINER_ID,
			},
			imageIds: {
				app: process.env.EXPECTED_APP_IMAGE_ID,
				postgres: process.env.EXPECTED_POSTGRES_IMAGE_ID,
				redis: process.env.EXPECTED_REDIS_IMAGE_ID,
			},
		});
		process.stdout.write(`${JSON.stringify(validated)}\n`);
	} else if (mode === 'database') {
		const database = JSON.parse(process.env.DATABASE_IDENTITY_JSON ?? 'null');
		const validated = validateDatabaseIdentity(
			database,
			process.env.EXPECTED_DATABASE_NAME
		);
		process.stdout.write(`${JSON.stringify(validated)}\n`);
	} else if (mode === 'post-lock') {
		const paths = process.argv.slice(3);
		if (paths.length !== 6) {
			throw new Error('post-lock requires pre/post runtime, database, and binding evidence paths.');
		}
		const [runtimeBefore, runtimeAfter, databaseBefore, databaseAfter, bindingBefore, bindingAfter] =
			await Promise.all(paths.map(async (file) => JSON.parse(await readFile(file, 'utf8'))));
		validatePostLockTarget({
			runtime: runtimeBefore, database: databaseBefore, binding: bindingBefore,
		}, {
			runtime: runtimeAfter, database: databaseAfter, binding: bindingAfter,
		}, process.env.EXPECTED_DATABASE_NAME);
	} else if (mode === 'compare') {
		const [beforePath, afterPath] = process.argv.slice(3);
		const [before, after] = await Promise.all(
			[beforePath, afterPath].map(async (file) => JSON.parse(await readFile(file, 'utf8')))
		);
		validateRuntimeStability(before, after);
	} else {
		throw new Error('Usage: runtime-identity.mjs bootstrap | database | post-lock | compare');
	}
}
