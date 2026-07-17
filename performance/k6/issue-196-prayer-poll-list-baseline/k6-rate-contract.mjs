export function normalizeCounterMetric(metric) {
	const values = unwrapMetric(metric, ['count'], 'Counter');
	if (!Number.isSafeInteger(values.count) || values.count <= 0) {
		throw new Error('Request Counter count must be a positive safe integer.');
	}
	return { count: values.count };
}

export function normalizeFailureRate(metric, expectedTotal) {
	if (!Number.isSafeInteger(expectedTotal) || expectedTotal <= 0) {
		throw new Error('Rate expected total must be a positive safe integer.');
	}
	const values = unwrapMetric(metric, ['rate', 'value', 'passes', 'fails'], 'Rate');
	const hasRate = Object.hasOwn(values, 'rate');
	const hasValue = Object.hasOwn(values, 'value');
	if (!hasRate && !hasValue) throw new Error('Rate must contain exactly supported rate or value evidence.');
	for (const key of ['rate', 'value']) {
		if (Object.hasOwn(values, key)
			&& (!Number.isFinite(values[key]) || values[key] < 0 || values[key] > 1)) {
			throw new Error(`Rate ${key} must be a finite number in the inclusive range 0..1.`);
		}
	}
	if (hasRate && hasValue && values.rate !== values.value) {
		throw new Error('Rate rate and value evidence must agree exactly.');
	}
	if (!Number.isSafeInteger(values.passes) || values.passes < 0
		|| !Number.isSafeInteger(values.fails) || values.fails < 0) {
		throw new Error('Rate passes and fails must be nonnegative safe integers.');
	}
	const observedTotal = values.passes + values.fails;
	if (!Number.isSafeInteger(observedTotal) || observedTotal !== expectedTotal) {
		throw new Error('Rate passes plus fails must equal the request Counter count exactly.');
	}
	const rate = hasRate ? values.rate : values.value;
	if (rate !== 0 || values.passes !== 0 || values.fails !== expectedTotal) {
		throw new Error('Failure Rate zero math must be rate=0, passes=0, and fails=request count.');
	}
	return { rate, passes: values.passes, fails: values.fails, expectedTotal };
}

function unwrapMetric(metric, semanticKeys, label) {
	if (!metric || typeof metric !== 'object' || Array.isArray(metric)) {
		throw new Error(`${label} metric must be an object.`);
	}
	if (!Object.hasOwn(metric, 'values')) return metric;
	if (!metric.values || typeof metric.values !== 'object' || Array.isArray(metric.values)) {
		throw new Error(`${label} values wrapper must be an object.`);
	}
	if (semanticKeys.some((key) => Object.hasOwn(metric, key))) {
		throw new Error(`${label} metric must not mix direct and values-wrapper evidence.`);
	}
	return metric.values;
}
