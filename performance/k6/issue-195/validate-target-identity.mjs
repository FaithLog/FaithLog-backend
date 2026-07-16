import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateTargetIdentity({
	baseUrl,
	appContainerId,
	appImageId,
	expectedAppImageId,
	appComposeService,
	expectedAppComposeService,
	appPublishedPortsJson,
	postgresContainerId,
	postgresComposeService,
	expectedPostgresComposeService,
	postgresImageId,
	expectedPostgresImageId,
	redisContainerId,
	redisComposeService,
	expectedRedisComposeService,
	redisImageId,
	expectedRedisImageId,
}) {
	for (const [name, value] of Object.entries({
		BASE_URL: baseUrl,
		APP_CONTAINER_ID: appContainerId,
		APP_IMAGE_ID: appImageId,
		EXPECTED_APP_IMAGE_ID: expectedAppImageId,
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
	if (appImageId !== expectedAppImageId) throw new Error(`App image mismatch: actual=${appImageId} expected=${expectedAppImageId}`);
	validateOptionalTarget('PostgreSQL', {
		containerId: postgresContainerId,
		composeService: postgresComposeService,
		expectedComposeService: expectedPostgresComposeService,
		imageId: postgresImageId,
		expectedImageId: expectedPostgresImageId,
	});
	validateOptionalTarget('Redis', {
		containerId: redisContainerId,
		composeService: redisComposeService,
		expectedComposeService: expectedRedisComposeService,
		imageId: redisImageId,
		expectedImageId: expectedRedisImageId,
	});

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
		appImageId,
		appComposeService,
		expectedAppComposeService,
		postgresComposeService: postgresComposeService || null,
		postgresContainerId: postgresContainerId || null,
		expectedPostgresComposeService: expectedPostgresComposeService || null,
		postgresImageId: postgresImageId || null,
		redisContainerId: redisContainerId || null,
		redisComposeService: redisComposeService || null,
		expectedRedisComposeService: expectedRedisComposeService || null,
		redisImageId: redisImageId || null,
		publishedEndpoint: matches[0],
	};
}

function validateOptionalTarget(label, values) {
	const supplied = Object.values(values).some(Boolean);
	if (!supplied) return;
	for (const [key, value] of Object.entries(values)) {
		if (!value) {
			throw new Error(`${label} ${key} is required when that target is validated.`);
		}
	}
	if (values.composeService !== values.expectedComposeService) {
		throw new Error(`${label} Compose service mismatch: actual=${values.composeService} expected=${values.expectedComposeService}`);
	}
	if (values.imageId !== values.expectedImageId) {
		throw new Error(`${label} image mismatch: actual=${values.imageId} expected=${values.expectedImageId}`);
	}
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
		appImageId: process.env.APP_IMAGE_ID,
		expectedAppImageId: process.env.EXPECTED_APP_IMAGE_ID,
		appComposeService: process.env.APP_COMPOSE_SERVICE,
		expectedAppComposeService: process.env.EXPECTED_APP_COMPOSE_SERVICE,
		appPublishedPortsJson: process.env.APP_PUBLISHED_PORTS_JSON,
		postgresContainerId: process.env.POSTGRES_CONTAINER_ID,
		postgresComposeService: process.env.POSTGRES_COMPOSE_SERVICE,
		expectedPostgresComposeService: process.env.EXPECTED_POSTGRES_COMPOSE_SERVICE,
		postgresImageId: process.env.POSTGRES_IMAGE_ID,
		expectedPostgresImageId: process.env.EXPECTED_POSTGRES_IMAGE_ID,
		redisContainerId: process.env.REDIS_CONTAINER_ID,
		redisComposeService: process.env.REDIS_COMPOSE_SERVICE,
		expectedRedisComposeService: process.env.EXPECTED_REDIS_COMPOSE_SERVICE,
		redisImageId: process.env.REDIS_IMAGE_ID,
		expectedRedisImageId: process.env.EXPECTED_REDIS_IMAGE_ID,
	});
	process.stdout.write(`${JSON.stringify(identity)}\n`);
}
