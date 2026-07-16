import assert from 'node:assert/strict';
import { appendFileSync } from 'node:fs';
import { parseDockerStatsDisplay } from './integrity-contract.mjs';

const capturedAt = process.env.CAPTURED_AT;
const component = process.env.RESOURCE_COMPONENT;
const containerName = process.env.RESOURCE_CONTAINER_NAME;
const containerId = process.env.RESOURCE_CONTAINER_ID;
const outputPath = process.env.RESOURCE_OUTPUT_PATH;
assert.ok(Number.isFinite(Date.parse(capturedAt)), 'resource capturedAt must be ISO');
assert.ok(['postgres', 'redis'].includes(component), 'resource component invalid');
for (const [value, label] of [[containerName, 'container name'], [containerId, 'container ID'], [outputPath, 'output path']]) {
	assert.ok(typeof value === 'string' && value.length > 0 && !value.includes(','), `resource ${label} invalid`);
}
const { cpuPercent, memoryUsedBytes, memoryLimitBytes, memoryPercent } = parseDockerStatsDisplay(
	process.env.RESOURCE_DOCKER_STATS,
);
appendFileSync(outputPath, [
	capturedAt, component, containerName, containerId, cpuPercent,
	memoryUsedBytes, memoryLimitBytes, memoryPercent,
].join(',') + '\n');
