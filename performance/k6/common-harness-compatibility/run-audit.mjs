#!/usr/bin/env node
import { execFileSync, spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { auditTarget, createImmutableAuditReport, validateTargetContinuity } from './audit-core.mjs';

const root = path.dirname(fileURLToPath(import.meta.url));
const contract = JSON.parse(fs.readFileSync(path.join(root, 'matrix-contract.json'), 'utf8'));
const reportRoot = process.env.AUDIT_REPORT_ROOT;
if (!reportRoot || !path.isAbsolute(reportRoot)) throw new Error('AUDIT_REPORT_ROOT is runtime-required and absolute');
const FOCUSED_TEST_TIMEOUT_MS = 120_000;
const sha256 = (value) => crypto.createHash('sha256').update(value).digest('hex');
const git = (worktree, args) => execFileSync('git', ['-C', worktree, ...args], { encoding: 'utf8', timeout: 10_000 }).trim();
const snapshot = (worktree) => ({ head: git(worktree, ['rev-parse', 'HEAD']), statusHash: sha256(git(worktree, ['status', '--porcelain=v1'])), dirty: git(worktree, ['status', '--porcelain=v1']).length > 0 });

function focusedCommand(worktree, command) {
	if (command.length !== 3 || command[0] !== 'node' || command[1] !== '--test' || path.isAbsolute(command[2]) || command[2].includes('..')) throw new Error(`command is outside allowlist: ${command.join(' ')}`);
	const testFile = path.resolve(worktree, command[2]);
	if (!testFile.startsWith(`${path.resolve(worktree)}${path.sep}`) || !fs.statSync(testFile).isFile()) throw new Error(`focused test is not a contained regular file: ${command[2]}`);
	const startedAt = Date.now();
	const result = spawnSync(command[0], command.slice(1), { cwd: worktree, encoding: 'utf8', timeout: FOCUSED_TEST_TIMEOUT_MS, env: { PATH: process.env.PATH, HOME: process.env.HOME, TMPDIR: process.env.TMPDIR } });
	return { command, ok: result.status === 0 && !result.error, exitStatus: result.status, timedOut: result.error?.code === 'ETIMEDOUT', durationMs: Date.now() - startedAt, stdoutSha256: sha256(result.stdout ?? ''), stderrSha256: sha256(result.stderr ?? '') };
}

function installedK6Evidence() {
	const fixture = path.join(root, 'test/k6-no-http-serialization.js');
	const version = execFileSync('k6', ['version'], { encoding: 'utf8', timeout: 10_000 }).trim();
	const inspect = execFileSync('k6', ['inspect', fixture], { encoding: 'utf8', timeout: 10_000 });
	const run = execFileSync('k6', ['run', '--quiet', fixture], { encoding: 'utf8', timeout: 30_000 });
	const sentinelAbsent = !run.includes('must-not-be-serialized');
	const summary = JSON.parse(run);
	const httpSamples = Number(summary.metrics?.http_reqs?.values?.count ?? 0);
	return { version, fixtureSha256: sha256(fs.readFileSync(fixture)), inspect: { ok: true, stdoutSha256: sha256(inspect) }, noHttpRun: { ok: sentinelAbsent && httpSamples === 0, stdoutSha256: sha256(run), sentinelAbsent, httpSamples } };
}

const targets = [];
for (const target of contract.targets) {
	const worktree = process.env[target.worktreeEnv];
	if (!worktree || !path.isAbsolute(worktree) || !fs.existsSync(worktree)) {
		const targetFinding = { checkId: 'target-availability', file: null, line: 1, counterexample: `${target.worktreeEnv} missing or unavailable`, recommendation: 'Restore the approved read-only target worktree before audit.' };
		targets.push({ issueNumber: target.issueNumber, worktree: worktree ?? null, cells: target.cells.map(({ checkId }) => ({ checkId, status: 'FAIL', findings: [targetFinding] })), continuity: { ok: false }, targetFinding });
		continue;
	}
	const initial = snapshot(worktree);
	const commands = [...new Map(target.cells.flatMap((cell) => cell.probes ?? []).filter((probe) => probe.command).map((probe) => [JSON.stringify(probe.command), probe.command])).values()];
	const evidence = {};
	for (const command of commands) evidence[JSON.stringify(command)] = focusedCommand(worktree, command);
	const result = auditTarget({ issueNumber: target.issueNumber, worktree, cells: target.cells, commandEvidence: evidence });
	const final = snapshot(worktree);
	const continuity = { initial, final, ok: validateTargetContinuity(initial, final) };
	targets.push({ ...result, focusedCommands: Object.values(evidence), continuity });
}

let k6Evidence;
try { k6Evidence = installedK6Evidence(); } catch (error) { k6Evidence = { version: 'unavailable', inspect: { ok: false }, noHttpRun: { ok: false }, errorClass: error.name }; }
const groups = contract.checks.map(({ id, group }) => ({ id, group, issues: targets.filter((target) => target.cells.some((cell) => cell.checkId === id && cell.status === 'FAIL')).map(({ issueNumber }) => issueNumber) }));
const patchQueueCandidates = targets.flatMap((target) => target.targetFinding ? [{ issueNumber: target.issueNumber, ...target.targetFinding }] : target.cells.flatMap((cell) => (cell.findings ?? []).map((finding) => ({ issueNumber: target.issueNumber, checkId: cell.checkId, ...finding }))));
const patchQueueByIdentity = new Map();
for (const finding of patchQueueCandidates) {
	const identity = `${finding.issueNumber}:${finding.file}:${finding.line}:${finding.counterexample}`;
	if (!patchQueueByIdentity.has(identity)) patchQueueByIdentity.set(identity, finding);
}
const patchQueue = [...patchQueueByIdentity.values()];
const continuityRejected = targets.some((target) => !target.continuity.ok || target.focusedCommands?.some((command) => !command.ok));
const actualLoadBlocked = patchQueue.length > 0 || continuityRejected || !/^k6 v2\.0\.0\b/.test(k6Evidence.version) || !k6Evidence.inspect.ok || !k6Evidence.noHttpRun.ok;
const report = createImmutableAuditReport(reportRoot, { status: actualLoadBlocked ? 'rejected-pending-compatibility' : 'scenario-ready-not-measured', installedK6: k6Evidence, targets, causeGroups: groups, patchQueue, actualLoadBlocked });
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (actualLoadBlocked) process.exitCode = 2;
