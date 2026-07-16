import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { lstatSync, readFileSync, realpathSync } from 'node:fs';
import { isAbsolute, relative, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';

export const RUNTIME_TOOLING_FILES = Object.freeze([
	'performance/k6/issue-196-prayer-poll-list-baseline/activity-sample.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/db-activity.sql',
	'performance/k6/issue-196-prayer-poll-list-baseline/db-quiescence.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/db-quiescence.sql',
	'performance/k6/issue-196-prayer-poll-list-baseline/docker-db-identity.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/db-runtime-identity.sql',
	'performance/k6/issue-196-prayer-poll-list-baseline/db-table-stats.sql',
	'performance/k6/issue-196-prayer-poll-list-baseline/filter-sql-log.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/fixture-contract.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/k6-rate-contract.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/prepare-runtime.sh',
	'performance/k6/issue-196-prayer-poll-list-baseline/redis-runtime-identity.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/resource-window-sampler.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh',
	'performance/k6/issue-196-prayer-poll-list-baseline/runtime-evidence.override.yml',
	'performance/k6/issue-196-prayer-poll-list-baseline/runtime-env-attestation.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/runtime-prep-contract.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/scenario.js',
	'performance/k6/issue-196-prayer-poll-list-baseline/seed-fixture.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh',
	'performance/k6/issue-196-prayer-poll-list-baseline/summarize-run.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/token-lifetime.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/tooling-provenance.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/validate-published-target.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/validate-run-completion.mjs',
	'performance/k6/issue-196-prayer-poll-list-baseline/validate-runtime-identity.mjs',
]);

export function captureToolingProvenance(worktree, expectedHead, requiredFiles = RUNTIME_TOOLING_FILES) {
	if (!isAbsolute(worktree)) throw new Error('Scenario worktree must be an absolute path.');
	const requestedRoot = resolve(worktree);
	const root = realpathSync(requestedRoot);
	if (lstatSync(requestedRoot).isSymbolicLink()) {
		throw new Error('Scenario worktree must not be a symlink.');
	}
	const repositoryRoot = git(root, ['rev-parse', '--show-toplevel']);
	if (realpathSync(repositoryRoot) !== root) throw new Error('Scenario worktree must be the repository root.');
	const head = git(root, ['rev-parse', 'HEAD']);
	if (!/^[a-f0-9]{40}$/.test(expectedHead) || head !== expectedHead) {
		throw new Error('Scenario tooling HEAD differs from the approved HEAD.');
	}
	if (git(root, ['status', '--porcelain', '--untracked-files=all']) !== '') {
		throw new Error('Scenario tooling worktree must be clean, including untracked files.');
	}
	if (!Array.isArray(requiredFiles) || requiredFiles.length === 0) throw new Error('Required tooling file list is empty.');
	const normalized = [...requiredFiles].sort();
	if (new Set(normalized).size !== normalized.length) throw new Error('Required tooling file list contains duplicates.');
	const files = normalized.map((path) => {
		if (typeof path !== 'string' || path.length === 0 || isAbsolute(path) || path.split(/[\\/]/).includes('..')) {
			throw new Error('Required tooling path must be a safe repository-relative path.');
		}
		const absolute = resolve(root, path);
		const rootPrefix = `${root}${sep}`;
		if (!absolute.startsWith(rootPrefix) || relative(root, absolute) !== path) throw new Error(`Invalid tooling path: ${path}`);
		const stat = lstatSync(absolute);
		if (stat.isSymbolicLink()) throw new Error(`Tracked tooling file must not be a symlink: ${path}`);
		if (!stat.isFile()) throw new Error(`Tracked tooling path is not a regular file: ${path}`);
		git(root, ['ls-files', '--error-unmatch', '--', path]);
		return { path, sha256: sha256(readFileSync(absolute)) };
	});
	return {
		worktree: root,
		head,
		clean: true,
		files,
		aggregateSha256: sha256(Buffer.from(files.map(({ path, sha256: digest }) => `${path}\0${digest}\n`).join(''))),
	};
}

export function assertCurrentTooling(expected, worktree, expectedHead) {
	validateProvenanceShape(expected);
	const current = captureToolingProvenance(worktree, expectedHead, expected.files.map(({ path }) => path));
	if (JSON.stringify(current) !== JSON.stringify(expected)) throw new Error('Scenario tooling provenance drifted from the runtime preparation manifest.');
	return current;
}

export function validateProvenanceShape(value) {
	if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Tooling provenance must be an object.');
	const keys = Object.keys(value).sort();
	if (JSON.stringify(keys) !== JSON.stringify(['aggregateSha256', 'clean', 'files', 'head', 'worktree'])) throw new Error('Tooling provenance has an invalid exact schema.');
	if (!isAbsolute(value.worktree) || !/^[a-f0-9]{40}$/.test(value.head) || value.clean !== true) throw new Error('Tooling provenance identity is invalid.');
	if (!Array.isArray(value.files) || value.files.length === 0) throw new Error('Tooling provenance files are required.');
	let previous = '';
	for (const file of value.files) {
		if (!file || Object.keys(file).sort().join(',') !== 'path,sha256' || typeof file.path !== 'string'
			|| file.path <= previous || !/^[a-f0-9]{64}$/.test(file.sha256)) throw new Error('Tooling provenance file schema is invalid.');
		previous = file.path;
	}
	if (!/^[a-f0-9]{64}$/.test(value.aggregateSha256)) throw new Error('Tooling aggregate SHA-256 is invalid.');
	return value;
}

function git(cwd, args) {
	return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

function sha256(value) {
	return createHash('sha256').update(value).digest('hex');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const [mode, worktree, expectedHead, manifestPath] = process.argv.slice(2);
	if (mode === '--capture' && worktree && expectedHead && !manifestPath) {
		process.stdout.write(JSON.stringify(captureToolingProvenance(worktree, expectedHead)));
	} else if (mode === '--assert-manifest' && worktree && expectedHead && manifestPath) {
		const document = JSON.parse(readFileSync(manifestPath, 'utf8'));
		const tooling = document.tooling || document.runtimePreparation?.tooling;
		assertCurrentTooling(tooling, worktree, expectedHead);
	} else {
		throw new Error('Usage: tooling-provenance.mjs --capture WORKTREE HEAD | --assert-manifest WORKTREE HEAD MANIFEST');
	}
}
