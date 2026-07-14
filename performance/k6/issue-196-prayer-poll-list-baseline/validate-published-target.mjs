import { pathToFileURL } from 'node:url';

export function validatePublishedTarget(baseUrl, bindingLines) {
	let url;
	try {
		url = new URL(baseUrl);
	} catch {
		throw new Error('BASE_URL must be an absolute URL.');
	}
	if (url.protocol !== 'http:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
		throw new Error('BASE_URL must be a credential-free HTTP origin without path, query, or fragment.');
	}
	const target = url.hostname === '127.0.0.1'
		? { family: 'ipv4', host: '127.0.0.1', compatibleHosts: new Set(['127.0.0.1', '0.0.0.0']) }
		: url.hostname === '[::1]'
			? { family: 'ipv6', host: '::1', compatibleHosts: new Set(['::1', '::']) }
			: null;
	if (!target) throw new Error('BASE_URL host must be the explicit numeric loopback 127.0.0.1 or [::1].');
	const hostPort = Number(url.port || '80');
	if (!Number.isInteger(hostPort) || hostPort < 1 || hostPort > 65535) throw new Error('BASE_URL port is invalid.');
	if (!Array.isArray(bindingLines) || bindingLines.length === 0) throw new Error('Docker published bindings are required.');
	const parsedBindings = bindingLines.map(parseBinding);
	const compatible = parsedBindings.filter((binding) => (
		binding.family === target.family && target.compatibleHosts.has(binding.host) && binding.port === hostPort
	));
	if (compatible.length !== 1) {
		throw new Error('Exactly one address-family-compatible Docker binding must match the BASE_URL host port.');
	}
	return {
		targetHost: target.host,
		addressFamily: target.family,
		hostPort,
		compatibleBindingCount: compatible.length,
		bindingHost: compatible[0].host,
	};
}

function parseBinding(line) {
	if (typeof line !== 'string' || line.length === 0 || line.trim() !== line) throw new Error('Malformed Docker published binding.');
	const ipv6 = /^\[([0-9a-fA-F:]+)]:(\d{1,5})$/.exec(line);
	if (ipv6) return checkedBinding('ipv6', ipv6[1].toLowerCase(), ipv6[2]);
	const ipv4 = /^(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})$/.exec(line);
	if (!ipv4) throw new Error('Malformed Docker published binding.');
	const octets = ipv4[1].split('.').map(Number);
	if (octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
		throw new Error('Malformed IPv4 Docker binding.');
	}
	return checkedBinding('ipv4', ipv4[1], ipv4[2]);
}

function checkedBinding(family, host, portText) {
	const port = Number(portText);
	if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Docker binding port is invalid.');
	return { family, host, port };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
	try {
		const envMode = process.argv[2] === '--host-port';
		const result = envMode
			? validatePublishedTarget(process.env.BASE_URL_VALUE, (process.env.PUBLISHED_BINDINGS_VALUE || '').split(/\r?\n/).filter(Boolean))
			: validatePublishedTarget(process.argv[2], process.argv.slice(3));
		process.stdout.write(envMode ? String(result.hostPort) : JSON.stringify(result));
	} catch (error) {
		process.stderr.write(`${error.message}\n`);
		process.exit(2);
	}
}
