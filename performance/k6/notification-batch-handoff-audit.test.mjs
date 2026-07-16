import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);

function readScenario(name) {
	return readFileSync(new URL(name, SCENARIO_ROOT), 'utf8');
}

test('runner binds the source and compiled harness it executes without an unused app container', async () => {
	const runner = readScenario('run-before.sh');
	const combined = `${runner}\n${readScenario('guard-runtime.sh')}\n${readScenario('capture-runtime-identity.sh')}`;
	assert.doesNotMatch(combined, /APP_CONTAINER|PERF_EXPECTED_APP_|source-image-provenance/);
	assert.match(runner, /harness-provenance\.mjs/);
	assert.match(runner, /harness-artifact-provenance\.mjs/);
	assert.ok(runner.indexOf('cleanTest testClasses') < runner.indexOf('harness-artifact-provenance.mjs'));
	assert.ok(runner.indexOf('harness-artifact-provenance.mjs') < runner.indexOf('--tests com.faithlog.performance.notification.NotificationBatchBeforeScenarioTest'));
	for (const phase of ['prelock', 'locked', 'preworkload', 'postworkload', 'final']) {
		assert.match(runner, new RegExp(`harness-source-${phase}\\.json`));
	}

	const sourceContractUrl = new URL('harness-provenance.mjs', SCENARIO_ROOT);
	assert.equal(existsSync(fileURLToPath(sourceContractUrl)), true);
	const { validateHarnessSourceEvidence } = await import(sourceContractUrl);
	const source = {
		schemaVersion: 1, head: '1'.repeat(40), originDevelop: '2'.repeat(40),
		mergeBase: '2'.repeat(40), clean: true, srcMainDiffCount: 0,
		contractDigest: '3'.repeat(64), deployedAppImage: 'not-applicable-local-gradle-test-profile',
	};
	assert.equal(validateHarnessSourceEvidence(source, {
		originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), source);
	assert.throws(() => validateHarnessSourceEvidence({ ...source, clean: false }, {
		originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), /clean/i);
	assert.throws(() => validateHarnessSourceEvidence({ ...source, srcMainDiffCount: 1 }, {
		originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), /src\/main/i);

	const artifactContractUrl = new URL('harness-artifact-provenance.mjs', SCENARIO_ROOT);
	assert.equal(existsSync(fileURLToPath(artifactContractUrl)), true);
	const { validateHarnessArtifactEvidence } = await import(artifactContractUrl);
	const artifact = { schemaVersion: 1, fileCount: 4, digest: '4'.repeat(64) };
	assert.equal(validateHarnessArtifactEvidence(artifact), artifact);
	assert.throws(() => validateHarnessArtifactEvidence({ ...artifact, fileCount: 0 }), /artifact/i);
});

test('optional report root keeps every fixture and run namespace exclusive', () => {
	const runner = readScenario('run-before.sh');
	const prepare = readScenario('prepare-fixtures.sh');
	for (const source of [runner, prepare]) {
		assert.match(source, /PERF_REPORT_ROOT/);
		assert.match(source, /notification-batch/);
	}
	assert.match(runner, /mkdir "\$\{RUN_DIR\}"/);
	assert.match(prepare, /mkdir "\$\{REPORT_DIR\}"/);
});
