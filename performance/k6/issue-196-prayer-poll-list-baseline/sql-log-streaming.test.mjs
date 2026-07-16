import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('.', import.meta.url));
const FILTER = join(ROOT, 'filter-sql-log.mjs');
const SQL_STATEMENT = '2026-07-17T00:00:00.000Z DEBUG org.hibernate.SQL : select u1_0.id from users u1_0 where u1_0.id=?';

test('SQL evidence filtering streams logs larger than the Node heap without losing statement-only output', async () => {
	const result = await runFilter({
		chunks: 8_193,
		trailingLines: [`${SQL_STATEMENT}\n`],
	});

	assert.equal(result.signal, null);
	assert.equal(result.status, 0, result.stderr);
	assert.equal(result.stdout, `${SQL_STATEMENT}\n`);
});

test('a late bind logger record prevents every previously observed SQL statement from being persisted', async () => {
	const result = await runFilter({
		chunks: 1,
		leadingLines: [`${SQL_STATEMENT}\n`],
		trailingLines: ['TRACE org.hibernate.orm.jdbc.bind : binding parameter (1:VARCHAR) <- [redacted-test-value]\n'],
	});

	assert.equal(result.signal, null);
	assert.equal(result.status, 2, result.stderr);
	assert.equal(result.stdout, '');
	assert.doesNotMatch(result.stderr, /redacted-test-value/);
});

async function runFilter({ chunks, leadingLines = [], trailingLines = [] }) {
	const child = spawn(process.execPath, ['--max-old-space-size=32', FILTER], {
		stdio: ['pipe', 'pipe', 'pipe'],
	});
	let stdout = '';
	let stderr = '';
	child.stdout.setEncoding('utf8');
	child.stderr.setEncoding('utf8');
	child.stdout.on('data', (value) => { stdout += value; });
	child.stderr.on('data', (value) => { stderr += value; });

	const timer = setTimeout(() => child.kill('SIGKILL'), 20_000);
	const noise = `${'x'.repeat((64 * 1_024) - 1)}\n`;
	try {
		for (const line of leadingLines) await write(child, line);
		for (let index = 0; index < chunks; index += 1) await write(child, noise);
		for (const line of trailingLines) await write(child, line);
		child.stdin.end();
		const [status, signal] = await once(child, 'exit');
		return { status, signal, stdout, stderr };
	} finally {
		clearTimeout(timer);
	}
}

async function write(child, value) {
	if (!child.stdin.write(value)) await once(child.stdin, 'drain');
}
