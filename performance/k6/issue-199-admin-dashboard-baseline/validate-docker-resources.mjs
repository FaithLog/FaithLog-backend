import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const COMPONENTS = ['app', 'postgres', 'redis'];
const [rawPath, identityPath, datasetMode, boundary, outputPath] = process.argv.slice(2);

try {
	assert.ok(rawPath && identityPath && outputPath,
		'Usage: validate-docker-resources.mjs <raw.jsonl> <identity.json> <mode> <boundary> <output.json>');
	assert.ok(['empty', 'small', 'thousand'].includes(datasetMode), 'Docker resource dataset mode is invalid.');
	assert.ok(['before', 'after'].includes(boundary), 'Docker resource boundary must be before or after.');

	const identity = JSON.parse(fs.readFileSync(path.resolve(identityPath), 'utf8'));
	object(identity, 'runtime identity');
	object(identity.containers, 'runtime identity containers');
	assert.deepEqual(Object.keys(identity.containers).sort(), COMPONENTS,
		'Runtime identity container component set must be exact.');

	const expectedById = new Map();
	for (const component of COMPONENTS) {
		const container = identity.containers[component];
		object(container, `runtime identity containers.${component}`);
		string(container.id, `runtime identity containers.${component}.id`);
		string(container.name, `runtime identity containers.${component}.name`);
		assert.ok(!expectedById.has(container.id), 'Runtime identity container IDs must be unique.');
		expectedById.set(container.id, {component, name: container.name});
	}

	const lines = fs.readFileSync(path.resolve(rawPath), 'utf8').split(/\r?\n/).filter((line) => line.length > 0);
	assert.equal(lines.length, COMPONENTS.length, 'Docker resource evidence must contain exactly three components.');
	const seen = new Set();
	const resourcesByComponent = new Map();
	for (const [index, line] of lines.entries()) {
		const row = JSON.parse(line);
		object(row, `Docker resource row ${index}`);
		assert.equal(row.datasetMode, datasetMode, `Docker resource row ${index} dataset mode mismatch.`);
		assert.equal(row.boundary, boundary, `Docker resource row ${index} boundary mismatch.`);
		object(row.stats, `Docker resource row ${index}.stats`);
		for (const field of ['ID', 'Name', 'CPUPerc', 'MemUsage', 'MemPerc']) {
			string(row.stats[field], `Docker resource row ${index}.stats.${field}`);
		}
		const expected = expectedById.get(row.stats.ID);
		assert.ok(expected, `Docker resource row ${index} container ID is not in runtime identity.`);
		assert.equal(row.stats.Name, expected.name,
			`Docker resource ${expected.component} name does not match runtime identity.`);
		assert.ok(!seen.has(expected.component), `Duplicate Docker resource component: ${expected.component}`);
		seen.add(expected.component);
		const cpuPercent = percent(row.stats.CPUPerc, `${expected.component}.CPUPerc`);
		const memoryPercent = percent(row.stats.MemPerc, `${expected.component}.MemPerc`);
		const {usedBytes, limitBytes} = memoryUsage(row.stats.MemUsage, `${expected.component}.MemUsage`);
		assert.ok(limitBytes > 0, `${expected.component}.MemUsage limit bytes must be positive.`);
		assert.ok(usedBytes <= limitBytes,
			`${expected.component}.MemUsage used bytes must not exceed the limit.`);
		assert.ok(memoryPercent <= 100, `${expected.component}.MemPerc must be at most 100%.`);
		resourcesByComponent.set(expected.component, {
			component: expected.component,
			containerId: row.stats.ID,
			containerName: row.stats.Name,
			cpuPercent,
			memoryUsageBytes: usedBytes,
			memoryLimitBytes: limitBytes,
			memoryPercent,
		});
	}
	assert.deepEqual([...seen].sort(), COMPONENTS, 'Docker resource component set must be exact.');

	const output = {
		status: 'docker-resource-evidence-valid',
		adoptable: true,
		datasetMode,
		boundary,
		sampling: 'boundary-snapshot-not-continuous-or-peak',
		components: COMPONENTS.map((component) => resourcesByComponent.get(component)),
	};
	fs.writeFileSync(path.resolve(outputPath), `${JSON.stringify(output, null, 2)}\n`, {flag: 'wx', mode: 0o600});
} catch (error) {
	if (outputPath && !fs.existsSync(path.resolve(outputPath))) {
		try {
			fs.writeFileSync(path.resolve(outputPath), `${JSON.stringify({
				status: 'contaminated',
				adoptable: false,
				datasetMode: datasetMode || null,
				boundary: boundary || null,
				sampling: 'boundary-snapshot-not-continuous-or-peak',
				failures: [{name: 'dockerResourceEvidence', actual: error.message}],
			}, null, 2)}\n`, {flag: 'wx', mode: 0o600});
		} catch {
			// Keep the original validation failure as the process result.
		}
	}
	process.stderr.write(`Docker resource evidence is invalid: ${error.message}\n`);
	process.exitCode = 1;
}

function percent(value, label) {
	const match = /^(0|[1-9]\d*)(?:\.(\d+))?%$/.exec(value);
	assert.ok(match, `${label} must be a finite non-negative percentage.`);
	const parsed = Number(value.slice(0, -1));
	assert.ok(Number.isFinite(parsed) && parsed >= 0, `${label} must be finite and non-negative.`);
	return parsed;
}

function memoryUsage(value, label) {
	const quantity = '((?:0|[1-9]\\d*)(?:\\.\\d+)?)\\s*(B|kB|MB|GB|TB|PB|KiB|MiB|GiB|TiB|PiB)';
	const match = new RegExp(`^${quantity}\\s*\\/\\s*${quantity}$`).exec(value);
	assert.ok(match, `${label} must be '<non-negative bytes> / <non-negative limit>'.`);
	return {
		usedBytes: bytes(match[1], match[2], `${label} used`),
		limitBytes: bytes(match[3], match[4], `${label} limit`),
	};
}

function bytes(value, unit, label) {
	const multipliers = {
		B: 1,
		kB: 1e3, MB: 1e6, GB: 1e9, TB: 1e12, PB: 1e15,
		KiB: 2 ** 10, MiB: 2 ** 20, GiB: 2 ** 30, TiB: 2 ** 40, PiB: 2 ** 50,
	};
	const parsed = Number(value) * multipliers[unit];
	assert.ok(Number.isFinite(parsed) && parsed >= 0 && parsed <= Number.MAX_SAFE_INTEGER,
		`${label} must be finite, non-negative, and within the safe numeric range.`);
	return parsed;
}

function object(value, label) {
	assert.ok(value !== null && typeof value === 'object' && !Array.isArray(value), `${label} must be an object.`);
}

function string(value, label) {
	assert.ok(typeof value === 'string' && value.length > 0, `${label} must be a non-empty string.`);
}
