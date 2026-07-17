import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const STAGES = new Set([
	'runtime-pre-lock', 'lock', 'runtime-post-lock', 'credentials', 'fixture', 'preflight',
	'warmup', 'initial-login-ack', 'users-maintenance', 'boundary', 'measured', 'validation',
	'final-continuity', 'classification',
]);

export function buildMeasurementRejection(stage, exitStatus) {
	if (!STAGES.has(stage)) {
		throw new Error('Measurement rejection stage is not an approved non-secret value.');
	}
	if (!Number.isInteger(exitStatus) || exitStatus <= 0 || exitStatus > 255) {
		throw new Error('Measurement rejection exit status must be an integer from 1 through 255.');
	}
	return {
		measurementStatus: 'rejected',
		evidenceIntegrity: 'incomplete',
		automaticAdoption: false,
		stage,
		exitStatus,
	};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const [outputPath, stage, exitStatusRaw] = process.argv.slice(2);
	if (!outputPath) {
		throw new Error('Usage: measurement-rejection.mjs <output-path> <stage> <exit-status>');
	}
	const rejection = buildMeasurementRejection(stage, Number(exitStatusRaw));
	await writeFile(outputPath, `${JSON.stringify(rejection, null, 2)}\n`, {flag: 'wx', mode: 0o600});
}
