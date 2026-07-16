import assert from 'node:assert/strict';
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { pathToFileURL } from 'node:url';

const MODULE_URL = new URL('./docker-db-identity.mjs', import.meta.url);

function fakeDocker(temporary) {
	const bin = join(temporary, 'bin');
	mkdirSync(bin);
	const command = join(bin, 'docker');
	writeFileSync(command, `#!/usr/bin/env bash
set -euo pipefail
[[ "\${1:-}" == exec && "\${2:-}" == -i ]] || exit 0
input="$(cat)"
[[ "$input" == 'select issue_196_identity;' ]] || exit 73
[[ -n "\${PGPASSWORD:-}" ]] || exit 74
[[ -z "\${PERF_ADMIN_PASSWORD:-}" && -z "\${PERF_MEMBER_PASSWORD:-}" ]] || exit 75
[[ "\${FAKE_EMPTY_OUTPUT:-false}" == true ]] && exit 0
printf '%s\n' '{"currentDatabase":"faithlog","postmasterStartedAt":"2026-07-17T00:00:00.000Z"}'
`);
	chmodSync(command, 0o755);
	return command;
}

test('DB identity collector attaches stdin to docker exec and parses the JSON result', async () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-db-identity-'));
	try {
		const dockerCommand = fakeDocker(temporary);
		const { captureDockerDbIdentity } = await import(`${pathToFileURL(MODULE_URL.pathname).href}?success=${Date.now()}`);
		const result = captureDockerDbIdentity({
			dockerCommand,
			container: 'approved-db',
			user: 'faithlog',
			database: 'faithlog',
			password: 'db-secret-not-for-output',
			applicationName: 'faithlog_issue196_observer',
			sql: 'select issue_196_identity;',
			env: { PATH: process.env.PATH },
		});
		assert.equal(result.currentDatabase, 'faithlog');
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('DB identity collector rejects empty stdout without exposing SQL or credentials', async () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-db-identity-empty-'));
	try {
		const dockerCommand = fakeDocker(temporary);
		const { captureDockerDbIdentity } = await import(`${pathToFileURL(MODULE_URL.pathname).href}?empty=${Date.now()}`);
		assert.throws(() => captureDockerDbIdentity({
			dockerCommand,
			container: 'approved-db',
			user: 'faithlog',
			database: 'faithlog',
			password: 'db-secret-not-for-output',
			applicationName: 'faithlog_issue196_observer',
			sql: 'select issue_196_identity;',
			env: { PATH: process.env.PATH, FAKE_EMPTY_OUTPUT: 'true' },
		}), (error) => {
			assert.match(error.message, /DB identity collector returned empty output/);
			assert.doesNotMatch(error.message, /select issue_196_identity|db-secret-not-for-output/);
			return true;
		});
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('every issue-local docker collector that consumes SQL stdin attaches container stdin', () => {
	const seed = readFileSync(new URL('./seed-fixture.mjs', import.meta.url), 'utf8');
	const shape = readFileSync(new URL('./shape-fixture.sh', import.meta.url), 'utf8');
	const runner = readFileSync(new URL('./run-baseline.sh', import.meta.url), 'utf8');
	assert.match(seed, /captureDockerDbIdentity\(/);
	assert.equal((shape.match(/docker exec -i /g) || []).length, 1, 'shape DB identity collector must attach stdin');
	assert.equal((runner.match(/docker exec -i /g) || []).length, 3,
		'runner identity, table-stat, and activity collectors must attach stdin');
});
