import assert from 'node:assert/strict';
import { existsSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { mkdtempSync } from 'node:fs';
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
	assert.match(runner, /PERF_EXPECTED_HARNESS_HEAD/);
	assert.match(runner, /PERF_EXPECTED_HARNESS_CONTRACT_DIGEST/);
	assert.ok(runner.indexOf('PERF_EXPECTED_HARNESS_HEAD') < runner.indexOf('acquire_notification_batch_locks'));
	assert.ok(runner.indexOf('PRELOCK_HARNESS_SOURCE_PATH') < runner.indexOf('acquire_notification_batch_locks'));
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
		contractDigest: '3'.repeat(64), trackedInputs: { 'build.gradle': '4'.repeat(40) },
		deployedAppImage: 'not-applicable-local-gradle-test-profile',
	};
	assert.equal(validateHarnessSourceEvidence(source, {
		head: source.head, originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), source);
	assert.throws(() => validateHarnessSourceEvidence(source, {
		originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), /head/i);
	assert.throws(() => validateHarnessSourceEvidence(source, {
		head: '9'.repeat(40), originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), /head/i);
	assert.throws(() => validateHarnessSourceEvidence(source, {
		head: source.head, originDevelop: source.originDevelop, contractDigest: '9'.repeat(64),
	}), /digest/i);
	assert.throws(() => validateHarnessSourceEvidence({ ...source, clean: false }, {
		head: source.head, originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), /clean/i);
	assert.throws(() => validateHarnessSourceEvidence({ ...source, srcMainDiffCount: 1 }, {
		head: source.head, originDevelop: source.originDevelop, contractDigest: source.contractDigest,
	}), /src\/main/i);

	const artifactContractUrl = new URL('harness-artifact-provenance.mjs', SCENARIO_ROOT);
	assert.equal(existsSync(fileURLToPath(artifactContractUrl)), true);
	const { validateHarnessArtifactEvidence } = await import(artifactContractUrl);
	const artifact = { schemaVersion: 1, fileCount: 4, digest: '4'.repeat(64) };
	assert.equal(validateHarnessArtifactEvidence(artifact), artifact);
	assert.throws(() => validateHarnessArtifactEvidence({ ...artifact, fileCount: 0 }), /artifact/i);
	const artifactSource = readScenario('harness-artifact-provenance.mjs');
	for (const root of [
		'build/classes/java/main', 'build/resources/main',
		'build/classes/java/test/com/faithlog/performance/notification', 'build/resources/test',
	]) assert.match(artifactSource, new RegExp(root.replaceAll('/', '\\/')));
	assert.match(artifactSource, /symbolic link|symlink/i);

	const continuity = readScenario('assert-harness-provenance-continuity.mjs');
	assert.match(continuity, /artifact.*at least two|At least two.*artifact/is);
	assert.match(continuity, /unique.*source|source.*unique/is);
	assert.match(continuity, /unique.*artifact|artifact.*unique/is);
	assert.match(continuity, /source-only/);
	const { validateHarnessPhaseContract } = await import(new URL(
		'assert-harness-provenance-continuity.mjs', SCENARIO_ROOT));
	assert.doesNotThrow(() => validateHarnessPhaseContract(['prelock', 'locked'], [], 'source-only'));
	assert.doesNotThrow(() => validateHarnessPhaseContract(
		['prelock', 'final'], ['preworkload', 'final'], 'full'));
	assert.throws(() => validateHarnessPhaseContract(['prelock', 'prelock'], [], 'source-only'), /unique/i);
	assert.throws(() => validateHarnessPhaseContract(['prelock', 'final'], ['preworkload'], 'full'), /two.*artifact/i);
	assert.throws(() => validateHarnessPhaseContract(
		['prelock', 'final'], ['preworkload', 'preworkload'], 'full'), /unique/i);

	const root = mkdtempSync(join(tmpdir(), 'faithlog-198-artifact-'));
	try {
		const roots = [
			'build/classes/java/main', 'build/resources/main',
			'build/classes/java/test/com/faithlog/performance/notification',
			'build/classes/java/test/com/faithlog/support', 'build/resources/test',
		];
		for (const directory of roots) mkdirSync(join(root, directory), { recursive: true });
		writeFileSync(join(root,
			'build/classes/java/test/com/faithlog/performance/notification/NotificationBatchBeforeScenarioTest.class'), 'test');
		for (const [index, directory] of roots.entries()) writeFileSync(join(root, directory, `artifact-${index}`), `${index}`);
		const { collectHarnessArtifactEvidence } = await import(artifactContractUrl);
		assert.equal(collectHarnessArtifactEvidence(root).fileCount, 6);
		symlinkSync(join(root, 'build.gradle'), join(root, 'build/resources/test/forbidden-link'));
		assert.throws(() => collectHarnessArtifactEvidence(root), /symbolic link/i);
		rmSync(join(root, 'build/resources/test/forbidden-link'));
		rmSync(join(root, 'build/resources/main'), { recursive: true });
		assert.throws(() => collectHarnessArtifactEvidence(root));
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
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
