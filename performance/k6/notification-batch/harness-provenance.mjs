import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { realpathSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const BASE_COMMIT = '6796ed146244d8f3f5b5dd7048ebe16865084a97';
const INPUT_PATHS = [
	'src/main/java/com/faithlog/notification',
	'src/main/java/com/faithlog/campus/service/CampusDutyAssignmentService.java',
	'src/main/resources/db/migration',
	'src/test/java/com/faithlog/performance/notification/NotificationBatchBeforeScenarioTest.java',
	'build.gradle',
	'settings.gradle',
	'gradlew',
	'gradle/wrapper/gradle-wrapper.properties',
];

function exactObject(value, keys, label) {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${label} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), [...keys].sort(), `${label} schema mismatch`);
}

export function validateHarnessSourceEvidence(value, expected) {
	exactObject(value, [
		'schemaVersion', 'head', 'originDevelop', 'mergeBase', 'clean', 'srcMainDiffCount',
		'contractDigest', 'trackedInputs', 'deployedAppImage',
	], 'harness source evidence');
	exactObject(expected, ['head', 'originDevelop', 'contractDigest'], 'expected harness source evidence');
	assert.equal(value.schemaVersion, 1);
	for (const field of ['head', 'originDevelop', 'mergeBase']) assert.match(value[field], /^[a-f0-9]{40}$/);
	assert.equal(value.clean, true, 'executed #198 worktree must remain clean');
	assert.equal(value.srcMainDiffCount, 0, 'src/main must have zero diff from origin/develop');
	assert.equal(value.head, expected.head, 'harness HEAD differs from runtime approval');
	assert.equal(value.originDevelop, expected.originDevelop);
	assert.equal(value.mergeBase, expected.originDevelop, 'merge-base must equal exact origin/develop');
	assert.match(value.contractDigest, /^[a-f0-9]{64}$/);
	assert.equal(value.contractDigest, expected.contractDigest, 'harness contract digest differs from runtime approval');
	exactObject(value.trackedInputs, Object.keys(value.trackedInputs), 'tracked harness inputs');
	assert.ok(Object.keys(value.trackedInputs).length > 0, 'tracked harness inputs must not be empty');
	for (const [path, objectId] of Object.entries(value.trackedInputs)) {
		assert.ok(path.length > 0 && !path.startsWith('/'), 'tracked harness input path must be relative');
		assert.match(objectId, /^[a-f0-9]{40}$/, 'tracked harness input object ID must be exact');
	}
	assert.equal(value.deployedAppImage, 'not-applicable-local-gradle-test-profile');
	return value;
}

function git(root, args, options = {}) {
	return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', ...options }).trimEnd();
}

function collect(rootInput) {
	const root = realpathSync(rootInput);
	const head = git(root, ['rev-parse', 'HEAD']);
	const originDevelop = git(root, ['rev-parse', 'origin/develop']);
	const mergeBase = git(root, ['merge-base', 'HEAD', 'origin/develop']);
	const clean = git(root, ['status', '--porcelain=v1', '--untracked-files=all']) === '';
	const srcMainDiff = git(root, ['diff', '--name-only', 'origin/develop', '--', 'src/main']);
	const inventory = git(root, ['ls-tree', '-r', 'HEAD', '--', ...INPUT_PATHS]);
	assert.ok(inventory.length > 0, 'harness source/Gradle inventory must not be empty');
	const trackedInputs = Object.fromEntries(inventory.split(/\r?\n/).map((line) => {
		const match = line.match(/^[0-7]{6} blob ([a-f0-9]{40})\t(.+)$/);
		assert.ok(match, `unexpected harness input inventory entry: ${line}`);
		return [match[2], match[1]];
	}));
	const contractDigest = createHash('sha256').update(`${inventory}\n`).digest('hex');
	const value = {
		schemaVersion: 1, head, originDevelop, mergeBase, clean,
		srcMainDiffCount: srcMainDiff ? srcMainDiff.split(/\r?\n/).length : 0,
		contractDigest, trackedInputs,
		deployedAppImage: 'not-applicable-local-gradle-test-profile',
	};
	validateHarnessSourceEvidence(value, {
		head: process.env.PERF_EXPECTED_HARNESS_HEAD,
		originDevelop: BASE_COMMIT,
		contractDigest: process.env.PERF_EXPECTED_HARNESS_CONTRACT_DIGEST,
	});
	return value;
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
	const [command, root, output] = process.argv.slice(2);
	if (command !== 'capture' || !output) throw new Error('harness provenance capture arguments are required');
	writeFileSync(output, `${JSON.stringify(collect(root), null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}
