export function parseRedisRuntimeIdentity(infoText, expectedPort) {
	if (typeof infoText !== 'string' || !Number.isInteger(Number(expectedPort)) || Number(expectedPort) < 1) {
		throw new Error('EXPECTED_REDIS_PORT is required at runtime.');
	}
	const values = Object.fromEntries(infoText.split(/\r?\n/)
		.filter((line) => line && !line.startsWith('#') && line.includes(':'))
		.map((line) => {
			const separator = line.indexOf(':');
			return [line.slice(0, separator), line.slice(separator + 1).replace(/\r$/, '')];
		}));
	const tcpPort = Number(values.tcp_port);
	if (!/^[a-f0-9]{40}$/.test(values.run_id || '') || !values.redis_version
		|| !Number.isInteger(tcpPort) || tcpPort !== Number(expectedPort)) {
		throw new Error('Redis runtime identity is malformed or targets an unexpected port.');
	}
	return { redisRunId: values.run_id, redisVersion: values.redis_version, redisPort: tcpPort };
}

if (import.meta.url === `file://${process.argv[1]}`) {
	try {
		process.stdout.write(`${JSON.stringify(parseRedisRuntimeIdentity(
			process.env.REDIS_INFO_TEXT || '', process.env.EXPECTED_REDIS_PORT,
		))}\n`);
	} catch (error) {
		console.error(error.message);
		process.exitCode = 1;
	}
}
