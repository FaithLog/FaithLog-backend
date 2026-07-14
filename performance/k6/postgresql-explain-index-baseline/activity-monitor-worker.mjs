import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import { parseActivitySampleInterval } from './runtime-contract.mjs';

const [outputPath, measuredApplicationPrefix, sampleIntervalInput, measuredDatabase] = process.argv.slice(2);
if (!outputPath || !measuredApplicationPrefix || !measuredDatabase) {
	throw new Error('Usage: activity-monitor-worker.mjs <output-path> <measured-application-prefix> <sample-interval-ms> <measured-database>');
}
const activitySampleIntervalMs = parseActivitySampleInterval(sampleIntervalInput);

let stopping = false;
let wakePendingDelay = null;
let sampleCount = 0;
let externalNotificationSent = false;
const activeRegistrations = new Map();
const completedRegistrations = [];
const cancelledRegistrations = [];
const sessions = new Map();
const pendingPrefixedSessions = new Map();

process.on('SIGTERM', () => {
	stopping = true;
	wakePendingDelay?.();
});
process.stdin.setEncoding('utf8');
let inputBuffer = '';
process.stdin.on('data', (chunk) => {
	inputBuffer += chunk;
	let newline;
	while ((newline = inputBuffer.indexOf('\n')) >= 0) {
		const line = inputBuffer.slice(0, newline);
		inputBuffer = inputBuffer.slice(newline + 1);
		handleControlLine(line);
	}
});

while (!stopping) {
	recordSample(captureSample());
	if (sampleCount === 1) process.stdout.write('ready\n');
	await interruptibleDelay(activitySampleIntervalMs);
}

recordSample(captureSample());

for (const [key, pending] of pendingPrefixedSessions) {
	if (pending.wasActive) sessions.set(key, pending.session);
}

const measured = [
	...completedRegistrations,
	...[...activeRegistrations.values()].map((registration) => ({ ...registration, unregistered: false })),
];
const observed = [...sessions.values()];
fs.writeFileSync(outputPath, `${JSON.stringify({
	adoptable: observed.length === 0 && measured.length > 0
		&& measured.every((registration) => registration.observed && registration.unregistered),
	transientExternalActivityDetected: observed.length > 0,
	sampleCount,
	activitySampleIntervalMs,
	measuredDatabase,
	measuredApplicationPrefix,
	measuredSessionObserved: measured.length > 0 && measured.every((registration) => registration.observed),
	measuredSessions: measured,
	cancelledRegistrations,
	sessions: observed,
}, null, 2)}\n`, { mode: 0o600 });
process.stdin.destroy();

function handleControlLine(line) {
	if (line.startsWith('PREPARE:')) {
		const registration = JSON.parse(Buffer.from(line.slice('PREPARE:'.length), 'base64url').toString('utf8'));
		validatePreparedRegistration(registration);
		if (activeRegistrations.has(registration.registrationToken)
			|| [...activeRegistrations.values(), ...completedRegistrations]
				.some((entry) => entry.label === registration.label)) {
			throw new Error('Duplicate activity monitor registration token or label.');
		}
		activeRegistrations.set(registration.registrationToken, {
			...registration, pid: null, backendStart: null, bound: false,
			observed: false, unregistered: false,
		});
		process.stdout.write(`measured-prepared:${registration.registrationToken}\n`);
		return;
	}
	if (line.startsWith('BIND:')) {
		const binding = JSON.parse(Buffer.from(line.slice('BIND:'.length), 'base64url').toString('utf8'));
		const registration = activeRegistrations.get(binding.registrationToken);
		validateBinding(registration, binding);
		Object.assign(registration, {
			pid: binding.pid,
			backendStart: binding.backendStart,
			bound: true,
		});
		reconcilePendingRegistration(registration);
		return;
	}
	if (line.startsWith('UNREGISTER:')) {
		const registrationToken = line.slice('UNREGISTER:'.length);
		const registration = activeRegistrations.get(registrationToken);
		if (!registration || !registration.observed) throw new Error('Unknown or unobserved activity monitor registration.');
		activeRegistrations.delete(registrationToken);
		completedRegistrations.push({ ...registration, unregistered: true });
		process.stdout.write(`measured-unregistered:${registrationToken}\n`);
		return;
	}
	if (line.startsWith('CANCEL:')) {
		const registrationToken = line.slice('CANCEL:'.length);
		const registration = activeRegistrations.get(registrationToken);
		if (!registration || registration.observed) throw new Error('Unknown or already observed activity monitor registration cancellation.');
		activeRegistrations.delete(registrationToken);
		cancelledRegistrations.push({ ...registration, cancelled: true });
		process.stdout.write(`measured-cancelled:${registrationToken}\n`);
	}
}

