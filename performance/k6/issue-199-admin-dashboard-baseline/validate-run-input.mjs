import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const [manifestPath, rawModes] = process.argv.slice(2);

try {
	assert.ok(manifestPath, 'INPUT_MANIFEST is required.');
	assert.ok(rawModes, 'DATASET_MODES is required.');
	const parts = rawModes.split(',');
	assert.ok(parts.every((part) => part.trim()), 'DATASET_MODES must not contain empty entries.');
	const modes = parts.map((part) => part.trim());
	assert.equal(new Set(modes).size, modes.length, 'Duplicate dataset mode is not allowed.');
	for (const mode of modes) {
		assert.ok(['empty', 'small', 'thousand'].includes(mode), `Unsupported dataset mode: ${mode}`);
	}

	const manifest = JSON.parse(fs.readFileSync(path.resolve(manifestPath), 'utf8'));
	assert.equal(manifest.issue, 199, 'Manifest issue must be 199.');
	for (const mode of modes) {
		assert.ok(manifest.modes?.[mode], `Missing dataset mode in manifest: ${mode}`);
	}
	for (const component of ['app', 'postgres', 'redis']) {
		assert.match(
			manifest.runtimeTarget?.[component]?.service || '',
			/^[A-Za-z0-9._-]+$/,
			`runtimeTarget.${component}.service is required and must be a safe exact label.`,
		);
	}
	assert.ok(
		Number.isInteger(manifest.runtimeTarget?.app?.containerPort)
			&& manifest.runtimeTarget.app.containerPort > 0
			&& manifest.runtimeTarget.app.containerPort <= 65535,
		'runtimeTarget.app.containerPort must be an integer from 1 to 65535.',
	);
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}
