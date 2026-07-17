import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { test } from 'node:test';
import { targetRoleIdentity, workloadContract } from './single-mode-contract.mjs';

const MODES = ['coffee-sequential', 'meal-sequential', 'coffee-concurrent', 'meal-concurrent'];

test('bundle preserves four mode metrics without synthesizing a combined latency', async () => {
	const fixture = bundleFixture();
	try {
		const { buildModeBundle } = await import('./bundle-results.mjs');
		const bundle = buildModeBundle(fixture.root, fixture.manifest);
		assert.deepEqual(Object.keys(bundle.modes), MODES);
		assert.equal(bundle.accepted, false); assert.equal(bundle.automaticAdoption, false);
		assert.equal(Object.hasOwn(bundle, 'latencyMs'), false);
		for (const [index, mode] of MODES.entries()) assert.equal(bundle.modes[mode].latencyMs.p50, 10 + index);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('bundle rejects missing, extra, duplicate modes and reused run identities', async () => {
	const { buildModeBundle } = await import('./bundle-results.mjs');
	for (const mutate of [
		(value) => { value.runs.pop(); },
		(value) => { value.runs.push(structuredClone(value.runs[0])); },
		(value) => { value.runs[1].mode = value.runs[0].mode; },
		(value) => { value.runs[1].case.datasetId = value.runs[0].case.datasetId; },
		(value) => { value.extra = true; },
	]) { const fixture = bundleFixture(); try { mutate(fixture.manifest); assert.throws(() => buildModeBundle(fixture.root, fixture.manifest), /bundle|mode|distinct|schema/i); } finally { rmSync(fixture.root, { recursive: true, force: true }); } }
});

test('bundle rejects hash drift, target/workload mismatch, rejected or automatic adoption', async () => {
	const { buildModeBundle } = await import('./bundle-results.mjs');
	for (const mutate of [
		(f) => { f.manifest.runs[0].files.summary.sha256 = '0'.repeat(64); },
		(f) => { const path = resolve(f.root, f.manifest.runs[1].directory, 'baseline-summary.json'); const value = json(path); value.sourceCommit = 'f'.repeat(40); writeJson(path, value); refresh(f, 1, 'summary'); },
		(f) => { const path = resolve(f.root, f.manifest.runs[1].directory, 'baseline-summary.json'); const value = json(path); value.workloadContract.memberCount = 999; writeJson(path, value); refresh(f, 1, 'summary'); },
		(f) => { const path = resolve(f.root, f.manifest.runs[1].directory, 'baseline-adoption.json'); const value = json(path); value.evidenceIntegrity = 'rejected'; value.measurementStatus = 'rejected'; writeJson(path, value); refresh(f, 1, 'adoption'); },
		(f) => { const path = resolve(f.root, f.manifest.runs[1].directory, 'baseline-adoption.json'); const value = json(path); value.automaticAdoption = true; writeJson(path, value); refresh(f, 1, 'adoption'); },
	]) { const fixture = bundleFixture(); try { mutate(fixture); assert.throws(() => buildModeBundle(fixture.root, fixture.manifest), /hash|source|target|workload|conditional|adoption|rejected/i); } finally { rmSync(fixture.root, { recursive: true, force: true }); } }
});

test('bundle rejects traversal, symlink, and non-regular JSON inputs', async () => {
	const { buildModeBundle } = await import('./bundle-results.mjs');
	for (const kind of ['traversal', 'symlink']) {
		const fixture = bundleFixture();
		try {
			if (kind === 'traversal') fixture.manifest.runs[0].directory = '../escape';
			else {
				const directory = resolve(fixture.root, fixture.manifest.runs[0].directory); const target = resolve(directory, 'real-summary.json');
				writeFileSync(target, readFileSync(resolve(directory, 'baseline-summary.json'))); rmSync(resolve(directory, 'baseline-summary.json')); symlinkSync(target, resolve(directory, 'baseline-summary.json'));
			}
			assert.throws(() => buildModeBundle(fixture.root, fixture.manifest), /path|regular|symlink|contained/i);
		} finally { rmSync(fixture.root, { recursive: true, force: true }); }
	}
});

test('bundle directory is the exact approved EXEC192 executionRunId', async () => {
	const { buildModeBundle } = await import('./bundle-results.mjs');
	const fixture = bundleFixture();
	try {
		const run = fixture.manifest.runs[0];
		const foreign = 'EXEC192_FOREIGN_DIRECTORY';
		renameSync(resolve(fixture.root, run.directory), resolve(fixture.root, foreign));
		run.directory = foreign;
		assert.throws(() => buildModeBundle(fixture.root, fixture.manifest), /directory|execution|contained/i);
	} finally { rmSync(fixture.root, { recursive: true, force: true }); }
});

test('bundle validates parsed target source, role identity, and workload against the summary', async () => {
	const { buildModeBundle } = await import('./bundle-results.mjs');
	for (const mutate of [
		(target) => { target.sourceCommit = 'f'.repeat(40); },
		(target) => { target.baseUrl = 'http://127.0.0.1:28081'; },
		(target) => { target.maintenanceReadiness.expectedChargeWrites['coffee-sequential'] = 10999; },
	]) {
		const fixture = bundleFixture();
		try {
			for (const [index, run] of fixture.manifest.runs.entries()) {
				const targetPath = resolve(fixture.root, run.directory, 'target-contract.json');
				const target = json(targetPath); mutate(target); writeJson(targetPath, target); refresh(fixture, index, 'target');
				const summaryPath = resolve(fixture.root, run.directory, 'baseline-summary.json');
				const summary = json(summaryPath); summary.targetIdentitySha256 = run.files.target.sha256; writeJson(summaryPath, summary); refresh(fixture, index, 'summary');
			}
			assert.throws(() => buildModeBundle(fixture.root, fixture.manifest), /source|target|role|workload|mismatch/i);
		} finally { rmSync(fixture.root, { recursive: true, force: true }); }
	}
});

test('bundle validates parsed fixture case, selected mode, and member count', async () => {
	const { buildModeBundle } = await import('./bundle-results.mjs');
	for (const mutate of [
		(value) => { value.datasetId = 'PERFORMANCE_192_FOREIGN'; },
		(value) => { value.fixtureRunId = 'FIXTURE_FOREIGN'; },
		(value) => { value.selectedMode = 'meal-sequential'; },
		(value) => { value.memberCount = 999; },
	]) {
		const fixture = bundleFixture();
		try {
			const run = fixture.manifest.runs[0]; const path = resolve(fixture.root, run.directory, 'fixture-manifest.json');
			const value = json(path); mutate(value); writeJson(path, value); refresh(fixture, 0, 'fixture');
			assert.throws(() => buildModeBundle(fixture.root, fixture.manifest), /fixture|case|mode|member|mismatch/i);
		} finally { rmSync(fixture.root, { recursive: true, force: true }); }
	}
});

function bundleFixture({ legacyDirectories = false } = {}) {
	const root = mkdtempSync(resolve(tmpdir(), 'faithlog-192-bundle-'));
	const runs = MODES.map((mode, index) => {
		const caseIdentity = { datasetId: `PERFORMANCE_192_${index}`, fixtureRunId: `FIXTURE_${index}`, executionRunId: `EXEC192_MODE_${index}`, mode };
		const directory = legacyDirectories ? mode : caseIdentity.executionRunId; mkdirSync(resolve(root, directory));
		const target = JSON.parse(readFileSync(resolve(new URL('target-contract.json', import.meta.url).pathname), 'utf8'));
		writeJson(resolve(root, directory, 'target-contract.json'), target);
		const targetIdentitySha256 = digest(resolve(root, directory, 'target-contract.json'));
		writeJson(resolve(root, directory, 'baseline-summary.json'), { case: caseIdentity, sourceCommit: target.sourceCommit, targetIdentitySha256, targetRoleIdentity: targetRoleIdentity(target), workloadContract: workloadContract(target), memberCount: 1000, mode, metrics: { measuredRequestCount: mode.endsWith('sequential') ? 10 : 5, latencyMs: { p50: 10 + index, p95: 20 + index, p99: 30 + index, max: 40 + index }, throughputRps: 1 + index, failureRate: 0 } });
		writeJson(resolve(root, directory, 'baseline-adoption.json'), { case: caseIdentity, accepted: false, automaticAdoption: false, evidenceIntegrity: 'validated', measurementStatus: 'conditional-boundary-only', conditionalSummaryAvailable: true });
		writeJson(resolve(root, directory, 'fixture-manifest.json'), { datasetId: caseIdentity.datasetId, fixtureRunId: caseIdentity.fixtureRunId, selectedMode: mode, memberCount: 1000 });
		return { mode, directory, case: caseIdentity, files: Object.fromEntries(['summary', 'adoption', 'target', 'fixture'].map((name) => [name, { name: ({ summary: 'baseline-summary.json', adoption: 'baseline-adoption.json', target: 'target-contract.json', fixture: 'fixture-manifest.json' })[name], sha256: digest(resolve(root, directory, ({ summary: 'baseline-summary.json', adoption: 'baseline-adoption.json', target: 'target-contract.json', fixture: 'fixture-manifest.json' })[name])) }])) };
	});
	return { root, manifest: { protocolVersion: 'faithlog-192-single-mode-v1', runs } };
}
function refresh(fixture, index, name) { fixture.manifest.runs[index].files[name].sha256 = digest(resolve(fixture.root, fixture.manifest.runs[index].directory, fixture.manifest.runs[index].files[name].name)); }
function digest(path) { return createHash('sha256').update(readFileSync(path)).digest('hex'); }
function json(path) { return JSON.parse(readFileSync(path, 'utf8')); }
function writeJson(path, value) { writeFileSync(path, `${JSON.stringify(value)}\n`); }
