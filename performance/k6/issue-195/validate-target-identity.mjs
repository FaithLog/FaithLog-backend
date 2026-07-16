import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateTargetIdentity({
	baseUrl,
	appContainerId,
	appComposeService,
	expectedAppComposeService,
	appPublishedPortsJson,
	postgresComposeService,
	expectedPostgresComposeService,
}) {
	for (const [name, value] of Object.entries({
		BASE_URL: baseUrl,
		APP_CONTAINER_ID: appContainerId,
		APP_COMPOSE_SERVICE: appComposeService,
		EXPECTED_APP_COMPOSE_SERVICE: expectedAppComposeService,
		APP_PUBLISHED_PORTS_JSON: appPublishedPortsJson,
	})) {
		if (!value) {
			throw new Error(`${name} is required for target identity validation.`);
		}
	}
	if (appComposeService !== expectedAppComposeService) {
		throw new Error(`App Compose service mismatch: actual=${appComposeService} expected=${expectedAppComposeService}`);
	}
	if ((postgresComposeService || expectedPostgresComposeService)
		&& postgresComposeService !== expectedPostgresComposeService) {
		throw new Error(`PostgreSQL Compose service mismatch: actual=${postgresComposeService} expected=${expectedPostgresComposeService}`);
	}

	const target = new URL(baseUrl);
	if (target.protocol !== 'http:'
		|| !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname)
		|| !target.port
		|| target.pathname !== '/'
		|| target.search
		|| target.hash
		|| target.username
		|| target.password) {
		throw new Error('BASE_URL must be an exact local published HTTP origin with an explicit port and no path/query/credentials.');
	}

	let publishedPorts;
	try {
		publishedPorts = JSON.parse(appPublishedPortsJson);
	} catch (error) {
		throw new Error('APP_PUBLISHED_PORTS_JSON must be valid Docker inspect JSON.', { cause: error });
	}
	const matches = [];
	for (const [containerPort, bindings] of Object.entries(publishedPorts || {})) {
		for (const binding of bindings || []) {
			if (String(binding.HostPort) === target.port && hostBindingMatches(target.hostname, binding.HostIp)) {
				matches.push({
					containerPort,
					hostIp: binding.HostIp,
					hostPort: String(binding.HostPort),
				});
			}
		}
	}
	if (matches.length !== 1) {
		throw new Error(`BASE_URL ${target.origin} must match exactly one published port of APP_CONTAINER_ID ${appContainerId}.`);
	}
	return {
		baseUrl: target.origin,
		appContainerId,
		appComposeService,
		expectedAppComposeService,
		postgresComposeService: postgresComposeService || null,
		expectedPostgresComposeService: expectedPostgresComposeService || null,
		publishedEndpoint: matches[0],
	};
}

function hostBindingMatches(hostname, hostIp) {
	const normalized = hostIp || '0.0.0.0';
	if (hostname === 'localhost') {
		return ['0.0.0.0', '127.0.0.1', '::', '::1'].includes(normalized);
	}
	if (hostname === '127.0.0.1') {
		return ['0.0.0.0', '127.0.0.1'].includes(normalized);
	}
	return ['::', '::1'].includes(normalized);
}

const isMain = process.argv[1]
	&& path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
	const identity = validateTargetIdentity({
		baseUrl: process.env.BASE_URL,
		appContainerId: process.env.APP_CONTAINER_ID,
		appComposeService: process.env.APP_COMPOSE_SERVICE,
		expectedAppComposeService: process.env.EXPECTED_APP_COMPOSE_SERVICE,
		appPublishedPortsJson: process.env.APP_PUBLISHED_PORTS_JSON,
		postgresComposeService: process.env.POSTGRES_COMPOSE_SERVICE,
		expectedPostgresComposeService: process.env.EXPECTED_POSTGRES_COMPOSE_SERVICE,
	});
	process.stdout.write(`${JSON.stringify(identity)}\n`);
}
