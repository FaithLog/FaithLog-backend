import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const summaryPath = process.argv[2];
if (!summaryPath) {
	throw new Error('Usage: node validate-k6-summary.mjs <summary.json>');
}

try {
	const summary = JSON.parse(fs.readFileSync(path.resolve(summaryPath), 'utf8'));
	const duration = metricValues(summary, 'admin_dashboard_duration');
	const requests = metricValues(summary, 'admin_dashboard_requests');
	const failures = metricValues(summary, 'admin_dashboard_failure_rate');

	const failureRate = number(rateValue(failures), 'admin_dashboard_failure_rate.rate');
	assert.equal(failureRate, 0, 'admin_dashboard_failure_rate must be exactly zero');

	const requestCount = number(requests.count, 'admin_dashboard_requests.count');
	const throughput = number(requests.rate, 'admin_dashboard_requests.rate');
	assert.ok(Number.isSafeInteger(requestCount) && requestCount > 0,
		'admin_dashboard_requests.count must be a positive safe integer');
	assert.ok(throughput > 0, 'admin_dashboard_requests.rate throughput must be greater than zero');
	if (Object.hasOwn(failures, 'passes') || Object.hasOwn(failures, 'fails')) {
		const passes = safeCount(failures.passes, 'admin_dashboard_failure_rate.passes');
		const fails = safeCount(failures.fails, 'admin_dashboard_failure_rate.fails');
		assert.equal(passes + fails, requestCount, 'failure Rate sample count must equal request Counter count');
		assert.equal(passes, 0, 'zero failure Rate must have zero true failure samples');
	}

	const latency = {};
	for (const statistic of ['p(50)', 'p(95)', 'p(99)', 'max']) {
		latency[statistic] = number(duration[statistic], `admin_dashboard_duration.${statistic}`);
		assert.ok(latency[statistic] >= 0, `${statistic} must not be negative`);
	}
	assert.ok(
		latency['p(50)'] <= latency['p(95)']
		&& latency['p(95)'] <= latency['p(99)']
		&& latency['p(99)'] <= latency.max,
		'admin_dashboard_duration percentiles must satisfy p50 <= p95 <= p99 <= max',
	);

	process.stdout.write(`${JSON.stringify({
		status: 'k6-summary-valid',
		adoptable: false,
		automaticAdoption: false,
		summaryPath: path.resolve(summaryPath),
		failureRate,
		requestCount,
		throughput,
		latency,
	}, null, 2)}\n`);
} catch (error) {
	process.stdout.write(`${JSON.stringify({
		status: 'contaminated', adoptable: false, automaticAdoption: false,
		failures: [{name: 'k6Summary', actual: error.message}],
	}, null, 2)}\n`);
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}

function metricValues(summaryDocument, metricName) {
	const metric = summaryDocument.metrics?.[metricName];
	assert.ok(metric, `Missing k6 metric ${metricName}`);
	assert.ok(typeof metric === 'object' && !Array.isArray(metric), `Malformed k6 metric ${metricName}`);
	if (Object.hasOwn(metric, 'values')) {
		assert.ok(metric.values !== null && typeof metric.values === 'object' && !Array.isArray(metric.values),
			`Malformed k6 metric values ${metricName}`);
		const directMetricFields = ['count', 'rate', 'value', 'passes', 'fails', 'p(50)', 'p(95)', 'p(99)', 'max'];
		assert.ok(!directMetricFields.some((field) => Object.hasOwn(metric, field)),
			`Mixed direct and values shape for k6 metric ${metricName}`);
		return metric.values;
	}
	return metric;
}

function rateValue(values) {
	assert.ok(Object.hasOwn(values, 'rate') || Object.hasOwn(values, 'value'), 'Failure Rate must contain rate or value.');
	if (Object.hasOwn(values, 'rate') && Object.hasOwn(values, 'value')) {
		assert.equal(values.rate, values.value, 'Failure Rate rate and value must agree.');
	}
	return values.rate ?? values.value;
}

function safeCount(value, label) {
	assert.ok(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative safe integer.`);
	return value;
}

function number(value, label) {
	assert.equal(typeof value, 'number', `Missing numeric k6 value ${label}`);
	assert.ok(Number.isFinite(value), `${label} must be finite`);
	return value;
}
