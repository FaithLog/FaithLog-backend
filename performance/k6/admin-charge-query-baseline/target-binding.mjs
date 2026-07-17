import {pathToFileURL} from 'node:url';

export function validateTargetBinding(baseUrl, inspected, expectedService) {
	let target;
	try {
		target = new URL(baseUrl);
	} catch (_error) {
		throw new Error('BASE_URL must be a valid local URL.');
	}
	const targetFamily = numericLoopbackFamily(target.hostname);
	if (target.protocol !== 'http:' || !targetFamily || target.username || target.password) {
		throw new Error('BASE_URL host must be an unauthenticated numeric local loopback HTTP target.');
	}
	if ((target.pathname !== '/' && target.pathname !== '') || target.search || target.hash || !target.port) {
		throw new Error('BASE_URL must contain only the explicit published target port.');
	}
	if (typeof inspected?.service !== 'string' || inspected.service.length === 0) {
		throw new Error('Inspected APP_CONTAINER is missing its Compose service label.');
	}
	if (typeof expectedService !== 'string' || inspected.service !== expectedService) {
		throw new Error(`Inspected APP_CONTAINER service does not match approved service ${String(expectedService)}.`);
	}
	const matches = Object.entries(inspected.ports ?? {}).flatMap(([containerPort, bindings]) =>
		Array.isArray(bindings)
			? bindings.map((binding) => ({containerPort, ...binding}))
			: []
	).filter((binding) => binding.containerPort === '8080/tcp'
		&& binding.HostPort === target.port
		&& publishedHostFamily(binding.HostIp) === targetFamily);
	if (matches.length !== 1) {
		throw new Error(`BASE_URL target port ${target.port} does not uniquely match the inspected APP_CONTAINER published port.`);
	}
	return {service: inspected.service, containerPort: matches[0].containerPort, hostPort: matches[0].HostPort};
}

function numericLoopbackFamily(hostname) {
	if (hostname === '127.0.0.1') {
		return 'ipv4';
	}
	if (hostname === '[::1]' || hostname === '::1') {
		return 'ipv6';
	}
	return null;
}

function publishedHostFamily(hostIp) {
	if (hostIp === '0.0.0.0' || hostIp === '127.0.0.1') {
		return 'ipv4';
	}
	if (hostIp === '::' || hostIp === '::1') {
		return 'ipv6';
	}
	return null;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const baseUrl = process.env.BASE_URL;
	const inspected = JSON.parse(process.env.TARGET_BINDING_JSON ?? 'null');
	const binding = validateTargetBinding(baseUrl, inspected, process.env.EXPECTED_APP_COMPOSE_SERVICE);
	process.stdout.write(`${JSON.stringify({baseUrl, ...binding})}\n`);
}
