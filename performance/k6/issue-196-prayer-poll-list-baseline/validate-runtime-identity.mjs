const IDENTITY_KEYS = [
	'currentDatabase', 'currentUser', 'forceRlsTableCount', 'jdbcOwnedTableCount', 'latestFlywayVersion',
	'pgStatStatementsExtensionInstalled', 'pgStatStatementsPreloaded', 'pgStatStatementsViewAvailable',
	'policyCount', 'postmasterStartedAt', 'publicApplicationTableCount', 'rlsEnabledTableCount',
	'serverAddress', 'serverPort', 'sessionUser', 'sessionUserIsDatabaseOwner',
].sort();

export function validateRuntimeIdentity(identity, { expectedFlywayVersion, expectedTableCount = 27 } = {}) {
	if (!identity || typeof identity !== 'object' || Array.isArray(identity)
		|| !sameValues(Object.keys(identity).sort(), IDENTITY_KEYS)) {
		throw new Error('PostgreSQL runtime identity has an unexpected schema.');
	}
	if (typeof expectedFlywayVersion !== 'string' || expectedFlywayVersion.length === 0) {
		throw new Error('EXPECTED_FLYWAY_VERSION is required at runtime.');
	}
	if (typeof identity.currentDatabase !== 'string' || identity.currentDatabase.length === 0
		|| typeof identity.currentUser !== 'string' || identity.currentUser !== identity.sessionUser
		|| identity.sessionUserIsDatabaseOwner !== true
		|| typeof identity.serverAddress !== 'string' || identity.serverAddress.length === 0
		|| !Number.isInteger(identity.serverPort) || identity.serverPort < 1
		|| !validTimestamp(identity.postmasterStartedAt)) {
		throw new Error('PostgreSQL JDBC owner/runtime continuity identity is malformed.');
	}
	if (identity.latestFlywayVersion !== expectedFlywayVersion) {
		throw new Error('flyway-version-drift');
	}
	if (identity.publicApplicationTableCount !== expectedTableCount
		|| identity.rlsEnabledTableCount !== expectedTableCount
		|| identity.forceRlsTableCount !== 0 || identity.policyCount !== 0) {
		throw new Error('rls-contract-drift');
	}
	if (identity.jdbcOwnedTableCount !== expectedTableCount) {
		throw new Error('jdbc-owner-bypass-drift');
	}
	if (![identity.pgStatStatementsExtensionInstalled, identity.pgStatStatementsPreloaded,
		identity.pgStatStatementsViewAvailable].every((value) => typeof value === 'boolean')) {
		throw new Error('invalid-pgss-state');
	}
	if (identity.pgStatStatementsExtensionInstalled !== identity.pgStatStatementsViewAvailable) {
		throw new Error('invalid-pgss-state');
	}
	return identity;
}

function sameValues(left, right) {
	return left.length === right.length && left.every((value, index) => value === right[index]);
}

function validTimestamp(value) {
	return typeof value === 'string' && value.length > 0 && Number.isFinite(Date.parse(value));
}

if (import.meta.url === `file://${process.argv[1]}`) {
	try {
		const identity = JSON.parse(process.env.DB_RUNTIME_IDENTITY_JSON || 'null');
		validateRuntimeIdentity(identity, {
			expectedFlywayVersion: process.env.EXPECTED_FLYWAY_VERSION,
			expectedTableCount: 27,
		});
		process.stdout.write(`${JSON.stringify(identity)}\n`);
	} catch (error) {
		console.error(error.message);
		process.exitCode = 1;
	}
}
