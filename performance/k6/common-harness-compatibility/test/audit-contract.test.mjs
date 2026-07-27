import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
	auditTarget,
	createImmutableAuditReport,
	normalizeMetric,
	parseDockerByteSize,
	validateTargetContinuity,
} from '../audit-core.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const contract = JSON.parse(fs.readFileSync(path.join(root, 'matrix-contract.json'), 'utf8'));

const expectedChecks = [
	'k6-json-init',
	'k6-metric-math',
	'secret-serialization',
	'token-ttl',
	'docker-resource-identity',
	'psql-machine-io',
	'fresh-report-root',
	'db-control-attribution',
	'runtime-continuity',
	'macos-k6-v2',
];

test('matrix contract covers issues 192 through 199 and all ten compatibility checks', () => {
	assert.equal(contract.schemaVersion, 1);
	assert.deepEqual(contract.issueNumbers, [192, 193, 194, 195, 196, 197, 198, 199]);
	assert.deepEqual(contract.checks.map(({ id }) => id), expectedChecks);
	assert.equal(contract.targets.length, 8);
	for (const target of contract.targets) {
		assert.match(target.worktreeEnv, /^ISSUE_(?:19[2-9])_WORKTREE$/);
		assert.equal(target.cells.length, 10);
		assert.deepEqual(target.cells.map(({ checkId }) => checkId), expectedChecks);
		for (const cell of target.cells) {
			assert.ok(['probe', 'not-applicable'].includes(cell.mode));
			if (cell.mode === 'probe') {
				assert.ok(cell.probes.length > 0);
				assert.equal(typeof cell.recommendation, 'string');
				for (const probe of cell.probes) {
					assert.notEqual(probe.require, '.', `${target.issueNumber}/${cell.checkId} cannot pass on README existence`);
					assert.ok(probe.command || probe.require || probe.forbid || probe.capability);
					if (probe.capability === 'rate-exact-external-count') {
						assert.deepEqual(probe.focusedCommand?.slice(0, 2), ['node', '--test']);
						assert.ok(probe.markerAlternatives?.every((alternative) => alternative.length >= 3));
					}
				}
			} else {
				assert.equal(typeof cell.reason, 'string');
				assert.match(cell.evidence.file, /^performance\/k6\//);
				assert.ok(Number.isInteger(cell.evidence.line) && cell.evidence.line > 0);
			}
		}
	}
});

test('dirty targets and HEAD or status drift fail continuity', () => {
	const clean = { head: 'abc', statusHash: 'empty', dirty: false };
	assert.equal(validateTargetContinuity(clean, { ...clean }), true);
	assert.equal(validateTargetContinuity({ ...clean, dirty: true }, clean), false);
	assert.equal(validateTargetContinuity(clean, { ...clean, head: 'def' }), false);
	assert.equal(validateTargetContinuity(clean, { ...clean, statusHash: 'changed' }), false);
});

test('installed-k6 fixture is no-HTTP and handleSummary excludes its token sentinel', () => {
	const fixture = fs.readFileSync(path.join(root, 'test/k6-no-http-serialization.js'), 'utf8');
	assert.doesNotMatch(fixture, /from ['"]k6\/http['"]|http\./);
	assert.match(fixture, /accessToken: 'must-not-be-serialized'/);
	const summary = fixture.slice(fixture.indexOf('export function handleSummary'));
	assert.doesNotMatch(summary, /accessToken|must-not-be-serialized/);
	const runner = fs.readFileSync(path.join(root, 'run-audit.mjs'), 'utf8');
	assert.match(runner, /\['inspect', fixture\]/);
	assert.match(runner, /\['run', '--quiet', fixture\]/);
});

test('focused compatibility tests have enough headroom for the slowest integrated contract', () => {
	const runner = fs.readFileSync(path.join(root, 'run-audit.mjs'), 'utf8');
	const timeout = runner.match(/const FOCUSED_TEST_TIMEOUT_MS = ([\d_]+);/);
	assert.ok(timeout, 'focused test timeout must be an explicit named contract');
	assert.ok(Number(timeout[1].replaceAll('_', '')) >= 120_000);
	assert.match(runner, /timeout: FOCUSED_TEST_TIMEOUT_MS/);
});

test('EXPLAIN-only #194 and local Gradle #198 do not inherit irrelevant k6 HTTP gates', () => {
	for (const issueNumber of [194, 198]) {
		const target = contract.targets.find((candidate) => candidate.issueNumber === issueNumber);
		for (const checkId of ['k6-json-init', 'k6-metric-math', 'secret-serialization', 'token-ttl', 'macos-k6-v2']) {
			const cell = target.cells.find((candidate) => candidate.checkId === checkId);
			assert.equal(cell.mode, 'not-applicable');
			assert.match(cell.reason, issueNumber === 194 ? /EXPLAIN-only/ : /Gradle/);
		}
	}
});

test('Counter, Rate, and Trend direct and values shapes normalize without losing exact math', () => {
	assert.deepEqual(normalizeMetric({ count: 4, rate: 4 }, 'counter'), { count: 4 });
	assert.deepEqual(normalizeMetric({ values: { count: 4, rate: 4 } }, 'counter'), { count: 4 });
	assert.deepEqual(normalizeMetric({ value: 0, passes: 0, fails: 4 }, 'rate', 4), {
		value: 0, passes: 0, fails: 4, expectedTotal: 4,
	});
	assert.deepEqual(normalizeMetric({ values: { rate: 0, passes: 0, fails: 4 } }, 'rate', 4), {
		value: 0, passes: 0, fails: 4, expectedTotal: 4,
	});
	assert.deepEqual(
		normalizeMetric({ values: { count: 4, avg: 5, med: 5, 'p(50)': 5, 'p(95)': 8, 'p(99)': 9, max: 10 } }, 'trend'),
		{ count: 4, avg: 5, med: 5, p50: 5, p95: 8, p99: 9, max: 10 },
	);
	assert.throws(
		() => normalizeMetric({ values: { rate: 0, passes: 1, fails: 0 } }, 'rate', 2),
		/passes.*fails.*count|exact/i,
	);
});

test('Docker byte parser covers decimal and binary units deterministically', () => {
	const cases = {
		'1B': 1n,
		'1kB': 1000n,
		'1KB': 1000n,
		'1KiB': 1024n,
		'1MB': 1000000n,
		'1MiB': 1048576n,
		'1GB': 1000000000n,
		'1GiB': 1073741824n,
		'1TB': 1000000000000n,
		'1TiB': 1099511627776n,
		'1.5MiB': 1572864n,
		'586.832MiB': 615337951n,
	};
	for (const [input, expected] of Object.entries(cases)) {
		assert.equal(parseDockerByteSize(input), expected, input);
	}
	for (const invalid of ['1', '-1MiB', 'NaNMiB', '1PiB', '9007199254740992B']) {
		assert.throws(() => parseDockerByteSize(invalid), /Docker byte size|unit|exact/i, invalid);
	}
});

test('N/A evidence is a contained regular file with an existing nonblank scope marker line', () => {
	const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-208-na-'));
	try {
		fs.writeFileSync(path.join(temporary, 'scope.md'), 'EXPLAIN-only diagnostic\n');
		const valid = auditTarget({ issueNumber: 194, worktree: temporary, cells: [{ checkId: 'k6', mode: 'not-applicable', reason: 'not used', evidence: { file: 'scope.md', line: 1, scopeMarker: 'EXPLAIN-only' } }] });
		assert.equal(valid.cells[0].status, 'N/A');
		assert.throws(() => auditTarget({ issueNumber: 194, worktree: temporary, cells: [{ checkId: 'k6', mode: 'not-applicable', reason: 'not used', evidence: { file: 'scope.md', line: 2, scopeMarker: 'EXPLAIN-only' } }] }), /evidence|line/i);
		assert.throws(() => auditTarget({ issueNumber: 194, worktree: temporary, cells: [{ checkId: 'k6', mode: 'not-applicable', reason: 'not used', evidence: { file: '../outside', line: 1, scopeMarker: 'x' } }] }), /traversal|evidence/i);
	} finally { fs.rmSync(temporary, { recursive: true, force: true }); }
});

test('generic probes return machine-readable PASS, FAIL with line/counterexample, and N/A', () => {
	const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-208-audit-'));
	try {
		fs.mkdirSync(path.join(temporary, 'performance'), { recursive: true });
		fs.writeFileSync(path.join(temporary, 'performance', 'scenario.js'), [
			"const manifest = JSON.parse(open(__ENV.MANIFEST));",
			"export function setup() { return { accessToken: 'secret' }; }",
		].join('\n'));
		const result = auditTarget({
			issueNumber: 999,
			worktree: temporary,
			cells: [
				{
					checkId: 'json', mode: 'probe', recommendation: 'Use JSON.parse(open(path)).',
					probes: [{ file: 'performance/scenario.js', require: 'JSON\\.parse\\(open\\(' }],
				},
				{
					checkId: 'secret', mode: 'probe', recommendation: 'Keep the token outside setup data.',
					probes: [{ file: 'performance/scenario.js', forbid: 'return \\{ accessToken:' }],
				},
				{
					checkId: 'rate', mode: 'probe', recommendation: 'Validate exact Rate math against the Counter.',
					probes: [{ file: 'performance/scenario.js', capability: 'rate-exact-external-count' }],
				},
				{ checkId: 'not-used', mode: 'not-applicable', reason: 'No PostgreSQL process exists.', evidence: { file: 'performance/scenario.js', line: 1, scopeMarker: 'manifest' } },
			],
		});
		assert.equal(result.cells[0].status, 'PASS');
		assert.equal(result.cells[1].status, 'FAIL');
		assert.equal(result.cells[1].findings[0].line, 2);
		assert.match(result.cells[1].findings[0].counterexample, /accessToken/);
		assert.equal(result.cells[1].findings[0].recommendation, 'Keep the token outside setup data.');
		assert.equal(result.cells[2].status, 'FAIL');
		assert.equal(result.cells[3].status, 'N/A');
	} finally {
		fs.rmSync(temporary, { recursive: true, force: true });
	}
});

test('#197 helper-based Rate evidence is accepted but keyword-only text is rejected', () => {
	const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-208-rate-capability-'));
	try {
		fs.writeFileSync(path.join(temporary, 'valid.mjs'), `
function extractPhaseEvidence(summary) {
  const failures = metricValues(summary, 'failure');
  const transactions = metricValues(summary, 'transactions');
  return { transactions: transactions.count, failurePasses: failures.passes, failureFails: failures.fails };
}
function failureRate(failures) {
  const rate = failures.rate;
  const value = failures.value;
  if (rate !== undefined && value !== undefined) assert.equal(rate, value);
  return rate ?? value;
}
export function validateSummary(summary, expectedTransactions) {
  const evidence = extractPhaseEvidence(summary);
  const failureTotal = evidence.failurePasses + evidence.failureFails;
  assert.equal(failureTotal, evidence.transactions);
  assert.equal(failureTotal, expectedTransactions);
}
`);
		fs.writeFileSync(path.join(temporary, 'keywords.mjs'), '// transactions.count passes fails expectedTransactions rate value assert.equal\n');
		const command = ['node', '--test', 'focused.test.mjs'];
		const cell = (file) => ({ checkId: 'rate', mode: 'probe', recommendation: 'exact Rate math', probes: [{ file, capability: 'rate-exact-external-count', focusedCommand: command, markerAlternatives: [['transactions\\.count', '(?:evidence\\.)?failurePasses\\s*\\+\\s*(?:evidence\\.)?failureFails', 'assert\\.equal\\(failureTotal, evidence\\.transactions', 'assert\\.equal\\(rate, value']] }] });
		const validResult = auditTarget({ issueNumber: 197, worktree: temporary, cells: [cell('valid.mjs')], commandEvidence: { [JSON.stringify(command)]: { ok: true } } });
		assert.equal(validResult.cells[0].status, 'PASS', JSON.stringify(validResult.cells[0]));
		assert.equal(auditTarget({ issueNumber: 999, worktree: temporary, cells: [cell('keywords.mjs')], commandEvidence: {} }).cells[0].status, 'FAIL');
	} finally { fs.rmSync(temporary, { recursive: true, force: true }); }
});

test('audit report root is absolute, fresh, exclusive, and preserves the first rejection', () => {
	const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-208-report-'));
	const reportRoot = path.join(temporary, 'fresh-report');
	try {
		const first = createImmutableAuditReport(reportRoot, { status: 'rejected', reasons: ['first'] });
		assert.equal(first.status, 'rejected');
		assert.equal(fs.statSync(path.join(reportRoot, 'audit-report.json')).mode & 0o777, 0o600);
		assert.throws(
			() => createImmutableAuditReport(reportRoot, { status: 'rejected', reasons: ['second'] }),
			/already exists|fresh/i,
		);
		assert.deepEqual(
			JSON.parse(fs.readFileSync(path.join(reportRoot, 'audit-report.json'), 'utf8')).reasons,
			['first'],
		);
		assert.throws(() => createImmutableAuditReport('relative/report', {}), /absolute/i);
	} finally {
		fs.rmSync(temporary, { recursive: true, force: true });
	}
});
