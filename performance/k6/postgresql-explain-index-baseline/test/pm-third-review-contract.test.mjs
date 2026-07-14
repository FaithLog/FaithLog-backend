import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const testRoot = path.dirname(fileURLToPath(import.meta.url));
const scenarioRoot = path.resolve(testRoot, '..');
const moduleUrl = (relativePath) => pathToFileURL(path.join(scenarioRoot, relativePath)).href;

test('current #193/#196/#199 conditional artifacts and fabricated legacy success statuses remain pending', async () => {
	const { classifyIssueArtifactAcceptance } = await import(moduleUrl('cross-issue-contract.mjs'));
	const cases = [
		{
			issueNumber: 193,
			current: { status: 'conditional-shared-stack', automaticAdoption: false },
			fabricated: { status: 'eligible-for-pm-review', approved: true },
		},
		{
			issueNumber: 196,
			current: { status: 'conditional-not-adoptable', accepted: false, automaticAdoption: false },
			fabricated: { status: 'measured', accepted: true, measurementStatus: 'measured', approved: true },
		},
		{
			issueNumber: 199,
			current: { baselineAdoptionStatus: 'conditional-not-adoptable', automaticAdoption: false },
			fabricated: { status: 'adoptable', adoptable: true, approved: true },
		},
	];
	for (const { issueNumber, current, fabricated } of cases) {
		const currentResult = classifyIssueArtifactAcceptance(issueNumber, current);
		assert.equal(currentResult.accepted, false);
		assert.equal(currentResult.pendingApprovalContract, true);
		assert.match(currentResult.reason, new RegExp(`issue-${issueNumber}.*pending`, 'i'));
		assert.equal(classifyIssueArtifactAcceptance(issueNumber, fabricated).accepted, false,
			'legacy/invented success strings and a generic approved flag must not bypass a missing approved bridge contract');
	}
});

test('activity integrity covers every database on the PostgreSQL instance and identifies cross-database activity', async () => {
	const { validateActivityWindow } = await import(moduleUrl('activity-monitor-contract.mjs'));
	const result = validateActivityWindow({
		sampleCount: 3,
		measuredDatabase: 'faithlog',
		measuredSessionObserved: true,
		transientExternalActivityDetected: true,
		measuredSessions: [{
			pid: 777, label: 'query-1', registrationToken: 'a'.repeat(32), observed: true, unregistered: true,
		}],
		sessions: [{ pid: 903, database: 'postgres', backendType: 'client backend', state: 'active' }],
	}, { expectedLabels: ['query-1'] });
	assert.equal(result.adoptable, false);
	assert.ok(result.reasons.includes('other-database-activity-detected'));

	const worker = fs.readFileSync(path.join(scenarioRoot, 'activity-monitor-worker.mjs'), 'utf8');
	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	assert.doesNotMatch(worker, /WHERE\s+datname\s*=\s*current_database\(\)/i);
	assert.doesNotMatch(runner, /WHERE\s+datname\s*=\s*current_database\(\)/i);
	assert.match(worker, /backend_type\s*=\s*'client backend'/i);
	assert.match(worker, /'database'\s*,\s*datname/i);
	assert.match(runner, /'database'\s*,\s*datname/i);
});

test('canonical lock rebinds immutable Compose identity before psql and verifies continuity after EXPLAIN', async () => {
	const { validateComposeIdentityContinuity } = await import(moduleUrl('runtime-contract.mjs'));
	const approved = {
		postgresContainerId: 'container-a', postgresContainerName: '/faithlog-postgres',
		postgresImageId: 'sha256:image-a', postgresImageReference: 'postgres:17',
		containerStartedAt: '2026-07-14T00:00:00Z', composeProject: 'faithlog', composeService: 'postgres',
		composeConfigFiles: '/repo/compose.yml', composeWorkingDir: '/repo', configuredDatabase: 'faithlog',
		postgresInternalPort: 5432, containerNetworkAddresses: ['172.20.0.2'],
		networkIdentity: [{ name: 'faithlog_default', networkId: 'network-a', ipAddress: '172.20.0.2', globalIPv6Address: '' }],
	};
	assert.equal(validateComposeIdentityContinuity(approved, structuredClone(approved)).stable, true);
	assert.equal(validateComposeIdentityContinuity({}, {}).stable, false);
	for (const changed of [
		{ postgresContainerId: 'container-b' },
		{ postgresImageId: 'sha256:image-b' },
		{ containerStartedAt: '2026-07-14T00:00:01Z' },
		{ networkIdentity: [{ ...approved.networkIdentity[0], networkId: 'network-b' }] },
	]) {
		const result = validateComposeIdentityContinuity(approved, { ...structuredClone(approved), ...changed });
		assert.equal(result.stable, false);
		assert.match(result.reasons.join(','), /compose-container-identity-changed/i);
	}

	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	const lock = runner.indexOf('acquireProjectLock(');
	const rebound = runner.indexOf('composeIdentityAfterLock = inspectComposeIdentity(');
	const continuity = runner.indexOf('validateComposeIdentityContinuity(composeIdentityBeforeLock, composeIdentityAfterLock)');
	const database = runner.indexOf('databaseIdentity = captureDatabaseIdentity()');
	const finalInspect = runner.indexOf('composeIdentityAfterMeasurement = inspectComposeIdentity(');
	const finalContinuity = runner.indexOf('validateComposeIdentityContinuity(composeIdentityAfterDatabaseIdentity, composeIdentityAfterMeasurement)');
	assert.ok(lock >= 0 && lock < rebound && rebound < continuity && continuity < database,
		'container replacement after the pre-lock inspect must fail before any psql/schema/anchor/EXPLAIN call');
	assert.ok(finalInspect > runner.indexOf('await explain(') && finalInspect < finalContinuity,
		'container continuity must be captured after all EXPLAIN work and validated before adoption');
});

