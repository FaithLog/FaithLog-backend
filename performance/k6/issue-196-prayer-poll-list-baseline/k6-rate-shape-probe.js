import { Counter, Rate } from 'k6/metrics';

const requests = new Counter('compat_requests');
const failures = new Rate('compat_failures');

export const options = { vus: 1, iterations: 1 };

export default function () {
	requests.add(1);
	failures.add(false);
}

export function handleSummary(data) {
	return { stdout: JSON.stringify({ metrics: data.metrics }) };
}
