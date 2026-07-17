import { createHash } from 'node:crypto';
import { lstatSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { SETTLEMENT_MODES, SINGLE_MODE_PROTOCOL_VERSION, targetRoleIdentity, workloadContract } from './single-mode-contract.mjs';

const FILE_KEYS = Object.freeze(['summary', 'adoption', 'target', 'fixture']);
const FILE_NAMES = Object.freeze({ summary: 'baseline-summary.json', adoption: 'baseline-adoption.json', target: 'target-contract.json', fixture: 'fixture-manifest.json' });

export function buildModeBundle(inputRoot, manifest) {
	assertExactKeys(manifest, ['protocolVersion', 'runs'], 'bundle manifest');
	if (manifest.protocolVersion !== SINGLE_MODE_PROTOCOL_VERSION || !Array.isArray(manifest.runs) || manifest.runs.length !== 4) throw new Error('bundle exact mode set mismatch');
	const root = resolve(inputRoot);
	const modes = new Set(); const datasets = new Set(); const fixtures = new Set(); const executions = new Set();
	let common = null;
	const resultModes = {};
	for (const run of manifest.runs) {
		assertExactKeys(run, ['mode', 'directory', 'case', 'files'], 'bundle run');
		if (!SETTLEMENT_MODES.includes(run.mode) || modes.has(run.mode)) throw new Error('bundle duplicate or invalid mode');
		modes.add(run.mode);
		assertExactKeys(run.case, ['datasetId', 'fixtureRunId', 'executionRunId', 'mode'], 'bundle case');
		if (run.case.mode !== run.mode) throw new Error('bundle mode case mismatch');
		for (const [set, value] of [[datasets, run.case.datasetId], [fixtures, run.case.fixtureRunId], [executions, run.case.executionRunId]]) {
			if (typeof value !== 'string' || !value || set.has(value)) throw new Error('bundle run identities must be distinct'); set.add(value);
		}
		if (typeof run.directory !== 'string' || !/^EXEC192_[A-Z0-9_-]{4,40}$/.test(run.directory) || run.directory !== run.case.executionRunId || basename(run.directory) !== run.directory) throw new Error('bundle path directory execution mismatch');
		assertExactKeys(run.files, FILE_KEYS, 'bundle files');
		const values = {};
		for (const key of FILE_KEYS) {
			const descriptor = run.files[key]; assertExactKeys(descriptor, ['name', 'sha256'], 'bundle file');
			if (descriptor.name !== FILE_NAMES[key] || !/^[a-f0-9]{64}$/.test(descriptor.sha256)) throw new Error('bundle file schema');
			const path = resolve(root, run.directory, descriptor.name);
			if (!path.startsWith(`${root}${sep}`)) throw new Error('bundle path must be contained');
			const stat = lstatSync(path); if (stat.isSymbolicLink() || !stat.isFile()) throw new Error('bundle input must be regular JSON without symlink');
			const bytes = readFileSync(path); if (createHash('sha256').update(bytes).digest('hex') !== descriptor.sha256) throw new Error('bundle SHA-256 hash drift');
			try { values[key] = JSON.parse(bytes.toString('utf8')); } catch { throw new Error('bundle input JSON invalid'); }
		}
		validateRun(values, run, run.files.target.sha256);
		const identity = { sourceCommit: values.summary.sourceCommit, targetIdentitySha256: values.summary.targetIdentitySha256, targetRoleIdentity: values.summary.targetRoleIdentity, workloadContract: values.summary.workloadContract };
		if (common === null) common = identity; else if (stable(common) !== stable(identity)) throw new Error('bundle source target or workload mismatch');
		resultModes[run.mode] = values.summary.metrics;
	}
	if (stable([...modes].sort()) !== stable([...SETTLEMENT_MODES].sort())) throw new Error('bundle exact mode set mismatch');
	return {
		protocolVersion: SINGLE_MODE_PROTOCOL_VERSION,
		comparisonIdentity: { sourceCommit: common.sourceCommit, targetIdentitySha256: common.targetIdentitySha256, targetRoleIdentity: common.targetRoleIdentity, workloadContract: common.workloadContract },
		accepted: false, automaticAdoption: false, evidenceIntegrity: 'validated', measurementStatus: 'conditional-boundary-only', modes: resultModes,
	};
}

function validateRun(values, run, targetSha256) {
	const summary = values.summary; const adoption = values.adoption; const target = values.target; const fixture = values.fixture;
	if (stable(summary.case) !== stable(run.case) || summary.mode !== run.mode) throw new Error('bundle summary mode mismatch');
	if (stable(adoption.case) !== stable(run.case)) throw new Error('bundle adoption case mismatch');
	if (summary.workloadContract?.protocolVersion !== SINGLE_MODE_PROTOCOL_VERSION || typeof summary.sourceCommit !== 'string' || !/^[a-f0-9]{40}$/.test(summary.sourceCommit) || summary.targetIdentitySha256 !== targetSha256) throw new Error('bundle source target workload hash mismatch');
	if (target?.sourceCommit !== summary.sourceCommit || stable(targetRoleIdentity(target)) !== stable(summary.targetRoleIdentity) || stable(workloadContract(target)) !== stable(summary.workloadContract)) throw new Error('bundle parsed target mismatch');
	if (fixture?.datasetId !== run.case.datasetId || fixture?.fixtureRunId !== run.case.fixtureRunId || fixture?.selectedMode !== run.mode || fixture?.memberCount !== summary.memberCount || summary.memberCount !== summary.workloadContract.memberCount) throw new Error('bundle parsed fixture mismatch');
	if (adoption.accepted !== false || adoption.automaticAdoption !== false || adoption.evidenceIntegrity !== 'validated' || adoption.measurementStatus !== 'conditional-boundary-only' || adoption.conditionalSummaryAvailable !== true) throw new Error('bundle conditional adoption rejected');
	if (!summary.metrics || typeof summary.metrics !== 'object' || Array.isArray(summary.metrics)) throw new Error('bundle metrics schema');
}
function assertExactKeys(value, keys, label) { if (!value || typeof value !== 'object' || Array.isArray(value) || stable(Object.keys(value).sort()) !== stable([...keys].sort())) throw new Error(`${label} schema`); }
function stable(value) { return JSON.stringify(value); }

async function main() {
	const root = required('BUNDLE_INPUT_ROOT'); const manifestPath = resolve(required('BUNDLE_MANIFEST')); const output = resolve(required('BUNDLE_OUTPUT'));
	const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
	writeFileSync(output, `${JSON.stringify(buildModeBundle(root, manifest), null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
