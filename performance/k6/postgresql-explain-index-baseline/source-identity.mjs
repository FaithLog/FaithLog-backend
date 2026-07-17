import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export function buildSourceIdentity({
	repositoryRoot, scenarioRoot, gitCommit, gitDirty, sqlFiles, productionSourceRefs,
	inventoryPath, reportContractPath, sourceManifestPath = null, expected = {}, sqlSources = null,
	controlSources = null,
}) {
	const hashFile = (base, relativePath) => {
		const bytes = readContainedRegularFile(base, relativePath);
		return { relativePath, sha256: createHash('sha256').update(bytes).digest('hex') };
	};
	const sqlIdentity = sqlSources
		? sqlSources.map(({ relativePath, sha256 }) => ({ relativePath, sha256 })).sort(byRelativePath)
		: [...new Set(sqlFiles)].sort().map((file) => hashFile(scenarioRoot, file));
	const controlSourceByPath = new Map((controlSources || [])
		.map(({ relativePath, sha256 }) => [relativePath, { relativePath, sha256 }]));
	const controlIdentity = (relativePath) => controlSourceByPath.get(relativePath) ?? hashFile(scenarioRoot, relativePath);
	const effectiveSourceManifestPath = sourceManifestPath
		?? (controlSourceByPath.has('source-manifest.json') ? 'source-manifest.json' : null);
	const sourceIdentity = [...new Set(productionSourceRefs)].sort().map((file) => hashFile(repositoryRoot, file));
	const expectedPaths = Object.keys(expected.productionSourceRefs || {}).sort();
	const actualPaths = sourceIdentity.map(({ relativePath }) => relativePath).sort();
	const mismatches = sourceIdentity
		.filter(({ relativePath, sha256 }) => expected.productionSourceRefs?.[relativePath] !== sha256)
		.map(({ relativePath }) => relativePath);
	if (JSON.stringify(expectedPaths) !== JSON.stringify(actualPaths)) mismatches.push('manifest-source-set');
	return {
		gitCommit,
		gitDirty,
		inventory: controlIdentity(inventoryPath),
		reportContract: controlIdentity(reportContractPath),
		sourceManifest: effectiveSourceManifestPath ? controlIdentity(effectiveSourceManifestPath) : null,
		sqlFiles: sqlIdentity,
		productionSourceRefs: sourceIdentity,
		sourceHashMismatches: mismatches,
		manifestSha256: expected.manifestSha256 ?? null,
	};
}

export function loadSqlSources(scenarioRoot, sqlFiles) {
	return [...new Set(sqlFiles)].sort().map((relativePath) => {
		const bytes = readContainedRegularFile(scenarioRoot, relativePath);
		return {
			relativePath,
			bytes,
			text: bytes.toString('utf8'),
			sha256: createHash('sha256').update(bytes).digest('hex'),
		};
	});
}

export function assertSingleReadOnlySelect(sql, queryId = 'query') {
	if (sql.includes('\\')) {
		throw new Error(`${queryId} must not contain psql meta-command or backslash syntax.`);
	}
	if (!/^\s*(?:--[^\n]*\n\s*)*(?:WITH|SELECT)\b/i.test(sql)) {
		throw new Error(`${queryId} must start with SELECT or a read-only WITH query.`);
	}
	if (sql.includes(';')) {
		throw new Error(`${queryId} must contain exactly one SQL statement.`);
	}
	if (/\b(?:INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|VACUUM|ANALYZE|CALL|COPY)\b/i.test(sql)) {
		throw new Error(`${queryId} contains a forbidden write, DDL, maintenance, or copy statement.`);
	}
}

export function validateSourceIdentity(identity) {
	const reasons = [];
	if (typeof identity?.gitCommit !== 'string' || identity.gitCommit.length === 0) reasons.push('git-commit-missing');
	if (identity?.gitDirty !== false) reasons.push('git-worktree-dirty');
	if (!Array.isArray(identity?.sourceHashMismatches) || identity.sourceHashMismatches.length > 0) {
		reasons.push('production-source-hash-mismatch');
	}
	for (const group of ['sqlFiles', 'productionSourceRefs']) {
		if (!Array.isArray(identity?.[group]) || identity[group].some((entry) => !/^[a-f0-9]{64}$/.test(entry.sha256))) {
			reasons.push(`${group}-hash-incomplete`);
		}
	}
	for (const field of ['inventory', 'reportContract']) {
		if (identity?.[field]?.relativePath?.length === 0 || !/^[a-f0-9]{64}$/.test(identity?.[field]?.sha256 ?? '')) {
			reasons.push(`${field}-hash-incomplete`);
		}
	}
	if (identity?.sourceManifest !== null
		&& !/^[a-f0-9]{64}$/.test(identity?.sourceManifest?.sha256 ?? '')) {
		reasons.push('sourceManifest-hash-incomplete');
	}
	return { adoptable: reasons.length === 0, reasons };
}

export function validateSourceContinuity(before, after) {
	const reasons = [...validateSourceIdentity(after).reasons];
	for (const field of ['gitCommit', 'inventory', 'reportContract', 'sourceManifest', 'sqlFiles', 'productionSourceRefs', 'manifestSha256']) {
		if (stableStringify(before?.[field]) !== stableStringify(after?.[field])) {
			reasons.push(`source-identity-changed:${field}`);
		}
	}
	return { stable: reasons.length === 0, reasons: [...new Set(reasons)] };
}

function stableStringify(value) {
	if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
	if (value && typeof value === 'object') {
		return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
	}
	return JSON.stringify(value);
}

function readContainedRegularFile(root, relativePath) {
	if (typeof relativePath !== 'string' || relativePath.length === 0 || path.isAbsolute(relativePath)) {
		throw new Error('Source identity paths must be non-empty relative paths.');
	}
	const segments = relativePath.split(/[\\/]+/);
	if (segments.includes('..') || segments.includes('.')) throw new Error(`Source path traversal is forbidden: ${relativePath}`);
	const rootReal = fs.realpathSync(root);
	let current = rootReal;
	for (const segment of segments) {
		current = path.join(current, segment);
		const component = fs.lstatSync(current);
		if (component.isSymbolicLink()) throw new Error(`Source path must not contain a symlink: ${relativePath}`);
	}
	const absolutePath = path.resolve(rootReal, relativePath);
	const descriptor = fs.openSync(absolutePath, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
	try {
		const opened = fs.fstatSync(descriptor);
		if (!opened.isFile()) throw new Error(`Source path must be a regular file: ${relativePath}`);
		const realPath = fs.realpathSync(absolutePath);
		const real = fs.statSync(realPath);
		const contained = realPath === rootReal || realPath.startsWith(`${rootReal}${path.sep}`);
		if (!contained || opened.dev !== real.dev || opened.ino !== real.ino) {
			throw new Error(`Source path identity changed or escaped its root: ${relativePath}`);
		}
		return fs.readFileSync(descriptor);
	} finally {
		fs.closeSync(descriptor);
	}
}

function byRelativePath(left, right) {
	return left.relativePath.localeCompare(right.relativePath, 'en');
}
