import assert from 'node:assert/strict';
import { realpathSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export function writeFirstRejection(outputPath, { stage, reason, exitCode }) {
	assert.ok(typeof outputPath === 'string' && outputPath.length > 0, 'rejection output path is required');
	assert.match(stage, /^[a-z0-9][a-z0-9-]{0,79}$/, 'rejection stage is invalid');
	assert.match(reason, /^[a-z0-9][a-z0-9-]{0,119}$/, 'rejection reason is invalid');
	assert.ok(Number.isSafeInteger(Number(exitCode)) && Number(exitCode) > 0, 'rejection exitCode is invalid');
	const rejection = {
		issue: 198,
		accepted: false,
		automaticAdoption: false,
		evidenceIntegrity: 'rejected',
		measurementStatus: 'rejected',
		stage,
		reasons: [reason],
		exitCode: Number(exitCode),
		rejectedAt: new Date().toISOString(),
		secretsIncluded: false,
	};
	try {
		writeFileSync(outputPath, `${JSON.stringify(rejection, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
	} catch (error) {
		if (error?.code !== 'EEXIST') throw error;
	}
	return rejection;
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
	writeFirstRejection(process.env.REJECTION_PATH, {
		stage: process.env.REJECTION_STAGE,
		reason: process.env.REJECTION_REASON,
		exitCode: Number(process.env.REJECTION_EXIT_CODE),
	});
}
