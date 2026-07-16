import fs from 'node:fs';
import path from 'node:path';

const UNITS = { B: 1n, kB: 1000n, KB: 1000n, KiB: 1024n, MB: 1000000n, MiB: 1048576n, GB: 1000000000n, GiB: 1073741824n, TB: 1000000000000n, TiB: 1099511627776n };

export function parseDockerByteSize(input) {
	const match = /^(\d+)(?:\.(\d+))?(B|kB|KB|KiB|MB|MiB|GB|GiB|TB|TiB)$/.exec(input);
	if (!match) throw new Error(`Invalid Docker byte size or unit: ${input}`);
	const fraction = match[2] ?? '';
	const scale = 10n ** BigInt(fraction.length);
	const numerator = BigInt(`${match[1]}${fraction}`) * UNITS[match[3]];
	if (numerator % scale !== 0n) throw new Error(`Docker byte size is not exact: ${input}`);
	return numerator / scale;
}

function finite(value, name) {
	if (!Number.isFinite(value)) throw new Error(`${name} must be finite`);
	return value;
}

export function normalizeMetric(metric, kind) {
	const value = metric?.values ?? metric;
	if (!value || typeof value !== 'object') throw new Error(`${kind} metric is missing`);
	if (kind === 'counter') return { count: finite(value.count, 'count') };
	if (kind === 'rate') {
		const result = { value: finite(value.value ?? value.rate, 'value'), passes: finite(value.passes, 'passes'), fails: finite(value.fails, 'fails'), count: finite(value.count, 'count') };
		if (![result.passes, result.fails, result.count].every(Number.isSafeInteger) || result.passes + result.fails !== result.count || (result.count > 0 && result.value !== result.passes / result.count)) {
			throw new Error('Rate exact math requires passes + fails = count and value = passes/count');
		}
		return result;
	}
	if (kind === 'trend') {
		const result = { count: finite(value.count, 'count'), avg: finite(value.avg, 'avg'), med: finite(value.med, 'med'), p50: finite(value['p(50)'], 'p50'), p95: finite(value['p(95)'], 'p95'), p99: finite(value['p(99)'], 'p99'), max: finite(value.max, 'max') };
		if (result.med !== result.p50 || !(result.p50 <= result.p95 && result.p95 <= result.p99 && result.p99 <= result.max) || result.avg > result.max) throw new Error('Trend values must preserve exact ordered latency');
		return result;
	}
	throw new Error(`Unknown metric kind: ${kind}`);
}

function resolveRegularFile(root, relative) {
	const absolute = path.resolve(root, relative);
	if (absolute !== root && !absolute.startsWith(`${root}${path.sep}`)) throw new Error(`Path traversal rejected: ${relative}`);
	const stat = fs.lstatSync(absolute);
	if (!stat.isFile() || stat.isSymbolicLink()) throw new Error(`Probe must be a regular non-symlink file: ${relative}`);
	return absolute;
}

function findLine(text, expression) {
	const match = new RegExp(expression, 'm').exec(text);
	return match ? { line: text.slice(0, match.index).split('\n').length, counterexample: text.split('\n')[text.slice(0, match.index).split('\n').length - 1].trim() } : null;
}

export function auditTarget({ issueNumber, worktree, cells }) {
	if (!path.isAbsolute(worktree)) throw new Error('worktree must be absolute');
	const root = fs.realpathSync(worktree);
	return {
		issueNumber,
		worktree: root,
		cells: cells.map((cell) => {
			if (cell.mode === 'not-applicable') return { checkId: cell.checkId, status: 'N/A', reason: cell.reason, evidence: cell.evidence };
			const findings = [];
			for (const probe of cell.probes) {
				let text;
				try { text = fs.readFileSync(resolveRegularFile(root, probe.file), 'utf8'); }
				catch (error) { findings.push({ file: probe.file, line: 1, counterexample: error.message, recommendation: cell.recommendation }); continue; }
				const required = probe.require ? findLine(text, probe.require) : null;
				const forbidden = probe.forbid ? findLine(text, probe.forbid) : null;
				if (probe.require && !required) findings.push({ file: probe.file, line: 1, counterexample: `missing required pattern: ${probe.require}`, recommendation: cell.recommendation });
				if (forbidden) findings.push({ file: probe.file, ...forbidden, recommendation: cell.recommendation });
			}
			return { checkId: cell.checkId, status: findings.length ? 'FAIL' : 'PASS', findings };
		}),
	};
}

export function createImmutableAuditReport(reportRoot, report) {
	if (!path.isAbsolute(reportRoot)) throw new Error('report root must be absolute');
	fs.mkdirSync(reportRoot, { recursive: false, mode: 0o700 });
	const complete = { schemaVersion: 1, automaticAdoption: false, supportingOnly: true, actualLoadBlocked: report.actualLoadBlocked ?? true, ...report };
	const file = path.join(reportRoot, 'audit-report.json');
	const descriptor = fs.openSync(file, 'wx', 0o600);
	try { fs.writeFileSync(descriptor, `${JSON.stringify(complete, null, 2)}\n`); } finally { fs.closeSync(descriptor); }
	return complete;
}
