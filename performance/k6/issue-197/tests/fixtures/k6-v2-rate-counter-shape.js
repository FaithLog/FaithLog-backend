import { Counter, Rate, Trend } from 'k6/metrics';

const failures = new Rate('devotion_weekly_measured_failure');
const transactions = new Counter('devotion_weekly_measured_transactions');
const latency = new Trend('devotion_weekly_measured', true);

export const options = {
	iterations: 2,
	vus: 1,
	summaryTrendStats: ['p(50)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
	failures.add(false);
	transactions.add(1);
	latency.add(1);
}
