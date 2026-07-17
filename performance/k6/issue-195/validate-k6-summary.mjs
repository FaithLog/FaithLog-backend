import fs from 'node:fs';

const [summaryPath, scenario, caseName, phase, outputPath] = process.argv.slice(2);
if (!summaryPath || !scenario || !caseName || !phase || !outputPath) {
	throw new Error('summaryPath, scenario, caseName, phase, and outputPath are required.');
}
if (!['warmup', 'measured'].includes(phase)) {
	throw new Error('phase must be warmup or measured.');
}
const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
const metricName = `issue195_${sanitize(scenario)}_${sanitize(caseName)}`;
const duration = valuesOf(summary.metrics?.[`${metricName}_duration`]);
const requests = valuesOf(summary.metrics?.[`${metricName}_requests`]);
const failures = valuesOf(summary.metrics?.[`${metricName}_failures`]);
for (const key of ['p(50)', 'p(95)', 'p(99)', 'max']) {
	requireNonNegativeFinite(duration?.[key], `${metricName}_duration.${key}`);
}
if (!(duration['p(50)'] <= duration['p(95)']
	&& duration['p(95)'] <= duration['p(99)']
	&& duration['p(99)'] <= duration.max)) {
	throw new Error(`${metricName}_duration percentiles must satisfy p50 <= p95 <= p99 <= max.`);
}
requirePositiveSafeInteger(requests?.count, `${metricName}_requests.count`);
requirePositiveFinite(requests?.rate, `${metricName}_requests.rate`);
if (!Number.isFinite(failures?.value) || failures.value !== 0) {
	throw new Error(`${metricName}_failures.value must be finite and exactly zero.`);
}
requireNonNegativeSafeInteger(failures?.passes, `${metricName}_failures.passes`);
requireNonNegativeSafeInteger(failures?.fails, `${metricName}_failures.fails`);
if (failures.passes !== 0) {
	throw new Error(`${metricName}_failures.passes must be exactly zero.`);
}
if (failures.fails !== requests.count) {
	throw new Error(`${metricName}_failures.fails must equal ${metricName}_requests.count.`);
}
const normalized = {
	status: 'adoptable',
	phase,
	scenario,
	case: caseName,
	metricName,
	requestCount: requests.count,
	throughput: requests.rate,
	failureRate: failures.value,
	latency: {
		p50: duration['p(50)'],
		p95: duration['p(95)'],
		p99: duration['p(99)'],
		max: duration.max,
	},
};
fs.writeFileSync(outputPath, `${JSON.stringify(normalized, null, 2)}\n`);

function valuesOf(metric) {
	return metric?.values ?? metric;
}

function requirePositiveFinite(value, name) {
	if (!Number.isFinite(value) || value <= 0) {
		throw new Error(`${name} must be finite and positive.`);
	}
}

function requireNonNegativeFinite(value, name) {
	if (!Number.isFinite(value) || value < 0) {
		throw new Error(`${name} must be finite and non-negative.`);
	}
}

function requirePositiveSafeInteger(value, name) {
	if (!Number.isSafeInteger(value) || value <= 0) {
		throw new Error(`${name} must be a positive safe integer.`);
	}
}

function requireNonNegativeSafeInteger(value, name) {
	if (!Number.isSafeInteger(value) || value < 0) {
		throw new Error(`${name} must be a non-negative safe integer.`);
	}
}

function sanitize(value) {
	return String(value).replace(/[^a-zA-Z0-9_]/g, '_');
}
