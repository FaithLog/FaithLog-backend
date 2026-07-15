import {readFile} from 'node:fs/promises';
import {REQUEST_CASE_NAMES} from './scenario-definition.mjs';

export function validateMeasuredSummary(summary, {expectedRequestCount} = {}) {
	const invalidMetrics = [];
	const observedCounts = [];
	for (const name of REQUEST_CASE_NAMES) {
		const failureMetric = `admin_charge_${name}_failure`;
		const failure = valuesOf(summary?.metrics?.[failureMetric]);
		const requestsMetric = `admin_charge_${name}_requests`;
		const requests = valuesOf(summary?.metrics?.[requestsMetric]);
		const durationMetric = `admin_charge_${name}_duration`;
		const duration = valuesOf(summary?.metrics?.[durationMetric]);

		const requestCount = requests?.count;
		const throughput = requests?.rate;
		if (!Number.isSafeInteger(requestCount) || requestCount <= 0
			|| (expectedRequestCount !== undefined && requestCount !== expectedRequestCount)) {
			invalidMetrics.push(`${requestsMetric}.count=${String(requestCount)}`);
		}
		if (!finiteNonNegative(throughput) || throughput === 0) {
			invalidMetrics.push(`${requestsMetric}.rate=${String(throughput)}`);
		}
		observedCounts.push(requestCount);

		const failureRate = failure?.rate;
		const passes = failure?.passes;
		const fails = failure?.fails;
		if (failureRate !== 0
			|| !Number.isSafeInteger(passes) || passes !== 0
			|| !Number.isSafeInteger(fails) || fails < 0
			|| passes + fails !== requestCount
			|| failureRate !== passes / (passes + fails)) {
			invalidMetrics.push(`${failureMetric} failure math is invalid`);
		}

		const latencyFields = ['avg', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'];
		for (const field of latencyFields) {
			if (!finiteNonNegative(duration?.[field])) {
				invalidMetrics.push(`${durationMetric}.${field}=${String(duration?.[field])}`);
			}
		}
		if (!Number.isSafeInteger(duration?.count) || duration.count !== requestCount) {
			invalidMetrics.push(`${durationMetric}.count=${String(duration?.count)}`);
		}
		if (finiteNonNegative(duration?.med) && finiteNonNegative(duration?.['p(50)'])
			&& duration.med !== duration['p(50)']) {
			invalidMetrics.push(`${durationMetric} median and p(50) differ`);
		}
		const ordered = [duration?.['p(50)'], duration?.['p(95)'], duration?.['p(99)'], duration?.max];
		if (ordered.every(finiteNonNegative)
			&& ordered.some((value, index) => index > 0 && ordered[index - 1] > value)) {
			invalidMetrics.push(`${durationMetric} percentile order is invalid`);
		}
	}
	if (observedCounts.some((count) => count !== observedCounts[0])) {
		invalidMetrics.push('The 16 ordered cases do not have identical request counts.');
	}
	if (invalidMetrics.length > 0) {
		throw new Error(`Measured summary adoption gate rejected the summary: ${invalidMetrics.join(', ')}`);
	}
	return true;
}

function valuesOf(metric) {
	return metric?.values ?? metric;
}

function finiteNonNegative(value) {
	return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

const summaryPath = process.argv[2];
if (summaryPath) {
	const summary = JSON.parse(await readFile(summaryPath, 'utf8'));
	const expectedRequestCount = process.argv[3] === undefined ? undefined : Number(process.argv[3]);
	validateMeasuredSummary(summary, {expectedRequestCount});
}
