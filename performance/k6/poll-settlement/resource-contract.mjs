const MAX_SAFE = BigInt(Number.MAX_SAFE_INTEGER);
const ROLES = ['app', 'postgres', 'redis'];
const BYTE_SCALES = { B: 1n, kB: 1000n, KiB: 1024n, MB: 1000000n, MiB: 1048576n, GB: 1000000000n, GiB: 1073741824n };

export function parseDockerStatsRows(rows, target) {
	if (!Array.isArray(rows) || rows.length !== ROLES.length) throw new Error('resource row count mismatch');
	const result = {};
	const consumed = new Set();
	for (const role of ROLES) {
		const expected = target.containers?.[role];
		if (!expected || !/^[a-f0-9]{64}$/.test(expected.id) || typeof expected.name !== 'string' || !expected.name) throw new Error(`resource target: ${role}`);
		const matches = rows.map((row, index) => ({ row, index })).filter(({ row }) => row?.ID === expected.id && row?.Name === expected.name);
		if (matches.length !== 1 || consumed.has(matches[0]?.index)) throw new Error(`resource container row: ${role}`);
		const { row, index } = matches[0]; consumed.add(index);
		const cpuPercent = parsePercentDisplay(row.CPUPerc, 'CPU');
		const memoryPercent = parsePercentDisplay(row.MemPerc, 'memory');
		const [usedText, limitText, extra] = String(row.MemUsage).split('/').map((value) => value.trim());
		if (extra !== undefined || !usedText || !limitText) throw new Error('resource memory shape');
		const memoryUsedBytes = parseByteDisplay(usedText); const memoryLimitBytes = parseByteDisplay(limitText);
		const used = BigInt(memoryUsedBytes); const limit = BigInt(memoryLimitBytes);
		if (limit === 0n || used > limit) throw new Error('resource memory bounds');
		validateMemoryDisplayConsistency(usedText, limitText, row.MemPerc);
		result[role] = {
			containerId: expected.id, containerName: expected.name,
			cpuPercent, cpuPercentDisplay: String(row.CPUPerc),
			memoryUsedBytes, memoryLimitBytes, memoryUsageDisplay: String(row.MemUsage),
			memoryPercent, memoryPercentDisplay: String(row.MemPerc),
		};
	}
	if (consumed.size !== rows.length) throw new Error('resource foreign or duplicate row');
	return result;
}

export function parsePercentDisplay(value, label = 'resource') { const match = /^(0|[1-9]\d*)(?:\.(\d+))?%$/.exec(String(value)); if (!match) throw new Error(`Invalid ${label} percent: ${value}`); const parsed = Number(`${match[1]}${match[2] ? `.${match[2]}` : ''}`); if (!Number.isFinite(parsed)) throw new Error(`Invalid ${label} percent`); return parsed; }
export function parseByteDisplay(value) {
	const display = parseByteParts(value);
	const rounded = (display.centerNumerator + display.centerDenominator / 2n) / display.centerDenominator;
	if (rounded > MAX_SAFE) throw new Error(`Unsafe memory: ${value}`);
	return String(rounded);
}

export function validateMemoryDisplayConsistency(usedText, limitText, percentText) {
	const used = byteInterval(usedText, true); const limit = byteInterval(limitText, false); const percent = percentInterval(percentText);
	if (percent.center.numerator > 100n * percent.center.denominator) throw new Error('resource memory percent consistency');
	if (limit.lower.numerator <= 0n) throw new Error('resource memory bounds');
	const ratioLower = divide(multiply(used.lower, 100n), limit.upper);
	const ratioUpper = divide(multiply(used.upper, 100n), limit.lower);
	if (!lessThanOrEqual(ratioLower, percent.upper) || !lessThanOrEqual(percent.lower, ratioUpper)) throw new Error('resource memory percent consistency');
	return true;
}

function byteInterval(value, clampLower) {
	const display = parseByteParts(value); const denominator = display.centerDenominator * 2n;
	let lowerNumerator = display.centerNumerator * 2n - display.scale;
	if (clampLower && lowerNumerator < 0n) lowerNumerator = 0n;
	return {
		lower: fraction(lowerNumerator, denominator),
		upper: fraction(display.centerNumerator * 2n + display.scale, denominator),
	};
}

function percentInterval(value) {
	const match = /^(0|[1-9]\d*)(?:\.(\d+))?%$/.exec(String(value));
	if (!match) throw new Error(`Invalid memory percent: ${value}`);
	const denominator = 10n ** BigInt(match[2]?.length ?? 0); const digits = BigInt(`${match[1]}${match[2] ?? ''}`);
	return { center: fraction(digits, denominator), lower: fraction(digits * 2n - 1n, denominator * 2n), upper: fraction(digits * 2n + 1n, denominator * 2n) };
}

function parseByteParts(value) {
	const match = /^(0|[1-9]\d*)(?:\.(\d+))?\s*(B|kB|KiB|MB|MiB|GB|GiB)$/.exec(value);
	if (!match) throw new Error(`Invalid memory: ${value}`);
	if (match[3] === 'B' && match[2]) throw new Error(`Fractional byte: ${value}`);
	const scale = BYTE_SCALES[match[3]]; const centerDenominator = 10n ** BigInt(match[2]?.length ?? 0);
	const centerNumerator = BigInt(`${match[1]}${match[2] ?? ''}`) * scale;
	return { centerNumerator, centerDenominator, scale };
}

function fraction(numerator, denominator) { return { numerator, denominator }; }
function multiply(value, multiplier) { return fraction(value.numerator * multiplier, value.denominator); }
function divide(left, right) { return fraction(left.numerator * right.denominator, left.denominator * right.numerator); }
function lessThanOrEqual(left, right) { return left.numerator * right.denominator <= right.numerator * left.denominator; }
