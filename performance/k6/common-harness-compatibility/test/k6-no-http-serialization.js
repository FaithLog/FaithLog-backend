import { Counter, Rate, Trend } from 'k6/metrics';

const count = new Counter('compat_count');
const failure = new Rate('compat_failure');
const latency = new Trend('compat_latency', true);

export const options = { vus: 1, iterations: 1 };
export function setup() { return { accessToken: 'must-not-be-serialized' }; }
export default function () { count.add(1); failure.add(false); latency.add(1); }
export function handleSummary(data) {
	return { stdout: JSON.stringify({ metrics: data.metrics }) };
}
