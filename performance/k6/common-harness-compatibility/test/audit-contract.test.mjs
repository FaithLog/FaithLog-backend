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
			} else {
				assert.equal(typeof cell.reason, 'string');
				assert.match(cell.evidence.file, /^performance\/k6\//);
				assert.ok(Number.isInteger(cell.evidence.line) && cell.evidence.line > 0);
			}
		}
	}
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
	assert.deepEqual(normalizeMetric({ value: 0, passes: 0, fails: 4, count: 4 }, 'rate'), {
		value: 0, passes: 0, fails: 4, count: 4,
	});
	assert.deepEqual(normalizeMetric({ values: { rate: 0, passes: 0, fails: 4, count: 4 } }, 'rate'), {
		value: 0, passes: 0, fails: 4, count: 4,
	});
	assert.deepEqual(
		normalizeMetric({ values: { count: 4, avg: 5, med: 5, 'p(50)': 5, 'p(95)': 8, 'p(99)': 9, max: 10 } }, 'trend'),
		{ count: 4, avg: 5, med: 5, p50: 5, p95: 8, p99: 9, max: 10 },
	);
	assert.throws(
		() => normalizeMetric({ values: { rate: 0, passes: 1, fails: 0, count: 2 } }, 'rate'),
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
	};
	for (const [input, expected] of Object.entries(cases)) {
		assert.equal(parseDockerByteSize(input), expected, input);
	}
	for (const invalid of ['1', '-1MiB', 'NaNMiB', '1PiB', '1.0001B']) {
		assert.throws(() => parseDockerByteSize(invalid), /Docker byte size|unit|exact/i, invalid);
	}
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
				{ checkId: 'not-used', mode: 'not-applicable', reason: 'No PostgreSQL process exists.' },
			],
		});
		assert.equal(result.cells[0].status, 'PASS');
		assert.equal(result.cells[1].status, 'FAIL');
		assert.equal(result.cells[1].findings[0].line, 2);
		assert.match(result.cells[1].findings[0].counterexample, /accessToken/);
		assert.equal(result.cells[1].findings[0].recommendation, 'Keep the token outside setup data.');
		assert.equal(result.cells[2].status, 'N/A');
	} finally {
		fs.rmSync(temporary, { recursive: true, force: true });
	}
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
