import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseDockerStatsRows } from './resource-contract.mjs';

const TARGET = {
	containers: {
		app: { id: 'a'.repeat(64), name: 'faithlog-latest-app' },
		postgres: { id: 'b'.repeat(64), name: 'faithlog-latest-postgres' },
		redis: { id: 'c'.repeat(64), name: 'faithlog-latest-redis' },
	},
};

function rows() {
	return Object.values(TARGET.containers).map((container) => ({
		ID: container.id, Name: container.name, CPUPerc: '1.25%', MemUsage: '100B / 1kB', MemPerc: '10.00%',
	}));
}

test('resource collector binds one exact full container ID and name per role', () => {
	const parsed = parseDockerStatsRows(rows(), TARGET);
	assert.equal(parsed.app.containerId, TARGET.containers.app.id);
	for (const mutate of [
		(value) => { value[0].ID = value[0].ID.slice(0, 12); },
		(value) => { value[0].ID = `${value[0].ID.slice(0, 63)}f`; },
		(value) => { value[0].Name = 'foreign'; },
		(value) => { value.push({ ...value[0] }); },
	]) {
		const value = rows(); mutate(value);
		assert.throws(() => parseDockerStatsRows(value, TARGET), /resource|container|row|ID|name/i);
	}
});

test('resource collector rejects malformed or inconsistent CPU, memory, and MemPerc', () => {
	for (const mutate of [
		(value) => { value[0].CPUPerc = '-1%'; },
		(value) => { value[0].MemUsage = '100B / 0B'; },
		(value) => { value[0].MemUsage = '1001B / 1kB'; },
		(value) => { value[0].MemUsage = '9007199254740992B / 9007199254740992B'; },
		(value) => { value[0].MemUsage = '64MiB / 1GiB'; value[0].MemPerc = '99.00%'; },
	]) {
		const value = rows(); mutate(value);
		assert.throws(() => parseDockerStatsRows(value, TARGET), /percent|memory|resource|safe|bounds/i);
	}
});

test('resource collector canonicalizes normal Docker human units without accepting fractional B values', () => {
	const value = rows();
	value[0].MemUsage = '104.3MiB / 1GiB';
	value[0].MemPerc = '10.19%';
	const parsed = parseDockerStatsRows(value, TARGET);
	assert.match(parsed.app.memoryUsedBytes, /^\d+$/);
	value[0].MemUsage = '1.5B / 1kB';
	assert.throws(() => parseDockerStatsRows(value, TARGET), /fractional byte/i);
});

test('resource collector accepts independently rounded Docker memory displays when their precision intervals overlap', () => {
	const value = rows();
	value[0].MemUsage = '206MiB / 7.653GiB';
	value[0].MemPerc = '2.64%';
	const exactRepresentativeRatio = 206 * 1024 ** 2 / (7.653 * 1024 ** 3) * 100;
	assert.ok(Math.abs(exactRepresentativeRatio - 2.64) > 0.005, 'fixture must reproduce the old point-estimate false rejection');
	assert.doesNotThrow(() => parseDockerStatsRows(value, TARGET));
});

test('resource memory display interval boundary is inclusive but one percent display unit outside is rejected', () => {
	const value = rows();
	value[0].MemUsage = '2B / 1kB';
	value[0].MemPerc = '1%';
	assert.equal((2.5 / 500) * 100, 0.5, 'memory ratio upper bound must exactly touch the 1% display lower bound');
	assert.doesNotThrow(() => parseDockerStatsRows(value, TARGET));
	value[0].MemPerc = '2%';
	assert.throws(() => parseDockerStatsRows(value, TARGET), /memory percent consistency/i);
});

test('resource collector rejects memory percentages above 100 without imposing that limit on multi-core CPU', () => {
	for (const memoryPercent of ['100.01%', '100.00000000000000001%']) {
		const value = rows();
		value[0].CPUPerc = '125.00%';
		value[0].MemUsage = '100B / 100B';
		value[0].MemPerc = memoryPercent;
		assert.throws(() => parseDockerStatsRows(value, TARGET), /memory percent consistency/i);
	}
});
