import { execFileSync } from 'node:child_process';

export function captureContainerIdentity(containerId, {
	execFileSyncImpl = execFileSync,
	environment = process.env,
} = {}) {
	let inspected;
	try {
		inspected = JSON.parse(execFileSyncImpl(
			'docker',
			['inspect', containerId],
			{ encoding: 'utf8', env: sanitizedChildEnvironment(environment) },
		));
	} catch (error) {
		throw new Error('Docker inspect did not return valid container identity JSON.', { cause: error });
	}
	if (!Array.isArray(inspected) || inspected.length !== 1) {
		throw new Error('Docker inspect must resolve exactly one container.');
	}
	const value = inspected[0];
	const identity = {
		containerId: value.Id,
		name: value.Name,
		imageId: value.Image,
		startedAt: value.State?.StartedAt,
		composeProject: value.Config?.Labels?.['com.docker.compose.project'],
		composeService: value.Config?.Labels?.['com.docker.compose.service'],
		publishedPorts: value.NetworkSettings?.Ports,
	};
	validateContainerIdentity(identity);
	return identity;
}

export function sanitizedChildEnvironment(environment) {
	const allowed = new Set([
		'PATH',
		'HOME',
		'TMPDIR',
		'TMP',
		'TEMP',
		'DOCKER_HOST',
		'DOCKER_CONTEXT',
		'DOCKER_TLS_VERIFY',
		'DOCKER_CERT_PATH',
		'DOCKER_CONFIG',
		'XDG_CONFIG_HOME',
		'LANG',
		'LC_ALL',
		'LC_CTYPE',
	]);
	return Object.fromEntries(Object.entries(environment).filter(([name, value]) => (
		allowed.has(name) && typeof value === 'string'
	)));
}

function validateContainerIdentity(identity) {
	if (!/^[a-f0-9]{64}$/i.test(identity.containerId ?? '')) {
		throw new Error('Container identity requires a full 64-hex immutable container ID.');
	}
	if (!/^sha256:[a-f0-9]{64}$/i.test(identity.imageId ?? '')) {
		throw new Error('Container identity requires a full sha256 image ID.');
	}
	for (const [label, value] of [
		['name', identity.name],
		['Compose project', identity.composeProject],
		['Compose service', identity.composeService],
	]) {
		if (typeof value !== 'string' || !value.trim() || !/^[A-Za-z0-9_./-]+$/.test(value)) {
			throw new Error(`Container identity ${label} must be a safe non-empty string.`);
		}
	}
	if (typeof identity.startedAt !== 'string' || !Number.isFinite(Date.parse(identity.startedAt))) {
		throw new Error('Container identity StartedAt must be a valid timestamp.');
	}
	validatePublishedPorts(identity.publishedPorts);
}

function validatePublishedPorts(ports) {
	if (!ports || typeof ports !== 'object' || Array.isArray(ports)) {
		throw new Error('Container identity published ports must be a Docker ports object.');
	}
	for (const [containerPort, bindings] of Object.entries(ports)) {
		if (!/^\d+\/(?:tcp|udp|sctp)$/.test(containerPort)
			|| (bindings !== null && !Array.isArray(bindings))) {
			throw new Error('Container identity published ports schema is invalid.');
		}
		for (const binding of bindings ?? []) {
			const port = Number(binding?.HostPort);
			if (typeof binding?.HostIp !== 'string'
				|| !Number.isSafeInteger(port)
				|| port <= 0
				|| port > 65535) {
				throw new Error('Container identity published port binding is invalid.');
			}
		}
	}
}
