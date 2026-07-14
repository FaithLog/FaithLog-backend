import assert from 'node:assert/strict';

const [baseUrlSource, containerPortSource, publishedPortsSource] = process.argv.slice(2);

try {
	assert.ok(baseUrlSource, 'BASE_URL is required.');
	const target = new URL(baseUrlSource);
	assert.equal(target.protocol, 'http:', 'BASE_URL must use local HTTP.');
	assert.ok(target.port, 'BASE_URL must include an explicit published port.');
	assert.equal(target.pathname, '/', 'BASE_URL must not include an application path.');
	assert.equal(target.search, '', 'BASE_URL must not include query parameters.');
	assert.equal(target.hash, '', 'BASE_URL must not include a fragment.');
	assert.equal(target.username, '', 'BASE_URL must not include credentials.');
	assert.equal(target.password, '', 'BASE_URL must not include credentials.');
	assert.ok(
		['127.0.0.1', '[::1]'].includes(target.hostname),
		'BASE_URL must use a numeric loopback host so the address family is exact.',
	);

	const containerPort = Number(containerPortSource);
	assert.ok(Number.isInteger(containerPort) && containerPort > 0 && containerPort <= 65535,
		'Expected app container port must be valid.');
	const publishedPorts = JSON.parse(publishedPortsSource || '{}');
	const bindingKey = `${containerPort}/tcp`;
	const bindings = publishedPorts[bindingKey];
	assert.ok(Array.isArray(bindings), `APP_CONTAINER has no published port for ${bindingKey}.`);
	assert.equal(bindings.length, 1, `APP_CONTAINER must have exactly one published binding for ${bindingKey}.`);
	const binding = bindings[0];
	assert.equal(String(binding.HostPort), target.port,
		`BASE_URL port ${target.port} does not match APP_CONTAINER published port ${binding.HostPort}.`);
	const allowedBindingAddresses = target.hostname === '127.0.0.1'
		? ['0.0.0.0', '127.0.0.1']
		: ['::', '::1'];
	assert.ok(
		allowedBindingAddresses.includes(binding.HostIp),
		`APP_CONTAINER published HostIp address family does not match BASE_URL: ${binding.HostIp}`,
	);

	process.stdout.write(JSON.stringify({
		baseUrl: target.origin,
		containerPort,
		hostIp: binding.HostIp,
		hostPort: Number(binding.HostPort),
	}));
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}
