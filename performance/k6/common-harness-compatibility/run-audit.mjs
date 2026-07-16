#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { auditTarget, createImmutableAuditReport } from './audit-core.mjs';

const root = path.dirname(fileURLToPath(import.meta.url));
const contract = JSON.parse(fs.readFileSync(path.join(root, 'matrix-contract.json'), 'utf8'));
const reportRoot = process.env.AUDIT_REPORT_ROOT;
if (!reportRoot || !path.isAbsolute(reportRoot)) throw new Error('AUDIT_REPORT_ROOT is runtime-required and absolute');

function git(worktree, args) { return execFileSync('git', ['-C', worktree, ...args], { encoding: 'utf8' }).trim(); }
const targets = contract.targets.map((target) => {
	const worktree = process.env[target.worktreeEnv];
	if (!worktree || !path.isAbsolute(worktree)) throw new Error(`${target.worktreeEnv} is runtime-required and absolute`);
	const result = auditTarget({ issueNumber: target.issueNumber, worktree, cells: target.cells });
	return { ...result, git: { head: git(worktree, ['rev-parse', 'HEAD']), branch: git(worktree, ['branch', '--show-current']), dirty: git(worktree, ['status', '--porcelain']).length > 0 } };
});
const groups = contract.checks.map(({ id, group }) => ({
	id, group,
	issues: targets.filter((target) => target.cells.some((cell) => cell.checkId === id && cell.status === 'FAIL')).map(({ issueNumber }) => issueNumber),
}));
const patchQueue = targets.flatMap((target) => target.cells.flatMap((cell) => (cell.findings ?? []).map((finding) => ({ issueNumber: target.issueNumber, checkId: cell.checkId, ...finding }))));
let installedK6 = null;
try { installedK6 = execFileSync('k6', ['version'], { encoding: 'utf8' }).trim(); } catch { installedK6 = 'unavailable'; }
const actualLoadBlocked = patchQueue.length > 0 || !/^k6 v2\.0\.0\b/.test(installedK6);
const report = createImmutableAuditReport(reportRoot, { status: actualLoadBlocked ? 'rejected-pending-compatibility' : 'scenario-ready-not-measured', installedK6, targets, causeGroups: groups, patchQueue, actualLoadBlocked });
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (actualLoadBlocked) process.exitCode = 2;
