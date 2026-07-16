import fs from 'node:fs';
import { validateTargetIdentity } from './validate-target-identity.mjs';

const [identityPath, originalComposeProject, baseUrl, expectedAppService, expectedPostgresService, expectedDatabase] = process.argv.slice(2);
if (!identityPath || !originalComposeProject || !baseUrl || !expectedAppService || !expectedPostgresService || !expectedDatabase) {
	throw new Error('identityPath, originalComposeProject, baseUrl, expected services, and expectedDatabase are required.');
}

const identity = JSON.parse(fs.readFileSync(identityPath, 'utf8'));
if (!isObject(identity?.app) || !isObject(identity?.postgres) || !isObject(identity?.database)) {
	throw new Error('Post-lock runtime identity must contain app, postgres, and database objects.');
}
for (const [label, container] of [['app', identity.app], ['postgres', identity.postgres]]) {
	for (const key of ['containerId', 'name', 'imageId', 'startedAt', 'composeProject', 'composeService']) {
		if (typeof container[key] !== 'string' || container[key].length === 0) {
			throw new Error(`Post-lock ${label}.${key} must be a non-empty string.`);
		}
	}
	if (!isObject(container.publishedPorts)) throw new Error(`Post-lock ${label}.publishedPorts must be an object.`);
	if (container.composeProject !== originalComposeProject) {
		throw new Error(`Post-lock ${label} Compose project changed: ${container.composeProject}.`);
	}
}
if (identity.app.composeService !== expectedAppService) {
	throw new Error(`Post-lock app Compose service mismatch: ${identity.app.composeService}.`);
}
if (identity.postgres.composeService !== expectedPostgresService) {
	throw new Error(`Post-lock PostgreSQL Compose service mismatch: ${identity.postgres.composeService}.`);
}
if (identity.database.name !== expectedDatabase) {
	throw new Error(`Post-lock PostgreSQL database mismatch: ${identity.database.name}.`);
}

const approved = validateTargetIdentity({
	baseUrl,
	appContainerId: identity.app.containerId,
	appComposeService: identity.app.composeService,
	expectedAppComposeService: expectedAppService,
	appPublishedPortsJson: JSON.stringify(identity.app.publishedPorts),
	postgresComposeService: identity.postgres.composeService,
	expectedPostgresComposeService: expectedPostgresService,
});
process.stdout.write(`${JSON.stringify({ ...approved, composeProject: originalComposeProject })}\n`);

function isObject(value) {
	return value !== null && typeof value === 'object' && !Array.isArray(value);
}
