import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);

function readScenario(name) {
	return readFileSync(new URL(name, SCENARIO_ROOT), 'utf8');
}

test('runner accepts the approved detached-source proof when OCI revision labels are unavailable', async () => {
	const contractUrl = new URL('source-image-provenance.mjs', SCENARIO_ROOT);
	assert.equal(existsSync(fileURLToPath(contractUrl)), true);
	const { validateSourceImageProvenance } = await import(contractUrl);
	const sourceWorktree = '/private/tmp/FaithLog-perf-206-deploy';
	const facts = {
		schemaVersion: 1,
		proofMode: 'clean-detached-checkout-image-created-after-checkout',
		sourceWorktree,
		composeWorkingDir: sourceWorktree,
		revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		detached: true,
		clean: true,
		checkoutAt: '2026-07-16T04:20:28.000Z',
		imageId: `sha256:${'1'.repeat(64)}`,
		imageCreatedAt: '2026-07-16T04:22:48.810Z',
		apiContractSha256: '2'.repeat(64),
		limitation: 'image-alone-revision-label-unavailable',
	};
	const expected = {
		sourceWorktree,
		revision: facts.revision,
		imageId: facts.imageId,
		apiContractSha256: facts.apiContractSha256,
	};
	assert.equal(validateSourceImageProvenance(facts, expected), facts);
	assert.throws(() => validateSourceImageProvenance({ ...facts, clean: false }, expected), /clean/i);
	assert.throws(() => validateSourceImageProvenance({ ...facts, imageCreatedAt: facts.checkoutAt }, expected), /after checkout/i);

	const runner = readScenario('run-before.sh');
	const capture = readScenario('capture-runtime-identity.sh');
	for (const source of [runner, capture]) {
		assert.match(source, /APP_SOURCE_WORKTREE/);
		assert.match(source, /source-image-provenance\.mjs/);
		assert.match(source, /com\.docker\.compose\.project\.working_dir/);
		assert.match(source, /docker image inspect[^\n]*\.Created/);
		assert.doesNotMatch(source, /org\.opencontainers\.image\.(revision|api-contract-sha256)/);
	}
	assert.match(readScenario('assert-runtime-continuity.mjs'), /sourceImageProvenance/);
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
