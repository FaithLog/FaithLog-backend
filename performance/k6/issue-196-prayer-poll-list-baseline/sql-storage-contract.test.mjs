import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import {
	chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, truncateSync, writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { Readable } from 'node:stream';
import { test } from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { gunzipSync } from 'node:zlib';

const ROOT = dirname(fileURLToPath(import.meta.url));
const FIRST = 'FAITHLOG_SQL_WINDOW_FIRST_fixture_execution_endpoint';
const FINAL = 'FAITHLOG_SQL_WINDOW_FINAL_fixture_execution_endpoint';
const SQL_A = 'DEBUG org.hibernate.SQL : select p1_0.id from polls p1_0 where p1_0.id=1';
const SQL_B = 'DEBUG org.hibernate.SQL : select p1_0.id from polls p1_0 where p1_0.id=2';

test('SQL window capture atomically writes full-fidelity gzip with exact digest and counts', async () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-sql-gzip-'));
	try {
		const paths = evidencePaths(temporary);
		const { captureSqlEvidence, validateSqlEvidence } = await sqlEvidenceModule();
		const result = await captureSqlEvidence(Readable.from([
			`before-window\n${FIRST}\n${SQL_A}\n`, `${SQL_B}\n${FINAL}\nafter-window\n`,
		]), captureOptions(paths));
		assert.equal(result.status, 'complete');
		assert.equal(statSync(paths.artifact).mode & 0o777, 0o600);
		assert.equal(statSync(paths.attestation).mode & 0o777, 0o600);
		assert.equal(statSync(paths.ready).mode & 0o777, 0o600);
		const expected = `${SQL_A}\n${SQL_B}\n`;
		assert.equal(gunzipSync(readFileSync(paths.artifact)).toString('utf8'), expected);
		const attestation = JSON.parse(readFileSync(paths.attestation, 'utf8'));
		assert.deepEqual({
			status: attestation.status,
			compressedSha256: attestation.compressedSha256,
			compressedBytes: attestation.compressedBytes,
			uncompressedBytes: attestation.uncompressedBytes,
			lineCount: attestation.lineCount,
			statementCount: attestation.statementCount,
		}, {
			status: 'complete',
			compressedSha256: createHash('sha256').update(readFileSync(paths.artifact)).digest('hex'),
			compressedBytes: String(statSync(paths.artifact).size),
			uncompressedBytes: String(Buffer.byteLength(expected)),
			lineCount: '2',
			statementCount: '2',
		});
		const evidence = await validateSqlEvidence(paths.artifact, paths.attestation);
		assert.equal(evidence.queryCount, 2);
		assert.deepEqual(evidence.repeatedSql, [{ sql: 'select p1_0.id from polls p1_0 where p1_0.id=?', count: 2 }]);
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('SQL capture fails closed for missing sentinels, bind output, child truncation, and corrupted gzip', async () => {
	const { captureSqlEvidence, validateSqlEvidence } = await sqlEvidenceModule();
	for (const [name, input] of [
		['missing-first', `${SQL_A}\n${FINAL}\n`],
		['missing-final', `${FIRST}\n${SQL_A}\n`],
		['bind', `${FIRST}\n${SQL_A}\nTRACE org.hibernate.orm.jdbc.bind : [sentinel-secret]\n${FINAL}\n`],
	]) {
		const temporary = mkdtempSync(join(tmpdir(), `faithlog-196-${name}-`));
		try {
			const paths = evidencePaths(temporary);
			await assert.rejects(() => captureSqlEvidence(Readable.from([input]), captureOptions(paths)));
			assert.equal(existsSync(paths.artifact), false);
			const rejection = JSON.parse(readFileSync(paths.attestation, 'utf8'));
			assert.equal(rejection.status, 'rejected');
			assert.equal(rejection.reusable, false);
			assert.doesNotMatch(JSON.stringify(rejection), /sentinel-secret/);
		} finally {
			rmSync(temporary, { recursive: true, force: true });
		}
	}
	const limitDirectory = mkdtempSync(join(tmpdir(), 'faithlog-196-gzip-limit-'));
	try {
		const paths = evidencePaths(limitDirectory);
		await assert.rejects(() => captureSqlEvidence(Readable.from([`${FIRST}\n${SQL_A}\n${FINAL}\n`]), {
			...captureOptions(paths), maxCompressedBytes: '1',
		}));
		assert.equal(existsSync(paths.artifact), false);
		assert.equal(JSON.parse(readFileSync(paths.attestation, 'utf8')).reason, 'compressed-size-limit-exceeded');
	} finally {
		rmSync(limitDirectory, { recursive: true, force: true });
	}

	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-truncated-gzip-'));
	try {
		const paths = evidencePaths(temporary);
		await captureSqlEvidence(Readable.from([`${FIRST}\n${SQL_A}\n${FINAL}\n`]), captureOptions(paths));
		const truncated = join(temporary, 'truncated.sql.log.gz');
		copyFileSync(paths.artifact, truncated);
		truncateSync(truncated, Math.max(1, statSync(truncated).size - 4));
		await assert.rejects(() => validateSqlEvidence(truncated, paths.attestation));
		const malformedAttestation = join(temporary, 'malformed-attestation.json');
		const value = JSON.parse(readFileSync(paths.attestation, 'utf8'));
		writeFileSync(malformedAttestation, JSON.stringify({ ...value, statementCount: '2', unexpected: true }));
		await assert.rejects(() => validateSqlEvidence(paths.artifact, malformedAttestation));
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('fake Docker follower requires --follow boundaries and propagates incomplete child evidence', () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-fake-docker-log-'));
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const docker = join(bin, 'docker');
		writeFileSync(docker, `#!/usr/bin/env bash\nset -euo pipefail\nprintf '%s\\n' "$*" > "$FAKE_DOCKER_ARGS"\nprintf '%s\\n' "$SQL_FIRST_SENTINEL"\nprintf '%s\\n' 'DEBUG org.hibernate.SQL : select 1'\nif [[ "\${FAKE_DOCKER_FAIL:-false}" == true ]]; then exit 42; fi\nprintf '%s\\n' "$SQL_FINAL_SENTINEL"\nif [[ "\${FAKE_DOCKER_FAIL_AFTER_FINAL:-false}" == true ]]; then exit 42; fi\nif [[ "\${FAKE_DOCKER_TERM_143:-false}" == true ]]; then trap 'exit 143' TERM; while :; do sleep 1; done; fi\n`);
		chmodSync(docker, 0o700);
		const complete = runFollower(temporary, { PATH: `${bin}:${process.env.PATH}` });
		assert.equal(complete.status, 0, complete.stderr);
		assert.equal(readFileSync(join(temporary, 'docker.args'), 'utf8').trim(),
			'logs --follow --since 2026-07-17T00:00:00.000Z fake-app');
		const dockerCliStopDirectory = mkdtempSync(join(tmpdir(), 'faithlog-196-fake-docker-143-'));
		try {
			const dockerCliStop = runFollower(dockerCliStopDirectory, {
				PATH: `${bin}:${process.env.PATH}`, FAKE_DOCKER_TERM_143: 'true',
			});
			assert.equal(dockerCliStop.error, undefined);
			assert.equal(dockerCliStop.signal, null);
			assert.equal(dockerCliStop.status, 0, dockerCliStop.stderr);
		} finally {
			rmSync(dockerCliStopDirectory, { recursive: true, force: true });
		}

		const failedDirectory = mkdtempSync(join(tmpdir(), 'faithlog-196-fake-docker-fail-'));
		try {
			const failed = runFollower(failedDirectory, {
				PATH: `${bin}:${process.env.PATH}`, FAKE_DOCKER_FAIL: 'true',
			});
			assert.notEqual(failed.status, 0);
			assert.equal(existsSync(join(failedDirectory, 'hibernate-sql.log.gz')), false);
		} finally {
			rmSync(failedDirectory, { recursive: true, force: true });
		}
		const lateFailureDirectory = mkdtempSync(join(tmpdir(), 'faithlog-196-fake-docker-late-fail-'));
		try {
			const failed = runFollower(lateFailureDirectory, {
				PATH: `${bin}:${process.env.PATH}`, FAKE_DOCKER_FAIL_AFTER_FINAL: 'true',
			});
			assert.notEqual(failed.status, 0);
			assert.equal(existsSync(join(lateFailureDirectory, 'hibernate-sql.log.gz')), false);
		} finally {
			rmSync(lateFailureDirectory, { recursive: true, force: true });
		}
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('bounded follower termination preserves one secret-free rejected attestation and no partial gzip', async () => {
	const temporary = mkdtempSync(join(tmpdir(), 'faithlog-196-fake-docker-timeout-'));
	try {
		const bin = join(temporary, 'bin');
		mkdirSync(bin);
		const docker = join(bin, 'docker');
		writeFileSync(docker, `#!/usr/bin/env bash\nset -euo pipefail\nprintf '%s\\n' "$SQL_FIRST_SENTINEL"\nprintf '%s\\n' 'DEBUG org.hibernate.SQL : select 1'\nwhile :; do sleep 1; done\n`);
		chmodSync(docker, 0o700);
		const paths = evidencePaths(temporary);
		const child = spawn('bash', [join(ROOT, 'capture-sql-window.sh')], {
			env: followerEnv(temporary, { PATH: `${bin}:${process.env.PATH}` }),
			stdio: ['ignore', 'ignore', 'pipe'],
		});
		const outcomePromise = new Promise((resolveExit) => child.once('exit', (code, signal) => resolveExit({ code, signal })));
		let stderr = '';
		child.stderr.setEncoding('utf8');
		child.stderr.on('data', (chunk) => { stderr += chunk; });
		for (let attempt = 0; attempt < 50 && !existsSync(paths.ready); attempt += 1) {
			await new Promise((resolveDelay) => setTimeout(resolveDelay, 20));
		}
		assert.equal(existsSync(paths.ready), true, 'fake follower did not reach the first sentinel');
		child.kill('SIGTERM');
		const outcome = await outcomePromise;
		assert.notEqual(outcome.code, 0);
		assert.equal(existsSync(paths.artifact), false);
		const rejection = JSON.parse(readFileSync(paths.attestation, 'utf8'));
		assert.equal(rejection.status, 'rejected');
		assert.equal(rejection.reason, 'upstream-child-failed');
		assert.doesNotMatch(stderr, /select 1|Authorization|token/i);
	} finally {
		rmSync(temporary, { recursive: true, force: true });
	}
});

test('runtime prep binds bounded local logging and runner gates projected 27-endpoint storage without weakening existing windows', async () => {
	const override = read('runtime-evidence.override.yml');
	const prep = read('prepare-runtime.sh');
	const runner = read('run-baseline.sh');
	const capture = read('capture-sql-window.sh');
	const sqlEvidence = read('sql-evidence.mjs');
	const summarizer = read('summarize-run.mjs');
	for (const marker of ['driver: local', 'max-size:', 'max-file:', 'compress:']) assert.ok(override.includes(marker));
	for (const marker of ['PERF_APP_LOG_MAX_SIZE', 'PERF_APP_LOG_MAX_FILE', 'daemonLogRetention', 'maximumRetainedBytes']) {
		assert.ok(prep.includes(marker), `runtime prep missing ${marker}`);
	}
	assert.match(capture, /capture-docker/);
	assert.match(sqlEvidence, /\['logs', '--follow', '--since'/);
	assert.match(sqlEvidence, /createGunzip/);
	assert.match(sqlEvidence, /createInterface/);
	assert.match(summarizer, /validateSqlEvidence/);
	assert.doesNotMatch(summarizer, /readFileSync\(sqlLogPath/);
	for (const marker of [
		'SQL_FIRST_SENTINEL', 'SQL_FINAL_SENTINEL', 'sql-evidence-attestation.json',
		'hibernate-sql.log.gz', 'PERF_SQL_GZIP_MAX_BYTES', 'PERF_NON_SQL_EVIDENCE_MAX_BYTES',
		'PERF_STORAGE_SAFETY_HEADROOM_BYTES', 'storage-budget.mjs',
	]) assert.ok(runner.includes(marker), `runner missing ${marker}`);
	for (const entrypoint of ['seed-fixture.mjs', 'shape-fixture.sh', 'run-baseline.sh']) {
		const source = read(entrypoint);
		for (const marker of ['appLogDriver', 'appLogMaxSize', 'appLogMaxFile', 'appLogCompress']) {
			assert.ok(source.includes(marker), `${entrypoint} missing runtime log identity ${marker}`);
		}
	}
	assert.doesNotMatch(runner, /docker logs --since "\$\{log_since\}" --until "\$\{log_until\}"/);
	assert.match(runner, /PERF_MAINTENANCE_QUIET_SECONDS/);
	assert.match(runner, /CREDENTIALS_FILE/);
	assert.match(runner, /RESOURCE_WINDOW_SAMPLER/);

	const { evaluateStorageBudget } = await import(`${pathToFileURL(join(ROOT, 'storage-budget.mjs')).href}?test=${Date.now()}`);
	const safe = evaluateStorageBudget({
		availableBytes: '10737418240', remainingEndpoints: '27', compressedSqlMaxBytesPerEndpoint: '67108864',
		nonSqlMaxBytesPerEndpoint: '8388608', daemonLogMaximumRetainedBytes: '201326592', safetyHeadroomBytes: '2147483648',
	});
	assert.equal(safe.projectedRequiredBytes, '4387241984');
	assert.equal(safe.safe, true);
	assert.equal(evaluateStorageBudget({
		availableBytes: '4387241983', remainingEndpoints: '27', compressedSqlMaxBytesPerEndpoint: '67108864',
		nonSqlMaxBytesPerEndpoint: '8388608', daemonLogMaximumRetainedBytes: '201326592', safetyHeadroomBytes: '2147483648',
	}).safe, false);
	assert.throws(() => evaluateStorageBudget({
		availableBytes: '9999999999', remainingEndpoints: '27', compressedSqlMaxBytesPerEndpoint: '1',
		nonSqlMaxBytesPerEndpoint: '1', daemonLogMaximumRetainedBytes: '1', safetyHeadroomBytes: '1073741824',
	}), /2 GiB/);
});

async function sqlEvidenceModule() {
	return import(`${pathToFileURL(join(ROOT, 'sql-evidence.mjs')).href}?test=${Date.now()}-${Math.random()}`);
}

function evidencePaths(directory) {
	return {
		artifact: join(directory, 'hibernate-sql.log.gz'),
		attestation: join(directory, 'sql-evidence-attestation.json'),
		ready: join(directory, 'sql-evidence.ready'),
	};
}

function captureOptions(paths) {
	return { ...paths, firstMarker: FIRST, finalMarker: FINAL, maxCompressedBytes: '67108864' };
}

function runFollower(directory, extraEnv) {
	return spawnSync('bash', [join(ROOT, 'capture-sql-window.sh')], {
		encoding: 'utf8', timeout: 5000,
		env: followerEnv(directory, extraEnv),
	});
}

function followerEnv(directory, extraEnv) {
	return {
		...process.env, ...extraEnv,
		APP_CONTAINER: 'fake-app', LOG_SINCE: '2026-07-17T00:00:00.000Z',
		SQL_FIRST_SENTINEL: FIRST, SQL_FINAL_SENTINEL: FINAL,
		SQL_EVIDENCE_ARTIFACT: join(directory, 'hibernate-sql.log.gz'),
		SQL_EVIDENCE_ATTESTATION: join(directory, 'sql-evidence-attestation.json'),
		SQL_EVIDENCE_READY_FILE: join(directory, 'sql-evidence.ready'),
		SQL_GZIP_MAX_BYTES: '67108864', FAKE_DOCKER_ARGS: join(directory, 'docker.args'),
	};
}

function read(name) {
	return readFileSync(join(ROOT, name), 'utf8');
}
