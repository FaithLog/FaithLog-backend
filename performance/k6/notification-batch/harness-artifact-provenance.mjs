import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { lstatSync, readdirSync, readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateHarnessArtifactEvidence(value) {
	assert.deepEqual(Object.keys(value).sort(), ['digest', 'fileCount', 'schemaVersion'].sort(),
		'harness artifact evidence schema mismatch');
	assert.equal(value.schemaVersion, 1);
	assert.ok(Number.isSafeInteger(value.fileCount) && value.fileCount > 0,
		'harness artifact file count must be positive');
	assert.match(value.digest, /^[a-f0-9]{64}$/, 'harness artifact digest must be SHA-256');
	return value;
}

function walk(directory) {
	const files = [];
	for (const name of readdirSync(directory)) {
		const path = join(directory, name);
		const stat = lstatSync(path);
		assert.equal(stat.isSymbolicLink(), false, `symbolic link is forbidden in harness artifacts: ${path}`);
		if (stat.isDirectory()) files.push(...walk(path));
		else {
			assert.equal(stat.isFile(), true, `non-regular harness artifact is forbidden: ${path}`);
			files.push(path);
		}
	}
	return files;
}

export function collectHarnessArtifactEvidence(rootInput) {
	const root = realpathSync(rootInput);
	const roots = [
		join(root, 'build/classes/java/main'),
		join(root, 'build/resources/main'),
		join(root, 'build/classes/java/test/com/faithlog/performance/notification'),
		join(root, 'build/classes/java/test/com/faithlog/support'),
		join(root, 'build/resources/test'),
	];
	for (const directory of roots) realpathSync(directory);
	const files = roots.flatMap(walk).sort();
	assert.equal(new Set(files).size, files.length, 'duplicate harness artifact path is forbidden');
	assert.ok(files.some((path) => /NotificationBatchBeforeScenarioTest(?:\$.*)?\.class$/.test(path)),
		'compiled notification batch harness artifact is missing');
	const hash = createHash('sha256');
	for (const path of files) {
		hash.update(relative(root, path));
		hash.update('\0');
		hash.update(createHash('sha256').update(readFileSync(path)).digest('hex'));
		hash.update('\n');
	}
	return validateHarnessArtifactEvidence({ schemaVersion: 1, fileCount: files.length, digest: hash.digest('hex') });
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
	const [command, root, output] = process.argv.slice(2);
	if (command !== 'capture' || !output) throw new Error('harness artifact capture arguments are required');
	writeFileSync(output, `${JSON.stringify(collectHarnessArtifactEvidence(root), null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}
