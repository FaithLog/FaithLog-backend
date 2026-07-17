import {mkdir} from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';

export async function createExecutionDirectory(parentDirectory, executionRunId) {
	if (typeof parentDirectory !== 'string' || parentDirectory.length === 0) {
		throw new Error('Execution report parent directory is required.');
	}
	if (typeof executionRunId !== 'string' || !/^EXEC193_[A-Za-z0-9_-]{1,56}$/.test(executionRunId)) {
		throw new Error('PERF_EXECUTION_RUN_ID must be a fresh EXEC193_ identifier (max 64 characters).');
	}
	await mkdir(parentDirectory, {recursive: true, mode: 0o700});
	const executionDirectory = path.join(parentDirectory, executionRunId);
	try {
		await mkdir(executionDirectory, {mode: 0o700});
	} catch (error) {
		if (error?.code === 'EEXIST') {
			throw new Error(`Execution report directory already exists and cannot be reused: ${executionDirectory}`);
		}
		throw error;
	}
	return executionDirectory;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const directory = await createExecutionDirectory(process.argv[2], process.argv[3]);
	process.stdout.write(directory);
}
