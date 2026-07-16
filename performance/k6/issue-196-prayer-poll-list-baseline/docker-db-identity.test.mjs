import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
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
if [[ "\${FAKE_CHILD_FAILURE:-false}" == true ]]; then
	printf '%s\n' 'select issue_196_identity; db-secret-not-for-output synthetic-child-stderr' >&2
	exit 76
fi
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
	assert.equal((shape.match(/docker exec -i /g) || []).length, 2,
		'shape DB identity and fixture-owned mutation paths must attach stdin');
	assert.equal((runner.match(/docker exec -i /g) || []).length, 3,
		'runner identity, table-stat, and activity collectors must attach stdin');
});

test('DB identity collector sanitizes child-process stderr on failure', async () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-db-identity-child-error-'));
	try {
		const dockerCommand = fakeDocker(temporary);
		const { captureDockerDbIdentity } = await import(`${pathToFileURL(MODULE_URL.pathname).href}?child=${Date.now()}`);
		assert.throws(() => captureDockerDbIdentity({
			dockerCommand,
			container: 'approved-db',
			user: 'faithlog',
			database: 'faithlog',
			password: 'db-secret-not-for-output',
			applicationName: 'faithlog_issue196_observer',
			sql: 'select issue_196_identity;',
			env: { PATH: process.env.PATH, FAKE_CHILD_FAILURE: 'true' },
		}), (error) => {
			assert.match(error.message, /DB identity collector child failed/);
			assert.doesNotMatch(error.message, /select issue_196_identity|db-secret-not-for-output|synthetic-child-stderr/);
			return true;
		});
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('shape streams SQL through attached stdin so psql variables are expanded', () => {
	const shape = readFileSync(new URL('./shape-fixture.sh', import.meta.url), 'utf8');
	assert.match(shape, /shape_result=.*docker exec -i[\s\S]*-v open_id=[\s\S]*-f - <<< "\$\{sql\}"/);
	assert.doesNotMatch(shape, /-c "\$\{sql\}"/);

	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-shape-stdin-'));
	try {
		const command = join(temporary, 'docker');
		writeFileSync(command, `#!/usr/bin/env bash
set -euo pipefail
[[ "\${1:-}" == exec && "\${2:-}" == -i && "$*" == *'-v open_id=41'* && "$*" == *'-f -'* ]] || exit 81
input="$(cat)"
[[ "$input" == *":'open_id'"* ]] || exit 82
printf '%s\n' '{"open":{"startsAt":"2026-07-17T00:00:00Z"}}'
`);
		chmodSync(command, 0o755);
		const result = spawnSync(command, [
			'exec', '-i', '-e', 'PGPASSWORD', 'approved-db', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
			'-v', 'open_id=41', '-f', '-',
		], { encoding: 'utf8', input: "SELECT :'open_id';", env: { PATH: process.env.PATH, PGPASSWORD: 'db-secret' } });
		assert.equal(result.status, 0, result.stderr);
		assert.equal(JSON.parse(result.stdout).open.startsAt, '2026-07-17T00:00:00Z');
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});
