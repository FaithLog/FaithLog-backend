import assert from 'node:assert/strict';

try {
	let source = '';
	for await (const chunk of process.stdin) source += chunk;
	const fields = new Map();
	for (const line of source.split(/\r?\n/)) {
		if (!line || line.startsWith('#')) continue;
		const separator = line.indexOf(':');
		if (separator > 0) fields.set(line.slice(0, separator), line.slice(separator + 1).replace(/\r$/, ''));
	}
	const runId = required(fields, 'run_id');
	const serverVersion = required(fields, 'redis_version');
	const portSource = required(fields, 'tcp_port');
	const uptimeSeconds = required(fields, 'uptime_in_seconds');
	assert.match(runId, /^[a-f0-9]{40}$/, 'Redis run_id must be a 40-hex server identity.');
	assert.match(portSource, /^(?:[1-9]\d*)$/, 'Redis tcp_port must be positive.');
	assert.match(uptimeSeconds, /^(?:0|[1-9]\d*)$/, 'Redis uptime must be a decimal string.');
	const serverPort = Number(portSource);
	assert.ok(Number.isSafeInteger(serverPort) && serverPort <= 65535);
	process.stdout.write(`${JSON.stringify({runId, serverVersion, serverPort, uptimeSeconds})}\n`);
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function required(fields, name) {
	const value = fields.get(name);
	assert.ok(value, `Redis INFO server field ${name} is required.`);
	return value;
}
