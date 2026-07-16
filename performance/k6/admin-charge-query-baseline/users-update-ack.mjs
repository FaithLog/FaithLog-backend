import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const POSTGRESQL_BIGINT_MAX = 9223372036854775807n;

export function classifyUsersUpdateAck(beforeRaw, currentRaw) {
	const before = parsePostgreSqlCounter(beforeRaw, 'before');
	const current = parsePostgreSqlCounter(currentRaw, 'current');
	const delta = current - before;
	if (delta === 0n) {
		return 'pending';
	}
	if (delta === 1n) {
		return 'acknowledged';
	}
	if (delta < 0n) {
		throw new Error('users.n_tup_upd decreased before the measured-login ACK.');
	}
	throw new Error('users.n_tup_upd exceeded the exact measured-login delta of one.');
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
	const [beforePath, currentPath] = process.argv.slice(2);
	if (!currentPath) {
		throw new Error('Usage: users-update-ack.mjs <before-counter> <current-counter>');
	}
	const [beforeRaw, currentRaw] = await Promise.all(
		[beforePath, currentPath].map((file) => readFile(file, 'utf8')),
	);
	process.stdout.write(classifyUsersUpdateAck(beforeRaw, currentRaw));
}
