import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const PROBE = join(ROOT, 'k6-rate-shape-probe.js');
const CONTRACT = join(ROOT, 'k6-rate-contract.mjs');

test('installed k6 v2 no-HTTP Rate shape preserves exact external Counter math', async () => {
	const probeSource = readFileSync(PROBE, 'utf8');
	assert.doesNotMatch(probeSource, /from ['"]k6\/http['"]|http\./);
	const inspect = spawnSync('k6', ['inspect', PROBE], { encoding: 'utf8', timeout: 10_000 });
	assert.equal(inspect.error, undefined);
	assert.equal(inspect.signal, null);
	assert.equal(inspect.status, 0, inspect.stderr);
	const run = spawnSync('k6', ['run', '--quiet', PROBE], { encoding: 'utf8', timeout: 30_000 });
	assert.equal(run.error, undefined);
	assert.equal(run.signal, null);
	assert.equal(run.status, 0, run.stderr);
	const installed = JSON.parse(run.stdout);
	assert.equal(Number(installed.metrics?.http_reqs?.values?.count ?? 0), 0);
	const counterMetric = installed.metrics.compat_requests;
	const rateMetric = installed.metrics.compat_failures;
	assert.ok(counterMetric && rateMetric, `installed k6 summary is missing custom metrics: ${run.stdout}`);
	assert.deepEqual(rateMetric.values, { rate: 0, passes: 0, fails: 1 });

	const contract = await import(`${pathToFileURL(CONTRACT).href}?installed=${Date.now()}`);
	const counter = contract.normalizeCounterMetric(counterMetric);
	assert.deepEqual(counter, { count: 1 });
	assert.deepEqual(contract.normalizeFailureRate(rateMetric, counter.count), {
		rate: 0, passes: 0, fails: 1, expectedTotal: 1,
	});
});

test('Rate direct/values and rate/value one-of reject malformed or inexact math', async () => {
	const contract = await import(`${pathToFileURL(CONTRACT).href}?shapes=${Date.now()}`);
	for (const metric of [
		{ rate: 0, passes: 0, fails: 2 },
		{ value: 0, passes: 0, fails: 2 },
		{ rate: 0, value: 0, passes: 0, fails: 2 },
		{ values: { rate: 0, passes: 0, fails: 2 } },
		{ values: { value: 0, passes: 0, fails: 2 } },
	]) {
		assert.deepEqual(contract.normalizeFailureRate(metric, 2), { rate: 0, passes: 0, fails: 2, expectedTotal: 2 });
	}
	for (const metric of [
		{ passes: 0, fails: 2 },
		{ rate: 0, value: 1, passes: 0, fails: 2 },
		{ rate: 0, passes: 0, fails: 1 },
		{ rate: 0, passes: 1, fails: 1 },
		{ rate: 0, passes: -1, fails: 3 },
		{ rate: 0, passes: 0.5, fails: 1.5 },
		{ rate: 0, passes: 0, fails: 2, values: { rate: 0, passes: 0, fails: 2 } },
	]) assert.throws(() => contract.normalizeFailureRate(metric, 2), /rate|math|mixed|safe|failure/i);
	for (const count of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1]) {
		assert.throws(() => contract.normalizeCounterMetric({ count }), /count|safe|positive/i);
	}
	assert.throws(() => contract.normalizeFailureRate({ rate: 0, passes: 0, fails: 2 }, Number.MAX_SAFE_INTEGER + 1), /total|safe/i);

	const summarizer = readFileSync(join(ROOT, 'summarize-run.mjs'), 'utf8');
	for (const marker of ['normalizeCounterMetric', 'normalizeFailureRate', 'failurePasses', 'failureFails', 'requestCount']) {
		assert.match(summarizer, new RegExp(marker), `summarizer must bind exact Rate math marker ${marker}`);
	}
});
