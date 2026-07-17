import fs from 'node:fs';
import path from 'node:path';

export function allocateReportDirectory(reportsRoot, {
	datasetId, fixtureRunId, startedAt, nonce,
}) {
	const safeDatasetId = safeSegment(datasetId);
	const safeFixtureRunId = safeSegment(fixtureRunId);
	if (typeof startedAt !== 'string' || !Number.isFinite(Date.parse(startedAt))) {
		throw new Error('Report startedAt must be an RFC3339-compatible instant.');
	}
	if (!/^[a-f0-9]{16}$/.test(nonce ?? '')) {
		throw new Error('Report directory nonce must be 16 lowercase hexadecimal characters.');
	}
	const reportDirectory = path.join(
		reportsRoot,
		`${safeDatasetId}__${safeFixtureRunId}__${startedAt.replaceAll(':', '-')}__${nonce}__report`
	);
	try {
		fs.mkdirSync(reportDirectory, { recursive: false, mode: 0o700 });
	} catch (error) {
		if (error.code === 'EEXIST') {
			throw new Error(`Report directory collision; refusing to reuse existing path: ${reportDirectory}`, { cause: error });
		}
		throw error;
	}
	fs.mkdirSync(path.join(reportDirectory, 'raw'), { recursive: false, mode: 0o700 });
	fs.mkdirSync(path.join(reportDirectory, 'normalized'), { recursive: false, mode: 0o700 });
	return reportDirectory;
}

export async function terminateChildProcess(child, exitPromise, {
	gracefulTimeoutMs = 3000,
	killTimeoutMs = 1000,
} = {}) {
	if (child.exitCode === null) child.kill('SIGTERM');
	const graceful = await withTimeout(exitPromise, gracefulTimeoutMs, null);
	if (graceful) return graceful;
	if (child.exitCode === null) child.kill('SIGKILL');
	const killed = await withTimeout(exitPromise, killTimeoutMs, null);
	if (killed) return killed;
	const error = new Error('Child process could not be reaped; canonical runner lock must remain held.');
	error.code = 'CHILD_NOT_REAPED';
	throw error;
}

export async function withTimeout(promise, milliseconds, timeoutValue) {
	let timer;
	try {
		return await Promise.race([
			promise,
			new Promise((resolve) => {
				timer = setTimeout(() => resolve(timeoutValue), milliseconds);
			}),
		]);
	} finally {
		clearTimeout(timer);
	}
}

function safeSegment(value) {
	if (typeof value !== 'string' || !/^[A-Za-z0-9._-]+$/.test(value)) {
		throw new Error('DATASET_ID and FIXTURE_RUN_ID may contain only letters, digits, dot, underscore, and hyphen.');
	}
	return value;
}
