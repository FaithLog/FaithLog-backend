import assert from 'node:assert/strict';
import fs from 'node:fs';
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
	const finalContinuity = runner.indexOf('validateComposeIdentityContinuity(composeIdentityAfterLock, composeIdentityAfterMeasurement)');
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
