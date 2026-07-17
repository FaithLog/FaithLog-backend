import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { SETTLEMENT_MODES, SINGLE_MODE_PROTOCOL_VERSION } from './single-mode-contract.mjs';

const BUNDLE_KEYS = Object.freeze([
	'protocolVersion', 'comparisonIdentity', 'accepted', 'automaticAdoption',
	'evidenceIntegrity', 'measurementStatus', 'modes',
]);
const IDENTITY_KEYS = Object.freeze(['sourceCommit', 'targetIdentitySha256', 'targetRoleIdentity', 'workloadContract']);

export function compareModeBundles(before, after) {
	validateBundle(before);
	validateBundle(after);
	if (canonical(before.comparisonIdentity.targetRoleIdentity) !== canonical(after.comparisonIdentity.targetRoleIdentity)) reject();
	if (canonical(before.comparisonIdentity.workloadContract) !== canonical(after.comparisonIdentity.workloadContract)) reject();
	if (before.comparisonIdentity.sourceCommit === after.comparisonIdentity.sourceCommit) reject();
	if (before.comparisonIdentity.targetIdentitySha256 === after.comparisonIdentity.targetIdentitySha256) reject();

	return {
		protocolVersion: SINGLE_MODE_PROTOCOL_VERSION,
		comparisonIdentity: {
			beforeSourceCommit: before.comparisonIdentity.sourceCommit,
			afterSourceCommit: after.comparisonIdentity.sourceCommit,
			beforeTargetIdentitySha256: before.comparisonIdentity.targetIdentitySha256,
			afterTargetIdentitySha256: after.comparisonIdentity.targetIdentitySha256,
			targetRoleIdentity: structuredClone(before.comparisonIdentity.targetRoleIdentity),
			workloadContract: structuredClone(before.comparisonIdentity.workloadContract),
		},
		accepted: false,
		automaticAdoption: false,
		evidenceIntegrity: 'validated',
		measurementStatus: 'conditional-boundary-only',
		modes: Object.fromEntries(SETTLEMENT_MODES.map((mode) => [mode, {
			before: structuredClone(before.modes[mode]),
			after: structuredClone(after.modes[mode]),
		}])),
	};
}

function validateBundle(bundle) {
	assertExactKeys(bundle, BUNDLE_KEYS);
	assertExactKeys(bundle.comparisonIdentity, IDENTITY_KEYS);
	if (bundle.protocolVersion !== SINGLE_MODE_PROTOCOL_VERSION
		|| bundle.accepted !== false
		|| bundle.automaticAdoption !== false
		|| bundle.evidenceIntegrity !== 'validated'
		|| bundle.measurementStatus !== 'conditional-boundary-only') reject();
	if (!/^[a-f0-9]{40}$/.test(bundle.comparisonIdentity.sourceCommit)
		|| !/^[a-f0-9]{64}$/.test(bundle.comparisonIdentity.targetIdentitySha256)) reject();
	if (canonical(Object.keys(bundle.modes).sort()) !== canonical([...SETTLEMENT_MODES].sort())) reject();
	if (bundle.comparisonIdentity.workloadContract?.protocolVersion !== SINGLE_MODE_PROTOCOL_VERSION) reject();
	for (const mode of SETTLEMENT_MODES) validateMetrics(bundle.modes[mode], mode);
}

function validateMetrics(metrics, mode) {
	if (!metrics || typeof metrics !== 'object' || Array.isArray(metrics)) reject();
	const expectedCount = mode.endsWith('-sequential') ? 10 : 5;
	if (metrics.measuredRequestCount !== expectedCount || metrics.failureRate !== 0
		|| !Number.isFinite(metrics.throughputRps) || metrics.throughputRps <= 0) reject();
	const latency = metrics.latencyMs;
	if (!latency || !['p50', 'p95', 'p99', 'max'].every((key) => Number.isFinite(latency[key]) && latency[key] >= 0)
		|| !(latency.p50 <= latency.p95 && latency.p95 <= latency.p99 && latency.p99 <= latency.max)) reject();
}

function assertExactKeys(value, keys) {
	if (!value || typeof value !== 'object' || Array.isArray(value)
		|| canonical(Object.keys(value).sort()) !== canonical([...keys].sort())) reject();
}
function canonical(value) {
	if (Array.isArray(value)) return JSON.stringify(value.map((item) => JSON.parse(canonical(item))));
	if (value && typeof value === 'object') return JSON.stringify(Object.fromEntries(Object.keys(value).sort().map((key) => [key, JSON.parse(canonical(value[key]))])));
	return JSON.stringify(value);
}
function reject() { throw new Error('bundle comparison contract mismatch'); }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }

async function main() {
	const before = JSON.parse(readFileSync(resolve(required('BEFORE_BUNDLE')), 'utf8'));
	const after = JSON.parse(readFileSync(resolve(required('AFTER_BUNDLE')), 'utf8'));
	writeFileSync(resolve(required('COMPARISON_OUTPUT')), `${JSON.stringify(compareModeBundles(before, after), null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
