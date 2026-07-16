import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PROOF_MODE = 'clean-detached-checkout-image-created-after-checkout';
const LIMITATION = 'image-alone-revision-label-unavailable';
const API_CONTRACT_PATHS = [
	'src/main/java/com/faithlog/faithlog/domain/devotion',
	'src/main/java/com/faithlog/faithlog/domain/notification',
	'src/main/java/com/faithlog/faithlog/global/scheduler',
	'src/main/resources/db/migration',
];

function exactObject(value, keys, label) {
	assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${label} must be an object`);
	assert.deepEqual(Object.keys(value).sort(), [...keys].sort(), `${label} must have exact keys`);
}

function nonEmptyString(value, label) {
	assert.equal(typeof value, 'string', `${label} must be a string`);
	assert.ok(value.length > 0, `${label} must not be empty`);
}

function strictTimestamp(value, label) {
	nonEmptyString(value, label);
	assert.match(value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/, `${label} must be an ISO timestamp`);
	assert.ok(Number.isFinite(Date.parse(value)), `${label} must be a valid timestamp`);
}

export function parseNewestHeadReflogCheckoutAt(reflog) {
	nonEmptyString(reflog, 'HEAD reflog');
	const newestLine = reflog.split(/\r?\n/, 1)[0];
	const fields = newestLine.split('\t');
	assert.ok(fields.length >= 2, 'HEAD reflog selector timestamp is missing');
	const selector = fields[1];
	const match = /^HEAD@\{(.+)\}$/.exec(selector);
	assert.ok(match, 'HEAD reflog selector timestamp is malformed');
	strictTimestamp(match[1], 'HEAD reflog selector timestamp');
	return match[1];
}

export function validateSourceImageProvenance(facts, expected) {
	exactObject(facts, [
		'schemaVersion', 'proofMode', 'sourceWorktree', 'composeWorkingDir', 'revision', 'detached', 'clean',
		'checkoutAt', 'imageId', 'imageCreatedAt', 'apiContractSha256', 'limitation',
	], 'source/image provenance');
	exactObject(expected, ['sourceWorktree', 'revision', 'imageId', 'apiContractSha256'], 'expected source/image provenance');
	assert.equal(facts.schemaVersion, 1, 'source/image provenance schemaVersion must be 1');
	assert.equal(facts.proofMode, PROOF_MODE, 'source/image provenance proofMode is invalid');
	assert.equal(facts.limitation, LIMITATION, 'source/image provenance limitation must disclose the absent image revision label');
	assert.equal(facts.clean, true, 'source worktree must be clean');
	assert.equal(facts.detached, true, 'source worktree must be detached');
	for (const [label, value] of [
		['source worktree', facts.sourceWorktree], ['Compose working directory', facts.composeWorkingDir],
		['expected source worktree', expected.sourceWorktree],
	]) nonEmptyString(value, label);
	assert.equal(facts.sourceWorktree, expected.sourceWorktree, 'source worktree must match the runtime-approved path');
	assert.equal(facts.composeWorkingDir, facts.sourceWorktree, 'Compose working directory must match the source worktree');
	for (const [label, value] of [['revision', facts.revision], ['expected revision', expected.revision]]) {
		assert.match(value, /^[a-f0-9]{40}$/, `${label} must be a full Git commit`);
	}
	for (const [label, value] of [['image ID', facts.imageId], ['expected image ID', expected.imageId]]) {
		assert.match(value, /^sha256:[a-f0-9]{64}$/, `${label} must be a full Docker image ID`);
	}
	for (const [label, value] of [['API contract SHA-256', facts.apiContractSha256], ['expected API contract SHA-256', expected.apiContractSha256]]) {
		assert.match(value, /^[a-f0-9]{64}$/, `${label} must be a SHA-256 digest`);
	}
	assert.equal(facts.revision, expected.revision, 'source revision must match the runtime-approved revision');
	assert.equal(facts.imageId, expected.imageId, 'app image ID must match the runtime-approved image');
	assert.equal(facts.apiContractSha256, expected.apiContractSha256, 'source API contract SHA-256 must match the runtime-approved digest');
	strictTimestamp(facts.checkoutAt, 'source checkoutAt');
	strictTimestamp(facts.imageCreatedAt, 'imageCreatedAt');
	assert.ok(Date.parse(facts.imageCreatedAt) > Date.parse(facts.checkoutAt), 'image must be created after checkout');
	return facts;
}

function git(sourceWorktree, args, options = {}) {
	return execFileSync('git', ['-C', sourceWorktree, ...args], { encoding: 'utf8', ...options }).trimEnd();
}

function collectFacts(sourceWorktreeInput, composeWorkingDirInput, expectedRevision, imageId, imageCreatedAt) {
	const sourceWorktree = fs.realpathSync(sourceWorktreeInput);
	const composeWorkingDir = fs.realpathSync(composeWorkingDirInput);
	const revision = git(sourceWorktree, ['rev-parse', 'HEAD']);
	const cleanBefore = git(sourceWorktree, ['status', '--porcelain=v1', '--untracked-files=all']) === '';
	let detached = false;
	try {
		git(sourceWorktree, ['symbolic-ref', '-q', 'HEAD']);
	} catch (error) {
		if (error.status !== 1) throw error;
		detached = true;
	}
	const reflog = git(sourceWorktree, ['reflog', '--date=iso-strict', '--format=%cI%x09%gD%x09%gs', 'HEAD']);
	const checkoutAt = parseNewestHeadReflogCheckoutAt(reflog);
	const treeInventory = git(sourceWorktree, ['ls-tree', '-r', expectedRevision, '--', ...API_CONTRACT_PATHS]);
	assert.ok(treeInventory.length > 0, 'source API contract inventory must not be empty');
	const apiContractSha256 = crypto.createHash('sha256').update(`${treeInventory}\n`).digest('hex');
	const cleanAfter = git(sourceWorktree, ['status', '--porcelain=v1', '--untracked-files=all']) === '';
	assert.equal(git(sourceWorktree, ['rev-parse', 'HEAD']), revision, 'source revision changed during provenance capture');
	return {
		schemaVersion: 1,
		proofMode: PROOF_MODE,
		sourceWorktree,
		composeWorkingDir,
		revision,
		detached,
		clean: cleanBefore && cleanAfter,
		checkoutAt,
		imageId,
		imageCreatedAt,
		apiContractSha256,
		limitation: LIMITATION,
	};
}

function writeSecureJson(filePath, value) {
	fs.mkdirSync(path.dirname(filePath), { recursive: true });
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}

async function main() {
	const [command, sourceWorktree, composeWorkingDir, expectedRevision, expectedImageId, imageCreatedAt, expectedApiContractSha256, outputPath] = process.argv.slice(2);
	if (command !== 'capture' || !outputPath) throw new Error('source-image-provenance capture requires source, Compose working directory, revision, image, creation time, API digest, and output');
	const facts = collectFacts(sourceWorktree, composeWorkingDir, expectedRevision, expectedImageId, imageCreatedAt);
	validateSourceImageProvenance(facts, {
		sourceWorktree: fs.realpathSync(sourceWorktree),
		revision: expectedRevision,
		imageId: expectedImageId,
		apiContractSha256: expectedApiContractSha256,
	});
	writeSecureJson(outputPath, facts);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