test('activity sampling interval has no default and must be an explicit positive runtime integer', async () => {
	const { parseActivitySampleInterval } = await import(moduleUrl('runtime-contract.mjs'));
	for (const value of [undefined, null, '', '0', '-1', '1.5', 'not-a-number']) {
		assert.throws(() => parseActivitySampleInterval(value), /ACTIVITY_SAMPLE_INTERVAL_MS|positive.*integer/i);
	}
	assert.equal(parseActivitySampleInterval('1'), 1);
	assert.equal(parseActivitySampleInterval('60000'), 60000);

	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	const worker = fs.readFileSync(path.join(scenarioRoot, 'activity-monitor-worker.mjs'), 'utf8');
	assert.match(runner, /requiredRuntimeInputs[\s\S]*'ACTIVITY_SAMPLE_INTERVAL_MS'/);
	assert.match(runner, /activitySampleIntervalMs\s*=\s*parseActivitySampleInterval\(process\.env\.ACTIVITY_SAMPLE_INTERVAL_MS\)/);
	assert.match(runner, /activity-monitor-worker\.mjs'\),\s*outputPath,\s*measuredApplicationName,\s*String\(activitySampleIntervalMs\)/s);
	assert.match(runner, /activitySampleIntervalMs,/);
	assert.doesNotMatch(worker, /delay\(50\)/);
});

test('instance-wide activity SQL treats visibility-restricted NULL state as contamination', () => {
	const worker = fs.readFileSync(path.join(scenarioRoot, 'activity-monitor-worker.mjs'), 'utf8');
	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	for (const source of [worker, runner]) {
		assert.match(source, /state\s+IS\s+DISTINCT\s+FROM\s+'idle'/i);
		assert.doesNotMatch(source, /state\s*<>\s*'idle'/i);
	}
});

test('DB identity capture is bracketed by immutable container inspections at start and finish', () => {
	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	const afterLockInspect = runner.indexOf('composeIdentityAfterLock = inspectComposeIdentity(');
	const databaseBefore = runner.indexOf('databaseIdentity = captureDatabaseIdentity()');
	const afterDatabaseInspect = runner.indexOf('composeIdentityAfterDatabaseIdentity = inspectComposeIdentity(');
	const bindingContinuity = runner.indexOf('validateComposeIdentityContinuity(composeIdentityAfterLock, composeIdentityAfterDatabaseIdentity)');
	const schemaBefore = runner.indexOf('schemaBefore = captureSchemaState(');
	assert.ok(afterLockInspect < databaseBefore && databaseBefore < afterDatabaseInspect
		&& afterDatabaseInspect < bindingContinuity && bindingContinuity < schemaBefore,
		'container replacement around the initial DB identity capture must fail before schema/anchor/EXPLAIN');

	const plannerAfter = runner.indexOf('const after = capturePlannerState(');
	const schemaAfter = runner.indexOf('const schemaAfter = captureSchemaState(');
	const databaseAfter = runner.indexOf('const databaseIdentityAfter = captureDatabaseIdentity()');
	const finalInspect = runner.indexOf('composeIdentityAfterMeasurement = inspectComposeIdentity(');
	const finalContinuity = runner.indexOf('validateComposeIdentityContinuity(composeIdentityAfterDatabaseIdentity, composeIdentityAfterMeasurement)');
	assert.ok(plannerAfter < schemaAfter && schemaAfter < databaseAfter && databaseAfter < finalInspect
		&& finalInspect < finalContinuity,
		'the last immutable inspect must bracket all after-state planner/schema/database captures');
});

test('SIGTERM interrupts an approved long sampling delay so the worker preserves its final report', async () => {
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-194-monitor-stop-'));
	let worker = null;
	try {
		const bin = path.join(root, 'bin');
		const output = path.join(root, 'window.json');
		fs.mkdirSync(bin);
		const fakePsql = path.join(bin, 'psql');
		fs.writeFileSync(fakePsql, "#!/bin/sh\nprintf '%s\\n' '[]'\n");
		fs.chmodSync(fakePsql, 0o700);
		worker = spawn(process.execPath, [
			path.join(scenarioRoot, 'activity-monitor-worker.mjs'), output, 'faithlog-', '60000', 'faithlog',
		], {
			env: { ...process.env, PATH: `${bin}:${process.env.PATH}` }, stdio: ['pipe', 'pipe', 'pipe'],
		});
		await new Promise((resolve, reject) => {
			const timer = setTimeout(() => reject(new Error('long-interval worker readiness timed out')), 3000);
			worker.once('error', reject);
			worker.stdout.on('data', (chunk) => {
				if (chunk.toString().includes('ready')) {
					clearTimeout(timer);
					worker.kill('SIGTERM');
					resolve();
				}
			});
		});
		const outcome = await new Promise((resolve) => {
			let timedOut = false;
			const timer = setTimeout(() => {
				timedOut = true;
				worker.kill('SIGKILL');
			}, 2500);
			worker.once('exit', (code, signal) => {
				clearTimeout(timer);
				resolve({ timedOut, code, signal });
			});
		});
		assert.deepEqual(outcome, { timedOut: false, code: 0, signal: null });
		const report = JSON.parse(fs.readFileSync(output, 'utf8'));
		assert.equal(report.activitySampleIntervalMs, 60000);
		assert.ok(report.sampleCount >= 2);
	} finally {
		if (worker && worker.exitCode === null && worker.signalCode === null) worker.kill('SIGKILL');
		fs.rmSync(root, { recursive: true, force: true });
	}
});

test('observer grace covers an in-flight sample plus the final sample before forced kill', async () => {
	const {
		ACTIVITY_MONITOR_GRACEFUL_TIMEOUT_MS,
		ACTIVITY_SAMPLE_TIMEOUT_MS,
	} = await import(moduleUrl('runtime-contract.mjs'));
	assert.ok(ACTIVITY_MONITOR_GRACEFUL_TIMEOUT_MS > ACTIVITY_SAMPLE_TIMEOUT_MS * 2,
		'observer grace must exceed two worst-case samples');
	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	assert.match(runner, /terminateChild\(monitor\.child, monitor\.exitPromise,\s*\{ gracefulTimeoutMs: ACTIVITY_MONITOR_GRACEFUL_TIMEOUT_MS \}\)/);

	const { terminateChildProcess } = await import(moduleUrl('runner-safety-contract.mjs'));
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-194-monitor-slow-stop-'));
	let worker = null;
	try {
		const bin = path.join(root, 'bin');
		const output = path.join(root, 'window.json');
		const firstSampleMarker = path.join(root, 'first-sample-marker');
		fs.mkdirSync(bin);
		const fakePsql = path.join(bin, 'psql');
		fs.writeFileSync(fakePsql, `#!/bin/sh
if ! mkdir "$FAKE_FIRST_SAMPLE_MARKER" 2>/dev/null; then sleep 1.6; fi
printf '%s\n' '[]'
`);
		fs.chmodSync(fakePsql, 0o700);
		worker = spawn(process.execPath, [
			path.join(scenarioRoot, 'activity-monitor-worker.mjs'), output, 'faithlog-', '1', 'faithlog',
		], {
			env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, FAKE_FIRST_SAMPLE_MARKER: firstSampleMarker },
			stdio: ['pipe', 'pipe', 'pipe'],
		});
		const exitPromise = new Promise((resolve) => {
			worker.once('error', (error) => resolve({ error, code: null }));
			worker.once('exit', (code) => resolve({ error: null, code }));
		});
		await new Promise((resolve, reject) => {
			const timer = setTimeout(() => reject(new Error('slow worker readiness timed out')), 3000);
			worker.once('error', reject);
			worker.stdout.on('data', (chunk) => {
				if (chunk.toString().includes('ready')) {
					clearTimeout(timer);
					setTimeout(resolve, 100);
				}
			});
		});
		const outcome = await terminateChildProcess(worker, exitPromise, {
			gracefulTimeoutMs: ACTIVITY_MONITOR_GRACEFUL_TIMEOUT_MS,
		});
		assert.equal(outcome.code, 0);
		assert.equal(outcome.error, null);
		assert.ok(fs.existsSync(output), 'worker must preserve its final window report before the grace expires');
		assert.ok(JSON.parse(fs.readFileSync(output, 'utf8')).sampleCount >= 3);
	} finally {
		if (worker && worker.exitCode === null && worker.signalCode === null) worker.kill('SIGKILL');
		fs.rmSync(root, { recursive: true, force: true });
	}
});
