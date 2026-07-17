import assert from 'node:assert/strict';
import { readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateHarnessPhaseContract(sourcePhases, artifactPhases, mode) {
	assert.ok(sourcePhases.length >= 2, 'At least two harness source phases are required');
	assert.equal(new Set(sourcePhases).size, sourcePhases.length, 'Harness source phases must be unique');
	assert.ok(mode === 'source-only' || mode === 'full', 'HARNESS_PROVENANCE_MODE must be source-only or full');
	assert.equal(new Set(artifactPhases).size, artifactPhases.length, 'Harness artifact phases must be unique');
	if (mode === 'source-only') assert.equal(artifactPhases.length, 0, 'source-only mode forbids artifact phases');
	else assert.ok(artifactPhases.length >= 2, 'At least two harness artifact phases are required in full mode');
}

function main() {
	const runDir = process.env.RUN_DIR;
	assert.ok(runDir, 'RUN_DIR is required');
	const sourcePhases = (process.env.HARNESS_SOURCE_PHASES ?? '').split(',').map((value) => value.trim()).filter(Boolean);
	const artifactPhases = (process.env.HARNESS_ARTIFACT_PHASES ?? '').split(',').map((value) => value.trim()).filter(Boolean);
	const mode = process.env.HARNESS_PROVENANCE_MODE;
	validateHarnessPhaseContract(sourcePhases, artifactPhases, mode);
	const sources = sourcePhases.map((phase) => JSON.parse(readFileSync(join(runDir, `harness-source-${phase}.json`), 'utf8')));
	for (const [index, value] of sources.slice(1).entries()) {
		assert.deepEqual(value, sources[0], `harness source provenance changed at ${sourcePhases[index + 1]}`);
	}
	const artifacts = artifactPhases.map((phase) => JSON.parse(readFileSync(join(runDir, `harness-artifact-${phase}.json`), 'utf8')));
	for (const [index, value] of artifacts.slice(1).entries()) {
		assert.deepEqual(value, artifacts[0], `compiled harness artifact changed at ${artifactPhases[index + 1]}`);
	}
	writeFileSync(process.env.HARNESS_PROVENANCE_REPORT_PATH, `${JSON.stringify({
		status: 'verified', sourcePhases, artifactPhases,
		head: sources[0].head, originDevelop: sources[0].originDevelop,
		sourceContractDigest: sources[0].contractDigest,
		artifactDigest: artifacts[0]?.digest ?? null,
		deployedAppImage: 'not-applicable-local-gradle-test-profile',
		checkedAt: new Date().toISOString(),
	}, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) main();
