import fs from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const [beforePath, afterPath, outputPath] = process.argv.slice(2);
if (!beforePath || !afterPath) {
	throw new Error('beforePath and afterPath are required.');
}
const before = readCounters(beforePath);
const after = readCounters(afterPath);
const expected = new Map([
	['users_dataset_name_match', 1000],
	['users_active_dataset_name_match', 1000],
	['users_active_dataset_name_email_match', 1000],
	['users_inactive_dataset_name_match', 0],
	['users_active_dataset_user', 1000],
	['fixture_campuses', 25],
	['primary_active_members', 1000],
	['primary_active_members_member', 999],
	['primary_active_members_minister', 1],
	['isolation_active_members', 2],
	['primary_active_duties', 101],
	['primary_active_duties_meal', 100],
	['primary_active_duties_coffee', 1],
]);
for (const [metric, value] of expected) {
	if (before.get(metric) !== value) {
		throw new Error(`Before table counter ${metric} must equal ${value}.`);
	}
}
if (!isDeepStrictEqual([...before.entries()], [...after.entries()])) {
	throw new Error('Before/after table counters changed during the measured case.');
}
if (outputPath) {
	fs.writeFileSync(outputPath, `${JSON.stringify({
		status: 'adoptable',
		requiredExpectations: Object.fromEntries(expected),
		beforeAfterInvariant: true,
	}, null, 2)}\n`);
}

function readCounters(filePath) {
	const counters = new Map();
	for (const line of fs.readFileSync(filePath, 'utf8').split('\n').filter(Boolean)) {
		const match = line.match(/^(?:"([a-z0-9_]+)"|([a-z0-9_]+)),(\d+)$/);
		const metric = match?.[1] || match?.[2];
		if (!match || counters.has(metric)) {
			throw new Error(`Invalid or duplicate table counter row: ${line}`);
		}
		counters.set(metric, Number(match[3]));
	}
	for (const required of ['users_total', 'users_active']) {
		if (!counters.has(required)) throw new Error(`Missing required table counter: ${required}`);
	}
	return counters;
}
