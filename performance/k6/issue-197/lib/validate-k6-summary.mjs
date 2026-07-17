import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function extractPhaseMetrics(summary, phase) {
	return extractPhaseEvidence(summary, phase).metrics;
}

function extractPhaseEvidence(summary, phase) {
	const trend = metricValues(summary, `devotion_weekly_${phase}`);
	const failures = metricValues(summary, `devotion_weekly_${phase}_failure`);
	const transactions = metricValues(summary, `devotion_weekly_${phase}_transactions`);
	const iterations = metricValues(summary, 'iterations');
	return {
		metrics: {
			p50: numeric(trend['p(50)'], `${phase}.p50`),
			p95: numeric(trend['p(95)'], `${phase}.p95`),
			p99: numeric(trend['p(99)'], `${phase}.p99`),
			max: numeric(trend.max, `${phase}.max`),
			throughput: numeric(iterations.rate, `${phase}.throughput`),
			failureRate: failureRate(failures, phase),
			transactions: nonNegativeSafeInteger(transactions.count, `${phase}.transactions`),
		},
		failurePasses: nonNegativeSafeInteger(failures.passes, `${phase}.failure.passes`),
		failureFails: nonNegativeSafeInteger(failures.fails, `${phase}.failure.fails`),
	};
}

export function validateSummary(summary, phase, expectedTransactions) {
	assert.ok(['warmup', 'measured', 'rollback'].includes(phase), 'phase must be warmup, measured, or rollback');
	assert.ok(Number.isSafeInteger(expectedTransactions) && expectedTransactions > 0, `${phase} expected transaction count must be a positive safe integer`);
	const { metrics, failurePasses, failureFails } = extractPhaseEvidence(summary, phase);
	const failureTotal = failurePasses + failureFails;
	assert.ok(Number.isSafeInteger(failureTotal), `${phase} failure passes + fails total must be a safe integer`);
	assert.equal(failureTotal, metrics.transactions, `${phase} failure passes + fails total must equal transaction Counter total`);
	assert.equal(failureTotal, expectedTransactions, `${phase} failure passes + fails total must be exact`);
	assert.equal(metrics.failureRate, 0, `${phase} failure rate must be zero`);
	assert.equal(failurePasses, 0, `${phase} zero failure rate requires zero failure passes`);
	assert.equal(failureFails, expectedTransactions, `${phase} zero failure rate requires failure fails to equal expected transactions`);
	assert.ok(metrics.transactions > 0, `${phase} transaction count must be positive`);
	assert.equal(metrics.transactions, expectedTransactions, `${phase} transaction count must be exact`);
	assert.ok(metrics.throughput > 0, `${phase} throughput must be positive`);
	for (const percentile of ['p50', 'p95', 'p99', 'max']) {
		assert.ok(metrics[percentile] >= 0, `${phase} ${percentile} latency must be non-negative`);
	}
	assert.ok(
		metrics.p50 <= metrics.p95 && metrics.p95 <= metrics.p99 && metrics.p99 <= metrics.max,
		`${phase} latency percentiles must satisfy p50 <= p95 <= p99 <= max`
	);
	return metrics;
}

function metricValues(summary, metricName) {
	const metric = summary.metrics?.[metricName];
	assert.ok(metric, `Missing k6 metric ${metricName}`);
	return metric.values || metric;
}

function numeric(value, label) {
	assert.equal(typeof value, 'number', `Missing numeric k6 value ${label}`);
	assert.ok(Number.isFinite(value), `${label} must be finite`);
	return value;
}

function nonNegativeSafeInteger(value, label) {
	assert.equal(typeof value, 'number', `Missing numeric k6 value ${label}`);
	assert.ok(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative safe integer`);
	return value;
}

function failureRate(failures, phase) {
	const hasRate = Object.prototype.hasOwnProperty.call(failures, 'rate');
	const hasValue = Object.prototype.hasOwnProperty.call(failures, 'value');
	assert.ok(hasRate || hasValue, `${phase} failure metric must contain rate or value`);
	const rate = hasRate ? numeric(failures.rate, `${phase}.failure.rate`) : undefined;
	const value = hasValue ? numeric(failures.value, `${phase}.failure.value`) : undefined;
	if (hasRate && hasValue) {
		assert.equal(rate, value, `${phase} failure rate and value must agree exactly`);
	}
	return hasRate ? rate : value;
}

async function main() {
	const [summaryPath, phase, expectedTransactions] = process.argv.slice(2);
	const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
	const metrics = validateSummary(summary, phase, Number(expectedTransactions));
	process.stdout.write(`${JSON.stringify({ status: 'adoptable', phase, metrics }, null, 2)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
