import { execFileSync } from 'node:child_process';

export function captureContainerIdentity(containerId, {
	execFileSyncImpl = execFileSync,
	environment = process.env,
} = {}) {
	const inspected = JSON.parse(execFileSyncImpl(
		'docker',
		['inspect', containerId],
		{ encoding: 'utf8', env: sanitizedChildEnvironment(environment) },
	));
	if (!Array.isArray(inspected) || inspected.length !== 1) {
		throw new Error('Docker inspect must resolve exactly one container.');
	}
	const value = inspected[0];
	return {
		containerId: value.Id,
		name: value.Name,
		imageId: value.Image,
		startedAt: value.State?.StartedAt,
		composeProject: value.Config?.Labels?.['com.docker.compose.project'],
		composeService: value.Config?.Labels?.['com.docker.compose.service'],
		publishedPorts: value.NetworkSettings?.Ports,
	};
}

export function sanitizedChildEnvironment(environment) {
	return Object.fromEntries(Object.entries(environment).filter(([name]) => (
		!/(PASSWORD|TOKEN|SECRET|CREDENTIAL)/i.test(name)
		&& name !== 'PERF_ADMIN_EMAIL'
	)));
}
