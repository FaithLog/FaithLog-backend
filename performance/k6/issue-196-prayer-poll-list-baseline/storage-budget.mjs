import { lstatSync, readdirSync, realpathSync } from 'node:fs';
import { resolve, sep } from 'node:path';

const DECIMAL = /^(0|[1-9][0-9]*)$/;
const MINIMUM_SAFETY_HEADROOM_BYTES = 2n * 1024n * 1024n * 1024n;

export function evaluateStorageBudget(input) {
	const values = Object.fromEntries([
		'availableBytes', 'remainingEndpoints', 'compressedSqlMaxBytesPerEndpoint',
		'nonSqlMaxBytesPerEndpoint', 'daemonLogMaximumRetainedBytes', 'safetyHeadroomBytes',
	].map((name) => [name, decimal(input?.[name], name)]));
	if (values.remainingEndpoints < 0n) throw new Error('remainingEndpoints must be nonnegative.');
	if (values.compressedSqlMaxBytesPerEndpoint <= 0n || values.nonSqlMaxBytesPerEndpoint <= 0n
		|| values.daemonLogMaximumRetainedBytes <= 0n) {
		throw new Error('Storage evidence and daemon bounds must be positive.');
	}
	if (values.safetyHeadroomBytes < MINIMUM_SAFETY_HEADROOM_BYTES) {
		throw new Error('Storage safety headroom must be at least 2 GiB.');
	}
	const projectedEndpointBytes = values.remainingEndpoints
		* (values.compressedSqlMaxBytesPerEndpoint + values.nonSqlMaxBytesPerEndpoint);
	const projectedRequiredBytes = projectedEndpointBytes
		+ values.daemonLogMaximumRetainedBytes + values.safetyHeadroomBytes;
	return {
		availableBytes: values.availableBytes.toString(),
		remainingEndpoints: values.remainingEndpoints.toString(),
		projectedEndpointBytes: projectedEndpointBytes.toString(),
		daemonLogMaximumRetainedBytes: values.daemonLogMaximumRetainedBytes.toString(),
		safetyHeadroomBytes: values.safetyHeadroomBytes.toString(),
		projectedRequiredBytes: projectedRequiredBytes.toString(),
		safe: values.availableBytes >= projectedRequiredBytes,
	};
}

export function measureNonSqlEvidence(directoryPath, sqlArtifactPath, maximumBytes) {
	const requestedRoot = resolve(directoryPath);
	if (lstatSync(requestedRoot).isSymbolicLink()) throw new Error('Evidence directory must not be a symlink.');
	const root = realpathSync(requestedRoot);
	const sqlArtifact = realpathSync(resolve(sqlArtifactPath));
	if (!sqlArtifact.startsWith(`${root}${sep}`)) throw new Error('SQL artifact must be inside the evidence directory.');
	const limit = decimal(maximumBytes, 'maximumBytes');
	if (limit <= 0n) throw new Error('maximumBytes must be positive.');
	let bytes = 0n;
	const visit = (directory) => {
		for (const entry of readdirSync(directory, { withFileTypes: true })) {
			const path = resolve(directory, entry.name);
			const stat = lstatSync(path);
			if (stat.isSymbolicLink()) throw new Error('Evidence directory must not contain symlinks.');
			if (stat.isDirectory()) visit(path);
			else if (stat.isFile() && realpathSync(path) !== sqlArtifact) bytes += BigInt(stat.size);
			else if (!stat.isFile()) throw new Error('Evidence directory contains a non-regular entry.');
		}
	};
	visit(root);
	return { nonSqlEvidenceBytes: bytes.toString(), maximumBytes: limit.toString(), safe: bytes <= limit };
}

function decimal(value, name) {
	if (typeof value !== 'string' || !DECIMAL.test(value)) throw new Error(`${name} must be a decimal string.`);
	return BigInt(value);
}

if (import.meta.url === `file://${process.argv[1]}`) {
	try {
		const result = process.argv[2] === '--measure-non-sql'
			? measureNonSqlEvidence(process.argv[3], process.argv[4], process.env.PERF_NON_SQL_EVIDENCE_MAX_BYTES)
			: evaluateStorageBudget({
				availableBytes: process.env.AVAILABLE_BYTES,
				remainingEndpoints: process.env.REMAINING_ENDPOINTS,
				compressedSqlMaxBytesPerEndpoint: process.env.PERF_SQL_GZIP_MAX_BYTES,
				nonSqlMaxBytesPerEndpoint: process.env.PERF_NON_SQL_EVIDENCE_MAX_BYTES,
				daemonLogMaximumRetainedBytes: process.env.DAEMON_LOG_MAXIMUM_RETAINED_BYTES,
				safetyHeadroomBytes: process.env.PERF_STORAGE_SAFETY_HEADROOM_BYTES,
			});
		process.stdout.write(`${JSON.stringify(result)}\n`);
		if (!result.safe) process.exitCode = 2;
	} catch (error) {
		console.error(error.message);
		process.exitCode = 1;
	}
}
