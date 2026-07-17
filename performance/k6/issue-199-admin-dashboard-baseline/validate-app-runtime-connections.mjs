import assert from 'node:assert/strict';

const [environmentSource, postgresHost, postgresPort, database, databaseUser, redisHost, redisPort] = process.argv.slice(2);
try {
	const entries = JSON.parse(environmentSource);
	assert.ok(Array.isArray(entries), 'App container environment must be an array.');
	const environment = new Map(entries.map((entry) => {
		const separator = entry.indexOf('=');
		return [entry.slice(0, separator), entry.slice(separator + 1)];
	}));
	const datasource = new URL(required(environment, 'SPRING_DATASOURCE_URL').replace(/^jdbc:/, ''));
	assert.equal(datasource.protocol, 'postgresql:');
	assert.equal(datasource.hostname, postgresHost);
	assert.equal(datasource.port, postgresPort);
	assert.equal(datasource.pathname, `/${database}`);
	assert.equal(required(environment, 'SPRING_DATASOURCE_USERNAME'), databaseUser);
	assert.equal(required(environment, 'SPRING_DATA_REDIS_HOST'), redisHost);
	assert.equal(required(environment, 'SPRING_DATA_REDIS_PORT'), redisPort);
	process.stdout.write(`${JSON.stringify({status: 'app-runtime-connections-bound', automaticAdoption: false,
		postgres: {host: postgresHost, port: Number(postgresPort), database, userMatched: true},
		redis: {host: redisHost, port: Number(redisPort)}})}\n`);
} catch (error) {
	process.stdout.write(`${JSON.stringify({status: 'rejected', automaticAdoption: false,
		failures: [{name: 'appRuntimeConnections', actual: error.message}]})}\n`);
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function required(environment, name) {
	const value = environment.get(name);
	assert.ok(value, `${name} is required in the app container.`);
	return value;
}
