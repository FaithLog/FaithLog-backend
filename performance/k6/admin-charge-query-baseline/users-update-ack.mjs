import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const POSTGRESQL_BIGINT_MAX = 9223372036854775807n;

export function classifyUsersUpdateAck(beforeRaw, currentRaw, expectedDeltaRaw) {
	const before = parsePostgreSqlCounter(beforeRaw, 'before');
	const current = parsePostgreSqlCounter(currentRaw, 'current');
	const expectedDelta = parseExpectedDelta(expectedDeltaRaw);
	const delta = current - before;
	if (delta < 0n) {
		throw new Error('users.n_tup_upd decreased before the initial-login ACK.');
	}
	if (delta < expectedDelta) {
		return 'pending';
	}
	if (delta === expectedDelta) {
		return 'acknowledged';
	}
	throw new Error(`users.n_tup_upd exceeded the exact initial-login delta of ${expectedDelta}.`);
}

function parseExpectedDelta(raw) {
	if (typeof raw !== 'string' || !/^[1-9][0-9]*$/.test(raw)) {
		throw new Error('Expected users.n_tup_upd delta must be a canonical positive decimal string.');
	}
	const value = BigInt(raw);
	if (value > POSTGRESQL_BIGINT_MAX) {
		throw new Error('Expected users.n_tup_upd delta exceeds the PostgreSQL bigint range.');
	}
	return value;
}

function parsePostgreSqlCounter(raw, label) {
	if (typeof raw !== 'string' || !/^(?:0|[1-9][0-9]*)(?:\r?\n)?$/.test(raw)) {
		throw new Error(`${label} users.n_tup_upd must be one canonical decimal-string line.`);
	}
	const canonical = raw.endsWith('\r\n')
		? raw.slice(0, -2)
		: raw.endsWith('\n') ? raw.slice(0, -1) : raw;
	const value = BigInt(canonical);
	if (value > POSTGRESQL_BIGINT_MAX) {
		throw new Error(`${label} users.n_tup_upd exceeds the PostgreSQL bigint range.`);
	}
	return value;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const [beforePath, currentPath, expectedDelta] = process.argv.slice(2);
	if (!currentPath || !expectedDelta) {
		throw new Error('Usage: users-update-ack.mjs <before-counter> <current-counter> <expected-delta>');
	}
	const [beforeRaw, currentRaw] = await Promise.all(
		[beforePath, currentPath].map((file) => readFile(file, 'utf8')),
	);
	process.stdout.write(classifyUsersUpdateAck(beforeRaw, currentRaw, expectedDelta));
}
