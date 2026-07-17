import assert from 'node:assert/strict';
import { isIP } from 'node:net';
import { realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

function nonEmptyString(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.ok(value.length > 0 && value === value.trim(), `${label} must be non-empty and trimmed`);
}

function canonicalLoopbackAddress(value, allowHostCidr, label) {
	nonEmptyString(value, label);
	const fields = value.split('/');
	assert.ok(fields.length === 1 || (allowHostCidr && fields.length === 2),
		`${label} must be an explicit numeric loopback${allowHostCidr ? ' with an optional host CIDR' : ''}`);
	const [address, prefix] = fields;
	const version = isIP(address);
	assert.ok(version === 4 || version === 6, `${label} must be an explicit numeric loopback address`);
	if (prefix !== undefined) {
		const expectedPrefix = version === 4 ? '32' : '128';
		assert.equal(prefix, expectedPrefix, `${label} CIDR prefix must be exactly /${expectedPrefix}`);
	}
	const canonical = version === 4 ? address : new URL(`http://[${address}]/`).hostname.slice(1, -1);
	assert.ok(canonical === '127.0.0.1' || canonical === '::1', `${label} must be the approved numeric loopback host`);
	return canonical;
}

export function validatePostgresServerAddress(actual, approved) {
	const actualCanonical = canonicalLoopbackAddress(actual, true, 'PostgreSQL server address');
	const approvedCanonical = canonicalLoopbackAddress(
		approved, false, 'runtime-approved PostgreSQL server address');
	assert.equal(actualCanonical, approvedCanonical,
		'PostgreSQL server address must match the explicit runtime-approved loopback target');
	return actual;
}

export function validateApprovedPostgresServerAddress(approved) {
	return canonicalLoopbackAddress(
		approved, false, 'runtime-approved PostgreSQL server address');
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
	const [command, actual, approved] = process.argv.slice(2);
	if (command === 'validate') validatePostgresServerAddress(actual, approved);
	else if (command === 'validate-approved') validateApprovedPostgresServerAddress(actual);
	else throw new Error('runtime inet contract command must be validate or validate-approved');
}
