import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const [beforePath, afterPath, outputPath] = process.argv.slice(2);
try {
	assert.ok(beforePath && afterPath && outputPath, 'Pre/post-lock snapshots and output are required.');
	const before = JSON.parse(fs.readFileSync(path.resolve(beforePath), 'utf8'));
	const after = JSON.parse(fs.readFileSync(path.resolve(afterPath), 'utf8'));
	assert.deepEqual(Object.keys(before.containers).sort(), ['app', 'postgres', 'redis']);
	assert.deepEqual(Object.keys(after.containers).sort(), ['app', 'postgres', 'redis']);
	for (const component of ['app', 'postgres', 'redis']) {
		assert.deepEqual(after.containers[component], before.containers[component], `${component} changed while acquiring the shared lock.`);
	}
	write({status: 'pre-post-lock-continuous', continuous: true, automaticAdoption: false});
} catch (error) {
	if (outputPath && !fs.existsSync(path.resolve(outputPath))) {
		try { write({status: 'runtime-identity-changed', continuous: false, automaticAdoption: false,
			failures: [{name: 'prePostLockTarget', actual: error.message}]}); } catch { /* preserve first error */ }
	}
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function write(value) {
	fs.writeFileSync(path.resolve(outputPath), `${JSON.stringify(value, null, 2)}\n`, {flag: 'wx', mode: 0o600});
}
