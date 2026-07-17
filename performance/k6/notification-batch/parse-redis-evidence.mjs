import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';
import { canonicalDecimal } from './integrity-contract.mjs';

const outputPath = process.env.REDIS_EVIDENCE_OUTPUT_PATH;
const dbSizeRaw = process.env.REDIS_DBSIZE;
const serverInfo = process.env.REDIS_SERVER_INFO;
const commandStats = process.env.REDIS_COMMANDSTATS;
const capturedAt = process.env.REDIS_CAPTURED_AT;
assert.ok(outputPath, 'REDIS_EVIDENCE_OUTPUT_PATH is required');
assert.ok(serverInfo, 'REDIS_SERVER_INFO is required');
assert.ok(commandStats, 'REDIS_COMMANDSTATS is required');
assert.ok(Number.isFinite(Date.parse(capturedAt)), 'REDIS_CAPTURED_AT must be an ISO timestamp');

const value = (text, name) => {
	const match = text.match(new RegExp(`^${name}:(.+)\\r?$`, 'm'));
	assert.ok(match, `Redis evidence missing ${name}`);
	return match[1].trim();
};
const integer = (raw, name, minimum = 0) => {
	assert.match(raw, /^[0-9]+$/, `${name} must be numeric`);
	const parsed = Number(raw);
	assert.ok(Number.isSafeInteger(parsed) && parsed >= minimum, `${name} is invalid`);
	return parsed;
};
const decimal = (raw, name) => String(canonicalDecimal(raw, name));
const setMatch = commandStats.match(/^cmdstat_set:calls=(\d+),/m);
assert.ok(setMatch, 'Redis evidence missing cmdstat_set');

const snapshot = {
	capturedAt,
	runId: value(serverInfo, 'run_id'),
	uptimeSeconds: integer(value(serverInfo, 'uptime_in_seconds'), 'uptime', 1),
	tcpPort: integer(value(serverInfo, 'tcp_port'), 'tcp_port', 1),
	dbSize: decimal(dbSizeRaw, 'DBSIZE'),
	commands: { set: decimal(setMatch[1], 'SET calls') },
};
writeFileSync(outputPath, `${JSON.stringify(snapshot)}\n`, { flag: 'wx' });
