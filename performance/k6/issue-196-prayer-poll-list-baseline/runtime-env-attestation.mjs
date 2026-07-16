import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export const APPROVED_INSTRUMENTATION_ENV = Object.freeze([
	'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=OFF',
	'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_EXTRACT=OFF',
	'LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG',
	'SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false',
	'SPRING_JPA_SHOW_SQL=false',
]);

export function attestRuntimeEnvironment(previousRaw, currentRaw) {
	const previous = parseEnvironment(previousRaw, 'previous app environment');
	const current = parseEnvironment(currentRaw, 'instrumented app environment');
	for (const assignment of APPROVED_INSTRUMENTATION_ENV) {
		const separator = assignment.indexOf('=');
		const name = assignment.slice(0, separator);
		const expectedValue = assignment.slice(separator + 1);
		if (previous.has(name)) throw new Error(`Approved instrumentation variable must be absent before recreation: ${name}`);
		if (current.get(name) !== expectedValue) throw new Error(`Instrumented app environment is missing the exact approved value for ${name}`);
		current.delete(name);
	}
	const previousCanonical = canonical(previous);
	const currentCanonical = canonical(current);
	if (previousCanonical !== currentCanonical) throw new Error('Unrelated app environment drift is forbidden.');
	return {
		previousSanitizedSha256: sha256(previousCanonical),
		newSanitizedSha256: sha256(currentCanonical),
		allowedDelta: [...APPROVED_INSTRUMENTATION_ENV],
	};
}

function parseEnvironment(raw, name) {
	if (!Array.isArray(raw)) throw new Error(`${name} must be a Docker Config.Env array.`);
	const values = new Map();
	for (const assignment of raw) {
		if (typeof assignment !== 'string') throw new Error(`${name} contains a non-string assignment.`);
		const separator = assignment.indexOf('=');
		const key = separator < 0 ? assignment : assignment.slice(0, separator);
		const value = separator < 0 ? '' : assignment.slice(separator + 1);
		if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key) || values.has(key)) throw new Error(`${name} contains an invalid or duplicate key.`);
		values.set(key, value);
	}
	return values;
}

function canonical(values) {
	return [...values].sort(([left], [right]) => left.localeCompare(right)).map(([key, value]) => `${key}=${value}\n`).join('');
}

function sha256(value) {
	return createHash('sha256').update(value).digest('hex');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const previous = JSON.parse(process.env.PREVIOUS_APP_ENV_JSON || 'null');
	const current = JSON.parse(process.env.CURRENT_APP_ENV_JSON || 'null');
	process.stdout.write(JSON.stringify(attestRuntimeEnvironment(previous, current)));
}
