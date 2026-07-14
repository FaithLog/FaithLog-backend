import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const summaryPath = process.argv[2];
if (!summaryPath) {
	throw new Error('Usage: node validate-k6-summary.mjs <summary.json>');
}

const summary = JSON.parse(fs.readFileSync(path.resolve(summaryPath), 'utf8'));
const duration = metricValues(summary, 'admin_dashboard_duration');
const requests = metricValues(summary, 'admin_dashboard_requests');
const failures = metricValues(summary, 'admin_dashboard_failure_rate');

const failureRate = number(failures.rate ?? failures.value, 'admin_dashboard_failure_rate.rate');
assert.equal(failureRate, 0, 'admin_dashboard_failure_rate must be exactly zero');

const requestCount = number(requests.count, 'admin_dashboard_requests.count');
const throughput = number(requests.rate, 'admin_dashboard_requests.rate');
assert.ok(requestCount > 0, 'admin_dashboard_requests.count must be greater than zero');
assert.ok(throughput > 0, 'admin_dashboard_requests.rate throughput must be greater than zero');

const latency = {};
for (const statistic of ['p(50)', 'p(95)', 'p(99)', 'max']) {
	latency[statistic] = number(duration[statistic], `admin_dashboard_duration.${statistic}`);
	assert.ok(latency[statistic] >= 0, `${statistic} must not be negative`);
}

process.stdout.write(`${JSON.stringify({
	status: 'k6-summary-adoptable',
	summaryPath: path.resolve(summaryPath),
	failureRate,
	requestCount,
	throughput,
	latency,
}, null, 2)}\n`);

function metricValues(summaryDocument, metricName) {
	const metric = summaryDocument.metrics?.[metricName];
	assert.ok(metric, `Missing k6 metric ${metricName}`);
	return metric.values || metric;
}

function number(value, label) {
	assert.equal(typeof value, 'number', `Missing numeric k6 value ${label}`);
	assert.ok(Number.isFinite(value), `${label} must be finite`);
	return value;
}
