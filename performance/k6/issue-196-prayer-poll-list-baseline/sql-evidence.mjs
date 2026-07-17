import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import {
	createReadStream, createWriteStream, existsSync, mkdirSync, renameSync, rmSync,
} from 'node:fs';
import { readFile, writeFile } from 'node:fs/promises';
import { once } from 'node:events';
import { dirname, resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { PassThrough, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { createGunzip, createGzip } from 'node:zlib';

const SQL_MARKER = 'org.hibernate.SQL';
const FORBIDDEN_VALUE_LOGGER = /org\.hibernate\.orm\.jdbc\.(?:bind|extract)|org\.hibernate\.type\.descriptor\.sql\.(?:BasicBinder|BasicExtractor)/;
const DECIMAL = /^(0|[1-9][0-9]*)$/;

export async function captureSqlEvidence(input, options) {
	const validated = validateCaptureOptions(options);
	const temporaryArtifact = `${validated.artifact}.partial-${process.pid}-${Date.now()}`;
	let readyWritten = false;
	let firstObserved = false;
	let finalObserved = false;
	let forbiddenValueLoggerObserved = false;
	let rejectionReason = null;
	let uncompressedBytes = 0n;
	let lineCount = 0n;
	let statementCount = 0n;
	let compressedBytes = 0n;
	const compressedHash = createHash('sha256');
	const gzip = createGzip();
	const compressedMeter = new Transform({
		transform(chunk, _encoding, callback) {
			compressedBytes += BigInt(chunk.length);
			if (compressedBytes > validated.maxCompressedBytes) {
				callback(new Error('compressed-size-limit-exceeded'));
				return;
			}
			compressedHash.update(chunk);
			callback(null, chunk);
		},
	});
	const output = createWriteStream(temporaryArtifact, { flags: 'wx', mode: 0o600 });
	let pipelineError = null;
	const pipelineCompletion = pipeline(gzip, compressedMeter, output).catch((error) => {
		pipelineError = error;
	});

	try {
		const lines = createInterface({ input, crlfDelay: Infinity });
		for await (const line of lines) {
			if (line === validated.firstMarker) {
				if (firstObserved || finalObserved) {
					rejectionReason = 'duplicate-or-out-of-order-first-sentinel';
					break;
				}
				firstObserved = true;
				await writeFile(validated.ready, 'ready\n', { flag: 'wx', mode: 0o600 });
				readyWritten = true;
				continue;
			}
			if (line === validated.finalMarker) {
				if (!firstObserved || finalObserved) {
					rejectionReason = 'missing-or-out-of-order-first-sentinel';
					break;
				}
				finalObserved = true;
				if (validated.onFinal) await validated.onFinal();
				break;
			}
			if (!firstObserved || finalObserved) continue;
			if (FORBIDDEN_VALUE_LOGGER.test(line)) {
				forbiddenValueLoggerObserved = true;
				rejectionReason = 'forbidden-value-logger-observed';
				continue;
			}
			if (!line.includes(SQL_MARKER)) continue;
			const bytes = Buffer.from(`${line}\n`);
			uncompressedBytes += BigInt(bytes.length);
			lineCount += 1n;
			statementCount += 1n;
			if (!gzip.write(bytes)) await once(gzip, 'drain');
		}
		input.destroy?.();
		if (!firstObserved) rejectionReason ||= 'missing-first-sentinel';
		if (!finalObserved) rejectionReason ||= 'missing-final-sentinel';
		gzip.end();
		await pipelineCompletion;
		if (pipelineError) throw pipelineError;
		if (rejectionReason || forbiddenValueLoggerObserved) {
			throw new Error(rejectionReason || 'forbidden-value-logger-observed');
		}

		renameSync(temporaryArtifact, validated.artifact);
		const attestation = {
			contractVersion: 1,
			status: 'complete',
			capturedAt: new Date().toISOString(),
			reusable: false,
			firstSentinelSha256: sha256(validated.firstMarker),
			finalSentinelSha256: sha256(validated.finalMarker),
			firstObserved,
			finalObserved,
			forbiddenValueLoggerObserved: false,
			compressedSha256: compressedHash.digest('hex'),
			compressedBytes: compressedBytes.toString(),
			uncompressedBytes: uncompressedBytes.toString(),
			lineCount: lineCount.toString(),
			statementCount: statementCount.toString(),
		};
		await writeJsonExclusive(validated.attestation, attestation);
		return attestation;
	} catch (error) {
		gzip.destroy();
		compressedMeter.destroy();
		output.destroy();
		await pipelineCompletion;
		rmSync(temporaryArtifact, { force: true });
		rmSync(validated.artifact, { force: true });
		const reason = sanitizeReason(rejectionReason || error?.message);
		if (!existsSync(validated.attestation)) {
			await writeJsonExclusive(validated.attestation, {
				contractVersion: 1,
				status: 'rejected',
				rejectedAt: new Date().toISOString(),
				reusable: false,
				reason,
				firstObserved,
				finalObserved,
				forbiddenValueLoggerObserved,
			});
		}
		if (!readyWritten) rmSync(validated.ready, { force: true });
		throw new Error(`SQL evidence capture rejected: ${reason}`);
	}
}

export async function captureDockerSqlEvidence(options) {
	if (typeof options?.appContainer !== 'string' || !options.appContainer
		|| typeof options?.logSince !== 'string' || !Number.isFinite(Date.parse(options.logSince))) {
		throw new Error('Docker SQL capture target and RFC3339 start time are required.');
	}
	const child = spawn('docker', ['logs', '--follow', '--since', options.logSince, options.appContainer], {
		stdio: ['ignore', 'pipe', 'pipe'],
		env: dockerFollowerEnvironment(),
	});
	const childExit = new Promise((resolveExit, rejectExit) => {
		child.once('error', rejectExit);
		child.once('exit', (code, signal) => resolveExit({ code, signal }));
	});
	const merged = new PassThrough();
	let openStreams = 2;
	const endMerged = () => {
		openStreams -= 1;
		if (openStreams === 0) merged.end();
	};
	child.stdout.pipe(merged, { end: false });
	child.stderr.pipe(merged, { end: false });
	child.stdout.once('end', endMerged);
	child.stderr.once('end', endMerged);
	const stopChild = (signal) => {
		if (child.exitCode === null && child.signalCode === null) child.kill(signal);
	};
	const onInterrupt = () => {
		stopChild('SIGTERM');
		merged.destroy(new Error('upstream-child-failed'));
	};
	process.once('SIGINT', onInterrupt);
	process.once('SIGTERM', onInterrupt);
	try {
		return await captureSqlEvidence(merged, {
			...options,
			onFinal: async () => {
				const naturalExit = await Promise.race([
					childExit.then((outcome) => outcome),
					new Promise((resolveDelay) => setTimeout(() => resolveDelay(null), 25)),
				]);
				if (naturalExit) {
					if (naturalExit.code !== 0 || naturalExit.signal !== null) throw new Error('upstream-child-failed');
					return;
				}
				if (!child.kill('SIGTERM')) throw new Error('upstream-child-failed');
				const stopped = await childExit;
				if (stopped.signal !== 'SIGTERM' && stopped.code !== 0 && stopped.code !== 143) {
					throw new Error('upstream-child-failed');
				}
			},
		});
	} catch (error) {
		stopChild('SIGTERM');
		await childExit.catch(() => null);
		throw error;
	} finally {
		process.removeListener('SIGINT', onInterrupt);
		process.removeListener('SIGTERM', onInterrupt);
	}
}

function dockerFollowerEnvironment() {
	const allowedNames = [
		'PATH', 'HOME', 'TMPDIR', 'DOCKER_CONFIG', 'DOCKER_HOST', 'SQL_FIRST_SENTINEL', 'SQL_FINAL_SENTINEL',
	];
	return Object.fromEntries(allowedNames
		.filter((name) => typeof process.env[name] === 'string')
		.map((name) => [name, process.env[name]]));
}

export async function validateSqlEvidence(artifactPath, attestationPath) {
	const attestation = JSON.parse(await readFile(attestationPath, 'utf8'));
	validateAttestation(attestation);
	const compressedHash = createHash('sha256');
	let compressedBytes = 0n;
	let uncompressedBytes = 0n;
	let lineCount = 0n;
	let statementCount = 0n;
	const normalizedMap = new Map();
	const meter = new Transform({
		transform(chunk, _encoding, callback) {
			compressedBytes += BigInt(chunk.length);
			compressedHash.update(chunk);
			callback(null, chunk);
		},
	});
	const decoded = createReadStream(artifactPath).pipe(meter).pipe(createGunzip());
	const lines = createInterface({ input: decoded, crlfDelay: Infinity });
	for await (const line of lines) {
		const bytes = Buffer.byteLength(`${line}\n`);
		uncompressedBytes += BigInt(bytes);
		lineCount += 1n;
		if (!line.includes(SQL_MARKER) || FORBIDDEN_VALUE_LOGGER.test(line)) {
			throw new Error('SQL gzip contains a non-statement or forbidden logger line.');
		}
		statementCount += 1n;
		const normalized = normalizeSql(extractSql(line));
		if (!normalized) throw new Error('SQL gzip contains an empty statement.');
		normalizedMap.set(normalized, (normalizedMap.get(normalized) || 0) + 1);
	}
	const actual = {
		compressedSha256: compressedHash.digest('hex'),
		compressedBytes: compressedBytes.toString(),
		uncompressedBytes: uncompressedBytes.toString(),
		lineCount: lineCount.toString(),
		statementCount: statementCount.toString(),
	};
	if (statementCount > BigInt(Number.MAX_SAFE_INTEGER)) {
		throw new Error('SQL statement count exceeds the safe report range.');
	}
	for (const [key, value] of Object.entries(actual)) {
		if (attestation[key] !== value) throw new Error(`SQL evidence ${key} mismatch.`);
	}
	const normalizedCounts = [...normalizedMap.entries()]
		.map(([sql, count]) => ({ sql, count }))
		.sort((left, right) => right.count - left.count || left.sql.localeCompare(right.sql));
	return {
		queryCount: Number(statementCount),
		repeatedSql: normalizedCounts.filter((entry) => entry.count > 1),
		normalizedCounts,
		attestation,
	};
}

function validateCaptureOptions(options) {
	const paths = ['artifact', 'attestation', 'ready'];
	if (!options || paths.some((name) => typeof options[name] !== 'string' || !options[name])) {
		throw new Error('SQL evidence output paths are required.');
	}
	const resolved = Object.fromEntries(paths.map((name) => [name, resolve(options[name])]));
	if (new Set(Object.values(resolved)).size !== paths.length || Object.values(resolved).some(existsSync)) {
		throw new Error('SQL evidence paths must be fresh and distinct.');
	}
	if (typeof options.firstMarker !== 'string' || !options.firstMarker
		|| typeof options.finalMarker !== 'string' || !options.finalMarker
		|| options.firstMarker === options.finalMarker) {
		throw new Error('Distinct SQL evidence sentinels are required.');
	}
	const maxCompressedBytes = decimalBigInt(options.maxCompressedBytes);
	if (maxCompressedBytes === null || maxCompressedBytes <= 0n) {
		throw new Error('A positive SQL gzip byte limit is required.');
	}
	mkdirSync(dirname(resolved.artifact), { recursive: true });
	if (options.onFinal !== undefined && typeof options.onFinal !== 'function') {
		throw new Error('SQL evidence final callback must be a function.');
	}
	return {
		...resolved, firstMarker: options.firstMarker, finalMarker: options.finalMarker,
		maxCompressedBytes, onFinal: options.onFinal,
	};
}

function validateAttestation(value) {
	const expectedKeys = [
		'capturedAt', 'compressedBytes', 'compressedSha256', 'contractVersion', 'finalObserved',
		'finalSentinelSha256', 'firstObserved', 'firstSentinelSha256', 'forbiddenValueLoggerObserved',
		'lineCount', 'reusable', 'statementCount', 'status', 'uncompressedBytes',
	].sort();
	if (!value || Object.keys(value).sort().join('\0') !== expectedKeys.join('\0')
		|| value.contractVersion !== 1 || value.status !== 'complete' || value.reusable !== false
		|| value.firstObserved !== true || value.finalObserved !== true || value.forbiddenValueLoggerObserved !== false
		|| !Number.isFinite(Date.parse(value.capturedAt))
		|| !/^[a-f0-9]{64}$/.test(value.compressedSha256 || '')
		|| !/^[a-f0-9]{64}$/.test(value.firstSentinelSha256 || '')
		|| !/^[a-f0-9]{64}$/.test(value.finalSentinelSha256 || '')
		|| ['compressedBytes', 'uncompressedBytes', 'lineCount', 'statementCount']
			.some((key) => decimalBigInt(value[key]) === null)) {
		throw new Error('SQL evidence attestation is malformed or incomplete.');
	}
	if (value.lineCount !== value.statementCount || BigInt(value.compressedBytes) <= 0n) {
		throw new Error('SQL evidence attestation counts are inconsistent.');
	}
}

function extractSql(line) {
	return line.slice(line.indexOf(SQL_MARKER) + SQL_MARKER.length).replace(/^\s*[-:]?\s*/, '');
}

function normalizeSql(sql) {
	return sql.replace(/'[^']*'/g, '?').replace(/\b\d+\b/g, '?').replace(/\s+/g, ' ').trim().toLowerCase();
}

function decimalBigInt(value) {
	return typeof value === 'string' && DECIMAL.test(value) ? BigInt(value) : null;
}

function sha256(value) {
	return createHash('sha256').update(value).digest('hex');
}

function sanitizeReason(reason) {
	return [
		'duplicate-or-out-of-order-first-sentinel', 'missing-or-out-of-order-first-sentinel',
		'missing-first-sentinel', 'missing-final-sentinel', 'forbidden-value-logger-observed',
		'compressed-size-limit-exceeded', 'upstream-child-failed',
	].includes(reason) ? reason : 'capture-stream-failed';
}

async function writeJsonExclusive(path, value) {
	await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

if (import.meta.url === `file://${process.argv[1]}`) {
	const [command, artifact, attestation, ready, firstMarker, finalMarker] = process.argv.slice(2);
	try {
		if (command === 'capture') {
			await captureSqlEvidence(process.stdin, {
				artifact, attestation, ready, firstMarker, finalMarker,
				maxCompressedBytes: process.env.SQL_GZIP_MAX_BYTES,
			});
		} else if (command === 'capture-docker') {
			await captureDockerSqlEvidence({
				artifact, attestation, ready, firstMarker, finalMarker,
				maxCompressedBytes: process.env.SQL_GZIP_MAX_BYTES,
				appContainer: process.env.APP_CONTAINER,
				logSince: process.env.LOG_SINCE,
			});
		} else if (command === 'validate') {
			process.stdout.write(`${JSON.stringify(await validateSqlEvidence(artifact, attestation))}\n`);
		} else {
			throw new Error('Usage: sql-evidence.mjs capture|validate ...');
		}
	} catch (error) {
		console.error(error.message);
		process.exitCode = 1;
	}
}