function validatePreparedRegistration(registration) {
	if (!registration || !/^[a-f0-9]{32}$/.test(registration.registrationToken ?? '')
		|| typeof registration.label !== 'string' || registration.label.length === 0
		|| !registration.applicationName?.startsWith(measuredApplicationPrefix)) {
		throw new Error('Invalid prepared activity monitor registration identity.');
	}
}

function validateBinding(registration, binding) {
	if (!registration || registration.bound
		|| !Number.isSafeInteger(binding?.pid) || binding.pid <= 0
		|| binding.applicationName !== registration.applicationName
		|| typeof binding.backendStart !== 'string' || !Number.isFinite(Date.parse(binding.backendStart))) {
		throw new Error('Invalid activity monitor backend binding identity.');
	}
}

function captureSample() {
	const result = spawnSync('psql', ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-v', `measured_prefix=${measuredApplicationPrefix}`, '-c', `
		SELECT COALESCE(json_agg(json_build_object(
			'pid', pid, 'database', datname, 'backendType', backend_type,
			'applicationName', application_name, 'backendStart', backend_start,
			'state', state, 'waitEventType', wait_event_type, 'waitEvent', wait_event, 'queryStart', query_start
		) ORDER BY pid), '[]'::json)
		FROM pg_stat_activity
		WHERE backend_type = 'client backend'
			AND pid <> pg_backend_pid()
			AND (state IS DISTINCT FROM 'idle' OR application_name LIKE :'measured_prefix' || '%');
	`], {
		encoding: 'utf8', env: { ...process.env, PGCONNECT_TIMEOUT: '2' },
		maxBuffer: 4 * 1024 * 1024, timeout: 2000, killSignal: 'SIGKILL',
	});
	if (result.error) throw result.error;
	if (result.status !== 0) throw new Error(`Activity sampling failed with status ${result.status}.`);
	return JSON.parse(result.stdout.trim());
}

function recordSample(sample) {
	sampleCount += 1;
	for (const session of sample) {
		const registration = [...activeRegistrations.values()]
			.find((candidate) => candidate.bound && sameBackend(candidate, session));
		if (registration) {
			observeRegistration(registration);
			continue;
		}
		const key = sessionKey(session);
		if (session.applicationName?.startsWith(measuredApplicationPrefix)) {
			const reservation = [...activeRegistrations.values()]
				.find((candidate) => candidate.applicationName === session.applicationName);
			if (reservation?.bound) {
				recordExternal(key, session);
			} else if (!reservation) {
				if (session.state !== 'idle') recordExternal(key, session);
			} else {
				const pending = pendingPrefixedSessions.get(key) ?? { session, wasActive: false };
				pending.session = session;
				pending.wasActive ||= session.state !== 'idle';
				pendingPrefixedSessions.set(key, pending);
			}
		} else if (session.state !== 'idle') {
			recordExternal(key, session);
		}
	}
}

function reconcilePendingRegistration(registration) {
	for (const [key, pending] of pendingPrefixedSessions) {
		pendingPrefixedSessions.delete(key);
		if (sameBackend(registration, pending.session)) observeRegistration(registration);
		else if (pending.wasActive) recordExternal(key, pending.session);
	}
}

function observeRegistration(registration) {
	registration.observed = true;
	if (!registration.acknowledged) {
		registration.acknowledged = true;
		process.stdout.write(`measured-observed:${registration.registrationToken}\n`);
	}
}

function recordExternal(key, session) {
	sessions.set(key, { ...session, observedAtSample: sampleCount });
	if (!externalNotificationSent) {
		externalNotificationSent = true;
		process.stdout.write('external-observed\n');
	}
}

function sameBackend(registration, session) {
	return registration.pid === session.pid
		&& registration.applicationName === session.applicationName
		&& Date.parse(registration.backendStart) === Date.parse(session.backendStart);
}

function sessionKey(session) {
	return `${session.pid}:${session.backendStart ?? ''}:${session.queryStart ?? ''}`;
}

function interruptibleDelay(milliseconds) {
	if (stopping) return Promise.resolve();
	return new Promise((resolve) => {
		let settled = false;
		const finish = () => {
			if (settled) return;
			settled = true;
			clearTimeout(timer);
			if (wakePendingDelay === finish) wakePendingDelay = null;
			resolve();
		};
		const timer = setTimeout(finish, milliseconds);
		wakePendingDelay = finish;
	});
}
