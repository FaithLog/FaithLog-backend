import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { validateRuntimeSnapshot } from './evidence-contract.mjs';

const targetPath = required('TARGET_CONTRACT');
const phase = required('EVIDENCE_PHASE');
const target = JSON.parse(readFileSync(resolve(targetPath), 'utf8'));
const evidenceCase = {
	datasetId: required('PERF_DATASET_ID'),
	fixtureRunId: required('PERF_FIXTURE_RUN_ID'),
	executionRunId: required('PERF_EXECUTION_RUN_ID'),
};
const containers = Object.fromEntries(Object.entries(target.containers).map(([role, expected]) => [role, inspect(role, expected)]));
const database = JSON.parse(exec(target.containers.postgres.id, [
	'psql', '-U', target.database.user, '-d', target.database.name, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-c',
	"SELECT json_build_object('name',current_database(),'user',current_user,'serverAddress',inet_server_addr()::text,'serverPort',inet_server_port(),'postmasterStartedAt',pg_postmaster_start_time(),'statsReset',(SELECT stats_reset FROM pg_stat_database WHERE datname=current_database()));",
]));
const redisInfo = Object.fromEntries(exec(target.containers.redis.id, ['redis-cli', '--raw', 'INFO', 'server'])
	.split('\n').map((line) => line.trim()).filter((line) => line && !line.startsWith('#')).map((line) => line.split(':', 2)));
const redis = { runId: redisInfo.run_id, serverPort: Number(redisInfo.tcp_port), uptimeSeconds: redisInfo.uptime_in_seconds };
const snapshot = { case: evidenceCase, phase, capturedAt: new Date().toISOString(), containers, database, redis };
validateRuntimeSnapshot(snapshot, target, evidenceCase);
process.stdout.write(`${JSON.stringify(snapshot)}\n`);

function inspect(role, expected) {
	const result = command('docker', ['inspect', expected.id]);
	const value = JSON.parse(result)[0];
	if (!value || value.State?.Status !== 'running') throw new Error(`${role} is not running.`);
	if (String(value.Name || '').replace(/^\//, '') !== expected.name) throw new Error(`${role} container name drift.`);
	const publishedPorts = [];
	for (const [containerPort, bindings] of Object.entries(value.NetworkSettings?.Ports || {})) {
		for (const binding of bindings || []) {
			const host = binding.HostIp.includes(':') ? `[${binding.HostIp}]` : binding.HostIp;
			publishedPorts.push(`${host}:${binding.HostPort}->${containerPort}`);
		}
	}
	return {
		name: expected.name, id: value.Id, imageId: value.Image, startedAt: value.State.StartedAt,
		composeProject: value.Config.Labels?.['com.docker.compose.project'], composeService: value.Config.Labels?.['com.docker.compose.service'],
		configHash: value.Config.Labels?.['com.docker.compose.config-hash'], health: value.State.Health?.Status || 'none', publishedPorts: publishedPorts.sort(),
	};
}

function exec(containerId, args) { return command('docker', ['exec', containerId, ...args]).trim(); }
function command(name, args) { const result = spawnSync(name, args, { encoding: 'utf8' }); if (result.status !== 0) throw new Error(`${name} failed: ${result.stderr.trim()}`); return result.stdout; }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
