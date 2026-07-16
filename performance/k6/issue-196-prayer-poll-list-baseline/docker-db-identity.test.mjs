import assert from 'node:assert/strict';
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
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
